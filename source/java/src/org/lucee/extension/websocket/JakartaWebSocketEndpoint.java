package org.lucee.extension.websocket;

import java.io.IOException;

import org.lucee.extension.websocket.util.JakartaServletAwareConfig;

import jakarta.websocket.CloseReason;
import jakarta.websocket.EncodeException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lucee.runtime.exp.PageException;

//ServerEndpoint(value="/endpoint", encoders = MessageEncoder.class, decoders= MessageDecoder.class)

@ServerEndpoint(value = "/ws/{component-name}", configurator = JakartaServletAwareConfig.class)
public class JakartaWebSocketEndpoint extends BaseWebSocketEndpoint {

	// called by the Servelt engine
	public JakartaWebSocketEndpoint() {
		super();
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig config, @PathParam("component-name") String componentName) throws PageException, IOException {
		super.onOpen(session, config, componentName);
	}

	@OnMessage
	public String onMessage(Session session, String message, boolean last, @PathParam("component-name") String componentName) throws PageException, IOException {
		return super.onMessage(session, message, last, componentName);
	}

	@OnError
	public void onError(Session session, Throwable t, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		super.onError(session, t, componentName);
	}

	@OnClose
	public void onClose(Session session, CloseReason reason, @PathParam("component-name") String componentName) throws PageException, IOException, EncodeException {
		super.onClose(session, reason, componentName);
	}
}
