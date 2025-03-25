component extends="org.lucee.cfml.test.LuceeTestCase" labels="websocket" {

	function run( testResults, testBox ){
		describe(title="ImageWrite Function", body=function() {
			it("should write an image to a file", function() {
				webSocketInfo()
			});
		});
	}

}
