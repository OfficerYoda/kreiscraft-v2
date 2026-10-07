---
title: Architecture Overview
---

This document explains how Kreiscraft is structured and why it works the way it does. It is aimed
at the developer-admins who maintain the plugin and need to understand the underlying design to
make changes confidently.

---

## Core Concepts

| Term | Meaning in Kreiscraft |
|---|---|
| **Plugin** | The single deployable Kreiscraft JAR loaded by Paper. All modules run inside it. |
| **Module** | A cohesive runtime feature that owns its implementation, such as commands, listeners, configuration, and state. A module is not a separate plugin or Gradle project. |
| **Module dependency** | A requirement declared through `dependsOn()`. Dependencies start before the module and remain available until it stops. |
| **Module lifecycle** | `enable()` starts a module, `onConfigReload()` updates reloadable settings, and `disable()` releases resources. |
| **`ModuleContext`** | The dependencies and framework capabilities supplied to a module when it starts. |
| **Service** | Functionality published by the core, shared layer, or a module for other modules to consume through `ServiceRegistry`. |
| **Event and listener** | An event announces that something happened; listeners react without the sender calling them directly. `EventBus` manages Bukkit listener registration and cleanup. |
| **Configuration and state** | Configuration is administrator-managed input in `config.yml`; state is runtime data persisted under `data/`. |
| **Composition root** | `KreiscraftBootstrap`, the one place that constructs objects and connects their dependencies. |

Modules are cohesive but not isolated. They can collaborate through declared dependencies, shared
services, and events. For example, `chat-format` depends on `status`, while `combat` and
`clickthrough` consume the shared, always-available `PlayerPreferencesService` instead of depending
on the settings GUI.

---

## Codebase Areas

The source tree is divided into four distinct packages, which form a clear dependency hierarchy:

```
de.kreiscraft/
├── platform/          — thin Paper/NMS wrappers (no game logic)
├── core/              — module registry, config, events, scheduler, commands, messages
├── shared/            — cross-cutting services used by multiple modules (ChatBridge, UserService, …)
└── modules/           — 23 independently opt-in feature modules
```

**Platform layer** (`platform/`)  
Contains lightweight record-based wrappers around Paper APIs so that the rest of the code never
calls `Bukkit.getServer()` or other static methods directly. `PaperPlatform` is a plain Java
`record(JavaPlugin, Server)` that is constructed once in the composition root and injected
wherever it is needed. This design means that any class that operates only on `PaperPlatform`
can be instantiated in a test without starting a real server. Other platform classes
(`NmsWhitelist`, `OfflineInventoryReader`, `PlayerBrandProvider`) follow the same pattern: they
accept a `PaperPlatform` as a constructor argument instead of reaching into global Bukkit state.

**Core layer** (`core/`)  
Provides the infrastructure that every module relies on: `ModuleRegistry`, `ModuleContext`,
`ServiceRegistry`, `EventBus`, `Scheduler`/`BukkitScheduler`, `ConfigService`/`YamlConfigService`,
`DataFile`, `MessageService`, and `CommandRegistrar`/`PaperCommandRegistrar`. None of these
classes have knowledge of any specific feature; they are the engine, not the game.

**Shared layer** (`shared/`)  
Houses services that cut across modules but are not part of the core engine: `ChatBridge`,
`UserService`/`UserListener`, `GuiRegistry`/`Gui`, `WhitelistService`, and the always-available
`PlayerPreferencesService`/`PlayerPreferencesData`. They are constructed in the composition root and
published to `ServiceRegistry` so any module can consume them. `PlayerPreferencesService` is
published by the root rather than by a module so preferences stay available even when every
preference-editing module is disabled.

**Feature layer** (`modules/`)  
Contains 23 feature modules, one package per feature. Each module keeps its feature-specific code
together and registers its Bukkit listeners, Brigadier commands, and data files through the
handles provided by `ModuleContext`. Modules that need services from other modules declare that
dependency explicitly via `dependsOn()`, which both enforces initialization order and documents
coupling.

---

## The Composition Root: `KreiscraftBootstrap`

`Kreiscraft.java` (the `JavaPlugin` subclass) contains almost no logic. Its `onEnable()` creates
a `KreiscraftBootstrap` and calls `bootstrap.start()`; `onDisable()` calls `bootstrap.stop()`.

`KreiscraftBootstrap` is the composition root — the single place where every object is
instantiated and every dependency is wired. The full wiring sequence in `start()` is:

1. Build `YamlConfigService` (reads `config.yml`).
2. Load `PlayerPreferencesData` and build the always-available `PlayerPreferencesService`.
3. Build `ConfigurateMessageService`, loading every language tree and resolving each recipient's persisted language through the preferences service.
4. Build `BukkitScheduler`, `EventBus`, `PaperCommandRegistrar`, `ServiceRegistry`.
5. Publish global services (`MessageService`, `ConfigService`, `PlaceholderResolver`, `PlayerPreferencesService`) to the registry.
6. Construct platform-layer objects and publish them.
7. Construct shared-layer objects (`UserService`, `ChatBridge`, `GuiRegistry`, etc.) and publish them.
8. Create `ModuleRegistry`, register all 23 module instances, call `enableAll()`.
9. Start `AutoSaveTask`.
10. Register the `/kreiscraft reload` command.

`stop()` does the inverse: close all GUIs, stop auto-save, disable all modules in reverse order,
and unregister all Bukkit event listeners.

The benefit of this pattern is that there is no static mutable state anywhere in the plugin. Every
dependency relationship is visible in a single file. If you want to understand what a module
receives, you trace from the call site in `KreiscraftBootstrap.start()` to the module's
`enable(ModuleContext)` method. Nothing is hidden behind a service locator singleton or a
`@Inject` annotation that requires reading framework documentation.

---

## The Runtime Module System

`ModuleRegistry` activates a module if and only if `modules.<id>.enabled: true` appears in
`config.yml`.

### Registration and enable ordering

After all 23 module instances are registered with `ModuleRegistry.register()`, a single call to
`enableAll()` processes them:

1. Iterate all registered modules; collect those with `enabled: true` into a candidate list.
2. Call `topologicalSort(candidates)`, which uses depth-first search to produce an order where
   every module appears after all its declared dependencies. If a dependency is not enabled or
   does not exist, a `ModuleException` is thrown immediately with a clear message. If the
   dependency graph contains a cycle, the DFS detects the back-edge and also throws
   `ModuleException`.
3. Call `m.enable(contextFactory.apply(m))` for each module in sorted order. Failures are caught
   per-module, logged at `SEVERE`, and counted; other modules continue enabling.
4. Return a `StartupSummary` record with counts of enabled, disabled, and failed modules.

Disabling runs in the reverse of the enable order (`enabledInOrder` iterated from the end), so a
module's dependencies are always still running when that module shuts down.

### Why topological sort rather than a flat list

A flat, hand-ordered list would work in practice but would be fragile: the correct ordering would
be an implicit convention rather than an enforced constraint. Topological sort makes the
dependency contract machine-checked. It also makes it safe to add a new module that declares
`dependsOn("combat")` without auditing the entire registration order in `KreiscraftBootstrap`.

### Startup summary

The `StartupSummary` record is immediately broadcast as an in-game message via the message service
so that admins see at login which modules are active, disabled, or failed.

---

## `ModuleContext` as a Dependency Bag

Every module's `enable()` method receives a single `ModuleContext` record:

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

The context is constructed by the factory lambda in `KreiscraftBootstrap`:

```java
m -> new ModuleContext(plugin, config.module(m.id()), messages, services, scheduler, commands, events)
```

`config.module(m.id())` returns the `modules.<id>` subtree of the root config, so each module
sees only its own configuration slice. This prevents accidental cross-module config reading.

Passing the context as a record rather than letting modules call static singletons has two
consequences. First, teardown is clean: when `disable()` is called, the module holds no global
references that need to be nulled out elsewhere. Second, tests can construct a `ModuleContext`
with mock or stub implementations of each service and call `enable()` directly — no Paper server
required for the majority of unit tests.

---

## `ServiceRegistry` as a Lightweight DI Container

`ServiceRegistry` is a `ConcurrentHashMap<Class<?>, Object>` with three methods:

```java
void publish(Class<T> type, T impl)   // register a service
T    get(Class<T> type)               // retrieve or throw ServiceNotFoundException
Optional<T> find(Class<T> type)       // retrieve without throwing
```

Modules that expose functionality to other modules call `ctx.services().publish(...)` at the end
of their `enable()` method. For example, `StatusModule` publishes `StatusService`:

```java
ctx.services().publish(StatusService.class, service);
```

A dependent module — `ChatFormatModule`, which declares `dependsOn(List.of("status"))` — then
retrieves it:

```java
StatusService statusService = ctx.services().get(StatusService.class);
```

Because topological sort guarantees that `status` has completed `enable()` before `chat-format`
starts, `get()` never throws due to ordering. It throws only if `status` is not enabled at all,
which would have been caught at sort time.

This pattern provides service-lookup decoupling without a full DI framework. The registry is
keyed by interface type, so the consuming module never depends on the implementing class.

---

## `EventBus` and Clean Teardown

`EventBus` wraps the Bukkit `PluginManager` with a tracking list:

```java
public void register(Listener listener) {
    plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    registered.add(listener);
}

public void unregisterAll() {
    for (Listener l : registered)
        HandlerList.unregisterAll(l);
    registered.clear();
}
```

Without this, Bukkit would keep listener references alive indefinitely in its global handler
lists. A module that registers three listeners would need to call `HandlerList.unregisterAll()`
for each of them in its `disable()` method, which is boilerplate that would be easy to forget.
By routing all registrations through `EventBus`, modules do not need to track their own
listeners. When `KreiscraftBootstrap.stop()` calls `events.unregisterAll()`, every listener
registered by any module during the plugin's lifetime is removed in one operation.

Modules that need to unregister independently (e.g. the `api` module stopping Javalin) still call
their own teardown logic in `disable()`; `EventBus` covers Bukkit event listeners specifically.

---

## `ChatBridge` for Module Decoupling

The `api` module needs to forward every formatted chat line to connected WebSocket clients. The
`chat-format` module produces those lines. A naive approach would be to add `api` to
`chat-format`'s `dependsOn()` list and pass an `ApiServer` reference directly. That would mean
`chat-format` cannot be enabled without `api` also being enabled — an unacceptable coupling for
a server that might not run the web API.

`ChatBridge` breaks this coupling. It is a thread-safe pub/sub bus built on a
`CopyOnWriteArrayList`:

- `chat-format` calls `chatBridge.publish(event)` after rendering each chat line.
- `api` calls `chatBridge.subscribe(event -> ...)` during its `enable()`.
- Neither module holds a reference to the other.

Both modules receive the shared `ChatBridge` instance via `ctx.services().get(ChatBridge.class)`.
Because `ChatBridge` lives in the shared layer (constructed in `KreiscraftBootstrap` before any
modules start), it is always available regardless of which modules are enabled. If `api` is
disabled, no subscriber is registered and published events are silently dropped.

This pattern is the right tool when two independently optional modules have a one-way data flow
between them. It costs a small amount of indirection but preserves full opt-in independence.

---

## `DataFile<T>` and the Separation of Config vs State

`config.yml` is the writable Server Configuration file. It holds admin-managed settings and the
operator-owned choices that in-game commands write, such as the spawn and jail locations, the
update-mode flag, and start-sequence completion (see ADR-10). Player and gameplay **Activity
Data** — jail entries and restore locations, saved positions, Bedrock identities and pre-login
authorizations, statuses, anti-cheat logs, and player settings — changes during normal server
operation and is persisted separately.

`DataFile<T>` is an abstract base class for YAML-backed Activity Data:

```java
public abstract class DataFile<T> {
    public void load() throws IOException { ... }       // initial load from disk
    public T get() { return state; }                    // read current state
    public void update(Consumer<T> mutator) {           // mutate + mark dirty
        mutator.accept(state);
        dirty = true;
    }
    public void flushIfDirty() throws IOException { ... } // write only if changed
}
```

A concrete subclass (e.g. `JailData`, `HackLogData`, `PositionsData`) implements three abstract
methods: `load(ConfigurationNode)`, `save(T, ConfigurationNode)`, and `empty()`. The `dirty`
flag means that disk I/O only happens when something actually changed, which keeps the periodic
save cycle cheap.

Data files are saved by `AutoSaveTask`, a repeating scheduler task created in `KreiscraftBootstrap`
with an interval controlled by `core.auto-save-interval` in `config.yml` (default 5 minutes).
Each module registers its data file with `ConfigService.register(dataFile)` during `enable()`,
and `AutoSaveTask` calls `flushIfDirty()` on all registered files at every tick. When the plugin
shuts down, `autoSave.stop()` cancels the task and then calls `flushAll()` synchronously to
ensure no dirty state is lost on server stop.

---

## Config Hot-Reload Flow

When an admin runs `/kreiscraft reload`, `ReloadCommand` executes three steps in order:

```java
cfg.reload();                          // re-read config.yml from disk
messages.reload();                     // re-read every language tree from disk
modules.reloadConfigs(cfg.root());     // dispatch to each enabled module
```

`ModuleRegistry.reloadConfigs()` iterates `enabledInOrder` and calls
`m.onConfigReload(newRoot.node("modules", m.id()))` for each module. The default implementation
of `onConfigReload` in the `Module` interface is a no-op; modules opt in by overriding it.

Modules that need live config propagation hold the current config in an `AtomicReference` and
give listeners a `Supplier<XxxConfig>`. This makes the new value visible without re-registering
listeners. See [Reload Configuration](/how-to/reload-configuration/#thread-safe-propagation-with-atomicreference)
for the implementation pattern.

---

## Layer Relationships and Module Lifecycle (ASCII Diagram)

```
 onEnable()
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      KreiscraftBootstrap                        │
│                       (Composition Root)                        │
│                                                                 │
│  1. Build core infrastructure                                   │
│     ConfigService · MessageService · Scheduler                  │
│     EventBus · CommandRegistrar · ServiceRegistry               │
│                                                                 │
│  2. Build platform layer                                        │
│     PaperPlatform · NmsWhitelist · OfflineInventoryReader       │
│     PlayerBrandProvider                                         │
│                                                                 │
│  3. Build shared layer                                          │
│     UserService · ChatBridge · GuiRegistry · WhitelistService   │
│     (all published to ServiceRegistry)                          │
│                                                                 │
│  4. ModuleRegistry.enableAll()                                  │
│     ┌───────────────────────────────────────────────┐           │
│     │   read modules.<id>.enabled from config.yml   │           │
│     │              ↓                                │           │
│     │      topologicalSort(candidates)              │           │
│     │              ↓                                │           │
│     │   for each module (dependency-first order):   │           │
│     │     ModuleContext ctx = contextFactory(m)     │           │
│     │     m.enable(ctx)                             │           │
│     │       ├─ ctx.events().register(listener)      │           │
│     │       ├─ ctx.commands().register(node)        │           │
│     │       ├─ ctx.services().publish(Svc, impl)    │           │
│     │       └─ ctx.services().get(OtherSvc)         │           │
│     └───────────────────────────────────────────────┘           │
│                                                                 │
│  5. Start AutoSaveTask                                          │
│  6. Register /kreiscraft reload                                 │
└─────────────────────────────────────────────────────────────────┘
    │
    │  onDisable()
    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      KreiscraftBootstrap.stop()                 │
│  GuiRegistry.closeAll()                                         │
│  AutoSaveTask.stop()  (flushes dirty DataFiles)                 │
│  ModuleRegistry.disableAll()  (reverse enable order)            │
│    └─ m.disable() for each module                               │
│  EventBus.unregisterAll()  (remove all Bukkit listeners)        │
└─────────────────────────────────────────────────────────────────┘
```

```
Module dependency graph example (subset):

  status ◄── chat-format ──► ChatBridge ◄── api
                                             │
                                          WebSocket
                                            clients

  PlayerPreferencesService ◄── combat
                          ◄── clickthrough
                          ◄── settings-gui (GUI client)

`status` must be enabled before `chat-format` starts (declared via `dependsOn`). `api` and
`chat-format` share `ChatBridge` without any declared dependency on each other — the pub/sub bus
is the only connection. `PlayerPreferencesService` is published by the composition root, so
`combat`, `clickthrough`, and `settings-gui` consume it without depending on one another.

The composition root also publishes a `ModuleEnablement` view backed by the `ModuleRegistry`.
`settings-gui` uses it to omit controls whose owning module is disabled, so the GUI presents only
features the running server actually offers, while still taking no declared dependency on those
modules.
