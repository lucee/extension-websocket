<cfscript>
// Asserts wsClients (plural, passed to onFirstOpen) exposes size(), getClients(), broadcast()
// and that the reference stays usable across later callback invocations.

writeOutput( "=== wsClients (plural) Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	wsUrl = "ws://localhost:8888/ws/PluralListener";

	clientA = new tests.integration.ClientListener();
	clientB = new tests.integration.ClientListener();

	wsA = CreateWebSocketClient( wsUrl, clientA );
	wsB = CreateWebSocketClient( wsUrl, clientB );
	sleep( 500 );

	// ---- size() — both connected, expect 2 ----
	wsA.sendText( "__SIZE__" );
	sleep( 300 );

	// ---- getClients() — expect array of length 2 ----
	wsA.sendText( "__GETCLIENTS__" );
	sleep( 300 );

	// ---- broadcast via plural — both receive PLURAL_BCAST ----
	wsA.sendText( "__BROADCAST_VIA_PLURAL__" );
	sleep( 500 );

	wsA.disconnect();
	wsB.disconnect();
	sleep( 500 );

	aMsgs = clientA.getMessages();
	bMsgs = clientB.getMessages();
	writeOutput( "A received: " & aMsgs.toJSON() & chr( 10 ) );
	writeOutput( "B received: " & bMsgs.toJSON() & chr( 10 ) );

	errors = [];

	if ( !aMsgs.find( "size:2" ) )
		arrayAppend( errors, "wsClients.size() did not report 2; got: " & aMsgs.toJSON() );
	if ( !aMsgs.find( "getClients:2" ) )
		arrayAppend( errors, "wsClients.getClients() did not return 2 entries; got: " & aMsgs.toJSON() );
	if ( !aMsgs.find( "PLURAL_BCAST" ) || !bMsgs.find( "PLURAL_BCAST" ) )
		arrayAppend( errors, "wsClients.broadcast() did not reach all clients; A=#aMsgs.toJSON()# B=#bMsgs.toJSON()#" );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: wsClients size/getClients/broadcast working" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
