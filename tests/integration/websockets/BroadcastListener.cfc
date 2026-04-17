component hint="Calls wsClient.broadcast() when it receives the sentinel message" {

	function onOpen( wsClient ) {
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		if ( arguments.message == "__BROADCAST__" ) {
			arguments.wsClient.broadcast( "BCAST" );
			return;
		}
		if ( arguments.message == "__CHECK_ISOPEN__" ) {
			arguments.wsClient.send( "isOpen:" & arguments.wsClient.isOpen() & ",isClose:" & arguments.wsClient.isClose() );
			return;
		}
		if ( arguments.message == "__CLOSE__" ) {
			arguments.wsClient.send( "CLOSING" );
			arguments.wsClient.close();
			return;
		}
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {}

}
