package org.lucee.extension.websocket;

import java.io.IOException;
import java.util.Date;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.lucee.extension.websocket.client.WSClient;
import org.lucee.extension.websocket.client.WSClients;
import org.lucee.extension.websocket.util.AsyncInvoker;
import org.lucee.extension.websocket.util.GraceStop;
import org.lucee.extension.websocket.util.ServletAwareConfig;
import org.lucee.extension.websocket.util.WSUtil;

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

//ServerEndpoint(value="/endpoint", encoders = MessageEncoder.class, decoders= MessageDecoder.class)

@ServerEndpoint(value = "/ws/{component-name}", configurator = ServletAwareConfig.class)
public class WebSocketEndpoint {
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

	static {
		GRACE_PERIOD = CFMLEngineFactory.getInstance().getCastUtil().toKey("graceperiod");
	}

	// called by the Servelt engine
	public WebSocketEndpoint() {
		factory = WebSocketEndpointFactory.getInstance();
		startTime = new Date();

		// TODO check log level (don't do the work when not needed)
		Config config = CFMLEngineFactory.getInstance().getThreadConfig();
		if (config instanceof ConfigWeb) {
			ConfigWeb cw = (ConfigWeb) config;
			try {
				PageContext pc = WSUtil.createPageContext(cw, null, null);
				mapping = factory.getComponentMapping(pc);

			}
			catch (Exception e) {
				WSUtil.error(config, e);
			}
		}
		else if (config != null) WSUtil.trace(config, "init WebSocketEndpoint");
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig config, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		this.cw = WSUtil.getConfig(factory.getConfigServer(), session);

		synchronized (factory) {
			factory.setSessions(cw, session);
			WSUtil.trace(cw,
					"onOpen got involved for component [" + componentName + "], current session size [" + factory.getSessions(cw).size() + "], session size 1==first open");
			// onFirstOpen
			if (factory.getSessions(cw).size() == 1) {
				PageContext pc = WSUtil.createPageContext(cw, session, componentName);
				try {
					UDF ofo = getStaticFunction(pc, componentName, factory.ON_FIRST_OPEN);
					if (ofo != null) {
						WSUtil.trace(cw, "calling [onFirstOpen] function for component [" + componentName + "]");
						gracePeriodFirstOpen = CFMLEngineFactory.getInstance().getCastUtil().toIntValue(ofo.getMetaData(pc).get(GRACE_PERIOD, null), DEFAULT_GRACE_PERIOD);

						if (firstOpen == null || !firstOpen.isAlive()) {
							firstOpen = new AsyncInvoker(this, session, componentName, factory.ON_FIRST_OPEN, true, new Object[] { new WSClients(factory) });
							firstOpen.start();
						}
					}
					else {
						WSUtil.trace(cw, "no [onFirstOpen] function found for component [" + componentName + "]");
					}
				}
				catch (PageException | IOException | EncodeException e) {
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

		PageContext pc = WSUtil.createPageContext(cw, session, componentName);
		try {
			// onOpenAsync
			UDF ooa = getFunction(pc, componentName, factory.ON_OPEN_ASYNC);
			if (ooa != null) {
				WSUtil.trace(cw, "calling [onOpenAsync] function for component [" + componentName + "]");
				gracePeriodOpenAsync = CFMLEngineFactory.getInstance().getCastUtil().toIntValue(ooa.getMetaData(pc).get(GRACE_PERIOD, null), DEFAULT_GRACE_PERIOD);

				openAsync = new AsyncInvoker(this, session, componentName, factory.ON_OPEN_ASYNC, false, new Object[] { new WSClient(factory, session) });
				openAsync.start();
			}
			else {
				WSUtil.trace(cw, "no [onOpenAsync] function found for component [" + componentName + "]");
			}
			// onOpen
			WSUtil.trace(cw, "calling [onOpen] function for component [" + componentName + "]");
			Object res = invoke(pc, componentName, factory.ON_OPEN, new Object[] { new WSClient(factory, session) }, WSUtil.NULL);
			if (res == WSUtil.NULL) WSUtil.trace(cw, "no [onOpen] function for component [" + componentName + "]");
			else WSUtil.trace(cw, "called [onOpen] function for component [" + componentName + "]");
			WSUtil.send(session, res);
		}
		catch (PageException | IOException | EncodeException e) {
			WSUtil.error(cw, e);
			throw e;
		}
		finally {
			WSUtil.releasePageContext(pc);
		}
	}

	// https://docs.oracle.com/javaee/7/api/javax/websocket/OnMessage.html
	@OnMessage
	public String onMessage(Session session, String message, boolean last, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		WSUtil.trace(cw, "onMessage got involved for component [" + componentName + "] with session id [" + session.getId() + "]");
		if (!last) {
			WSUtil.trace(cw, "buffering message part for onMessage call involved for component [" + componentName + "] with session id [" + session.getId() + "], new part size: ["
					+ message.length() + "], total buffered size: [" + (message.length() + (messageBuffer == null ? 0 : messageBuffer.length())) + "].");
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

	private String onMessage(Session session, String message, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		PageContext pc = WSUtil.createPageContext(cw, session, componentName);
		try {
			WSUtil.trace(cw, "calling [onMessage] for component [" + componentName + "] with session id [" + session.getId() + "], message size: [" + message.length() + "].");

			Object res = session.isOpen() ? invoke(pc, componentName, factory.ON_MESSAGE, new Object[] { new WSClient(factory, session), message }, WSUtil.NULL) : null;
			// session is closed
			if (!session.isOpen()) {
				WSUtil.warn(cw, "session [" + session.getId() + "], is no longer open.");
				return null;
			}
			// listener function did return null
			else if (res == null) {
				WSUtil.trace(cw, "called [onMessage] for component [" + componentName + "] with session id [" + session.getId() + "], message size: [" + message.length()
						+ "] with no return value provided.");
				return null;
			}
			// no listener function
			else if (res == WSUtil.NULL) {
				WSUtil.trace(cw, "no [onMessage] for component [" + componentName + "] with session id [" + session.getId() + "], message size: [" + message.length() + "].");
				return null;
			}
			WSUtil.trace(cw, "called [onMessage] for component [" + componentName + "] with session id [" + session.getId() + "], message size: [" + message.length()
					+ "], got a return value.");

			return CFMLEngineFactory.getInstance().getCastUtil().toString(res); // TODO could we use send from above instead?
		}
		catch (PageException | IOException | EncodeException e) {
			WSUtil.error(cw, e);
			throw e;
		}
		finally {
			WSUtil.releasePageContext(pc);
		}
	}

	// public byte[] onMessage(Session session, byte[] message, boolean last,
	// @PathParam("component-name") String componentName) throws PageException, IOException,
	// EncodeException {

	@OnError
	public void onError(Session session, Throwable t, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		WSUtil.trace(cw, "onError got involved for component [" + componentName + "] with session id [" + session.getId() + "]", t);

		PageContext pc = WSUtil.createPageContext(cw, session, componentName);
		Struct cb = WSUtil.toCatchBlock(pc.getConfig(), t);
		try {
			if (session.isOpen()) {
				WSUtil.trace(cw, "calling [onError] for component [" + componentName + "] with session id [" + session.getId() + "].");
				Object res = invoke(pc, componentName, factory.ON_ERROR, new Object[] { new WSClient(factory, session), cb }, WSUtil.NULL);
				if (session.isOpen()) { // could be that the session was closed in invoke above
					// TODO wrap Exception in a catch block
					if (res != WSUtil.NULL) {
						WSUtil.trace(cw, "called [onError] for component [" + componentName + "] with session id [" + session.getId() + "].");
						WSUtil.send(session, res);
					}
					else {
						WSUtil.trace(cw, "no [onError] for component [" + componentName + "] with session id [" + session.getId() + "].");
						WSUtil.send(session, WSUtil.serializeJSON(pc, cb, false));
					}
				}
			}
		}
		catch (Exception e) {
			WSUtil.error(cw, e);
			Struct ncb = WSUtil.toCatchBlock(pc.getConfig(), e);
			ncb.setEL("origin", cb);
			WSUtil.send(session, WSUtil.serializeJSON(pc, ncb, false));
		}
		finally {
			WSUtil.releasePageContext(pc);
		}

	}

	@OnClose
	public void onClose(Session session, CloseReason reason, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		WSUtil.trace(cw, "onClose got involved for component [" + componentName + "], current session size [" + factory.getSessions(cw).size() + "], session size 0==last close"); // TODO
		// is
		// thqt
		// correct?
		// onClose
		{
			PageContext pc = WSUtil.createPageContext(cw, session, componentName);
			try {
				WSUtil.trace(cw, "calling [onClose] for component [" + componentName + "] with session id [" + session.getId() + "].");
				Object res = invoke(pc, componentName, factory.ON_CLOSE, new Object[] { new WSClient(factory, session), reason.getReasonPhrase() }, WSUtil.NULL);

				if (res != WSUtil.NULL) {
					WSUtil.trace(cw, "called [onClose] for component [" + componentName + "] with session id [" + session.getId() + "].");
				}
				else {
					WSUtil.trace(cw, "no [onClose] for component [" + componentName + "] with session id [" + session.getId() + "].");
				}
			}
			catch (PageException | IOException | EncodeException e) {
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
				WSUtil.trace(cw, "calling [onLastClose] function for component [" + componentName + "]");
				PageContext pc = WSUtil.createPageContext(cw, session, componentName);
				try {
					UDF olc = getStaticFunction(pc, componentName, factory.ON_LAST_CLOSE);
					if (olc != null) {
						lastClose = new AsyncInvoker(this, session, componentName, factory.ON_LAST_CLOSE, true, new Object[] {});
						lastClose.start();
						WSUtil.trace(cw, "async triggered [onLastClose] for component [" + componentName + "].");
					}
					else {
						WSUtil.trace(cw, "no STATIC function [onLastClose] for component [" + componentName + "].");
					}
				}
				catch (PageException | IOException | EncodeException e) {
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

	/*
	 * private void dump(Session session) { print.e("RequestURI:" + session.getRequestURI());
	 * print.e("ID:" + session.getId()); print.e("MaxBinaryMessageBufferSize:" +
	 * session.getMaxBinaryMessageBufferSize()); print.e("MaxIdleTimeout:" +
	 * session.getMaxIdleTimeout()); print.e("NegotiatedSubprotocol:" +
	 * session.getNegotiatedSubprotocol()); print.e("ProtocolVersion:" + session.getProtocolVersion());
	 * print.e("QueryString:" + session.getQueryString()); print.e("UserPrincipal:" +
	 * session.getUserPrincipal()); print.e(session.getPathParameters());
	 * print.e(session.getRequestParameterMap()); print.e(session.getUserProperties()); }
	 */

	private Component getCFC(PageContext pc, String componentName) throws PageException, IOException, EncodeException {
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

	private UDF getFunction(PageContext pc, String componentName, Collection.Key udfName) throws PageException, IOException, EncodeException {
		Object obj = getCFC(pc, componentName).get(udfName, null);
		if (obj instanceof UDF) return (UDF) obj;
		return null;

	}

	private UDF getStaticFunction(PageContext pc, String componentName, Collection.Key udfName) throws PageException, IOException, EncodeException {
		Object obj = getCFC(pc, componentName).staticScope().get(udfName, null);
		if (obj instanceof UDF) return (UDF) obj;
		return null;
	}

	public Object invoke(PageContext pc, String componentName, Collection.Key udfName, Object[] args, Object rtnIfNoUDF) throws PageException, IOException, EncodeException {
		getCFC(pc, componentName);
		// has function
		if (cfc.get(udfName, null) instanceof UDF) {
			return cfc.call(pc, udfName, args);
		}
		return rtnIfNoUDF;
	}

	public Object invokeStatic(PageContext pc, String componentName, Collection.Key udfName, Object[] args, Object rtnIfNoUDF) throws PageException, IOException, EncodeException {
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
}
