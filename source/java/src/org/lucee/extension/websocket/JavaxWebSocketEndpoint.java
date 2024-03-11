package org.lucee.extension.websocket;

import java.io.IOException;

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

import org.lucee.extension.websocket.util.JavaxServletAwareConfig;

import lucee.runtime.exp.PageException;

//ServerEndpoint(value="/endpoint", encoders = MessageEncoder.class, decoders= MessageDecoder.class)

@ServerEndpoint(value = "/ws/{component-name}", configurator = JavaxServletAwareConfig.class)
public class JavaxWebSocketEndpoint extends BaseWebSocketEndpoint {

	// called by the Servelt engine
	public JavaxWebSocketEndpoint() {
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
