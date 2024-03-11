package org.lucee.extension.websocket.client;

import org.lucee.extension.websocket.WebSocketEndpointFactory;
import org.lucee.extension.websocket.util.WSUtil;

import lucee.runtime.PageContext;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;

/**
 * Wrapper for a Webservice
 */
public final class WSClients extends AbsWSClient {

	private Key[] keys;

	public WSClients(WebSocketEndpointFactory factory) {
		super(factory, "WSClients");
		keys = new Key[] { BROADCAST_MESSAGE, CLOSE, GET_CLIENTS, SEND_MESSAGE, SIZE };
	}

	@Override
	public Object call(PageContext pc, Key name, Object[] args) throws PageException {
		if (SEND.equals(name) || SEND_MESSAGE.equals(name) || BROADCAST.equals(name) || BROADCAST_MESSAGE.equals(name)) {
			checkArgs(name, args, 1);
			return WSUtil.broadcast(pc.getConfig(), factory, args[0]);
		}
		else if (SIZE.equals(name)) {
			checkArgs(name, args, 0);
			return Double.valueOf(factory.getSessions(pc.getConfig()).size());
		}
		else if (GET_CLIENTS.equals(name)) {
			checkArgs(name, args, 0);
			return getClients(pc.getConfig());
		}
		else if (CLOSE.equals(name)) {
			checkArgs(name, args, 0, 1);
			try {
				Object cr = (args.length == 0) ? null : WSUtil.toCloseReason(pc.getConfig(), args[0]);
				for (Object session: factory.getSessions(pc.getConfig())) {
					WSUtil.close(pc.getConfig(), session, cr);
				}
				return null;
			}
			catch (Exception e) {
				throw caster.toPageException(e);
			}
		}
		throw exception.createExpressionException("WSClient does not have the function [" + name + "]");
	}

	@Override
	public String toString() {
		return "{"

				+ "\n\tsize():number;"

				+ "\n\tbroadcast(any message):boolean;"

				+ "\n\tgetClients():Client[];"

				+ "\n\tclose():void;"

				+ "\n}";
	}

	@Override
	public Object callWithNamedValues(PageContext pc, Key name, Struct args) throws PageException {
		if (SEND.equals(name) || SEND_MESSAGE.equals(name) || BROADCAST.equals(name) || BROADCAST_MESSAGE.equals(name)) {
			return WSUtil.broadcast(pc.getConfig(), factory, args.get(MESSAGE));
		}
		else if (SIZE.equals(name)) {
			return Double.valueOf(factory.getSessions(pc.getConfig()).size());
		}
		else if (GET_CLIENTS.equals(name)) {
			return getClients(pc.getConfig());
		}
		else if (CLOSE.equals(name)) {
			try {
				Object cr = (args.size() == 0) ? null : WSUtil.toCloseReason(pc.getConfig(), args);
				for (Object session: factory.getSessions(pc.getConfig())) {
					WSUtil.close(pc.getConfig(), session, cr);
				}
				return null;
			}
			catch (Exception e) {
				throw caster.toPageException(e);
			}
		}
		throw exception.createExpressionException("WSClient does not have the function [" + name + "]");
	}

	private Object getClients(ConfigWeb cw) throws PageException {
		Array arr = creator.createArray();
		for (Object session: factory.getSessions(cw)) {
			arr.appendEL(new WSClient(factory, session));
		}
		return arr;
	}

}