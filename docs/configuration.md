# Kreiscraft Configuration Reference

This document is the authoritative reference for every configuration key available in
`src/main/resources/config.yml`. Keys are grouped by module section, matching the YAML
hierarchy under `modules.<id>`. Defaults shown here come directly from `config.yml`; where
a key is absent from the file, Configurate applies the compact-constructor default noted in the
table.

- For permission node listings, see [`permissions.md`](permissions.md).
- For module purpose descriptions and dependency graphs, see [`modules.md`](modules.md).

**Universal module toggle:** every module block contains an `enabled` key (type `boolean`).
`true` is the shipped default for all modules; operators explicitly opt out by setting it to
`false`. If the key is absent, the ModuleRegistry defaults to `false` (disabled).

**Duration strings** accept the following suffixes: `ms` (milliseconds), `t` (ticks, 50 ms each),
`s` (seconds), `m` (minutes), `h` (hours), `d` (days). Example: `30s`, `5m`, `20t`.

**Location values** are YAML maps with the shape:
`{ world: <world-name>, x: <double>, y: <double>, z: <double>, yaw: <float>, pitch: <float> }`.

**Vector values** are YAML maps with the shape `{ x: <double>, y: <double>, z: <double> }`.

---

## core (global)

Global keys live under the top-level `core:` node, not under `modules`.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `language` | `String` | `de` | Language code for loading `lang/<language>.yml` message files. |
| `timezone` | `String` | `Europe/Berlin` | Java `ZoneId` name used for time-based display and placeholders. |
| `auto-save-interval` | `Duration` | `5m` | Interval between automatic flushes of all registered data files. |
| `debug` | `boolean` | `false` | Enables verbose debug logging across all modules when `true`. |

---

## admin

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the admin module. |
| `timezone` | `String` | `UTC` | Timezone used for last-online time formatting in admin commands. |
| `links.website` | `URI` | `https://kreiscraft.de` | Server website URL surfaced via admin info commands. |
| `links.discord` | `URI` | `https://discord.gg/example` | Discord invite URL surfaced via admin info commands. |
| `links.youtube` | `URI` | `https://youtube.com/@example` | YouTube channel URL surfaced via admin info commands. |

---

## anti-cheat

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the anti-cheat module. |
| `channel` | `String` | `kreiscraft:mod_checker` | Plugin-messaging channel name used to request client mod information. |
| `kick-on-fabric-forge` | `boolean` | `true` | Kicks players whose client reports a Fabric or Forge mod loader when `true`. |
| `handshake-grace-duration` | `Duration` | `5s` | Time after login before an absent mod-check response triggers enforcement. |

---

## api

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the API HTTP/WebSocket server. |
| `host` | `String` | `0.0.0.0` | Bind address for the embedded Javalin HTTP server. |
| `port` | `int` | `8080` | TCP port the Javalin HTTP server listens on. |
| `cors-origins` | `List<String>` | `["https://kreiscraft.de"]` | Allowed CORS origins for API responses. |
| `session-cookie.name` | `String` | `website_user_uuid` | Name of the session cookie used to identify authenticated website users. |
| `session-cookie.max-age` | `Duration` | `30d` | Lifetime of the session cookie; sent as `Max-Age` in the `Set-Cookie` header. |
| `heartbeat-interval` | `Duration` | `20s` | Interval between WebSocket heartbeat pings sent to connected clients. |
| `async-timeout` | `Duration` | `10s` | Maximum time allowed for async HTTP handler completion before a 503 is returned. |
| `metrics-refresh-interval` | `Duration` | `1s` | How often the server-metrics snapshot pushed to WebSocket subscribers is recalculated. |
| `verification-timeout` | `Duration` | `2m` | Maximum time a player verification session may remain open before it is discarded. |
| `admin-api-key` | `String` | `""` | Shared secret required in the `X-Admin-Key` header for admin-only endpoints; empty string disables those endpoints. |

### Deferred / informational keys

The following key is documented for operator awareness but has **no runtime effect** in the
current release.

| Key | Type | Planned Default | Description |
|-----|------|-----------------|-------------|
| `chat-format.event-priority` | `EventPriority` | `LOWEST` | Desired `EventPriority` for the `AsyncChatEvent` listener. Currently hardcoded to `LOWEST` in the listener; runtime priority adjustment requires an `EventBus.register(Listener, EventPriority)` overload deferred to Plan 5+. See also `## chat-format` for the key that operators can set today (it is read but not yet applied at listener-registration time). |

---

## bedrock

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the Bedrock compatibility module. |
| `prefix` | `String` | `.` | String prepended to Bedrock player names to distinguish them from Java players. |

---

## bot

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the bot module. |

> The bot module has no additional tunables beyond the module toggle.

---

## chat-format

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables chat formatting. |
| `event-priority` | `EventPriority` | `LOWEST` | `AsyncChatEvent` listener priority. Currently read into the config record but not applied at listener registration (see `## api` → Deferred keys). |
| `allow-mini-message-in-body` | `boolean` | `false` | When `true`, players may use MiniMessage tags in chat message bodies. |
| `format` | `String` | `<team-prefix><name-colored><team-suffix><gray>: <reset><body>` | MiniMessage template used to render outgoing chat messages. |

---

## clickthrough

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables click-through targeting. |
| `target-block-max-distance` | `int` | `6` | Maximum ray-cast distance (in blocks) when resolving the block behind a transparent block. |

---

## combat

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the combat-tag module. |
| `tag-duration` | `Duration` | `10s` | How long a combat tag persists after the last damage event. |
| `disable-elytra` | `boolean` | `true` | Prevents players from activating elytra while combat-tagged when `true`. |
| `excluded-worlds` | `List<String>` | `["Lobby"]` | World names where combat tagging is suppressed entirely. |
| `default-display` | `CombatDisplay` | `ACTIONBAR` | Default display mode for combat-tag notifications (`CHAT`, `ACTIONBAR`, or `TITLE`). |

---

## effects

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the cosmetic particle effects module. |
| `default-particle` | `Particle` | `HAPPY_VILLAGER` | Bukkit `Particle` enum value used when no per-player particle is configured. |
| `count` | `int` | `25` | Number of particles spawned per effect trigger. |
| `offset-x` | `double` | `0.9` | Particle spawn spread on the X axis relative to the player position. |
| `offset-y` | `double` | `0.9` | Particle spawn spread on the Y axis relative to the player position. |
| `offset-z` | `double` | `0.9` | Particle spawn spread on the Z axis relative to the player position. |
| `force` | `boolean` | `true` | When `true`, particles are sent to all players regardless of client particle settings. |

---

## firework

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the custom firework rocket module. |
| `custom-model-data` | `int` | `696969` | Custom model data value identifying the special firework rocket item. |
| `namespace-key` | `String` | `kreiscraft:firework_rocket` | Namespaced key used to identify the firework item via a persistent data container tag. |

---

## glow

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the glow cosmetic module. |

> The glow module has no additional tunables beyond the module toggle.

---

## invsee

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the inventory inspection module. |

> The invsee module has no additional tunables beyond the module toggle.

---

## jail

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the jail module. |
| `location` | `Location` | `{world: world, x: 126, y: 91, z: 92, yaw: 0, pitch: 0}` | World location where jailed players are teleported and held. |
| `blocked-commands` | `List<String>` | `[msg, tell, w, pay, r]` | Command names (without `/`) that jailed players are prevented from executing. |

---

## plugin-hider

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the plugin-hider module. |
| `default-allowed-commands` | `List<String>` | `[help, spawn, msg, tell, w, r, tps, ping, status]` | Commands available to players who do not have the bypass permission (and would otherwise see "unknown command"). |

---

## positions

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the saved positions module. |
| `gui-rows` | `int` | `6` | Number of rows shown in the positions GUI (1–6). |
| `teleport-on-click` | `boolean` | `true` | When `true`, clicking a position entry in the GUI teleports the player immediately. |

---

## reboot

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the scheduled-reboot module. |
| `default-countdown` | `Duration` | `60s` | Countdown duration used when `/reboot` is issued without an explicit time argument. |
| `title-seconds` | `List<Integer>` | `[500, 60, 30, 10]` | Seconds-remaining values at which a title screen warning is displayed to all players. |
| `chat-seconds` | `List<Integer>` | `[10, 9, 8, 7, 6, 5, 4, 3, 2, 1]` | Seconds-remaining values at which a chat countdown message is broadcast. |

---

## settings-gui

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the per-player settings GUI module. |

> The settings-gui module has no additional tunables beyond the module toggle.

---

## sign-color

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the sign color formatting module. |

> The sign-color module has no additional tunables beyond the module toggle.

---

## spawn

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the spawn module. |
| `teleport-range` | `int` | `100` | Radius (in blocks) around spawn within which the `/spawn` teleport lands randomly. |
| `glide.enabled` | `boolean` | `true` | Enables the elytra glide boost applied when players enter the spawn-glide area. |
| `glide.multiply-value` | `int` | `3` | Velocity multiplier applied to the player's motion when the glide boost activates. |
| `glide.spawn-radius` | `int` | `30` | Radius (in blocks) from the spawn origin within which the glide zone is active. |
| `glide.launch-location` | `Location` | `{world: world, x: -35, y: 141, z: -101, yaw: 0, pitch: 0}` | Optional location of the launch pad that triggers the glide boost. |
| `swap-hand-boost.enabled` | `boolean` | `false` | Enables an additional velocity boost when the player swaps their held item while gliding. |
| `swap-hand-boost.vector-multiplier` | `double` | `3.0` | Scalar applied to the player's current velocity vector on hand-swap boost activation. |
| `spawn-launch.enabled` | `boolean` | `false` | Enables the block-trigger launch-pad that fires a player upward on contact. |
| `spawn-launch.trigger` | `Location` | `{world: world, x: -35, y: 141, z: -101, yaw: 0, pitch: 0}` | Location of the block that triggers the launch-pad velocity injection. |
| `spawn-launch.velocity` | `Vector` | `{x: 0.0, y: 5.0, z: 0.0}` | Velocity vector applied to the player when the launch-pad trigger is activated. |

---

## start-sequence

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the world-border start-sequence module. |
| `countdown` | `Duration` | `10s` | Delay between the `/startsequence` command and the border expansion beginning. |
| `initial-border-size` | `double` | `50.0` | World-border diameter (in blocks) at the start of the sequence. |
| `target-border-size` | `double` | `30000000.0` | Final world-border diameter (in blocks) once the full expansion completes. |
| `phase-1-duration` | `Duration` | `550000s` | Duration of the first expansion phase (border moves from initial to half-target). |
| `phase-2-duration` | `Duration` | `300000s` | Duration of the second expansion phase (border moves from half-target to target). |
| `phase-1-to-2-delay` | `Duration` | `7500ms` | Pause between the end of phase 1 and the start of phase 2. |
| `phase-2-to-instant-delay` | `Duration` | `15000ms` | Pause between the end of phase 2 and the instant snap to the final border size. |

---

## status

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the player status module. |

> The status module has no additional tunables beyond the module toggle.

---

## tablist

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the tab-list header/footer module. |
| `update-interval` | `Duration` | `20t` | How often the tab-list header and footer are refreshed (20 ticks = 1 second). |
| `header` | `String` | `<gradient:#5555ff:#55ffff>Kreiscraft</gradient>` | MiniMessage template rendered as the tab-list header; supports PlaceholderAPI placeholders. |
| `footer` | `String` | `TPS: %tps% \| Players: %server_players%` | MiniMessage template rendered as the tab-list footer; supports PlaceholderAPI placeholders. |

---

## update-mode

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables update-mode (prevents non-admin logins during maintenance). |
| `bypass-permission` | `String` | `kreiscraft.updatemode.bypass` | Permission node that allows a player to join while update-mode is active. |
| `bypass-names` | `List<String>` | `[]` | Player name allowlist that bypasses update-mode without requiring the permission node. |

---

## wither

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the custom wither boss module. |
| `max-health` | `double` | `600.0` | Maximum health points of the custom wither (replaces the vanilla value). |
| `attack-damage` | `double` | `40.0` | Base attack damage dealt by the custom wither's direct attack. |
| `armor` | `double` | `8.0` | Armor attribute value applied to the custom wither. |
| `half-health-threshold` | `double` | `300.0` | Health value at which the wither enters its second phase. |
| `minion-count` | `int` | `4` | Number of wither skeleton minions spawned when the wither reaches half health. |
| `half-health-explosion-power` | `double` | `4.0` | Power of the explosion triggered when the wither first reaches half health. |
| `death-boss-bar-radius` | `int` | `50` | Radius (in blocks) within which players see the wither's death boss-bar animation. |
| `death-explosion-power` | `double` | `20.0` | Power of the explosion triggered on wither death. |

---

## world-mgmt

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | `boolean` | `true` | Enables or disables the world management module. |

> The world-mgmt module has no additional tunables beyond the module toggle.
