---
title: HTTP and WebSocket API Reference
---

Complete reference for the embedded Javalin HTTP/WebSocket server provided by the `api` module.

---

## Overview

The `api` module starts an embedded [Javalin 6](https://javalin.io/) HTTP server that serves as a bridge between the Kreiscraft web frontend and the Paper server. It exposes:

- JSON REST endpoints for server status, player data, session management, browser↔player linking, and whitelist administration.
- A WebSocket endpoint (`/chat`) that relays in-game chat to browser clients and accepts browser chat messages.

The server starts inside `ApiModule.enable()` and binds to the address and port configured in `modules.api`. There is no reverse proxy layer in the default setup — the Javalin instance listens directly on the configured TCP port.

**Technology:** Javalin 6, embedded Jetty, Jackson for JSON serialisation (`ObjectMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES = false`).

**Base URL pattern:** `http://<host>:<port>` — e.g. `http://0.0.0.0:8080`.

---

## Configuration

| Key (`modules.api.*`) | Type | Default | Description |
|---|---|---|---|
| `host` | String | `0.0.0.0` | Bind address. |
| `port` | int | `8080` | TCP port. |
| `cors-origins` | List\<String\> | `["https://kreiscraft.de"]` | Allowed CORS origins. Credentials are always allowed. |
| `session-cookie.name` | String | `website_user_uuid` | Cookie name used to track browser sessions. |
| `session-cookie.max-age` | Duration | `30d` | Cookie lifetime. |
| `heartbeat-interval` | Duration | `20s` | WebSocket ping cadence. |
| `async-timeout` | Duration | `10s` | Javalin async request timeout. |
| `metrics-refresh-interval` | Duration | `1s` | How often server metrics are refreshed. |
| `verification-timeout` | Duration | `2m` | How long a pending link request stays valid. |
| `admin-api-key` | String | `""` | Secret for admin endpoints; empty string disables them. |

See [Configuration Reference](/reference/configuration/#api) for full detail and reload behaviour.

---

## Authentication and Account Linking

### Browser session identity

The API uses an HTTP cookie to identify a browser session. The cookie name is configured via `session-cookie.name` (default: `website_user_uuid`). Its value is a random UUID assigned by `GET /initialize`.

All endpoints that act on behalf of a specific user (link, unlink, poll) read this cookie and parse it as a UUID. If the cookie is absent or malformed, the endpoint returns `401 {"error": "missing_session"}`.

### Player linking flow

Linking ties a browser session UUID to an in-game player UUID so the frontend can display player-specific data.

1. The browser calls `GET /initialize` to obtain or reuse a session UUID (stored in the cookie).
2. The browser calls `POST /link-player` with a JSON body containing the target player's username.
3. The server dispatches to the Bukkit main thread, looks up the player by exact name, and — if the player is online — opens a 3×9 chest GUI for them via `LinkGuiPrompt`.
   - The GUI title and item labels come from the MessageService (`api.link-prompt-title`, etc.).
   - Slot 4 (row 0, centre): paper item showing the browser session identifier (`browser <first-8-chars-of-uuid>`).
   - Row 1, column 2 (slot 11): green wool — **Accept**.
   - Row 1, column 6 (slot 15): red wool — **Decline**.
4. The browser polls `GET /linked-player` until the status changes from `pending`.
5. If the player clicks **Accept**, `VerificationService.confirmVerification()` maps the browser UUID → player UUID. Status becomes `accepted`.
6. If the player clicks **Decline**, or the verification times out (configurable `verification-timeout`, default `2m`), the status becomes `declined`. Expired requests are scrubbed by a background task running every `timeout / 4`.
7. To remove an accepted link the browser calls `POST /unlink-player`.

### Admin endpoints

`GET /whitelist` and `POST /whitelist` require the header:

```
X-Kreiscraft-Api-Key: <value matching modules.api.admin-api-key>
```

If `admin-api-key` is blank, both endpoints return `503 {"error": "admin_api_disabled"}`.

---

## HTTP Endpoints

### `GET /status`

**Description:** Returns a current snapshot of server metrics. Updated on the main thread at the configured `metrics-refresh-interval`.

**Auth required:** No.

**Response:**

- `200 OK` — JSON object:

```json
{
  "status": "Online",
  "tps": 20.0,
  "onlinePlayerCount": 5,
  "memoryUsedMB": 1024,
  "memoryMaxMB": 4096,
  "cpuUsage": 12.5
}
```

| Field | Type | Description |
|---|---|---|
| `status` | String | Always `"Online"` (placeholder value from `ServerMetricsCollector`). |
| `tps` | double | 1-minute TPS average from `Server.getTPS()[0]`. |
| `onlinePlayerCount` | int | Number of currently online players. |
| `memoryUsedMB` | long | Heap memory used (MiB). |
| `memoryMaxMB` | long | Heap memory max (MiB). |
| `cpuUsage` | double | Process CPU usage 0–100. Returns `-1.0` on JVMs where `com.sun.management.OperatingSystemMXBean` is unavailable. |

---

### `GET /players`

**Description:** Returns the current list of online players with per-player statistics. Updated on the same refresh cycle as `/status`.

**Auth required:** No.

**Response:**

- `200 OK` — JSON array of player objects:

```json
[
  {
    "name": "PlayerName",
    "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "health": 20,
    "deaths": 3,
    "playerKills": 1,
    "playtimeSeconds": 86400
  }
]
```

| Field | Type | Description |
|---|---|---|
| `name` | String | Player's display name. |
| `uuid` | String | Player UUID as a dashed string. |
| `health` | int | Current health, rounded to nearest integer. |
| `deaths` | int | Lifetime death count (`Statistic.DEATHS`). |
| `playerKills` | int | Lifetime player kill count (`Statistic.PLAYER_KILLS`). |
| `playtimeSeconds` | long | Total playtime in seconds (`Statistic.PLAY_ONE_MINUTE / 20`). |

---

### `GET /initialize`

**Description:** Initialises or resumes a browser session. Checks for an existing valid UUID cookie; if absent or malformed, generates a new UUID and sets the cookie.

**Auth required:** No.

**Cookie set:**

| Attribute | Value |
|---|---|
| Name | `modules.api.session-cookie.name` (default `website_user_uuid`) |
| Value | Random UUID v4 string |
| Path | `/` |
| HttpOnly | `true` |
| Secure | `false` (set to `true` if serving over HTTPS) |
| SameSite | `Strict` |
| Max-Age | `modules.api.session-cookie.max-age` in seconds (default 30 days) |

**Response:**

- `200 OK`:

```json
{ "status": "new", "userId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" }
```

or

```json
{ "status": "existing", "userId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" }
```

`status` is `"new"` when a new cookie was issued, `"existing"` when the request already carried a valid UUID cookie.

---

### `POST /link-player`

**Description:** Initiates a browser→player link request. Looks up the named player on the Bukkit main thread and opens the in-game confirmation GUI.

**Auth required:** Session cookie (browser UUID).

**Request body:** `application/json`

```json
{ "username": "ExactPlayerName" }
```

**Response:**

| Status | Body | Condition |
|---|---|---|
| `202 Accepted` | `{"status": "verification_sent"}` | GUI opened on the target player. |
| `401 Unauthorized` | `{"error": "missing_session"}` | Cookie absent or not a valid UUID. |
| `400 Bad Request` | `{"error": "invalid_body"}` | Body is not valid JSON or `LinkRequest` cannot be parsed. |
| `400 Bad Request` | `{"error": "missing_username"}` | `username` field is null or blank. |
| `409 Conflict` | `{"status": "already_pending"}` | A verification request for this browser session is already pending. |
| `422 Unprocessable Entity` | `{"error": "player_offline"}` | No online player found with that exact name. |
| `500 Internal Server Error` | `{"error": "unexpected_result"}` | Unexpected enum value from `VerificationService`. |

---

### `GET /linked-player`

**Description:** Polls the current link status for the browser session identified by the session cookie.

**Auth required:** Session cookie (browser UUID).

**Response:**

- `200 OK` — always returns `200`. Body varies by status:

```json
{ "status": "not_linked" }
```

```json
{ "status": "pending" }
```

```json
{ "status": "declined" }
```

```json
{
  "status": "accepted",
  "playerData": {
    "name": "PlayerName",
    "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "health": 20,
    "deaths": 3,
    "playerKills": 1,
    "playtimeSeconds": 86400
  }
}
```

When `status` is `"accepted"` but the linked player is currently offline, `playerData` is omitted (`null` is excluded by `@JsonInclude(NON_NULL)`).

- `401 Unauthorized` — `{"error": "missing_session"}` — Cookie absent or invalid.

---

### `POST /unlink-player`

**Description:** Removes an accepted browser→player link. Notifies the player in-game if they are online.

**Auth required:** Session cookie (browser UUID).

**Request body:** None.

**Response:**

| Status | Body | Condition |
|---|---|---|
| `200 OK` | `{"status": "unlinked"}` | Link removed. |
| `400 Bad Request` | `{"error": "not_linked"}` | The session had no accepted link. |
| `401 Unauthorized` | `{"error": "missing_session"}` | Cookie absent or invalid. |

After unlinking, the module sends the message key `api.unlinked` to the player on the main thread (fire-and-forget; no response is delayed).

---

### `GET /whitelist`

**Description:** Returns the server whitelist as a sorted list of player names.

**Auth required:** `X-Kreiscraft-Api-Key` header matching `modules.api.admin-api-key`. Disabled when `admin-api-key` is blank.

**Response:**

| Status | Body | Condition |
|---|---|---|
| `200 OK` | `["Alice", "Bob", ...]` | Success — sorted, null entries filtered. |
| `401 Unauthorized` | *(empty body)* | Missing or wrong API key. |
| `503 Service Unavailable` | `{"error": "admin_api_disabled"}` | `admin-api-key` is blank. |

---

### `POST /whitelist`

**Description:** Adds a Java player to the server whitelist.

**Auth required:** `X-Kreiscraft-Api-Key` header.

**Request body:** `application/json`

```json
{ "playerName": "ExactPlayerName" }
```

`playerName` must match `^[a-zA-Z0-9_]{1,16}$` (standard Minecraft Java username rules).

**Response:**

| Status | Body | Condition |
|---|---|---|
| `202 Accepted` | `{"status": "whitelisted"}` | Player added to whitelist. |
| `400 Bad Request` | `{"error": "invalid_body"}` | Body not valid JSON. |
| `400 Bad Request` | `{"error": "invalid_name"}` | `playerName` is null, blank, or fails the regex. |
| `401 Unauthorized` | *(empty body)* | Missing or wrong API key. |
| `503 Service Unavailable` | `{"error": "admin_api_disabled"}` | `admin-api-key` is blank. |

The whitelist mutation is dispatched to the Bukkit main thread via `Scheduler.runOnMain`.

---

## WebSocket Endpoint: `GET /chat`

**Description:** Bidirectional WebSocket bridge between the web frontend and in-game chat.

### Connection

Connect with a standard WebSocket upgrade to `ws://<host>:<port>/chat`. No authentication is required to connect. CORS rules (configured via `cors-origins`) apply to WebSocket upgrade requests in the same way as HTTP requests.

On successful connection the server immediately sends a handshake message:

```json
{ "type": "VERIFICATION", "content": "Connection successful." }
```

### Message Format

All messages in both directions are JSON objects:

```json
{ "type": "<TYPE>", "content": "<string>" }
```

| Field | Type | Description |
|---|---|---|
| `type` | String (enum) | One of `CHAT`, `PING`, `PONG`, `VERIFICATION`, `EXCEPTION`. |
| `content` | String | Message content. Meaning depends on `type`. |

### Message Types

| Type | Direction | Description |
|---|---|---|
| `CHAT` | Server → Client | An in-game chat message forwarded to the browser. Content uses the legacy `§`-prefixed colour scheme (e.g. `§#ff5500PlayerName§§: hello`). |
| `CHAT` | Client → Server | A chat message sent from the browser. Content is broadcast to in-game chat via `MessageService.broadcast("api.chat-format-from-web", "content", <content>)` and echoed to all other connected WebSocket clients. |
| `PING` | Server → Client | Heartbeat ping sent every `heartbeat-interval`. Content: `"heartbeat ping"`. |
| `PONG` | Client → Server | Expected reply to `PING`. Server ignores this message. |
| `PONG` | Server → Client | Reply to a `PING` sent by the client. Content: `"heartbeat pong"`. |
| `VERIFICATION` | Server → Client | Handshake on connection, or future auth messages. Content: `"Connection successful."`. Clients must not send this type. |
| `EXCEPTION` | Server → Client | Sent when the server cannot parse an incoming message. Content: `"bad message"`. Clients must not send this type. |

### Colour Encoding

In-game chat events received from `ChatBridge` are rendered using a legacy `§`-prefixed encoding:

- Each `TextComponent` segment is prefixed with `§#rrggbb` (hex colour, e.g. `§#ff5500`).
- Segments with no colour use `§§` as a double-escape.
- Non-`TextComponent` children (e.g. `TranslatableComponent`) fall back to the pre-computed plain text string.

This encoding is retained for zero-downtime frontend compatibility. Migration to MiniMessage may happen in the future.

### Heartbeat

The server sends a `PING` to all connected clients on a fixed schedule (`heartbeat-interval`, default 20 s). Before each ping cycle, sessions with a closed underlying Jetty session are pruned from the connected set. There is no strict client-side pong requirement — the server does not disconnect clients that do not reply to pings.

### Threading

All Bukkit API calls triggered by incoming WebSocket messages (e.g. `MessageService.broadcast`) are dispatched to the Bukkit main thread via `Scheduler.runOnMain`. WebSocket event handlers run on a Jetty worker thread and must not call Bukkit API directly.

---

## Error Response Format

There is no single standardised error envelope across all endpoints. Errors are returned as plain JSON objects with an `"error"` key, or as an empty body. The `content-type` is `application/json` for all JSON error bodies.

Common error bodies:

| Body | Meaning |
|---|---|
| `{"error": "missing_session"}` | Session cookie absent or not a valid UUID. |
| `{"error": "invalid_body"}` | Request body could not be parsed as the expected record. |
| `{"error": "admin_api_disabled"}` | `admin-api-key` is blank in config. |
| `{"error": "not_linked"}` | No accepted link exists for this session. |
| `{"error": "player_offline"}` | Target player is not online. |
