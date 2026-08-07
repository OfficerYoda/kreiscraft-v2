# Kreiscraft

Combined Paper 26.x plugin: gameplay + admin utilities + web API bridge.

## What's inside

- Module-based architecture (`core/module`) — every feature is opt-in via `config.yml`.
- Paper Brigadier commands + Adventure MiniMessage everywhere.
- YAML configuration (Configurate) + separate `data/` files for runtime state.
- Test coverage on all business logic via JUnit 6, MockBukkit, and Mockito.
- Javalin HTTP + WebSocket API for web frontends (see [API endpoints](#api-endpoints) below).

## Build

```bash
./gradlew build test         # compile + Spotless check + tests
./gradlew test               # tests only
./gradlew spotlessApply      # auto-format all Java sources
./gradlew runServer          # spin up a local Paper server with the plugin loaded
```

## Repository layout

```
src/main/java/de/kreiscraft/
├── Kreiscraft.java              JavaPlugin entrypoint
├── KreiscraftBootstrap.java     wires services and modules
├── ReloadCommand.java           /kreiscraft reload
└── core/                        the platform (module system, config, messages, scheduler, events)
```

Feature modules live under `modules/`.

## Modules

25 opt-in modules (all enabled by default; disable any in `config.yml`).
Full descriptions, config keys, and permission nodes: [`docs/modules.md`](docs/modules.md).

| Module ID | Summary |
|---|---|
| `admin` | 14 small operator commands (item, teleport, weather, spawnmob, gamemode, ip lookup, statistic query, sudo, and more); see [`docs/modules.md`](docs/modules.md) for the full list |
| `anti-cheat` | Client-brand-based cheat detection with hacker log storage and `/hackerlogs` command |
| `api` | Javalin HTTP + WebSocket bridge serving player/status data and bidirectional chat to web frontends |
| `bedrock` | Bedrock player tracking, `/bedrockList` command, and Java-only whitelist enforcement |
| `bot` | Bot-account detection and data persistence |
| `chat-format` | Custom in-game chat formatting via MiniMessage; bridges formatted messages to the shared `ChatBridge` |
| `clickthrough` | Per-player toggle to click through grass and similar blocks; integrates with `settings-gui` |
| `combat` | Combat tag and PvP protection rules with per-player toggle via `settings-gui` |
| `effects` | `/effects` command for players to apply and remove their own potion effects |
| `firework` | Automatic firework launches on configurable server events |
| `glow` | `/glow` command to toggle a player's glowing outline |
| `invsee` | `/invsee` and `/enderchest` (`/ec`) commands to inspect other players' inventories |
| `jail` | `/jail` and `/unjail` commands with configurable jail location and timed sentences |
| `plugin-hider` | Intercepts `/plugins` and `/version` to hide the installed plugin list |
| `positions` | Named waypoint system with InvUI GUI; `/pos` command and persistent per-player storage |
| `reboot` | Countdown + broadcast reboot with `/reboot` command and configurable countdown broadcast (default-countdown, title-seconds, chat-seconds) |
| `settings-gui` | Per-player settings GUI opened with `/settings`; publishes `SettingsService` for other modules |
| `sign-color` | MiniMessage colour codes in sign text and anvil rename fields |
| `spawn` | Server spawn management: `/setspawn`, `/spawn`, glide zone, swap-hand boost, and launch pad |
| `start-sequence` | Startup animation sequence displayed to players joining a freshly-started server |
| `status` | Server status service (online players, TPS) with `/status` command; consumed by `chat-format` and `api` |
| `tablist` | Custom tab-list header/footer with configurable MiniMessage templates and placeholder support |
| `update-mode` | Maintenance mode: locks the server to whitelisted operators via `/updatemode` |
| `wither` | Configurable Wither spawn behaviour and per-world restrictions |
| `world-mgmt` | `/world`, `/portal`, and `/setworldspawn` commands for multi-world navigation |

## API endpoints

The `api` module starts a Javalin server on the configured host/port (default `0.0.0.0:8080`).

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/status` | Server metrics | None |
| GET | `/players` | Online player snapshots | None |
| GET | `/initialize` | Get/create browser session UUID cookie | None |
| POST | `/link-player` | Request Bukkit account link | Session cookie |
| GET | `/linked-player` | Query link state | Session cookie |
| POST | `/unlink-player` | Remove link | Session cookie |
| GET | `/whitelist` | Read server whitelist | `X-Kreiscraft-Api-Key` |
| POST | `/whitelist` | Add player to whitelist | `X-Kreiscraft-Api-Key` |
| WS | `/chat` | Bidirectional chat + verification events | None |

> **Note:** The `/chat` WebSocket accepts unauthenticated connections in Plan 4; per-message
> actions that write to the game (`CHAT` type) are routed through in-game chat which is subject
> to normal server permissions. Enforcing session cookies on WS connect is Plan 5+ territory.

## Configuration

All config keys, defaults, and hot-reload behaviour: [`docs/configuration.md`](docs/configuration.md).

## Permissions

Full permission tree with defaults and inheritance: [`docs/permissions.md`](docs/permissions.md).

## Migrating from legacy plugins

Replacing the old `kreiscraft-api` or other standalone plugins with this monolith:
[`docs/migration-from-legacy.md`](docs/migration-from-legacy.md).

## Contributing

Conventional Commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`).
Install the pre-commit hook once: `./gradlew installGitHooks`.

See `docs/development.md` for architecture details and module authoring guide.
