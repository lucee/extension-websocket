component hint="WebSocket listener with short idleTimeout for LDEV-6219 testing" {

	property name="idleTimeout" default=2;

	function onOpen( wsClient ) {
		wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		wsClient.send( "ECHO:" & message );
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {}

}
