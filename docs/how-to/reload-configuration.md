---
title: How to reload configuration at runtime
---

## 1. Run the command

```
/kreiscraft reload
```

Required permission: `kreiscraft.admin` (granted to ops by default).
Can be run in-game or from the server console.

On success you see `core.reload.success`. On failure (any stage throws) you see `core.reload.failure` with the error message; no partial state is left — modules that reloaded before the error keep their new config.

---

## 2. What the reload does

`ReloadCommand` executes three steps in order (`ReloadCommand.java:21-23`):

| Step | Method | Effect |
|------|--------|--------|
| 1 | `ConfigService.reload()` | Re-reads `config.yml` from disk into a new `ConfigurationNode`. |
| 2 | `MessageService.reload()` | Re-reads the active lang file (e.g. `lang/de.yml`). |
| 3 | `ModuleRegistry.reloadConfigs(newRoot)` | Calls `onConfigReload(moduleNode)` on every **enabled** module, passing only that module's sub-node (`modules.<id>`). Modules are called in the order they were enabled. |

If `onConfigReload` throws for a particular module the exception is caught, logged at `WARNING`, and the remaining modules continue — see `ModuleRegistry.java:82-88`.

---

## 3. What the reload does NOT do

- **Does not enable or disable modules.** A module that was disabled before the reload stays disabled. New `enabled: true` entries take effect only on the next server start.
- **Does not restart the server.**
- **Does not restart the Javalin HTTP server** — *unless* `host`, `port`, or `cors-origins` changed, in which case `ApiModule.onConfigReload` stops and restarts Javalin automatically (`ApiModule.java:107-120`). All other API config fields (`heartbeat-interval`, `async-timeout`, etc.) require a full server restart.

---

## 4. Implementing `onConfigReload` in a module

The `Module` interface provides a no-op default:

```java
// Module.java
default void onConfigReload(ConfigurationNode moduleConfig) {}
```

Override it whenever your module has runtime state derived from config.

### Minimal pattern

```java
@Override
public void onConfigReload(ConfigurationNode moduleNode) {
    try {
        this.config = moduleNode.get(MyConfig.class);
    } catch (SerializationException e) {
        throw new ModuleException("failed to reload config for module " + id(), e);
    }
}
```

This is sufficient when no listener or background task holds a direct reference to `config` — you can just read `this.config` from the main thread each time.

### Thread-safe propagation with `AtomicReference`

When a listener or async task needs live access to the current config, hold it in an `AtomicReference` and pass a `Supplier` lambda to the listener. `AntiCheatModule` and `ChatFormatModule` both use this pattern.

```java
// In the module class:
private final AtomicReference<MyConfig> configRef = new AtomicReference<>();

@Override
public void enable(ModuleContext ctx) {
    try {
        configRef.set(ctx.moduleConfig().get(MyConfig.class));
    } catch (SerializationException e) {
        throw new ModuleException("failed to load config for module " + id(), e);
    }
    // Pass a Supplier so the listener always reads the current value:
    ctx.events().register(new MyListener(configRef::get, ...));
}

@Override
public void onConfigReload(ConfigurationNode moduleNode) {
    try {
        configRef.set(moduleNode.get(MyConfig.class));
    } catch (SerializationException e) {
        throw new ModuleException("failed to reload config for module " + id(), e);
    }
}
```

```java
// In the listener:
public final class MyListener implements Listener {
    private final Supplier<MyConfig> config;

    public MyListener(Supplier<MyConfig> config, ...) {
        this.config = config;
    }

    @EventHandler
    public void onSomeEvent(SomeEvent e) {
        MyConfig cfg = config.get(); // always the latest value
        ...
    }
}
```

`AtomicReference.set` is a single volatile write — safe to call from the main thread while Bukkit event handlers read it concurrently (`configRef::get`).

---

## 5. Error handling contract

- If `onConfigReload` throws a `RuntimeException` (including `ModuleException`), `ModuleRegistry` catches it, logs it at `WARNING`, and proceeds with the next module.
- Config deserialization errors should be wrapped in `ModuleException` so the log message identifies the module.
- After a failed reload the module retains whatever config it held before the call.
