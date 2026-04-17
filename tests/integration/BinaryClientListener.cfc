component hint="Client-side listener that captures both text and binary messages" {

	variables.received = [];
	variables.receivedBinary = [];

	function onMessage( message ) {
		arrayAppend( variables.received, arguments.message );
	}

	function onBinaryMessage( binary ) {
		arrayAppend( variables.receivedBinary, arguments.binary );
	}

	function onClose() {}

	function onError( type, cause ) {
		systemOutput( "WebSocket client error: #arguments.type# - #arguments.cause.getMessage()#", true );
	}

	array function getMessages() {
		return variables.received;
	}

	array function getBinaryMessages() {
		return variables.receivedBinary;
	}

}
