<cfscript>
// Integration test - connects via websocket client and validates the full lifecycle
// Requires: websocket extension (server) + websocket-client extension

writeOutput( "=== WebSocket Integration Test ===" & chr( 10 ) );

try {
	// Check websocket-client extension is available
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) ) {
		throw( message="CreateWebSocketClient function not available - websocket-client extension not installed", type="TestSetupError" );
	}

	// Check server extension loaded
	info = websocketInfo();
	writeOutput( "Server extension loaded, instances: #arrayLen( info.instances )#" & chr( 10 ) );

	// Reset the test listener state via static method
	invoke( "websockets.TestListener", "reset" );

	// Determine websocket URL (same host/port as HTTP)
	wsUrl = "ws://localhost:8888/ws/TestListener";
	writeOutput( "Connecting to: #wsUrl#" & chr( 10 ) );

	// Client listener component to receive messages
	received = [];
	clientListener = new component {
		function onMessage( message ) {
			arrayAppend( received, message );
		}
		function onClose() {}
		function onError( type, cause ) {}
	};

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

	// Check server-side events via static method
	results = invoke( "websockets.TestListener", "getTestResults" );
	writeOutput( chr( 10 ) & "=== Server Events ===" & chr( 10 ) );
	writeOutput( "Events: #arrayToList( results.events )#" & chr( 10 ) );
	writeOutput( "Messages received: #arrayLen( results.messages )#" & chr( 10 ) );

	// Validate
	errors = [];

	if ( !arrayFind( results.events, "onFirstOpen" ) ) {
		arrayAppend( errors, "onFirstOpen not called" );
	}
	if ( !arrayFind( results.events, "onOpen" ) ) {
		arrayAppend( errors, "onOpen not called" );
	}
	if ( !arrayFind( results.events, "onMessage" ) ) {
		arrayAppend( errors, "onMessage not called" );
	}
	if ( arrayLen( results.messages ) == 0 || results.messages[ 1 ] != testMsg ) {
		arrayAppend( errors, "Message not received correctly on server" );
	}

	// Check client received echo
	writeOutput( chr( 10 ) & "=== Client Received ===" & chr( 10 ) );
	writeOutput( "Messages: #arrayLen( received )#" & chr( 10 ) );
	for ( msg in received ) {
		writeOutput( "  - #msg#" & chr( 10 ) );
	}

	// Should have received CONNECTED and ECHO:testMsg
	if ( arrayLen( received ) < 2 ) {
		arrayAppend( errors, "Client should have received at least 2 messages (CONNECTED + echo)" );
	}

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors ) {
			writeOutput( "  - #err#" & chr( 10 ) );
		}
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: All lifecycle events fired correctly!" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
