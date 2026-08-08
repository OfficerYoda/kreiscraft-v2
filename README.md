# Kreiscraft

A combined Paper 26.x plugin for the kreiscraft.de Minecraft server — a single JAR that replaces several standalone plugins with 25 opt-in feature modules and an embedded HTTP + WebSocket API for the web frontend.

## Highlights

- **Runtime module system.** 25 opt-in feature modules toggled from `config.yml`. Startup uses topological sorting on declared dependencies.
- **Modern Paper APIs.** Brigadier commands via `LifecycleEvents.COMMANDS`, Adventure + MiniMessage everywhere, no legacy `CommandExecutor` / `§`-code strings.
- **Clean lifecycle.** Every module registers through a `ModuleContext` dependency bag; teardown is tracked so `disable()` cleans up listeners, commands, scheduled tasks, and GUIs.
- **Config vs. state.** Admin-managed settings live in `config.yml`; runtime state lives in dirty-tracked `DataFile<T>` YAMLs under `data/` with periodic auto-save.
- **Embedded web bridge.** The `api` module runs Javalin 6 (shadow-relocated) with 8 REST endpoints, a `/chat` WebSocket, and an in-game GUI account-linking flow.
- **Tested.** JUnit Jupiter 6, MockBukkit, Mockito, AssertJ, and Javalin TestTools cover core logic, listeners, and API routes. `LangParityTest` and `MessageKeyReferenceTest` prevent i18n drift.

## Quick start

```bash
./gradlew installGitHooks    # once — Spotless pre-commit hook
./gradlew build              # compile + Spotless check + shadowJar + tests
./gradlew runServer          # spin up a local Paper server with the plugin loaded
./gradlew test               # tests only
./gradlew spotlessApply      # auto-format all Java sources
```

Requires **Java 25**. The `runServer` task boots a local Paper instance in `run/`; join at `localhost:25565` after startup.

## Documentation

Full documentation is organised by the [Diátaxis](https://diataxis.fr/) framework in [`docs/`](docs/README.md).

- **Tutorials** — [Getting Started](docs/tutorials/getting-started.md) · [Your First Module](docs/tutorials/your-first-module.md) · [Writing a Command](docs/tutorials/writing-a-command.md)
- **How-to guides** — [Add a Module](docs/how-to/add-a-new-module.md) · [Persist State](docs/how-to/persist-module-state.md) · [Reload Config](docs/how-to/reload-configuration.md) · [Write Tests](docs/how-to/write-tests.md) · [and more…](docs/README.md#how-to-guides--solving-specific-problems)
- **Reference** — [Module Catalog](docs/reference/module-catalog.md) · [Commands & Permissions](docs/reference/commands-and-permissions.md) · [Configuration](docs/reference/configuration.md) · [HTTP API](docs/reference/http-api.md) · [SDK](docs/reference/sdk.md) · [Module Template](docs/reference/module-template.md)
- **Explanation** — [Architecture](docs/explanation/architecture.md) · [Design Decisions (ADRs)](docs/explanation/design-decisions.md)

## Repository layout

```
src/main/java/de/kreiscraft/
├── Kreiscraft.java              JavaPlugin entrypoint
├── KreiscraftBootstrap.java     composition root — wires services + modules
├── ReloadCommand.java           /kreiscraft reload
├── core/                        platform: module system, config, messages,
│                                scheduler, events, commands, services
├── platform/                    Paper-specific adapters
├── shared/                      cross-module services (ChatBridge, GUI, user, whitelist)
└── modules/                     the 25 feature modules
```

- `src/main/resources/config.yml` — default config template
- `src/main/resources/lang/{de,en}.yml` — MiniMessage lang files (parity enforced by tests)
- `src/main/resources/plugin.yml` — command and permission declarations
- `scripts/pre-commit` — installed by `./gradlew installGitHooks`
- `run/` — local dev server (created by `./gradlew runServer`)

## Modules

25 opt-in modules. Toggle each with `modules.<id>.enabled: true|false` in `config.yml`. Full config keys, permissions, and services per module in the [Module Catalog](docs/reference/module-catalog.md).

| Module ID | Summary |
|---|---|
| `admin` | 14 operator commands (item, gamemode, skull, head, weather, spawnmob, ip, uuid, lastonline, ping, tps, statistic, sudo, help) |
| `anti-cheat` | Client-brand-based cheat detection with hacker log storage and `/hackerlogs` command |
| `api` | Javalin HTTP + WebSocket bridge serving player/status data and bidirectional chat to web frontends |
| `bedrock` | Bedrock player tracking, `/bedrocklist` command, and Java-only whitelist enforcement |
| `bot` | Registers the data file used by the Discord bot integration for player–account linking state |
| `chat-format` | Custom in-game chat formatting via MiniMessage; bridges messages to the shared `ChatBridge` |
| `clickthrough` | Per-player toggle to click through grass and flowers; integrates with `settings-gui` |
| `combat` | Combat tag and elytra-block during combat, with per-player toggle via `settings-gui` |
| `effects` | `/effects` — player-toggled particle trails |
| `firework` | Custom firework rocket crafting recipe with configurable custom model data |
| `glow` | `/glow` — toggle a player's glowing outline |
| `invsee` | `/invsee` and `/enderchest` (`/ec`) — inspect online and offline players' inventories |
| `jail` | `/jail` and `/unjail` — teleport-based jail with configurable location and blocked commands |
| `plugin-hider` | Hides non-allowlisted commands from tab-completion to obscure the full plugin list |
| `positions` | Named waypoint system with InvUI GUI; `/pos` with persistent per-player storage |
| `reboot` | `/reboot` — countdown with title + chat broadcasts and configurable intervals |
| `settings-gui` | Per-player settings GUI opened with `/settings`; publishes `SettingsService` for other modules |
| `sign-color` | MiniMessage tags in sign text and anvil rename fields |
| `spawn` | `/setspawn`, `/spawn`, glide zone, swap-hand boost, launch pad |
| `start-sequence` | Season-start animation: countdown titles + world-border expansion phases; `/start` command |
| `status` | Per-player prefix/suffix managed with `/status`; fires `StatusChangedEvent` consumed by `chat-format` and `tablist` |
| `tablist` | Custom tab-list header/footer with MiniMessage templates and `%placeholder%` tokens |
| `update-mode` | `/updatemode` — maintenance mode that kicks non-bypass players on join |
| `wither` | Configurable Wither stats (health, damage, minions, explosion) |
| `world-mgmt` | `/world`, `/portal`, `/setworldspawn` — multi-world navigation |

## Tech stack

- **Java** 25
- **Paper** 26.x
- **Gradle** Kotlin DSL, with `paperweight-userdev`, `shadow`, `spotless`, `run-paper`
- **Configurate** 4 YAML — config parsing with custom serializers (`Duration`, `Location`, `Component`, `Material`, `Vector`)
- **Adventure + MiniMessage** — all in-game text
- **Javalin** 6 (shadow-relocated) — embedded HTTP + WebSocket server
- **InvUI** (shadow-relocated) — inventory GUI framework
- **JUnit Jupiter 6 · MockBukkit · Mockito 5 · AssertJ 3 · Javalin TestTools** — tests

Shaded libraries are relocated to `de.kreiscraft.libs.*` to avoid classpath conflicts.

## Contributing

- **Commit style:** Conventional Commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`).
- **Formatting:** Spotless is enforced. Run `./gradlew installGitHooks` once to install the pre-commit hook, or `./gradlew spotlessApply` before committing.
- **Adding a module:** see the [Add a New Module](docs/how-to/add-a-new-module.md) how-to and the [Module Template](docs/reference/module-template.md) scaffold.
- **Design context:** see the [Architecture Overview](docs/explanation/architecture.md) and [Design Decisions](docs/explanation/design-decisions.md) before making structural changes.

## Authors

Maintained by the two developer-admins of kreiscraft.de.
