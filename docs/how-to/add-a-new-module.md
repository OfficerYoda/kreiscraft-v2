---
title: How to add a new module
---

## Prerequisites

- Know the kebab-case ID your module will use (e.g. `my-feature`).
- The module class lives under `src/main/java/de/kreiscraft/modules/<name>/`.

---

## Steps

### 1. Create the module class

Create `src/main/java/de/kreiscraft/modules/<name>/<Name>Module.java` implementing `Module`.

```java
package de.kreiscraft.modules.myfeature;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class MyFeatureModule implements Module {

    // Hold the config in a plain field and pass () -> this.config (a Supplier) to any listener,
    // command, or service that reads config at event time. Those objects then observe the new
    // record after onConfigReload() swaps the field — see docs/how-to/reload-configuration.md.
    //
    // If any consumer reads config off the main thread (e.g. an AsyncChatEvent handler), mark
    // the field `volatile`.
    private MyFeatureConfig config;

    @Override
    public String id() {
        return "my-feature";          // kebab-case, matches config key
    }

    // Override only if this module depends on another module being enabled first.
    // @Override
    // public List<String> dependsOn() { return List.of("other-module"); }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(MyFeatureConfig.class);
            if (this.config == null) {
                this.config = new MyFeatureConfig(/* defaults */);
            }
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        // Register listeners, commands, services here using ctx.*
        // Pass () -> this.config so the listener always reads the current config.
        ctx.events().register(new MyFeatureListener(() -> this.config, ctx.messages()));
        ctx.commands().register(MyFeatureCommand.build(ctx.messages()), "My feature command");
    }

    @Override
    public void disable() {
        // Clean up resources that EventBus.unregisterAll() won't handle.
        // Listeners registered via ctx.events().register() are unregistered automatically.
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(MyFeatureConfig.class);
            if (this.config == null) {
                this.config = new MyFeatureConfig(/* defaults */);
            }
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
```

`MyFeatureListener` therefore takes a `Supplier<MyFeatureConfig>` and calls `config.get()` inside each
handler — never the record itself. If no listener, command, or task reads config after startup, a
plain field is fine; the `Supplier` is what makes reloads propagate. Full detail:
[reload-configuration](reload-configuration.md).

`ModuleContext` fields available in `enable()`:

| Field | Type | Purpose |
|---|---|---|
| `ctx.plugin()` | `JavaPlugin` | Raw plugin handle |
| `ctx.moduleConfig()` | `ConfigurationNode` | Pre-scoped to `modules.<id>` |
| `ctx.messages()` | `MessageService` | Send/render i18n messages |
| `ctx.services()` | `ServiceRegistry` | Publish or consume services |
| `ctx.scheduler()` | `Scheduler` | Schedule tasks |
| `ctx.commands()` | `CommandRegistrar` | Register Brigadier commands |
| `ctx.events()` | `EventBus` | Register Bukkit listeners |

### 2. Create a `@ConfigSerializable` config class

```java
package de.kreiscraft.modules.myfeature;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record MyFeatureConfig(boolean someFlag, int someValue) {

    public MyFeatureConfig {
        // Apply safe defaults when keys are absent (Configurate initialises
        // primitive int to 0 and boolean to false when the key is missing).
        if (someValue == 0) someValue = 42;
    }
}
```

Supported custom serializer types: `Duration` (e.g. `"5m"`), `Location`, `Component`,
`Material`, `Vector`. These are registered automatically via `KreiscraftSerializers`.

### 3. Add the module entry to `config.yml`

In `src/main/resources/config.yml`, add under `modules:`:

```yaml
modules:
  my-feature:
    enabled: true
    some-flag: true
    some-value: 42
```

A module is **disabled by default** if the `enabled` key is absent.

### 4. Add message keys to both lang files

Add identical key sets to **both** `src/main/resources/lang/de.yml` and `lang/en.yml`.

```yaml
# lang/en.yml and lang/de.yml  (keys must be identical in both files)
my-feature:
  action-done: "<prefix> <green>Done: <result>"
  error-foo: "<prefix> <red>Something went wrong."
```

`LangParityTest` enforces that both files share the same key set. The build will fail if they diverge.

### 5. Register the module in `KreiscraftBootstrap.java`

In `src/main/java/de/kreiscraft/KreiscraftBootstrap.java`, add one line inside `start()`:

```java
modules.register(new de.kreiscraft.modules.myfeature.MyFeatureModule());
```

Insert it in alphabetical order with the existing registrations.

### 6. Add permissions to `plugin.yml` (if needed)

If the module registers commands or gates features behind a permission node, declare it in
`src/main/resources/plugin.yml`:

```yaml
permissions:
  kreiscraft.myfeature.use:
    description: "Allows using the my-feature command"
    default: op
```

### 7. Write at least one test

Create `src/test/java/de/kreiscraft/modules/myfeature/MyFeatureModuleTest.java`.

Minimal pattern using MockBukkit:

```java
@ExtendWith(MockitoExtension.class)
class MyFeatureModuleTest {

    private ServerMock server;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void enable_withValidConfig_doesNotThrow() {
        // Build a ModuleContext with a real or mocked config node and assert behaviour.
    }
}
```

Run all tests (including `LangParityTest`) with:

```
./gradlew test
```
