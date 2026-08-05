# Kreiscraft

Combined Paper 26.x plugin: gameplay + admin utilities + web API bridge.

## What's inside

- Module-based architecture (`core/module`) — every feature is opt-in via `config.yml`.
- Paper Brigadier commands + Adventure MiniMessage everywhere.
- YAML configuration (Configurate) + separate `data/` files for runtime state.
- Test coverage on all business logic via JUnit 6, MockBukkit, and Mockito.

## Build

```bash
./gradlew build              # compile + Spotless check + tests
./gradlew test               # tests only
./gradlew spotlessApply      # auto-format all Java sources
```

> **Note:** The `runServer` task (run-paper plugin) is present but requires a Paper version
> to be configured in `build.gradle.kts` before it will work. This is planned for Plan 2.

## Repository layout

```
src/main/java/de/kreiscraft/
├── Kreiscraft.java              JavaPlugin entrypoint
├── KreiscraftBootstrap.java     wires services and modules
├── ReloadCommand.java           /kreiscraft reload
└── core/                        the platform (module system, config, messages, scheduler, events)
```

Feature modules live under `modules/` (added in Plan 3+).

## Contributing

Conventional Commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`).
Install the pre-commit hook once: `./gradlew installGitHooks`.

See `docs/development.md` for architecture details and module authoring guide.
