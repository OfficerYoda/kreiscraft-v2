---
title: Module Catalog
---

Catalog of all 25 Kreiscraft modules: purpose, dependencies, services, and events.

---

## Summary Table

| Module ID | Package | Status | Depends On | Commands | Brief Description |
|-----------|---------|--------|------------|----------|-------------------|
| `admin` | `de.kreiscraft.modules.admin` | stable | none | kreiscraftgamemode, item, skull, head, kreiscraftweather, spawnmob, ip, uuid, lastonline, ping, tps, statistic, sudo, kreiscrafthelp | General admin utility commands (gamemode, item spawning, player info, TPS, weather). |
| `anti-cheat` | `de.kreiscraft.modules.anticheat` | stable | none | hackerlogs | Detects Fabric/Forge clients via plugin messaging and persists hack reports from an external mod-checker. |
| `api` | `de.kreiscraft.modules.api` | stable | none | — | Javalin HTTP/WebSocket server exposing player data, whitelist management, metrics, and a bidirectional chat bridge. |
| `bedrock` | `de.kreiscraft.modules.bedrock` | stable | none | bedrocklist | Detects and tracks Bedrock Edition players by username prefix; allows adding them to the whitelist. |
| `bot` | `de.kreiscraft.modules.bot` | stable | none | — | Registers the data file used by the Discord bot integration for player–account linking state. |
| `chat-format` | `de.kreiscraft.modules.chatformat` | stable | `status` | — | Renders chat messages with a configurable MiniMessage template that embeds status prefix/suffix. |
| `clickthrough` | `de.kreiscraft.modules.clickthrough` | stable | `settings-gui` | — | Lets players hit entities through non-solid blocks; toggled per-player via the settings GUI. |
| `combat` | `de.kreiscraft.modules.combat` | stable | `settings-gui` | combat | Tags players as in combat on damage; optionally blocks elytra use while tagged. |
| `effects` | `de.kreiscraft.modules.effects` | stable | none | effects | Lets players toggle and choose a personal particle trail emitted around them each tick. |
| `firework` | `de.kreiscraft.modules.firework` | stable | none | — | Registers a custom-model-data firework rocket crafting recipe. |
| `glow` | `de.kreiscraft.modules.glow` | stable | none | glow | Lets players toggle a persistent glow (Glowing effect) on themselves. |
| `invsee` | `de.kreiscraft.modules.invsee` | stable | none | invsee, enderchest | Allows admins to view (and optionally edit) another player's inventory or ender chest. |
| `jail` | `de.kreiscraft.modules.jail` | stable | none | jail, unjail | Teleports players to a configured jail location and restricts a configurable set of commands while jailed. |
| `plugin-hider` | `de.kreiscraft.modules.pluginhider` | stable | none | pluginhider | Hides non-allowlisted commands from tab-completion to obscure the full plugin list. |
| `positions` | `de.kreiscraft.modules.positions` | stable | none | pos | Saves and retrieves named waypoints per player; includes a GUI browser and optional click-to-teleport. |
| `reboot` | `de.kreiscraft.modules.reboot` | stable | none | reboot | Schedules a graceful server restart with configurable countdown titles and chat warnings. |
| `settings-gui` | `de.kreiscraft.modules.settingsgui` | stable | none | settings | Per-player settings GUI (clickthrough toggle, combat display mode selection). |
| `sign-color` | `de.kreiscraft.modules.signcolor` | stable | none | — | Parses MiniMessage tags in sign text and anvil renames for players with the appropriate permission. |
| `spawn` | `de.kreiscraft.modules.spawn` | stable | none | spawn, setspawn | Manages the server spawn point; includes an elytra glide zone, optional swap-hand boost, and a block-trigger launch pad. |
| `start-sequence` | `de.kreiscraft.modules.startsequence` | stable | none | start | Triggers the season-start sequence: countdown, world border expansion across three phases. |
| `status` | `de.kreiscraft.modules.status` | stable | none | status | Stores and applies per-player MiniMessage prefix/suffix shown in chat and on the tablist. |
| `tablist` | `de.kreiscraft.modules.tablist` | stable | none | — | Refreshes the tab list header and footer on a configurable tick interval using PlaceholderResolver. |
| `update-mode` | `de.kreiscraft.modules.updatemode` | stable | none | updatemode | Maintenance mode that kicks non-bypassed players on login. |
| `wither` | `de.kreiscraft.modules.wither` | stable | none | — | Enhances the Wither boss with custom health, damage, armour, minion spawning, and a death explosion. |
| `world-mgmt` | `de.kreiscraft.modules.worldmgmt` | stable | none | world, setworldspawn, portal | Commands for teleporting between worlds, setting world spawns, and computing Nether/Overworld portal coordinates. |

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

## `bot`

**Package:** `de.kreiscraft.modules.bot`
**Depends on:** none
**Default enabled:** true

### Description

Registers `BotData` (backed by `data/bot.yml`) with the `ConfigService` auto-save scheduler. This data file holds Discord ↔ Minecraft account link state consumed by the `api` module's Discord bot integration. The module itself exposes no commands or listeners.

---

## `chat-format`

**Package:** `de.kreiscraft.modules.chatformat`
**Depends on:** `status`
**Default enabled:** true

### Description

Intercepts `AsyncChatEvent` (at the configured priority) and reformats each message using a MiniMessage template. The template has access to `<team-prefix>`, `<name-colored>`, `<team-suffix>`, and `<body>` placeholders drawn from `StatusService`. Optionally allows players to use MiniMessage tags inside the chat body.

---

## `clickthrough`

**Package:** `de.kreiscraft.modules.clickthrough`
**Depends on:** `settings-gui`
**Default enabled:** true

### Description

Allows players with the clickthrough setting enabled to hit entities that would normally be blocked by adjacent non-solid blocks (e.g. glass panes). The per-player toggle is stored by `SettingsService`. A ray-cast is performed up to `targetBlockMaxDistance` blocks.

---

## `combat`

**Package:** `de.kreiscraft.modules.combat`
**Depends on:** `settings-gui`
**Default enabled:** true

### Description

Tags players in combat when they deal or receive damage, keeping them tagged for a configurable duration. While tagged, elytra use can be blocked. Worlds listed in `excluded-worlds` are exempt. Combat status is displayed via the per-player preference stored in `SettingsService` (action bar, chat, or title).

### Events published

- `CombatTaggedEvent` — fired when a player's combat tag is set or refreshed.

### Services published

- `CombatService` — query and manipulate combat tag state for players.

---

## `effects`

**Package:** `de.kreiscraft.modules.effects`
**Depends on:** none
**Default enabled:** true

### Description

Spawns a configurable particle effect around a player each tick while they have effects enabled. Players can toggle their trail on/off and choose the particle type via `/effects`. State is stored in memory (not persisted).

### Services published

- `EffectsService` — enable, disable, and query particle effects for players.

---

## `firework`

**Package:** `de.kreiscraft.modules.firework`
**Depends on:** none
**Default enabled:** true

### Description

Registers a Bukkit shaped crafting recipe that yields a firework rocket with the configured `customModelData`. The recipe is unregistered on module disable. No commands, listeners, or services are provided.

---

## `glow`

**Package:** `de.kreiscraft.modules.glow`
**Depends on:** none
**Default enabled:** true

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

Teleports a player to the configured jail `location` and records an entry in `data/jail.yml`. While jailed, a configurable set of commands is blocked. On unjail the player is teleported back to their original location. Players with `kreiscraft.jail.bypass` cannot be jailed.

### Services published

- `JailService` — jail, release, and query jail state for players.

---

## `plugin-hider`

**Package:** `de.kreiscraft.modules.pluginhider`
**Depends on:** none
**Default enabled:** true

### Description

Intercepts `TabCompleteEvent` and filters out command suggestions not in the allowlist, so non-operators cannot discover which plugins are installed. The allowlist is persisted in `data/plugin-hider.yml`. Players with `kreiscraft.pluginhider.bypass` see all commands regardless.

### Services published

- `PluginHiderService` — query and mutate the command allowlist.

---

## `positions`

**Package:** `de.kreiscraft.modules.positions`
**Depends on:** none
**Default enabled:** true

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

Parses MiniMessage tags in sign lines when a player with `kreiscraft.signcolor.use` edits a sign, and in anvil rename fields when the player has `kreiscraft.signcolor.anvil`. No commands are provided.

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

Triggers the season-start ceremony: a countdown broadcast, followed by three world-border phases (phase 1: slow expansion; phase 2: medium expansion; instant: full size). State is persisted in `data/start-sequence.yml` so a restart does not re-trigger the sequence.

### Services published

- `StartSequenceService` — trigger and reset the start sequence; query whether it has started.

---

## `status`

**Package:** `de.kreiscraft.modules.status`
**Depends on:** none
**Default enabled:** true

### Description

Stores a MiniMessage prefix and suffix for each player, persisted in `data/statuses.yml`. The prefix/suffix is applied to scoreboards/teams and broadcast as a `StatusChangedEvent` so other modules (`chat-format`, `tablist`) can react.

### Events published

- `StatusChangedEvent` — fired when a player's prefix or suffix changes.

### Services published

- `StatusService` — get and set per-player MiniMessage prefix/suffix.

---

## `tablist`

**Package:** `de.kreiscraft.modules.tablist`
**Depends on:** none
**Default enabled:** true

### Description

Updates the tab list header and footer for all online players on a repeating scheduler task. Header and footer are MiniMessage strings resolved through `PlaceholderResolver` (supports `%tps%`, `%server_players%`, etc.). Changing `update-interval` requires a restart.

---

## `update-mode`

**Package:** `de.kreiscraft.modules.updatemode`
**Depends on:** none
**Default enabled:** true

### Description

When active, kicks players who attempt to log in unless they have the bypass permission or their name appears in `bypass-names`. State (active/inactive) is persisted in `data/update-mode.yml`.

### Services published

- `UpdateModeService` — activate, deactivate, and query the maintenance-mode state.

---

## `wither`

**Package:** `de.kreiscraft.modules.wither`
**Depends on:** none
**Default enabled:** true

### Description

Enhances the vanilla Wither boss. On spawn its health, attack damage, and armour attributes are overridden. When it reaches `half-health-threshold` it spawns `minion-count` Wither Skeletons and triggers an explosion. On death it creates a large explosion and displays a boss-bar effect to players within `death-boss-bar-radius` blocks.

---

## `world-mgmt`

**Package:** `de.kreiscraft.modules.worldmgmt`
**Depends on:** none
**Default enabled:** true

### Description

Provides three world-management commands: teleport to any loaded world's spawn, set the spawn point of the current world, and compute Overworld ↔ Nether portal coordinate translations (÷8 / ×8).
