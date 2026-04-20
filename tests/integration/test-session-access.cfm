<cfscript>
// Covers three doc claims in one pass:
//   1. wsClient.getSession() accessors — getId(), getUserProperties(), getRequestParameterMap()
//   2. Multiple connections per user / listener — each session has a unique id,
//      user properties are per-connection (not shared across sockets)
//   3. Handshake session isolation — no Lucee cookie/form scope propagates into
//      the listener's synthetic PageContext

writeOutput( "=== Session Access + Multi-tab + Handshake Isolation Test ===" & chr( 10 ) );

logFile = getTempDirectory() & "ws-session-access.log";
if ( fileExists( logFile ) )
	fileDelete( logFile );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available", type="TestSetupError" );

	// Two concurrent connections to the same listener with different query-string tokens
	// — simulates two browser tabs for the same user.
	clientA = new tests.integration.ClientListener();
	clientB = new tests.integration.ClientListener();

	wsA = CreateWebSocketClient( "ws://localhost:8888/ws/SessionAccessListener?token=aaa", clientA );
	sleep( 300 );
	wsB = CreateWebSocketClient( "ws://localhost:8888/ws/SessionAccessListener?token=bbb", clientB );
	sleep( 500 );

	wsA.sendText( "__GETID__" );
	wsB.sendText( "__GETID__" );
	sleep( 300 );

	wsA.sendText( "__GETPARAMS__" );
	wsB.sendText( "__GETPARAMS__" );
	sleep( 300 );

	// A writes a user property, reads it back — should survive across callbacks on the same session
	wsA.sendText( "__PUTPROP__" );
	sleep( 200 );
	wsA.sendText( "__GETPROP__" );
	sleep( 300 );

	// B never wrote — should see NULL (proves userProperties is per-connection, not shared)
	wsB.sendText( "__GETPROP__" );
	sleep( 300 );

	wsA.disconnect();
	wsB.disconnect();
	sleep( 500 );

	aMsgs = clientA.getMessages();
	bMsgs = clientB.getMessages();
	events = fileExists( logFile ) ? fileRead( logFile ).listToArray( chr( 10 ) ) : [];

	writeOutput( "A received: " & aMsgs.toJSON() & chr( 10 ) );
	writeOutput( "B received: " & bMsgs.toJSON() & chr( 10 ) );
	writeOutput( "Listener events: " & events.toJSON() & chr( 10 ) );

	errors = [];

	// ---- getId(): each connection gets its own unique id ----
	aId = "";
	bId = "";
	for ( m in aMsgs )
		if ( left( m, 3 ) == "id:" )
			aId = mid( m, 4, len( m ) );
	for ( m in bMsgs )
		if ( left( m, 3 ) == "id:" )
			bId = mid( m, 4, len( m ) );

	if ( aId == "" || bId == "" )
		arrayAppend( errors, "missing getId() reply: A=[#aId#] B=[#bId#]" );
	else if ( aId == bId )
		arrayAppend( errors, "multi-tab failed: both connections reported the same session id [#aId#]" );

	// ---- getRequestParameterMap(): each client sees its own query-string token ----
	if ( !aMsgs.find( "token:aaa" ) )
		arrayAppend( errors, "A did not see its query-string token; got: " & aMsgs.toJSON() );
	if ( !bMsgs.find( "token:bbb" ) )
		arrayAppend( errors, "B did not see its query-string token; got: " & bMsgs.toJSON() );

	// ---- getUserProperties(): A's put survives, B's scope stays empty ----
	aProp = "";
	for ( m in aMsgs )
		if ( left( m, 5 ) == "prop:" )
			aProp = m;
	expectedAProp = "prop:val-" & aId;
	if ( aProp != expectedAProp )
		arrayAppend( errors, "A getUserProperties roundtrip failed; expected [#expectedAProp#] got: [#aProp#]" );

	bProp = "";
	for ( m in bMsgs )
		if ( left( m, 5 ) == "prop:" )
			bProp = m;
	if ( bProp != "prop:NULL" )
		arrayAppend( errors, "B getUserProperties should be empty (per-connection isolation), got: [#bProp#]" );

	// ---- Handshake isolation: cookie + form scopes empty inside the listener ----
	cookieLines = events.filter( function( e ) { return left( arguments.e, 19 ) == "onOpen:cookieCount="; } );
	for ( e in cookieLines ) {
		if ( right( e, 2 ) != "=0" )
			arrayAppend( errors, "handshake cookie scope not empty: [#e#]" );
	}
	formLines = events.filter( function( e ) { return left( arguments.e, 17 ) == "onOpen:formCount="; } );
	for ( e in formLines ) {
		if ( right( e, 2 ) != "=0" )
			arrayAppend( errors, "handshake form scope not empty: [#e#]" );
	}

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: getSession() accessors + multi-tab + handshake isolation all OK" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
