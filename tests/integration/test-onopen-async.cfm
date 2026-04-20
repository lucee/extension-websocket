<cfscript>
// Doc claim: "onOpenAsync — fires at the same time as onOpen, in parallel. Long
// init work that shouldn't block the connection ack."
//
// Test: connect, assert both onOpen and onOpenAsync logged, on DIFFERENT threads.

writeOutput( "=== onOpenAsync parallel execution ===" & chr( 10 ) );

logFile = getTempDirectory() & "ws-onopen-async.log";
if ( fileExists( logFile ) )
	fileDelete( logFile );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	client = new tests.integration.ClientListener();
	ws = CreateWebSocketClient( "ws://localhost:8888/ws/OnOpenAsyncListener", client );
	sleep( 1000 );  // generous window for async callback to complete

	ws.disconnect();
	sleep( 300 );

	events = fileExists( logFile ) ? fileRead( logFile ).listToArray( chr( 10 ) ) : [];

	writeOutput( chr( 10 ) & "=== Events logged ===" & chr( 10 ) );
	for ( e in events )
		writeOutput( "  - #e#" & chr( 10 ) );

	onOpenThread = "";
	onAsyncThread = "";
	for ( e in events ) {
		if ( left( e, 14 ) == "onOpen:thread=" )
			onOpenThread = mid( e, 15, len( e ) );
		if ( left( e, 19 ) == "onOpenAsync:thread=" )
			onAsyncThread = mid( e, 20, len( e ) );
	}

	errors = [];

	if ( onOpenThread == "" )
		arrayAppend( errors, "onOpen did not fire (no log entry)" );
	if ( onAsyncThread == "" )
		arrayAppend( errors, "onOpenAsync did not fire (no log entry)" );
	if ( onOpenThread != "" && onOpenThread == onAsyncThread )
		arrayAppend( errors, "onOpenAsync ran on the SAME thread as onOpen (#onOpenThread#) — not parallel" );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: onOpen (t=#onOpenThread#) and onOpenAsync (t=#onAsyncThread#) ran on different threads" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
