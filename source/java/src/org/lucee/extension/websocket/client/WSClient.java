package org.lucee.extension.websocket.client;

import org.lucee.extension.websocket.WebSocketEndpointFactory;
import org.lucee.extension.websocket.util.WSUtil;

import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;

/**
 * Wrapper for a Webservice
 */
public final class WSClient extends AbsWSClient {

	private Object session;
	private Key[] keys;

	public WSClient(WebSocketEndpointFactory factory, Object session) {
		super(factory, "WSClient");
		this.session = session;
		keys = new Key[] { BROADCAST_MESSAGE, CLOSE, IS_OPEN, SEND_MESSAGE };
	}

	@Override
	public Object call(PageContext pc, Key name, Object[] args) throws PageException {
		if (SEND.equals(name) || SEND_MESSAGE.equals(name)) {
			checkArgs(name, args, 1);
			return WSUtil.send(pc.getConfig(), session, args[0]);
		}
		else if (BROADCAST.equals(name) || BROADCAST_MESSAGE.equals(name)) {
			checkArgs(name, args, 1);
			return WSUtil.broadcast(pc.getConfig(), factory, args[0]);
		}
		else if (IS_OPEN.equals(name)) {
			checkArgs(name, args, 0);
			return WSUtil.isOpen(pc.getConfig(), session);
		}
		else if (IS_CLOSE.equals(name)) {
			checkArgs(name, args, 0);
			return !WSUtil.isOpen(pc.getConfig(), session);
		}
		else if (CLOSE.equals(name)) {
			checkArgs(name, args, 0, 1);
			try {
				if (args.length == 0) WSUtil.close(pc.getConfig(), session, null);
				else WSUtil.close(pc.getConfig(), session, WSUtil.toCloseReason(pc.getConfig(), args[0]));
				return null;
			}
			catch (Exception e) {
				throw caster.toPageException(e);
			}
		}

		throw exception.createExpressionException("WSClient does not have the function [" + name + "]");
	}

	@Override
	public Object callWithNamedValues(PageContext pc, Key name, Struct args) throws PageException {
		if (SEND.equals(name) || SEND_MESSAGE.equals(name)) {
			return WSUtil.send(pc.getConfig(), session, args.get(MESSAGE));
		}
		else if (BROADCAST.equals(name) || BROADCAST_MESSAGE.equals(name)) {
			return WSUtil.broadcast(pc.getConfig(), factory, args.get(MESSAGE));
		}
		else if (IS_OPEN.equals(name)) {
			return WSUtil.isOpen(pc.getConfig(), session);
		}
		else if (IS_CLOSE.equals(name)) {
			return !WSUtil.isOpen(pc.getConfig(), session);
		}
		else if (CLOSE.equals(name)) {
			try {
				WSUtil.close(pc.getConfig(), session, WSUtil.toCloseReason(pc.getConfig(), args));
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

				+ "\n\tsend(any message):boolean;"

				+ "\n\tbroadcast(any message):boolean;"

				+ "\n\tisOpen():boolean;"

				+ "\n\tisClose():boolean;"

				+ "\n\tclose():void;"

				+ "\n}";
	}

}