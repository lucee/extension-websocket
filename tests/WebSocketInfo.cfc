component extends="org.lucee.cfml.test.LuceeTestCase" labels="websocket" {

	function run( testResults, testBox ){
		describe(title="call webSocketInfo()", body=function() {
			it("shouldn't error WIP!", function() {
				var info = webSocketInfo();
				systemOutput( info, true );
				expect( fileExists( info.configFile ) ).toBeTrue( info.configFile );
				var cfg = FileRead( info.configFile );
				expect( isJson( cfg ) ).toBeTrue();
			});
		});
	}

}
