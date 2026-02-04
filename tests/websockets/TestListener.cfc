component hint="WebSocket listener for integration testing" {

	// Static storage for test results - tracks what events fired
	static {
		events = [];
		messages = [];
		clientCount = 0;
	}

	public static function onFirstOpen( wsClients ) {
		arrayAppend( static.events, "onFirstOpen" );
		static.clientCount = wsClients.size();
	}

	function onOpen( wsClient ) {
		arrayAppend( static.events, "onOpen" );
		// Echo back confirmation
		wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		arrayAppend( static.events, "onMessage" );
		arrayAppend( static.messages, message );
		// Echo the message back with prefix
		wsClient.send( "ECHO:" & message );
	}

	function onClose( wsClient, reasonPhrase ) {
		arrayAppend( static.events, "onClose" );
	}

	function onError( wsClient, cfCatch ) {
		arrayAppend( static.events, "onError:#cfCatch.message#" );
	}

	public static function onLastClose() {
		arrayAppend( static.events, "onLastClose" );
	}

	// Helper to get test results (called via CFML)
	public static struct function getTestResults() {
		return {
			"events": static.events,
			"messages": static.messages,
			"clientCount": static.clientCount
		};
	}

	// Reset for fresh test run
	public static void function reset() {
		static.events = [];
		static.messages = [];
		static.clientCount = 0;
	}

}
