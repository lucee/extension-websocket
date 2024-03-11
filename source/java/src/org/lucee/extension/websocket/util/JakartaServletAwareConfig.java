package org.lucee.extension.websocket.util;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

public class JakartaServletAwareConfig extends ServerEndpointConfig.Configurator {

	@Override
	public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
		/*
		 * print.e("modifyHandshake start"); try {
		 * 
		 * HttpSession httpSession = (HttpSession) request.getHttpSession(); print.e("httpSession:" +
		 * httpSession); config.getUserProperties().put("httpSession", httpSession); } catch (Exception e) {
		 * print.e(e); } print.e("modifyHandshake end");
		 */
	}
}