---
title: How to reload configuration at runtime
---

## 1. Run the command

```
/kreiscraft reload
```

Required permission: `kreiscraft.admin` (granted to ops by default).
Can be run in-game or from the server console.

On success you see `core.reload.success`. Reload is not transactional: modules that reload successfully keep their new config even if another module fails.

---

## 2. What the reload does

`ReloadCommand` executes three steps in order:

| Step | Method | Effect |
|------|--------|--------|
| 1 | `ConfigService.reload()` | Re-reads `config.yml` from disk into a new `ConfigurationNode`. |
| 2 | `MessageService.reload()` | Re-reads the active lang file (e.g. `lang/de.yml`). |
| 3 | `ModuleRegistry.reloadConfigs(newRoot)` | Calls `onConfigReload(moduleNode)` on every **enabled** module, passing only that module's sub-node (`modules.<id>`). Modules are called in the order they were enabled. |

If `onConfigReload` throws for a particular module, `ModuleRegistry.reloadConfigs()` catches it, logs at `WARNING`, and continues with the remaining modules.

---

## 3. What the reload does NOT do

- **Does not enable or disable modules.** A module that was disabled before the reload stays disabled. New `enabled: true` entries take effect only on the next server start.
- **Does not restart the server.**
- **Does not restart the Javalin HTTP server** — *unless* `host`, `port`, or `cors-origins` changed, in which case `ApiModule.onConfigReload()` stops and restarts Javalin automatically. All other API config fields (`heartbeat-interval`, `async-timeout`, etc.) require a full server restart.
- **Does not reschedule repeating tasks.** Modules that hold a scheduler task read the new values but keep the old schedule. For example, `tablist` hot-reloads its header/footer templates, but a changed `update-interval` only takes effect after a restart.
- **Does not apply module enable/disable edits.** Module enablement is restart-only. In-game configuration edits are validated and saved atomically on save, so a reload simply observes the values that were already persisted.

---

## 4. Implementing `onConfigReload` in a module

The `Module` interface provides a no-op default:

```java
// Module.java
default void onConfigReload(ConfigurationNode moduleConfig) {}
```

Override it whenever your module has runtime state derived from config.

### Default: a plain field plus a `Supplier`

Hold the config in a plain field and hand consumers `() -> this.config`. They read the current value
on every call, so a reload is observed without re-registering anything.

```java
// In the module class:
private MyConfig config;

@Override
public void enable(ModuleContext ctx) {
    try {
        this.config = ctx.moduleConfig().get(MyConfig.class);
    } catch (SerializationException e) {
        throw new ModuleException("failed to load config for module " + id(), e);
    }
    // Pass a Supplier so the listener always reads the current value:
    ctx.events().register(new MyListener(() -> this.config, ...));
}

@Override
public void onConfigReload(ConfigurationNode moduleNode) {
    try {
        this.config = moduleNode.get(MyConfig.class);
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

If no listener, command, or task reads config after startup, you do not even need the `Supplier` —
just read `this.config` from the main thread each time.

### Reading config off the main thread: add `volatile`

`/kreiscraft reload` writes the config on the main thread, and most Bukkit event handlers run there
too, so the plain field above is enough. But some events fire on another thread — notably Paper's
`AsyncChatEvent` — and a reload write is not guaranteed to be visible to them. If any consumer calls
`config.get()` off the main thread, mark the module field `volatile`:

```java
private volatile MyConfig config;   // the reload is now visible to the async reader
```

A `volatile` write publishes the new reference so the async reader sees it on its next `config.get()`;
the `Supplier` lambda itself is unchanged. Without it, an async handler may keep reading the
pre-reload record indefinitely.

---

## 5. Error handling contract

- If `onConfigReload` throws a `RuntimeException` (including `ModuleException`), `ModuleRegistry` catches it, logs it at `WARNING`, and proceeds with the next module.
- Config deserialization errors should be wrapped in `ModuleException` so the log message identifies the module.
- After a failed reload the module retains whatever config it held before the call.
