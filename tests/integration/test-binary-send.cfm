<cfscript>
// Asserts server → client binary frames work:
// listener returns binary from onMessage → client's onBinaryMessage receives it.
//
// Known asymmetry: client → server binary is NOT supported. The server extension's
// @OnMessage handler only binds to text frames (see JakartaWebSocketEndpoint.java).

writeOutput( "=== Binary Send (server → client) Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	wsUrl = "ws://localhost:8888/ws/BinaryListener";

	client = new tests.integration.BinaryClientListener();
	ws = CreateWebSocketClient( wsUrl, client );
	sleep( 500 );

	// Trigger the listener to return binary
	ws.sendText( "__BINARY__" );
	sleep( 500 );

	ws.disconnect();
	sleep( 500 );

	binMsgs = client.getBinaryMessages();
	textMsgs = client.getMessages();
	writeOutput( "Binary messages received: " & arrayLen( binMsgs ) & chr( 10 ) );
	writeOutput( "Text messages received: " & textMsgs.toJSON() & chr( 10 ) );

	errors = [];

	if ( arrayLen( binMsgs ) == 0 )
		arrayAppend( errors, "Expected at least one binary message, received none" );
	else {
		// Decode and check payload matches what the listener returned
		// toBinary( toBase64( "BINARYPAYLOAD" ) ) === charsetDecode("BINARYPAYLOAD", "utf-8")
		expected = "BINARYPAYLOAD";
		actual = charsetEncode( binMsgs[ 1 ], "utf-8" );
		if ( actual != expected )
			arrayAppend( errors, "Binary payload mismatch: expected [#expected#], got [#actual#]" );
	}

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: server → client binary send works" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
