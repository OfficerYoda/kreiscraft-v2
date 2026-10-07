---
title: SDK Reference
---

This document is the authoritative reference for every public type that module authors interact with. All types live under `de.kreiscraft.*`. Signatures are taken directly from the source; do not rely on IntelliSense alone — the framework has contract rules that the compiler does not enforce.

---

## Table of Contents

1. [`Module`](#module)
2. [`ModuleContext`](#modulecontext)
3. [`ServiceRegistry`](#serviceregistry)
4. [`EventBus`](#eventbus)
5. [`CommandRegistrar`](#commandregistrar)
6. [`Scheduler`](#scheduler)
7. [`MessageService`](#messageservice)
8. [`ConfigService`](#configservice)
9. [`DataFile<T>`](#datafilet)
10. [`PlaceholderResolver`](#placeholderresolver)
11. [`ChatBridge`](#chatbridge)
12. [`Gui` / `GuiRegistry`](#gui--guiregistry)

---

## `Module`

**Package:** `de.kreiscraft.core.module`
**Type:** `interface`

The single interface every module must implement. The `ModuleRegistry` drives the lifecycle: it calls `enable()` on startup (after topological dependency sort), `disable()` on shutdown (reverse order), and `onConfigReload()` whenever the operator runs `/kreiscraft reload`.

### Methods

| Signature | Description |
|-----------|-------------|
| `String id()` | Unique, kebab-case identifier for this module. Must match the key under `modules:` in `config.yml`. Used for logging, dependency declarations, and config node lookup. Must not be `null` or empty and must be globally unique — `ModuleRegistry` throws `ModuleException` on a duplicate. |
| `default List<String> dependsOn()` | Returns the ids of modules that must be enabled before this one. Default: `List.of()`. If any declared dependency is not enabled, `ModuleRegistry.enableAll()` throws `ModuleException` before calling `enable()`. |
| `void enable(ModuleContext ctx)` | Called once when the module is started. Wire up listeners, publish services, and register commands here. **This is the only place where commands may be registered** — the Paper Brigadier lifecycle requires all commands to be registered during the plugin enable phase, which maps directly to this method. Throw `ModuleException` (wrapping the cause) to abort startup; the registry will log the error, skip the module, and count it as failed. Do not store a reference to `ctx` — instead, store individual services extracted from it. |
| `default void disable()` | Called on plugin shutdown, in reverse enable order. Release external resources (threads, task handles, NMS state). Exceptions are caught and logged by the registry; the disable sequence continues. Default implementation does nothing. |
| `default void onConfigReload(ConfigurationNode moduleConfig)` | Called after the operator reloads config. `moduleConfig` is the same node as `ctx.moduleConfig()` was in `enable()`, re-read from disk. Update in-memory config state and propagate to dependent objects. Exceptions are caught and logged; other modules still receive their reload callbacks. Default implementation does nothing. |

---

## `ModuleContext`

**Package:** `de.kreiscraft.core.module`
**Type:** `record`

```java
public record ModuleContext(
    JavaPlugin plugin,
    ConfigurationNode moduleConfig,
    MessageService messages,
    ServiceRegistry services,
    Scheduler scheduler,
    CommandRegistrar commands,
    EventBus events
) {}
```

Injected into `Module.enable()`. All fields are non-null at the time of injection.

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `plugin` | `org.bukkit.plugin.java.JavaPlugin` | The Kreiscraft `JavaPlugin` instance. Use for `getDataFolder()`, `getServer()`, or any Bukkit API that requires a plugin reference. Do not use for scheduling — use `scheduler` instead. |
| `moduleConfig` | `org.spongepowered.configurate.ConfigurationNode` | The Configurate node at `modules.<id>` in `config.yml`. Deserialize your `@ConfigSerializable` config record with `moduleConfig.get(MyConfig.class)`. The same node is passed to `onConfigReload()`. |
| `messages` | `MessageService` | Shared message/lang service. Use for all player-facing text. |
| `services` | `ServiceRegistry` | Shared service registry. Publish your service interface here so other modules can depend on it. Retrieve cross-module services here instead of casting or instantiating directly. |
| `scheduler` | `Scheduler` | Server-aware task scheduler. Use in preference to `Bukkit.getScheduler()` directly. |
| `commands` | `CommandRegistrar` | Brigadier command registrar. Call `commands.register()` inside `enable()` only. |
| `events` | `EventBus` | Bukkit event listener manager. All listeners registered through `events` are automatically unregistered on `disable()`. |

---

## `ServiceRegistry`

**Package:** `de.kreiscraft.core.service`
**Type:** `final class`

A type-keyed, thread-safe map of singleton services. Shared across all modules via `ModuleContext.services()`. The bootstrap wires core platform services (e.g. `MessageService`, `ConfigService`, `UserService`, `ChatBridge`, `Gui`) before any module `enable()` is called.

### Methods

| Signature | Description |
|-----------|-------------|
| `<T> void publish(Class<T> type, T impl)` | Registers `impl` under the `type` key. Overwrites any previous entry for the same type. Modules publishing a service should publish the **interface**, not the concrete class, so consumers remain decoupled. Call this inside `enable()`. |
| `<T> T get(Class<T> type)` | Returns the service registered under `type`. Throws `ServiceNotFoundException` (unchecked) if no service is registered. Use this when the service is a hard dependency; a missing service is a configuration/startup error. |
| `<T> Optional<T> find(Class<T> type)` | Returns an `Optional` of the service, empty if not registered. Use for optional integrations. |

**Contract:** `ConcurrentHashMap` is used internally — reads and writes are thread-safe, but there is no ordering guarantee between concurrent `publish` and `get` calls. In practice, `publish` always happens in `enable()` before any consumer calls `get()`, so no further synchronization is needed.

---

## `EventBus`

**Package:** `de.kreiscraft.core.event`
**Type:** `final class`

A thin wrapper over `Bukkit.getPluginManager().registerEvents()` that tracks every registered listener so they can all be bulk-unregistered on plugin disable. Every listener registered through this bus is automatically cleaned up when `KreiscraftBootstrap.stop()` calls `unregisterAll()`.

### Methods

| Signature | Description |
|-----------|-------------|
| `void register(Listener listener)` | Registers `listener` with Bukkit and adds it to the internal tracking list. Call this inside `Module.enable()`. |
| `void unregisterAll()` | Calls `HandlerList.unregisterAll()` for every previously registered listener and clears the tracking list. Called automatically by the bootstrap on plugin disable. **Do not call this from module code** — modules should rely on the framework-driven lifecycle. |

**`PriorityListener`** (`de.kreiscraft.core.event.PriorityListener`) is a sub-interface of `Listener` that provides a `priority()` default method returning `EventPriority.NORMAL`. It is a documentation marker only; actual priority is still controlled by the `@EventHandler(priority = ...)` annotation on each handler method.

---

## `CommandRegistrar`

**Package:** `de.kreiscraft.core.command`
**Type:** `interface`

Wraps the Paper Brigadier bootstrap API. Commands registered here are submitted to the server's Brigadier tree during the plugin enable phase and are available to all connected clients.

### Methods

| Signature | Description |
|-----------|-------------|
| `void register(LiteralCommandNode<CommandSourceStack> node, String description)` | Registers a root-level command node with the given description string (shown in `/help`). |
| `void register(LiteralCommandNode<CommandSourceStack> node, String description, String... aliases)` | Same as above, but also registers one or more alias literal names for the same node. |

**Brigadier lifecycle constraint:** `register()` **must** be called inside `Module.enable()`. The Paper lifecycle hooks that accept new commands close after the plugin enable phase; any call made after that (e.g. from a repeating task or an event handler) will silently not propagate to connected clients.

**Pattern:** Command classes use a static `build(...)` factory method returning a fully-constructed `LiteralCommandNode<CommandSourceStack>`:

```java
public final class MyCommand {
    private MyCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MyService service, MessageService msg) {
        return Commands.literal("mycommand")
            .requires(src -> src.getSender().hasPermission("kreiscraft.mymodule.use"))
            .executes(ctx -> {
                // ...
                return Command.SINGLE_SUCCESS;
            })
            .build();
    }
}
```

---

## `Scheduler`

**Package:** `de.kreiscraft.core.scheduler`
**Type:** `interface`

Server-thread-aware task scheduler. The production implementation (`BukkitScheduler`) delegates to the Paper `RegionScheduler` / `AsyncScheduler` APIs. All tasks return a `Scheduler.Task` handle for cancellation.

### Methods

| Signature | Return | Description |
|-----------|--------|-------------|
| `Task runOnMain(Runnable task)` | `Task` | Queues `task` to run on the server main thread as soon as possible. If the calling thread is already the main thread, execution is still deferred to the next tick. |
| `Task runLater(Duration delay, Runnable task)` | `Task` | Runs `task` on the main thread after `delay`. Use `Duration.ofMillis()`, `Duration.ofSeconds()`, etc. The scheduler converts to ticks internally. |
| `Task runRepeating(Duration delay, Duration period, Runnable task)` | `Task` | Runs `task` on the main thread after `delay`, then repeatedly every `period`. The returned handle must be stored and cancelled in `Module.disable()` if the module registers a repeating task. |
| `boolean isMainThread()` | `boolean` | Returns `true` if the calling thread is the server main thread. Use for assertions/guards in service methods. |

### `Scheduler.Task`

Nested interface returned by all scheduling methods.

| Signature | Description |
|-----------|-------------|
| `void cancel()` | Cancels the task. No-op if already cancelled or completed. |
| `boolean isCancelled()` | Returns `true` if the task has been cancelled. |

**Async note:** There are no explicit `async` variants on the `Scheduler` interface; for fire-and-forget async work, call `task.runAsync()` directly on the underlying Bukkit API, or use a `CompletableFuture`. Tasks submitted via `Scheduler` run on the main thread unless documented otherwise.

---

## `MessageService`

**Package:** `de.kreiscraft.core.message`
**Type:** `interface`

Locale-aware, MiniMessage-based message service. Lang files live in `src/main/resources/lang/<lang>.yml` and are loaded from `<dataFolder>/lang/<lang>.yml` at startup. The active language is configured under `core.language` in `config.yml` (default: `de`).

Messages support `<prefix>` as a built-in MiniMessage tag that expands to the value of the top-level `prefix:` key in the active lang file. Additional `%token%` placeholders are resolved by `PlaceholderResolver`.

### Methods

| Signature | Description |
|-----------|-------------|
| `Component render(String key, Object... placeholders)` | Resolves the lang key, substitutes placeholder pairs, and returns an Adventure `Component`. `placeholders` is a flat alternating array of `(name, value)` pairs: `render("foo.bar", "player", player.getName(), "count", 3)`. Returns a red error component if the key is missing. |
| `void send(CommandSender sender, String key, Object... placeholders)` | Calls `render()` and sends the result to `sender`. |
| `void broadcast(String key, Object... placeholders)` | Calls `render()` and sends the result to every online player and the console. |
| `void reload() throws IOException` | Reloads the lang file from disk. Called automatically by `/kreiscraft reload`. Should not be called from module code. |

**Placeholder convention:** Pass placeholder values as `(String name, Object value)` pairs in varargs. The value is converted to a string with `String.valueOf()`. Built-in `%token%` placeholders (see `PlaceholderResolver`) do not need to be supplied — they are resolved automatically.

---

## `ConfigService`

**Package:** `de.kreiscraft.core.config`
**Type:** `interface`

Manages the main `config.yml` and the registry of `DataFile` instances that the auto-save task persists periodically.

### Methods

| Signature | Description |
|-----------|-------------|
| `ConfigurationNode root()` | Returns the live Configurate root node for `config.yml`. Re-assigned on `reload()`. Do not cache this reference across reloads — re-read from `ctx.moduleConfig()` in `onConfigReload()` instead. |
| `default ConfigurationNode module(String id)` | Convenience: returns `root().node("modules", id)`. Equivalent to what `ModuleContext.moduleConfig()` provides. |
| `void reload() throws IOException` | Reloads `config.yml` from disk and updates `root()`. Called by `/kreiscraft reload`; the reload command separately triggers `ModuleRegistry.reloadConfigs()`. |
| `void register(DataFile<?> dataFile)` | Registers a `DataFile` with the service so the auto-save task includes it in periodic flushes. Call this inside `Module.enable()` after constructing and loading the `DataFile`. |
| `List<DataFile<?>> registeredDataFiles()` | Returns an unmodifiable live view of all registered data files. Used internally by `AutoSaveTask`. |

Retrieve `ConfigService` from the registry: `ctx.services().get(ConfigService.class)`.

---

## `DataFile<T>`

**Package:** `de.kreiscraft.core.config`
**Type:** `abstract class`

A single YAML file in the plugin data folder that persists a typed state object `T`. Tracks a dirty flag so `flushIfDirty()` only writes when the state has actually changed. The auto-save task calls `flushIfDirty()` on every registered `DataFile` at the configured interval (default: every 5 minutes).

### Constructor

```java
protected DataFile(Path dataFolder, String relativePath)
```

`dataFolder` is `ctx.plugin().getDataFolder().toPath()`. `relativePath` is relative to `dataFolder`, e.g. `"data/spawn.yml"`. The file need not exist at construction time; `load()` creates an empty state via `empty()` when the file is absent.

### Public methods

| Signature | Description |
|-----------|-------------|
| `String relativePath()` | Returns the relative path supplied at construction. |
| `void load() throws IOException` | Reads the file from disk and deserializes the state via `load(ConfigurationNode)`. If the file does not exist, calls `empty()` to create the initial state. Clears the dirty flag. Must be called once inside `Module.enable()` before registering with `ConfigService`. |
| `T get()` | Returns the current in-memory state. Returns `null` before `load()` has been called. |
| `void update(Consumer<T> mutator)` | Applies `mutator` to the current state and sets the dirty flag. Thread-safety is the caller's responsibility — ensure mutations happen on the main thread or are otherwise synchronized. |
| `void flushIfDirty() throws IOException` | If dirty: creates parent directories, serializes state via `save(T, ConfigurationNode)`, writes the YAML file, and clears the dirty flag. No-op if not dirty. |

### Abstract methods (implement in your subclass)

| Signature | Description |
|-----------|-------------|
| `protected abstract T load(ConfigurationNode node) throws SerializationException` | Deserializes `T` from a Configurate node. Called by `load()` when the file exists. |
| `protected abstract void save(T state, ConfigurationNode node) throws SerializationException` | Serializes `state` into `node`. Called by `flushIfDirty()`. |
| `protected abstract T empty()` | Returns the default/empty state used when the file does not exist. Must return a non-null mutable object if you will call `update()` on it. |

---

## `PlaceholderResolver`

**Package:** `de.kreiscraft.core.message`
**Type:** `final class`

Resolves `%token%` patterns in MiniMessage source strings. Called internally by `ConfigurateMessageService` before MiniMessage parsing. Tokens that are unrecognised are left as-is (the literal `%token%` string remains).

A `Player` context is optional — tokens that require a player (e.g. `%player_name%`) return `null` when `context` is `null`, which causes the token to be left unchanged.

### Built-in tokens

| Token | Requires player | Resolves to |
|-------|-----------------|-------------|
| `%player_name%` | yes | `player.getName()` |
| `%server_players%` | no | Current online player count (`server.getOnlinePlayers().size()`) as a string |
| `%tps%` | no | 1-minute TPS, capped at `20.0` and rounded to at most two decimal places (e.g. `"20.0"`, `"19.87"`) |
| `%system_time%` | no | Current server-local time formatted as `HH:mm:ss` (timezone from `core.timezone` in `config.yml`, default `Europe/Berlin`) |
| `%ping%` | yes | `player.getPing()` in milliseconds as a string |
| `%playtime%` | yes | Player's `PLAY_ONE_MINUTE` statistic formatted as `HH:MM:SS` (zero-padded hours, minutes and seconds) |
| `%plugin_prefix%` | no | Literal string `"[Kreiscraft]"` |

### Method

| Signature | Description |
|-----------|-------------|
| `String resolveString(String template, Player context)` | Replaces all `%token%` occurrences in `template` with resolved values. `context` may be `null` for non-player contexts (console, broadcast). |

`PlaceholderResolver` is published in the `ServiceRegistry` under `PlaceholderResolver.class` and can be retrieved with `ctx.services().get(PlaceholderResolver.class)`.

---

## `ChatBridge`

**Package:** `de.kreiscraft.shared.chat`
**Type:** `final class`

A thread-safe publish/subscribe bus for in-game chat lines. The `chat-format` module publishes `ChatBridgeEvent` instances; the `api` module subscribes to forward them to WebSocket clients. Any module may subscribe to observe chat.

`ChatBridge` is published in the `ServiceRegistry` under `ChatBridge.class`.

### Methods

| Signature | Description |
|-----------|-------------|
| `Subscription subscribe(Consumer<ChatBridgeEvent> subscriber)` | Registers `subscriber` to receive all future events. Returns a `Subscription` whose `unsubscribe()` removes it. Store the subscription and call `unsubscribe()` in `Module.disable()`. |
| `void publish(ChatBridgeEvent event)` | Delivers `event` to all current subscribers. Thread-safe. If a subscriber throws a `RuntimeException`, the exception is logged and delivery continues to the next subscriber. |
| `int subscriberCount()` | Returns the number of active subscribers. Intended for diagnostics. |

### `ChatBridgeEvent` record

```java
public record ChatBridgeEvent(
    UUID senderUuid,        // null for console/system messages
    String senderName,      // raw display name at time of send
    Component rendered,     // fully-rendered Adventure component broadcast in-game
    String plainText,       // pre-serialized plain text for external consumers
    Instant timestamp       // when the line was produced
) {}
```

### `ChatBridge.Subscription` interface

```java
public interface Subscription {
    void unsubscribe();
}
```

---

## `Gui` / `GuiRegistry`

**Package:** `de.kreiscraft.shared.gui`
**Type:** `final class` (both)

`Gui` is the single sanctioned entry point for opening inventory GUIs backed by InvUI. Every window opened through `Gui.open()` is tracked in `GuiRegistry` and automatically closed when the server shuts down (`KreiscraftBootstrap.stop()` calls `guiRegistry.closeAll()`). **Do not open InvUI windows directly** — bypassing `Gui` leaks window handles and causes ghost inventories on plugin reload.

Both classes are published in the `ServiceRegistry`:

```java
Gui gui = ctx.services().get(Gui.class);
```

### `Gui`

| Signature | Description |
|-----------|-------------|
| `GuiRegistry.Handle open(Player viewer, xyz.xenondevs.invui.gui.Gui invui, Component title)` | Wraps `invui` in a single-viewer InvUI `Window`, opens it for `viewer`, registers it with `GuiRegistry`, and returns a `Handle`. The handle auto-unregisters when the player closes the window normally. Call `handle.close()` to close it programmatically. |

Build the `xyz.xenondevs.invui.gui.Gui` object using InvUI's own builder API (`Item`, `AbstractItem`, layouts, etc.) and pass it to `Gui.open()`. `Gui` only manages the window and registry bookkeeping.

### `GuiRegistry`

Normally you do not interact with `GuiRegistry` directly — use `Gui.open()`. The relevant members for reference:

| Signature | Description |
|-----------|-------------|
| `void register(Handle handle)` | Adds `handle` to the tracked set. Called automatically by `Gui.open()`. |
| `void unregister(Handle handle)` | Removes `handle`. Called automatically when the player closes a window. |
| `void closeAll()` | Closes every open handle and clears the set. Exceptions per handle are caught and logged. Called by bootstrap on disable. |
| `int openCount()` | Returns the number of currently open windows. Useful for diagnostics. |

### `GuiRegistry.Handle`

```java
public interface Handle {
    void close();
}
```
