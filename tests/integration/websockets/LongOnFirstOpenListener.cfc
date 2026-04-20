component hint="Verifies requestTimeout kills long work inside onFirstOpen" {

	public static function onFirstOpen( wsClients ) {
		// Shrink the request timeout so CI doesn't wait the default 50s.
		setting requestTimeout=3;

		var logFile = getTempDirectory() & "ws-request-timeout.log";

		lock name="ws-request-timeout" type="exclusive" timeout="5" {
			fileAppend( logFile, "before-loop:" & getTickCount() & chr( 10 ) );
		}

		var start = getTickCount();
		// Hard cap at 15s — requestTimeout should kill us well before this.
		while ( ( getTickCount() - start ) < 15000 ) {
			sleep( 200 );
			lock name="ws-request-timeout" type="exclusive" timeout="1" {
				fileAppend( logFile, "tick:" & ( getTickCount() - start ) & chr( 10 ) );
			}
		}

		// If this line is reached, requestTimeout did NOT kill the loop.
		lock name="ws-request-timeout" type="exclusive" timeout="5" {
			fileAppend( logFile, "after-loop:" & getTickCount() & chr( 10 ) );
		}
	}

	function onOpen( wsClient ) {
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}
	function onError( wsClient, cfCatch ) {}

}
