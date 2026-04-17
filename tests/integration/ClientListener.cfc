component {

	variables.received = [];
	variables.closed = false;

	function onMessage( message ) {
		arrayAppend( variables.received, message );
	}

	function onClose() {
		variables.closed = true;
	}

	function onError( type, cause ) {
		systemOutput( "WebSocket client error: #type# - #cause.getMessage()#", true );
	}

	array function getMessages() {
		return variables.received;
	}

	boolean function isClosed() {
		return variables.closed;
	}

}
