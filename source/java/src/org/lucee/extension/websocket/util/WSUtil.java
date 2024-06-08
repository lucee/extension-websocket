package org.lucee.extension.websocket.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;

import org.lucee.extension.websocket.WebSocketEndpointFactory;

import lucee.commons.io.log.Log;
import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.Component;
import lucee.runtime.Mapping;
import lucee.runtime.PageContext;
import lucee.runtime.PageSource;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigServer;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.util.ClassUtil;
import lucee.runtime.util.Decision;

public class WSUtil {
	private static final boolean LOG2CONSOLE = true;
	public static final short TYPE_UNDEFINED = 0;
	public static final short TYPE_JAKARTA = 1;
	public static final short TYPE_JAVAX = 2;
	public static final short TYPE_NOT_AVAILABLE = -1;

	public static final String SERIALIZE_JSON_CLASS = "lucee.runtime.functions.conversion.SerializeJSON";
	public static final String SERIALIZE_CLASS = "lucee.runtime.functions.dynamicEvaluation.Serialize";
	public static final String EVALUATE_CLASS = "lucee.runtime.functions.dynamicEvaluation.Evaluate";
	public static final String DESERIALIZE_JSON_CLASS = "lucee.runtime.functions.conversion.DeserializeJSON";

	public static final Object NULL = new Object();
	public static final String DEFAULT_DIRECTORY = "{lucee-config}/websockets/";
	private static short containerType;

	public static String getSystemPropOrEnvVar(String name, String defaultValue) {
		// env
		String value = System.getenv(name);
		if (!Util.isEmpty(value)) return value;

		// prop
		value = System.getProperty(name);
		if (!Util.isEmpty(value)) return value;

		// env 2
		name = name.replace('.', '_').toUpperCase();
		value = System.getenv(name);
		if (!Util.isEmpty(value)) return value;

		return defaultValue;
	}

	public static Component loadComponent(PageContext pc, PageSource ps, String callPath, boolean isRealPath, boolean silent, boolean executeConstr) throws PageException {
		try {
			Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass("lucee.runtime.component.ComponentLoader");
			Method method = clazz.getMethod("loadComponent", new Class[] { PageContext.class, PageSource.class, String.class, boolean.class, boolean.class, boolean.class });
			return (Component) method.invoke(null, new Object[] { pc, ps, callPath, isRealPath, silent, executeConstr });
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
	}

	public static Mapping getMapping(ConfigWeb cw, String path) {
		return CFMLEngineFactory.getInstance().getCreationUtil().createMapping(cw, "/ws", path, null, Config.INSPECT_ONCE, true, false, false, false, true, true, null, -1, -1);
	}

	public static ConfigWeb getConfig(ConfigServer cs, Object session) {
		// TODO make it better

		String reqContextPath = null;

		// get a matching servletContext
		for (ConfigWeb cw: cs.getConfigWebs()) {
			if (reqContextPath == null) {
				String reqURI = "";
				if (WSUtil.getContainerType(cw) == WSUtil.TYPE_JAKARTA) reqURI = ((jakarta.websocket.Session) session).getRequestURI().toString();
				else if (WSUtil.getContainerType(cw) == WSUtil.TYPE_JAVAX) reqURI = ((javax.websocket.Session) session).getRequestURI().toString();
				reqContextPath = reqURI.substring(0, reqURI.indexOf("/ws"));
			}

			if (getContextPath(cw).equals(reqContextPath)) return cw;
			// print.e(getContextPath(cw.getServletContext()) + " == " + reqContextPath);
		}

		return null;
	}

	private static String getContextPath(ConfigWeb cw) {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		ServletContext sc = cw.getServletContext();
		try {
			return (String) sc.getClass().getMethod("getContextPath", new Class[0]).invoke(sc, new Object[0]);
		}
		catch (Exception e) {
			// TODO extract from cw.getServletContext().getRealPath("/"));
			String tmp = eng.getListUtil().last(sc.getRealPath("/"), "/", true);
			tmp = eng.getListUtil().last(tmp, "\\", true);
			if (tmp.equals("ROOT")) return "";
			return "/" + tmp;
		}
	}

	public static Mapping createMapping(PageContext pc, String componentPath) throws PageException, IOException {

		String resolvedPath = WSUtil.replacePlaceholder(componentPath, pc.getConfig());
		WSUtil.info(pc.getConfig(), "component path is [" + componentPath + "] resolved [" + resolvedPath + "]");

		Resource res = CFMLEngineFactory.getInstance().getResourceUtil().toResourceNotExisting(pc, resolvedPath, true, true);
		res.mkdirs();
		return CFMLEngineFactory.getInstance().getCreationUtil().createMapping(pc.getConfig(), "/ws", resolvedPath, null, Config.INSPECT_ONCE, true, false, false, false, true,
				true, null, -1, -1);
	}

	/**
	 * 
	 * @param session
	 * @param res
	 * @return return false when it could not send a message because the message was null, otherwise
	 *         true
	 * @throws PageException
	 * @throws IOException
	 * @throws EncodeException
	 */
	public static boolean send(ConfigWeb cw, Object session, Object res) throws PageException {
		if (res == null || res == NULL) return false;
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();
		Decision dec = eng.getDecisionUtil();
		if (session == null || !isOpen(cw, session)) throw eng.getExceptionUtil().createApplicationException("cannot send message, connection to client is closed.");
		try {

			if (dec.isBinary(res)) sendBinary(cw, session, ByteBuffer.wrap(cast.toBinary(res)));
			else if (dec.isSimpleValue(res)) sendText(cw, session, cast.toString(res));
			else sendObject(cw, session, res);
			return true;
		}
		catch (Exception e) {
			throw cast.toPageException(e);
		}
	}

	public static Object broadcast(ConfigWeb cw, WebSocketEndpointFactory factory, Object res) throws PageException {
		if (res == null || res == NULL) return false;
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();
		Decision dec = eng.getDecisionUtil();

		try {
			if (dec.isBinary(res)) {
				ByteBuffer data = ByteBuffer.wrap(cast.toBinary(res));
				for (Object session: factory.getSessions(cw)) {
					if (isOpen(cw, session)) sendBinary(cw, session, data);// session.getBasicRemote().sendBinary(data);
				}
			}
			else if (dec.isSimpleValue(res)) {
				String data = cast.toString(res);
				for (Object session: factory.getSessions(cw)) {
					if (isOpen(cw, session)) sendText(cw, session, data);
				}
			}
			else {
				for (Object session: factory.getSessions(cw)) {
					if (isOpen(cw, session)) sendObject(cw, session, res);
				}
			}
		}
		catch (Exception e) {
			throw cast.toPageException(e);
		}

		return null;
	}

	public static Struct toCatchBlock(Config config, Throwable t) {
		return CFMLEngineFactory.getInstance().getCastUtil().toPageException(t).getCatchBlock(config);
	}

	public static String serializeJSON(PageContext pc, Object var, boolean serializeQueryByColumns) throws PageException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast caster = engine.getCastUtil();
		try {
			BIF bif = engine.getClassUtil().loadBIF(pc, SERIALIZE_JSON_CLASS);
			return caster.toString(bif.invoke(pc, new Object[] { var, serializeQueryByColumns }));
		}
		catch (Exception e) {
			throw caster.toPageException(e);
		}
	}

	public static Object deserializeJSON(PageContext pc, String obj) throws PageException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast caster = engine.getCastUtil();
		try {
			BIF bif = engine.getClassUtil().loadBIF(pc, DESERIALIZE_JSON_CLASS);
			return bif.invoke(pc, new Object[] { obj });
		}
		catch (Exception e) {
			throw caster.toPageException(e);
		}
	}

	public static Object[] readConfig(PageContext pc) throws PageException, IOException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Charset utf8 = eng.getCastUtil().toCharset("UTF-8");
		String strConfigFile = WSUtil.getSystemPropOrEnvVar("lucee.websocket.config", null);

		Resource res;
		if (!eng.getStringUtil().isEmpty(strConfigFile, true)) {
			strConfigFile = WSUtil.replacePlaceholder(strConfigFile.trim(), pc.getConfig());
			res = eng.getResourceUtil().toResourceExisting(pc, strConfigFile);
			info(pc.getConfig(), "found system property/enviroment variable [lucee.websocket.config/LUCEE_WEBSOCKET_CONFIG] with value [" + strConfigFile + "]");
		}
		else {
			res = pc.getConfig().getConfigDir().getRealResource("websocket.json");

		}

		if (!res.isFile()) {
			info(pc.getConfig(), "creating configuration file at  [" + res + "], using default settings");
			res.getParentResource().mkdirs();
			eng.getIOUtil().write(res, "{\n\t\"directory\":\"" + DEFAULT_DIRECTORY + "\", \n\t\"requestTimeout\":50, \n\t\"idleTimeout\":300\n}", false, utf8);
		}

		info(pc.getConfig(), "found configuration at  [" + res + "]");

		String content = eng.getIOUtil().toString(res, utf8);

		return new Object[] { res, eng.getCastUtil().toStruct(deserializeJSON(pc, content)) };
	}

	public static Object toCloseReason(ConfigWeb cw, Object obj) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();

		if (obj instanceof jakarta.websocket.CloseReason) return obj;
		if (obj instanceof javax.websocket.CloseReason) return obj;

		short ct = WSUtil.getContainerType(cw);
		boolean jak = ct == WSUtil.TYPE_JAKARTA;

		// Close Reason as a Struct
		Struct sct = cast.toStruct(obj, null);
		if (sct != null) {
			Object o = sct.get(cast.toKey("reason"), null);
			if (o == null) o = sct.get(cast.toKey("closereason"), null);
			if (o == null) o = sct.get(cast.toKey("close_reason"), null);
			if (o == null) o = sct.get(cast.toKey("message"), null);
			if (o == null) o = sct.get(cast.toKey("text"), null);

			if (o == null) throw eng.getExceptionUtil().createApplicationException("missing key [message] in struct for close reason");
			if (jak) return new jakarta.websocket.CloseReason((jakarta.websocket.CloseReason.CloseCode) toCloseCode(cw, sct), cast.toString(o));
			return new javax.websocket.CloseReason((javax.websocket.CloseReason.CloseCode) toCloseCode(cw, sct), cast.toString(o));
		}

		// Close Reason as String
		String str = cast.toString(obj, null);
		if (str != null) {
			if (jak) return new jakarta.websocket.CloseReason(jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE, str);
			return new javax.websocket.CloseReason(javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE, str);
		}

		throw eng.getExceptionUtil().createApplicationException(
				"object argument for close must be a string with a close reason message or a struct looking like this [ {message:'close for my own reason',code:'NORMAL_CLOSURE'} ]. The code is optional and by default it is [NORMAL_CLOSURE].");

	}

	private static Object toCloseCode(ConfigWeb cw, Struct sct) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();
		short ct = WSUtil.getContainerType(cw);
		boolean jak = ct == WSUtil.TYPE_JAKARTA;

		Object o = sct.get(cast.toKey("code"), null);
		if (o == null) o = sct.get(cast.toKey("closecode"), null);
		if (o == null) o = sct.get(cast.toKey("close_code"), null);
		if (o == null) return jak ? jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE : javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;

		if (o instanceof jakarta.websocket.CloseReason.CloseCode) return o;
		if (o instanceof javax.websocket.CloseReason.CloseCode) return o;

		String str = cast.toString(o).toUpperCase();
		if ("CANNOT_ACCEPT".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.CANNOT_ACCEPT : javax.websocket.CloseReason.CloseCodes.CANNOT_ACCEPT;
		if ("CLOSED_ABNORMALLY".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.CLOSED_ABNORMALLY : javax.websocket.CloseReason.CloseCodes.CLOSED_ABNORMALLY;
		if ("GOING_AWAY".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.GOING_AWAY : javax.websocket.CloseReason.CloseCodes.GOING_AWAY;
		if ("NO_EXTENSION".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.NO_EXTENSION : javax.websocket.CloseReason.CloseCodes.NO_EXTENSION;
		if ("NO_STATUS_CODE".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.NO_STATUS_CODE : javax.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;
		if ("NORMAL_CLOSURE".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE : javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
		if ("NORMAL".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE : javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;
		if ("NOT_CONSISTENT".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.NOT_CONSISTENT : javax.websocket.CloseReason.CloseCodes.NOT_CONSISTENT;
		if ("PROTOCOL_ERROR".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.PROTOCOL_ERROR : javax.websocket.CloseReason.CloseCodes.PROTOCOL_ERROR;
		if ("RESERVED".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.RESERVED : javax.websocket.CloseReason.CloseCodes.RESERVED;
		if ("SERVICE_RESTART".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.SERVICE_RESTART : javax.websocket.CloseReason.CloseCodes.SERVICE_RESTART;
		if ("TLS_HANDSHAKE_FAILURE".equals(str))
			return jak ? jakarta.websocket.CloseReason.CloseCodes.TLS_HANDSHAKE_FAILURE : javax.websocket.CloseReason.CloseCodes.TLS_HANDSHAKE_FAILURE;
		if ("TOO_BIG".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.TOO_BIG : javax.websocket.CloseReason.CloseCodes.TOO_BIG;
		if ("TRY_AGAIN_LATER".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.TRY_AGAIN_LATER : javax.websocket.CloseReason.CloseCodes.TRY_AGAIN_LATER;
		if ("UNEXPECTED_CONDITION".equals(str))
			return jak ? jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION : javax.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
		if ("VIOLATED_POLICY".equals(str)) return jak ? jakarta.websocket.CloseReason.CloseCodes.VIOLATED_POLICY : javax.websocket.CloseReason.CloseCodes.VIOLATED_POLICY;

		throw eng.getExceptionUtil().createApplicationException("Invalid closure code definition [" + str.toUpperCase()
				+ "], valid values are [CANNOT_ACCEPT,CLOSED_ABNORMALLY,GOING_AWAY,NO_EXTENSION,NO_STATUS_CODE,NORMAL_CLOSURE,NOT_CONSISTENT,PROTOCOL_ERROR,RESERVED,SERVICE_RESTART,TLS_HANDSHAKE_FAILURE,TOO_BIG,TRY_AGAIN_LATER,UNEXPECTED_CONDITION,VIOLATED_POLICY]");

	}

	public static String replacePlaceholder(String path, Config config) {
		if (path.indexOf('{') != -1) {
			try {
				Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass("lucee.runtime.config.ConfigWebUtil");
				Method m = clazz.getMethod("replacePlaceholder", new Class[] { String.class, Config.class });
				return (String) m.invoke(null, new Object[] { path, config });
			}
			catch (Exception e) {
				error(config, e);
			}
		}
		return path;
	}

	public static PageContext createPageContext(WebSocketEndpointFactory factory, final ConfigWeb cw, final Object session, final String componentName) throws PageException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream(); // TODO get nirvana Stream
		return createPageContext(cw, session, baos, Util.isEmpty(componentName) ? "/" : ("/" + componentName + ".cfc"), session == null ? "" : getQueryString(cw, session),
				factory.getRequestTimeout(cw));
	}

	private static PageContext createPageContext(final ConfigWeb cw, final Object session, OutputStream os, final String path, String qs, long timeout) throws PageException {
		try {
			CFMLEngine eng = CFMLEngineFactory.getInstance();
			Class<?> clazz = eng.getClassUtil().loadClass("lucee.runtime.thread.ThreadUtil");
			Class<?> clazzPairArray = eng.getClassUtil().loadClass("lucee.commons.lang.Pair[]");

			Method method = clazz.getMethod("createPageContext", new Class[] { ConfigWeb.class, OutputStream.class, String.class, String.class, String.class, Cookie[].class,
					clazzPairArray, byte[].class, clazzPairArray, Struct.class, boolean.class, long.class });

			return (PageContext) method.invoke(null, new Object[] { cw, os, "", path, qs, new Cookie[0], null, null, null, null, true, timeout });
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
	}

	public static void releasePageContext(PageContext pc) {
		CFMLEngineFactory.getInstance().releasePageContext(pc, true);
	}

	public static void trace(Config config, String msg) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.trace("endpoint-factory", msg);
		else console(msg);
	}

	public static void trace(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.log(Log.LEVEL_TRACE, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	public static void info(Config config, String msg) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.info("endpoint-factory", msg);
		else console(msg);
	}

	public static void info(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.log(Log.LEVEL_INFO, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	public static void warn(Config config, String msg) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.warn("endpoint-factory", msg);
		else console(msg);
	}

	public static void warn(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.log(Log.LEVEL_WARN, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	public static void error(Config config, Exception e) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.error("endpoint-factory", e);
		else console(e);
	}

	public static void error(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (!LOG2CONSOLE && log != null) log.log(Log.LEVEL_ERROR, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	private static void console(String msg) {
		System.err.println(msg);
	}

	private static void console(String msg, Throwable t) {
		System.err.println(msg);
		t.printStackTrace();
	}

	private static void console(Throwable t) {
		t.printStackTrace();
	}

	public static Log getLog(Config config) {
		if (config == null) config = CFMLEngineFactory.getInstance().getThreadConfig();
		if (config instanceof ConfigServer) {
			// we only log to config Server if there is no web context
			Config cw = CFMLEngineFactory.getInstance().getThreadConfig();
			if (cw != null) config = cw;
		}
		if (config == null) return null;
		try {
			Log log = config.getLog("websocket");
			if (log == null) log = config.getLog("application");
			if (log != null) return log;
		}
		catch (Exception e) {
			Log log = config.getLog("application");
			log.error("websocket", e);
			return log;
		}
		return null;
	}

	public static String getLogName(ConfigWeb config) {
		try {
			config.getLog("websocket");
			return "websocket";
		}
		catch (Exception e) {
			return "application";
		}
	}

	public static boolean hasLogLevel(Config config, int level) {
		Log log = getLog(config);
		return log != null && log.getLogLevel() <= level;
	}

	public static String getId(ConfigWeb cw, Object session) {
		if (getContainerType(cw) == TYPE_JAKARTA) return ((jakarta.websocket.Session) session).getId();
		else if (getContainerType(cw) == TYPE_JAVAX) return ((javax.websocket.Session) session).getId();
		return null;
	}

	public static boolean isOpen(ConfigWeb cw, Object session) {
		if (getContainerType(cw) == TYPE_JAKARTA) return ((jakarta.websocket.Session) session).isOpen();
		else if (getContainerType(cw) == TYPE_JAVAX) return ((javax.websocket.Session) session).isOpen();
		return false;
	}

	public static Object getBasicRemote(ConfigWeb cw, Object session) {
		if (getContainerType(cw) == TYPE_JAKARTA) return ((jakarta.websocket.Session) session).getBasicRemote();
		else if (getContainerType(cw) == TYPE_JAVAX) return ((javax.websocket.Session) session).getBasicRemote();
		return null;
	}

	public static void sendBinary(ConfigWeb cw, Object session, ByteBuffer data) throws IOException {
		synchronized (session) {
			Object br = getBasicRemote(cw, session);
			if (getContainerType(cw) == TYPE_JAKARTA) ((jakarta.websocket.RemoteEndpoint.Basic) br).sendBinary(data);
			else if (getContainerType(cw) == TYPE_JAVAX) ((javax.websocket.RemoteEndpoint.Basic) br).sendBinary(data);
		}
	}

	public static void sendText(ConfigWeb cw, Object session, String data) throws IOException {
		synchronized (session) {
			Object br = getBasicRemote(cw, session);
			if (getContainerType(cw) == TYPE_JAKARTA) ((jakarta.websocket.RemoteEndpoint.Basic) br).sendText(data);
			else if (getContainerType(cw) == TYPE_JAVAX) ((javax.websocket.RemoteEndpoint.Basic) br).sendText(data);
		}
	}

	public static void sendObject(ConfigWeb cw, Object session, Object data) throws Exception {
		synchronized (session) {
			Object br = getBasicRemote(cw, session);
			if (getContainerType(cw) == TYPE_JAKARTA) ((jakarta.websocket.RemoteEndpoint.Basic) br).sendObject(data);
			else if (getContainerType(cw) == TYPE_JAVAX) ((javax.websocket.RemoteEndpoint.Basic) br).sendObject(data);
		}
	}

	public static String getQueryString(ConfigWeb cw, Object session) {
		synchronized (session) {
			if (getContainerType(cw) == TYPE_JAKARTA) return ((jakarta.websocket.Session) session).getQueryString();
			else if (getContainerType(cw) == TYPE_JAVAX) return ((javax.websocket.Session) session).getQueryString();
			return null;
		}
	}

	public static Object getReasonPhrase(ConfigWeb cw, Object session, Object closeReason) {
		synchronized (session) {
			if (getContainerType(cw) == TYPE_JAKARTA) return ((jakarta.websocket.CloseReason) closeReason).getReasonPhrase();
			else if (getContainerType(cw) == TYPE_JAVAX) return ((javax.websocket.CloseReason) closeReason).getReasonPhrase();
			return null;
		}
	}

	public static void close(ConfigWeb cw, Object session, Object cr) throws IOException {
		synchronized (session) {
			if (getContainerType(cw) == TYPE_JAKARTA) {
				if (cr != null) ((jakarta.websocket.Session) session).close((jakarta.websocket.CloseReason) cr);
				else((jakarta.websocket.Session) session).close();
			}
			else if (getContainerType(cw) == TYPE_JAVAX) {
				if (cr != null) ((javax.websocket.Session) session).close((javax.websocket.CloseReason) cr);
				else((javax.websocket.Session) session).close();
			}
		}
	}

	public static void setMaxIdleTimeout(ConfigWeb cw, Object session, long millis) {
		synchronized (session) {
			if (getContainerType(cw) == TYPE_JAKARTA) ((jakarta.websocket.Session) session).setMaxIdleTimeout(millis);
			else if (getContainerType(cw) == TYPE_JAVAX) ((javax.websocket.Session) session).setMaxIdleTimeout(millis);
		}
	}

	public static long getMaxIdleTimeout(ConfigWeb cw, Object session, long defaultValue) {
		synchronized (session) {
			if (getContainerType(cw) == TYPE_JAKARTA) return ((jakarta.websocket.Session) session).getMaxIdleTimeout();
			else if (getContainerType(cw) == TYPE_JAVAX) return ((javax.websocket.Session) session).getMaxIdleTimeout();
		}
		return defaultValue;
	}

	public static short getContainerType(ConfigWeb cw) {
		if (containerType == TYPE_UNDEFINED) {
			Object oServerContainer = cw.getServletContext().getAttribute("javax.websocket.server.ServerContainer");
			if (oServerContainer == null) oServerContainer = cw.getServletContext().getAttribute("jakarta.websocket.server.ServerContainer");

			/*
			 * if (oServerContainer == null) { print.e("+++++++++++++++++++++++++"); Enumeration e =
			 * cw.getServletContext().getAttributeNames(); while (e.hasMoreElements()) { Object n =
			 * e.nextElement(); print.e(n); } }
			 */

			if (oServerContainer instanceof jakarta.websocket.server.ServerContainer) {
				containerType = TYPE_JAKARTA;
			}
			else if (oServerContainer instanceof javax.websocket.server.ServerContainer) {
				containerType = TYPE_JAVAX;
			}
			else {
				ClassUtil util = CFMLEngineFactory.getInstance().getClassUtil();
				if (util.isInstaneOf(oServerContainer.getClass(), "jakarta.websocket.server.ServerContainer")) containerType = TYPE_JAKARTA;
				else if (util.isInstaneOf(oServerContainer.getClass(), "javax.websocket.server.ServerContainer")) containerType = TYPE_JAVAX;
				else containerType = TYPE_NOT_AVAILABLE;
			}
		}
		return containerType;
	}

	public static Struct getInfoSession(ConfigWeb config, CFMLEngine eng, Object session, boolean addRaw) {
		if (WSUtil.getContainerType(config) == WSUtil.TYPE_JAKARTA) return getInfoSessionJakarta(eng, session, addRaw);
		return getInfoSessionJavax(eng, session, addRaw);
	}

	private static Struct getInfoSessionJakarta(CFMLEngine eng, Object session, boolean addRaw) {

		// sessions
		jakarta.websocket.Session s = (jakarta.websocket.Session) session;
		try {
			// we only report open sessions
			if (!s.isOpen()) return null;
			Struct sct = eng.getCreationUtil().createStruct();
			sct.setEL("id", s.getId());
			sct.setEL("negotiatedSubprotocol", s.getNegotiatedSubprotocol());
			sct.setEL("protocolVersion", s.getProtocolVersion());
			sct.setEL("queryString", s.getQueryString());
			sct.setEL("maxBinaryMessageBufferSize", s.getMaxBinaryMessageBufferSize());
			sct.setEL("maxIdleTimeout", s.getMaxIdleTimeout());
			sct.setEL("maxTextMessageBufferSize", s.getMaxTextMessageBufferSize());
			sct.setEL("requestURI", s.getRequestURI().toASCIIString());

			// asyncRemote
			jakarta.websocket.RemoteEndpoint.Async as = s.getAsyncRemote();
			if (as != null) {
				Struct sctAs = eng.getCreationUtil().createStruct();
				sct.setEL("asyncRemote", sctAs);
				sctAs.setEL("batchingAllowed", as.getBatchingAllowed());
				sctAs.setEL("sendTimeout", as.getSendTimeout());
			}

			// BasicRemote
			jakarta.websocket.RemoteEndpoint.Basic br = s.getBasicRemote();
			if (br != null) {
				Struct sctBr = eng.getCreationUtil().createStruct();
				sct.setEL("basicRemote", sctBr);
				sctBr.setEL("batchingAllowed", br.getBatchingAllowed());
			}

			// PathParameters
			Map<String, String> pp = s.getPathParameters();
			if (pp != null && pp.size() > 0) {
				Struct sctPp = eng.getCreationUtil().createStruct();
				sct.setEL("pathParameters", sctPp);
				for (Entry<String, String> e: pp.entrySet()) {
					sctPp.setEL(e.getKey(), e.getValue());
				}
			}

			// RequestParameterMap
			Map<String, List<String>> rpm = s.getRequestParameterMap();
			if (rpm != null && rpm.size() > 0) {
				Struct sctRpm = eng.getCreationUtil().createStruct();
				sct.setEL("requestParameter", sctRpm);
				for (Entry<String, List<String>> e: rpm.entrySet()) {
					sctRpm.setEL(e.getKey(), e.getValue());
				}
			}

			// UserPrincipal
			Principal up = s.getUserPrincipal();
			if (up != null) {
				sct.setEL("userPrincipal", up.getName());
			}

			// UserProperties
			Map<String, Object> up2 = s.getUserProperties();
			if (up2 != null && up2.size() > 0) {
				Struct sctUp = eng.getCreationUtil().createStruct();
				sct.setEL("userProperties", sctUp);
				for (Entry<String, Object> e: up2.entrySet()) {
					sctUp.setEL(e.getKey(), e.getValue());
				}
			}

			if (addRaw) sct.setEL("raw", s);
			return sct;
		}
		catch (IllegalStateException ise) {
			// this happens when a session get closed while reading the data, unlikely because we check "isOpen"
			// before but possible.
		}
		return null;
	}

	private static Struct getInfoSessionJavax(CFMLEngine eng, Object session, boolean addRaw) {

		// sessions
		javax.websocket.Session s = (javax.websocket.Session) session;
		try {
			// we only report open sessions
			if (!s.isOpen()) return null;
			Struct sct = eng.getCreationUtil().createStruct();
			sct.setEL("id", s.getId());
			sct.setEL("negotiatedSubprotocol", s.getNegotiatedSubprotocol());
			sct.setEL("protocolVersion", s.getProtocolVersion());
			sct.setEL("queryString", s.getQueryString());
			sct.setEL("maxBinaryMessageBufferSize", s.getMaxBinaryMessageBufferSize());
			sct.setEL("maxIdleTimeout", s.getMaxIdleTimeout());
			sct.setEL("maxTextMessageBufferSize", s.getMaxTextMessageBufferSize());
			sct.setEL("requestURI", s.getRequestURI().toASCIIString());

			// asyncRemote
			javax.websocket.RemoteEndpoint.Async as = s.getAsyncRemote();
			if (as != null) {
				Struct sctAs = eng.getCreationUtil().createStruct();
				sct.setEL("asyncRemote", sctAs);
				sctAs.setEL("batchingAllowed", as.getBatchingAllowed());
				sctAs.setEL("sendTimeout", as.getSendTimeout());
			}

			// BasicRemote
			javax.websocket.RemoteEndpoint.Basic br = s.getBasicRemote();
			if (br != null) {
				Struct sctBr = eng.getCreationUtil().createStruct();
				sct.setEL("basicRemote", sctBr);
				sctBr.setEL("batchingAllowed", br.getBatchingAllowed());
			}

			// PathParameters
			Map<String, String> pp = s.getPathParameters();
			if (pp != null && pp.size() > 0) {
				Struct sctPp = eng.getCreationUtil().createStruct();
				sct.setEL("pathParameters", sctPp);
				for (Entry<String, String> e: pp.entrySet()) {
					sctPp.setEL(e.getKey(), e.getValue());
				}
			}

			// RequestParameterMap
			Map<String, List<String>> rpm = s.getRequestParameterMap();
			if (rpm != null && rpm.size() > 0) {
				Struct sctRpm = eng.getCreationUtil().createStruct();
				sct.setEL("requestParameter", sctRpm);
				for (Entry<String, List<String>> e: rpm.entrySet()) {
					sctRpm.setEL(e.getKey(), e.getValue());
				}
			}

			// UserPrincipal
			Principal up = s.getUserPrincipal();
			if (up != null) {
				sct.setEL("userPrincipal", up.getName());
			}

			// UserProperties
			Map<String, Object> up2 = s.getUserProperties();
			if (up2 != null && up2.size() > 0) {
				Struct sctUp = eng.getCreationUtil().createStruct();
				sct.setEL("userProperties", sctUp);
				for (Entry<String, Object> e: up2.entrySet()) {
					sctUp.setEL(e.getKey(), e.getValue());
				}
			}

			if (addRaw) sct.setEL("raw", s);
			return sct;
		}
		catch (IllegalStateException ise) {
			// this happens when a session get closed while reading the data, unlikely because we check "isOpen"
			// before but possible.
		}
		return null;
	}

}
