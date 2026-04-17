<cfscript>
// Triggers a hot re-install of the websocket extension via cfadmin and verifies
// the round-trip still works afterwards. The CI job then greps catalina.out for
// the "calling [onOpen] via reflection" warning to prove the reflection fallback
// (LDEV-6221) was actually exercised.
//
// Preconditions set by the surrounding CI job:
//   - LUCEE_ADMIN_PASSWORD env var is set and matches Lucee's server admin password
//   - WS_EXT_LEX_PATH env var points at the built .lex on disk
//   - A listener (TestListener) is reachable at ws://localhost:8888/ws/TestListener

writeOutput( "=== Reflection Hot-Reinstall Test ===" & chr( 10 ) );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	adminPassword = server.system.environment.LUCEE_ADMIN_PASSWORD ?: "";
	lexPath       = server.system.environment.WS_EXT_LEX_PATH    ?: "";

	if ( adminPassword == "" )
		throw( message="LUCEE_ADMIN_PASSWORD env var not set", type="TestSetupError" );
	if ( lexPath == "" || !fileExists( lexPath ) )
		throw( message="WS_EXT_LEX_PATH does not resolve to a file: [#lexPath#]", type="TestSetupError" );

	writeOutput( "Re-installing extension from: " & lexPath & chr( 10 ) );

	// Trigger re-install via admin API. This is synchronous — when it returns,
	// the new extension's startup hook has already run and either:
	//   - re-registered endpoints normally (if Tomcat allowed it), OR
	//   - fallen back to inject() via reflection (the LDEV-6221 path)
	cfadmin(
		action   = "updateRHExtension",
		type     = "server",
		password = adminPassword,
		source   = lexPath
	);

	writeOutput( "Re-install complete. Running round-trip test..." & chr( 10 ) );

	// Round-trip — must still work post-reinstall
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
		arrayAppend( errors, "expected ECHO:reflectionCheck after re-install; got: " & received.toJSON() );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: round-trip works after hot re-install — CI will verify the reflection warning in catalina.out" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
