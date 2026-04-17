<cfscript>
// LDEV-6219 - idleTimeout must be applied to ALL sessions, not just the first
//
// Connects two clients to TimeoutListener (which has property idleTimeout=5)
// and verifies both sessions have maxIdleTimeout=2000 via websocketInfo()

writeOutput( "=== LDEV-6219: idleTimeout applied to all sessions ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) ) {
		throw( message="CreateWebSocketClient not available - websocket-client extension not installed", type="TestSetupError" );
	}

	wsUrl = "ws://localhost:8888/ws/TimeoutListener";
	writeOutput( "Connecting to: #wsUrl#" & chr( 10 ) );

	// Connect two clients
	client1 = new tests.integration.ClientListener();
	client2 = new tests.integration.ClientListener();

	ws1 = CreateWebSocketClient( wsUrl, client1 );
	sleep( 500 );
	ws2 = CreateWebSocketClient( wsUrl, client2 );
	sleep( 500 );

	// Check websocketInfo while both are connected
	info = websocketInfo();
	writeOutput( "Total instances: #arrayLen( info.instances )#" & chr( 10 ) );

	// Find TimeoutListener sessions (component is a CFC object, use getMetaData to get the name)
	timeoutSessions = [];
	for ( inst in info.instances ) {
		meta = getMetaData( inst.component );
		if ( findNoCase( "TimeoutListener", meta.name ) ) {
			arrayAppend( timeoutSessions, inst.session );
		}
	}

	writeOutput( "TimeoutListener sessions: #arrayLen( timeoutSessions )#" & chr( 10 ) );

	errors = [];

	if ( arrayLen( timeoutSessions ) < 2 ) {
		arrayAppend( errors, "Expected 2 TimeoutListener sessions, got #arrayLen( timeoutSessions )#" );
	}

	for ( i = 1; i <= arrayLen( timeoutSessions ); i++ ) {
		s = timeoutSessions[ i ];
		writeOutput( "  Session #i#: maxIdleTimeout=#s.maxIdleTimeout#" & chr( 10 ) );
		if ( s.maxIdleTimeout != 2000 ) {
			arrayAppend( errors, "Session #i# maxIdleTimeout=#s.maxIdleTimeout#, expected 2000" );
		}
	}

	// Cleanup
	ws1.disconnect();
	ws2.disconnect();
	sleep( 500 );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors ) {
			writeOutput( "  - #err#" & chr( 10 ) );
		}
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: idleTimeout applied to all sessions" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
