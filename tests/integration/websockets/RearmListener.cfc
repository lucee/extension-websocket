component hint="Tests that onFirstOpen re-fires after onLastClose when new clients connect" {

	private string function getLogFile() {
		return getTempDirectory() & "ws-rearm.log";
	}

	public static function onFirstOpen( wsClients ) {
		lock name="ws-rearm" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-rearm.log", "onFirstOpen" & chr( 10 ) );
		}
	}

	public static function onLastClose() {
		lock name="ws-rearm" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-rearm.log", "onLastClose" & chr( 10 ) );
		}
	}

	function onOpen( wsClient ) {
		lock name="ws-rearm" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-rearm.log", "onOpen" & chr( 10 ) );
		}
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {
		lock name="ws-rearm" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-rearm.log", "onClose" & chr( 10 ) );
		}
	}

	function onError( wsClient, cfCatch ) {
		lock name="ws-rearm" type="exclusive" timeout="5" {
			fileAppend( getTempDirectory() & "ws-rearm.log", "onError:" & arguments.cfCatch.message & chr( 10 ) );
		}
	}

}
