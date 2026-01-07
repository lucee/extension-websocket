<cfscript>
// Simple test to verify websocket extension is loaded and working
try {
	info = websocketInfo();

	// Basic checks
	if ( !isStruct( info ) ) {
		throw( message="websocketInfo() did not return a struct", type="AssertionError" );
	}

	// Check required keys exist
	requiredKeys = [ "mapping", "config", "instances" ];
	for ( key in requiredKeys ) {
		if ( !structKeyExists( info, key ) ) {
			throw( message="websocketInfo() missing required key: #key#", type="AssertionError" );
		}
	}

	// Check version key exists (proves extension bundle loaded)
	if ( structKeyExists( info, "version" ) ) {
		writeOutput( "Extension version: #info.version#" & chr(10) );
	}

	writeOutput( "SUCCESS: websocketInfo() works correctly" & chr(10) );
	writeOutput( "Mapping: #info.mapping ?: 'not set'#" & chr(10) );
	writeOutput( "Instances: #arrayLen( info.instances )#" & chr(10) );
}
catch ( any e ) {
	writeOutput( "FAILED: #e.message#" & chr(10) );
	if ( structKeyExists( e, "detail" ) && len( e.detail ) ) {
		writeOutput( "Detail: #e.detail#" & chr(10) );
	}
	// Return 500 status to fail the CI
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
