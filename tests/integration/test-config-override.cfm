<cfscript>
// Asserts the LUCEE_WEBSOCKET_CONFIG env var override is honoured and a listener
// living under a non-default `directory` is reachable.
//
// This test is ONLY meaningful when the surrounding CI job has:
//   - written /tmp/ws-alt-config.json with {"directory":"/tmp/ws-alt-listeners/"}
//   - copied TestListener.cfc to /tmp/ws-alt-listeners/
//   - NOT copied any listeners to the default {lucee-config}/websockets/ path
//   - exported LUCEE_WEBSOCKET_CONFIG=/tmp/ws-alt-config.json before starting Lucee

writeOutput( "=== Config Override Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	expectedConfigFile = "/tmp/ws-alt-config.json";
	expectedDirectory  = "/tmp/ws-alt-listeners/";

	errors = [];

	// 1. websocketInfo() should report the alt config path (proves env var was read)
	info = websocketInfo();
	writeOutput( "websocketInfo.configFile = " & ( info.configFile ?: "<missing>" ) & chr( 10 ) );
	writeOutput( "websocketInfo.config.directory = " & ( info.config.directory ?: "<missing>" ) & chr( 10 ) );

	if ( ( info.configFile ?: "" ) != expectedConfigFile )
		arrayAppend( errors, "expected configFile=#expectedConfigFile#, got: " & ( info.configFile ?: "<missing>" ) );

	if ( ( info.config.directory ?: "" ) != expectedDirectory )
		arrayAppend( errors, "expected config.directory=#expectedDirectory#, got: " & ( info.config.directory ?: "<missing>" ) );

	// 2. Listener at the alt path should be reachable (proves directory setting was honoured)
	wsUrl = "ws://localhost:8888/ws/TestListener";
	listener = new tests.integration.ClientListener();
	ws = CreateWebSocketClient( wsUrl, listener );
	sleep( 500 );

	ws.sendText( "ping" );
	sleep( 500 );

	ws.disconnect();
	sleep( 500 );

	received = listener.getMessages();
	writeOutput( "Received: " & received.toJSON() & chr( 10 ) );

	if ( !received.find( "ECHO:ping" ) )
		arrayAppend( errors, "expected ECHO:ping from listener at alt directory; got: " & received.toJSON() );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: env-var config override + alt directory + round-trip all work" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
