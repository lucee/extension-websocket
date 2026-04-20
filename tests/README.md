# WebSocket Extension — Tests

## Two test tiers

### Extension tests (this folder)

Exercise the extension's own contract — WebSocket server not required beyond what Tomcat already provides:

- [`test-websocket-info.cfm`](test-websocket-info.cfm) — smoke test: extension bundle loaded, [`websocketInfo()`](../../../lucee-docs/docs/03.reference/01.functions/websocketinfo) returns the expected shape (`mapping`, `config`, `instances`).
- [`test-idle-timeout.cfm`](test-idle-timeout.cfm) — [LDEV-6219](https://luceeserver.atlassian.net/browse/LDEV-6219): per-listener `property idleTimeout` must apply to every session on that listener, not just the first.
- [`WebSocketInfo.cfc`](WebSocketInfo.cfc) / [`WebSocketListener.cfc`](WebSocketListener.cfc) / [`TestPlaceholder.cfc`](TestPlaceholder.cfc) — support CFCs.
- [`websockets/`](websockets/) — listener CFCs (`TestListener`, `TimeoutListener`) dropped into the configured websockets directory.

Driven by CI via `curl` against a Lucee Express instance with the built `.lex` installed — see `.github/workflows/main.yml`.

### Integration tests ([`tests/integration/`](integration/))

End-to-end client⇄server coverage — `sendText`, `sendBinary`, `onMessage`, `onClose`, `disconnect`, `isOpen`, lifecycle ordering, broadcast semantics, reflection-after-restart, config-path overrides.

These tests use [`CreateWebSocketClient`](https://github.com/lucee/extension-websocket-client) as the driver against listeners registered by this extension — so every integration test exercises **both** extensions together.

## Why integration tests live here, not in the client repo

Every integration test needs both extensions running. Duplicating the suite across two repos guarantees drift. Instead:

- One copy lives here (server repo) — the server is the thing being driven.
- The client repo's `test-integration` job **sparse-checkouts** `tests/integration/` from this repo, builds its branch's client `.lex`, pulls the latest server `.lex` from download.lucee.org, and runs the suite against the combined pair.
- Each side's CI catches the bug on its own side — server changes fail here, client changes fail in [extension-websocket-client](https://github.com/lucee/extension-websocket-client).

## What fails where

| Bug source | Caught by |
| --- | --- |
| Extension load, `websocketInfo()` shape, per-listener config | Extension tests in this repo (`tests/*.cfm`) |
| Server callback firing, broadcast, lifecycle, `wsClient` / `wsClients` APIs | Integration tests in this repo (`tests/integration/`) |
| Reflection fallback after Lucee restart (LDEV-6221) | `test-reflection-restart.cfm` (integration, Linux-only in CI) |
| Config overrides (env var, custom directory) | `test-config-override.cfm` (separate CI job, needs altered env) |
| Client BIF contract (scheme handling, error shape, connection timing) | Unit tests in [extension-websocket-client](https://github.com/lucee/extension-websocket-client/tree/master/tests) |
| Client send/receive, lifecycle, reconnect | Integration tests here — fail in both repos' CI |

## Adding a test

**Needs the WebSocket server contract (listener callbacks, broadcast, lifecycle)?** Add it to [`tests/integration/`](integration/). Both this repo's CI and the client repo's `test-integration` job will pick it up.

**Extension-level only (BIF output, config loading, startup behaviour)?** Add a `test-*.cfm` at the top level of `tests/` and wire it into the `test` job in [`.github/workflows/main.yml`](../.github/workflows/main.yml).

**Listener CFCs under test** go in `tests/integration/websockets/` (integration) or `tests/websockets/` (extension-level). Both directories are mapped as the websockets directory in their respective CI job.

## Running locally

Build the extension:

```bash
mvn clean install
```

Drop `target/*.lex` into a Lucee Express instance's `{lucee-config}/deploy/` folder, copy the test listener CFCs into `{lucee-config}/websockets/`, then `curl` the `.cfm` scripts:

```bash
curl http://localhost:8888/tests/test-websocket-info.cfm
curl http://localhost:8888/tests/integration/test-lifecycle-callbacks.cfm
```

Integration tests also need the [websocket-client extension](https://github.com/lucee/extension-websocket-client) installed in the same Lucee instance. The CI workflow ([`.github/workflows/main.yml`](../.github/workflows/main.yml)) shows the full setup.
