# Configuration Reference

Complete reference for every key in Kreiscraft's configuration files.

---

## File Locations

| Purpose | Path |
|---|---|
| Default/template (in JAR) | `src/main/resources/config.yml` |
| Live config (on server) | `plugins/Kreiscraft/config.yml` |
| Language files | `plugins/Kreiscraft/lang/de.yml`, `plugins/Kreiscraft/lang/en.yml` |
| Data files | `plugins/Kreiscraft/data/<name>.yml` |

On first start, `YamlConfigService` copies `config.yml` from the JAR into the data folder if the file does not yet exist (`YamlConfigService.java:25-30`). The lang files follow the same pattern: if the file is absent it is copied from the JAR resources.

Data files (subclasses of `DataFile<T>`) are module-managed YAML files stored under `plugins/Kreiscraft/data/`. They are loaded on demand, flushed on each auto-save tick, and are not part of the main `config.yml`.

---

## Core Settings

Located at the top-level `core:` block. These keys are read directly from the raw `ConfigurationNode` in `KreiscraftBootstrap` — there is no typed `CoreConfig` record.

| Key | Type | Default | Description |
|---|---|---|---|
| `core.language` | String | `de` | Language tag used to select the active lang file from `plugins/Kreiscraft/lang/<language>.yml`. Bundled options: `de`, `en`. |
| `core.timezone` | String | `Europe/Berlin` | IANA timezone ID passed to `ZoneId.of()`. Used by the admin module and any time-based placeholder. |
| `core.auto-save-interval` | Duration | `5m` | How often the auto-save task calls `flushIfDirty()` on every registered `DataFile`. Parsed by `Durations.parse`. |
| `core.debug` | boolean | `false` | Present in the bundled config; reserved for future verbose-logging toggles. Not currently wired to any runtime behaviour. |

---

## Module Enable Flags

Every module follows the pattern:

```yaml
modules:
  <module-id>:
    enabled: true   # or false
```

The `ModuleRegistry` reads `modules.<id>.enabled` before calling `Module.enable()`. Setting it to `false` skips the module entirely; its commands, listeners, and services are never registered.

---

## Per-Module Configuration

Each module's config is deserialized via Configurate's `@ConfigSerializable` object mapping. Field names follow camelCase → kebab-case conversion automatically (e.g., `tagDuration` → `tag-duration`). Fields annotated with `@Setting("explicit-key")` use the given key verbatim.

Modules whose `XxxConfig` record has no fields beyond `enabled` are not listed here: `bot`, `glow`, `invsee`, `settings-gui`, `sign-color`, `status`, `world-mgmt`.

---

### `admin`

| Key (relative to `modules.admin`) | Type | Default | Description |
|---|---|---|---|
| `timezone` | String | `UTC` | Timezone used for admin-visible timestamps. |
| `links.website` | URI | `https://kreiscraft.de` | Server website URL, displayed in admin-facing output. |
| `links.discord` | URI | `https://discord.gg/example` | Discord invite URL. |
| `links.youtube` | URI | `https://youtube.com/@example` | YouTube channel URL. |

---

### `api`

| Key (relative to `modules.api`) | Type | Default | Description |
|---|---|---|---|
| `host` | String | `0.0.0.0` | Bind address for the Javalin HTTP server. |
| `port` | int | `8080` | TCP port. |
| `cors-origins` | List\<String\> | `["https://kreiscraft.de"]` | Allowed CORS origins. Credentials are allowed (`allowCredentials = true`). |
| `session-cookie.name` | String | `website_user_uuid` | Name of the browser-session cookie set by `GET /initialize`. |
| `session-cookie.max-age` | Duration | `30d` | Cookie lifetime (`Max-Age` header). |
| `heartbeat-interval` | Duration | `20s` | Interval at which the WebSocket `PING` is sent to all connected clients. |
| `async-timeout` | Duration | `10s` | Javalin async timeout (`cfg.http.asyncTimeout`). |
| `metrics-refresh-interval` | Duration | `1s` | How often `ServerMetricsCollector` refreshes server metrics on the main thread. |
| `verification-timeout` | Duration | `2m` | How long a pending browser→player link request remains valid before being auto-declined. |
| `admin-api-key` | String | `""` | Secret key for admin endpoints (`X-Kreiscraft-Api-Key` header). **Empty string disables all admin endpoints** (they return `503`). |

> **Reload behaviour:** `host`, `port`, and `cors-origins` trigger a full Javalin server restart on `/kreiscraft reload`. All other `api` fields are baked into sub-services at enable time and require a server restart to take effect. See `ApiModule.java:29-35`.

---

### `anti-cheat`

| Key (relative to `modules.anti-cheat`) | Type | Default | Description |
|---|---|---|---|
| `channel` | String | `kreiscraft:mod_checker` | Plugin messaging channel used for the mod handshake. |
| `kick-on-fabric-forge` | boolean | `true` | Whether to kick players detected as Fabric/Forge clients. |
| `handshake-grace-duration` | Duration | `5s` | Time a newly joined player has to complete the mod-checker handshake before action is taken. |

---

### `bedrock`

| Key (relative to `modules.bedrock`) | Type | Default | Description |
|---|---|---|---|
| `prefix` | String | `.` | Name prefix that identifies Bedrock players (Geyser convention). |

---

### `chat-format`

| Key (relative to `modules.chat-format`) | Type | Default | Description |
|---|---|---|---|
| `event-priority` | EventPriority | `LOWEST` | Bukkit `AsyncChatEvent` priority at which the formatter registers. Values: `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`, `MONITOR`. |
| `allow-mini-message-in-body` | boolean | `false` | Whether players can use MiniMessage tags in their message body. |
| `format` | String | `<team-prefix><name-colored><team-suffix><gray>: <reset><body>` | MiniMessage chat format template. Available placeholders: `<team-prefix>`, `<name-colored>`, `<team-suffix>`, `<body>`. |

---

### `clickthrough`

| Key (relative to `modules.clickthrough`) | Type | Default | Description |
|---|---|---|---|
| `target-block-max-distance` | int | `6` | Maximum block-ray distance when checking what block a player clicks through. |

---

### `combat`

| Key (relative to `modules.combat`) | Type | Default | Description |
|---|---|---|---|
| `tag-duration` | Duration | `10s` | How long a player remains in combat after the last hit. |
| `disable-elytra` | boolean | `true` | Whether elytra flight is disabled during combat. |
| `excluded-worlds` | List\<String\> | `["Lobby"]` | World names where combat tagging is disabled. |
| `default-display` | CombatDisplay | `ACTIONBAR` | Default UI element for showing combat status. Values: `CHAT`, `ACTIONBAR`, `TITLE`. Per-player setting can override this via the settings GUI. |

---

### `effects`

| Key (relative to `modules.effects`) | Type | Default | Description |
|---|---|---|---|
| `default-particle` | Particle | `HAPPY_VILLAGER` | Bukkit `Particle` enum name used for particle effects. |
| `count` | int | `25` | Number of particles to spawn. |
| `offset-x` | double | `0.9` | Particle spread on the X axis. |
| `offset-y` | double | `0.9` | Particle spread on the Y axis. |
| `offset-z` | double | `0.9` | Particle spread on the Z axis. |
| `force` | boolean | `true` | When `true`, particles are sent even to players beyond the normal view distance. |

---

### `firework`

| Key (relative to `modules.firework`) | Type | Default | Description |
|---|---|---|---|
| `custom-model-data` | int | `696969` | Custom model data value on the firework item used to identify Kreiscraft fireworks. |
| `namespace-key` | String | `kreiscraft:firework_rocket` | Namespaced key stored in the item's persistent data to identify Kreiscraft fireworks. |

---

### `jail`

| Key (relative to `modules.jail`) | Type | Default | Description |
|---|---|---|---|
| `location` | Location | *(required)* | Teleport destination when jailing a player. Format: `{ world: <name>, x: <n>, y: <n>, z: <n>, yaw: <n>, pitch: <n> }`. |
| `blocked-commands` | List\<String\> | `[msg, tell, w, pay, r]` | Command names (without `/`) that jailed players are not allowed to execute. |

`location` is required — the module will fail to enable if it is absent from the config file.

---

### `plugin-hider`

| Key (relative to `modules.plugin-hider`) | Type | Default | Description |
|---|---|---|---|
| `default-allowed-commands` | List\<String\> | `[help]` | Commands visible to non-operator players when plugin list is hidden. |

---

### `positions`

| Key (relative to `modules.positions`) | Type | Default | Description |
|---|---|---|---|
| `gui-rows` | int | `6` | Number of rows in the saved-positions GUI (1–6). |
| `teleport-on-click` | boolean | `true` | Whether clicking a saved position in the GUI teleports the player immediately. |

---

### `reboot`

| Key (relative to `modules.reboot`) | Type | Default | Description |
|---|---|---|---|
| `default-countdown` | Duration | `60s` | Default countdown duration when no argument is passed to `/reboot`. |
| `title-seconds` | List\<Integer\> | `[500, 60, 30, 10]` | Seconds-remaining values at which a title is shown to all players. |
| `chat-seconds` | List\<Integer\> | `[10, 9, 8, 7, 6, 5, 4, 3, 2, 1]` | Seconds-remaining values at which a chat message is broadcast. |

---

### `spawn`

| Key (relative to `modules.spawn`) | Type | Default | Description |
|---|---|---|---|
| `teleport-range` | int | `100` | Maximum radius (blocks) for a random teleport near spawn. |
| `glide.enabled` | boolean | `true` | Whether elytra glide is active in the spawn area. |
| `glide.multiply-value` | int | `3` | Velocity multiplier at glide start. Currently stored but not applied at runtime. |
| `glide.spawn-radius` | int | `30` | Block radius around spawn within which glide is active. |
| `glide.launch-location` | Location | *(optional)* | Launch pad location. Null when absent. |
| `swap-hand-boost.enabled` | boolean | `false` | Whether the offhand-swap velocity boost is active while gliding. |
| `swap-hand-boost.vector-multiplier` | double | `3.0` | Multiplier applied to the player's velocity on offhand swap. |
| `spawn-launch.enabled` | boolean | `false` | Whether the block-trigger launch pad is active. |
| `spawn-launch.trigger` | Location | *(optional)* | Block location that triggers the launch. |
| `spawn-launch.velocity` | Vector | `{x: 0.0, y: 5.0, z: 0.0}` | Velocity vector applied on launch. Format: `{ x: <n>, y: <n>, z: <n> }`. |

---

### `start-sequence`

| Key (relative to `modules.start-sequence`) | Type | Default | Description |
|---|---|---|---|
| `countdown` | Duration | `10s` | Countdown before the game begins. |
| `initial-border-size` | double | `50.0` | World border diameter at game start (blocks). |
| `target-border-size` | double | `30000000.0` | World border diameter at full expansion. |
| `phase-1-duration` | Duration | `550000s` | Duration of border phase 1 (slow shrink). |
| `phase-2-duration` | Duration | `300000s` | Duration of border phase 2 (faster shrink). |
| `phase-1-to-2-delay` | Duration | `7500ms` | Pause between phase 1 ending and phase 2 starting. |
| `phase-2-to-instant-delay` | Duration | `15000ms` | Pause between phase 2 ending and instant collapse. |

These keys use explicit `@Setting` annotations in `StartSequenceConfig.java` so camelCase conversion does not apply.

---

### `tablist`

| Key (relative to `modules.tablist`) | Type | Default | Description |
|---|---|---|---|
| `update-interval` | Duration | `20t` (1 s) | How often the tab-list header/footer is refreshed. |
| `header` | String | `<gradient:#5555ff:#55ffff>Kreiscraft</gradient>` | MiniMessage string rendered as the tab-list header. Supports placeholders. |
| `footer` | String | `TPS: %tps% \| Players: %server_players%` | Template rendered as the tab-list footer. Supports `%tps%`, `%server_players%`, and other `PlaceholderResolver` tokens. |

---

### `update-mode`

| Key (relative to `modules.update-mode`) | Type | Default | Description |
|---|---|---|---|
| `bypass-permission` | String | `kreiscraft.updatemode.bypass` | Permission node that allows a player to join even when update mode is active. |
| `bypass-names` | List\<String\> | `[]` | Exact player names that bypass update mode regardless of permission. |

---

### `wither`

| Key (relative to `modules.wither`) | Type | Default | Description |
|---|---|---|---|
| `max-health` | double | `600.0` | Maximum health of the custom Wither boss. |
| `attack-damage` | double | `40.0` | Attack damage dealt per hit. |
| `armor` | double | `8.0` | Armor attribute value. |
| `half-health-threshold` | double | `300.0` | Health value at which the half-health phase triggers. |
| `minion-count` | int | `4` | Number of minions spawned at the half-health phase. |
| `half-health-explosion-power` | double | `4.0` | Explosion power at the half-health trigger. |
| `death-boss-bar-radius` | int | `50` | Block radius within which players see the death boss bar. |
| `death-explosion-power` | double | `20.0` | Explosion power at death. |

---

## Custom Value Types

These types are registered via `KreiscraftSerializers` (`KreiscraftSerializers.java`) and are used wherever a matching field type appears in a `@ConfigSerializable` record.

### `Duration`

Parsed by `DurationSerializer` → `Durations.parse()`. A single integer followed by a unit suffix (case-insensitive). **Compound expressions like `"1h30m"` are not supported — use a single unit.**

| Suffix | Unit |
|---|---|
| `ms` | milliseconds |
| `t` | ticks (1 tick = 50 ms) |
| `s` | seconds |
| `m` | minutes |
| `h` | hours |
| `d` | days |

Examples: `"5m"`, `"10s"`, `"1h"`, `"30d"`, `"500ms"`, `"20t"`

### `Location`

A YAML map with keys `world`, `x`, `y`, `z`, `yaw`, `pitch`. Parsed by `LocationSerializer` via `Locations.fromMap()`.

```yaml
location: { world: world, x: 126, y: 91, z: 92, yaw: 0, pitch: 0 }
```

### `Vector`

A YAML map with keys `x`, `y`, `z`. Parsed by `VectorSerializer`.

```yaml
velocity: { x: 0.0, y: 5.0, z: 0.0 }
```

### `Component`

A MiniMessage string. Parsed by `ComponentSerializer` using `MiniMessage.miniMessage().deserialize()`.

```yaml
header: "<gradient:#5555ff:#55ffff>Kreiscraft</gradient>"
```

### `Material`

A Bukkit material name string (case-insensitive, matched via `Material.matchMaterial()`).

```yaml
default-particle: HAPPY_VILLAGER
```

---

## Hot Reload

Run `/kreiscraft reload` (requires `kreiscraft.admin` permission) to hot-reload without a server restart.

**What reload does:**

1. Calls `ConfigService.reload()` — reparses `plugins/Kreiscraft/config.yml` from disk.
2. Calls `MessageService.reload()` — reloads the active lang file.
3. Calls `ModuleRegistry.reloadConfigs()` — calls `Module.onConfigReload()` on every enabled module.

**What is reloaded without restart:**

- All core settings (`core.*`)
- Most per-module keys

**What requires a server restart:**

- `modules.api.heartbeat-interval`, `async-timeout`, `metrics-refresh-interval`, `verification-timeout` — these are baked into `VerificationService` and `ServerMetricsCollector` at enable time and cannot be changed without rebuilding those objects. A reload that changes `host`, `port`, or `cors-origins` will automatically restart the Javalin server; the other `api` fields will still not change until a full plugin restart. See `ApiModule.java:29-35` and `ApiServer.java:26-33`.
- Adding or removing modules — the `enabled` flag is only evaluated at plugin startup.
