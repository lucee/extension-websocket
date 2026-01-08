# AGENTS.md

WebSocket server extension for Lucee CFML.

## Project Overview

This extension provides WebSocket server functionality for Lucee, allowing CFML components to handle WebSocket connections with lifecycle methods (onOpen, onMessage, onClose, etc).

**Dual API support**:

- **javax.websocket** - Lucee 6.x (Tomcat 9)
- **jakarta.websocket** - Lucee 7.x (Tomcat 11)

Both bundles are marked as optional in MANIFEST.MF so the extension loads on either platform.

## Repository Structure

```
extension-websocket/
├── source/java/           # Main Java source
│   └── src/META-INF/      # MANIFEST.MF with optional bundles
├── tests/                 # CFML tests
│   ├── websockets/        # Test WebSocket listener components
│   └── test-*.cfm         # Integration tests
└── .github/workflows/     # CI - tests against Lucee 6.2 and 7.0
```

## Building

```bash
mvn clean install
```

Output: `target/*.lex`

## Key Classes

- `WebSocketEndpointFactory` - Startup hook, registers endpoints
- `WSUtil` - Utilities for servlet context access, container type detection
- `WebSocketEndpoint` / `WebSocketEndpointJakarta` - Endpoint implementations for each API

## Testing

CI tests against both Lucee versions using Lucee Express:

- Lucee 6.2 + Tomcat 9 (javax)
- Lucee 7.0 + Tomcat 11 (jakarta)

Tests:

1. `test-websocket-info.cfm` - Smoke test (extension loads, BIF works)
2. `test-websocket-client.cfm` - Integration test using websocket-client extension

### Debugging GitHub Actions Failures

When a workflow fails, download logs locally for inspection:

```bash
# Create test-output folder (already in .gitignore)
mkdir -p test-output

# Download all logs from a failed run
gh run view <RUN_ID> --repo lucee/extension-websocket --log > test-output/run-<RUN_ID>.log

# Or download artifacts (contains Lucee logs on failure)
gh run download <RUN_ID> --repo lucee/extension-websocket --dir test-output/

# Search logs locally
grep -E "ERROR|Exception|Failed" test-output/run-<RUN_ID>.log
```

Always use `test-output/` for workflow logs - it's gitignored so won't pollute the repo.

## Code Style

- Java 11+
- Tabs for indentation
- Fail fast - throw RuntimeException rather than returning null
- CFML: spaces around arguments `doSomething( name="foo" )`
