component hint="Sends binary frames back to the client on sentinel input" {

	function onOpen( wsClient ) {
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		if ( arguments.message == "__BINARY__" ) {
			// Return binary — WSUtil.send() auto-detects isBinary and sends a binary frame
			return toBinary( toBase64( "BINARYPAYLOAD" ) );
		}
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {}

}
