<cfscript>
// Integration test - connects via websocket client and validates the full lifecycle
// Requires: websocket extension (server) + websocket-client extension
//
// NOTE: We can't use invoke() to access the static scope of the TestListener
// because CFML loads a separate instance vs what the websocket extension uses.
// Instead, we validate by checking client-received messages which proves the
// server-side CFC is working (CONNECTED from onOpen, ECHO from onMessage).

writeOutput( "=== WebSocket Integration Test ===" & chr( 10 ) );

try {
	// Check websocket-client extension is available
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) ) {
		throw( message="CreateWebSocketClient function not available - websocket-client extension not installed", type="TestSetupError" );
	}

	// Check server extension loaded
	info = websocketInfo();
	writeOutput( "Server extension loaded" & chr( 10 ) );
	writeOutput( "Mapping: #info.mapping ?: 'not set'#" & chr( 10 ) );

	// Determine websocket URL (same host/port as HTTP)
	wsUrl = "ws://localhost:8888/ws/TestListener";
	writeOutput( "Connecting to: #wsUrl#" & chr( 10 ) );

	// Client listener component to receive messages
	clientListener = new tests.ClientListener();

	// Connect
	ws = CreateWebSocketClient( wsUrl, clientListener );
	writeOutput( "Connected!" & chr( 10 ) );

	// Give server time to process onOpen
	sleep( 500 );

	// Send a test message
	testMsg = "Hello from test at #now()#";
	ws.sendText( testMsg );
	writeOutput( "Sent: #testMsg#" & chr( 10 ) );

	// Wait for echo response
	sleep( 500 );

	// Close connection
	ws.disconnect();
	writeOutput( "Disconnected" & chr( 10 ) );

	// Wait for close to process
	sleep( 500 );

	// Check client received messages - this validates the server CFC is working
	received = clientListener.getMessages();
	writeOutput( chr( 10 ) & "=== Client Received ===" & chr( 10 ) );
	writeOutput( "Messages: #arrayLen( received )#" & chr( 10 ) );
	for ( msg in received ) {
		writeOutput( "  - #msg#" & chr( 10 ) );
	}

	// Validate based on what the client received
	errors = [];

	// Check we got CONNECTED message (proves onOpen fired)
	hasConnected = false;
	for ( msg in received ) {
		if ( msg == "CONNECTED" ) {
			hasConnected = true;
			break;
		}
	}
	if ( !hasConnected ) {
		arrayAppend( errors, "Did not receive CONNECTED message (onOpen not working)" );
	}

	// Check we got ECHO:testMsg message (proves onMessage fired)
	hasEcho = false;
	expectedEcho = "ECHO:" & testMsg;
	for ( msg in received ) {
		if ( msg == expectedEcho ) {
			hasEcho = true;
			break;
		}
	}
	if ( !hasEcho ) {
		arrayAppend( errors, "Did not receive ECHO message (onMessage not working)" );
	}

	// Check websocketInfo instances after connection closed
	infoAfter = websocketInfo();
	writeOutput( chr( 10 ) & "=== websocketInfo() after test ===" & chr( 10 ) );
	writeOutput( "Instances: #arrayLen( infoAfter.instances )#" & chr( 10 ) );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors ) {
			writeOutput( "  - #err#" & chr( 10 ) );
		}
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: WebSocket lifecycle working correctly!" & chr( 10 ) );
		writeOutput( "  - onOpen fired (received CONNECTED)" & chr( 10 ) );
		writeOutput( "  - onMessage fired (received ECHO)" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
