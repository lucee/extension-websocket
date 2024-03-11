package org.lucee.extension.websocket.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCode;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EncodeException;
import javax.websocket.Session;

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
import lucee.runtime.util.Decision;

public class WSUtil {

	public static final String SERIALIZE_JSON_CLASS = "lucee.runtime.functions.conversion.SerializeJSON";
	public static final String SERIALIZE_CLASS = "lucee.runtime.functions.dynamicEvaluation.Serialize";
	public static final String EVALUATE_CLASS = "lucee.runtime.functions.dynamicEvaluation.Evaluate";
	public static final String DESERIALIZE_JSON_CLASS = "lucee.runtime.functions.conversion.DeserializeJSON";

	public static final Object NULL = new Object();
	public static final String DEFAULT_DIRECTORY = "{lucee-web}/websockets/";

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

	public static ConfigWeb getConfig(ConfigServer cs, Session session) {
		// TODO make it better
		// extract context path
		// print.e("path:" + session.getRequestURI().getPath());
		// print.e("getFragment:" + session.getRequestURI().getFragment());
		// print.e("path:" + session.getRequestURI().getPath());
		String reqURI = session.getRequestURI().toString();
		String reqContextPath = reqURI.substring(0, reqURI.indexOf("/ws"));
		// print.e("reqContextPath:" + reqContextPath);

		// get a matching servletContext
		for (ConfigWeb cw: cs.getConfigWebs()) {
			if (getContextPath(cw.getServletContext()).equals(reqContextPath)) return cw;
			// print.e(getContextPath(cw.getServletContext()) + " == " + reqContextPath);
		}

		return null;
	}

	private static String getContextPath(ServletContext servletContext) {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		try {
			return (String) servletContext.getClass().getMethod("getContextPath", new Class[0]).invoke(servletContext, new Object[0]);
		}
		catch (Exception e) {
			// TODO extract from cw.getServletContext().getRealPath("/"));
			String tmp = eng.getListUtil().last(servletContext.getRealPath("/"), "/", true);
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
	public static boolean send(Session session, Object res) throws PageException {
		if (res == null || res == NULL) return false;
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();
		Decision dec = eng.getDecisionUtil();
		if (session == null || !session.isOpen()) throw eng.getExceptionUtil().createApplicationException("cannot send message, connection to client is closed.");
		try {
			if (dec.isBinary(res)) session.getBasicRemote().sendBinary(ByteBuffer.wrap(cast.toBinary(res)));
			else if (dec.isSimpleValue(res)) session.getBasicRemote().sendText(cast.toString(res));
			else session.getBasicRemote().sendObject(res);
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
				for (Session session: factory.getSessions(cw)) {
					if (session.isOpen()) session.getBasicRemote().sendBinary(data);
				}
			}
			else if (dec.isSimpleValue(res)) {
				String data = cast.toString(res);
				for (Session session: factory.getSessions(cw)) {
					if (session.isOpen()) session.getBasicRemote().sendText(data);
				}
			}
			else {
				for (Session session: factory.getSessions(cw)) {
					if (session.isOpen()) session.getBasicRemote().sendObject(res);
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
			eng.getIOUtil().write(res, "{\n\t\"directory\":\"" + DEFAULT_DIRECTORY + "\"\n}", false, utf8);
		}

		info(pc.getConfig(), "found configuration at  [" + res + "]");

		String content = eng.getIOUtil().toString(res, utf8);

		return new Object[] { res, eng.getCastUtil().toStruct(deserializeJSON(pc, content)) };
	}

	public static CloseReason toCloseReason(Object obj) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();

		if (obj instanceof CloseReason) return (CloseReason) obj;

		// Close Reason as a Struct
		Struct sct = cast.toStruct(obj, null);
		if (sct != null) {
			Object o = sct.get(cast.toKey("reason"), null);
			if (o == null) o = sct.get(cast.toKey("closereason"), null);
			if (o == null) o = sct.get(cast.toKey("close_reason"), null);
			if (o == null) o = sct.get(cast.toKey("message"), null);
			if (o == null) o = sct.get(cast.toKey("text"), null);

			if (o == null) throw eng.getExceptionUtil().createApplicationException("missing key [message] in struct for close reason");
			return new CloseReason(toCloseCode(sct), cast.toString(o));
		}

		// Close Reason as String
		String str = cast.toString(obj, null);
		if (str != null) {
			return new CloseReason(CloseCodes.NORMAL_CLOSURE, str);
		}

		throw eng.getExceptionUtil().createApplicationException(
				"object argument for close must be a string with a close reason message or a struct looking like this [ {message:'close for my own reason',code:'NORMAL_CLOSURE'} ]. The code is optional and by default it is [NORMAL_CLOSURE].");

	}

	private static CloseCode toCloseCode(Struct sct) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();

		Object o = sct.get(cast.toKey("code"), null);
		if (o == null) o = sct.get(cast.toKey("closecode"), null);
		if (o == null) o = sct.get(cast.toKey("close_code"), null);
		if (o == null) return CloseCodes.NORMAL_CLOSURE;

		if (o instanceof CloseCode) return (CloseCode) o;

		String str = cast.toString(o).toUpperCase();
		if ("CANNOT_ACCEPT".equals(str)) return CloseCodes.CANNOT_ACCEPT;
		if ("CLOSED_ABNORMALLY".equals(str)) return CloseCodes.CLOSED_ABNORMALLY;
		if ("GOING_AWAY".equals(str)) return CloseCodes.GOING_AWAY;
		if ("NO_EXTENSION".equals(str)) return CloseCodes.NO_EXTENSION;
		if ("NO_STATUS_CODE".equals(str)) return CloseCodes.NO_STATUS_CODE;
		if ("NORMAL_CLOSURE".equals(str)) return CloseCodes.NORMAL_CLOSURE;
		if ("NORMAL".equals(str)) return CloseCodes.NORMAL_CLOSURE;
		if ("NOT_CONSISTENT".equals(str)) return CloseCodes.NOT_CONSISTENT;
		if ("PROTOCOL_ERROR".equals(str)) return CloseCodes.PROTOCOL_ERROR;
		if ("RESERVED".equals(str)) return CloseCodes.RESERVED;
		if ("SERVICE_RESTART".equals(str)) return CloseCodes.SERVICE_RESTART;
		if ("TLS_HANDSHAKE_FAILURE".equals(str)) return CloseCodes.TLS_HANDSHAKE_FAILURE;
		if ("TOO_BIG".equals(str)) return CloseCodes.TOO_BIG;
		if ("TRY_AGAIN_LATER".equals(str)) return CloseCodes.TRY_AGAIN_LATER;
		if ("UNEXPECTED_CONDITION".equals(str)) return CloseCodes.UNEXPECTED_CONDITION;
		if ("VIOLATED_POLICY".equals(str)) return CloseCodes.VIOLATED_POLICY;

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

	public static PageContext createPageContext(final ConfigWeb cw, final Session session, final String componentName) throws PageException, RuntimeException {
		long timeout = 50 * 1000;// TODO get from outside
		ByteArrayOutputStream baos = new ByteArrayOutputStream(); // TODO get nirvana Stream
		return createPageContext(cw, session, baos, Util.isEmpty(componentName) ? "/" : ("/" + componentName + ".cfc"), session == null ? "" : session.getQueryString(), timeout);
	}

	private static PageContext createPageContext(final ConfigWeb cw, final Session session, OutputStream os, final String path, String qs, long timeout)
			throws PageException, RuntimeException {
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
		if (log != null) log.trace("endpoint-factory", msg);
		else console(msg);
	}

	public static void trace(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (log != null) log.log(Log.LEVEL_TRACE, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	public static void info(Config config, String msg) {
		Log log = getLog(config);
		if (log != null) log.info("endpoint-factory", msg);
		else console(msg);
	}

	public static void info(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (log != null) log.log(Log.LEVEL_INFO, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	public static void warn(Config config, String msg) {
		Log log = getLog(config);
		if (log != null) log.warn("endpoint-factory", msg);
		else console(msg);
	}

	public static void warn(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (log != null) log.log(Log.LEVEL_WARN, "endpoint-factory", msg, t);
		else console(msg, t);
	}

	public static void error(Config config, Exception e) {
		Log log = getLog(config);
		if (log != null) log.error("endpoint-factory", e);
		else console(e);
	}

	public static void error(Config config, String msg, Throwable t) {
		Log log = getLog(config);
		if (log != null) log.log(Log.LEVEL_ERROR, "endpoint-factory", msg, t);
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
		try {
			return config.getLog("websocket");
		}
		catch (Exception e) {
			return config.getLog("application");
		}
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
}
