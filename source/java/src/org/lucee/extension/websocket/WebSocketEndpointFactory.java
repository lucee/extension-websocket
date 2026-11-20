package org.lucee.extension.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.lucee.extension.websocket.BaseWebSocketEndpoint.Instance;
import org.lucee.extension.websocket.util.WSUtil;
import org.lucee.extension.websocket.util.print;
import org.osgi.framework.Bundle;

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
	private static final long DEFAULT_PING_INTERVAL = 30 * 1000;
	private static final long DEFAULT_PONG_TIMEOUT = 10 * 1000;
	private static final long DEFAULT_STALE_TIMEOUT = 0; // disabled, only useful for clients that send a heartbeat

	private static final Class<?> JAKARTA_ENDPOINT_CLASS = JakartaWebSocketEndpoint.class;
	private static final Class<?> JAVAX_ENDPOINT_CLASS = JavaxWebSocketEndpoint.class;
	private final ConfigServer cs;
	private final CFMLEngine eng;
	private static final String ENDPOINT_PROPERTY = "lucee.websocket.endpoint";
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

			// TODO LDEV-6222: use ConfigListener.onLoadWebContext() for event-driven init
			// instead of polling. Currently blocked because ConfigServer.setConfigListener()
			// only accepts a single listener — we'd overwrite any other extension's listener.
			// Needs Lucee core to support a list of listeners first.
			new Registrar(this, config).start();

			instance = this;
		}
		catch (RuntimeException re) {
			this.re = re;
			throw re;
		}
	}

	// get or create the Data entry for a web context (thread-safe)
	// returns null if the web context is still initialising (getIdentification() not ready)
	private Data getOrCreateData(ConfigWeb cw) {
		if (cw == null) throw new IllegalArgumentException("ConfigWeb is null — web context not resolved");
		lucee.runtime.config.Identification id = cw.getIdentification();
		if (id == null) return null;
		return datas.computeIfAbsent(id.getId(), k -> new Data(cw));
	}

	// read config, create mapping, set timeouts for a web context
	private void initContextComponents(Data data, PageContext pc) throws PageException, IOException {
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

		// request timeout
		long timeout = eng.getCastUtil().toLongValue(data.configuration.get("requestTimeout", null), 0);
		if (timeout > 0L) data.requestTimeout = timeout * 1000;

		// idle timeout
		timeout = eng.getCastUtil().toLongValue(data.configuration.get("idleTimeout", null), 0);
		if (timeout > 0L) data.idleTimeout = timeout * 1000;

		// stale session detection (0 disables)
		data.pingInterval = toMillis(data.configuration.get("pingInterval", null), DEFAULT_PING_INTERVAL);
		data.pongTimeout = toMillis(data.configuration.get("pongTimeout", null), DEFAULT_PONG_TIMEOUT);
		data.staleTimeout = toMillis(data.configuration.get("staleTimeout", null), DEFAULT_STALE_TIMEOUT);

		ConfigWeb cw = pc.getConfig();
		WSUtil.info(cw, "init WebSocketEndpoint for web context [" + cw.getIdentification().getId() + " - " + WSUtil.getServletContextRealPath(cw, "/")
				+ "] mapping defined in the configuration is [" + path + "], this is resolved to [" + data.mapping.getPhysical() + "]");
	}

	// seconds to milliseconds, 0 disables, defaultValue (in milliseconds) when not set or invalid
	private long toMillis(Object value, long defaultValue) {
		if (value == null) return defaultValue;
		long l = eng.getCastUtil().toLongValue(value, -1);
		if (l < 0) return defaultValue;
		return l * 1000;
	}

	// register the websocket endpoint with the servlet container (one-time, thread-safe)
	private void registerEndpoint(ConfigWeb cw) throws PageException {
		if (isEndpointRegistered) return;
		synchronized (token) {
			if (isEndpointRegistered) return;
			short containerType = WSUtil.getContainerType(cw);
			Class<?> endpointClass = containerType == WSUtil.TYPE_JAKARTA ? JAKARTA_ENDPOINT_CLASS : (containerType == WSUtil.TYPE_JAVAX ? JAVAX_ENDPOINT_CLASS : null);
			Object oServerContainer = WSUtil.getServletContextAttribute(cw, (containerType == WSUtil.TYPE_JAKARTA ? "jakarta" : "javax") + ".websocket.server.ServerContainer");

			if (endpointClass == null) {
				if (oServerContainer == null) throw eng.getExceptionUtil().createApplicationException("[javax.websocket.server.ServerContainer] not supported on this server.");
				else throw eng.getExceptionUtil()
						.createApplicationException("container [" + oServerContainer.getClass().getName() + "] not supported, only the following container are supported ["
								+ jakarta.websocket.server.ServerContainer.class.getName() + ", " + javax.websocket.server.ServerContainer.class.getName() + "].");
			}

			// LDEV-6972: the startup hook can be instantiated more than once (e.g. deploy folder + config load),
			// every instance has its own Registrar thread and its own token, so the guard above is not enough.
			// claimEndpoint is atomic JVM wide (also across bundle class loaders), so only one caller ever adds the endpoint.
			Properties props = System.getProperties();
			Object endpoint = claimEndpoint(props, endpointClass);

			// already registered by an other factory instance of this bundle
			if (endpoint == endpointClass) {
				String msg = "WebSocketEndpoint already registered with servlet container";
				WSUtil.info(cs, msg);
				WSUtil.info(cw, msg);
			}
			// update
			else if (endpoint instanceof Class) {
				String msg = "update existing WebSocketEndpoint with via injection";
				WSUtil.info(cs, msg);
				WSUtil.info(cw, msg);

				CFMLEngine eng = CFMLEngineFactory.getInstance();
				try {
					if (containerType == WSUtil.TYPE_JAKARTA) eng.getClassUtil().callStaticMethod((Class) endpoint, "inject", new Object[] { new JakartaWebSocketEndpoint() });
					else eng.getClassUtil().callStaticMethod((Class) endpoint, "inject", new Object[] { new JavaxWebSocketEndpoint() });
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
					if (containerType == WSUtil.TYPE_JAKARTA) ((jakarta.websocket.server.ServerContainer) oServerContainer).addEndpoint(endpointClass);
					else ((javax.websocket.server.ServerContainer) oServerContainer).addEndpoint(endpointClass);
				}
				catch (jakarta.websocket.DeploymentException | javax.websocket.DeploymentException | RuntimeException e) {
					// some container are not able/do not allow to update the endpoint, but this is needed when the
					// extension updates, so we inject us in the old extension version (see above).
					// release the claim, so a later attempt can try again
					props.remove(ENDPOINT_PROPERTY, endpointClass);
					throw eng.getCastUtil().toPageException(e);
				}
			}
			isEndpointRegistered = true;
		}
	}

	/**
	 * Atomically claims the (JVM wide) endpoint registration slot.
	 *
	 * @return null when the caller claimed the slot and has to add the endpoint to the servlet container,
	 *         otherwise the endpoint class already registered (by this or an other bundle version)
	 */
	public static Object claimEndpoint(Properties props, Class<?> endpointClass) {
		// Properties is a Hashtable, putIfAbsent/replace are synchronized on the shared instance
		while (true) {
			Object existing = props.putIfAbsent(ENDPOINT_PROPERTY, endpointClass);
			if (existing == null || existing instanceof Class) return existing;
			// unexpected value (not set by us), replace it atomically
			if (props.replace(ENDPOINT_PROPERTY, existing, endpointClass)) return null;
		}
	}

	// called by the Registrar thread to discover and register new web contexts
	private void scanWebContexts() {
		for (ConfigWeb cw: cs.getConfigWebs()) {
			try {
				// Skip CLI servlet contexts or contexts still initialising
				if (cw != null && !WSUtil.isCliServletContext(cw)) {
					Data data = getOrCreateData(cw);
					if (data == null) continue;
					if (data.mapping == null) {
						PageContext pc = WSUtil.createPageContext(this, cw, null, null);
						try {
							initContextComponents(data, pc);
						}
						finally {
							WSUtil.releasePageContext(pc);
						}
					}
					registerEndpoint(cw);
				}
			}
			catch (Exception e) {
				WSUtil.error(cw, e);
			}
		}
	}

	public Struct getInfo(ConfigWeb config, boolean addRaw) throws PageException {
		return getInfo(config, addRaw, null);
	}

	/**
	 * @param component listener component name to filter the instances by, null for the listener
	 *            component the current thread is executing (all components of the web context when
	 *            called outside a listener), "*" for all components of the web context
	 */
	public Struct getInfo(ConfigWeb config, boolean addRaw, String component) throws PageException {
		Data data = getOrCreateData(config);
		if (data == null) throw eng.getExceptionUtil().createApplicationException("web context not yet ready (getIdentification() returned null)");
		if (data.mapping == null) {
			// lazy init — retry if previous attempt failed
			PageContext pc = WSUtil.createPageContext(this, config, null, null);
			try {
				initContextComponents(data, pc);
			}
			catch (Exception e) {
				throw eng.getCastUtil().toPageException(e);
			}
			finally {
				WSUtil.releasePageContext(pc);
			}
		}
		registerEndpoint(config);
		if (component == null) component = BaseWebSocketEndpoint.getCurrentComponent();
		else if ("*".equals(component.trim()) || component.trim().isEmpty()) component = null;
		return data.getInfo(addRaw, component);
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
		Data data = getOrCreateData(cw);
		if (data == null) throw eng.getExceptionUtil().createApplicationException("web context not yet ready (getIdentification() returned null)");
		if (data.mapping == null) {
			// lazy init — retry if previous attempt failed
			initContextComponents(data, pc);
		}
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
					factory.scanWebContexts();

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

	public java.util.Collection<Object> getSessions(ConfigWeb config) {
		Data data = getOrCreateData(config);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		return data.sessions.values();
	}

	public void setSessions(ConfigWeb config, Object session) {
		Data data = getOrCreateData(config);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		data.sessions.put(WSUtil.getId(config, session), session);
	}

	public void remSessions(ConfigWeb config, Object session) {
		Data data = getOrCreateData(config);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		data.sessions.remove(WSUtil.getId(config, session));
	}

	public long getRequestTimeout(ConfigWeb cw) {
		Data data = getOrCreateData(cw);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		return data.requestTimeout;
	}

	public long getIdleTimeout(ConfigWeb cw) {
		Data data = getOrCreateData(cw);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		return data.idleTimeout;
	}

	public long getPingInterval(ConfigWeb cw) {
		Data data = getOrCreateData(cw);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		return data.pingInterval;
	}

	public long getPongTimeout(ConfigWeb cw) {
		Data data = getOrCreateData(cw);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		return data.pongTimeout;
	}

	public long getStaleTimeout(ConfigWeb cw) {
		Data data = getOrCreateData(cw);
		if (data == null) throw new IllegalStateException("web context not yet ready");
		return data.staleTimeout;
	}

	private static class Data {
		public long requestTimeout = DEFAULT_REQUEST_TIMEOUT;
		public long idleTimeout = DEFAULT_IDLE_TIMEOUT;
		public long pingInterval = DEFAULT_PING_INTERVAL;
		public long pongTimeout = DEFAULT_PONG_TIMEOUT;
		public long staleTimeout = DEFAULT_STALE_TIMEOUT;
		private ConfigWeb config;
		private Map<String, Object> sessions = new ConcurrentHashMap<>();
		public Mapping mapping;
		public Struct configuration;
		public Resource configFile;

		public Data(ConfigWeb cw) {
			this.config = cw;
		}

		public Struct getInfo(boolean addRaw, String component) {
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

			// only the sessions of this web context (and listener component if defined), newest first
			String contextId = WSUtil.getContextId(config);
			java.util.List<Instance> list = new java.util.ArrayList<>();
			for (Instance inst: BaseWebSocketEndpoint.instances.values()) {
				if (inst.contextId != null && contextId != null && !inst.contextId.equals(contextId)) continue;
				if (component != null && inst.componentName != null && !inst.componentName.equalsIgnoreCase(component)) continue;
				list.add(inst);
			}
			list.sort((a, b) -> Long.compare(b.sequence, a.sequence));

			Struct data;
			for (Instance inst: list) {
				Struct sessionInfo = WSUtil.getInfoSession(config, eng, inst.session, addRaw);
				// if the session is closed, we don't care
				if (sessionInfo == null) continue;
				data = eng.getCreationUtil().createStruct();
				instances.appendEL(data);
				data.setEL("component", inst.cfc);
				data.setEL("componentName", inst.componentName);
				data.setEL("id", sessionInfo.get("id", null));
				data.setEL("openTime", eng.getCreationUtil().createDateTime(inst.openTime));
				data.setEL("lastMessageTime", eng.getCreationUtil().createDateTime(inst.lastMessage));
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
