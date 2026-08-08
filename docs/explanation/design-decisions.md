# Design Decisions

This document records the significant architectural decisions made for Kreiscraft in the format
of Architecture Decision Records (ADRs). Each ADR captures the context that motivated a choice,
what was actually decided, and the trade-offs that follow from it.

---

## ADR-1: Single Gradle Project with Runtime Modules (not Gradle subprojects)

**Status:** Accepted

**Context:**  
Kreiscraft is a merger of two previously standalone plugins. The natural Gradle-ecosystem answer
to "multiple deployable units" is Gradle subprojects, each producing its own JAR. However, the
combined plugin shares significant infrastructure (core, platform, shared layers) and is always
deployed as a unit on a single server.

**Decision:**  
Use one Gradle module with a single `build.gradle.kts`. Feature toggles are implemented at
runtime through `ModuleRegistry`: a module is active if `modules.<id>.enabled: true` in
`config.yml`. No module can be deployed or versioned independently; there is exactly one
artifact — the shadow JAR.

**Consequences:**  
- The build is simple: one project, one task (`./gradlew shadowJar`), one JAR to copy to the
  server.
- All 25 modules share the same classpath; cross-module utility code does not require published
  library versions.
- Modules cannot have separate release cycles or be installed on third-party servers without the
  full plugin.
- A compile-time error in any module blocks the entire build, which is a positive constraint
  that keeps the codebase uniformly compilable.

---

## ADR-2: No DI Framework

**Status:** Accepted

**Context:**  
Dependency injection frameworks such as Guice or Spring provide automatic wiring, lifecycle
management, and scope support. They reduce boilerplate in large applications. However, they add
significant classpath weight, require relocating their own internals in a shaded JAR, and
introduce "magic" wiring that is invisible to someone reading the code without knowing the
framework.

**Decision:**  
Use manual constructor injection wired in `KreiscraftBootstrap` (the composition root) and a
minimal `ServiceRegistry` service locator for module-to-module service publication. There are no
annotations, no classpath scanning, and no generated proxies.

**Consequences:**  
- Every dependency relationship is explicit in `KreiscraftBootstrap.start()`. A new developer can
  read the wiring in a single file.
- No annotation processing step; compilation is straightforward.
- No framework JAR to relocate and maintain.
- The composition root grows linearly with the number of modules; as of writing it is ~140 lines,
  which is still easily readable.
- Refactoring a dependency (e.g. adding a new service) requires editing `KreiscraftBootstrap`
  and the consuming constructor — slightly more verbose than `@Inject` but entirely explicit.

---

## ADR-3: Paper Brigadier over Legacy Command API

**Status:** Accepted

**Context:**  
Paper exposes two command registration pathways: the legacy `CommandExecutor` / `TabCompleter`
API inherited from Bukkit, and the modern Brigadier API exposed through
`LifecycleEvents.COMMANDS`. The legacy API requires declaring commands in `plugin.yml`, provides
no type-safe argument parsing, and has limited built-in tab-completion support.

**Decision:**  
All commands use the Paper Brigadier API via `PaperCommandRegistrar`, which hooks
`LifecycleEvents.COMMANDS` at construction time and flushes queued `LiteralCommandNode<CommandSourceStack>`
registrations when the lifecycle event fires. Commands are built as static factory methods
returning a `LiteralCommandNode<CommandSourceStack>` using `Commands.literal(...)` from
`io.papermc.paper.command.brigadier`.

**Consequences:**  
- Commands receive rich, type-safe argument nodes. The server handles argument parsing and
  tab-completion natively, including player name completion, coordinate suggestions, etc.
- Permission checks are expressed as `.requires(src -> src.getSender().hasPermission(...))` on
  the node, keeping permission logic close to the command definition.
- The `plugin.yml` `commands:` block is no longer used; commands are self-registering.
- The static factory pattern (each command is a `public static LiteralCommandNode<CommandSourceStack> build(...)`)
  is slightly more verbose than a class implementing `CommandExecutor`, but it makes the command
  tree visible as a plain expression that can be read without instantiating anything.
- Commands cannot be registered after the `COMMANDS` lifecycle event fires. `PaperCommandRegistrar`
  buffers `pending` registrations until the event, which means all `ctx.commands().register(...)`
  calls in module `enable()` methods are effectively delayed — the commands are available to
  players only after the server fully starts, which is the intended behaviour.

---

## ADR-4: Embedded Javalin for the Web API

**Status:** Accepted

**Context:**  
Kreiscraft provides a web frontend that communicates with the server over HTTP REST endpoints and
a WebSocket channel for live chat forwarding. Implementing this requires an HTTP server. Options
considered: a standalone reverse proxy + plugin messaging (complex ops setup), Netty directly
(low-level), or an embedded HTTP framework.

**Decision:**  
Embed Javalin 6 (`io.javalin:javalin:6.1.3`) as a shaded dependency. The shadow JAR task
relocates `io.javalin` → `de.kreiscraft.libs.javalin`, and its transitive dependencies (Jetty,
Jackson, SLF4J) are relocated under the same `de.kreiscraft.libs` base. `ApiServer` wraps
the `Javalin` instance and exposes `start()` / `stop()` methods. `ApiModule.disable()` calls
`server.stop()` to ensure clean shutdown.

**Consequences:**  
- No external infrastructure is required for development; the API is available as soon as the
  plugin loads.
- Relocation prevents classpath conflicts if Paper or another plugin bundles the same libraries
  at a different version.
- The `api` module is optional: if `modules.api.enabled: false`, Javalin is never instantiated
  and no port is bound.
- The bound port must be managed (default configured in `ApiConfig`); two running instances on
  the same host would conflict.
- `ApiModule.onConfigReload()` detects changes to `host`, `port`, or `corsOrigins` and restarts
  the Javalin instance; other config changes (timeouts, intervals) do not cause a restart because
  the sub-services that consume them do not expose an `updateConfig()` method — this is a
  deliberate simplification documented in the `ApiModule` Javadoc.

---

## ADR-5: `DataFile<T>` for Runtime State, Separate from `config.yml`

**Status:** Accepted

**Context:**  
Several modules need to persist data that changes at runtime: the spawn location, jailed players,
saved positions, anti-cheat hack logs, start-sequence state. Storing this in `config.yml` would
mean that every admin config reload risks overwriting or merging runtime data, and it would be
unclear at a glance which fields are admin-controlled vs server-generated.

**Decision:**  
Each domain that needs persistent runtime state subclasses `DataFile<T>`, which writes to a
dedicated YAML file under `data/` inside the plugin data folder (e.g. `data/spawn.yml`,
`data/jaildata.yml`). Modules register their `DataFile` instances with `ConfigService` during
`enable()`. `AutoSaveTask` calls `flushIfDirty()` on all registered files at a configurable
interval (default 5 minutes). On shutdown, `AutoSaveTask.stop()` performs a final flush before
cancelling the repeating task.

**Consequences:**  
- `config.yml` contains only admin-controlled settings. It is safe to replace or edit it without
  worrying about losing runtime data.
- Runtime state files are human-readable YAML that can be inspected or manually corrected if
  needed.
- The dirty flag in `DataFile` avoids writing files that have not changed, reducing I/O on
  low-activity servers.
- Each module is responsible for calling `configService.register(dataFile)` during `enable()`;
  forgetting this means the file is never auto-saved.
- Data files are not hot-reloaded by `/kreiscraft reload` — their contents are managed
  exclusively at runtime, not by admin config changes.

---

## ADR-6: `Supplier<Config>` / `AtomicReference<Config>` for Live Config Propagation

**Status:** Accepted

**Context:**  
Bukkit event listeners are registered once at plugin startup and remain registered for the
lifetime of the module. Modules need their listeners to react to config changes applied by
`/kreiscraft reload` without tearing down and re-creating those listeners.

**Decision:**  
Modules hold config state in an `AtomicReference<XxxConfig>`. The reference is set to the parsed
config in `enable()` and updated in `onConfigReload()`. Listeners receive either the
`AtomicReference` directly or the method reference `configRef::get` (which satisfies
`Supplier<XxxConfig>`). At each event invocation, the listener calls `.get()` on the reference
to read the latest config.

For example, `AntiCheatModule` passes `configRef` to `AntiCheatListener`, and
`ChatFormatModule` passes `configRef::get` to `ChatFormatListener` as a `Supplier`.

**Consequences:**  
- Config changes propagate to active listeners on the next event invocation — no listener
  re-registration required, and no risk of missing events between unregister and re-register.
- The `AtomicReference` ensures visibility across threads (Bukkit events are dispatched on the
  main thread, but some modules schedule async tasks).
- There is a small boilerplate cost: each module that participates in hot-reload declares the
  field and the update in `onConfigReload()`. This is intentional — it is explicit and grep-able.
- Config changes are applied atomically at the reference level. If two event handlers run
  concurrently and one has just seen the old config and another the new, that is acceptable
  because config changes from `/kreiscraft reload` are operationally low-frequency.

---

## ADR-7: Java 25 with `-Xlint:all -Werror`

**Status:** Accepted

**Context:**  
Java 25 is the current LTS-adjacent release at the time of writing and provides virtual threads,
pattern matching, records, sealed classes, and other language improvements that reduce ceremony
in the codebase. Strict compiler settings catch a broad class of bugs at compile time rather than
at runtime.

**Decision:**  
Set `toolchain.languageVersion = 25` in `build.gradle.kts`. Add `-Xlint:all -Werror` to
`compileJava` options so that every compiler warning is a build error.

```kotlin
tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
```

**Consequences:**  
- Language features such as records (`ModuleContext`, `PaperPlatform`, `StartupSummary`,
  `ChatBridgeEvent`) reduce boilerplate significantly compared to equivalent class definitions.
- Compiler warnings about unchecked casts, missing `@Override`, dead code, and similar issues
  become build failures, preventing them from accumulating silently.
- Some legitimate patterns (e.g. using `@SuppressWarnings("unchecked")` at a call site that
  is known-safe) require explicit annotation, which documents that the suppression was deliberate
  rather than overlooked.
- The plugin requires Java 25 on the server JVM; this is not a concern for a private server but
  would limit distribution to servers that have updated.

---

## ADR-8: `ChatBridge` Pub/Sub Between `chat-format` and `api`

**Status:** Accepted

**Context:**  
The `api` module forwards chat messages to connected WebSocket clients so that the web frontend
can display a live chat feed. The formatted chat lines are produced by `chat-format`. Both modules
are independently optional — a server running without the web API should still have formatted
chat, and a server running without the chat-format module should still be able to run the API.
A direct object reference from `chat-format` to `api` (or vice versa) would create a hard
dependency between two modules that have no logical relationship.

**Decision:**  
Place a `ChatBridge` pub/sub bus in the shared layer. `KreiscraftBootstrap` constructs it and
publishes it to `ServiceRegistry`. `chat-format` retrieves it via
`ctx.services().get(ChatBridge.class)` and calls `chatBridge.publish(event)` after rendering each
line. `api` retrieves it similarly and calls `chatBridge.subscribe(event -> ...)` to receive
events.

`ChatBridge` itself is a `CopyOnWriteArrayList` of `Consumer<ChatBridgeEvent>` subscribers.
`subscribe()` returns a `Subscription` handle whose `unsubscribe()` removes the specific
consumer, enabling `api` to clean up during `disable()` without affecting any other subscribers.

**Consequences:**  
- `chat-format` and `api` have no compile-time or runtime module dependency on each other.
  Either can be disabled independently.
- The decoupling means it is easy to add a third module that also subscribes to chat events
  (e.g. a logging module) without modifying `chat-format`.
- Exceptions thrown by individual subscribers are caught and logged; one broken subscriber does
  not prevent others from receiving the event.
- There is a slight indirection cost: instead of a direct method call, there is an iteration
  over the subscriber list. For chat events this is entirely negligible.
- `ChatBridge` is always present in `ServiceRegistry` regardless of which modules are enabled.
  Published events are silently dropped when there are no subscribers, which is the correct
  behaviour when `api` is disabled.
