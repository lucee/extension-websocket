package org.lucee.extension.websocket;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.DeploymentException;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;

import org.lucee.extension.websocket.util.WSUtil;
import org.lucee.extension.websocket.util.print;

import lucee.commons.io.log.Log;
import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.Mapping;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigServer;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Creation;

public class WebSocketEndpointFactory {
	private final ConfigServer cs;
	private final CFMLEngine eng;
	private static WebSocketEndpointFactory instance;
	// private Map<String, ConfigWeb> configs = new HashMap<>();

	public final Collection.Key ON_OPEN;
	public final Collection.Key ON_OPEN_ASYNC;
	public final Collection.Key ON_FIRST_OPEN;
	public final Collection.Key ON_LAST_CLOSE;
	public final Collection.Key ON_MESSAGE;
	public final Collection.Key ON_ERROR;
	public final Collection.Key ON_CLOSE;
	private boolean alive = true;

	//
	private Map<String, Data> datas = new ConcurrentHashMap<>();
	private boolean isEndpointRegistered = false;
	private Object token = new Object();

	private static class Data {
		public long timeout = 50 * 1000;
		private ConfigWeb config;
		private Map<String, Session> sessions = new ConcurrentHashMap<>();
		public Mapping mapping;
		public Struct configuration;
		public Resource configFile;

		public Data(ConfigWeb cw) {
			this.config = cw;
		}

		public Struct getInfo(boolean addRaw) {
			CFMLEngine eng = CFMLEngineFactory.getInstance();

			Struct result = eng.getCreationUtil().createStruct();

			// component path
			Resource p = mapping.getPhysical();
			if (p != null) result.setEL("mapping", p.getAbsolutePath());
			else {
				Resource a = mapping.getArchive();
				if (a != null) result.setEL("mapping", a.getAbsolutePath());
			}

			// configuration
			result.setEL("config", configuration);
			result.setEL("configFile", configFile == null ? null : configFile.getAbsolutePath());

			// log
			result.setEL("log", WSUtil.getLogName(config));

			// sessions
			Array arrSessions = eng.getCreationUtil().createArray();
			result.setEL("sessions", arrSessions);
			for (Session s: sessions.values()) {
				Struct sct = eng.getCreationUtil().createStruct();
				arrSessions.appendEL(sct);
				sct.setEL("id", s.getId());
				sct.setEL("negotiatedSubprotocol", s.getNegotiatedSubprotocol());
				sct.setEL("protocolVersion", s.getProtocolVersion());
				sct.setEL("queryString", s.getQueryString());
				sct.setEL("maxBinaryMessageBufferSize", s.getMaxBinaryMessageBufferSize());
				sct.setEL("maxIdleTimeout", s.getMaxIdleTimeout());
				sct.setEL("maxTextMessageBufferSize", s.getMaxTextMessageBufferSize());
				sct.setEL("requestURI", s.getRequestURI().toASCIIString());

				// asyncRemote
				Async as = s.getAsyncRemote();
				if (as != null) {
					Struct sctAs = eng.getCreationUtil().createStruct();
					sct.setEL("asyncRemote", sctAs);
					sctAs.setEL("batchingAllowed", as.getBatchingAllowed());
					sctAs.setEL("sendTimeout", as.getSendTimeout());
				}

				// BasicRemote
				Basic br = s.getBasicRemote();
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
			}

			return result;
		}

	}

	public WebSocketEndpointFactory(Config config) {
		eng = CFMLEngineFactory.getInstance();
		Creation creator = eng.getCreationUtil();

		ON_OPEN = creator.createKey("onOpen");
		ON_OPEN_ASYNC = creator.createKey("onOpenAsync");
		ON_FIRST_OPEN = creator.createKey("onFirstOpen");
		ON_LAST_CLOSE = creator.createKey("onLastClose");
		ON_MESSAGE = creator.createKey("onMessage");
		ON_ERROR = creator.createKey("onError");
		ON_CLOSE = creator.createKey("onClose");

		cs = (ConfigServer) config;

		new Registrar(this, config).start();
		// register();

		instance = this;
	}

	private void register() {
		for (ConfigWeb cw: cs.getConfigWebs()) {
			try {
				register(cw);
			}
			catch (Exception e) {
				WSUtil.error(cw, e);
			}
		}
	}

	private Data register(ConfigWeb cw) throws PageException {
		Data data = datas.get(cw.getIdentification().getId());
		if (data != null) return data;

		datas.put(cw.getIdentification().getId(), data = new Data(cw));
		javax.websocket.server.ServerContainer serverContainer = (javax.websocket.server.ServerContainer) cw.getServletContext()
				.getAttribute("javax.websocket.server.ServerContainer");

		// WSUtil.info(cs, "register web context [" + cw.getIdentification().getId() + " - " +
		// cw.getServletContext().getRealPath("/") + "]");
		// WSUtil.info(cw, "register web context [" + cw.getIdentification().getId() + " - " +
		// cw.getServletContext().getRealPath("/") + "]");
		try {
			if (WSUtil.hasLogLevel(cw, Log.LEVEL_INFO)) {
				PageContext pc = WSUtil.createPageContext(this, cw, null, null);
				data.mapping = getComponentMapping(pc);
				String msg = "register web context  [" + cw.getIdentification().getId() + " - " + cw.getServletContext().getRealPath("/")
						+ "] mapping defined in the configuration is [" + data.mapping.getPhysical() + "]";
				WSUtil.info(cs, msg);
				WSUtil.info(cw, msg);
			}
		}
		catch (Exception e) {
			WSUtil.error(cs, e);
			WSUtil.error(cw, e);
		}

		if (!isEndpointRegistered) {
			synchronized (token) {
				if (!isEndpointRegistered) {
					Properties props = System.getProperties();
					Object endpoint = props.get("lucee.websocket.endpoint");

					// update
					if (endpoint instanceof Class) {
						String msg = "update existing WebSocketEndpoint with via injection";
						WSUtil.info(cs, msg);
						WSUtil.info(cw, msg);

						CFMLEngine eng = CFMLEngineFactory.getInstance();
						try {
							eng.getClassUtil().callStaticMethod((Class) endpoint, "inject", new Object[] { new WebSocketEndpoint() });
						}
						catch (PageException e) {
							print.e(e);
							throw e;
						}
					}
					// add
					else {
						String msg = "register WebSocketEndpoint with servlet container";
						WSUtil.info(cs, msg);
						WSUtil.info(cw, msg);
						try {
							props.put("lucee.websocket.endpoint", WebSocketEndpoint.class);
							serverContainer.addEndpoint(WebSocketEndpoint.class);
						}
						catch (DeploymentException e) {
							// some container are not able/do not allow to update the endpoint, but this is needed when the
							// extension updates, so we inject us in the old extension version
							throw eng.getCastUtil().toPageException(e);
						}
					}
					isEndpointRegistered = true;
				}

			}

		}

		// inject(Object nv)

		return data;
	}

	public Struct getInfo(ConfigWeb config, boolean addRaw) throws PageException {
		return register(config).getInfo(addRaw);
	}

	public static WebSocketEndpointFactory getInstance() {
		return instance;
	}

	public boolean isAlive() {
		return alive;
	}

	public ConfigServer getConfigServer() {
		return cs;
	}

	public Mapping getComponentMapping(PageContext pc) throws PageException, IOException {
		ConfigWeb cw = pc.getConfig();
		Data data = register(cw);
		if (data.mapping != null) {
			return data.mapping;
		}
		Object[] arr = WSUtil.readConfig(pc);
		data.configFile = (Resource) arr[0];
		data.configuration = (Struct) arr[1];

		// directory
		String path = eng.getCastUtil().toString(data.configuration.get("directory", null), null);
		if (eng.getStringUtil().isEmpty(path, true)) {
			WSUtil.info(pc.getConfig(), "no [directory] setting found in configuration, using default location [{lucee-web}/websockets/]");
			path = WSUtil.DEFAULT_DIRECTORY;
			if (data.configuration == null) data.configuration = eng.getCreationUtil().createStruct();
			data.configuration.setEL("directory", path);
		}
		else {
			WSUtil.info(pc.getConfig(), "found [directory] setting in configuration, using [" + path + "]");
		}
		data.mapping = WSUtil.createMapping(pc, path);

		WSUtil.info(cw, "init WebSocketEndpoint for web context [" + cw.getIdentification().getId() + " - " + cw.getServletContext().getRealPath("/")
				+ "] mapping defined in the configuration is [" + path + "], this is resolved to [" + data.mapping.getPhysical() + "]");

		// timeout
		long timeout = eng.getCastUtil().toLongValue(data.configuration.get("timeout", null), 0);
		if (timeout > 0L) data.timeout = timeout;

		return data.mapping;
	}

	private static class Registrar extends Thread {
		private WebSocketEndpointFactory factory;
		private boolean alive = true;
		private Config config;

		public Registrar(WebSocketEndpointFactory factory, Config config) {
			this.factory = factory;
			this.config = config;
		}

		@Override
		public void run() {
			int sleepTime = 500;
			int count = 0;
			while (alive && factory.isAlive()) {
				count++;
				if (count == 5) sleepTime = 1000; // after 2.5 seconds we increase to a 1 second intervall
				if (count == 20) sleepTime = 10000; // after 10 seconds we increase to a 10 second intervall
				else if (count == 30) sleepTime = 60000; // after an other 100 seconds we increase to a minute intervall
				try {
					factory.register();

				}
				catch (Exception e) {
				}
				try {
					WSUtil.trace(config, "checking for new web context to register, current interval [" + sleepTime + "ms]");
					sleep(sleepTime);

				}
				catch (InterruptedException e) {
					WSUtil.warn(config, "sleep for register got interupted");
					break;
				}
			}
		}

		@Override
		public void finalize() {
			alive = false;
		}
	}

	public java.util.Collection<Session> getSessions(ConfigWeb config) throws PageException {
		return register(config).sessions.values();
	}

	public void setSessions(ConfigWeb config, Session session) throws PageException {
		register(config).sessions.put(session.getId(), session);
	}

	public void remSessions(ConfigWeb config, Session session) throws PageException {
		register(config).sessions.remove(session.getId());
	}

	public long getTimeout(ConfigWeb cw) throws PageException {
		return register(cw).timeout;
	}
}
