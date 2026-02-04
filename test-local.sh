#!/bin/bash
# Local test script for websocket extension
# Usage: ./test-local.sh [6.2|7.0|7.1]  (default: 7.0)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Parse version argument
LUCEE_VERSION="${1:-7.0}"

case "$LUCEE_VERSION" in
	6.2)
		TOMCAT="tomcat-9"
		JAVA_MIN=11
		;;
	7.0)
		TOMCAT="tomcat-11"
		JAVA_MIN=21
		;;
	7.1)
		TOMCAT="tomcat-11"
		JAVA_MIN=21
		;;
	*)
		echo "Usage: $0 [6.2|7.0|7.1]"
		exit 1
		;;
esac

PORT=8888
TEST_DIR="$SCRIPT_DIR/test-output/lucee-express-$LUCEE_VERSION"

echo "=== Testing Lucee $LUCEE_VERSION ($TOMCAT) ==="

echo "=== Building extension ==="
mvn -B -q clean package

echo "=== Setting up Lucee Express ==="
mkdir -p "$TEST_DIR"

# Download Express template if not exists
if [ ! -f "$TEST_DIR/express-template.zip" ]; then
	echo "Downloading Express template for $TOMCAT..."
	EXPRESS_URL=$(curl -s https://update.lucee.org/rest/update/provider/expressTemplates | grep -o "\"$TOMCAT\":\"[^\"]*\"" | cut -d'"' -f4)
	curl -L -o "$TEST_DIR/express-template.zip" "$EXPRESS_URL"
fi

# Extract fresh copy
rm -rf "$TEST_DIR/lucee-express"
unzip -q "$TEST_DIR/express-template.zip" -d "$TEST_DIR/lucee-express"

# Download Lucee JAR if not exists
if [ ! -f "$TEST_DIR/lucee-$LUCEE_VERSION.jar" ]; then
	echo "Downloading Lucee $LUCEE_VERSION JAR..."
	LUCEE_FILENAME=$(curl -s "https://update.lucee.org/rest/update/provider/latest/$LUCEE_VERSION/all/jar/filename" | tr -d '"')
	LUCEE_URL="https://cdn.lucee.org/$LUCEE_FILENAME"
	curl -L -f -o "$TEST_DIR/lucee-$LUCEE_VERSION.jar" "$LUCEE_URL"
fi

# Install Lucee JAR
rm -f "$TEST_DIR/lucee-express/lib/lucee-"*.jar
cp "$TEST_DIR/lucee-$LUCEE_VERSION.jar" "$TEST_DIR/lucee-express/lib/"

# Use local websocket-client extension build
WS_CLIENT_LEX="$SCRIPT_DIR/../extension-websocket-client/target/websocket-client-extension-2.3.0.7.lex"
if [ ! -f "$WS_CLIENT_LEX" ]; then
	echo "ERROR: websocket-client extension not found at $WS_CLIENT_LEX"
	echo "Build it first: cd ../extension-websocket-client && mvn package"
	exit 1
fi

# Install extensions
mkdir -p "$TEST_DIR/lucee-express/lucee-server/deploy"
cp target/*.lex "$TEST_DIR/lucee-express/lucee-server/deploy/"
cp "$WS_CLIENT_LEX" "$TEST_DIR/lucee-express/lucee-server/deploy/"

# Copy websocket listeners
mkdir -p "$TEST_DIR/lucee-express/lucee-server/context/websockets"
cp tests/websockets/*.cfc "$TEST_DIR/lucee-express/lucee-server/context/websockets/"

# Copy tests to webroot
cp -r tests "$TEST_DIR/lucee-express/webapps/ROOT/"

# Configure port
sed -i "s/port=\"8080\"/port=\"$PORT\"/g" "$TEST_DIR/lucee-express/conf/server.xml"

echo "=== Running Lucee warmup ==="
cd "$TEST_DIR/lucee-express"
export LUCEE_ENABLE_WARMUP=true
./bin/catalina.sh run
unset LUCEE_ENABLE_WARMUP

echo "=== Starting Lucee Express ==="
./bin/catalina.sh start

echo "=== Waiting for server ==="
for i in {1..60}; do
	if curl -s -o /dev/null -w "%{http_code}" http://localhost:$PORT/ | grep -q "200\|302\|404"; then
		echo "HTTP ready after $i seconds"
		break
	fi
	sleep 1
done

echo ""
echo "=== Running tests ==="
echo ""

TEST_FAILED=0

echo "--- websocketInfo() test ---"
RESULT=$(curl -s http://localhost:$PORT/tests/test-websocket-info.cfm)
echo "$RESULT"
if echo "$RESULT" | grep -q "FAILED"; then
	TEST_FAILED=1
fi
echo ""

echo "--- WebSocket connection test ---"
RESULT=$(curl -s http://localhost:$PORT/tests/test-websocket-client.cfm)
echo "$RESULT"
if echo "$RESULT" | grep -q "FAILED"; then
	TEST_FAILED=1
fi
echo ""

echo "=== Stopping server ==="
./bin/shutdown.sh || true

echo ""
if [ $TEST_FAILED -eq 0 ]; then
	echo "=== Lucee $LUCEE_VERSION: ALL TESTS PASSED ==="
else
	echo "=== Lucee $LUCEE_VERSION: TESTS FAILED ==="
fi

exit $TEST_FAILED
