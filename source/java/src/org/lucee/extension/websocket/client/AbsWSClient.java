package org.lucee.extension.websocket.client;

import org.lucee.extension.websocket.WebSocketEndpointFactory;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.dump.DumpData;
import lucee.runtime.dump.DumpProperties;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Objects;
import lucee.runtime.type.Struct;
import lucee.runtime.type.dt.DateTime;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Creation;
import lucee.runtime.util.Excepton;

/**
 * Wrapper for a Webservice
 */
public abstract class AbsWSClient implements Objects {

	protected static CFMLEngine _engine;
	protected static Cast caster;
	protected static Creation creator;
	protected static Excepton exception;

	static {
		_engine = CFMLEngineFactory.getInstance();
		caster = _engine.getCastUtil();
		creator = _engine.getCreationUtil();
		exception = _engine.getExceptionUtil();
	}

	protected Key MESSAGE;

	protected Key SEND;
	protected Key SEND_MESSAGE;
	protected Key BROADCAST;
	protected Key BROADCAST_MESSAGE;
	protected Key CLOSE;
	protected Key IS_OPEN;
	protected Key IS_CLOSE;
	protected Key SIZE;
	protected Key GET_CLIENTS;

	protected final WebSocketEndpointFactory factory;
	private String className;

	public AbsWSClient(WebSocketEndpointFactory factory, String className) {
		this.factory = factory;
		this.className = className;
		SEND = caster.toKey("send");
		SEND_MESSAGE = caster.toKey("sendMessage");
		CLOSE = caster.toKey("close");
		MESSAGE = caster.toKey("message");
		BROADCAST = caster.toKey("broadcast");
		BROADCAST_MESSAGE = caster.toKey("broadcastMessage");
		IS_OPEN = caster.toKey("isOpen");
		IS_CLOSE = caster.toKey("isClose");
		SIZE = caster.toKey("size");
		GET_CLIENTS = caster.toKey("getClients");
	}

	@Override
	public final Object get(PageContext pc, Key name) throws PageException {
		throw exception.createExpressionException(this.className + " does not have a property with name function [" + name + "]");
	}

	@Override
	public final Object get(PageContext pc, Key name, Object df) {
		return df;
	}

	@Override
	public final Object set(PageContext pc, Key name, Object val) throws PageException {
		throw exception.createExpressionException(this.className + " is readonly");
	}

	@Override
	public final Object setEL(PageContext pc, Key name, Object val) {
		return null;
	}

	@Override
	public final DumpData toDumpData(PageContext pageContext, int maxlevel, DumpProperties dp) {
		Struct data = creator.createStruct();
		data.setEL("classname", className);
		return caster.toDumpTable(data, "WSClient", pageContext, maxlevel, dp);

	}

	@Override
	public final String castToString() throws PageException {
		throw exception.createExpressionException("can't cast " + this.className + " to a string");
	}

	@Override
	public final String castToString(String defaultValue) {
		return defaultValue;
	}

	@Override
	public final boolean castToBooleanValue() throws PageException {
		throw exception.createExpressionException("can't cast " + this.className + " to a boolean");
	}

	@Override
	public final Boolean castToBoolean(Boolean defaultValue) {
		return defaultValue;
	}

	@Override
	public final double castToDoubleValue() throws PageException {
		throw exception.createExpressionException("can't cast " + this.className + " to a number");
	}

	@Override
	public final double castToDoubleValue(double defaultValue) {
		return defaultValue;
	}

	@Override
	public final DateTime castToDateTime() throws PageException {
		throw exception.createExpressionException("can't cast WSClient to a Date Object");
	}

	@Override
	public final DateTime castToDateTime(DateTime defaultValue) {
		return defaultValue;
	}

	@Override
	public final int compareTo(boolean b) throws PageException {
		throw exception.createExpressionException("can't compare " + this.className + " Object with a boolean value");
	}

	@Override
	public final int compareTo(DateTime dt) throws PageException {
		throw exception.createExpressionException("can't compare " + this.className + " Object with a DateTime Object");
	}

	@Override
	public final int compareTo(double d) throws PageException {
		throw exception.createExpressionException("can't compare " + this.className + " Object with a numeric value");
	}

	@Override
	public final int compareTo(String str) throws PageException {
		throw exception.createExpressionException("can't compare " + this.className + " Object with a String");
	}

	protected final void checkArgs(Key functionName, Object[] args, int argCount) throws PageException {
		if (args.length != argCount) throw _engine.getExceptionUtil()
				.createApplicationException("invalid argument count for function [" + functionName + "] it is [" + args.length + "] instead of [" + argCount + "]");
	}

	protected final void checkArgs(Key functionName, Object[] args, int argCountFrom, int argCountTo) throws PageException {
		if (args.length < argCountFrom || args.length > argCountTo) throw _engine.getExceptionUtil().createApplicationException(
				"invalid argument count for function [" + functionName + "] it is [" + args.length + "] but it must be between [" + argCountFrom + "] and [" + argCountTo + "]");
	}
}