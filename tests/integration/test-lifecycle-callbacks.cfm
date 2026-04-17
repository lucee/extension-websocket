<cfscript>
// Asserts every documented lifecycle callback fires at the correct point.
// Uses LifecycleListener which writes each callback invocation to a file
// (static/variables scope is not reliably readable from outside the websocket
// PageContext — see notes in test-websocket-client.cfm).

writeOutput( "=== Lifecycle Callbacks Test ===" & chr( 10 ) );

logFile = getTempDirectory() & "ws-events-lifecycle.log";

// Reset log — we want a clean slate
if ( fileExists( logFile ) )
	fileDelete( logFile );

try {
	if ( !structKeyExists( getFunctionList(), "CreateWebSocketClient" ) )
		throw( message="CreateWebSocketClient not available - websocket-client extension missing", type="TestSetupError" );

	wsUrl = "ws://localhost:8888/ws/LifecycleListener";

	// ---- Client A connects → expect onFirstOpen + onOpen ----
	clientA = new tests.integration.ClientListener();
	wsA = CreateWebSocketClient( wsUrl, clientA );
	sleep( 500 );

	// ---- Client A sends a message → expect onMessage ----
	wsA.sendText( "hello" );
	sleep( 500 );

	// ---- Client B connects → expect only onOpen (no second onFirstOpen) ----
	clientB = new tests.integration.ClientListener();
	wsB = CreateWebSocketClient( wsUrl, clientB );
	sleep( 500 );

	// ---- Client A triggers an error → expect onError ----
	wsA.sendText( "__THROW__" );
	sleep( 500 );

	// ---- Client A disconnects → expect onClose, no onLastClose yet ----
	wsA.disconnect();
	sleep( 500 );

	// ---- Client B disconnects → expect onClose + onLastClose ----
	wsB.disconnect();
	sleep( 1000 );

	// ---- Read event log and assert ----
	events = fileRead( logFile ).listToArray( chr( 10 ) );

	writeOutput( chr( 10 ) & "=== Events logged ===" & chr( 10 ) );
	for ( e in events )
		writeOutput( "  - #e#" & chr( 10 ) );

	errors = [];

	// Required events (order not strictly checked, just presence + count)
	required = {
		"onFirstOpen": 1,
		"onOpen": 2,
		"onMessage:hello": 1,
		"onMessage:__THROW__": 1,
		"onError:sentinel-error": 1,
		"onClose": 2,
		"onLastClose": 1
	};

	for ( eventName in required ) {
		wanted = required[ eventName ];
		found = 0;
		for ( e in events ) {
			if ( e == eventName )
				found++;
		}
		if ( found != wanted )
			arrayAppend( errors, "expected [#eventName#] #wanted# time(s), got #found#" );
	}

	// onFirstOpen must appear before first onOpen
	firstOpenIdx = events.find( "onFirstOpen" );
	firstOnOpenIdx = events.find( "onOpen" );
	if ( firstOpenIdx == 0 || firstOnOpenIdx == 0 || firstOpenIdx >= firstOnOpenIdx )
		arrayAppend( errors, "onFirstOpen should precede onOpen (got onFirstOpen@#firstOpenIdx#, onOpen@#firstOnOpenIdx#)" );

	if ( arrayLen( errors ) ) {
		writeOutput( chr( 10 ) & "FAILED:" & chr( 10 ) );
		for ( err in errors )
			writeOutput( "  - #err#" & chr( 10 ) );
		cfheader( statuscode=500, statustext="Test Failed" );
	}
	else {
		writeOutput( chr( 10 ) & "SUCCESS: All lifecycle callbacks fired as expected" & chr( 10 ) );
	}
}
catch ( any e ) {
	writeOutput( "FAILED with exception:" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
