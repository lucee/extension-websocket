<cfscript>
// Triggers a Lucee engine restart via the server admin API.
// Tomcat stays up — only the CFMLEngine reloads. This is the LDEV-6221 scenario:
// when the fresh engine's extension classes try to register their endpoint, Tomcat
// already has the endpoint class from before the restart, so the extension falls
// back to inject() via reflection.
//
// The CI job curls this, then waits for Lucee to come back, then runs
// test-reflection-restart.cfm which proves round-trip still works.

writeOutput( "=== Triggering Lucee engine restart ===" & chr( 10 ) );

try {
	adminPassword = server.system.environment.LUCEE_ADMIN_PASSWORD ?: "";
	if ( adminPassword == "" )
		throw( message="LUCEE_ADMIN_PASSWORD env var not set", type="TestSetupError" );

	cfadmin(
		action   = "restart",
		type     = "server",
		password = adminPassword
	);

	writeOutput( "Restart call returned — engine is reloading" & chr( 10 ) );
}
catch ( any e ) {
	writeOutput( "FAILED to trigger restart: " & e.message & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Restart Trigger Failed" );
}
</cfscript>
