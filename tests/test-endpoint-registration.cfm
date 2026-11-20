<cfscript>
// LDEV-6972: the startup hook can be instantiated more than once, every instance runs its own
// Registrar thread. Both threads used to read the "lucee.websocket.endpoint" system property as
// empty and both called ServerContainer.addEndpoint(), Tomcat then failed the deployment and all
// /ws/ URLs returned 404. This test hammers the (JVM wide) claim and the websocket.json creation
// concurrently, the startup log itself is checked by the CI step.
BUNDLE = "org.lucee.websocket.extension";
THREADS = 16;
ROUNDS = 25;

function assert( required boolean condition, required string message ) {
	if ( !arguments.condition ) throw( message=arguments.message, type="AssertionError" );
}

function joinAll( required array names ) {
	cfthread( action="join", name=arrayToList( arguments.names ), timeout=30000 );
	for ( var n in arguments.names ) {
		var t = cfthread[ n ];
		if ( structKeyExists( t, "error" ) ) throw( message="thread [#n#] failed: #t.error.message#", type="AssertionError" );
		assert( t.status == "COMPLETED", "thread [#n#] did not complete, status [#t.status#]" );
	}
}

function loadExtensionClass( required string className ) {
	try {
		return createObject( "java", arguments.className, BUNDLE );
	}
	catch ( any e ) {
		return createObject( "java", arguments.className );
	}
}

try {
	websocketInfo(); // make sure the extension is initialised
	factoryClass = loadExtensionClass( "org.lucee.extension.websocket.WebSocketEndpointFactory" );
	wsUtil = loadExtensionClass( "org.lucee.extension.websocket.util.WSUtil" );
	endpointClass = createObject( "java", "java.lang.Class" ).forName( "java.lang.String" );

	// 1. the real registration: exactly one endpoint class is registered JVM wide
	registered = createObject( "java", "java.lang.System" ).getProperties().get( "lucee.websocket.endpoint" );
	assert( !isNull( registered ), "system property [lucee.websocket.endpoint] is not set, endpoint was never registered" );
	assert( registered.getClass().getName() == "java.lang.Class", "system property [lucee.websocket.endpoint] is not a class" );
	writeOutput( "registered endpoint: #registered.getName()#" & chr( 10 ) );

	// 2. concurrent claims on one registry: only one caller may add the endpoint
	for ( round = 1; round <= ROUNDS; round++ ) {
		props = createObject( "java", "java.util.Properties" ).init();
		// odd rounds start with a foreign (non class) value, that must be replaced exactly once as well
		if ( round % 2 ) props.put( "lucee.websocket.endpoint", "foreign" );
		claims = createObject( "java", "java.util.concurrent.atomic.AtomicInteger" ).init( 0 );
		others = createObject( "java", "java.util.concurrent.atomic.AtomicInteger" ).init( 0 );
		gate = createObject( "java", "java.util.concurrent.CountDownLatch" ).init( 1 );
		names = [];
		for ( i = 1; i <= THREADS; i++ ) {
			name = "ldev6972_claim_#round#_#i#_#createUUID()#";
			arrayAppend( names, name );
			cfthread( name=name, action="run" ) {
				gate.await();
				thread.r = factoryClass.claimEndpoint( props, endpointClass );
				if ( isNull( thread.r ) ) claims.incrementAndGet();
				else if ( thread.r.getName() == "java.lang.String" ) others.incrementAndGet();
			}
		}
		gate.countDown();
		joinAll( names );
		assert( claims.get() == 1, "round #round#: expected exactly 1 caller to claim the endpoint, got [#claims.get()#]" );
		assert( others.get() == THREADS - 1, "round #round#: expected #THREADS - 1# callers to see the existing endpoint, got [#others.get()#]" );
		assert( props.get( "lucee.websocket.endpoint" ).getName() == "java.lang.String", "round #round#: registry does not hold the endpoint class" );
	}
	writeOutput( "SUCCESS: #ROUNDS# rounds of #THREADS# concurrent claims, exactly one claim each" & chr( 10 ) );

	// 3. concurrent creation of the default websocket.json must not fail
	resUtil = createObject( "java", "lucee.loader.engine.CFMLEngineFactory" ).getInstance().getResourceUtil();
	utf8 = createObject( "java", "java.nio.charset.Charset" ).forName( "UTF-8" );
	cfg = getPageContext().getConfig();
	for ( round = 1; round <= ROUNDS; round++ ) {
		dir = getTempDirectory() & "ldev6972-" & createUUID();
		res = resUtil.toResourceNotExisting( getPageContext(), dir & "/websocket.json" );
		gate = createObject( "java", "java.util.concurrent.CountDownLatch" ).init( 1 );
		names = [];
		for ( i = 1; i <= THREADS; i++ ) {
			name = "ldev6972_config_#round#_#i#_#createUUID()#";
			arrayAppend( names, name );
			cfthread( name=name, action="run" ) {
				gate.await();
				wsUtil.createConfigFileIfMissing( cfg, res, utf8 );
			}
		}
		gate.countDown();
		joinAll( names );
		json = deserializeJSON( fileRead( dir & "/websocket.json" ) );
		assert( structKeyExists( json, "directory" ), "round #round#: websocket.json has no [directory]" );
		directoryDelete( dir, true );
	}
	writeOutput( "SUCCESS: #ROUNDS# rounds of #THREADS# concurrent websocket.json creations, no error" & chr( 10 ) );
}
catch ( any e ) {
	writeOutput( "FAILED: #e.message#" & chr( 10 ) );
	writeOutput( e.stacktrace );
	cfheader( statuscode=500, statustext="Test Failed" );
}
</cfscript>
