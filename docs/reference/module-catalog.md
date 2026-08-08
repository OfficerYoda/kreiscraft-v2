# Module Catalog

Complete reference for all 25 Kreiscraft modules.

---

## Summary Table

| Module ID | Package | Status | Depends On | Commands | Brief Description |
|-----------|---------|--------|------------|----------|-------------------|
| `admin` | `de.kreiscraft.modules.admin` | stable | none | kreiscraftgamemode, item, skull, head, kreiscraftweather, spawnmob, ip, uuid, lastonline, ping, tps, statistic, sudo, khelp | General admin utility commands (gamemode, item spawning, player info, TPS, weather). |
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

---

## `admin`

**Package:** `de.kreiscraft.modules.admin`
**Depends on:** none
**Default enabled:** true

### Description

Provides a collection of general-purpose admin utility commands: gamemode changes, item and mob spawning, player-info lookups (IP, UUID, last-online, ping, statistics), server TPS display, weather control, sudo, and Kreiscraft help links.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `kreiscraftgamemode <mode> [player]` | `kreiscraft.admin.gamemode` | op | Set a player's gamemode (`survival`/`creative`/`adventure`/`spectator` or `0`–`3`). |
| `item <material> [count]` | `kreiscraft.admin.item` | op | Give yourself the specified item. Alias: `i`. |
| `skull [player]` | `kreiscraft.admin.skull` | op | Give yourself a player-head skull. Alias: `kopf`. |
| `head` | `kreiscraft.admin.head` | op | Swap the item in your main hand with your helmet slot. |
| `kreiscraftweather <clear\|rain\|thunder>` | `kreiscraft.admin.weather` | op | Change the weather in your current world. |
| `spawnmob <entityType> [count]` | `kreiscraft.admin.spawnmob` | op | Spawn mobs at your location. Alias: `sm`. |
| `ip [player]` | `kreiscraft.admin.ip` | op | Show a player's IP address. |
| `uuid [player]` | `kreiscraft.admin.uuid` | op | Show a player's UUID. |
| `lastonline <player>` | `kreiscraft.admin.lastonline` | op | Show when a player was last online (formatted in `timezone`). |
| `ping [player]` | `kreiscraft.admin.ping` | true | Show a player's ping in milliseconds. |
| `tps` | `kreiscraft.admin.tps` | true | Show server TPS. |
| `statistic <player> <stat>` | `kreiscraft.admin.statistic` | op | Show a player's Bukkit `Statistic` value. |
| `sudo <target\|all> <text>` | `kreiscraft.admin.sudo` | op | Force a player (or all players) to chat `text`. |
| `khelp` | `kreiscraft.admin.help` | true | Show configured website/Discord/YouTube links. Alias: `?`. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.admin.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.admin.timezone` | String | `"UTC"` | Timezone used by `/lastonline` to format timestamps (IANA zone ID). |
| `modules.admin.links.website` | URI | `"https://kreiscraft.de"` | Website URL shown by `/khelp`. |
| `modules.admin.links.discord` | URI | `"https://discord.gg/example"` | Discord invite URL shown by `/khelp`. |
| `modules.admin.links.youtube` | URI | `"https://youtube.com/@example"` | YouTube channel URL shown by `/khelp`. |

---

## `anti-cheat`

**Package:** `de.kreiscraft.modules.anticheat`
**Depends on:** none
**Default enabled:** true

### Description

Listens on a plugin-messaging channel (default `kreiscraft:mod_checker`) for reports from an external cheat-detection mod, logs them to `data/hackerlog.yml`, and optionally kicks players who connect with a Fabric or Forge client before the handshake grace period expires.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `hackerlogs <target> [cheats\|xray]` | `kreiscraft.anticheat.view` | op | View stored hack-log entries for a player, optionally filtered by kind. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.anti-cheat.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.anti-cheat.channel` | String | `"kreiscraft:mod_checker"` | Plugin messaging channel to register for incoming cheat reports. |
| `modules.anti-cheat.kick-on-fabric-forge` | boolean | `true` | Kick players whose client brand indicates Fabric or Forge. |
| `modules.anti-cheat.handshake-grace-duration` | Duration | `5s` | Grace window after login before the brand check fires. |

### Services published

- `HackLogService` — query stored hack-log entries by player UUID and kind.

---

## `api`

**Package:** `de.kreiscraft.modules.api`
**Depends on:** none
**Default enabled:** true

### Description

Starts a Javalin HTTP and WebSocket server. Exposes REST routes for player data, whitelist management, browser↔player account linking, and real-time metrics. A WebSocket endpoint relays in-game chat to connected browser clients. Configuration parameters that affect Javalin internals (host, port, CORS origins) restart the server on reload; other parameters (timeouts, intervals) require a full plugin restart.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.api.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.api.host` | String | `"0.0.0.0"` | Bind address for the Javalin server. |
| `modules.api.port` | int | `8080` | Listening port. |
| `modules.api.cors-origins` | List\<String\> | `["https://kreiscraft.de"]` | Allowed CORS origins. |
| `modules.api.session-cookie.name` | String | `"website_user_uuid"` | Session cookie name sent to browser clients. |
| `modules.api.session-cookie.max-age` | Duration | `30d` | Session cookie max-age. |
| `modules.api.heartbeat-interval` | Duration | `20s` | WebSocket heartbeat ping interval. |
| `modules.api.async-timeout` | Duration | `10s` | Timeout for async Javalin handlers. |
| `modules.api.metrics-refresh-interval` | Duration | `1s` | How often server metrics are collected. |
| `modules.api.verification-timeout` | Duration | `2m` | Timeout for pending browser-link verification requests. |
| `modules.api.admin-api-key` | String | `""` | Bearer token required for admin-only API endpoints. |

### Services published

- `VerificationService` — manages pending browser–Bukkit account link requests.

---

## `bedrock`

**Package:** `de.kreiscraft.modules.bedrock`
**Depends on:** none
**Default enabled:** true

### Description

Detects Bedrock Edition players by their username prefix (default `.`) when they connect, records their name and UUID in `data/bedrock-players.yml`, and provides a command to add them to the server whitelist via `WhitelistService`.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `bedrocklist add <name>` | `kreiscraft.bedrock.admin` | op | Add a known Bedrock player to the whitelist. |
| `bedrocklist list` | `kreiscraft.bedrock.admin` | op | Show the count of known Bedrock players. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.bedrock.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.bedrock.prefix` | String | `"."` | Username prefix that identifies a Bedrock player. |

### Services published

- `BedrockService` — query the set of known Bedrock player records.

---

## `bot`

**Package:** `de.kreiscraft.modules.bot`
**Depends on:** none
**Default enabled:** true

### Description

Registers `BotData` (backed by `data/bot.yml`) with the `ConfigService` auto-save scheduler. This data file holds Discord ↔ Minecraft account link state consumed by the `api` module's Discord bot integration. The module itself exposes no commands or listeners.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.bot.enabled` | boolean | `true` | Enable/disable this module. |

---

## `chat-format`

**Package:** `de.kreiscraft.modules.chatformat`
**Depends on:** `status`
**Default enabled:** true

### Description

Intercepts `AsyncChatEvent` (at the configured priority) and reformats each message using a MiniMessage template. The template has access to `<team-prefix>`, `<name-colored>`, `<team-suffix>`, and `<body>` placeholders drawn from `StatusService`. Optionally allows players to use MiniMessage tags inside the chat body.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.chat-format.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.chat-format.event-priority` | EventPriority | `LOWEST` | Bukkit event priority at which the chat listener is registered. |
| `modules.chat-format.allow-mini-message-in-body` | boolean | `false` | If true, players can use MiniMessage tags in chat messages. |
| `modules.chat-format.format` | String | `"<team-prefix><name-colored><team-suffix><gray>: <reset><body>"` | MiniMessage template for the formatted chat line. |

---

## `clickthrough`

**Package:** `de.kreiscraft.modules.clickthrough`
**Depends on:** `settings-gui`
**Default enabled:** true

### Description

Allows players with the clickthrough setting enabled to hit entities that would normally be blocked by adjacent non-solid blocks (e.g. glass panes). The per-player toggle is stored by `SettingsService`. A ray-cast is performed up to `targetBlockMaxDistance` blocks.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.clickthrough.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.clickthrough.target-block-max-distance` | int | `6` | Maximum ray-cast distance in blocks. |

---

## `combat`

**Package:** `de.kreiscraft.modules.combat`
**Depends on:** `settings-gui`
**Default enabled:** true

### Description

Tags players in combat when they deal or receive damage, keeping them tagged for a configurable duration. While tagged, elytra use can be blocked. Worlds listed in `excluded-worlds` are exempt. Combat status is displayed via the per-player preference stored in `SettingsService` (action bar, chat, or title).

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `combat <target> <seconds>` | `kreiscraft.combat.admin` | op | Manually tag a player as in combat for the specified duration. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.combat.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.combat.tag-duration` | Duration | `10s` | How long a player remains combat-tagged after their last damage event. |
| `modules.combat.disable-elytra` | boolean | `true` | If true, deny elytra deployment while a player is combat-tagged. |
| `modules.combat.excluded-worlds` | List\<String\> | `["Lobby"]` | World names in which combat tagging is disabled. |
| `modules.combat.default-display` | CombatDisplay | `ACTIONBAR` | Default combat-status display mode (`ACTIONBAR`, `CHAT`, or `TITLE`). |

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

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `effects` | `kreiscraft.effects.use` | true | Toggle your particle trail on/off. |
| `effects <particle>` | `kreiscraft.effects.use` | true | Set your particle type by Bukkit `Particle` enum name. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.effects.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.effects.default-particle` | Particle | `HAPPY_VILLAGER` | Default particle type for new sessions. |
| `modules.effects.count` | int | `25` | Number of particles spawned per tick. |
| `modules.effects.offset-x` | double | `0.9` | X spread offset passed to `World#spawnParticle`. |
| `modules.effects.offset-y` | double | `0.9` | Y spread offset. |
| `modules.effects.offset-z` | double | `0.9` | Z spread offset. |
| `modules.effects.force` | boolean | `true` | If true, force-send particles to all players regardless of distance. |

### Services published

- `EffectsService` — enable, disable, and query particle effects for players.

---

## `firework`

**Package:** `de.kreiscraft.modules.firework`
**Depends on:** none
**Default enabled:** true

### Description

Registers a Bukkit shaped crafting recipe that yields a firework rocket with the configured `customModelData`. The recipe is unregistered on module disable. No commands, listeners, or services are provided.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.firework.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.firework.custom-model-data` | int | `696969` | Custom model data value set on the crafted firework rocket. |
| `modules.firework.namespace-key` | String | `"kreiscraft:firework_rocket"` | Namespaced key used to register and later unregister the recipe. |

---

## `glow`

**Package:** `de.kreiscraft.modules.glow`
**Depends on:** none
**Default enabled:** true

### Description

Applies or removes the Bukkit `Glowing` effect for a player via `/glow`. State is tracked in `DefaultGlowService` and all active glow effects are cleared on module disable.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `glow` | `kreiscraft.glow.use` | true | Toggle your glow effect on/off. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.glow.enabled` | boolean | `true` | Enable/disable this module. |

### Services published

- `GlowService` — toggle and query glow state for players.

---

## `invsee`

**Package:** `de.kreiscraft.modules.invsee`
**Depends on:** none
**Default enabled:** true

### Description

Opens a GUI showing another player's inventory or ender chest. Players with `kreiscraft.invsee.edit` can modify the inventory directly; without it they get a read-only view. Offline inventory is read from the player data file via `OfflineInventoryReader`; offline ender-chest viewing is not supported.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `invsee <player>` | `kreiscraft.invsee.view` | op | Open a target player's inventory (read-only or editable depending on `kreiscraft.invsee.edit`). |
| `enderchest <player>` | `kreiscraft.invsee.view` | op | Open a target player's ender chest. Aliases: `ec`, `echest`. Online only. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.invsee.enabled` | boolean | `true` | Enable/disable this module. |

---

## `jail`

**Package:** `de.kreiscraft.modules.jail`
**Depends on:** none
**Default enabled:** true

### Description

Teleports a player to the configured jail `location` and records an entry in `data/jail.yml`. While jailed, a configurable set of commands is blocked. On unjail the player is teleported back to their original location. Players with `kreiscraft.jail.bypass` cannot be jailed.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `jail <target> [reason...]` | `kreiscraft.jail.use` | op | Jail a player. |
| `unjail <target>` | `kreiscraft.jail.use` | op | Release a jailed player. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.jail.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.jail.location` | Location | *(required — config.yml default: world/126/91/92)* | The location to teleport jailed players to. |
| `modules.jail.blocked-commands` | List\<String\> | `["msg","tell","w","pay","r"]` | Commands blocked while a player is jailed. |

### Services published

- `JailService` — jail, release, and query jail state for players.

---

## `plugin-hider`

**Package:** `de.kreiscraft.modules.pluginhider`
**Depends on:** none
**Default enabled:** true

### Description

Intercepts `TabCompleteEvent` and filters out command suggestions not in the allowlist, so non-operators cannot discover which plugins are installed. The allowlist is persisted in `data/plugin-hider.yml`. Players with `kreiscraft.pluginhider.bypass` see all commands regardless.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `pluginhider add <command>` | `kreiscraft.pluginhider.admin` | op | Add a command to the allowlist. |
| `pluginhider remove <command>` | `kreiscraft.pluginhider.admin` | op | Remove a command from the allowlist. |
| `pluginhider list` | `kreiscraft.pluginhider.admin` | op | List all currently allowed commands. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.plugin-hider.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.plugin-hider.default-allowed-commands` | List\<String\> | `["help","spawn","msg","tell","w","r","tps","ping","status"]` | Commands pre-populated into `data/plugin-hider.yml` on first load. |

### Services published

- `PluginHiderService` — query and mutate the command allowlist.

---

## `positions`

**Package:** `de.kreiscraft.modules.positions`
**Depends on:** none
**Default enabled:** true

### Description

Allows each player to save named waypoints at their current location and retrieve or teleport to them. A GUI browser displays all saved positions; clicking a position can optionally teleport the player. Positions are persisted per-player in `data/positions.yml`.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `pos` | `kreiscraft.positions.use` | true | Open the positions GUI. |
| `pos set <name>` | `kreiscraft.positions.use` | true | Save current location as `<name>`. |
| `pos get <name>` | `kreiscraft.positions.use` | true | Print coordinates for the named position. |
| `pos delete <name>` | `kreiscraft.positions.use` | true | Remove a named position. |
| `pos list` | `kreiscraft.positions.use` | true | List all saved positions with coordinates. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.positions.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.positions.gui-rows` | int | `6` | Number of rows in the positions GUI. |
| `modules.positions.teleport-on-click` | boolean | `true` | If true, clicking a position in the GUI teleports the player to it. |

### Services published

- `PositionsService` — CRUD for per-player named waypoints.

---

## `reboot`

**Package:** `de.kreiscraft.modules.reboot`
**Depends on:** none
**Default enabled:** true

### Description

Schedules a server restart with a countdown. At seconds listed in `title-seconds` a title is shown to all players; at seconds listed in `chat-seconds` a chat message is broadcast. When the countdown reaches zero the server is stopped via `PaperPlatform`.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `reboot` | `kreiscraft.reboot.admin` | op | Start a restart countdown using the default duration. |
| `reboot <seconds> [reason]` | `kreiscraft.reboot.admin` | op | Start a countdown with a custom duration and optional reason. |
| `reboot cancel` | `kreiscraft.reboot.admin` | op | Cancel an active countdown. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.reboot.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.reboot.default-countdown` | Duration | `60s` | Countdown duration when `/reboot` is used with no arguments. |
| `modules.reboot.title-seconds` | List\<Integer\> | `[500, 60, 30, 10]` | Seconds remaining at which a title warning is shown. |
| `modules.reboot.chat-seconds` | List\<Integer\> | `[10, 9, 8, 7, 6, 5, 4, 3, 2, 1]` | Seconds remaining at which a chat warning is broadcast. |

### Services published

- `RebootService` — start, cancel, and query the restart countdown.

---

## `settings-gui`

**Package:** `de.kreiscraft.modules.settingsgui`
**Depends on:** none
**Default enabled:** true

### Description

Opens an InvUI-backed GUI for per-player settings. Currently exposes two toggles: the clickthrough enable/disable (consumed by the `clickthrough` module) and the combat display mode (consumed by the `combat` module). Settings are persisted in `data/settings.yml`.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `settings` | `kreiscraft.settingsgui.use` | true | Open the settings GUI. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.settings-gui.enabled` | boolean | `true` | Enable/disable this module. |

### Services published

- `SettingsService` — read and write per-player settings (combat display, clickthrough toggle).

---

## `sign-color`

**Package:** `de.kreiscraft.modules.signcolor`
**Depends on:** none
**Default enabled:** true

### Description

Parses MiniMessage tags in sign lines when a player with `kreiscraft.signcolor.use` edits a sign, and in anvil rename fields when the player has `kreiscraft.signcolor.anvil`. No commands are provided.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.sign-color.enabled` | boolean | `true` | Enable/disable this module. |

---

## `spawn`

**Package:** `de.kreiscraft.modules.spawn`
**Depends on:** none
**Default enabled:** true

### Description

Manages the server spawn point (persisted in `data/spawn.yml`). Includes three optional subsystems: a glide zone that boosts elytra velocity near spawn, a swap-hand velocity boost while gliding, and a block-trigger launch pad. The `/spawn` command is restricted to players within `teleportRange` blocks of spawn.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `spawn` | `kreiscraft.spawn.use` | true | Teleport to the server spawn (only works within `teleport-range` blocks). |
| `setspawn` | `kreiscraft.spawn.set` | op | Set the server spawn to your current location. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.spawn.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.spawn.teleport-range` | int | `100` | Maximum distance from spawn within which `/spawn` is usable. |
| `modules.spawn.glide.enabled` | boolean | `true` | Enable the elytra glide zone near spawn. |
| `modules.spawn.glide.multiply-value` | int | `3` | Velocity multiplier at glide start. Currently stored but not applied at runtime. |
| `modules.spawn.glide.spawn-radius` | int | `30` | Radius (blocks) around the `launch-location` that defines the glide zone. |
| `modules.spawn.glide.launch-location` | Location | `null` | Centre of the glide zone. |
| `modules.spawn.swap-hand-boost.enabled` | boolean | `false` | Enable the swap-hand velocity boost while gliding. |
| `modules.spawn.swap-hand-boost.vector-multiplier` | double | `3.0` | Multiplier applied to the player's velocity on swap-hand. |
| `modules.spawn.spawn-launch.enabled` | boolean | `false` | Enable the block-trigger launch pad. |
| `modules.spawn.spawn-launch.trigger` | Location | `null` | Location of the trigger block. |
| `modules.spawn.spawn-launch.velocity` | Vector | `{x:0, y:5, z:0}` | Velocity applied to players stepping on the trigger. |

### Services published

- `SpawnService` — get/set the spawn location and check whether a player is within the spawn radius.

---

## `start-sequence`

**Package:** `de.kreiscraft.modules.startsequence`
**Depends on:** none
**Default enabled:** true

### Description

Triggers the season-start ceremony: a countdown broadcast, followed by three world-border phases (phase 1: slow expansion; phase 2: medium expansion; instant: full size). State is persisted in `data/start-sequence.yml` so a restart does not re-trigger the sequence.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `start` | `kreiscraft.startsequence.admin` | op | Trigger the start sequence for the player's current world. |
| `start reset` | `kreiscraft.startsequence.admin` | op | Reset the started flag so the sequence can be triggered again. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.start-sequence.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.start-sequence.countdown` | Duration | `10s` | Countdown broadcast before the border begins expanding. |
| `modules.start-sequence.initial-border-size` | double | `50.0` | World border diameter at sequence start. |
| `modules.start-sequence.target-border-size` | double | `30000000.0` | Final world border diameter. |
| `modules.start-sequence.phase-1-duration` | Duration | `550000s` | Duration of phase 1 (slow expansion). |
| `modules.start-sequence.phase-2-duration` | Duration | `300000s` | Duration of phase 2 (medium expansion). |
| `modules.start-sequence.phase-1-to-2-delay` | Duration | `7500ms` | Delay between phase 1 completing and phase 2 starting. |
| `modules.start-sequence.phase-2-to-instant-delay` | Duration | `15000ms` | Delay between phase 2 completing and the instant-expansion phase. |

### Services published

- `StartSequenceService` — trigger and reset the start sequence; query whether it has started.

---

## `status`

**Package:** `de.kreiscraft.modules.status`
**Depends on:** none
**Default enabled:** true

### Description

Stores a MiniMessage prefix and suffix for each player, persisted in `data/statuses.yml`. The prefix/suffix is applied to scoreboards/teams and broadcast as a `StatusChangedEvent` so other modules (`chat-format`, `tablist`) can react.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `status prefix <text>` | `kreiscraft.status.set.self` | true | Set your own prefix (≤32 visible characters). |
| `status prefix <target> <text>` | `kreiscraft.status.set.other` | op | Set another player's prefix. |
| `status suffix <text>` | `kreiscraft.status.set.self` | true | Set your own suffix. |
| `status suffix <target> <text>` | `kreiscraft.status.set.other` | op | Set another player's suffix. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.status.enabled` | boolean | `true` | Enable/disable this module. |

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

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.tablist.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.tablist.update-interval` | Duration | `20t` (1 s) | How often the header/footer is refreshed. Changing this requires a restart. |
| `modules.tablist.header` | String | `"<gradient:#5555ff:#55ffff>Kreiscraft</gradient>"` | MiniMessage template for the tab list header. |
| `modules.tablist.footer` | String | `"TPS: %tps% | Players: %server_players%"` | MiniMessage template for the tab list footer. |

---

## `update-mode`

**Package:** `de.kreiscraft.modules.updatemode`
**Depends on:** none
**Default enabled:** true

### Description

When active, kicks players who attempt to log in unless they have the bypass permission or their name appears in `bypass-names`. State (active/inactive) is persisted in `data/update-mode.yml`.

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `updatemode on` | `kreiscraft.updatemode.admin` | op | Activate maintenance mode. Alias sub-command: `an` (German). |
| `updatemode off` | `kreiscraft.updatemode.admin` | op | Deactivate maintenance mode. Alias sub-command: `aus` (German). |
| `updatemode status` | `kreiscraft.updatemode.admin` | op | Show whether maintenance mode is currently active. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.update-mode.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.update-mode.bypass-permission` | String | `"kreiscraft.updatemode.bypass"` | Permission node that allows login during maintenance mode. |
| `modules.update-mode.bypass-names` | List\<String\> | `[]` | Player names that bypass maintenance mode regardless of permission. |

### Services published

- `UpdateModeService` — activate, deactivate, and query the maintenance-mode state.

---

## `wither`

**Package:** `de.kreiscraft.modules.wither`
**Depends on:** none
**Default enabled:** true

### Description

Enhances the vanilla Wither boss. On spawn its health, attack damage, and armour attributes are overridden. When it reaches `half-health-threshold` it spawns `minion-count` Wither Skeletons and triggers an explosion. On death it creates a large explosion and displays a boss-bar effect to players within `death-boss-bar-radius` blocks.

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.wither.enabled` | boolean | `true` | Enable/disable this module. |
| `modules.wither.max-health` | double | `600.0` | Maximum health (HP) assigned on spawn. |
| `modules.wither.attack-damage` | double | `40.0` | Attack damage attribute value. |
| `modules.wither.armor` | double | `8.0` | Armour attribute value. |
| `modules.wither.half-health-threshold` | double | `300.0` | HP at which the half-health phase triggers. |
| `modules.wither.minion-count` | int | `4` | Number of Wither Skeletons spawned at half-health. |
| `modules.wither.half-health-explosion-power` | double | `4.0` | Explosion power at the half-health phase. |
| `modules.wither.death-boss-bar-radius` | int | `50` | Radius (blocks) within which the death boss-bar is shown. |
| `modules.wither.death-explosion-power` | double | `20.0` | Explosion power on Wither death. |

---

## `world-mgmt`

**Package:** `de.kreiscraft.modules.worldmgmt`
**Depends on:** none
**Default enabled:** true

### Description

Provides three world-management commands: teleport to any loaded world's spawn, set the spawn point of the current world, and compute Overworld ↔ Nether portal coordinate translations (÷8 / ×8).

### Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `world <name>` | `kreiscraft.worldmgmt.world` | op | Teleport to the spawn of the named world. |
| `setworldspawn` | `kreiscraft.worldmgmt.setworldspawn` | op | Set the current world's spawn to your location. Alias: `sws`. |
| `portal nether [x z]` | `kreiscraft.worldmgmt.portal` | true | Compute the Nether coordinates for current (or given) Overworld coords (÷8) and teleport. |
| `portal overworld [x z]` | `kreiscraft.worldmgmt.portal` | true | Compute the Overworld coordinates for current (or given) Nether coords (×8) and teleport. |

### Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `modules.world-mgmt.enabled` | boolean | `true` | Enable/disable this module. |
