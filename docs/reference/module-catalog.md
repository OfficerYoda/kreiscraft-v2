---
title: Module Catalog
---

Catalog of all 24 Kreiscraft modules: purpose, dependencies, services, and events.

---

## Summary Table

| Module ID | Package | Status | Depends On | Commands | Brief Description |
|-----------|---------|--------|------------|----------|-------------------|
| `admin` | `de.kreiscraft.modules.admin` | stable | none | kreiscraftgamemode, item, skull, head, kreiscraftweather, spawnmob, ip, uuid, lastonline, ping, tps, statistic, sudo, kreiscrafthelp | General admin utility commands (gamemode, item spawning, player info, TPS, weather). |
| `anti-cheat` | `de.kreiscraft.modules.anticheat` | stable | none | hackerlogs | Detects Fabric/Forge clients via plugin messaging and persists hack reports from an external mod-checker. |
| `api` | `de.kreiscraft.modules.api` | stable | none | — | Javalin HTTP/WebSocket server exposing player data, whitelist management, metrics, and a bidirectional chat bridge. |
| `bedrock` | `de.kreiscraft.modules.bedrock` | stable | none | bedrocklist | Detects and tracks Bedrock Edition players by username prefix; allows adding them to the whitelist. |
| `chat-format` | `de.kreiscraft.modules.chatformat` | stable | `status` | — | Renders chat messages with a configurable MiniMessage template that embeds status prefix/suffix. |
| `clickthrough` | `de.kreiscraft.modules.clickthrough` | stable | `settings-gui` | — | Lets players open containers and Ender Chests through wall signs and filled item frames; toggled per-player via the settings GUI. |
| `combat` | `de.kreiscraft.modules.combat` | stable | `settings-gui` | combat | Tags players as in combat on damage; optionally removes elytra while tagged. |
| `effects` | `de.kreiscraft.modules.effects` | stable | none | effects | Lets players toggle and choose a personal particle trail emitted around them each tick. |
| `firework` | `de.kreiscraft.modules.firework` | stable | none | — | Registers a custom-model-data firework rocket crafting recipe. |
| `glow` | `de.kreiscraft.modules.glow` | stable | none | glow | Lets players toggle a persistent glow (Glowing effect) on themselves. |
| `invsee` | `de.kreiscraft.modules.invsee` | stable | none | invsee, enderchest | Allows admins to view (and optionally edit) another player's inventory or ender chest. |
| `jail` | `de.kreiscraft.modules.jail` | stable | none | jail, kerker | Jails players at a configured jail location and restricts a configurable set of commands while jailed; `/jail` and `/kerker` also release already-jailed players. |
| `plugin-hider` | `de.kreiscraft.modules.pluginhider` | stable | none | pluginhider | Hides non-allowlisted commands from tab-completion to obscure the full plugin list. |
| `positions` | `de.kreiscraft.modules.positions` | stable | none | pos | Saves and retrieves named waypoints per player; includes a GUI browser and optional click-to-teleport. |
| `reboot` | `de.kreiscraft.modules.reboot` | stable | none | reboot | Schedules a graceful server restart with configurable countdown titles and chat warnings. |
| `settings-gui` | `de.kreiscraft.modules.settingsgui` | stable | none | settings | Per-player settings GUI (clickthrough toggle, combat display mode selection). |
| `sign-color` | `de.kreiscraft.modules.signcolor` | stable | none | — | Renders legacy `&` codes, Kreiscraft placeholders and safe MiniMessage in sign text and anvil renames for players with the appropriate permission. |
| `spawn` | `de.kreiscraft.modules.spawn` | stable | none | spawn, setspawn | Manages the server spawn point; includes an elytra glide zone, optional swap-hand boost, and a block-trigger launch pad. |
| `start-sequence` | `de.kreiscraft.modules.startsequence` | stable | none | start | Triggers the season-start sequence: countdown, world border expansion across three phases. |
| `status` | `de.kreiscraft.modules.status` | stable | none | status | Stores per-player MiniMessage prefix/suffix and color, applied to scoreboard teams and chat. |
| `tablist` | `de.kreiscraft.modules.tablist` | stable | none | — | Refreshes the tab list header and footer on a configurable tick interval using PlaceholderResolver. |
| `update-mode` | `de.kreiscraft.modules.updatemode` | stable | none | updatemode | Maintenance mode that kicks non-bypassed players on login. |
| `wither` | `de.kreiscraft.modules.wither` | stable | none | — | Enhances the Wither boss with custom health, damage, armour, minion spawning, and a death explosion. |
| `world-mgmt` | `de.kreiscraft.modules.worldmgmt` | stable | none | world, setworldspawn, portal | Commands for teleporting between worlds, setting world spawns, and computing Nether/Overworld portal coordinates. |

### Default-off modules

Most modules ship with `enabled: true` in the bundled `config.yml`. Five ship **disabled by default**
and only start when an operator explicitly sets `modules.<id>.enabled: true`:

| Module ID | Why it is off by default |
|-----------|--------------------------|
| `effects` | Player-facing particle trails are cosmetic; operators opt in per server. |
| `firework` | Registers a custom crafting recipe that changes vanilla progression; opt in explicitly. |
| `glow` | Player-facing glow effect is cosmetic; operators opt in per server. |
| `plugin-hider` | Intentionally disabled by default — command hiding is a server-specific policy decision. |
| `positions` | Intentionally disabled by default — the waypoint GUI overlaps with other teleport tooling. |

All five implementations are fully functional; enabling the flag is sufficient to activate them.

---

## Per-Module Entries

For command syntax and permissions, see [Commands and Permissions](/reference/commands-and-permissions/).
For config keys and defaults, see [Configuration](/reference/configuration/).

---

## `admin`

**Package:** `de.kreiscraft.modules.admin`
**Depends on:** none
**Default enabled:** true

### Description

Provides a collection of general-purpose admin utility commands: gamemode changes, item and mob spawning, player-info lookups (IP, UUID, last-online, ping, statistics), server TPS display, weather control, sudo, and Kreiscraft help links.

---

## `anti-cheat`

**Package:** `de.kreiscraft.modules.anticheat`
**Depends on:** none
**Default enabled:** true

### Description

Listens on a plugin-messaging channel (default `kreiscraft:mod_checker`) for reports from an external cheat-detection mod, logs them to `data/hackerlog.yml`, and can kick players who connect with a Fabric or Forge client before the handshake grace period expires (disabled by default via `kick-on-fabric-forge`).

### Services published

- `HackLogService` — query stored hack-log entries by player UUID and kind.

---

## `api`

**Package:** `de.kreiscraft.modules.api`
**Depends on:** none
**Default enabled:** true

### Description

Starts a Javalin HTTP and WebSocket server. Exposes REST routes for player data, whitelist management, browser↔player account linking, and real-time metrics. A WebSocket endpoint relays in-game chat to connected browser clients. Configuration parameters that affect Javalin internals (host, port, CORS origins) restart the server on reload; other parameters (timeouts, intervals) require a full plugin restart.

### Services published

- `VerificationService` — manages pending browser–Bukkit account link requests.

---

## `bedrock`

**Package:** `de.kreiscraft.modules.bedrock`
**Depends on:** none
**Default enabled:** true

### Description

Detects Bedrock Edition players by their username prefix (default `.`) when they connect, records their name and UUID in `data/bedrock-players.yml`, lists the known players via `/bedrocklist list`, and provides a command to add them to the server whitelist via `WhitelistService`.

### Services published

- `BedrockService` — query the set of known Bedrock player records.

---

## `chat-format`

**Package:** `de.kreiscraft.modules.chatformat`
**Depends on:** `status`
**Default enabled:** true

### Description

Intercepts `AsyncChatEvent` (at the configured priority) and reformats each message using a MiniMessage template. The template has access to `<team-prefix>`, `<name-colored>`, `<team-suffix>`, and `<body>` placeholders drawn from `StatusService`. Optionally allows players to use MiniMessage tags inside the chat body.

Messages that begin exactly with the Xaero waypoint payload token (`xaero-waypoint`, followed by the protocol's `:` separator) are passed through untouched: the event is left uncancelled so clients and the Xaero mod receive the raw payload, and no formatted message is broadcast or published to `ChatBridge`. Near-matches such as `xaero-waypoints` and ordinary chat are unaffected and formatted normally.

---

## `clickthrough`

**Package:** `de.kreiscraft.modules.clickthrough`
**Depends on:** `settings-gui`
**Default enabled:** true

### Description

Allows players with the clickthrough setting enabled to open containers and Ender Chests by right-clicking an adjacent wall sign or a filled item frame. Target acquisition uses a fixed-distance ray-cast, matching the original behaviour; the per-player toggle is stored by `SettingsService`.

---

## `combat`

**Package:** `de.kreiscraft.modules.combat`
**Depends on:** `settings-gui`
**Default enabled:** true

### Description

Tags players in combat when they deal or receive damage, keeping them tagged for a configurable duration (default `5s`). While tagged, elytra use can be blocked: a continuously swept check removes a worn elytra, returning it to the inventory when space exists or otherwise dropping it glowing and pickup-protected for its owner for a bounded window. Worlds listed in `excluded-worlds` are exempt. Combat status is displayed via the per-player preference stored in `SettingsService` (action bar, chat, or title).

### Events published

- `CombatTaggedEvent` — fired when a player's combat tag is set or refreshed.

### Services published

- `CombatService` — query and manipulate combat tag state for players.

---

## `effects`

**Package:** `de.kreiscraft.modules.effects`
**Depends on:** none
**Default enabled:** false — opt in with `modules.effects.enabled: true`

### Description

Spawns a configurable particle effect around a player each tick while they have effects enabled. Players can toggle their trail on/off and choose the particle type via `/effects`. State is stored in memory (not persisted).

### Services published

- `EffectsService` — enable, disable, and query particle effects for players.

---

## `firework`

**Package:** `de.kreiscraft.modules.firework`
**Depends on:** none
**Default enabled:** false — opt in with `modules.firework.enabled: true`

### Description

Registers a Bukkit shaped crafting recipe that yields a firework rocket with the configured `customModelData`. The recipe is unregistered on module disable. No commands, listeners, or services are provided.

---

## `glow`

**Package:** `de.kreiscraft.modules.glow`
**Depends on:** none
**Default enabled:** false — opt in with `modules.glow.enabled: true`

### Description

Applies or removes the Bukkit `Glowing` effect for a player via `/glow`. State is tracked in `DefaultGlowService` and all active glow effects are cleared on module disable.

### Services published

- `GlowService` — toggle and query glow state for players.

---

## `invsee`

**Package:** `de.kreiscraft.modules.invsee`
**Depends on:** none
**Default enabled:** true

### Description

Opens a GUI showing another player's inventory or ender chest. Players with `kreiscraft.invsee.edit` can modify the inventory directly; without it they get a read-only view. Offline inventory is read from the player data file via `OfflineInventoryReader`; offline ender-chest viewing is not supported.

---

## `jail`

**Package:** `de.kreiscraft.modules.jail`
**Depends on:** none
**Default enabled:** true

### Description

`/jail` and its alias `/kerker` are identical toggles: they jail an unjailed player (teleporting them to the configured jail `location` and recording an entry in `data/jail.yml`) or release an already-jailed player (teleporting them back to their original location). While jailed, a configurable set of commands is blocked; `/jail`, `/kerker`, and `/kreiscrafthelp` are always allowed. Players with `kreiscraft.jail.bypass` cannot be jailed, but the bypass permission does not prevent releasing an already-jailed player.

### Services published

- `JailService` — jail, release, and query jail state for players.

---

## `plugin-hider`

**Package:** `de.kreiscraft.modules.pluginhider`
**Depends on:** none
**Default enabled:** false — intentionally disabled by default; opt in with `modules.plugin-hider.enabled: true`

### Description

Intercepts `TabCompleteEvent` and filters out command suggestions not in the allowlist, so non-operators cannot discover which plugins are installed. The allowlist is persisted in `data/plugin-hider.yml`. Players with `kreiscraft.pluginhider.bypass` see all commands regardless.

### Services published

- `PluginHiderService` — query and mutate the command allowlist.

---

## `positions`

**Package:** `de.kreiscraft.modules.positions`
**Depends on:** none
**Default enabled:** false — intentionally disabled by default; opt in with `modules.positions.enabled: true`

### Description

Allows each player to save named waypoints at their current location and retrieve or teleport to them. A GUI browser displays all saved positions; clicking a position can optionally teleport the player. Positions are persisted per-player in `data/positions.yml`.

### Services published

- `PositionsService` — CRUD for per-player named waypoints.

---

## `reboot`

**Package:** `de.kreiscraft.modules.reboot`
**Depends on:** none
**Default enabled:** true

### Description

Schedules a server restart with a countdown. At seconds listed in `title-seconds` a title is shown to all players; at seconds listed in `chat-seconds` a chat message is broadcast. When the countdown reaches zero the server is stopped via `PaperPlatform`.

### Services published

- `RebootService` — start, cancel, and query the restart countdown.

---

## `settings-gui`

**Package:** `de.kreiscraft.modules.settingsgui`
**Depends on:** none
**Default enabled:** true

### Description

Opens an InvUI-backed GUI for per-player settings. Currently exposes two toggles: the clickthrough enable/disable (consumed by the `clickthrough` module) and the combat display mode (consumed by the `combat` module). Settings are persisted in `data/settings.yml`.

### Services published

- `SettingsService` — read and write per-player settings (combat display, clickthrough toggle).

---

## `sign-color`

**Package:** `de.kreiscraft.modules.signcolor`
**Depends on:** none
**Default enabled:** true

### Description

Parses MiniMessage tags, legacy ampersand colour/decoration codes, and established Kreiscraft `%placeholder%` tokens in sign lines when a player with `kreiscraft.signcolor.use` edits a sign, and in anvil rename fields when the player has `kreiscraft.signcolor.anvil`. MiniMessage is restricted to colour, decoration, rainbow, reset, and gradient tags so players cannot inject interactive or client-affecting tags. No commands are provided.

---

## `spawn`

**Package:** `de.kreiscraft.modules.spawn`
**Depends on:** none
**Default enabled:** true

### Description

Manages the server spawn point (persisted in `data/spawn.yml`). Includes three optional subsystems: a glide zone that boosts elytra velocity near spawn, a swap-hand velocity boost while gliding, and a block-trigger launch pad. The `/spawn` command is restricted to players within `teleportRange` blocks of spawn.

### Services published

- `SpawnService` — get/set the spawn location and check whether a player is within the spawn radius.

---

## `start-sequence`

**Package:** `de.kreiscraft.modules.startsequence`
**Depends on:** none
**Default enabled:** true

### Description

Triggers the season-start ceremony: a countdown (numbers `countdown - 1` … `1`, each with a title and sound), followed by the start title and three world-border phases (phase 1: slow expansion; phase 2: medium expansion; instant: full size). Border phase durations are configured in ticks, matching the original RusticPrism sequence, and passed to Bukkit's tick-based world-border API. State is persisted in `data/start-sequence.yml` so a restart does not re-trigger the sequence.

### Services published

- `StartSequenceService` — trigger and reset the start sequence; query whether it has started.

---

## `status`

**Package:** `de.kreiscraft.modules.status`
**Depends on:** none
**Default enabled:** true

### Description

Stores a MiniMessage prefix, suffix, and ARGB color for each player, persisted in `data/statuses.yml`. Prefix, suffix, and team color are applied to every online player's scoreboard team and broadcast as a `StatusChangedEvent` so other modules (`chat-format`, `tablist`) can react.

### Events published

- `StatusChangedEvent` — fired when a player's prefix, suffix, or color changes.

### Services published

- `StatusService` — get and set per-player MiniMessage prefix/suffix, and set per-player color.

---

## `tablist`

**Package:** `de.kreiscraft.modules.tablist`
**Depends on:** none
**Default enabled:** true

### Description

Updates the tab list header and footer for all online players on a repeating scheduler task. Header and footer are MiniMessage strings resolved through `PlaceholderResolver` (supports `%tps%`, `%ping%`, `%system_time%`, `%playtime%`, etc.). The shipped defaults restore the original Kreiscraft banner, playtime, clock, ping and TPS presentation. A joining player is updated immediately; everyone else is refreshed by the shared task. Header and footer templates hot-reload on `/kreiscraft reload`, but changing `update-interval` requires a restart because the shared task is not rescheduled.

---

## `update-mode`

**Package:** `de.kreiscraft.modules.updatemode`
**Depends on:** none
**Default enabled:** true

### Description

When active, activation immediately disconnects every online player who is not allowed to bypass, and login attempts from non-bypassed players are blocked. A player bypasses if their name is listed in `bypass-names`, they are an operator, or they hold the configured `bypass-permission`. Bypassed players who are online when maintenance is activated receive a notification instead of being disconnected. State (active/inactive) is persisted in `data/update-mode.yml`.

### Services published

- `UpdateModeService` — activate, deactivate, and query the maintenance-mode state.

---

## `wither`

**Package:** `de.kreiscraft.modules.wither`
**Depends on:** none
**Default enabled:** true

### Description

Enhances the vanilla Wither boss. On spawn its health, attack damage, and armour attributes are overridden. When it reaches `half-health-threshold` it spawns `minion-count` Wither Skeletons and triggers an explosion whose power, fire, and block-damage behavior are configurable. On death it creates a large explosion and displays a boss-bar effect to players within `death-boss-bar-radius` blocks.

---

## `world-mgmt`

**Package:** `de.kreiscraft.modules.worldmgmt`
**Depends on:** none
**Default enabled:** true

### Description

Provides three world-management commands: teleport to any loaded world's spawn, set the spawn point of the current world, and compute Overworld ↔ Nether portal coordinate translations (÷8 / ×8).
