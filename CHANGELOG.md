# Changelog

## 3.1.0.0 (unreleased)

- [LDEV-6457](https://luceeserver.atlassian.net/browse/LDEV-6457) — add `wsClient.getId()`, the id of the connection a listener function was called for
- [LDEV-6457](https://luceeserver.atlassian.net/browse/LDEV-6457) — **behaviour change**: `websocketInfo()` only returns the sessions of the current web context, ordered newest first; called from inside a listener it only returns the sessions of that listener component. New optional argument `component` (`"*"` for all components). Instances now also contain `id`, `componentName`, `openTime` and `lastMessageTime`
- [LDEV-6457](https://luceeserver.atlassian.net/browse/LDEV-6457) — stale session cleanup: ping every `pingInterval` (default 30s) and close sessions without a pong within `pongTimeout` (default 10s); optional `staleTimeout` (default off) closes sessions that did not send a message, e.g. pages frozen in Chrome's back/forward cache. Configurable in `websocket.json` or as listener properties
- [LDEV-6457](https://luceeserver.atlassian.net/browse/LDEV-6457) — skip `onOpenAsync` for sessions that are already closed, no more `IllegalStateException` from `getQueryString()`
- [LDEV-6972](https://luceeserver.atlassian.net/browse/LDEV-6972) — fix the endpoint being registered twice at startup when the startup hook is instantiated more than once, Tomcat then failed the deployment and all `/ws/` URLs returned 404; the registration is now claimed atomically JVM wide and the default `websocket.json` is created by one thread only

## 3.0.0.21 (unreleased)

- [LDEV-6277](https://luceeserver.atlassian.net/browse/LDEV-6277) — expose `wsClient.getSession()` so listeners can reach the underlying JSR-356 `Session` (`getId()`, `getUserProperties()`, `getRequestParameterMap()`)
- [LDEV-6221](https://luceeserver.atlassian.net/browse/LDEV-6221) — improve reflection fallback warning message to explain why and that a servlet engine restart is needed

## 3.0.0.20

- [LDEV-6179](https://luceeserver.atlassian.net/browse/LDEV-6179) — fix race condition in `readConfig()` causing `this.mapping is null` on startup
- [LDEV-6179](https://luceeserver.atlassian.net/browse/LDEV-6179) — restructure init lifecycle: separate data creation, component init, and endpoint registration
- [LDEV-6179](https://luceeserver.atlassian.net/browse/LDEV-6179) — lazy retry for mapping init instead of permanent failure on first error
- Fix `inject()` self-delegation causing infinite recursion in `onError`
- CI: dynamic Lucee version matrix, workflow_dispatch for version bisecting, always upload logs

## 3.0.0.19

- [LDEV-6050](https://luceeserver.atlassian.net/browse/LDEV-6050) — support jakarta WebSocket API (Lucee 7.x / Tomcat 11)
- Add GAV to Maven POM

## 3.0.0.18

- [LDEV-6050](https://luceeserver.atlassian.net/browse/LDEV-6050) — fix websocket extension not loading on Lucee 7.x
- Update Maven POM with new Sonatype endpoint

## 3.0.0.17

- [LDEV-5385](https://luceeserver.atlassian.net/browse/LDEV-5385) — include websocket jars with extension, add `Require-Bundle`
- Use single ConfigWeb when only one is available

## 3.0.0.16

- [LDEV-5385](https://luceeserver.atlassian.net/browse/LDEV-5385) — resolve class loading conflicts between javax and jakarta namespaces
- Add tests and CI workflow
- Add Maven deployment

## Earlier versions

- WebSocket idle timeout and request timeout support
- `websocketInfo()` BIF with version, instances, and session info
- Runtime extension update via `inject()`
- Initial javax.websocket implementation
