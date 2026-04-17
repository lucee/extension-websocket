<cfscript>
// Asserts wsClient.broadcast() reaches every connected client,
// and wsClient.isOpen() reports true while a callback is active.

writeOutput( "=== wsClient broadcast + isOpen Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	wsUrl = "ws://localhost:8888/ws/BroadcastListener";

	clientA = new tests.integration.ClientListener();
	clientB = new tests.integration.ClientListener();

	wsA = CreateWebSocketClient( wsUrl, clientA );
	wsB = CreateWebSocketClient( wsUrl, clientB );
	sleep( 500 );

	// ---- broadcast: A sends sentinel, both A and B should receive "BCAST" ----
	wsA.sendText( "__BROADCAST__" );
	sleep( 500 );

	// ---- isOpen check: A sends sentinel, listener replies with isOpen:true ----
	wsA.sendText( "__CHECK_ISOPEN__" );
	sleep( 500 );

	wsA.disconnect();
	wsB.disconnect();
	sleep( 500 );

	aMsgs = clientA.getMessages();
	bMsgs = clientB.getMessages();
	writeOutput( "A received: " & aMsgs.toJSON() & chr( 10 ) );
	writeOutput( "B received: " & bMsgs.toJSON() & chr( 10 ) );

	errors = [];

	if ( !aMsgs.find( "BCAST" ) )
		arrayAppend( errors, "Client A did not receive BCAST; broadcast did not reach sender" );
	if ( !bMsgs.find( "BCAST" ) )
		arrayAppend( errors, "Client B did not receive BCAST; broadcast did not reach other clients" );
	isOpenReply = "";
	for ( m in aMsgs ) {
		if ( m.startsWith( "isOpen:" ) ) {
			isOpenReply = m;
			break;
		}
	}
	if ( isOpenReply == "" )
		arrayAppend( errors, "expected isOpen reply during active callback; got: " & aMsgs.toJSON() );
	else if ( !isOpenReply.contains( "isOpen:true" ) && !isOpenReply.contains( "isOpen:YES" ) )
		arrayAppend( errors, "isOpen did not report true during active callback; got: " & isOpenReply );
	else if ( !isOpenReply.contains( "isClose:false" ) && !isOpenReply.contains( "isClose:NO" ) )
		arrayAppend( errors, "isClose did not report false during active callback; got: " & isOpenReply );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: wsClient.broadcast() + isOpen() working" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
