component hint="Writes every lifecycle callback to a file so tests can assert from outside" {

	private string function getLogFile() {
		return getTempDirectory() & "ws-events-lifecycle.log";
	}

	private void function _log( required string event ) {
		lock name="ws-events-lifecycle" type="exclusive" timeout="5" {
			fileAppend( getLogFile(), arguments.event & chr( 10 ) );
		}
	}

	public static function onFirstOpen( wsClients ) {
		lock name="ws-events-lifecycle" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-events-lifecycle.log", "onFirstOpen" & chr( 10 ) );
		}
	}

	function onOpen( wsClient ) {
		_log( "onOpen" );
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		_log( "onMessage:" & arguments.message );
		if ( arguments.message == "__THROW__" ) {
			throw( message="sentinel-error", type="TestError" );
		}
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {
		_log( "onClose" );
	}

	function onError( wsClient, cfCatch ) {
		_log( "onError:" & arguments.cfCatch.message );
	}

	public static function onLastClose() {
		lock name="ws-events-lifecycle" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-events-lifecycle.log", "onLastClose" & chr( 10 ) );
		}
	}

}
