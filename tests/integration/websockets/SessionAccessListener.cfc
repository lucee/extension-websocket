component hint="Exercises wsClient.getSession() accessors + verifies handshake session isolation" {

	private string function getLogFile() {
		return getTempDirectory() & "ws-session-access.log";
	}

	private void function _log( required string event ) {
		lock name="ws-session-access" type="exclusive" timeout="5" {
			fileAppend( getLogFile(), arguments.event & chr( 10 ) );
		}
	}

	function onOpen( wsClient ) {
		var sess = arguments.wsClient.getSession();
		_log( "onOpen:id=" & sess.getId() );

		// The doc claim: "The extension builds a synthetic PageContext with an empty
		// cookie array and never bridges the handshake HttpSession". Assert cookie
		// and form scopes are empty from inside the listener.
		try {
			_log( "onOpen:cookieCount=" & structCount( cookie ) );
		} catch ( any e ) {
			_log( "onOpen:cookieScope=UNAVAILABLE:" & e.message );
		}
		try {
			_log( "onOpen:formCount=" & structCount( form ) );
		} catch ( any e ) {
			_log( "onOpen:formScope=UNAVAILABLE:" & e.message );
		}

		arguments.wsClient.send( "CONNECTED:" & sess.getId() );
	}

	function onMessage( wsClient, message ) {
		var sess = arguments.wsClient.getSession();

		if ( arguments.message == "__GETID__" ) {
			arguments.wsClient.send( "id:" & sess.getId() );
			return;
		}

		if ( arguments.message == "__GETPARAMS__" ) {
			var params = sess.getRequestParameterMap();
			var tokenList = params.get( "token" );
			var token = isNull( tokenList ) ? "NONE" : tokenList.get( 0 );
			arguments.wsClient.send( "token:" & token );
			return;
		}

		if ( arguments.message == "__PUTPROP__" ) {
			sess.getUserProperties().put( "testKey", "val-" & sess.getId() );
			arguments.wsClient.send( "PUT_OK" );
			return;
		}

		if ( arguments.message == "__GETPROP__" ) {
			var val = sess.getUserProperties().get( "testKey" );
			arguments.wsClient.send( "prop:" & ( isNull( val ) ? "NULL" : val ) );
			return;
		}

		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {
		_log( "onError:" & arguments.cfCatch.message );
	}

}
