---
title: Configuration Reference
---

Complete reference for every key in Kreiscraft's configuration files.

---

## File Locations

| Purpose | Path |
|---|---|
| Default/template (in JAR) | `src/main/resources/config.yml` |
| Live config (on server) | `plugins/Kreiscraft/config.yml` |
| Language files | `plugins/Kreiscraft/lang/de.yml`, `plugins/Kreiscraft/lang/en.yml` |
| Data files | `plugins/Kreiscraft/data/<name>.yml` |

On first start, `YamlConfigService` copies `config.yml` from the JAR into the data folder if the file does not yet exist. The lang files follow the same pattern: if the file is absent it is copied from the JAR resources.

Data files (subclasses of `DataFile<T>`) are module-managed YAML files stored under `plugins/Kreiscraft/data/`. They are loaded on demand, flushed on each auto-save tick, and are not part of the main `config.yml`. Player preferences are the exception: `data/settings.yml` is owned by the always-available `PlayerPreferencesService` published by the composition root, not by a module, so a player's language, click-through, combat display, effects, and glow preferences persist even when the settings GUI or the consuming module is disabled.

---

## Core Settings

Located at the top-level `core:` block. These keys are read directly from the raw `ConfigurationNode` in `KreiscraftBootstrap` — there is no typed `CoreConfig` record.

| Key | Type | Default | Description |
|---|---|---|---|
| `core.language` | String | `de` | Server language: selects the fallback language tree from `plugins/Kreiscraft/lang/<language>.yml` and is used for console output. Bundled options: `de`, `en`. A player's persisted language in `data/settings.yml` takes precedence for output addressed to them. |
| `core.timezone` | String | `Europe/Berlin` | Canonical IANA timezone ID passed to `ZoneId.of()`. Used for every absolute timestamp and time-based placeholder across the plugin. |
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

The bundled `config.yml` enables most modules. The following five are **disabled by default** and must be opted in explicitly by setting `enabled: true`:

| Module ID | Rationale |
|-----------|-----------|
| `effects` | Cosmetic player particle trails; opt in per server. |
| `firework` | Adds a custom crafting recipe that changes vanilla progression; opt in explicitly. |
| `glow` | Cosmetic player glow effect; opt in per server. |
| `plugin-hider` | Intentionally disabled by default — command hiding is a server-specific policy decision. |
| `positions` | Intentionally disabled by default — the waypoint GUI overlaps with other teleport tooling. |

Enabling any of these activates its existing implementation; no additional configuration is required.

---

## Server Configuration Editing

Operator-owned values are changed through one validated boundary, `ConfigService.edit(ConfigEdit)`.
The proposed value is applied to a detached copy of the live configuration and validated there;
nothing changes unless the complete candidate is valid and durably written.

- A rejected value or a failed write leaves both the running configuration and `config.yml`
  untouched.
- A successful edit preserves existing comments and unrelated keys, writes a flushed temporary
  file, and replaces `config.yml` using an atomic move where the filesystem supports it.
- Every editable field declares its value type, validator, GUI exposure, and reload lifecycle.
  Module `enabled` flags are **restart-only**: `/kreiscraft reload` does not start or stop modules.

The declared fields live in `ServerConfigSchema`, which the Server Configuration GUI enumerates.
Fields that are not declared there remain YAML-only.

---

## Per-Module Configuration

Each module's config is deserialized via Configurate's `@ConfigSerializable` object mapping. Field names follow camelCase → kebab-case conversion automatically (e.g., `tagDuration` → `tag-duration`). Fields annotated with `@Setting("explicit-key")` use the given key verbatim.

Modules whose `XxxConfig` record has no fields beyond `enabled` are not listed here: `glow`, `invsee`, `settings-gui`, `sign-color`, `status`, `world-mgmt`.

---

### `admin`

| Key (relative to `modules.admin`) | Type | Default | Description |
|---|---|---|---|
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

> See [Hot Reload](#hot-reload) for which API settings require a server restart.

---

### `anti-cheat`

| Key (relative to `modules.anti-cheat`) | Type | Default | Description |
|---|---|---|---|
| `channel` | String | `kreiscraft:mod_checker` | Plugin messaging channel used for the mod handshake. |
| `kick-on-fabric-forge` | boolean | `false` | Opt-in: whether to kick players detected as Fabric/Forge clients. Disabled by default. |
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
| `format` | String | `<team-prefix> <name-colored> <team-suffix><gray> » <reset><body>` | MiniMessage chat format template. Available placeholders: `<team-prefix>`, `<name-colored>`, `<team-suffix>`, `<body>`. |

---

### `combat`

| Key (relative to `modules.combat`) | Type | Default | Description |
|---|---|---|---|
| `tag-duration` | Duration | `5s` | How long a player remains in combat after the last hit. |
| `disable-elytra` | boolean | `true` | Whether elytra use is disabled during combat. While tagged, a worn elytra is removed and returned to the inventory (or dropped, glowing and owner-protected, when there is no space). |
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
| `blocked-commands` | List\<String\> | `[msg, tell, w, r]` | Command names (without `/`) that jailed players are not allowed to execute. |

`location` is required — the module will fail to enable if it is absent from the config file.

`/jail`, `/kerker`, and `/kreiscrafthelp` are always allowed for jailed players regardless of `blocked-commands`.

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
| `location` | Location | *(optional)* | Operator-owned server spawn point, written by `/setspawn`. Absent until a spawn has been set; `/spawn` reports that no spawn is set. Not migrated from the retired `data/spawn.yml`. |
| `spawn-radius` | int | `50` | The single Spawn Radius (blocks) that bounds both spawn access and glide eligibility. `/spawn` measures horizontal (XZ) distance (the Spawn Access Zone); glide initiation measures full 3D distance (the Glide Zone). |
| `glide.enabled` | boolean | `true` | Whether elytra glide is active around spawn. |
| `glide.multiply-value` | int | `3` | Multiplier applied to the player's facing unit vector when a glide starts, launching them at that many blocks per tick along their look direction. |
| `swap-hand-boost.enabled` | boolean | `false` | Whether the offhand-swap velocity boost is active while gliding. |
| `swap-hand-boost.vector-multiplier` | double | `3.0` | Multiplier applied to the player's facing unit vector on offhand swap while gliding. |
| `glide-boost.enabled` | boolean | `true` | Whether the sneak-triggered boost is active for glides that started inside the Glide Zone. |
| `glide-boost.boost-strength` | double | `2.0` | Multiplier applied to the player's facing unit vector on the sneak boost. |
| `glide-boost.cooldown-seconds` | int | `5` | Per-player cooldown between sneak boosts, in seconds. |

---

### `start-sequence`

| Key (relative to `modules.start-sequence`) | Type | Default | Description |
|---|---|---|---|
| `has-started` | boolean | `false` | Operator-owned completion flag. `true` once the sequence has run; cleared by `/start reset`. Not migrated from the retired `data/start-sequence.yml`. |
| `countdown` | Duration | `10s` | Initial countdown value. The sequence displays `countdown - 1` … `1`, one number per second, then the start title. |
| `initial-border-size` | double | `50.0` | World border diameter at game start (blocks). |
| `target-border-size` | double | `30000000.0` | World border diameter at full expansion. |
| `phase-1-duration` | Duration | `550000t` (27500 s) | Border phase 1 expansion duration, in ticks. |
| `phase-2-duration` | Duration | `300000t` (15000 s) | Border phase 2 expansion duration, in ticks. |
| `phase-1-to-2-delay` | Duration | `150t` (7.5 s) | Delay after the start title before phase 2 begins. |
| `phase-2-to-instant-delay` | Duration | `300t` (15 s) | Delay after the start title before the border snaps to full size. |

These keys use explicit `@Setting` annotations in `StartSequenceConfig.java` so camelCase conversion does not apply.

Durations accept the suffixes `ms` (milliseconds), `t` (ticks, 20 ticks = 1 second), `s`, `m`, `h`, and `d`. The border phase durations and delays are expressed in ticks to mirror the original RusticPrism sequence; Bukkit's `WorldBorder.changeSize` API also takes a duration in ticks, so the service derives the tick count directly from each duration. Presentation also mirrors RusticPrism: the countdown title stays for 2 seconds and plays an experience-orb pickup sound, and the start title carries a subtitle and plays a goat-horn sound for 4 seconds.

---

### `tablist`

| Key (relative to `modules.tablist`) | Type | Default | Description |
|---|---|---|---|
| `update-interval` | Duration | `20t` (1 s) | How often the tab-list header/footer is refreshed. Changing this value requires a server restart. |
| `header` | String | Legacy Kreiscraft banner + `Playtime: %playtime%` | MiniMessage string rendered as the tab-list header. Supports placeholders. Hot-reloaded. |
| `footer` | String | Legacy Kreiscraft banner with `Uhrzeit: %system_time%`, `Ping: %ping%` and `Tps: %tps%` | Template rendered as the tab-list footer. Supports `%tps%`, `%ping%`, `%system_time%` and other `PlaceholderResolver` tokens. Hot-reloaded. |

The shipped `header` and `footer` defaults reproduce the original RusticPrism presentation: a
struck-through Kreiscraft banner with the player's playtime in the header, and the current time,
ping, TPS and a second banner in the footer. The templates are re-read on `/kreiscraft reload`;
only `update-interval` needs a restart because the shared repeating task is not rescheduled.

---

### `update-mode`

| Key (relative to `modules.update-mode`) | Type | Default | Description |
|---|---|---|---|
| `active` | boolean | `false` | Operator-owned maintenance state. Written by `/updatemode on`/`off`; read live on login and status checks. Not migrated from the retired `data/update-mode.yml`. |
| `bypass-permission` | String | `kreiscraft.updatemode.bypass` | Permission node that allows a player to stay online when update mode is activated and to join while it is active. |
| `bypass-names` | List\<String\> | `[]` | Exact player names that bypass update mode regardless of permission. |

> Players are also treated as bypassed when they are server operators. The activation and login kick messages are localisable strings in the lang files (`update-mode.kick-message`), so they support full MiniMessage formatting, including multi-line layouts.

---

### `wither`

| Key (relative to `modules.wither`) | Type | Default | Description |
|---|---|---|---|
| `max-health` | double | `600.0` | Maximum health of the custom Wither boss. |
| `attack-damage` | double | `40.0` | Attack damage dealt per hit. |
| `armor` | double | `8.0` | Armor attribute value. |
| `half-health-threshold` | double | `300.0` | Health value at which the half-health phase triggers. |
| `minion-count` | int | `4` | Number of minions spawned at the half-health phase. |
| `spawn-explosion-power` | double | `20.0` | Explosion power replacing the Wither's vanilla post-charge spawn blast. |
| `spawn-explosion-fire` | boolean | `true` | Whether the spawn explosion sets fire to nearby blocks. |
| `spawn-explosion-block-damage` | boolean | `true` | Whether the spawn explosion breaks blocks. |
| `half-health-explosion-power` | double | `4.0` | Explosion power at the half-health trigger. |
| `half-health-explosion-fire` | boolean | `true` | Whether the half-health explosion sets fire to nearby blocks. |
| `half-health-explosion-block-damage` | boolean | `true` | Whether the half-health explosion breaks blocks. |
| `death-explosion-power` | double | `20.0` | Explosion power at death. |
| `death-explosion-fire` | boolean | `true` | Whether the death explosion sets fire to nearby blocks. |
| `death-explosion-block-damage` | boolean | `true` | Whether the death explosion breaks blocks. |
| `death-boss-bar-radius` | int | `50` | Block radius within which players see the death boss bar. |

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

- `modules.api.heartbeat-interval`, `async-timeout`, `metrics-refresh-interval`, `verification-timeout` — these are baked into `VerificationService` and `ServerMetricsCollector` at enable time and cannot be changed without rebuilding those objects. A reload that changes `host`, `port`, or `cors-origins` will automatically restart the Javalin server; the other `api` fields will still not change until a full plugin restart.
- Adding or removing modules — the `enabled` flag is only evaluated at plugin startup.
- `modules.tablist.update-interval` — the shared repeating task is not rescheduled on reload; header/footer templates still hot-reload.
