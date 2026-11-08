# Kreiscraft

A combined Paper 26.x plugin for the kreiscraft.de Minecraft server — a single JAR that replaces several standalone plugins with 24 opt-in feature modules and an embedded HTTP + WebSocket API for the web frontend.

## Highlights

- **Runtime module system.** 24 opt-in feature modules toggled from `config.yml`. Startup uses topological sorting on declared dependencies.
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
./gradlew testNms            # real Paper/NMS offline player-data tests (also included in build)
./gradlew spotlessApply      # auto-format all Java sources
```

Requires **Java 25**. The `runServer` task boots a local Paper instance in `run/`; join at `localhost:25565` after startup.

## Documentation

Full documentation is organised by the [Diátaxis](https://diataxis.fr/) framework in [`docs/`](docs/index.md).

- **Tutorials** — [Getting Started](docs/tutorials/getting-started.md) · [Your First Module](docs/tutorials/your-first-module.md) · [Writing a Command](docs/tutorials/writing-a-command.md)
- **How-to guides** — [Add a Module](docs/how-to/add-a-new-module.md) · [Persist State](docs/how-to/persist-module-state.md) · [Reload Config](docs/how-to/reload-configuration.md) · [Write Tests](docs/how-to/write-tests.md) · [and more…](docs/index.md#how-to-guides--solving-specific-problems)
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
└── modules/                     the 24 feature modules
```

- `src/main/resources/config.yml` — default config template
- `src/main/resources/lang/{de,en}.yml` — MiniMessage lang files (parity enforced by tests)
- `src/main/resources/plugin.yml` — command and permission declarations
- `scripts/pre-commit` — installed by `./gradlew installGitHooks`
- `run/` — local dev server (created by `./gradlew runServer`)

## Modules

24 modules. Toggle each with `modules.<id>.enabled: true|false` in `config.yml`. Most ship enabled; only `firework` and `positions` ship **disabled by default**. Effects starts neutral and emits nothing until a player selects a particle. See the [Module Catalog](docs/reference/module-catalog.md) for module behavior and dependencies, [Configuration](docs/reference/configuration.md) for config keys, and [Commands & Permissions](docs/reference/commands-and-permissions.md) for access control.

| Module ID | Summary |
|---|---|
| `admin` | 12 operator commands (item, skull, head, spawnmob, ip, uuid, lastonline, ping, tps, statistic, sudo, help) |
| `ai-chat` | Chat-triggered AI replies via OpenRouter, with configurable trigger, model, and system prompt; `/aistats` usage statistics |
| `anti-cheat` | Client-brand-based cheat detection with hacker log storage and `/hackerlogs` command |
| `api` | Javalin HTTP + WebSocket bridge serving player/status data and bidirectional chat to web frontends |
| `bedrock` | Bedrock player tracking, `/bedrocklist` command, and Java-only whitelist enforcement |
| `chat-format` | Custom in-game chat formatting via MiniMessage; bridges messages to the shared `ChatBridge` |
| `clickthrough` | Per-player toggle to open containers and Ender Chests through wall signs and filled item frames; toggled through always-available player preferences |
| `combat` | Combat tag and elytra-block during combat, with a per-player display preference (module default when unset) |
| `effects` | `/effects` — player-toggled particle trails |
| `firework` | Custom firework rocket crafting recipe with configurable custom model data |
| `invsee` | `/invsee` and `/enderchest` (`/ec`) — inspect online and offline players' inventories |
| `jail` | `/jail` and `/kerker` — teleport-based jail toggle with an operator-owned location set by `/setjail` and configurable blocked commands |
| `positions` | Named waypoint system with InvUI GUI; `/positions` (`/pos`) with persistent per-player storage |
| `reboot` | `/reboot` and `/restart` — countdown with title + chat broadcasts and configurable intervals |
| `settings-gui` | Per-player settings GUI opened with `/settings`; edits always-available player preferences |
| `sign-color` | Legacy colours, placeholders and safe MiniMessage in sign text and anvil renames |
| `sit` | `/sit` — toggle sitting; also available via the cushion in `/settings` |
| `spawn` | `/setspawn`, `/spawn`, spawn glide zone, swap-hand boost, sneak glide boost |
| `start-sequence` | Season-start animation: countdown titles + one continuous world-border expansion; `/start` command |
| `status` | Per-player prefix/suffix managed with `/status`; fires `StatusChangedEvent` consumed by `chat-format` and `tablist` |
| `tablist` | Original Kreiscraft tab-list banner, playtime, clock, ping and TPS with MiniMessage templates and `%placeholder%` tokens |
| `update-mode` | `/updatemode` — maintenance mode that kicks non-bypass players on activation and login |
| `wither` | Configurable Wither stats (health, damage, minions, explosion) |
| `world-mgmt` | `/world`, `/portal`, `/dimension` — multi-world navigation and dimension access |

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
