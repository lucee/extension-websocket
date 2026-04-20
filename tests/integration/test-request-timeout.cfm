<cfscript>
// Doc claim: "requestTimeout also bounds onFirstOpen and any thread spawned inside
// it. A while loop in onFirstOpen will be killed once requestTimeout elapses."
//
// The listener runs a 15-second loop with requestTimeout=3. After ~3s the loop
// should be killed; the file log should show "before-loop" and some "tick:*"
// entries but NO "after-loop:" entry.

writeOutput( "=== requestTimeout kills onFirstOpen work ===" & chr( 10 ) );

logFile = getTempDirectory() & "ws-request-timeout.log";
if ( fileExists( logFile ) )
	fileDelete( logFile );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	listener = new tests.integration.ClientListener();
	ws = CreateWebSocketClient( "ws://localhost:8888/ws/LongOnFirstOpenListener", listener );

	// Wait long enough for requestTimeout (3s) to fire but less than the 15s hard cap
	sleep( 8000 );

	ws.disconnect();
	sleep( 500 );

	events = fileExists( logFile ) ? fileRead( logFile ).listToArray( chr( 10 ) ) : [];

	writeOutput( chr( 10 ) & "=== Events logged (#arrayLen( events )# total) ===" & chr( 10 ) );
	// Dump first + last 5 so CI output stays readable
	for ( i = 1; i <= min( 5, arrayLen( events ) ); i++ )
		writeOutput( "  - #events[ i ]#" & chr( 10 ) );
	if ( arrayLen( events ) > 10 )
		writeOutput( "  ... (#arrayLen( events ) - 10# more)" & chr( 10 ) );
	for ( i = max( 6, arrayLen( events ) - 4 ); i <= arrayLen( events ); i++ )
		writeOutput( "  - #events[ i ]#" & chr( 10 ) );

	reachedAfter = false;
	sawBefore = false;
	for ( e in events ) {
		if ( left( e, 11 ) == "before-loop" ) sawBefore = true;
		if ( left( e, 10 ) == "after-loop" ) reachedAfter = true;
	}

	errors = [];

	if ( !sawBefore )
		arrayAppend( errors, "onFirstOpen never started — 'before-loop' not logged" );

	if ( reachedAfter )
		arrayAppend( errors, "loop ran to completion — requestTimeout did NOT kill the long-running onFirstOpen work ('after-loop' was logged)" );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: requestTimeout killed onFirstOpen before the loop completed" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
