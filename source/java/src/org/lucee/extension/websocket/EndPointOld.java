package org.lucee.extension.websocket;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.lucee.extension.websocket.util.ServletAwareConfig;
import org.lucee.extension.websocket.util.WSUtil;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigServer;
import lucee.runtime.config.ConfigWeb;

//ServerEndpoint(value="/endpoint", encoders = MessageEncoder.class, decoders= MessageDecoder.class)

@ServerEndpoint(value = "/ws/{mapping-name}/{component-name}", configurator = ServletAwareConfig.class)
public class EndPointOld {
	private static CFMLEngine eng;
	private static ConfigServer cs;
	private static String strPath;
	private static boolean registered;
	private Date startTime;
	private EndpointConfig endpointConfig;
	private HttpSession httpSession;
	private ConfigWeb cw;

	// called by Lucee startup
	public EndPointOld(Config config) {
		eng = CFMLEngineFactory.getInstance();
		cs = (ConfigServer) config;

		System.err.println("WebSocketEndpoint-->init");

		// string path
		strPath = WSUtil.getSystemPropOrEnvVar("lucee.websocket.dir", null);
		if (eng.getStringUtil().isEmpty(strPath, true)) strPath = "{lucee-web}/components/";
		else strPath = strPath.trim();

		System.err.println("strPath:" + strPath);
		System.err.println("webs:" + cs.getConfigWebs().length);

		register();
	}

	// called by the Servelt engine
	public EndPointOld() {
		startTime = new Date();
	}

	private void register() {
		if (registered) return;

		try {
			ServletContext srvCnxt = (ServletContext) cs.getClass().getMethod("getServletContext", new Class[0]).invoke(cs, new Object[0]);
			javax.websocket.server.ServerContainer serverContainer = (javax.websocket.server.ServerContainer) srvCnxt.getAttribute("javax.websocket.server.ServerContainer");

			// Add endpoint manually to server container
			serverContainer.addEndpoint(EndPointOld.class);
		}
		catch (Exception e) {
			log(e);
		}
		registered = true;
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig config, @PathParam("mapping-name") String mappingName, @PathParam("component-name") String componentName)
			throws IOException {
		System.err.println("onOpen (" + startTime + "):" + mappingName + ":" + componentName);
		/*
		 * try { getConfig(config); PageContext pc = createPageContext(session); } catch (Exception e) {
		 * log(e); }
		 */
		//
		session.getBasicRemote().sendText("onOpen:" + mappingName + ":" + componentName);
	}

	private void getConfig(EndpointConfig endpointConfig) {
		this.endpointConfig = endpointConfig;
		this.httpSession = (HttpSession) endpointConfig.getUserProperties().get("httpSession");
		ServletContext sc = httpSession.getServletContext();
		ConfigWeb[] webs = cs.getConfigWebs();
		for (ConfigWeb cw: webs) {
			if (sc == cw.getServletContext()) {
				this.cw = cw;
				break;
			}
		}
		throw new RuntimeException("no matching ServletContext found!");
	}

	@OnMessage
	public String onMessage(String message, @PathParam("mapping-name") String mappingName, @PathParam("component-name") String componentName) {
		System.err.println("onMessage(" + startTime + "):" + mappingName + ":" + componentName);
		System.err.println(message);
		return message + " (from your server)";
	}

	@OnError
	public void onError(Throwable t, @PathParam("mapping-name") String mappingName, @PathParam("component-name") String componentName) {
		System.err.println("onError(" + startTime + "):" + mappingName + ":" + componentName);
		t.printStackTrace();
	}

	@OnClose
	public void onClose(Session session, @PathParam("mapping-name") String mappingName, @PathParam("component-name") String componentName) {
		System.err.println("onClose(" + startTime + "):" + mappingName + ":" + componentName);
	}

	@Override
	public void finalize() {
		System.err.println("WebSocketEndpoint=>finalize");
		new Throwable().printStackTrace();
	}

	private void log(Exception e) {
		e.printStackTrace();
	}
}
