---
title: Module Catalog
---

Catalog of all 26 Kreiscraft modules: purpose, dependencies, services, and events.

---

## Summary Table

| Module ID | Package | Status | Depends On | Commands | Brief Description |
|-----------|---------|--------|------------|----------|-------------------|
| `admin` | `de.kreiscraft.modules.admin` | stable | none | item, skull, head, spawnmob, ip, uuid, lastonline, ping, tps, statistic, sudo, kreiscrafthelp | General admin utility commands (item spawning, player info, TPS). |
| `ai-chat` | `de.kreiscraft.modules.aichat` | stable | none | — | Chat-triggered AI replies via the OpenRouter chat-completions API. |
| `anti-cheat` | `de.kreiscraft.modules.anticheat` | stable | none | hackerlogs | Detects Fabric/Forge clients via plugin messaging and persists hack reports from an external mod-checker. |
| `api` | `de.kreiscraft.modules.api` | stable | none | whitelist-requests | Javalin HTTP/WebSocket server exposing player data, whitelist management, metrics, and a bidirectional chat bridge. |
| `bedrock` | `de.kreiscraft.modules.bedrock` | stable | none | bedrocklist | Detects and tracks Bedrock Edition players by username prefix; allows adding them to the whitelist. |
| `chat-format` | `de.kreiscraft.modules.chatformat` | stable | `status` | — | Renders chat messages with a configurable MiniMessage template that embeds status prefix/suffix. |
| `clickthrough` | `de.kreiscraft.modules.clickthrough` | stable | none | — | Lets players open containers and Ender Chests through wall signs and filled item frames; toggled through always-available player preferences. |
| `combat` | `de.kreiscraft.modules.combat` | stable | none | combat | Tags players as in combat on damage; optionally removes elytra while tagged; combat display follows a player preference with a module default. |
| `effects` | `de.kreiscraft.modules.effects` | stable | none | effects | Lets players report, toggle, and choose a personal particle trail emitted on a configurable schedule (default 5 ticks) independent of movement; state and particle persist as player preferences. |
| `dragon-egg` | `de.kreiscraft.modules.dragonegg` | tracking slice | none | — | Compass + dragon head tracks one egg through possession, drops, placement, teleportation and falling; two-minute location pings. |
| `firework` | `de.kreiscraft.modules.firework` | stable | none | — | Registers a custom-model-data firework rocket crafting recipe. |
| `invsee` | `de.kreiscraft.modules.invsee` | stable | none | invsee, enderchest | Allows admins to view (and optionally edit) another player's inventory or ender chest. |
| `jail` | `de.kreiscraft.modules.jail` | stable | none | jail, kerker | Jails players at a configured jail location and restricts a configurable set of commands while jailed; `/jail` and `/kerker` also release already-jailed players. |
| `positions` | `de.kreiscraft.modules.positions` | stable | none | pos | Saves and retrieves named waypoints per player; includes a GUI browser and optional click-to-teleport. |
| `reboot` | `de.kreiscraft.modules.reboot` | stable | none | reboot, restart | Schedules a graceful server restart with configurable countdown titles and chat warnings. |
| `settings-gui` | `de.kreiscraft.modules.settingsgui` | stable | none | settings | Module-aware per-player settings GUI (language, click-through, combat display, effects, sit). |
| `shattered-crown` | `de.kreiscraft.modules.shatteredcrown` | three four-Anchor waves | `wither` | dragondebug | Population-scaled objectives at 100/65/30%, mounted/six-species/enhanced-boss rosters, permanent unlocks and terrain, quiet world cues, native victory/retry; [four-player RC/operator guide](/shattered-crown-release-candidate/). |
| `sign-color` | `de.kreiscraft.modules.signcolor` | stable | none | — | Renders legacy `&` codes, Kreiscraft placeholders and safe MiniMessage in sign text and anvil renames for players with the appropriate permission. |
| `sit` | `de.kreiscraft.modules.sit` | stable | none | sit | Toggle sitting through `/sit` or the cushion in `/settings`. |
| `spawn` | `de.kreiscraft.modules.spawn` | stable | none | spawn, setspawn | Manages the server spawn point; includes a spawn glide zone, optional swap-hand boost, and a persistent sneak glide boost. |
| `start-sequence` | `de.kreiscraft.modules.startsequence` | stable | none | start | Triggers the season-start sequence: countdown, then one continuous world border expansion. |
| `status` | `de.kreiscraft.modules.status` | stable | none | status | Stores per-player MiniMessage prefix/suffix and color, applied to chat, the Bukkit display name, the scoreboard/overhead name, and the tab list. |
| `tablist` | `de.kreiscraft.modules.tablist` | stable | none | — | Refreshes the tab list header and footer on a configurable tick interval using PlaceholderResolver. |
| `update-mode` | `de.kreiscraft.modules.updatemode` | stable | none | updatemode | Maintenance mode that kicks non-bypassed players on login. |
| `wither` | `de.kreiscraft.modules.wither` | stable | none | — | Enhances the Wither boss with custom health, damage, armour, independently configurable spawn, half-health, and death explosions, and minion spawning. |
| `world-mgmt` | `de.kreiscraft.modules.worldmgmt` | stable | none | world, portal, dimension | Commands for teleporting between worlds, computing Nether/Overworld portal coordinates, and enabling or disabling Nether/End portal travel. |

### Default-off modules

Most modules ship with `enabled: true` in the bundled `config.yml`. Three ship **disabled by default**
and only start when an operator explicitly sets `modules.<id>.enabled: true`:

| Module ID | Why it is off by default |
|-----------|--------------------------|
| `effects` | Cosmetic particle trails; opt in explicitly. |
| `firework` | Registers a custom crafting recipe that changes vanilla progression; opt in explicitly. |
| `positions` | Intentionally disabled by default — the waypoint GUI overlaps with other teleport tooling. |

Enable the flag and restart to activate these modules. Shattered Crown ships enabled and implements all three phases;
implementation is not live acceptance. See its [current scope and operator guide](/shattered-crown/).

---

## Per-Module Entries

## `dragon-egg`

**Package:** `de.kreiscraft.modules.dragonegg`

**Depends on:** none

**Default enabled:** true

Craft one compass with one dragon head (shapeless). Only marked tracking compasses are changed.
The first observed single egg item or placed egg becomes the gameplay egg; later eggs never replace it,
including after destruction. Direct inventory/cursor possession, moving dropped items, placed-block
teleportation, and falling-block placement/drop transitions retain its identity. Placed inventories,
the actual occupied half of double chests, and recursively nested shulkers/bundles also retain it
through placement, breaking, hopper transfers, cursor possession and dropped containers. Right-click reports
distance to the last sample and its age in seconds, or the target dimension when worlds differ.
Location is sampled every two minutes, not continuously; offline holders and unloaded chunks retain
their last known location without forcing a chunk load. Unknown custody or confirmed destruction
reports unavailable. Ender-chest insertion is rejected for **every** dragon egg, marked or not,
including nested containers, native clicks/shift-clicks/drags/hotbar/offhand swaps and online/offline
administrative ender-chest editing. Rejection keeps source and destination unchanged and informs
the actor; existing forbidden contents can still be removed. Buffs and offline reclamation are separate slices.

`DragonEggTracker` is the published identity/location seam for those slices; reuse its
`isTrackedEgg`, `containsTrackedEgg`, `claimContained`, `holdsTrackedEgg`, `observeInventory`,
custody transitions and `ping` rather than another tracker. Shared `ItemContents.any`/`editFirst`
traverse native item containers; edits write copied metadata back through all ancestors. Discovery
uses loaded-chunk lifecycle and inventory events, not periodic whole-world scans or forced loads.
After 24 elapsed **real hours**, including downtime, the offline carrier's egg (even inside
nested containers) is removed through `OfflinePlayerDataEditor.openForEdit` and returned in
barrel slot 13 at the logout block. An occupied block is never replaced: the nearest air block
inside the world border, within 16 blocks, is used; no available block means retry with ownership
retained. Returning before removal cancels the deadline. The barrel follows normal two-minute
compass sampling, not an immediate ping. Successful returns broadcast server-wide; the carrier
receives a deferred red login message with the actual barrel coordinates.
`DragonEggOfflineReturn` journals PREPARED → REMOVED → normal container custody. The journal
holds the exact serialized egg, chosen block, player-file digests and transaction token. Player
configuration/login reservations exclude live inventory loads from offline commits. Atomic,
fsynced Activity Data precedes removal; a fsynced player replacement precedes placement; Paper
chunk flushing, fsync and actual region-NBT read-back precede releasing journal custody. Pending
barrels cannot be opened, broken, burned, exploded or drained by hoppers. An ambiguous changed
player file or altered journal barrel fails closed, preserves the journal and blocks carrier login
until an administrator reconciles the evidence; do not delete the journal or restore a backup
blindly. Staged player writes and original backups remain available for diagnosis.
Broadcast recovery is **at-most-once**: a crash after the durable completion claim but before
chat delivery can omit the broadcast, never repeat it. Login notifications remain pending until
send succeeds (a crash before their acknowledgement may repeat the message). A hard crash while
the carrier is online uses the last durably saved online observation/location, rather than inventing
an exact crash timestamp; explicit logout and graceful shutdown record their real deadline.
Identity, custody, last known position and sampled position/time live in `data/dragon-egg.yml`
(Activity Data), auto-saved and flushed at shutdown; never edit these as Server Configuration.
Do not delete the data file to "repair" a destroyed egg: that would allow another egg to be claimed.

Runnable checks: `./gradlew test --tests '*DragonEgg*Test' --tests '*InvseeGuiTest'`.
Native inventory and region-file checks: `./gradlew testNms --tests '*NmsPlayerDataCodecTest'`.
Before live acceptance on Paper, craft and right-click in both hands/dimensions; move the egg before
and after two minutes; teleport it; let it fall onto a torch to become an item; pick up/drop/die;
unload/reload its chunk and restart with an offline holder; destroy it and verify another egg is not
selected. Repeat possession/placement/drop checks with nested shulkers and bundles; use hoppers and
double chests spanning a chunk boundary. Try ender-chest insertion with every input method and both
online/offline administrative editors, checking the cursor/source and unrelated contents after rejection.
Bukkit tests cover event and destination-update boundaries, not actual vanilla physics/event ordering.

Offline-return manual verification (disposable Paper world, never live data):
- Log out holding a marked egg directly, in a bundle, and inside shulker→bundle nesting; preserve
  containing items, sibling contents, armor/offhand, EnderItems and unrelated player tags.
- Advance only the saved deadline for a short test; restart with it overdue and no carrier online.
  Check exactly one egg in slot 13, one broadcast, red next-login message, and no early compass ping.
- Occupy the logout block; confirm the nearest safe fallback, unchanged occupied blocks and actual
  fallback coordinates in chat. Fill the search radius; confirm no removal/announcement until safe.
- Connect during configuration/removal; concurrently save an administrative offline inventory edit.
  Check either an untouched carrier inventory or a completed return, never both copies.
- Interrupt after journal preparation, after player replacement, during barrel save and after the
  completion claim; restart and count egg identity copies. Simulate player/YAML/region write failures
  and changed digests; verify retries or explicit fail-closed recovery, and protected pending barrels.
- Verify filesystem atomic rename/directory fsync support and Paper region read-back on the actual
  server filesystem. Disk/controller failures that violate fsync, external world/player restoration,
  other plugins overriding cancellations, and edits while this module is disabled are outside the
  transaction guarantee. Stop the server before manually reconciling a failed journal.

For command syntax and permissions, see [Commands and Permissions](/reference/commands-and-permissions/).
For config keys and defaults, see [Configuration](/reference/configuration/).

---

## `admin`

**Package:** `de.kreiscraft.modules.admin`
**Depends on:** none
**Default enabled:** true

### Description

Provides a collection of general-purpose admin utility commands: item and mob spawning, player-info lookups (IP, UUID, last-online, ping, statistics), server TPS display, sudo, and Kreiscraft help links. The module also blocks the vanilla `/seed` and `/minecraft:seed` commands: a player who runs either is told that `/seed` is disabled (`admin.seed-blocked`).

`/tps bar` toggles a personal 20-notch boss bar showing TPS, average milliseconds per tick (MSPT), and the viewer's ping. Each metric is independently green/yellow/red, fading to a darker shade across TPS/MSPT's decimal point and two decimal digits or all ping digits. The boss bar follows MSPT's color and fills from 0 ms (empty) to 50 ms (full) by default. Color thresholds and both fill endpoints are editable and hot-reloadable under **Admin** in Server Configuration. The bar refreshes every second using recipient-localized messages and is hidden on logout, permission loss, or module shutdown without clearing the saved choice. Visibility is stored per UUID in `data/settings.yml` and restored on join and for already-online players at module startup, provided they have `kreiscraft.admin.tps`. Missing preferences default to off. `/tps` still prints TPS in chat.

---

## `ai-chat`

**Package:** `de.kreiscraft.modules.aichat`
**Depends on:** none
**Default enabled:** true

### Description

Answers chat messages that contain the configured trigger string (default `@claude`). A player with
`kreiscraft.ai-chat.use` who sends a matching message causes the module to send the prompt, together
with the most recent `context-msg-count` chat lines recorded from `ChatBridge`, to the OpenRouter
chat-completions API on a background thread. Each chat line is formatted as
`[timestamp] [source] sender: content`. The system prompt is a configurable MiniMessage template
whose `<ai-trigger>`, `<always-supported-players>` and `<chat-history>` placeholders are expanded to
literal text before the request; the reply is rendered through the configured `response-format`
before it is broadcast to the server and published to `ChatBridge` as a line whose source is `AI`.
The module needs `modules.ai-chat.api-key` to be set; requests are sent with the configured `model`.

---

## `anti-cheat`

**Package:** `de.kreiscraft.modules.anticheat`
**Depends on:** none
**Default enabled:** true

### Description

Listens on a plugin-messaging channel (default `kreiscraft:mod_checker`) for reports from an external cheat-detection mod, logs them to `data/hackerlog.yml`, and can kick players who connect with a Fabric or Forge client before the handshake grace period expires (disabled by default via `kick-on-fabric-forge`). The `/hackerlogs <target> [cheats|xray]` command accepts a player selector (`@a`, `@p`, …), reports each selected player's entries with a localized kind label, and completes online plus known-offline profiles through the shared suggestion providers.

### Services published

- `HackLogService` — query stored hack-log entries by player UUID and kind.

---

## `api`

**Package:** `de.kreiscraft.modules.api`
**Depends on:** none
**Default enabled:** true

### Description

Starts a Javalin HTTP and WebSocket server. Exposes REST routes for player data, whitelist management, browser↔player account linking, and real-time metrics. WebSocket endpoints relay in-game chat and publish a read-only scene event stream to connected browser clients. Configuration parameters that affect Javalin internals (host, port, CORS origins, session cookie, heartbeat interval, HTTP async timeout) restart the server on reload; metrics refresh and verification timeouts require a full plugin restart.

Whitelist requests submitted by the website are reviewed in-game with
`/whitelist-requests` and `kreiscraft.api.whitelist-review` (default: op).
Reviewers receive approve/deny chat buttons for new submissions and a pending count
on joining. The paginated head menu uses left-click to approve and right-click to
deny, with approved/denied submenus for revising decisions. Disabling `api` also
disables request review and its notifications.

### Services published

- `VerificationService` — manages pending browser–Bukkit account link requests.

---

## `bedrock`

**Package:** `de.kreiscraft.modules.bedrock`
**Depends on:** none
**Default enabled:** true

### Description

Detects Bedrock Edition players by their username prefix (default `.`) when they connect and records
their name and UUID in `data/bedrock-players.yml`. Administrators pre-authorize a Bedrock name with
`/bedrocklist add <name>`, which creates a Pending Bedrock Authorization in
`data/bedrock-authorizations.yml`: the first matching login binds it to the observed UUID and
whitelists that identity, and a bound authorization never silently rebinds. Replacing a bound
identity requires `/bedrocklist remove <name>` followed by re-registration. `/bedrocklist list`
lists the known players in deterministic order.

### Services published

- `BedrockService` — query known Bedrock player records and Pending Bedrock Authorizations.

---

## `chat-format`

**Package:** `de.kreiscraft.modules.chatformat`
**Depends on:** `status`
**Default enabled:** true

### Description

Intercepts `AsyncChatEvent` and reformats each message using a MiniMessage template. The template has access to `<kc-prefix>`, `<player-prefix>`, `<name-colored>`, `<player-suffix>`, and `<body>`. Player affixes come from `StatusService`; visible affixes receive a separating space when missing and retain their formatting around the literal player name. Optionally allows players to use MiniMessage tags inside the chat body.

Messages that begin exactly with the Xaero waypoint payload token (`xaero-waypoint`, followed by the protocol's `:` separator) are passed through untouched: the event is left uncancelled so clients and the Xaero mod receive the raw payload, and no formatted message is broadcast or published to `ChatBridge`. Near-matches such as `xaero-waypoints` and ordinary chat are unaffected and formatted normally.

---

## `clickthrough`

**Package:** `de.kreiscraft.modules.clickthrough`
**Depends on:** none
**Default enabled:** true

### Description

Allows players with the clickthrough setting enabled to open containers and Ender Chests by right-clicking an adjacent wall sign (including wall hanging signs) or a filled item frame while not sneaking. Signs target the adjacent block opposite the clicked face; item frames use a ray-cast with a fixed maximum distance of six blocks. Empty frames and non-container targets are ignored. The per-player toggle is read from the always-available `PlayerPreferencesService`, falling back to the module default (enabled) when no preference is stored.

---

## `combat`

**Package:** `de.kreiscraft.modules.combat`
**Depends on:** none
**Default enabled:** true

### Description

Tags players in combat when they deal or receive damage, keeping them tagged for a configurable duration (default `5s`). Ender pearl impacts and damage are ignored and never tag a player. While tagged, elytra use can be blocked: a continuously swept check removes a worn elytra, returning it to the inventory when space exists or otherwise dropping it glowing and pickup-protected for its owner for a bounded window. Worlds listed in `excluded-worlds` are exempt. Combat status is displayed via the per-player preference read from the always-available `PlayerPreferencesService` (action bar, chat, or title); when a player has no stored preference the combat module's `default-display` applies. A fresh tag renders its full duration immediately and then counts down exactly once per second, driven by its own scheduler task rather than a shared sweep. The `/combat <target> <seconds>` command accepts a player selector (`@a`, `@p`, …) and tags every selected player.

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

Spawns a configurable particle effect around a player on a configurable tick schedule (default `5t`) while they have effects enabled and have selected a particle; emission is independent of movement. A new player emits nothing until choosing a particle with `/effects <particle>`, which enables the trail immediately. Enabled state and particle are stored as player preferences in `data/settings.yml`, so existing choices survive reconnects.

### Configuration

| Key (relative to `modules.effects`) | Type | Default | Description |
|---|---|---|---|
| `default-particle` | Particle | `HAPPY_VILLAGER` | Particle shown by controls until the player chooses one; it is not emitted automatically. |
| `schedule` | Duration | `5t` | Emission period; hot-reloaded. |
| `count` / `offset-x` / `offset-y` / `offset-z` / `force` | — | — | Emission shape and visibility, as in the Configuration reference. |

### Services published

- `EffectsService` — report, toggle, and select particle effects for players, backed by persisted preferences.

---

## `firework`

**Package:** `de.kreiscraft.modules.firework`
**Depends on:** none
**Default enabled:** false — opt in with `modules.firework.enabled: true`

### Description

Registers a Bukkit shaped crafting recipe that yields a firework rocket with the configured `customModelData`. The recipe is unregistered on module disable. No commands, listeners, or services are provided.

---

## `invsee`

**Package:** `de.kreiscraft.modules.invsee`
**Depends on:** none
**Default enabled:** true

### Description

Opens a GUI showing another player's inventory or ender chest. The single `kreiscraft.invsee.use` permission authorizes viewing and editing: a viewer who passes it may modify the target's live inventory directly. GUI titles are rendered through the message service so they are localized per viewer and follow the current message styling boundary. Online targets are edited live. Offline targets are opened as an editable snapshot through the version-isolated `OfflinePlayerDataEditor` boundary (`OfflinePlayerDataStore` over a `PlayerDataCodec`): the inventory uses a five-row layout with helmet, chestplate, leggings, boots, and offhand in the top row, main inventory in the middle, and the hotbar on the bottom, with immovable, unnamed gray stained-glass panes in positions that have no inventory slot; the Ender Chest is a three-row snapshot. Edits are held in a detached inventory and committed with a two-phase protocol (ADR-17): the edited items are encoded to compressed NBT preserving every unrelated tag, written and read back without touching the source, then — in one server-main-thread turn — the target's offline state and the source digest are re-checked, a backup is created, and the source is atomically replaced. A concurrent login aborts the write and offers a clickable reopen of the live inventory; a stale source or write failure aborts without modifying the file.

---

## `jail`

**Package:** `de.kreiscraft.modules.jail`
**Depends on:** none
**Default enabled:** true

### Description

`/jail` and its alias `/kerker` are identical toggles: they jail an unjailed player (teleporting them to the jail `location` and recording an entry in `data/jail.yml`) or release an already-jailed player (teleporting them back to their original location). The `<target>` argument completes to online player names. A freshly jailed player also receives a localized direct message whose reason is styled distinctly from the surrounding prose; when no reason is supplied, the message shows the localized `jail.no-reason` placeholder instead of an empty string. While jailed, a configurable set of commands is blocked; `/jail`, `/kerker`, and `/kreiscrafthelp` are always allowed. Players with `kreiscraft.jail.bypass` cannot be jailed, but the bypass permission does not prevent releasing an already-jailed player.

`/setjail` stores the executing player's current location as `modules.jail.location` in `config.yml` through the validated, atomic Server Configuration edit boundary. The jail location is operator-owned Server Configuration; jail entries and their restore locations remain Activity Data in `data/jail.yml`.

### Services published

- `JailService` — jail, release, query jail state, and read/write the operator-owned jail location for players.

---

## `positions`

**Package:** `de.kreiscraft.modules.positions`
**Depends on:** none
**Default enabled:** false — intentionally disabled by default; opt in with `modules.positions.enabled: true`

### Description

Allows each player to save named waypoints with `/positions` (`/pos`) and inspect them in a GUI. Clicking a position can optionally teleport players with `kreiscraft.positions.teleport`. Positions are persisted per-player in `data/positions.yml`.

### Services published

- `PositionsService` — CRUD for per-player named waypoints.

---

## `reboot`

**Package:** `de.kreiscraft.modules.reboot`
**Depends on:** none
**Default enabled:** true

### Description

Schedules a server restart with a countdown. The start announcement is sent per recipient so each player sees the reason — or the localized `reboot.no-reason` placeholder when none was given — in their own language. At seconds listed in `title-seconds` a title (with the reason as subtitle) is shown to all players; at seconds listed in `chat-seconds` a chat message is broadcast. When the countdown reaches zero, all players are kicked with a message containing the reason, initiator, and website, and the server is stopped via `PaperPlatform`. The command is registered as `/reboot` with alias `/restart`.

### Services published

- `RebootService` — start, cancel, and query the restart countdown.

---

## `settings-gui`

**Package:** `de.kreiscraft.modules.settingsgui`
**Depends on:** none
**Default enabled:** true

### Description

Opens an InvUI-backed Player Settings GUI for the invoking player. It exposes language, click-through, combat display, and effects (enabled state and particle) preferences, plus a Sit cushion that closes the menu and runs `/sit`. The cushion requires `kreiscraft.sit.use`. Every control is module-aware: a control is shown only when the module that consumes it is enabled, so a player never sees a setting for a feature the server does not run; language belongs to the always-available preferences boundary and is always present. Visible feature controls retain the order click-through, combat display, effects, sit. One control uses slot 13; two use 11/15; three use 10/13/16; four use 10/12/14/16. Language stays at slot 26 and the authorized Server Config entry stays at slot 8, neither counting toward the feature layout. Effects has its own page. Click-through's localized lore explains its supported obstacles and the sneaking exception.

The GUI owns no persistence of its own: it reads and writes the always-available `PlayerPreferencesService`, which stores preferences in `data/settings.yml`. This means combat and click-through keep working from stored preferences even when `settings-gui` is disabled. Each click is applied to the preferences boundary immediately; there is no time-based debounce, because InvUI dispatches one click to one item, so one physical click produces one logical mutation.

Click-through lore uses one `settings-gui.click-through-lore` translation entry. A YAML literal block (`|-`) renders one lore line per line of text, preserving blank lines and inherited MiniMessage formatting.

#### Server Configuration editor

Players holding `kreiscraft.settingsgui.admin` also see a **Server Configuration** entry on the Player Settings root, and can open it directly with `/settings admin`. Unauthorized players never see the entry and cannot reach the subcommand.

The editor gives every configured module its own section and representative icon; a section whose module is currently disabled shows a localized `| disabled` suffix on its entry. Related settings occupy one row per group; Wither spawn, half-health, and death controls use separate rows. Each module's enable toggle is a glass pane in the far-left slot of the bottom row with restart-required lore. Other controls remain typed: booleans toggle, whole and decimal numbers step, durations/enums/materials cycle, a location is set to the editor's current position, and free-form text, text lists, and integer lists are entered through an anvil editor (integer-list entries must be whole numbers). Sections have forward/back navigation; a category with more than 52 fields is split across pages with previous/next arrows.

Every edit goes through the validated, atomic `ConfigService` boundary, so a value is validated and durably written the moment it is clicked; a rejected or failed edit changes neither the running configuration nor `config.yml`. The editor is closed with a session-specific report: hot-reloadable edits offer the applicable clickable `/kc reload` action, and restart-only edits (always including every module enable flag) warn that a server restart is required. Module lifecycle changes are never described as hot-applied. Low-level, sensitive, networking, and tab-list header/footer values stay YAML-only.

### Services published

None. The GUI is a client of the shared `PlayerPreferencesService` and the validated `ConfigService`.

---

## `sign-color`

**Package:** `de.kreiscraft.modules.signcolor`
**Depends on:** none
**Default enabled:** true

### Description

Parses MiniMessage tags, legacy ampersand colour/decoration codes, and established Kreiscraft `%placeholder%` tokens in sign lines when a player with `kreiscraft.signcolor.use` edits a sign, and in anvil rename fields when the player has `kreiscraft.signcolor.anvil`. MiniMessage is restricted to colour, decoration, rainbow, reset, and gradient tags so players cannot inject interactive or client-affecting tags. No commands are provided.

---

## `sit`

**Package:** `de.kreiscraft.modules.sit`

**Depends on:** none

`/sit` toggles sitting on an invisible, temporary seat. Sneaking also stands up. The white cushion
in `/settings` closes the menu and runs the same command. Both require
`kreiscraft.sit.use` (default: true); the cushion is hidden when the module is disabled or
the player lacks permission. Seats are removed on dismount, quit, death, teleport, and module
disable. Sitting is not persisted and does not change combat rules. Existing vehicles are left alone.

Enable or disable with `modules.sit.enabled` (default: true; restart required).

---

## `spawn`

**Package:** `de.kreiscraft.modules.spawn`
**Depends on:** none
**Default enabled:** true

### Description

Manages the server spawn point and the single Spawn Radius (module configuration in `config.yml`). `/spawn` is overworld-only and restricted to the horizontal Spawn Access Zone; glide initiation requires the full three-dimensional Glide Zone. Includes three optional subsystems: elytra glide around spawn, a swap-hand velocity boost while gliding, and a persistent sneak boost for glides that started inside the Glide Zone. Spawn-launch behavior has been removed.

### Services published

- `SpawnService` — get/set the spawn location and check the Spawn Access Zone (XZ) and the Glide Zone (3D).

---

## `start-sequence`

**Package:** `de.kreiscraft.modules.startsequence`
**Depends on:** none
**Default enabled:** true

### Description

Triggers the season-start ceremony: a countdown (`countdown` down through `1`, each with a title and sound), followed by the start title and exactly one continuous world-border expansion. The border duration is configured in seconds, represented internally as a `Duration`, and converted to ticks only at Bukkit's `WorldBorder` boundary. The plugin delegates the in-progress transition to Minecraft and keeps no phase, timestamp, or remaining-duration resume state, so a graceful restart resumes the expansion. Completion is stored in operator-owned Server Configuration (`modules.start-sequence.has-started` in `config.yml`) so a restart does not re-trigger the sequence.

### Services published

- `StartSequenceService` — trigger and reset the start sequence; query whether it has started.

---

## `status`

**Package:** `de.kreiscraft.modules.status`
**Depends on:** none
**Default enabled:** true

### Description

Stores a MiniMessage prefix, suffix, and ARGB color for each player, persisted in `data/statuses.yml`. Prefix, suffix, and color are applied to every online viewer's scoreboard team (the overhead/nametag presentation); the prefix and suffix are also applied to the Bukkit display name and the tab-list player name. A `StatusChangedEvent` is broadcast so other modules (`chat-format`, `tablist`) can react. On join and on every status change, each online viewer's scoreboard is rebuilt so all viewers stay synchronized, and the player's display/tab-list names are refreshed.

Chat and the Tab player list parse the concatenated MiniMessage prefix, literal player name, and suffix without inserting a color or reset. Open prefix tags continue onto the name; closing tags and resets retain their normal meaning. A separating space is added after a visible prefix and before a visible suffix when missing; empty affixes add no spaces. Overhead nametags use the stored Minecraft team color.

Join and leave announcements use the reloadable `modules.status.join-format` and `leave-format` MiniMessage templates, defaulting to `<gray>[<green>+<gray>]<reset> <player-prefix><name-colored><player-suffix>` and `<gray>[<red>-<gray>]<reset> <player-prefix><name-colored><player-suffix>` for leaving. Already-suppressed announcements remain suppressed.

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

When active, activation immediately disconnects every online player who is not allowed to bypass, and login attempts from non-bypassed players are blocked. A player bypasses if their name is listed in `bypass-names`, they are an operator, or they hold the configured `bypass-permission`. Bypassed players who are online when maintenance is activated receive a notification instead of being disconnected. The active/inactive state is operator-owned Server Configuration (`modules.update-mode.active` in `config.yml`).

### Services published

- `UpdateModeService` — activate, deactivate, and query the maintenance-mode state.

---

## `wither`

**Package:** `de.kreiscraft.modules.wither`
**Depends on:** none
**Default enabled:** true

### Description

Enhances the vanilla Wither boss. On spawn its health, attack damage, and armour attributes are overridden. The Wither's vanilla post-charge spawn blast is intercepted through `ExplosionPrimeEvent`, cancelled, and synchronously replaced once with the configured `spawn-explosion-*` power, fire, and block-damage settings. When it reaches `half-health-threshold` it spawns `minion-count` Wither Skeletons and triggers a `half-health-explosion-*` explosion. On death it creates a `death-explosion-*` explosion and displays a boss-bar effect to players within `death-boss-bar-radius` blocks. Each of the three phases independently honors its own power, fire, and block-damage fields, including after a configuration reload.

---

## `world-mgmt`

**Package:** `de.kreiscraft.modules.worldmgmt`
**Depends on:** none
**Default enabled:** true

### Description

Provides three world-management commands: teleport to any loaded world's spawn, compute Overworld ↔ Nether portal coordinate translations (÷8 / ×8), and enable or disable portal travel into or out of the Nether and End. `/dimension` reports both dimensions, `/dimension <nether|end>` reports one, and `/dimension <nether|end> enable|disable` toggles access. The `allow-nether` and `allow-end` flags are also editable under **World Management** in the Server Configuration GUI and hot-reload immediately. Blocking a dimension cancels portal travel into or out of it; players already inside are not moved, and non-portal teleports such as `/world` remain available.

### Services published

None.
