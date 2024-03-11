package org.lucee.extension.websocket.udf;

import org.lucee.extension.websocket.WebSocketEndpointFactory;

/**
*
* Copyright (c) 2015, Lucee Assosication Switzerland
*
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either 
* version 2.1 of the License, or (at your option) any later version.
* 
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* Lesser General Public License for more details.
* 
* You should have received a copy of the GNU Lesser General Public 
* License along with this library.  If not, see <http://www.gnu.org/licenses/>.
* 
**/

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;

public class WebsocketInfo extends BIF {

	private static final long serialVersionUID = -3487196822940881552L;

	@Override
	public Object invoke(final PageContext pc, final Object[] args) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		if (args.length > 1 || args.length < 0) throw eng.getExceptionUtil().createFunctionException(pc, "WebsocketInfo", 0, 1, args.length);

		boolean addRaw = false;
		if (args.length == 1) addRaw = eng.getCastUtil().toBooleanValue(args[0]);

		return WebSocketEndpointFactory.getInstance().getInfo(pc.getConfig(), addRaw);
	}
}