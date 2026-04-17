<cfscript>
// Post-restart round-trip. Assumes Lucee has already been restarted via
// trigger-lucee-restart.cfm — the CI job orders these correctly and polls
// for the engine to come back before calling this.
//
// Proves the websocket extension's reflection fallback works: Tomcat's endpoint
// registry still points at the pre-restart class, but the fresh extension injected
// itself into the static slot via reflection. A successful round-trip confirms the
// path works; the CI job separately greps catalina.out for the reflection warning
// to prove the path was actually exercised.

writeOutput( "=== Post-Restart Reflection Round-Trip ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	wsUrl    = "ws://localhost:8888/ws/TestListener";
	listener = new tests.integration.ClientListener();
	ws       = CreateWebSocketClient( wsUrl, listener );
	sleep( 500 );

	ws.sendText( "reflectionCheck" );
	sleep( 500 );

	ws.disconnect();
	sleep( 500 );

	received = listener.getMessages();
	writeOutput( "Received: " & received.toJSON() & chr( 10 ) );

	errors = [];
	if ( !received.find( "ECHO:reflectionCheck" ) )
		arrayAppend( errors, "expected ECHO:reflectionCheck post-restart; got: " & received.toJSON() );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: round-trip works post-restart — CI will confirm the reflection warning in catalina.out" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
