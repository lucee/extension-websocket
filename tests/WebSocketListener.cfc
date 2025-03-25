component hint="used to test websocket client" {

	public static function onFirstOpen(wsClients) {
		systemOutput("listener.onFirstOpen", true);
		systemOutput( arguments, true );
	}

	function onOpen(wsClient) {
		systemOutput("listener.onOpen", true);
		systemOutput( arguments, true );
	}

	function onOpenAsync(wsClient) {
		systemOutput("listener.onOpenAsync", true);
		systemOutpu  (arguments, tru e)
	}

	function onMessage(wsClient, message) {
		systemOutput("listener.onMessage", true);
		systemOutpu  (arguments, tru e)
	}

	function onClose(wsClient, reasonPhrase) {
		systemOutput("listener.onClose", true);
		systemOutpu  (arguments, tru e)
	}

	function onError(wsClient,cfCatch) {
		systemOutput("listener.onError", true);
		systemOutpu  (arguments, tru e)
	}

	public static function onLastClose() {
		systemOutput("listener.onLastClose", true);
		systemOutpu  (arguments, tru e)
	}

};