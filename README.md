# Lucee WebSocket Extension

[![Java CI](https://github.com/lucee/extension-websocket/actions/workflows/main.yml/badge.svg)](https://github.com/lucee/extension-websocket/actions/workflows/main.yml)

WebSocket server support for Lucee CFML — define listener components with lifecycle methods (`onOpen`, `onMessage`, `onClose`, etc) and Lucee handles the WebSocket endpoint for you.

**Requires Lucee 6.2+**. Dual API support — loads on both Lucee 6.x (Tomcat 9 / `javax.websocket`) and Lucee 7.x (Tomcat 11 / `jakarta.websocket`).

## Installation

Install via Lucee Admin, or pin in your environment:

```bash
LUCEE_EXTENSIONS=org.lucee:websocket-extension:3.0.0.20-SNAPSHOT
```

## Documentation

- **Docs**: [docs.lucee.org/recipes/websocket-extension.html](https://docs.lucee.org/recipes/websocket-extension.html)
- **Downloads**: [download.lucee.org](https://download.lucee.org/#3F9DFF32-B555-449D-B0EB5DB723044045)
- **Issues**: [Lucee JIRA — WebSocket Issues](https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20%22websockets%22)

### What's Included

- **Listener components** — CFML components with `onOpen`, `onMessage`, `onClose`, `onError`, `onFirstOpen`, `onLastClose` lifecycle methods.
- **Async open handler** — optional `onOpenAsync` runs in parallel with `onOpen` for long-running init work.
- **`websocketInfo()` BIF** — returns `version`, `mapping`, `config`, `configFile`, `log`, and an `instances[]` array of active sessions with their component + session metadata.
- **Extension hot-upgrade** — upgrade the `.lex` in-place via `inject()` without restarting the servlet container.
- **Configurable timeouts** — `idleTimeout` and `requestTimeout` per web context via `websocket.json`.

## Configuration

Listener components live in a directory configured in `{lucee-config}/websocket.json` (auto-created with defaults on first load):

```json
{
    "directory": "{lucee-config}/websockets/",
    "requestTimeout": 50,
    "idleTimeout": 300
}
```

Override the config path with `-Dlucee.websocket.config=/path/to/websocket.json` or the `LUCEE_WEBSOCKET_CONFIG` env var.

## Quick Example

A listener component dropped in the configured directory as `EchoListener.cfc`:

```cfml
component {

    function onOpen( wsClient ) {
        wsClient.send( "CONNECTED" );
    }

    function onMessage( wsClient, message ) {
        wsClient.send( "ECHO:" & message );
    }

    function onClose( wsClient, reasonPhrase ) {}

    function onError( wsClient, cfCatch ) {
        systemOutput( "WS error: #cfCatch.message#", true );
    }

}
```

Clients connect to `ws://yourhost/ws/EchoListener`. Check server state:

```cfml
info = websocketInfo();
writeDump( info );
```

## Related

- **[extension-websocket-client](https://github.com/lucee/extension-websocket-client)** — WebSocket client BIFs (`CreateWebSocketClient`) for CFML. This repo's integration tests ([test-websocket-client.cfm](tests/test-websocket-client.cfm), [test-idle-timeout.cfm](tests/test-idle-timeout.cfm)) exercise the full client⇄server loop, so they cover both extensions.
- **[Lucee-websocket-commandbox](https://github.com/webonix/Lucee-websocket-commandbox)** — full client + server example with JavaScript and CFML.
- **[CFCAMP 2024 presentation](https://www.cfcamp.org/resource/getting-started-with-lucee-6-websockets.html)** — Getting Started with Lucee 6 WebSockets.
