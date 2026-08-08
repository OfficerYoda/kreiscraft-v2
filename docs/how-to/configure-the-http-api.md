# How to configure the HTTP/WebSocket API

## 1. Enable the module

In `config.yml`:

```yaml
modules:
  api:
    enabled: true
```

The server must be (re)started for `enabled` to take effect. The module cannot be hot-enabled via `/kreiscraft reload`.

---

## 2. Config keys

All keys live under `modules.api` in `config.yml`. Source of truth: `ApiConfig.java`.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `host` | string | `"0.0.0.0"` | Bind address for the Javalin HTTP server. Use `"127.0.0.1"` to restrict to localhost. |
| `port` | int | `8080` | TCP port. |
| `cors-origins` | list of strings | `["https://kreiscraft.de"]` | Origins allowed by the CORS policy. Requests from unlisted origins receive a 400 response. |
| `session-cookie.name` | string | `"website_user_uuid"` | Name of the browser session cookie set by `GET /initialize`. |
| `session-cookie.max-age` | duration | `30d` | Max-age of the session cookie. |
| `heartbeat-interval` | duration | `20s` | WebSocket ping interval sent to `/chat` clients. |
| `async-timeout` | duration | `10s` | Javalin async request timeout. |
| `metrics-refresh-interval` | duration | `1s` | How often `ServerMetricsCollector` polls TPS and player count. |
| `verification-timeout` | duration | `2m` | How long a pending browser link request stays alive before it expires. |
| `admin-api-key` | string | `""` | API key required in the `X-Kreiscraft-Api-Key` header for admin endpoints (`/whitelist`, etc.). Empty string disables those endpoints (they return 503 `admin_api_disabled`). |

Duration syntax examples: `20s`, `2m`, `30d`, `500ms`.

---

## 3. Routes exposed

| Route | Method | Description |
|-------|--------|-------------|
| `/status` | GET | Server TPS, player count, uptime. |
| `/players` | GET | Online player list and metadata. |
| `/initialize` | GET | Initialises or resumes a browser session and sets the session cookie. |
| `/link-player` | POST | Initiate a browser↔player link for a named online player. |
| `/linked-player` | GET | Poll link status for the current browser session. |
| `/unlink-player` | POST | Remove an accepted link. |
| `/whitelist` | GET/POST | Read or add whitelist entries. Requires `admin-api-key`. |
| `/chat` | WebSocket | Bidirectional chat bridge between browser and in-game chat. |

---

## 4. Starting and stopping

**Start:** the API server starts automatically when the module enables (`ApiServer.start()` is called from `ApiModule.enable()`). Look for Javalin's startup log line — it prints the bound address and port.

**Stop:** `ApiModule.disable()` calls `ApiServer.stop()`, which shuts down the WebSocket heartbeat, closes all WebSocket sessions, stops Javalin, and stops the metrics collector. This runs on normal server shutdown.

**No manual start/stop command exists.**

---

## 5. Hot-reload behaviour

`/kreiscraft reload` calls `ApiModule.onConfigReload`. The outcome depends on which fields changed (`ApiModule.java:107-120`):

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

- **`admin-api-key` is required** to use the `/whitelist` endpoint. Leave it empty (`""`) if you do not need it — the endpoint will respond with 503.
- The CORS allowlist (`cors-origins`) is the only origin-based protection for the other endpoints. Do not add wildcard origins.
- The API server has **no TLS, no authentication beyond the linking flow and the admin key**. Do not expose the port publicly without a reverse proxy that handles TLS and, if needed, additional authentication.
- The linking flow authenticates the browser as a specific player; it does not authenticate admin operations — those use `admin-api-key`.
