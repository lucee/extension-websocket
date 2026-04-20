component hint="Tests onOpenAsync fires in parallel with onOpen on a different thread" {

	private string function getLogFile() {
		return getTempDirectory() & "ws-onopen-async.log";
	}

	private void function _log( required string event ) {
		lock name="ws-onopen-async" type="exclusive" timeout="5" {
			fileAppend( getLogFile(), arguments.event & chr( 10 ) );
		}
	}

	function onOpen( wsClient ) {
		var tid = createObject( "java", "java.lang.Thread" ).currentThread().getId();
		_log( "onOpen:thread=" & tid );
		arguments.wsClient.send( "CONNECTED" );
	}

	function onOpenAsync( wsClient ) {
		var tid = createObject( "java", "java.lang.Thread" ).currentThread().getId();
		_log( "onOpenAsync:thread=" & tid );
	}

	function onMessage( wsClient, message ) {
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}
	function onError( wsClient, cfCatch ) {}

}
