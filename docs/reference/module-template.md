# Module Template

Copy-paste scaffold for a new Kreiscraft module. Replace every occurrence of `Xxx` / `xxx` with your module name (PascalCase / kebab-case respectively). Remove sections you do not need.

---

## 1. `XxxModule.java`

```java
package de.kreiscraft.modules.xxx;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.xxx.commands.XxxCommand;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class XxxModule implements Module {

    // Store config and service as fields so onConfigReload() can update them.
    private XxxConfig config;
    private XxxService service; // remove if no service interface

    @Override
    public String id() {
        // Must match the key under `modules:` in config.yml.
        return "xxx";
    }

    @Override
    public List<String> dependsOn() {
        // List ids of modules that must be enabled before this one.
        // Remove or return List.of() if there are no dependencies.
        return List.of(/* "other-module" */);
    }

    @Override
    public void enable(ModuleContext ctx) {
        // 1. Deserialize config. Wrap SerializationException in ModuleException
        //    to abort startup cleanly.
        try {
            this.config = ctx.moduleConfig().get(XxxConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        if (config == null) {
            config = new XxxConfig(/* safe defaults */);
        }

        // 2. Load persistent data (if any).
        XxxData data = new XxxData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load xxx data", e);
        }
        // Register with ConfigService so AutoSaveTask flushes it periodically.
        ctx.services().get(ConfigService.class).register(data);

        // 3. Build and publish your service (so other modules can depend on it).
        this.service = new DefaultXxxService(data, config);
        ctx.services().publish(XxxService.class, service);

        // 4. Register event listeners.
        ctx.events().register(new XxxListener(service));

        // 5. Register commands (MUST happen inside enable() — Brigadier lifecycle).
        ctx.commands().register(
            XxxCommand.build(service, ctx.messages()),
            "Short description shown in /help"
        );
    }

    @Override
    public void disable() {
        // Release resources: cancel tasks, close connections, etc.
        // The EventBus already unregisters all listeners automatically.
        // The AutoSaveTask calls flushIfDirty() on DataFiles before this runs.
        if (service != null) {
            service.shutdown(); // only if your service has cleanup logic
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        // Re-deserialize config and propagate to live objects.
        try {
            this.config = moduleConfig.get(XxxConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
        if (config == null) {
            config = new XxxConfig(/* safe defaults */);
        }
        if (service != null) {
            service.updateConfig(config); // only if your service caches config
        }
    }
}
```

---

## 2. `XxxConfig.java`

```java
package de.kreiscraft.modules.xxx;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Configurate-serializable config record for the xxx module.
 * Fields map to YAML keys under modules.xxx in config.yml.
 * The `enabled` flag is read by ModuleRegistry directly from the config node —
 * do not include it in the record.
 * Primitive defaults (0, false, null) mean "absent from YAML" —
 * apply safe defaults in the compact constructor.
 */
@ConfigSerializable
public record XxxConfig(int exampleValue) {

    public XxxConfig {
        // Treat 0 as "not configured"; apply a sensible default.
        if (exampleValue == 0) exampleValue = 42;
    }
}
```

Nested config records (for sub-sections in YAML) must also be annotated `@ConfigSerializable`:

```java
@ConfigSerializable
public record SubConfig(boolean active, double multiplier) {
    public SubConfig {
        if (multiplier == 0.0) multiplier = 1.0;
    }
}
```

---

## 3. `XxxData.java`

```java
package de.kreiscraft.modules.xxx;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists xxx module state to data/xxx.yml.
 * State type is XxxState — a mutable container so update() works in place.
 */
public final class XxxData extends DataFile<XxxData.XxxState> {

    public XxxData(Path dataFolder) {
        super(dataFolder, "data/xxx.yml");
    }

    @Override
    protected XxxState load(ConfigurationNode node) throws SerializationException {
        // Deserialize your state from the node.
        // Example: a map of UUID -> boolean stored under "entries".
        Map<UUID, Boolean> entries = new HashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> e
                : node.node("entries").childrenMap().entrySet()) {
            UUID id = UUID.fromString(e.getKey().toString());
            entries.put(id, e.getValue().getBoolean(false));
        }
        return new XxxState(entries);
    }

    @Override
    protected void save(XxxState state, ConfigurationNode node) throws SerializationException {
        for (Map.Entry<UUID, Boolean> e : state.entries().entrySet()) {
            node.node("entries", e.getKey().toString()).set(e.getValue());
        }
    }

    @Override
    protected XxxState empty() {
        return new XxxState(new HashMap<>());
    }

    /**
     * Mutable state container.
     * Use a class (not a record) if you need in-place mutation via update().
     */
    public static final class XxxState {
        private final Map<UUID, Boolean> entries;

        public XxxState(Map<UUID, Boolean> entries) {
            this.entries = entries;
        }

        public Map<UUID, Boolean> entries() {
            return entries;
        }
    }
}
```

---

## 4. `XxxListener.java`

```java
package de.kreiscraft.modules.xxx;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Bukkit event listener for the xxx module.
 * Register via ctx.events().register(new XxxListener(service)) in XxxModule.enable().
 * Unregistration is automatic — do not call HandlerList.unregisterAll() manually.
 */
public final class XxxListener implements Listener {

    private final XxxService service;

    public XxxListener(XxxService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Example: notify the service when a player joins.
        service.onPlayerJoin(event.getPlayer());
    }
}
```

Alternatively, implement `de.kreiscraft.core.event.PriorityListener` (a sub-interface of `Listener`) as a documentation marker when a specific priority is important:

```java
public final class XxxListener implements PriorityListener {
    // PriorityListener.priority() default returns NORMAL;
    // the actual dispatch priority is still set via @EventHandler(priority = ...).
}
```

---

## 5. `XxxCommand.java`

```java
package de.kreiscraft.modules.xxx.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.xxx.XxxService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * Brigadier command for the xxx module.
 * Use the static build() factory; never instantiate this class.
 * Register the result in XxxModule.enable():
 *   ctx.commands().register(XxxCommand.build(service, ctx.messages()), "Description");
 */
public final class XxxCommand {

    private XxxCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            XxxService service, MessageService messages) {

        return Commands.literal("xxx")
            .requires(src -> src.getSender().hasPermission("kreiscraft.xxx.use"))
            .executes(ctx -> {
                if (!(ctx.getSource().getSender() instanceof Player player)) {
                    messages.send(ctx.getSource().getSender(), "core.player-only");
                    return Command.SINGLE_SUCCESS;
                }
                service.doSomething(player);
                messages.send(player, "xxx.done");
                return Command.SINGLE_SUCCESS;
            })
            // Add subcommands with .then(Commands.literal("sub").executes(...))
            .build();
    }
}
```

---

## 6. `config.yml` snippet

Add under the `modules:` block in `src/main/resources/config.yml`:

```yaml
modules:
  # ... existing modules ...
  xxx:
    enabled: true
    example-value: 42        # maps to XxxConfig.exampleValue
    # sub:
    #   active: true
    #   multiplier: 1.5
```

---

## 7. `plugin.yml` permissions snippet

Add to `src/main/resources/plugin.yml` under `permissions:`:

```yaml
permissions:
  # ... existing permissions ...
  kreiscraft.xxx.use:
    description: Use the /xxx command
    default: true
  kreiscraft.xxx.admin:
    description: Administer the xxx feature
    default: op
```

---

## 8. Lang key snippets

### `src/main/resources/lang/en.yml`

```yaml
xxx:
  done: "<prefix> <green>Done!"
  error: "<prefix> <red>Something went wrong: <reason>"
  # Add further keys as needed.
```

### `src/main/resources/lang/de.yml`

```yaml
xxx:
  done: "<prefix> <green>Fertig!"
  error: "<prefix> <red>Fehler: <reason>"
```

Lang values use MiniMessage syntax. `<prefix>` expands to the top-level `prefix:` value in the active lang file. Inline placeholders follow the `%token%` convention resolved by `PlaceholderResolver`; module-specific values are supplied as `(name, value)` pairs in the `messages.send()` / `messages.render()` call.

---

## 9. Bootstrap registration

In `KreiscraftBootstrap.java`, add your module to the `modules.register(...)` block in alphabetical order:

```java
// KreiscraftBootstrap.java — inside start(), after the other modules.register() calls
modules.register(new de.kreiscraft.modules.xxx.XxxModule());
```

The `ModuleRegistry` reads `modules.xxx.enabled` from `config.yml` before calling `enable()`. If `enabled: false` (or the key is absent), the module is skipped entirely.
