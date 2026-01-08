component {

	variables.received = [];

	function onMessage( message ) {
		arrayAppend( variables.received, message );
	}

	function onClose() {}

	function onError( type, cause ) {
		systemOutput( "WebSocket client error: #type# - #cause.getMessage()#", true );
	}

	array function getMessages() {
		return variables.received;
	}

}
