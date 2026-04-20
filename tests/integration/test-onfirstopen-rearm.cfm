<cfscript>
// Doc claim: "onFirstOpen can fire more than once per listener lifetime — each time
// the channel goes from zero clients back to one, it runs again."
//
// Sequence: connect → disconnect (channel goes to zero) → wait → connect again.
// Expect: onFirstOpen count == 2, onLastClose count >= 1.

writeOutput( "=== onFirstOpen re-arm after onLastClose ===" & chr( 10 ) );

logFile = getTempDirectory() & "ws-rearm.log";
if ( fileExists( logFile ) )
	fileDelete( logFile );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	wsUrl = "ws://localhost:8888/ws/RearmListener";

	// First connection — expect onFirstOpen
	clientA = new tests.integration.ClientListener();
	wsA = CreateWebSocketClient( wsUrl, clientA );
	sleep( 500 );

	wsA.disconnect();
	sleep( 1500 );  // give onLastClose's async thread time to fire

	// Second connection after channel drained — expect another onFirstOpen
	clientB = new tests.integration.ClientListener();
	wsB = CreateWebSocketClient( wsUrl, clientB );
	sleep( 500 );

	wsB.disconnect();
	sleep( 1500 );

	events = fileExists( logFile ) ? fileRead( logFile ).listToArray( chr( 10 ) ) : [];

	writeOutput( chr( 10 ) & "=== Events logged ===" & chr( 10 ) );
	for ( e in events )
		writeOutput( "  - #e#" & chr( 10 ) );

	firstOpenCount = 0;
	lastCloseCount = 0;
	for ( e in events ) {
		if ( e == "onFirstOpen" ) firstOpenCount++;
		if ( e == "onLastClose" ) lastCloseCount++;
	}

	errors = [];

	if ( firstOpenCount != 2 )
		arrayAppend( errors, "expected onFirstOpen to fire 2 times (once per cold start), got #firstOpenCount#" );

	if ( lastCloseCount < 1 )
		arrayAppend( errors, "expected onLastClose to fire at least once, got #lastCloseCount#" );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: onFirstOpen re-fired after channel drained" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
