<cfscript>
// Asserts that returning a string from onOpen/onMessage triggers auto-send
// (no explicit wsClient.send() call in the listener).

writeOutput( "=== Return-Value Auto-Send Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	wsUrl = "ws://localhost:8888/ws/ReturnValueListener";
	client = new tests.integration.ClientListener();
	ws = CreateWebSocketClient( wsUrl, client );

	// onOpen returns "WELCOME" — should be sent automatically
	sleep( 500 );

	// onMessage returns "AUTO:" & message — should be sent automatically
	ws.sendText( "ping" );
	sleep( 500 );

	ws.disconnect();
	sleep( 500 );

	received = client.getMessages();
	writeOutput( "Received: " & received.toJSON() & chr( 10 ) );

	errors = [];
	if ( !received.find( "WELCOME" ) )
		arrayAppend( errors, "expected WELCOME from return-value onOpen, got: " & received.toJSON() );
	if ( !received.find( "AUTO:ping" ) )
		arrayAppend( errors, "expected AUTO:ping from return-value onMessage, got: " & received.toJSON() );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: return-value auto-send works" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
