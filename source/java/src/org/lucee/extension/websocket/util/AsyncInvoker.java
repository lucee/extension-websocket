package org.lucee.extension.websocket.util;

import javax.websocket.Session;

import org.lucee.extension.websocket.WebSocketEndpoint;

import lucee.runtime.PageContext;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;

public class AsyncInvoker extends Thread {

	private String componentName;
	private Key name;
	private Object[] args;
	private Session session;
	private WebSocketEndpoint endpoint;
	private boolean isStatic;

	public AsyncInvoker(WebSocketEndpoint endpoint, Session session, String componentName, Collection.Key name, boolean isStatic, Object[] args) {
		this.session = session;
		this.endpoint = endpoint;
		this.componentName = componentName;
		this.name = name;
		this.args = args;
		this.isStatic = isStatic;
	}

	@Override
	public void run() {
		PageContext pc = null;
		try {
			pc = WSUtil.createPageContext(endpoint.getConfig(), session, componentName);
			if (isStatic) endpoint.invokeStatic(pc, componentName, name, args, WSUtil.NULL);
			else endpoint.invoke(pc, componentName, name, args, WSUtil.NULL);
		}
		catch (Exception e) {
			// TODO
			e.printStackTrace();
		}
		finally {
			if (pc != null) WSUtil.releasePageContext(pc);
		}
	}
}
