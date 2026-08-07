# Migration from Legacy Plugins

## Preamble

This document is for **operators** upgrading a server that previously ran both
`kreiscraft-plugin` (the monolithic Bukkit plugin, `de.rusticprism.kreiscraft`)
and `kreiscraft-api` (the standalone Javalin HTTP/WebSocket plugin,
`de.officeryoda.kreiscraftApi`) to the merged **`kreiscraft-java`** plugin.

Migration is **manual**. No automatic data-migration tool ships with this release.
Work through each section below in order, stop the server before editing any YAML
file, and start the server to verify each step before proceeding to the next.

Legacy source paths for cross-reference:
- Plugin: `kreiscraft-plugin/src/main/java/de/rusticprism/kreiscraft/`
- API: `kreiscraft-api/src/main/java/de/officeryoda/kreiscraftApi/`

---

## 1. Data-file migration

All data files now live under `plugins/Kreiscraft/data/`. Case conventions are
normalised to lowercase-kebab-case throughout.

### 1.1 Mapping table

| Old file / key | New file / key |
|---|---|
| `data/status.yml` — `Status.<uuid>.*` | `data/statuses.yml` — `<uuid>: {…}` |
| `data/Positions.yml` — `<uuid>.<name>.*` (mixed-case sub-keys) | `data/positions.yml` — `<uuid>: {<name>: {…}}` (lowercase sub-keys) |
| `data/Kerker.yml` | `data/jail.yml` |
| `data/settings.yml` | `data/settings.yml` — shape unchanged |
| `data/hackerlog.yml` | `data/hackerlog.yml` — shape unchanged |
| `PluginHider.yml` (at plugin root, not in `data/`) | `data/plugin-hider.yml` |
| `data/bot.yml` | `data/bot.yml` — shape unchanged |
| `config.yml` — `SpawnGlideElytra.*`, `Spawn.*`, `start` | `config.yml` — `modules.spawn.*`, `modules.start-sequence.*` |

### 1.2 Before / after YAML examples

#### `data/status.yml` → `data/statuses.yml`

Legacy (`data/status.yml`):
```yaml
Status:
  550e8400-e29b-41d4-a716-446655440000:
    prefix: "<blue>[Admin]</blue> "
    suffix: ""
    color: -16777216
```

New (`data/statuses.yml`):
```yaml
550e8400-e29b-41d4-a716-446655440000:
  prefix: "<blue>[Admin]</blue> "
  suffix: ""
  color: -16777216
```

The top-level `Status:` wrapper and the `Status.<uuid>` path are gone; each UUID
is now a direct top-level key.

#### `data/Positions.yml` → `data/positions.yml`

Legacy (`data/Positions.yml`):
```yaml
550e8400-e29b-41d4-a716-446655440000:
  Home:
    World: world
    X: 100.0
    Y: 64.0
    Z: -200.0
    Yaw: 90.0
    Pitch: 0.0
```

New (`data/positions.yml`):
```yaml
550e8400-e29b-41d4-a716-446655440000:
  home:
    world: world
    x: 100.0
    y: 64.0
    z: -200.0
    yaw: 90.0
    pitch: 0.0
```

Sub-keys `World`, `X`, `Y`, `Z`, `Yaw`, `Pitch` are renamed to lowercase
(`world`, `x`, `y`, `z`, `yaw`, `pitch`). Position names are also stored
lowercase (e.g. `Home` → `home`); the GUI normalises the display label.

#### `data/Kerker.yml` → `data/jail.yml`

Legacy (`data/Kerker.yml`):
```yaml
Player:
  - 550e8400-e29b-41d4-a716-446655440000
```

New (`data/jail.yml`):
```yaml
players:
  - 550e8400-e29b-41d4-a716-446655440000
```

The top-level key is renamed from `Player` to `players`.

#### `PluginHider.yml` (plugin root) → `data/plugin-hider.yml`

Legacy (`plugins/Kreiscraft/PluginHider.yml`):
```yaml
Allowed:
  - help
  - spawn
  - ping
```

New (`data/plugin-hider.yml`):
```yaml
allowed:
  - help
  - spawn
  - ping
```

The key is renamed from `Allowed` to `allowed`. The file moves from the plugin
root into `data/`.

#### `config.yml` — spawn keys

Legacy (`config.yml`, read by `SpawnGlideElytra.java`):
```yaml
SpawnGlideElytra:
  multiplyvalue: 3
  spawnRadius: 30
Spawn:
  world: world
  x: -35.0
  y: 141.0
  z: -101.0
start: false
```

New (`config.yml`):
```yaml
modules:
  spawn:
    enabled: true
    teleport-range: 100
    glide:
      enabled: true
      multiply-value: 3
      spawn-radius: 30
      launch-location: { world: world, x: -35, y: 141, z: -101, yaw: 0, pitch: 0 }
    swap-hand-boost:
      enabled: false
      vector-multiplier: 3.0
    spawn-launch:
      enabled: false
      trigger: { world: world, x: -35, y: 141, z: -101, yaw: 0, pitch: 0 }
      velocity: { x: 0.0, y: 5.0, z: 0.0 }
  start-sequence:
    enabled: true
    countdown: 10s
    initial-border-size: 50.0
    target-border-size: 30000000.0
```

The legacy boolean flag `start: false` (written to `config.yml` by
`StartCommand`) is not ported. The start-sequence module uses its own state
tracking and does not persist a flag to `config.yml`.

---

## 2. API config mapping

All values that were hardcoded in `KreiscraftApi.java` are now configurable
under `modules.api` in `config.yml`.

| Legacy hardcoded value | New `config.yml` key | Default |
|---|---|---|
| `PORT = 8080` | `modules.api.port` | `8080` |
| *(always binds `0.0.0.0`)* | `modules.api.host` | `"0.0.0.0"` |
| `PERMANENT_ID_COOKIE = "website_user_uuid"` | `modules.api.session-cookie.name` | `"website_user_uuid"` |
| `Duration.ofDays(30)` for cookie max-age | `modules.api.session-cookie.max-age` | `30d` |
| `executor` heartbeat period of `20` seconds | `modules.api.heartbeat-interval` | `20s` |
| `config.http.asyncTimeout = 10_000L` ms | `modules.api.async-timeout` | `10s` |
| *(no legacy equivalent)* | `modules.api.metrics-refresh-interval` | `1s` |
| *(no legacy equivalent)* | `modules.api.verification-timeout` | `2m` |
| `rule.allowHost("http://localhost:5173")` + `"http://localhost:3000"` | `modules.api.cors-origins` | `["https://kreiscraft.de"]` |

### CORS origin change

The legacy plugin allowed only `http://localhost:5173` (Vue dev) and
`http://localhost:3000` (React dev). The new default is **`https://kreiscraft.de`**
(production). Operators running a local development frontend must add the dev
origins to their local `config.yml`:

```yaml
modules:
  api:
    cors-origins:
      - "https://kreiscraft.de"
      - "http://localhost:5173"
      - "http://localhost:3000"
```

---

## 3. Whitelist API authentication

### Legacy behaviour (security bug)

`POST /whitelist` and `GET /whitelist` in the legacy plugin were fully
**unauthenticated**. Any client that could reach port 8080 could add arbitrary
player names to the server whitelist. This was the most significant security
hole in the legacy plugin.

### New behaviour

Both endpoints now require the `X-Kreiscraft-Api-Key` request header whose
value must match `modules.api.admin-api-key` in `config.yml`.

```yaml
modules:
  api:
    admin-api-key: "change-me-to-a-random-secret"
```

**If `admin-api-key` is empty or absent (the default), both endpoints return
`503 Service Unavailable`.** This is deliberate: an operator who has not yet
configured admin access cannot accidentally expose the whitelist API.

Additionally, `POST /whitelist` now sanitises the `playerName` field: any
character outside `[a-zA-Z0-9_]` or a name longer than 16 characters returns
`400 Bad Request`. The legacy plugin injected the raw name directly into a
console command string, allowing shell-injection-style whitelist manipulation
via names like `; stop`.

---

## 4. Cookie security flags

The session cookie (`website_user_uuid` by default) gains three new flags:

| Flag | Legacy | New |
|---|---|---|
| `HttpOnly` | not set | `true` |
| `SameSite` | not set | `Strict` |
| `Path` | not set | `/` |
| `Secure` | not set | `false` (intentional) |

`Secure=false` is deliberate: the plugin serves plain HTTP and TLS termination
is the operator's reverse-proxy responsibility. Setting `Secure=true` would
break the cookie over plain HTTP connections.

---

## 5. JSON serialiser change (Gson → Jackson)

The legacy `ChatWebsocketHandler` used **Gson** for both serialising outbound
`WebSocketMessage` objects and deserialising inbound messages:

```java
// Legacy (ChatWebsocketHandler.java)
private final Gson gson;
this.gson = new Gson();
WebSocketMessage message = gson.fromJson(wsCtx.message(), WebSocketMessage.class);
client.send(gson.toJson(message));
```

The HTTP routes used Javalin's built-in `ctx.json(...)` which in that version
defaulted to **Jackson**. This created a double-mapping inconsistency: HTTP
responses were Jackson-serialised while WebSocket messages were Gson-serialised,
with different field-name conventions and null-handling behaviour.

The new plugin uses **Jackson exclusively** (no Gson dependency). All HTTP and
WebSocket messages are (de)serialised by the same `ObjectMapper` instance. The
wire format is compatible with the legacy format for the existing message types;
no website-side changes are required.

---

## 6. Verification thread-safety fix

The legacy `VerificationHandler` stored pending and verified sessions in plain
`HashMap` instances:

```java
// Legacy (VerificationHandler.java)
private final Map<String, UUID> verifiedUsers = new HashMap<>();
private final Map<String, VerificationStatus> verificationStatuses = new HashMap<>();
private final Map<String, UUID> pendingLinks = new HashMap<>();
```

These maps were read and written from two concurrent threads: the HTTP handler
thread (serving `POST /link-player`, `GET /linked-player`, `POST /unlink-player`)
and the Bukkit main thread (the `ClickEvent.callback()` fired by the player's
in-game click). This was an unguarded data race with undefined behaviour under
load.

The new `VerificationService` uses `ConcurrentHashMap` throughout, and all
compound check-then-update operations use atomic `compute()` / `computeIfPresent()`
calls to eliminate the TOCTOU window.

---

## 7. Verification GUI change

| Aspect | Legacy | New |
|---|---|---|
| Prompt delivery | Chat message with `ClickEvent.callback()` buttons | InvUI 3×9 chest GUI |
| Accept button | `[CONNECT]` chat text (green) | Green wool (slot 11) |
| Decline button | `[DECLINE]` chat text (red) | Red wool (slot 15) |
| Thread routing | Callback fires on arbitrary thread | GUI click fires on Bukkit main thread; no additional scheduling required |

The legacy chat prompt was fragile: the Paper `ClickEvent.callback()` API is
not officially supported for production use and may be removed. The new InvUI
chest GUI matches the plugin's established GUI pattern (`shared/gui/Gui`).

---

## 8. Verification timeout added

The legacy `VerificationHandler` had **no timeout**. A `PENDING` entry created
by `POST /link-player` lived in memory forever if the player never clicked the
prompt. Over a long session this could accumulate stale entries and prevent
re-linking attempts from being handled correctly.

The new plugin adds:

| Config key | Default | Description |
|---|---|---|
| `modules.api.verification-timeout` | `2m` | How long a PENDING verification entry lives |

A scrub task runs every `timeout / 4` (30 seconds at the default) and removes
all entries whose age exceeds the configured timeout. The requesting browser
receives `declined` on the next poll after the entry is scrubbed.

---

## 9. Intentionally NOT ported

The following constructs from the legacy plugins were removed and are not
present in `kreiscraft-java`:

| Legacy construct | Reason for removal |
|---|---|
| `public static Kreiscraft.instance` / `Kreiscraft.plugin` | Use injected `Ctx` / services |
| `public static Kreiscraft.USERS` (`HashMap<UUID, User>`) | Use `UserService` |
| `static ConfigManager.configs` | Use injected `ConfigService` |
| `PlayerCache` config class | Dead code in the legacy plugin |
| Decompiled `SignColor` source | Replaced with from-scratch listener |
| Reflection-based command/listener registration | Replaced with explicit per-module registration |
| `Bukkit.getMaxPlayers()` set to current year | Dropped as unclear-intent easter egg |
| Gson dependency | Jackson only |
| PlaceholderAPI softdepend | Removed |
| Offline ender chest (`OfflineInventoryReader.readEnderChest`) | Deferred to Plan 5+ |
| `AsyncChatEvent` scraping in `ChatWebsocketHandler` | Replaced with `ChatBridge` subscription |
| `Bukkit.dispatchCommand("whitelist add …")` | Replaced with `WhitelistService.addJavaPlayer(name)` |
| `ScheduledExecutorService` as static field | Now a non-static field of `ApiServer`, shut down in `ApiModule.disable()` |

---

## 10. Behavioral fixes summary

All fixes below were introduced during the Plan 4 `api` module port.

| Fix | Description | Status |
|---|---|---|
| Hardcoded API config | Port, CORS, cookie name/max-age, heartbeat, timeout were all compile-time constants. Moved to `modules.api.*` in `config.yml`. | Fixed in api module port |
| Unauthenticated whitelist endpoints | `POST /whitelist` and `GET /whitelist` accepted requests from any client. Now require `X-Kreiscraft-Api-Key` header; empty key returns 503. | Fixed in api module port |
| Name injection via `/whitelist` | Player name was concatenated directly into a `whitelist add <name>` console command string without sanitisation. Now validated against `[a-zA-Z0-9_]{1,16}`. | Fixed in api module port |
| Thread-unsafe `VerificationHandler` maps | `HashMap` read/written from HTTP thread and Bukkit main thread concurrently. Replaced with `ConcurrentHashMap` + atomic `compute()`. | Fixed in api module port |
| NPE in `unlinkUser` | `server.getPlayer(uuid).sendMessage(...)` throws NPE when the player has gone offline between the link and the unlink. Added null-check before `sendMessage`. | Fixed in api module port |
| `POST /unlink-player` returns 204 on no-link | Legacy returned `204 No Content` when no player was linked to the session. Should be `400 Bad Request` (caller made an invalid request). | Fixed in api module port |
| `POST /link-player` returns 202 when player offline | Legacy returned `202 Accepted` even when the named player was not online (verification can never complete). Now returns `422 Unprocessable Entity`. | Fixed in api module port |
| `ClickEvent.callback()` verification prompt | Paper's `ClickEvent.callback()` is not a stable production API. Replaced with an InvUI chest GUI consistent with the plugin's GUI conventions. | Fixed in api module port |
| No verification timeout | `PENDING` entries accumulated forever. Added `modules.api.verification-timeout` (default `2m`) with a periodic scrub task. | Fixed in api module port |
| Gson / Jackson double-mapping inconsistency | HTTP routes used Jackson (Javalin default); WebSocket messages used Gson. Wire format inconsistency and different null-handling. Jackson used exclusively throughout. | Fixed in api module port |
| `AsyncChatEvent` scraping in chat WS bridge | Legacy `ChatWebsocketHandler` subscribed to `AsyncChatEvent` and read the (potentially cancelled) event message to forward to WebSocket clients. Replaced with `ChatBridge` subscription which only emits confirmed, non-cancelled chat. | Fixed in api module port |
| WebSocket `onClose` handler empty | The `ws.onClose` callback was a no-op; the disconnected `WsContext` was never removed from `connectedClients`. Subsequent heartbeats attempted to write to the closed session. Now the context is removed on close. | Fixed in api module port |
| Static `ScheduledExecutorService` in chat handler | The executor was a `static` field, meaning it was never shut down on plugin disable and leaked across reloads. Now a non-static field of `ApiServer` shut down in `ApiModule.disable()`. | Fixed in api module port |
| `Bukkit.getScheduler()` called directly for whitelist | Legacy used `Bukkit.getScheduler().runTask(...)` to dispatch the whitelist command to the main thread. Now uses `Scheduler.runOnMain(...)` per the threading rule. | Fixed in api module port |
| `Bukkit.dispatchCommand("whitelist add …")` | Console-command dispatch for whitelist management. Replaced with direct `WhitelistService.addJavaPlayer(name)` call. | Fixed in api module port |
| `TranslatableComponent` `ClassCastException` in chat | Legacy code cast `player.name()` to `TextComponent` unconditionally. Player display names that use `TranslatableComponent` throw `ClassCastException` at runtime. New code uses `PlainTextComponentSerializer`. | Fixed in api module port |
| Hardcoded admin names (`RusticPrism`, `OfficerYoda`) in jail bypass | Jail command hardcoded a list of admin player names for bypass instead of a permission node. | Fixed in api module port (uses `kreiscraft.jail.bypass`) |
| Jail location hardcoded | Kerker/jail spawn coordinates were hardcoded in `KerkerListener`. Now configurable as `modules.jail.location` in `config.yml`. | Fixed in api module port |
| Chat bridging via cancelled-event scraping | `ChatWebsocketHandler` read the raw `AsyncChatEvent.message()` including from cancelled events. Replaced with `ChatBridge` publish/subscribe. | Fixed in api module port |
| Timezone hardcoded to JVM default | No timezone config existed; the JVM's default timezone was used for log timestamps. Now configurable as `core.timezone`. | Fixed in api module port |
| Offline ender chest (`OfflineInventoryReader.readEnderChest`) | `EnderChestCommand` attempted to read ender chests of offline players via NMS reflection. Deferred: the NMS approach was too fragile for Paper 1.21.11. | Deferred (Plan 5+) |
| `chat-format` runtime event priority | The `AsyncChatEvent` listener priority was hardcoded to `LOWEST`. Now configurable as `modules.chat-format.event-priority`, but dynamic re-registration without a restart is not yet supported. | Deferred (Plan 5+) |
