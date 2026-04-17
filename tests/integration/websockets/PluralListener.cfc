component hint="Exercises the wsClients (plural) object passed to onFirstOpen" {

	static {
		wsclientsRef = "";
	}

	public static function onFirstOpen( wsClients ) {
		static.wsclientsRef = arguments.wsClients;
	}

	function onOpen( wsClient ) {
		arguments.wsClient.send( "CONNECTED" );
	}

	function onMessage( wsClient, message ) {
		if ( arguments.message == "__SIZE__" ) {
			arguments.wsClient.send( "size:" & static.wsclientsRef.size() );
			return;
		}
		if ( arguments.message == "__GETCLIENTS__" ) {
			var clients = static.wsclientsRef.getClients();
			arguments.wsClient.send( "getClients:" & arrayLen( clients ) );
			return;
		}
		if ( arguments.message == "__BROADCAST_VIA_PLURAL__" ) {
			static.wsclientsRef.broadcast( "PLURAL_BCAST" );
			return;
		}
		if ( arguments.message == "__CLOSE_ALL__" ) {
			arguments.wsClient.send( "CLOSING_ALL" );
			static.wsclientsRef.close();
			return;
		}
		arguments.wsClient.send( "ECHO:" & arguments.message );
	}

	function onClose( wsClient, reasonPhrase ) {}

	function onError( wsClient, cfCatch ) {}

}
