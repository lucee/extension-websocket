package org.lucee.extension.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.lucee.extension.websocket.BaseWebSocketEndpoint.Instance;
import org.lucee.extension.websocket.util.WSUtil;
import org.lucee.extension.websocket.util.print;
import org.osgi.framework.Bundle;

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

	private static final long DEFAULT_REQUEST_TIMEOUT = 50 * 1000;
	private static final long DEFAULT_IDLE_TIMEOUT = 300 * 1000;

	private static final Class<?> JAKARTA_ENDPOINT_CLASS = JakartaWebSocketEndpoint.class;
	private static final Class<?> JAVAX_ENDPOINT_CLASS = JavaxWebSocketEndpoint.class;
	private final ConfigServer cs;
	private final CFMLEngine eng;
	private static WebSocketEndpointFactory instance = null;
	private static RuntimeException re;
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

	public WebSocketEndpointFactory(Config config) {
		try {
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
		catch (RuntimeException re) {
			this.re = re;
			throw re;
		}
	}

	private void register() {
		for (ConfigWeb cw: cs.getConfigWebs()) {
			try {
				// Skip CLI servlet contexts
				if (cw != null && !WSUtil.isCliServletContext(cw)) {
					register(cw);
				}
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

		try {
			if (WSUtil.hasLogLevel(cw, Log.LEVEL_INFO) || data.mapping == null) {
				PageContext pc = WSUtil.createPageContext(this, cw, null, null);
				data.mapping = getComponentMapping(pc);
				String msg = "register web context  [" + cw.getIdentification().getId() + " - " + WSUtil.getServletContextRealPath(cw, "/")
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
							if (WSUtil.getContainerType(cw) == WSUtil.TYPE_JAKARTA)
								eng.getClassUtil().callStaticMethod((Class) endpoint, "inject", new Object[] { new JakartaWebSocketEndpoint() });
							else if (WSUtil.getContainerType(cw) == WSUtil.TYPE_JAVAX)
								eng.getClassUtil().callStaticMethod((Class) endpoint, "inject", new Object[] { new JavaxWebSocketEndpoint() });
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

							Object oServerContainer = WSUtil.getServletContextAttribute(cw,
									(WSUtil.getContainerType(cw) == WSUtil.TYPE_JAKARTA ? "jakarta" : "javax") + ".websocket.server.ServerContainer");

							if (WSUtil.getContainerType(cw) == WSUtil.TYPE_JAKARTA) {
								props.put("lucee.websocket.endpoint", JAKARTA_ENDPOINT_CLASS);
								((jakarta.websocket.server.ServerContainer) oServerContainer).addEndpoint(JAKARTA_ENDPOINT_CLASS);
							}
							else if (WSUtil.getContainerType(cw) == WSUtil.TYPE_JAVAX) {
								props.put("lucee.websocket.endpoint", JAVAX_ENDPOINT_CLASS);
								((javax.websocket.server.ServerContainer) oServerContainer).addEndpoint(JAVAX_ENDPOINT_CLASS);
							}
							else {
								if (oServerContainer == null)
									throw eng.getExceptionUtil().createApplicationException("[javax.websocket.server.ServerContainer] not supported on this server.");
								else throw eng.getExceptionUtil()
										.createApplicationException("container [" + oServerContainer.getClass().getName()
												+ "] not supported, only the following container are supported [" + jakarta.websocket.server.ServerContainer.class.getName() + ", "
												+ javax.websocket.server.ServerContainer.class.getName() + "].");
							}
						}

						catch (jakarta.websocket.DeploymentException | javax.websocket.DeploymentException e) {
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

	public static WebSocketEndpointFactory getInstance() throws PageException {
		if (instance == null) {
			PageException pe = CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("WebSocketEndpointFactory failed to initialize within the Lucee engine (startup-hook).");
			if (re != null) pe.initCause(re);
			throw pe;
		}
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
			WSUtil.info(pc.getConfig(), "no [directory] setting found in configuration, using default location [" + WSUtil.DEFAULT_DIRECTORY + "]");
			path = WSUtil.DEFAULT_DIRECTORY;
			if (data.configuration == null) data.configuration = eng.getCreationUtil().createStruct();
			data.configuration.setEL("directory", path);
		}
		else {
			WSUtil.info(pc.getConfig(), "found [directory] setting in configuration, using [" + path + "]");
		}
		data.mapping = WSUtil.createMapping(pc, path);

		WSUtil.info(cw, "init WebSocketEndpoint for web context [" + cw.getIdentification().getId() + " - " + WSUtil.getServletContextRealPath(cw, "/")
				+ "] mapping defined in the configuration is [" + path + "], this is resolved to [" + data.mapping.getPhysical() + "]");

		// request timeout
		long timeout = eng.getCastUtil().toLongValue(data.configuration.get("requestTimeout", null), 0);
		if (timeout > 0L) data.requestTimeout = timeout * 1000;

		// idle timeout
		timeout = eng.getCastUtil().toLongValue(data.configuration.get("idleTimeout", null), 0);
		if (timeout > 0L) data.idleTimeout = timeout * 1000;

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
				if (count == 5) sleepTime = 1000; // after 2.5 seconds we increase to a 1 second interval
				if (count == 20) sleepTime = 10000; // after 10 seconds we increase to a 10 second interval
				else if (count == 30) sleepTime = 60000; // after an other 100 seconds we increase to a minute interval
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

	public java.util.Collection<Object> getSessions(ConfigWeb config) throws PageException {
		return register(config).sessions.values();
	}

	public void setSessions(ConfigWeb config, Object session) throws PageException {
		register(config).sessions.put(WSUtil.getId(config, session), session);
	}

	public void remSessions(ConfigWeb config, Object session) throws PageException {
		register(config).sessions.remove(WSUtil.getId(config, session));
	}

	public long getRequestTimeout(ConfigWeb cw) throws PageException {
		return register(cw).requestTimeout;
	}

	public long getIdleTimeout(ConfigWeb cw) throws PageException {
		return register(cw).idleTimeout;
	}

	private static class Data {
		public long requestTimeout = DEFAULT_REQUEST_TIMEOUT;
		public long idleTimeout = DEFAULT_IDLE_TIMEOUT;
		private ConfigWeb config;
		private Map<String, Object> sessions = new ConcurrentHashMap<>();
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
			Array instances = eng.getCreationUtil().createArray();
			result.setEL("instances", instances);

			Struct data;
			for (Instance inst: BaseWebSocketEndpoint.instances.values()) {
				Struct sessionInfo = WSUtil.getInfoSession(config, eng, inst.session, addRaw);
				// if the session is closed, we don't care
				if (sessionInfo == null) continue;
				data = eng.getCreationUtil().createStruct();
				instances.appendEL(data);
				data.setEL("component", inst.cfc);
				data.setEL("session", sessionInfo);
			}

			// version
			try {
				ClassLoader cl = this.getClass().getClassLoader();
				Bundle b = (Bundle) eng.getClassUtil().callMethod(cl, eng.getCreationUtil().createKey("getBundle"), new Object[0]);
				result.setEL(eng.getCreationUtil().createKey("version"), b.getVersion().toString());
			}
			catch (Exception e) {
			}

			return result;
		}

	}
}
