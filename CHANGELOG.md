# Changelog

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
