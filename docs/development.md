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
       enabled: true
       # …your config keys
   ```

5. Register the module in `KreiscraftBootstrap.start()`.
6. Write tests. Services get pure JUnit 6 + Mockito. Commands/listeners get MockBukkit.

## Services published on the `ServiceRegistry`

After Plan 2, these services are available to every module via `ctx.services().get(...)`:

| Type                                | Provided by                                | Purpose                                                                 |
|-------------------------------------|--------------------------------------------|-------------------------------------------------------------------------|
| `ConfigService`                     | `YamlConfigService`                        | root `config.yml` and per-module subtrees                              |
| `MessageService`                    | `ConfigurateMessageService`                | i18n MiniMessage rendering                                              |
| `PlaceholderResolver`               | (same-named class)                         | `%player_name%`, `%tps%`, etc.                                          |
| `PaperPlatform`                     | (same-named record)                        | injected `JavaPlugin` + `Server`; DI seam for `platform/*`              |
| `PlayerBrandProvider`               | (same-named class)                         | reads client "brand" (fabric/forge/vanilla/…) via NMS                   |
| `NmsWhitelist`                      | (same-named class)                         | direct NMS whitelist access; used only for Bedrock UUIDs                |
| `OfflineInventoryReader`            | (same-named class)                         | reads `.dat` NBT for offline `/invsee`                                  |
| `UserService`                       | (same-named class)                         | per-player `User` envelope; replaces the legacy `USERS` static map      |
| `ChatBridge`                        | (same-named class)                         | pub/sub for chat lines; producers publish, api module subscribes        |
| `WhitelistService`                  | `PaperWhitelistService`                    | unified Java (Bukkit API) + Bedrock (NMS) whitelist                     |
| `GuiRegistry`                       | (same-named class)                         | tracks open GUI windows; closed on plugin disable                       |
| `Gui`                               | (same-named class)                         | thin InvUI facade — the sanctioned way to open inventories              |

## Testing conventions

- Test class mirrors production class + `Test` suffix.
- Test names: `method_whenCondition_expectedResult()`.
- No `Thread.sleep`. Use `MockScheduler.advance(Duration)` for time.
- Test fixtures live under `src/test/java/de/kreiscraft/testsupport/`.
- Stack: JUnit 6 (Jupiter) + MockBukkit (mockbukkit-v26.1.2) + AssertJ + Mockito.

## Rules

1. `core/` and `shared/` must not import from `modules/`.
2. `modules/*` must not import each other. Communicate via services on `ServiceRegistry` or events.
3. `platform/` is the only place NMS imports (`net.minecraft.*`, `org.bukkit.craftbukkit.*`) are allowed. Compile-time access is provided by the `paperweight-userdev` Gradle plugin via `paperweight.paperDevBundle("26.1.2.build.72-stable")`; at runtime, modern Paper (1.20.5+) ships mojang-mapped so the same imports resolve without remapping.
4. Every Bukkit API call originating from an HTTP thread must be dispatched through `Scheduler.runOnMain`.

## Build environment

- **Java:** toolchain 25 (OpenJDK 25)
- **Gradle:** 9.6.1
- **Paper API:** 26.x (currently `26.1.2.build.72-stable`)
- **Build plugin:** builds against `paper-api` + the `paperweight-userdev` plugin (v2.0.0-beta.21) which provides compile-time NMS access via `paperweight.paperDevBundle(...)`. Paper 1.20.5+ ships mojang-mapped at runtime, so `platform/` code can `import net.minecraft.*` with mojang-mapped names directly. The plugin's mapped-server jar is filtered off the test classpath to keep MockBukkit as the source of truth for tests.

## Running the local test server

Full local-server support via `./gradlew runServer` is available now — Paper is downloaded and
launched with the shaded plugin JAR installed.
