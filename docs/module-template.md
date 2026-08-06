# Module Template

This document is **normative** for every feature module built in Plan 3 and beyond. Parallel subagents authoring modules must produce code that matches this template exactly. The cross-cutting reviewer (`cross-cutting-reviewer` agent) uses this document as the reference when checking for consistency across modules.

If you find yourself wanting to deviate from this template, that is a signal to stop and raise the question with the main reviewer — do not fork the pattern silently.

## Naming

| Thing | Convention | Example |
|---|---|---|
| Module id (config key, log messages) | kebab-case | `chat-format`, `plugin-hider`, `settings-gui` |
| Java package name | module id with dashes stripped | `chatformat`, `pluginhider`, `settingsgui` |
| Module class | `{PascalCasePackageName}Module` | `ChatFormatModule`, `PluginHiderModule` |
| Config record | `{PascalCasePackageName}Config` | `ChatFormatConfig` |
| Service interface | `{Purpose}Service` | `JailService`, `StatusService` |
| Service impl class | `Default{Purpose}Service` | `DefaultJailService` |
| Data file class | `{Purpose}Data` | `JailData`, `StatusData` |
| Listener class | `{Purpose}Listener` | `ChatFormatListener`, `JailListener` |
| Command class | `{Verb}Command` | `JailCommand`, `SetSpawnCommand` |
| Command builder method | `build(...)` returning `LiteralCommandNode<CommandSourceStack>` | see Commands section |

**Do not** use `Manager`, `Handler`, `Util`, or `Helper` in class names. If it manages state, it is a `Service`. If it responds to events, it is a `Listener`. If it holds config, it is a `Config`.

## Directory layout

Every module lives in `src/main/java/de/kreiscraft/modules/<package>/`:

```
modules/<package>/
├── <PascalCase>Module.java         ← required, implements core.module.Module
├── <PascalCase>Config.java         ← required if the module has any config
├── <Purpose>Service.java           ← interface, if the module publishes a service
├── Default<Purpose>Service.java    ← impl of the above
├── <Purpose>Data.java              ← if the module persists per-player state
├── <Purpose>Listener.java          ← if the module registers listeners
└── commands/
    └── <Verb>Command.java          ← every command in its own class
```

Tests mirror this exactly under `src/test/java/de/kreiscraft/modules/<package>/`.

## Required file: `<Name>Module.java`

```java
package de.kreiscraft.modules.<package>;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class <Name>Module implements Module {

    private <Name>Config config;
    private Default<Purpose>Service service;

    @Override
    public String id() { return "<kebab-case-id>"; }

    @Override
    public List<String> dependsOn() { return List.of(/* other module ids, if any */); }

    @Override
    public void enable(ModuleContext ctx) throws SerializationException {
        this.config = ctx.moduleConfig().get(<Name>Config.class);
        // Build service, register with ServiceRegistry:
        this.service = new Default<Purpose>Service(ctx.scheduler(), config, /* deps */);
        ctx.services().publish(<Purpose>Service.class, service);
        // Register listeners:
        ctx.events().register(new <Name>Listener(service, ctx.messages()));
        // Register commands:
        ctx.commands().register(<Verb>Command.build(service, ctx.messages()),
                                "One-line description");
        // Register data files (if any):
        // ctx.services().get(ConfigService.class).register(new <Purpose>Data(...));
    }

    @Override
    public void disable() {
        // Only needed if the service holds resources (open sockets, background
        // tasks) beyond what ctx.events() / ctx.scheduler() cancels for you.
        if (service != null) service.shutdown();
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) throws SerializationException {
        this.config = moduleConfig.get(<Name>Config.class);
        if (service != null) service.updateConfig(config);
    }
}
```

Rules:
- Always `final` class.
- Never store the `ModuleContext` as a field. Extract what you need in `enable()`.
- Never reach into another module's package. Cross-module deps go through `ctx.services().get(...)`.
- If your module registers a listener, use `ctx.events().register(...)` — never `plugin.getServer().getPluginManager().registerEvents(...)` directly. `EventBus` handles cleanup on disable.
- If your module schedules a repeating task, use `ctx.scheduler().runRepeating(...)` — never `Bukkit.getScheduler()...` directly.

## Required file: `<Name>Config.java`

```java
package de.kreiscraft.modules.<package>;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record <Name>Config(
    boolean enabled,
    Duration tagDuration,
    boolean disableElytra
    // …
) {
    // Compact defaults — Configurate applies these when a key is missing from YAML.
    public <Name>Config {
        if (tagDuration == null) tagDuration = Duration.ofSeconds(10);
    }
}
```

Rules:
- Always a `record`, always `@ConfigSerializable`.
- Field names in the record are camelCase; the corresponding YAML keys are kebab-case (Configurate handles the mapping automatically).
- Provide safe defaults in the compact constructor for every non-primitive field, so the module works even with an empty config section.
- Do not put `enabled: boolean` in the record — that flag is handled by `ModuleRegistry` at a higher level. Reading it here does nothing.

## Config section in `config.yml`

Every module owns one section. Add it via Batch C wiring, not from within your module's code:

```yaml
modules:
  <kebab-case-id>:
    enabled: true             # enabled by default; operators opt out with `false`
    tag-duration: 10s
    disable-elytra: true
    # …
```

Rules:
- `enabled: true` is the default for every new module. Operators opt out explicitly by setting it to `false`.
- Every duration is a string (`10s`, `5m`, `20t`) — the `Duration` type serializer handles it.
- Every location is a map (`{ world, x, y, z, yaw, pitch }`) — the `Location` type serializer handles it.
- MiniMessage strings for message content go in `lang/*.yml`, not `config.yml`. `config.yml` holds tunables, not text.

## Message keys in `lang/de.yml` and `lang/en.yml`

Every module owns one namespace matching its id (with dashes → dots):

```yaml
chat-format:
  disabled: "<prefix> <red>Chat formatting ist deaktiviert."

jail:
  jailed: "<prefix> <yellow><player></yellow> wurde eingesperrt."
  released: "<prefix> <green><player></green> wurde freigelassen."
  already-jailed: "<prefix> <red><player></red> ist bereits eingesperrt."
```

Rules:
- Namespace root matches module id.
- Sub-keys are kebab-case, describing the message purpose (not the trigger).
- Every key exists in both `de.yml` and `en.yml`. Adding a key to one without the other is a bug the reviewer will flag.
- Use `<prefix>` at the start of every operator-facing message. Global placeholder handled by `MessageService`.
- Message-specific placeholders: `<player>`, `<amount>`, `<duration>`, etc. — resolved by the caller via `messages.send(sender, "jail.jailed", "player", target.getName())`.

## Service interfaces

If your module offers behavior other modules need, define an interface:

```java
package de.kreiscraft.modules.<package>;

import java.util.UUID;

public interface <Purpose>Service {

    boolean isJailed(UUID playerId);

    void jail(UUID playerId, String reason);

    void release(UUID playerId);
}
```

Then implement it in `Default<Purpose>Service` in the same package. Only the interface goes on the `ServiceRegistry`; consumers get the interface.

Rules:
- Public API on the interface, only.
- Every method takes primitive or platform types (`UUID`, `String`, `Player`, `Location`), never module-specific value objects — those stay package-private in the impl.
- Do not put I/O, scheduling, or listener registration in the interface. Those are implementation details.

## Command classes

Every command is one class with a static `build(...)` method:

```java
package de.kreiscraft.modules.<package>.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.<package>.<Purpose>Service;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

public final class JailCommand {

    private JailCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            JailService service, MessageService messages) {
        return Commands.literal("jail")
            .requires(src -> src.getSender().hasPermission("kreiscraft.jail.use"))
            .then(Commands.argument("target", ArgumentTypes.player())
                .executes(ctx -> {
                    Player target = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                       .resolve(ctx.getSource()).getFirst();
                    service.jail(target.getUniqueId(), "");
                    messages.send(ctx.getSource().getSender(), "jail.jailed",
                                  "player", target.getName());
                    return Command.SINGLE_SUCCESS;
                }))
            .build();
    }
}
```

Rules:
- Class is `final`, constructor is `private`, has one static `build(...)` method returning `LiteralCommandNode<CommandSourceStack>`.
- Every command has a permission node in the form `kreiscraft.<module-id>.<action>`, checked via `.requires(...)`.
- Every command message goes through `MessageService.send(...)`, never `sender.sendMessage(Component.text(...))` directly.
- Errors → messages, then `return Command.SINGLE_SUCCESS`. Only throw when Brigadier requires it (arg resolution failures).
- No business logic in the command class. The command extracts args, calls a service method, and sends a message.

## Listener classes

```java
package de.kreiscraft.modules.<package>;

import de.kreiscraft.core.event.PriorityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JailListener implements PriorityListener {

    private final JailService service;

    public JailListener(JailService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (service.isJailed(event.getPlayer().getUniqueId())) {
            // …
        }
    }
}
```

Rules:
- Class is `final` and implements `PriorityListener`.
- Every field is `private final`.
- Never call `Bukkit.getScheduler()...` from a listener — inject `Scheduler` into the listener's constructor if you need it.
- Never call another module's package directly — inject the service.

## Data files (persistent per-player state)

```java
package de.kreiscraft.modules.<package>;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class JailData extends DataFile<Map<UUID, JailEntry>> {

    public JailData(Path dataFolder) {
        super(dataFolder, "jail.yml");
    }

    @Override
    protected Map<UUID, JailEntry> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, JailEntry> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            UUID id = UUID.fromString(String.valueOf(entry.getKey()));
            map.put(id, entry.getValue().get(JailEntry.class));
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, JailEntry> state, ConfigurationNode node) throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            node.node(entry.getKey().toString()).set(JailEntry.class, entry.getValue());
        }
    }

    @Override
    protected Map<UUID, JailEntry> empty() { return new HashMap<>(); }
}
```

Rules:
- File name is kebab-case matching module id: `jail.yml`, `plugin-hider.yml`, `hacker-log.yml`.
- Register with the ConfigService in `Module.enable()`: `ctx.services().get(ConfigService.class).register(new JailData(ctx.plugin().getDataFolder().toPath()));`
- Never save on every mutation. Call `data.update(m -> m.put(...))` and let `AutoSaveTask` batch writes.
- Values stored in the map are `@ConfigSerializable` records.

## Threading rules

- **Config load, service construction, listener registration** — main thread only (they happen inside `enable()`, which the plugin lifecycle runs on the main thread).
- **Listener bodies** — the thread depends on the event. Async events (`AsyncChatEvent`, `AsyncPlayerPreLoginEvent`) fire off-thread; everything else is main-thread. If you dispatch a Bukkit API call from an async event, use `ctx.scheduler().runOnMain(...)`.
- **HTTP threads (api module only)** — never touch Bukkit API directly; use `Scheduler.runOnMain`.
- **`Scheduler.runRepeating`** — the callback runs on the main thread.

## Tests

Every module has at minimum:

```
src/test/java/de/kreiscraft/modules/<package>/
├── <Name>ConfigTest.java           ← config parses from YAML round-trip
├── Default<Purpose>ServiceTest.java ← service logic (Mockito or MockBukkit)
├── <Name>ListenerTest.java         ← listener response to events (MockBukkit)
└── commands/<Verb>CommandTest.java  ← command dispatch, permission gate, tab completion (MockBukkit)
```

Rules:
- Test class = production class name + `Test` suffix. One-to-one mapping.
- Test method names: `method_whenCondition_expectedResult()`. Given-When-Then in body.
- No `Thread.sleep`. Use `MockScheduler.advance(Duration)` for time.
- Reuse fixtures from `src/test/java/de/kreiscraft/testsupport/` (`MockScheduler`, `PlayerFixtures`, etc.) — do not roll your own player/server mocks.
- One assertion per behavior. Multiple assertions per test are OK if they cover the same behavior.

## Permissions

Every command and every gated operation gets a permission node:

- `kreiscraft.<module-id>.<action>` — the operator-facing action.
- `kreiscraft.<module-id>.bypass` — if the module has a bypass (jail, update-mode).

Declare every permission in the module's Batch C `plugin.yml` addition with a `description` and a sensible `default` (`op` for admin actions, `true` for read-only queries).

## What NOT to do

- **Do not** use `Bukkit.getScheduler()`, `Bukkit.getPluginManager()`, `plugin.getConfig()`, or `plugin.getServer().registerEvents(...)` directly. Everything goes through `ModuleContext`.
- **Do not** hold static state. If it needs to be shared across the module, put it on the `Service` instance.
- **Do not** use `System.out.println` or `e.printStackTrace()`. Log via `plugin.getLogger()` or (for tests) rely on assertion failures.
- **Do not** use `org.bukkit.ChatColor` or legacy color codes. MiniMessage only, via `MessageService`.
- **Do not** hardcode player names, coordinates, or numeric magic values. If it's tunable, put it in `Config`. If it's not, add a code comment explaining why.
- **Do not** implement `Listener` on a command class or vice versa. One class, one role.
- **Do not** cross the module boundary. If module A needs something from module B, module B publishes a service.
- **Do not** register commands or listeners outside `Module.enable()`.
- **Do not** invent new patterns not covered here. Ask instead.

## When in doubt

If you're a subagent building a module and you hit a situation this template doesn't cover, **stop and flag it in your task result**. Do not make up a pattern — the cross-cutting reviewer will catch and reject inventive interpretations. Consistency across 24 modules is the entire point.
