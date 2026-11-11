---
title: Configuration Reference
---

Complete reference for every key in Kreiscraft's configuration files.

---

## File Locations

### Shattered Crown fixed mechanics

The three waves contain exactly four Anchors at 100%, 65% and 30% effective maximum health.
Population freezes once per wave and scales complete Roster Units with `ceil(population/4)`, without
a cap. These are not configuration knobs. Wave two's unit is fixed: one Ravager, Guardian, Piglin
Brute, Vindicator, Blaze and Breeze at each Anchor (24 total at 1–4 End players; 48 at 5–8).
`defender-count`/`defender-type` control only waves one/three placeholders pending #153/#155;
`defender-health` and available native attack attributes use the validated health/damage settings.
Weapons/projectiles retain native damage, not guaranteed final HP loss. `ground-search-radius`
finds nearby surviving defender footing; Anchors use fixed X/Z and current highest/last-known height.
Legacy `final-health-fraction` and `ground-max-y` remain accepted for configuration compatibility but
are ignored. Reload still takes effect on the next attempt; debug jumps refresh populations, not config.

| Purpose | Path |
|---|---|
| Default/template (in JAR) | `src/main/resources/config.yml` |
| Live config (on server) | `plugins/Kreiscraft/config.yml` |
| Language files | `plugins/Kreiscraft/lang/de.yml`, `plugins/Kreiscraft/lang/en.yml` |
| Data files | `plugins/Kreiscraft/data/<name>.yml` |

On first start, `YamlConfigService` copies `config.yml` from the JAR into the data folder if the file does not yet exist. The lang files follow the same pattern: if the file is absent it is copied from the JAR resources.

Data files (subclasses of `DataFile<T>`) are module-managed YAML files stored under `plugins/Kreiscraft/data/`. They are loaded on demand, flushed on each auto-save tick, and are not part of the main `config.yml`. Player preferences are the exception: `data/settings.yml` is owned by the always-available `PlayerPreferencesService` published by the composition root, not by a module, so a player's language, click-through, combat display, effects, and TPS bar preferences persist even when the settings GUI or the consuming module is disabled.

---

## Core Settings

Located at the top-level `core:` block. These keys are read directly from the raw `ConfigurationNode` in `KreiscraftBootstrap` — there is no typed `CoreConfig` record.

| Key | Type | Default | Description |
|---|---|---|---|
| `core.language` | String | `en` | Server language: selects the fallback language tree from `plugins/Kreiscraft/lang/<language>.yml` and is used for console output. Bundled options: `de`, `en`. A player's persisted language in `data/settings.yml` takes precedence for output addressed to them. |
| `core.timezone` | String | `Europe/Berlin` | Canonical IANA timezone ID passed to `ZoneId.of()`. Used for every absolute timestamp and time-based placeholder across the plugin. |
| `core.auto-save-interval` | Duration | `5m` | How often the auto-save task calls `flushIfDirty()` on every registered `DataFile`. Parsed by `Durations.parse`. |

---

## Module Enable Flags

Every module follows the pattern:

```yaml
modules:
  <module-id>:
    enabled: true   # or false
```

The `ModuleRegistry` reads `modules.<id>.enabled` before calling `Module.enable()`. Setting it to `false` skips the module entirely; its commands, listeners, and services are never registered.

The bundled `config.yml` enables most modules. The following two are **disabled by default** and must be opted in explicitly by setting `enabled: true`:

| Module ID | Rationale |
|-----------|-----------|
| `firework` | Adds a custom crafting recipe that changes vanilla progression; opt in explicitly. |
| `positions` | Intentionally disabled by default — the waypoint GUI overlaps with other teleport tooling. |

Enable and restart to activate these modules. Shattered Crown defaults to enabled. Verify its designated
world, actual arrival platform, and usable objective terrain against its
[initial Server Configuration profile](/shattered-crown/). Attack tuning and melee windows are
documented in the [hunt guide](/shattered-crown-hunt/); reload/restart behavior is documented in the
[lifecycle guide](/shattered-crown-lifecycle/). Balance remains unvalidated until representative live
playtesting; follow the [live acceptance checklist](/shattered-crown-validation/).

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
  Module `enabled` flags are **restart-only** for activation. Shattered Crown additionally aborts
  immediately when reloaded with `enabled: false`; re-enabling it still requires a restart.

The declared fields live in `ServerConfigSchema`, which the Server Configuration GUI enumerates.
Fields that are not declared there remain YAML-only.

### Server Configuration GUI

Authorized administrators (`kreiscraft.settingsgui.admin`) reach the editor from the Player Settings
root or with `/settings admin`. `ServerConfigSchema.editableFields()` declares the complete safe,
GUI-exposed field set. Every configured module has a section with a representative icon; a section
whose module is currently disabled shows a localized `| disabled` suffix on its entry. Settings
are arranged one related group per row; the module enable toggle is a glass pane in the far-left
slot of the bottom row and explains that the change requires a restart. The GUI picks a control from
each field's value type:

- Boolean — a toggle that flips on click.
- Whole number — a stepper, bounded by the field's validator.
- Decimal number — a stepper, bounded by the field's validator.
- Duration — a cycled set of durations chosen for the field's scale (for example combat tag
  durations versus the start-sequence border duration).
- Enum — a cycled set of the field's allowed values.
- Material — a cycled set of Bukkit material names (supported for any material-typed field).
- Location — set to the editing administrator's current position.
- Text, text list, and integer list — entered through an anvil editor; each value is validated before
  it is written (integer-list entries must be whole numbers).

Every module `enabled` flag is also exposed and is **restart-only**.

Each accepted edit is written immediately through the validated boundary, so closing the GUI never
discards work. Closing reports the changes made in that session: hot-reloadable edits offer a
clickable `/kreiscraft reload` action, and restart-only edits warn that a server restart is required.
Module lifecycle changes are never reported as hot-applied.

#### Lifecycle classification

A field is hot-reloadable only when the owning module re-reads the value on `/kreiscraft reload`.
Everything read once at enable time is marked **restart-only** so the close prompt never promises a
live apply.

| Restart-only field | Why |
|---|---|
| every `modules.*.enabled` | Modules are started or skipped only at plugin start. |
| `core.language` | The message service captures the server language when it is constructed; `reload` only re-reads the language files. |
| `modules.firework.custom-model-data`, `modules.firework.namespace-key` | Read when the firework recipe is registered; the module has no reload hook. |
| `modules.tablist.update-interval` | The shared repeating task is not rescheduled on reload. |

#### Kept YAML-only

Credentials, networking and low-level infrastructure, the tab-list header/footer, and
operator-owned runtime state written by commands stay out of the GUI:

| YAML-only value | Reason |
|---|---|
| `modules.api.admin-api-key` | Credential. |
| `modules.api.host`, `port`, `cors-origins`, `session-cookie`, `heartbeat-interval`, `async-timeout`, `metrics-refresh-interval`, `verification-timeout` | Networking / infrastructure. |
| `modules.anti-cheat.channel` | Plugin-messaging channel (networking). |
| `modules.tablist.header`, `modules.tablist.footer` | Tab-list header/footer. |
| `core.timezone`, `core.auto-save-interval` | Low-level infrastructure / timezone and persistence settings. |
| `modules.update-mode.active`, `modules.start-sequence.has-started` | Operator-owned state written by commands whose side effects the GUI must not bypass. |

---

## Per-Module Configuration

Each module's config is deserialized via Configurate's `@ConfigSerializable` object mapping. Field names follow camelCase → kebab-case conversion automatically (e.g., `tagDuration` → `tag-duration`). Fields annotated with `@Setting("explicit-key")` use the given key verbatim.

Modules with no settings beyond `enabled` are not listed here: `invsee`, `settings-gui`, `sign-color`, `sit`, `status`.

---

### `admin`

| Key (relative to `modules.admin`) | Type | Default | Description |
|---|---|---|---|
| `links.website` | URI | `https://kreiscraft.de` | Server website URL, displayed in admin-facing output. |
| `links.discord` | URI | `https://discord.gg/example` | Discord invite URL. |
| `links.youtube` | URI | `https://youtube.com/@example` | YouTube channel URL. |
| `tps-bar.mspt-green-max` | double | `40.0` | Highest green MSPT value (ms). |
| `tps-bar.mspt-yellow-max` | double | `50.0` | Highest yellow MSPT value (ms); higher values are red. |
| `tps-bar.tps-green-min` | double | `19.0` | Lowest green TPS value. |
| `tps-bar.tps-yellow-min` | double | `15.0` | Lowest yellow TPS value; lower values are red. |
| `tps-bar.ping-green-max` | int | `50` | Highest green ping (ms). |
| `tps-bar.ping-yellow-max` | int | `120` | Highest yellow ping (ms); higher values are red. |
| `tps-bar.mspt-empty` | double | `0.0` | MSPT at which the boss bar is empty. |
| `tps-bar.mspt-full` | double | `50.0` | MSPT at which the boss bar is full. |

All eight TPS bar numbers are editable in `/settings admin` → **Admin** and hot-reload on closing Server Configuration (or `/kreiscraft reload`). Existing installations without this section use and display the defaults without rewriting configuration just to read them. Open bars update on their next one-second refresh; no retoggle is needed.

Each number has its own green/yellow/red color, decided from the unrounded metric. Green takes precedence at inclusive boundaries. The boss bar has 20 notches; its color follows MSPT, and its fill is `(MSPT - mspt-empty) / (mspt-full - mspt-empty)`, clamped to 0–100%. With the defaults, 0 ms is empty, 25 ms is half full, and 50 ms or more is full.

TPS/MSPT whole-number digits stay in the light health color; the decimal point and two decimal digits fade from that color to dark green, orange (`#ffaa00`), or dark red. Ping fades across all its digits, with a single digit staying light. Labels and units keep their localized styling.

`/tps bar` stores the player's on/off choice as `tps-bar-enabled` under their UUID in `data/settings.yml`, independent of the settings GUI module. The existing preferences auto-save and clean-shutdown flush persist it across server restarts. Missing keys default to off without a read-time write. Logout, permission loss, and module shutdown hide the live bar but preserve the preference; join and module startup restore enabled bars only for online players with `kreiscraft.admin.tps`.

GUI validation rejects non-finite, negative, and crossed thresholds. TPS thresholds are limited to 0–20; other values are limited to 100,000. `mspt-full` must be at least 0.1 and strictly greater than `mspt-empty`. Invalid YAML is rejected on load/reload rather than applying unusable fill endpoints.

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

### `ai-chat`

| Key (relative to `modules.ai-chat`) | Type | Default | Description |
|---|---|---|---|
| `api-key` | String | `""` | OpenRouter API key sent as the `Authorization: Bearer` header. The client rejects an empty value. |
| `model` | String | `deepseek/deepseek-v4.1-flash` | Model identifier sent to the OpenRouter chat-completions endpoint. |
| `system-prompt` | String | Bundled assistant prompt | MiniMessage template sent as the system prompt. Its `<ai-trigger>`, `<always-supported-players>` and `<chat-history>` placeholders are expanded to literal text before the request. |
| `response-format` | String | `<prefix> <color:#D17955>AI Claude</color><gray> » <reset><content>` | MiniMessage template used to render the AI reply; `<prefix>` is the lang prefix and `<content>` the model's answer. |
| `ai-trigger` | String | `@claude` | Substring a chat message must contain to trigger an AI request from a player holding `kreiscraft.ai-chat.use`. |
| `context-msg-count` | int | `20` | Maximum number of recent chat lines kept as context and sent with each request. |
| `always-supported-players` | List\<String\> | `[]` | Player names the assistant is instructed to support in subjective disputes, expanded into `<always-supported-players>` in the system prompt. Null and blank entries are dropped. |

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
| `prefix` | String | `.` | Name prefix that identifies Bedrock players (Geyser convention). It is applied when normalizing names, so `/bedrocklist add <name>` registration and login matching agree on the same canonical identity. |

---

### `chat-format`

| Key (relative to `modules.chat-format`) | Type | Default | Description |
|---|---|---|---|
| `allow-mini-message-in-body` | boolean | `false` | Whether players can use MiniMessage tags in their message body. |
| `format` | String | `<kc-prefix> <player-prefix><name-colored><player-suffix><gray> » <reset><body>` | MiniMessage chat format template. Available placeholders: `<kc-prefix>`, `<player-prefix>`, `<name-colored>`, `<player-suffix>`, `<body>`. Present player prefixes include one trailing space; present suffixes include one leading space. Empty affixes add no whitespace. |

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
| `default-particle` | Particle | `HAPPY_VILLAGER` | Display fallback before a player selects a particle; not emitted automatically. |
| `schedule` | Duration | `5t` (5 ticks) | How often each enabled player's particle trail is emitted. Hot-reloaded: the running task is rescheduled. |
| `count` | int | `25` | Number of particles to spawn. |
| `offset-x` | double | `0.9` | Particle spread on the X axis. |
| `offset-y` | double | `0.9` | Particle spread on the Y axis. |
| `offset-z` | double | `0.9` | Particle spread on the Z axis. |
| `force` | boolean | `true` | When `true`, particles are sent even to players beyond the normal view distance. |

Emission is scheduled on the server tick clock and is independent of player movement. Enabled state
and the chosen particle are player preferences stored in `data/settings.yml`; a player who has never
chosen a particle emits nothing. `/effects` reports the effective state, display particle, and usage
without mutating anything; `/effects toggle` flips and persists the state, and `/effects <particle>`
sets and persists the particle while enabling effects immediately.

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
| `location` | Location | `{ world: world, x: 0, y: 100, z: 0, yaw: 0, pitch: 0 }` | Operator-owned teleport destination when jailing a player; written by `/setjail`. Format: `{ world: <name>, x: <n>, y: <n>, z: <n>, yaw: <n>, pitch: <n> }`. |
| `blocked-commands` | List\<String\> | `[msg, tell, w, r]` | Command names (without `/`) that jailed players are not allowed to execute. |

`location` is operator-owned Server Configuration (ADR-10), not module configuration: `/setjail`
stores the executing player's current position through the validated, atomic edit boundary. If the
key is absent, jailing still records the player but no teleport destination is available until a
jail location is set.

`/jail`, `/kerker`, and `/kreiscrafthelp` are always allowed for jailed players regardless of `blocked-commands`.

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
| `title-seconds` | List\<Integer\> | `[500, 60, 30, 10, 5, 4, 3, 2, 1]` | Seconds-remaining values at which a title is shown to all players. |
| `chat-seconds` | List\<Integer\> | `[500, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]` | Seconds-remaining values at which a chat message is broadcast. |

`title-seconds` and `chat-seconds` are hot-reloadable and editable through the Server Configuration
GUI as integer lists.

---

### `spawn`

| Key (relative to `modules.spawn`) | Type | Default | Description |
|---|---|---|---|
| `location` | Location | *(optional)* | Operator-owned server spawn point, written by `/setspawn`. Absent until a spawn has been set; `/spawn` reports that no spawn is set. Not migrated from the retired `data/spawn.yml`. |
| `spawn-radius` | int | `50` | The single Spawn Radius (blocks) that bounds both spawn access and glide eligibility. `/spawn` measures horizontal (XZ) distance (the Spawn Access Zone); glide initiation measures full 3D distance (the Glide Zone). |
| `glide.enabled` | boolean | `true` | Whether elytra glide is active around spawn. |
| `glide.multiply-value` | double | `2` | Multiplier applied to the player's facing unit vector when a glide starts, launching them at that many blocks per tick along their look direction. |
| `glide-boost.enabled` | boolean | `true` | Whether glides started inside the Glide Zone receive one sneak-triggered boost opportunity. |
| `glide-boost.boost-strength` | double | `2.0` | Multiplier applied to the player's facing unit vector when that opportunity is consumed, inside or outside the Glide Zone. |

---

### `start-sequence`

| Key (relative to `modules.start-sequence`) | Type | Default | Description |
|---|---|---|---|
| `has-started` | boolean | `false` | Operator-owned completion flag. `true` once the sequence has run; cleared by `/start reset`. Not migrated from the retired `data/start-sequence.yml`. |
| `countdown` | Duration | `10s` | Countdown value. The ceremony renders `countdown` down through `1`, one number per second, then the start title one second later. |
| `initial-border-size` | double | `50.0` | World border diameter at game start (blocks). |
| `target-border-size` | double | `30000000.0` | World border diameter after the single expansion (blocks). |
| `border-duration` | Duration | `30000s` (30,000 s) | Duration of the one continuous border expansion, configured in seconds. |

Durations accept the suffixes `ms` (milliseconds), `t` (ticks, 20 ticks = 1 second), `s`, `m`, `h`, and `d`. The border duration is configured in seconds but represented internally as a `Duration`; it is converted to ticks only at the Bukkit `WorldBorder` boundary (20 ticks = 1 second). The `trigger` call issues exactly one `WorldBorder.changeSize(target-border-size, border-duration-in-ticks)` — no second phase, final snap, phase timestamp, or remaining-duration state is owned by the plugin. Paper persists the in-progress transition across a graceful restart and resumes it, which was verified on Paper 26.2 build 132; abrupt crashes and cross-version migration are not guaranteed. Presentation: the countdown title stays for 2 seconds and plays an experience-orb pickup sound, and the start title carries a subtitle and plays a goat-horn sound for 4 seconds.

---

### `tablist`

| Key (relative to `modules.tablist`) | Type | Default | Description |
|---|---|---|---|
| `update-interval` | Duration | `20t` (1 s) | How often the tab-list header/footer is refreshed. Changing this value requires a server restart. |
| `header` | String | Legacy Kreiscraft banner + `Playtime: %playtime%` | MiniMessage string rendered as the tab-list header. Supports placeholders. Hot-reloaded. |
| `footer` | String | Legacy Kreiscraft banner with `Uhrzeit: %system_time%`, `Ping: %ping%` and `Tps: %tps%` | Template rendered as the tab-list footer. Supports `%tps%`, `%ping%`, `%system_time%` and other `PlaceholderResolver` tokens. Hot-reloaded. |

The shipped `header` and `footer` defaults reproduce the classic Kreiscraft tab-list presentation: a
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

### `world-mgmt`

| Key (relative to `modules.world-mgmt`) | Type | Default | Description |
|---|---|---|---|
| `allow-nether` | boolean | `true` | Whether portal travel into or out of the Nether is allowed. |
| `allow-end` | boolean | `true` | Whether portal travel into or out of the End is allowed. |

Both flags are editable under **World Management** in the Server Configuration GUI and hot-reload
immediately: they are read from the live configuration root, so `/dimension` toggles also take
effect without a reload. Disabling a dimension cancels portal travel into or out of it and leaves players
already inside untouched. Command and plugin teleports (including `/world`) are not intercepted.
Existing installations that do not yet contain these keys behave as if both are `true`, and the GUI
displays that default without rewriting `config.yml`.

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
location: { world: world, x: 0, y: 100, z: 0, yaw: 0, pitch: 0 }
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

- Most core settings and most per-module keys
- Every field the Server Configuration GUI marks hot-reloadable
- `modules.effects.schedule` — the particle-emission task is cancelled and rescheduled with the new period.

**What requires a server restart:**

- `core.language` — the message service captures the server language when it is constructed; `reload` only re-reads the language trees, so a new server language needs a restart. A player's persisted language still takes precedence for output addressed to them.
- `modules.api.heartbeat-interval`, `async-timeout`, `metrics-refresh-interval`, `verification-timeout` — these are baked into `VerificationService` and `ServerMetricsCollector` at enable time and cannot be changed without rebuilding those objects. A reload that changes `host`, `port`, or `cors-origins` will automatically restart the Javalin server; the other `api` fields will still not change until a full plugin restart.
- Adding or removing modules — the `enabled` flag is only evaluated at plugin startup.
- `modules.firework.custom-model-data`, `modules.firework.namespace-key` — read when the firework recipe is registered.
- `modules.tablist.update-interval` — the shared repeating task is not rescheduled on reload; header/footer templates still hot-reload.
