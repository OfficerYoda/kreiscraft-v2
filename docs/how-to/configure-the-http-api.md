---
title: How to configure the HTTP/WebSocket API
---

## 1. Enable the module

In `config.yml`:

```yaml
modules:
  api:
    enabled: true
```

The server must be (re)started for `enabled` to take effect. The module cannot be hot-enabled via `/kreiscraft reload`.

---

## 2. Configure the server

Set the bind address, port, allowed browser origins, and an admin key if whitelist administration is needed:

```yaml
modules:
  api:
    host: "127.0.0.1"
    port: 8080
    cors-origins:
      - "https://kreiscraft.de"
    admin-api-key: ""
```

See [Configuration](/reference/configuration/#api) for every key, default, and duration syntax.

---

## 3. Check the routes

Use [HTTP and WebSocket API Reference](/reference/http-api/) for routes, payloads, status codes, and the WebSocket protocol.

---

## 4. Starting and stopping

**Start:** the API server starts automatically when the module enables (`ApiServer.start()` is called from `ApiModule.enable()`). Look for Javalin's startup log line — it prints the bound address and port.

**Stop:** `ApiModule.disable()` calls `ApiServer.stop()`, which shuts down the WebSocket heartbeat, closes all WebSocket sessions, stops Javalin, and stops the metrics collector. This runs on normal server shutdown.

**No manual start/stop command exists.**

---

## 5. Hot-reload behaviour

`/kreiscraft reload` calls `ApiModule.onConfigReload`. The outcome depends on which fields changed:

| Changed fields | Behaviour |
|----------------|-----------|
| `host`, `port`, or `cors-origins` | Javalin is stopped and restarted with the new values. Open WebSocket connections are dropped. |
| Any other field | Config object is updated in memory but the running server is **not** restarted. Changes take effect only on the next full server start. |

Fields baked in at construction time (not hot-updatable): `heartbeat-interval`, `async-timeout`, `metrics-refresh-interval`, `verification-timeout`. The `ApiModule` Javadoc documents this constraint explicitly.

---

## 6. Player account linking (browser ↔ in-game)

The linking flow ties a browser session to an online Minecraft player so the website can identify who is logged in.

1. The browser sends `POST /link-player` with the player's name.
2. The server creates a pending verification entry in `VerificationService` and opens a 3×9 chest GUI for the target player in-game (`LinkGuiPrompt`). The GUI shows a green-wool Accept button and a red-wool Decline button.
3. The player clicks **Accept** — the browser session is linked to the player's account; or clicks **Decline** — the request is cancelled.
4. The browser polls `GET /linked-player` to discover the result.
5. Pending requests expire after `verification-timeout` (default 2 minutes).

The player must be online for a link request to be initiated. There is no command the player types — the GUI appears automatically.

---

## 7. Security notes

The API has no built-in TLS and only admin endpoints use `admin-api-key`. Do not expose it publicly without a TLS reverse proxy, and review the [API security constraints](/reference/http-api/#security) before deployment.
