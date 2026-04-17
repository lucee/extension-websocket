<cfscript>
// Asserts server-initiated close — both single-client (wsClient.close) and
// all-clients (wsClients.close from the plural reference).

writeOutput( "=== Server-Initiated Close Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	errors = [];

	// ---- wsClient.close() (single client) ----
	writeOutput( chr( 10 ) & "-- wsClient.close() --" & chr( 10 ) );

	singleUrl = "ws://localhost:8888/ws/BroadcastListener";
	solo = new tests.integration.ClientListener();
	wsSolo = CreateWebSocketClient( singleUrl, solo );
	sleep( 500 );

	wsSolo.sendText( "__CLOSE__" );
	// Server sends CLOSING then calls wsClient.close() — give it time for the frame to arrive
	sleep( 1000 );

	if ( !solo.isClosed() )
		arrayAppend( errors, "wsClient.close() did not trigger client onClose; client.isClosed()=false" );
	if ( !solo.getMessages().find( "CLOSING" ) )
		arrayAppend( errors, "expected 'CLOSING' message before server-initiated close; got: " & solo.getMessages().toJSON() );

	writeOutput( "Solo received: " & solo.getMessages().toJSON() & " closed=" & solo.isClosed() & chr( 10 ) );

	// ---- wsClients.close() (plural, closes every client on the listener) ----
	writeOutput( chr( 10 ) & "-- wsClients.close() --" & chr( 10 ) );

	pluralUrl = "ws://localhost:8888/ws/PluralListener";
	clientA = new tests.integration.ClientListener();
	clientB = new tests.integration.ClientListener();

	wsA = CreateWebSocketClient( pluralUrl, clientA );
	wsB = CreateWebSocketClient( pluralUrl, clientB );
	sleep( 500 );

	wsA.sendText( "__CLOSE_ALL__" );
	sleep( 1500 );

	if ( !clientA.isClosed() )
		arrayAppend( errors, "wsClients.close() did not close client A" );
	if ( !clientB.isClosed() )
		arrayAppend( errors, "wsClients.close() did not close client B (the non-initiating client)" );

	writeOutput( "A received: " & clientA.getMessages().toJSON() & " closed=" & clientA.isClosed() & chr( 10 ) );
	writeOutput( "B received: " & clientB.getMessages().toJSON() & " closed=" & clientB.isClosed() & chr( 10 ) );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: wsClient.close() and wsClients.close() both work" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
