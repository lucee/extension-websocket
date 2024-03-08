package org.lucee.extension.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.Session;

import org.lucee.extension.websocket.util.WSUtil;
import org.lucee.extension.websocket.util.print;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigServer;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Creation;

public class WebSocketEndpointFactory {
	private final ConfigServer cs;
	private String strComponentDir;
	private final CFMLEngine eng;
	private static WebSocketEndpointFactory instance;
	private Map<String, ConfigWeb> configs = new HashMap<>();

	public final Collection.Key ON_OPEN;
	public final Collection.Key ON_OPEN_ASYNC;
	public final Collection.Key ON_FIRST_OPEN;
	public final Collection.Key ON_LAST_CLOSE;
	public final Collection.Key ON_MESSAGE;
	public final Collection.Key ON_ERROR;
	public final Collection.Key ON_CLOSE;
	private boolean alive = true;

	public Map<String, Session> sessions = new ConcurrentHashMap<>();
	private Log log;

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
		log = WSUtil.getLog(cs);

		// string path
		String tmp = WSUtil.getSystemPropOrEnvVar("lucee.websocket.dir", null);
		if (!eng.getStringUtil().isEmpty(tmp, true)) {
			WSUtil.info(log, "found system property/enviroment variable [lucee.websocket.dir/LUCEE_WEBSOCKET_DIR] with value [" + tmp + "]");
			strComponentDir = tmp.trim();
		}
		else {
			WSUtil.info(log, "no system property/enviroment variable [lucee.websocket.dir/LUCEE_WEBSOCKET_DIR] found");
		}

		new Registrar(this, log).start();
		// register();

		instance = this;
	}

	private void register() {
		for (ConfigWeb cw: cs.getConfigWebs()) {
			if (configs.containsKey(cw.getIdentification().getId())) continue;
			try {
				configs.put(cw.getIdentification().getId(), cw);
				javax.websocket.server.ServerContainer serverContainer = (javax.websocket.server.ServerContainer) cw.getServletContext()
						.getAttribute("javax.websocket.server.ServerContainer");

				WSUtil.info(log, "register web context [" + cw.getIdentification().getId() + " / " + cw.getServletContext().getRealPath("/") + "]");

				print.e(cw.getConfigDir());
				print.e("sc:" + cw.getServletContext());
				print.e("realpath:" + cw.getServletContext().getRealPath("/"));
				print.e("conainer:" + serverContainer);
				// Add endpoint manually to server container
				serverContainer.addEndpoint(WebSocketEndpoint.class);

			}
			catch (Exception e) {
				WSUtil.error(log, e);
			}
		}
	}

	public static WebSocketEndpointFactory getInstance() {
		return instance;
	}

	@Override
	public void finalize() {
		alive = false;
		System.err.println("WebSocketEndpoint=>finalize");
		new Throwable().printStackTrace();
	}

	public boolean isAlive() {
		return alive;
	}

	public ConfigServer getConfigServer() {
		return cs;
	}

	public String getComponentPath(PageContext pc) throws PageException, IOException {
		if (eng.getStringUtil().isEmpty(strComponentDir, true)) {
			Struct config = WSUtil.readConfig(pc, log);
			String path = eng.getCastUtil().toString(config.get("directory", null), null);
			if (eng.getStringUtil().isEmpty(path, true)) {
				WSUtil.info(log, "no [directory] setting found in configuration, using default location [{lucee-web}/websockets/]");
				return "{lucee-web}/websockets/";
			}
			WSUtil.info(log, "found [directory] setting in configuration, using [" + path + "]");
			return path;
		}
		return strComponentDir;
	}

	private static class Registrar extends Thread {
		private WebSocketEndpointFactory factory;
		private boolean alive = true;
		private Log log;

		public Registrar(WebSocketEndpointFactory factory, Log log) {
			this.factory = factory;
			this.log = log;
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
					WSUtil.info(log, "checking for new web context to register, current interval [" + sleepTime + "ms]");
					sleep(sleepTime);

				}
				catch (InterruptedException e) {
					WSUtil.warn(log, "sleep for register got interupted");
					break;
				}
			}
		}

		@Override
		public void finalize() {
			alive = false;
		}
	}

	public java.util.Collection<Session> getSessions() {
		return sessions.values();
	}
}
