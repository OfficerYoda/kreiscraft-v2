---
title: Design Decisions
---

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
- All 23 modules share the same classpath; cross-module utility code does not require published
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

**Status:** Accepted; amended by ADR-10

**Context:**  
Several modules need to persist data that changes at runtime: jailed players, saved positions, and
anti-cheat hack logs. (ADR-10 later moved the operator-owned spawn location, update-mode and
start-sequence state out of data files and into writable `config.yml`.) Storing all runtime data
in `config.yml` would mean that every admin config reload risks overwriting or merging runtime
data, and it would be unclear at a glance which fields are admin-controlled vs server-generated.

**Decision:**  
Each domain that needs persistent runtime state subclasses `DataFile<T>`, which writes to a
dedicated YAML file under `data/` inside the plugin data folder (e.g. `data/jaildata.yml`,
`data/hacklogs.yml`). Modules register their `DataFile` instances with `ConfigService` during
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

ADR-10 narrows this decision: values deliberately owned by an operator belong in writable
`config.yml` even when an in-game command changes them.

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

---

## ADR-9: Four Modules Ship Disabled by Default

**Status:** Superseded by ADR-0001

**Context:**  
`ModuleRegistry` treats a module as active only when `modules.<id>.enabled: true` is present in
`config.yml`, and the bundled template controls what a fresh install looks like. Most modules are
useful on every server, but four are not: `effects`, `firework` and `glow` are cosmetic
player-facing features that a server may not want, while `positions` encodes
server-specific policy (how players navigate). Shipping them
active would silently change gameplay on servers that never asked for them.

**Decision:**  
Ship `effects`, `firework`, `glow` and `positions` with `enabled: false` in the
bundled `config.yml`. Keep their implementations, commands, listeners and services fully
registered in `KreiscraftBootstrap`; enabling the flag is the only step required to activate them.
Document the policy in the Module Catalog, the Configuration reference, and the
enable/disable how-to, and pin it with a configuration test
(`YamlConfigServiceTest.everyModuleSection_hasEnabledKey`).

**Consequences:**  
- A fresh install leaves all four inactive; their commands and listeners are never registered.
- Operators can opt in per module with `modules.<id>.enabled: true`; no code changes are needed.
- The set of default-off modules is explicit in code review: changing it requires editing both
  the bundled config and the configuration test, so the policy cannot drift silently.
- The distinction between "opt-in cosmetic" (`effects`, `firework`, `glow`) and "opt-in policy"
  (`positions`) is documented but not enforced in code.

---

## ADR-10: Writable Server Configuration and Activity Data Boundary

**Status:** Accepted

**Context:**
Administrators need to edit most gameplay configuration through an in-game GUI, and commands such
as `/setspawn` change values that are conceptually operator choices. The previous rule put every
runtime mutation under `data/`, which created duplicate sources such as spawn range in both
`config.yml` and `data/spawn.yml`. Moving all mutable records into `config.yml`, however, would make
reloads capable of overwriting player activity and operational history.

**Decision:**
Make `config.yml` writable through an admin GUI and store operator-owned choices there, including
spawn location, update-mode state, and start-sequence completion. Keep player and gameplay activity
records in dedicated data files: player settings, statuses, saved positions, active jail records,
Bedrock identities, and anti-cheat logs. GUI edits are validated and saved atomically on each
click. A context-aware message shown when the GUI closes offers config reload when useful and
identifies changes that require a full server restart. Existing operator-owned data files are not
migrated; the new config values start from defaults.

**Consequences:**
- `ConfigService` must support validated, atomic writes in addition to reloads.
- Module enablement remains a restart boundary; config reload does not alter module lifecycle.
- Sensitive and low-level infrastructure values remain YAML-only.
- Activity data remains protected from ordinary config reloads.
- The admin GUI is a supported configuration surface, so every exposed field needs validation,
  lifecycle metadata, documentation, and tests.

**Applied keys:** The operator-owned values live at `modules.spawn.location`,
`modules.update-mode.active` and `modules.start-sequence.has-started`. Their write metadata is
declared in `ServerConfigSchema` and commands persist them through `ConfigService.applyEdit`, which
rejects or fails without touching the live tree or the file. The single Spawn Radius is module
configuration (`modules.spawn.spawn-radius`), not operator-owned state. The retired `data/spawn.yml`,
`data/update-mode.yml` and `data/start-sequence.yml` files are no longer read or written, so no
migration occurs and config reloads cannot touch the remaining Activity Data.

---

## ADR-11: Shared Command Suggestion Providers

**Status:** Accepted

**Context:**
Several commands accept the same kinds of argument values: a player name, an entity type, or a
particle name. Before this decision, each command would have had to enumerate players or scan the
entity/particle registries itself, producing near-identical loops in many command classes and
allowing the candidates (and their filtering) to drift between commands. Player lookups also have
two distinct scopes — online only, and online plus known-offline profiles — that commands must pick
deliberately.

**Decision:**
Centralise Brigadier completions in `de.kreiscraft.core.command.SuggestionProviders`. Each factory
returns a `SuggestionProvider<CommandSourceStack>` for one candidate family:

- `onlinePlayers(server, permission)` — every currently-online player name.
- `onlineAndKnownOfflineProfiles(server, permission)` — online names plus offline profiles that
  have played before and still resolve to a name.
- `allAndOnlinePlayers(server, permission)` — the literal `all` alongside every online player name,
  for broadcast-style targets such as `/sudo`.
- `entityTypes(permission)` — every spawnable `EntityType`, by lowercase enum name.
- `particles(permission)` — every `Particle`, by lowercase enum name.

Commands attach a provider with `.suggests(...)` on the argument node and pass the same permission
node their `.requires(...)` predicate uses. Suggestions are filtered case-insensitively against the
partially typed input and emitted in deterministic order.

**Consequences:**
- Commands no longer duplicate profile or registry scans; adding a command that takes a player,
  entity, or particle argument reuses an existing provider.
- A provider emits nothing when the sender lacks its permission, so completions cannot leak a
  candidate that the command would refuse to execute.
- The offline scope is explicit in the provider name and mirrors the command's visibility rule
  (`hasPlayedBefore()`): historical profiles are suggested only by commands that can act on them.
- Registry-backed providers track the server's own `EntityType`/`Particle` enums, so new types are
  suggested automatically after a Minecraft upgrade; test assertions target representative,
  stable members rather than the exact full list.

---

## ADR-12: Validated Atomic Server Configuration Edits

**Status:** Accepted

**Context:**
ADR-10 makes `config.yml` writable through an admin GUI and through commands such as `/setspawn`.
Rewriting the file in place risks a half-written file if the process is interrupted, and
Configurate's YAML backend drops comments and reformats unrelated keys whenever it serializes a
tree. An invalid value must never mutate either the live configuration or the durable file.

**Decision:**
`ConfigService` exposes one validated edit boundary, `edit(ConfigEdit)`. Each edit:

1. applies the proposed value to a detached candidate tree (`root().copy()`);
2. validates the candidate value with the field's `ConfigValidator`;
3. renders the candidate and writes it to a flushed temporary file in the same directory;
4. replaces `config.yml` with an atomic move where the filesystem supports it, falling back to a
   plain replace otherwise;
5. swaps the live tree to the candidate only after the write succeeds.

Rejected values and I/O failures return a `ConfigEditResult` and leave both the live tree and the
file untouched. Because Configurate's YAML backend does not carry comments, the candidate is
rendered through SnakeYAML with comment processing enabled, and comment metadata is copied from
the file being replaced onto the matching nodes of the candidate; this preserves the file header
and comments on unrelated keys. Every editable field declares a `ConfigFieldType`, a
`ConfigValidator`, GUI exposure, and a `ConfigLifecycle` (hot-reload or restart). Module `enabled`
flags are always restart-only.

**Consequences:**
- On a filesystem with atomic renames the durable file can never be observed half-written.
- A rejected or failed edit is a no-op, so the GUI can report the reason and retry safely.
- Every edit rewrites the whole file; edits are operator-driven and infrequent, so this is cheap.
- Comments on unrelated keys survive, and an inline comment on the edited value is carried across.
- New GUI fields must be declared in `ServerConfigSchema`; the edit boundary itself only needs a
  `ConfigField`.

---

## ADR-13: Pending Bedrock Authorization Binds Once

**Status:** Accepted

**Context:**
Bedrock players connect with a configured name prefix (default `.`) and a synthetic UUID that the
Mojang profile service cannot resolve. An administrator may need to admit a Bedrock player before
that player has ever connected, but a name is not an identity: the same name could later present a
different UUID. Treating a pre-approved name as permanently interchangeable would let an identity
silently take over another player's authorization.

**Decision:**
Model pre-login access as a Pending Bedrock Authorization. `/bedrocklist add <name>` stores an
unbound authorization in `data/bedrock-authorizations.yml`, keyed by the normalized name
(configured prefix plus lower-cased name body). On pre-login, the first matching login binds the
authorization permanently to the observed UUID and whitelists that identity. A bound authorization
is never rebound to a different UUID; replacing it requires `/bedrocklist remove <name>` and
re-registration, which starts from a fresh unbound state. Authorization records are Activity Data,
not operator-owned Server Configuration, so ordinary config reloads cannot overwrite them.

**Consequences:**
- Registration and login matching share one normalization step, so the configured prefix and case
  cannot disagree between the two paths.
- A rebound attempt leaves the original binding intact and is logged; it does not admit the new
  UUID.
- Removal revokes the bound whitelist entry, so the explicit lifecycle cannot leave a stale
  identity whitelisted.
- The lifecycle is covered by removal/re-registration tests rather than by an implicit
  overwrite.

---

## ADR-14: Forwarding Aliases via Brigadier Redirect

**Status:** Superseded — `/gm` removed for Paper 26.2 compatibility

**Context:**
Administrative commands should match the syntax and authorization players already know from
vanilla Minecraft. Re-implementing a command such as `/gamemode` — its argument types, its
selector support, its completions, and its op-level requirement — invites drift from vanilla
behaviour and duplicates parsing logic. `/gm` was wanted purely as an alias for the existing
`minecraft:gamemode` command.

**Decision:**
Add `CommandRegistrar.registerRedirect(alias, target, description, aliases...)`. Registration is
deferred to the `LifecycleEvents.COMMANDS` phase, where `Commands#getDispatcher()` exposes the live
server tree together with the command nodes vanilla already registered. The registrar resolves the
`target` label to its node and builds the alias with `RedirectCommand.build(alias, target)`:

```java
Commands.literal(alias).requires(target.getRequirement()).redirect(target).build();
```

The alias copies the target's own requirement predicate and adds a Brigadier redirect into the
target subtree, so a `/gm` invocation parses and executes exactly as `minecraft:gamemode` would.
If the target is absent the registrar logs a warning and skips the alias rather than registering a
broken command.

**Consequences:**
- `/gm` inherited vanilla syntax, selector handling, completions, and authorization without
  duplicated logic.
- The requirement was reused from the target because redirects do not re-apply the target root's
  own predicate.
- Forwarding was generic: any alias could point at a command already in the dispatcher.

**Supersession:**
Paper 26.2 represents vanilla targets as opaque `ShadowBrigNode` instances. Bukkit help-map
initialization traversed the redirect target and threw `UnsupportedOperationException`, crashing
server startup. `/gm`, the generic redirect API, and their tests were therefore removed. Operators
can use vanilla `/gamemode` directly.

---

## ADR-15: Delegate the Start-Sequence Border Expansion to Minecraft

**Status:** Accepted

**Context:**
The old Start Sequence expanded the world border in three plugin-owned phases (two timed expansions
and an instant snap) and stored phase state itself. That produced a hidden final snap and required the
plugin to reason about resume state. A prototype verified that Paper 26.2 build 132 persists a
world-border transition's current size, target, and remaining ticks across a graceful restart and
resumes it after downtime.

**Decision:**
The Start Sequence issues exactly one `WorldBorder.changeSize(target, ticks)` and nothing else. The
border duration is configured and documented in seconds, represented internally as a `Duration`, and
converted to ticks (20 ticks = 1 second) only at the Bukkit boundary. The plugin keeps no phase,
start timestamp, or remaining-duration state. The only persisted value is the operator-owned
`modules.start-sequence.has-started` completion flag, which prevents re-triggering.

**Consequences:**
- Minecraft owns the in-progress transition; a graceful restart resumes it without plugin
  intervention.
- The countdown is a pure presentation loop: it renders the configured value down through `1`, then
  the start title, then delegates the single transition.
- Abrupt process death and cross-version border-state migration are not guaranteed and are
  documented as unsupported.
- Tests verify the delegation contract — one transition call and no competing persistence — under
  MockBukkit rather than automating a real restart.

---

## ADR-16: Version-Isolated Offline Player-Data Access

**Status:** Accepted

**Context:**
Reading an offline player's inventory requires the server's NMS NBT and item-conversion surface,
which is unavailable under MockBukkit's test classpath and differs across Paper versions. The
`invsee` module nevertheless needs to display ordinary inventories, armor, offhand, and Ender Chest
contents, and the same surface must later support committed offline writes. Letting gameplay code
import NMS directly would make the module untestable and entangle it with the version-specific
reader.

**Decision:**
Offline player data is reached through a version-independent `OfflinePlayerData` interface that
returns an immutable-by-copy `OfflinePlayerSnapshot` of ordinary-inventory and Ender Chest slot
items. File access (`OfflinePlayerDataStore`) is plain Java over a player-data directory and a
`PlayerDataCodec`; the NMS decode (`NmsPlayerDataCodec`) and the world-directory lookup
(`PaperPlayerDataPaths`) are the only version-specific classes. The composition root wires the NMS
codec behind the store and publishes only the interface to the service registry.

**Consequences:**
- The file protocol — missing files, reading without writing, and decode failures — is tested
  against real temporary files with a substitute codec; the NMS decode is a documented seam.
- The module never imports NMS and remains fully testable under MockBukkit.
- Offline writes extend the same boundary (ADR-17) without leaking NMS into gameplay code or the
  commands.

---

## ADR-17: Two-Phase Commits for Offline Player-Data Edits

**Status:** Accepted

**Context:**
`/invsee` and `/enderchest` present an offline target's inventory and Ender Chest as an editable
snapshot. Committing those edits must never lose the player's unrelated data, must not race a
concurrent login, and must be recoverable if a write fails. The server may also be writing the same
`<uuid>.dat` when the player joins, so a naive in-place write is unsafe.

**Decision:**
Offline edits are opened as an `OfflinePlayerEditSession` (via `OfflinePlayerDataEditor`) that holds
the decoded `OfflinePlayerSnapshot`, the raw source bytes, and a SHA-256 digest of the source as
opened. Committing is a two-phase protocol:

1. **Stage** — the edited inventory and Ender Chest items are converted to compressed NBT by the
   version-specific `PlayerDataCodec.encode`, which deep-copies the complete source document and
   replaces only `Inventory` and `EnderItems`. The result is written, flushed, and read back through
   the codec before the source is touched.
2. **Commit** — in one server-main-thread turn the session re-checks that the target is still offline
   and that the source digest still matches, creates a byte-for-byte backup, then performs an atomic
   `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. A non-atomic fallback is refused.

The GUI is backed by a detached InvUI `VirtualInventory` (indexed by Bukkit slot), so edits are made
against the snapshot, never the source. Aborts are reported to the viewer: a login collision offers a
clickable reopen of the live inventory, a stale source aborts without overwriting the newer data, and
a write failure reports that the source was left unchanged.

**Consequences:**
- Unrelated root tags and item component metadata survive because `encode` copies the whole source
  document; only the two editable lists are replaced.
- The offline/digest check and the atomic replace run in one main-thread turn, so a join event cannot
  interleave between them.
- The file protocol — staging, read-back verification, backup, abort on login/stale source, and
  atomic replace — is tested against real temporary files with a substitute codec; the NMS
  encode/decode remains a documented seam (ADR-16).
