component hint="Sends binary frames back to the client on sentinel input" {

	function onOpen( wsClient ) {
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		if ( arguments.message == "__BINARY__" ) {
			// charsetDecode() returns a byte[] — WSUtil.send() detects isBinary and
			// routes through sendBinary(). toBinary(toBase64(...)) was observed to fall
			// through to the text path on return, so we use charsetDecode and call
			// .send() explicitly to stay on the documented binary-send path.
			arguments.wsClient.send( charsetDecode( "BINARYPAYLOAD", "utf-8" ) );
			return;
		}
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {}

}
