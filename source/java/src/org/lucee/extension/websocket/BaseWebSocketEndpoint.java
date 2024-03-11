package org.lucee.extension.websocket;

import java.io.IOException;
import java.util.Date;

import org.lucee.extension.websocket.client.WSClient;
import org.lucee.extension.websocket.client.WSClients;
import org.lucee.extension.websocket.util.AsyncInvoker;
import org.lucee.extension.websocket.util.GraceStop;
import org.lucee.extension.websocket.util.WSUtil;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.Component;
import lucee.runtime.Mapping;
import lucee.runtime.PageContext;
import lucee.runtime.PageSource;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;

public class BaseWebSocketEndpoint {
	private static final Collection.Key GRACE_PERIOD;
	private static final int DEFAULT_GRACE_PERIOD = 5;
	private final Date startTime;
	private final WebSocketEndpointFactory factory;
	private ConfigWeb cw;
	private Component cfc;
	private Mapping mapping;
	private static AsyncInvoker firstOpen;
	private static AsyncInvoker lastClose;
	private AsyncInvoker openAsync;
	private int gracePeriodOpenAsync;
	private int gracePeriodFirstOpen;
	private StringBuilder messageBuffer;
	private static Object newerVersion;

	static {
		GRACE_PERIOD = CFMLEngineFactory.getInstance().getCastUtil().toKey("graceperiod");
	}

	// called by the Servelt engine
	public BaseWebSocketEndpoint() {
		factory = WebSocketEndpointFactory.getInstance();
		startTime = new Date();

		Config config = CFMLEngineFactory.getInstance().getThreadConfig();
		if (config instanceof ConfigWeb) {
			ConfigWeb cw = (ConfigWeb) config;
			try {
				PageContext pc = WSUtil.createPageContext(factory, cw, null, null);
				mapping = factory.getComponentMapping(pc);

			}
			catch (Exception e) {
				WSUtil.error(config, e);
			}
		}
		else if (config != null) WSUtil.info(config, "init WebSocketEndpoint");
	}

	public static void inject(Object nv) {
		newerVersion = nv;
	}

	private static Object on(ConfigWeb cw, String methodName, Object... args) throws PageException, IOException {
		WSUtil.warn(cw, "calling [" + methodName + "] via reflection, Lucee restart needed!");
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		return eng.getClassUtil().callMethod(newerVersion, eng.getCreationUtil().createKey(methodName), args);
	}

	public void onOpen(Object session, Object endpointConfig, String componentName) throws PageException, IOException {
		// in case we have a newer version injected, we use that newver version
		if (newerVersion != null) {
			on(WSUtil.getConfig(factory.getConfigServer(), session), "onOpen", session, endpointConfig, componentName);
			return;
		}

		this.cw = WSUtil.getConfig(factory.getConfigServer(), session);

		synchronized (factory) {
			factory.setSessions(cw, session);
			WSUtil.info(cw, "onOpen got involved for component [" + componentName + "], current session size [" + factory.getSessions(cw).size() + "], session size 1==first open");
			// onFirstOpen
			if (factory.getSessions(cw).size() == 1) {
				PageContext pc = WSUtil.createPageContext(factory, cw, session, componentName);
				try {
					UDF ofo = getStaticFunction(pc, componentName, factory.ON_FIRST_OPEN);
					if (ofo != null) {
						WSUtil.info(cw, "calling [onFirstOpen] function for component [" + componentName + "]");
						gracePeriodFirstOpen = CFMLEngineFactory.getInstance().getCastUtil().toIntValue(ofo.getMetaData(pc).get(GRACE_PERIOD, null), DEFAULT_GRACE_PERIOD);

						if (firstOpen == null || !firstOpen.isAlive()) {
							firstOpen = new AsyncInvoker(this, session, componentName, factory.ON_FIRST_OPEN, true, new Object[] { new WSClients(factory) });
							firstOpen.start();
						}
					}
					else {
						WSUtil.info(cw, "no [onFirstOpen] function found for component [" + componentName + "]");
					}
				}
				catch (PageException | IOException e) {
					WSUtil.error(cw, e);
					throw e;
				}
				finally {
					WSUtil.releasePageContext(pc);
				}
			}
		}

		// print.e("onOpen (" + session.getId() + "-" + startTime + "-" + factory.sessions.size() + "):" +
		// componentName);

		PageContext pc = WSUtil.createPageContext(factory, cw, session, componentName);
		try {
			// onOpenAsync
			UDF ooa = getFunction(pc, componentName, factory.ON_OPEN_ASYNC);
			if (ooa != null) {
				WSUtil.info(cw, "calling [onOpenAsync] function for component [" + componentName + "]");
				gracePeriodOpenAsync = CFMLEngineFactory.getInstance().getCastUtil().toIntValue(ooa.getMetaData(pc).get(GRACE_PERIOD, null), DEFAULT_GRACE_PERIOD);

				openAsync = new AsyncInvoker(this, session, componentName, factory.ON_OPEN_ASYNC, false, new Object[] { new WSClient(factory, session) });
				openAsync.start();
			}
			else {
				WSUtil.info(cw, "no [onOpenAsync] function found for component [" + componentName + "]");
			}
			// onOpen
			WSUtil.info(cw, "calling [onOpen] function for component [" + componentName + "]");
			Object res = invoke(pc, componentName, factory.ON_OPEN, new Object[] { new WSClient(factory, session) }, WSUtil.NULL);
			if (res == WSUtil.NULL) WSUtil.info(cw, "no [onOpen] function for component [" + componentName + "]");
			else WSUtil.info(cw, "called [onOpen] function for component [" + componentName + "]");
			WSUtil.send(cw, session, res);
		}
		catch (PageException | IOException e) {
			WSUtil.error(cw, e);
			throw e;
		}
		finally {
			WSUtil.releasePageContext(pc);
		}
	}

	public String onMessage(Object session, String message, boolean last, String componentName) throws PageException, IOException {
		// in case we have a newer version injected, we use that newver version
		if (newerVersion != null) {
			return CFMLEngineFactory.getInstance().getCastUtil()
					.toString(on(WSUtil.getConfig(factory.getConfigServer(), session), "onMessage", session, message, last, componentName));
		}

		WSUtil.info(cw, "onMessage got involved for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "]");
		if (!last) {
			WSUtil.info(cw, "buffering message part for onMessage call involved for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session)
					+ "], new part size: [" + message.length() + "], total buffered size: [" + (message.length() + (messageBuffer == null ? 0 : messageBuffer.length())) + "].");
			if (messageBuffer == null) messageBuffer = new StringBuilder();
			messageBuffer.append(message);
			return null;
		}
		else if (messageBuffer != null) {
			message = messageBuffer.append(message).toString();
			messageBuffer = new StringBuilder();
		}

		return onMessage(session, message, componentName);
	}

	private String onMessage(Object session, String message, String componentName) throws PageException, IOException {
		PageContext pc = WSUtil.createPageContext(factory, cw, session, componentName);
		try {
			WSUtil.info(cw,
					"calling [onMessage] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "], message size: [" + message.length() + "].");

			Object res = WSUtil.isOpen(cw, session) ? invoke(pc, componentName, factory.ON_MESSAGE, new Object[] { new WSClient(factory, session), message }, WSUtil.NULL) : null;
			// session is closed
			if (!WSUtil.isOpen(cw, session)) {
				WSUtil.warn(cw, "session [" + WSUtil.getId(cw, session) + "], is no longer open.");
				return null;
			}
			// listener function did return null
			else if (res == null) {
				WSUtil.info(cw, "called [onMessage] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "], message size: [" + message.length()
						+ "] with no return value provided.");
				return null;
			}
			// no listener function
			else if (res == WSUtil.NULL) {
				WSUtil.info(cw,
						"no [onMessage] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "], message size: [" + message.length() + "].");
				return null;
			}
			WSUtil.info(cw, "called [onMessage] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "], message size: [" + message.length()
					+ "], got a return value.");

			return CFMLEngineFactory.getInstance().getCastUtil().toString(res); // TODO could we use send from above instead?
		}
		catch (PageException | IOException e) {
			WSUtil.error(cw, e);
			throw e;
		}
		finally {
			WSUtil.releasePageContext(pc);
		}
	}

	public void onError(Object session, Throwable t, String componentName) throws PageException, IOException {
		// in case we have a newer version injected, we use that newver version
		if (newerVersion != null) {
			on(WSUtil.getConfig(factory.getConfigServer(), session), "onError", session, t, componentName);
			return;
		}

		WSUtil.info(cw, "onError got involved for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "]", t);

		PageContext pc = WSUtil.createPageContext(factory, cw, session, componentName);
		Struct cb = WSUtil.toCatchBlock(pc.getConfig(), t);
		try {
			if (WSUtil.isOpen(cw, session)) {
				WSUtil.info(cw, "calling [onError] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "].");
				Object res = invoke(pc, componentName, factory.ON_ERROR, new Object[] { new WSClient(factory, session), cb }, WSUtil.NULL);
				if (WSUtil.isOpen(cw, session)) { // could be that the session was closed in invoke above
					if (res != WSUtil.NULL) {
						WSUtil.info(cw, "called [onError] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "].");
						WSUtil.send(cw, session, res);
					}
					else {
						WSUtil.info(cw, "no [onError] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "].");
						WSUtil.send(cw, session, WSUtil.serializeJSON(pc, cb, false));
					}
				}
			}
		}
		catch (Exception e) {
			WSUtil.error(cw, e);
			Struct ncb = WSUtil.toCatchBlock(pc.getConfig(), e);
			ncb.setEL("origin", cb);
			WSUtil.send(cw, session, WSUtil.serializeJSON(pc, ncb, false));
		}
		finally {
			WSUtil.releasePageContext(pc);
		}

	}

	public void onClose(Object session, Object closeReason, String componentName) throws PageException, IOException {
		// in case we have a newer version injected, we use that newver version
		if (newerVersion != null) {
			on(WSUtil.getConfig(factory.getConfigServer(), session), "onClose", session, closeReason, componentName);
			return;
		}

		WSUtil.info(cw, "onClose got involved for component [" + componentName + "], current session size [" + factory.getSessions(cw).size() + "], session size 0==last close");
		{
			PageContext pc = WSUtil.createPageContext(factory, cw, session, componentName);
			try {
				WSUtil.info(cw, "calling [onClose] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "].");
				Object res = invoke(pc, componentName, factory.ON_CLOSE, new Object[] { new WSClient(factory, session), WSUtil.getReasonPhrase(cw, closeReason) }, WSUtil.NULL);

				if (res != WSUtil.NULL) {
					WSUtil.info(cw, "called [onClose] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "].");
				}
				else {
					WSUtil.info(cw, "no [onClose] for component [" + componentName + "] with session id [" + WSUtil.getId(cw, session) + "].");
				}
			}
			catch (PageException | IOException e) {
				WSUtil.error(cw, e);
				throw e;
			}
			finally {
				WSUtil.releasePageContext(pc);
			}
		}
		// close openAsync
		if (this.openAsync != null && openAsync.isAlive()) {
			new GraceStop(openAsync, gracePeriodOpenAsync).start();
		}

		// onLastClose
		synchronized (factory) {
			factory.remSessions(cw, session);
			// factory.sessions.remove(session.getId());

			// onLastClose
			if (factory.getSessions(cw).size() == 0) {
				WSUtil.info(cw, "calling [onLastClose] function for component [" + componentName + "]");
				PageContext pc = WSUtil.createPageContext(factory, cw, session, componentName);
				try {
					UDF olc = getStaticFunction(pc, componentName, factory.ON_LAST_CLOSE);
					if (olc != null) {
						lastClose = new AsyncInvoker(this, session, componentName, factory.ON_LAST_CLOSE, true, new Object[] {});
						lastClose.start();
						WSUtil.info(cw, "async triggered [onLastClose] for component [" + componentName + "].");
					}
					else {
						WSUtil.info(cw, "no STATIC function [onLastClose] for component [" + componentName + "].");
					}
				}
				catch (PageException | IOException e) {
					WSUtil.error(cw, e);
					throw e;
				}
				finally {
					WSUtil.releasePageContext(pc);
				}

				// close onFirstOpen
				if (firstOpen != null && firstOpen.isAlive()) {
					new GraceStop(firstOpen, gracePeriodFirstOpen).start();
				}
			}
		}
	}

	private Component getCFC(PageContext pc, String componentName) throws PageException, IOException {
		if (mapping == null) {
			mapping = factory.getComponentMapping(pc);
			WSUtil.info(cw, "directory used is [" + mapping.getPhysical() + " - " + mapping.toString() + "]");

		}
		if (cfc == null) {
			PageSource ps = mapping.getPageSource(componentName + ".cfc");
			this.cfc = WSUtil.loadComponent(pc, ps, "/" + componentName + ".cfc", false, false, true);
		}
		return cfc;
	}

	private UDF getFunction(PageContext pc, String componentName, Collection.Key udfName) throws PageException, IOException {
		Object obj = getCFC(pc, componentName).get(udfName, null);
		if (obj instanceof UDF) return (UDF) obj;
		return null;

	}

	private UDF getStaticFunction(PageContext pc, String componentName, Collection.Key udfName) throws PageException, IOException {
		Object obj = getCFC(pc, componentName).staticScope().get(udfName, null);
		if (obj instanceof UDF) return (UDF) obj;
		return null;
	}

	public Object invoke(PageContext pc, String componentName, Collection.Key udfName, Object[] args, Object rtnIfNoUDF) throws PageException, IOException {
		getCFC(pc, componentName);
		// has function
		if (cfc.get(udfName, null) instanceof UDF) {
			return cfc.call(pc, udfName, args);
		}
		return rtnIfNoUDF;
	}

	public Object invokeStatic(PageContext pc, String componentName, Collection.Key udfName, Object[] args, Object rtnIfNoUDF) throws PageException, IOException {
		getCFC(pc, componentName);
		// has function
		if (cfc.staticScope().get(udfName, null) instanceof UDF) {
			return cfc.staticScope().call(pc, udfName, args);
		}
		return rtnIfNoUDF;
	}

	public ConfigWeb getConfig() {
		return cw;
	}

	public WebSocketEndpointFactory getFactory() {
		return factory;
	}
}
