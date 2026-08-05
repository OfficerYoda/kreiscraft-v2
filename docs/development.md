# Development Guide

## Architecture summary

Kreiscraft is a Paper plugin where every feature is a `Module`. A `ModuleRegistry` loads all modules known at compile time, filters by `modules.<id>.enabled` in `config.yml`, topologically sorts by declared `dependsOn()`, and enables them in order.

Each module receives a `ModuleContext` on `enable()`:

- `plugin` — the JavaPlugin, if you truly need it.
- `moduleConfig` — this module's `ConfigurationNode` subtree.
- `messages` — `MessageService` for i18n MiniMessage output.
- `services` — `ServiceRegistry`; publish your service here so other modules can consume it.
- `scheduler` — abstraction over Bukkit's scheduler; **use this instead of Bukkit directly**.
- `commands` — `CommandRegistrar`; registers Paper Brigadier commands.
- `events` — `EventBus`; register listeners here, they're auto-unregistered in `disable()`.

## Writing a new module

1. Create a folder `modules/<yourname>/` (kebab-case is the module id; Java package strips dashes).
2. Add a class `YourNameModule implements Module`.
3. Add a `@ConfigSerializable public record YourNameConfig(...)`.
4. Add a section to `src/main/resources/config.yml`:

   ```yaml
   modules:
     yourname:
       enabled: false
       # …your config keys
   ```

5. Register the module in `KreiscraftBootstrap.start()`.
6. Write tests. Services get pure JUnit + Mockito. Commands/listeners get MockBukkit.

## Testing conventions

- Test class mirrors production class + `Test` suffix.
- Test names: `method_whenCondition_expectedResult()`.
- No `Thread.sleep`. Use `MockScheduler.advance(Duration)` for time.
- Test fixtures live under `src/test/java/de/kreiscraft/testsupport/`.
- Stack: JUnit 6 (Jupiter) + MockBukkit (mockbukkit-v26.1.2) + AssertJ + Mockito.

## Rules

1. `core/` and `shared/` must not import from `modules/`.
2. `modules/*` must not import each other. Communicate via services on `ServiceRegistry` or events.
3. `platform/` is the only place NMS imports are allowed (planned for Plan 2; paperweight.userdev will be added then).
4. Every Bukkit API call originating from an HTTP thread must be dispatched through `Scheduler.runOnMain`.

## Build environment

- **Java:** toolchain 25 (OpenJDK 25)
- **Gradle:** 9.6.1
- **Paper API:** 26.x (currently `26.1.2.build.72-stable`)
- **Build plugin:** builds against `paper-api` only (no paperweight.userdev in Plan 1)

## Running the local test server

The `runServer` task from `run-paper` (3.x) is configured in the build but currently has no
Paper version set. To use it, add a `runServer { minecraftVersion("...") }` block to
`build.gradle.kts`. Full local-server support is planned for Plan 2 when paperweight.userdev
is introduced.
