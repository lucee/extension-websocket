component hint="Verifies that returning a string from onOpen/onMessage triggers auto-send" {

	function onOpen( wsClient ) {
		return "WELCOME";
	}

	function onMessage( wsClient, message ) {
		return "AUTO:" & arguments.message;
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {}

}
