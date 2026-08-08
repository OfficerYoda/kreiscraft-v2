# Modules

This document covers every feature module shipped with Kreiscraft-Java.
For per-module configuration tunables see [`configuration.md`](configuration.md).
For the permission nodes each module registers see [`permissions.md`](permissions.md).
Every module can be enabled or disabled independently with `modules.<id>.enabled: true|false` in `config.yml`; a full server restart is required for the change to take effect.

---

## admin

The `admin` module bundles 14 small operator commands — `/kreiscraftgamemode`, `/item`, `/skull`, `/head`, `/kreiscraftweather`, `/spawnmob`, `/ip`, `/uuid`, `/lastonline`, `/ping`, `/tps`, `/statistic`, `/sudo`, and `/khelp` — that have no config or listeners of their own and are too small to warrant individual modules. Toggle with `modules.admin.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `PlaceholderResolver` (used by `/tps` to inject spark data). The `/tps` command delegates to spark when spark is present on the server, falling back to a built-in TPS estimate otherwise.

## anti-cheat

The `anti-cheat` module listens on the `kreiscraft:mod_checker` plugin-message channel (channel name is configurable), parses client-mod handshakes into `HackEntry(hack, player, Instant)` records, persists them to `data/hackerlog.yml`, and kicks offending players. Toggle with `modules.anti-cheat.enabled` in `config.yml`; restart required. **Publishes:** `HackLogService`. **Consumes:** `ConfigService`, `PlayerBrandProvider`. The channel is registered and unregistered cleanly on module enable/disable, and the live config is held in an `AtomicReference` so `/kreload` propagates without restarting the listener.

## api

The `api` module runs a Javalin HTTP server and WebSocket endpoint on the configured host and port, exposing 8 REST routes plus a `/chat` WebSocket for the in-game ↔ web chat bridge; all previously hardcoded values (port, CORS origins, cookie name, cookie max-age, heartbeat interval) are now configurable. Toggle with `modules.api.enabled` in `config.yml`; restart required. **Publishes:** `VerificationService`. **Consumes:** `ChatBridge`, `WhitelistService`, `Gui`. The `VerificationService` is backed by a `ConcurrentHashMap` to eliminate the data race present in the original `kreiscraft-api`; the Javalin `ScheduledExecutorService` is shut down on `disable()`, and any Bukkit API call originating from an HTTP or WebSocket thread is dispatched through `Scheduler.runOnMain`.

## bedrock

The `bedrock` module detects Bedrock Edition players by their Geyser `.`-prefixed names on login, tracks them in `data/bedrock-players.yml`, and provides `/bedrocklist add|remove|list` to manage the whitelist. Toggle with `modules.bedrock.enabled` in `config.yml`; restart required. **Publishes:** `BedrockService`. **Consumes:** `ConfigService`, `WhitelistService`. The NMS whitelist interaction is isolated behind the `platform/NmsWhitelist` adapter, keeping module code free of server-version dependencies.

## bot

The `bot` module is a data-only placeholder: it loads and persists `data/bot.yml` (matching the shape of the legacy bot config) but registers no commands or listeners. Toggle with `modules.bot.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `ConfigService` (registers the data file for auto-save on `/kreload`). No bot logic is implemented; this module exists to ensure the data file survives reloads while full bot functionality is planned for the future.

## chat-format

The `chat-format` module replaces vanilla chat with a MiniMessage-formatted broadcast (`<team-prefix> <name><team-suffix>: <message>`), cancels the vanilla `AsyncChatEvent`, and forwards the formatted line through `ChatBridge` so the `api` module receives it without scraping a cancelled event. Toggle with `modules.chat-format.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `StatusService`, `PaperPlatform`, `ChatBridge`. This module declares a hard dependency on `status` (must be enabled first); the event priority is read from config but runtime priority change is deferred — see the "Deferred features" section below.

## clickthrough

The `clickthrough` module intercepts right-click events on signs and item frames and routes the interaction to any attached container (chest, barrel, etc.), giving players direct access without breaking the sign or frame. Toggle with `modules.clickthrough.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `SettingsService`. The feature is per-player: each player can enable or disable it via the settings GUI, and the preference is read from `SettingsService` on every interaction; this module declares a hard dependency on `settings-gui`.

## combat

The `combat` module implements a PvP tag lifecycle: `CombatService.tag(player, duration)` is called on any PvP damage event, tagging both attacker and victim with a per-player countdown `Scheduler.Task` that is automatically cancelled on quit, death, or expiry. Toggle with `modules.combat.enabled` in `config.yml`; restart required. **Publishes:** `CombatService`. **Consumes:** `SettingsService`, `PaperPlatform`. While a player is tagged, elytra flight is denied (configurable) and a combat-remaining-time indicator is shown in the display position the player chose via `SettingsService`; this module declares a hard dependency on `settings-gui`.

## effects

The `effects` module lets players toggle and configure a particle trail that follows them while they move. Toggle with `modules.effects.enabled` in `config.yml`; restart required. **Publishes:** `EffectsService`. **Consumes:** None. Particle preferences are stored in-memory and cleaned up on player quit via `EffectsCleanupListener`; the config (particle type list, etc.) is hot-reloadable via `onConfigReload`.

## firework

The `firework` module registers a custom shaped firework recipe on module enable using a `NamespacedKey` derived from the config field `namespace-key`. Toggle with `modules.firework.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `PaperPlatform`. The recipe is unregistered from the server on module `disable()`, so toggling the module cleanly removes the recipe without a restart cycle.

## glow

The `glow` module provides a `/glow` command that toggles the glowing effect (visible outline through walls) for the executing player, managed via scoreboard teams. Toggle with `modules.glow.enabled` in `config.yml`; restart required. **Publishes:** `GlowService`. **Consumes:** `PaperPlatform`. On module `disable()`, `GlowService.disableAll()` is called to strip the glow effect from all currently online players before the scoreboard teams are removed.

## invsee

The `invsee` module provides `/invsee <player>` to open a live view of another player's inventory and `/enderchest <player>` (aliases: `ec`, `echest`) to view their ender chest; for offline players the inventory is read from the `.dat` NBT file via the `OfflineInventoryReader` platform adapter. Toggle with `modules.invsee.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `Gui`, `OfflineInventoryReader`. Offline ender chest reading is not yet implemented — `/enderchest <offlinePlayer>` returns "not supported"; see "Deferred features" below.

## jail

The `jail` module confines a player to the location defined by `modules.jail.location` in `config.yml`, blocking all commands except those on the configurable allowlist and denying block interactions, and restores their original location on unjail; state is persisted in `data/jail.yml`. Toggle with `modules.jail.enabled` in `config.yml`; restart required. **Publishes:** `JailService`. **Consumes:** `ConfigService`. The jail coordinates are now fully config-driven (replacing the former hardcoded values), and admin bypass is controlled by the `kreiscraft.jail.bypass` permission node rather than a hardcoded operator name list.

## plugin-hider

The `plugin-hider` module intercepts `PlayerCommandSendEvent` to filter tab-completions down to an allowlist, preventing players from discovering installed plugins via tab-complete. Toggle with `modules.plugin-hider.enabled` in `config.yml`; restart required. **Publishes:** `PluginHiderService`. **Consumes:** `ConfigService`. The default allowlist is defined in `config.yml` and seeded into `data/plugin-hider.yml` on first run; runtime mutations via `/pluginhider add|remove|list` are persisted to the data file, keeping config and runtime state separate.

## positions

The `positions` module provides personal named waypoints: players store up to the configured maximum number of locations with `/pos set <name>`, teleport to them with `/pos tp <name>`, list them in an InvUI-backed GUI, and delete them with `/pos delete <name>`; all data is persisted per-player in a YAML data file. Toggle with `modules.positions.enabled` in `config.yml`; restart required. **Publishes:** `PositionsService`. **Consumes:** `ConfigService`, `Gui`. The GUI uses the `KreiscraftHolder`-based routing from the shared `Gui` facade rather than manual inventory-title string matching.

## reboot

The `reboot` module implements a countdown-based server restart: `/reboot [seconds] [reason]` starts a configurable-interval countdown broadcast via titles and action bars, then calls `Bukkit.shutdown()`; the countdown is cancelable and blocks direct use of `/stop` and `/restart` while active. Toggle with `modules.reboot.enabled` in `config.yml`; restart required. **Publishes:** `RebootService`. **Consumes:** `PaperPlatform`. The countdown is implemented as a cancellable `Scheduler.Task`, making it unit-testable without real server timing.

## settings-gui

The `settings-gui` module provides a per-player InvUI-backed chest GUI (opened with `/settings`) where players configure their click-through-signs toggle and combat-message display position; preferences are persisted in `data/settings.yml`. Toggle with `modules.settings-gui.enabled` in `config.yml`; restart required. **Publishes:** `SettingsService`. **Consumes:** `ConfigService`, `Gui`. The GUI uses a proper InvUI `Gui` facade — there is no longer a shared static `InventoryBuilder.inventory` field, eliminating the inventory-state corruption present in the legacy implementation.

## sign-color

The `sign-color` module registers two event listeners: `SignEditListener` translates MiniMessage markup entered on signs, and `AnvilRenameListener` translates MiniMessage markup in anvil rename fields, allowing colored and styled text on signs and renamed items. Toggle with `modules.sign-color.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** None. The sign-edit listener was rewritten from scratch (the original was decompiled code); the anvil listener is a port of the legacy `AnvilColor` class.

## spawn

The `spawn` module manages the server spawn point (persisted in `data/spawn.yml` via `/setspawn`) and provides `/spawn` for teleportation within the configured `teleport-range`; three opt-in sub-features are independently toggleable: a glide zone granting flight near spawn, a swap-hand boost mechanic, and a spawn-launch pad on first move. Toggle with `modules.spawn.enabled` in `config.yml`; restart required. **Publishes:** `SpawnService`. **Consumes:** `ConfigService`. Each sub-feature registers its listener only when its own `enabled` flag is `true` in config, so disabling a sub-feature at startup avoids any listener overhead.

## start-sequence

The `start-sequence` module handles the season-start world-border expansion sequence, triggered once via `/start`; a `has-started` flag is persisted in `data/start-sequence.yml` so the command cannot be executed a second time on the same season. Toggle with `modules.start-sequence.enabled` in `config.yml`; restart required. **Publishes:** `StartSequenceService`. **Consumes:** `ConfigService`, `PaperPlatform`. The expansion sequence is implemented as a cancellable `Scheduler.Task` that shuts down cleanly on module `disable()`.

## status

The `status` module manages per-player prefix, suffix, and color, persisted in `data/statuses.yml`, exposed via `/status set prefix|suffix|color …`, and applied as a scoreboard team entry on player login. Toggle with `modules.status.enabled` in `config.yml`; restart required. **Publishes:** `StatusService`. **Consumes:** `ConfigService`. Text is stored and rendered using Adventure's `Component` model instead of the legacy `ChatColor.translateAlternateColorCodes`, and `StatusChangedEvent` is fired on every mutation for other modules to react to.

## tablist

The `tablist` module periodically updates every online player's tab-list header and footer using configurable MiniMessage templates with placeholder resolution via `PlaceholderResolver`; the update interval is configurable (default 20 ticks). Toggle with `modules.tablist.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `PaperPlatform`, `PlaceholderResolver`. A single shared `Scheduler.Task` drives all updates (replacing the legacy one-task-per-player approach); templates are hot-reloadable via `onConfigReload`, but changing `update-interval` at runtime logs a warning and requires a restart to take effect.

## update-mode

The `update-mode` module implements a maintenance-mode login gate: when active, `AsyncPlayerPreLoginEvent` kicks any player who lacks the `kreiscraft.updatemode.bypass` permission, while operators toggle it with `/updatemode on|off`; the active state is persisted in `data/update-mode.yml`. Toggle with `modules.update-mode.enabled` in `config.yml`; restart required. **Publishes:** `UpdateModeService`. **Consumes:** `PaperPlatform`, `ConfigService`. The legacy op-check bypass has been replaced with the `kreiscraft.updatemode.bypass` permission node for cleaner operator management.

## wither

The `wither` module enhances the wither boss fight with configurable mechanics (damage modifiers, spawn effects, etc.) via an event listener registered on module enable. Toggle with `modules.wither.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** None. The config is held in an `AtomicReference` so hot-reloads propagate to the listener without restart; the listener's internal `Scheduler.Task` (if any) is shut down cleanly in `WitherListener.shutdown()` called from module `disable()`.

## world-mgmt

The `world-mgmt` module provides three operator commands: `/world <name>` to teleport to another world's spawn, `/setworldspawn` (alias `sws`) to set the current world's spawn at the player's position, and `/portal` to compute nether-to-overworld (and reverse) coordinate translations at the standard 1:8 scale ratio. Toggle with `modules.world-mgmt.enabled` in `config.yml`; restart required. **Publishes:** None. **Consumes:** `PaperPlatform`. There are no listeners or data files; all three commands are stateless operations delegated to the Paper server API.

---

## Deferred features

**Offline ender chest** — `/enderchest <offlinePlayer>` currently returns "not supported" because reading an offline player's ender chest requires `OfflineInventoryReader.readEnderChest(UUID)` in `platform/`, which is not yet implemented. This is not scheduled; add it if user demand appears.

**Chat-format runtime event priority** — The config key `modules.chat-format.event-priority` is read during module enable but is not applied to the event listener registration because `EventBus.register(Listener, EventPriority)` overload does not exist. Fixing this requires adding that overload to the `EventBus` abstraction. Deferred to a future release.
