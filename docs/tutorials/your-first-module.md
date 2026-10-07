---
title: Your First Module
---

This tutorial walks you through building a complete `greet` module from scratch. The module sends a configurable welcome message to every player when they join. By the end you will have a working module with its own config record, event listener, and lang keys, wired into the plugin's startup cycle.

A module is a cohesive runtime feature inside the Kreiscraft plugin. It owns its configuration,
listeners, commands, and state, but may interact with other modules through declared dependencies,
shared services, and events. For example, `chat-format` depends on `status`, while `combat` and
`clickthrough` read the shared, always-available `PlayerPreferencesService`. See
[Core Concepts](/explanation/architecture/#core-concepts) for the surrounding terminology.

The patterns here match what you will find in existing modules such as `GlowModule` and `FireworkModule`. Read those alongside this tutorial to see the same ideas applied to different problems.

---

## What you will build

```
modules/greet/
├── GreetConfig.java
├── GreetListener.java
└── GreetModule.java
```

---

## 1. Create the module class

Create `src/main/java/de/kreiscraft/modules/greet/GreetModule.java`:

```java
package de.kreiscraft.modules.greet;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class GreetModule implements Module {

    private GreetConfig config;

    @Override
    public String id() {
        return "greet";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(GreetConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        if (config == null) config = new GreetConfig(null);

        ctx.events().register(new GreetListener(() -> this.config, ctx.messages()));
    }

    @Override
    public void disable() {
        // EventBus.unregisterAll() handles listener teardown automatically.
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            GreetConfig reloaded = moduleConfig.get(GreetConfig.class);
            this.config = reloaded != null ? reloaded : new GreetConfig(null);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
```

Key points:

- `id()` returns `"greet"` — this string must match the key under `modules:` in `config.yml`.
- `dependsOn()` returns an empty list. If your module needed another module to be active first, you would list its id here.
- Config is parsed in `enable()` using Configurate's `get(Class)`. A compact constructor on the config record supplies defaults, so `null` is only returned when the entire `modules.greet` node is absent.
- `onConfigReload()` is called by `ModuleRegistry.reloadConfigs()` after `/kreiscraft reload`. Keep it in sync with `enable()`.

---

## 2. Create the config record

Create `src/main/java/de/kreiscraft/modules/greet/GreetConfig.java`:

```java
package de.kreiscraft.modules.greet;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record GreetConfig(String message) {

    public GreetConfig {
        if (message == null) message = "Willkommen auf Kreiscraft!";
    }
}
```

`@ConfigSerializable` tells Configurate how to map YAML keys to record components. The compact constructor (the body of the record's single constructor without a parameter list) runs after deserialization, so any missing key gets the default value rather than `null`.

---

## 3. Add the config entry

Open `src/main/resources/config.yml` and add a block under `modules:`:

```yaml
  greet:
    enabled: true
    message: "Willkommen auf Kreiscraft!"
```

`enabled` is read by `ModuleRegistry.enableAll()`. Without it (or with `enabled: false`) the module is skipped at startup. The `message` field overrides the default from `GreetConfig`'s compact constructor.

---

## 4. Create the listener

Create `src/main/java/de/kreiscraft/modules/greet/GreetListener.java`:

```java
package de.kreiscraft.modules.greet;

import de.kreiscraft.core.message.MessageService;
import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class GreetListener implements Listener {

    private final Supplier<GreetConfig> config;
    private final MessageService messages;

    public GreetListener(Supplier<GreetConfig> config, MessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        messages.send(event.getPlayer(), "greet.join", "message", config.get().message());
    }
}
```

`messages.send(sender, key, placeholders...)` resolves `key` from the active lang file, substitutes placeholder tokens, and dispatches the MiniMessage-parsed component to the sender. Placeholders are interleaved key–value pairs: `"message", config.get().message()` replaces `<message>` in the template.

The listener receives a `Supplier<GreetConfig>` rather than the config object itself. This way, when `/kreiscraft reload` replaces the module's config, the listener reads the latest value on the next event instead of holding a stale reference.

---

## 5. Add the lang keys

Open `src/main/resources/lang/de.yml` and append:

```yaml
greet:
  join: "<prefix> <green><message>"
```

Open `src/main/resources/lang/en.yml` and append the same:

```yaml
greet:
  join: "<prefix> <green><message>"
```

`<prefix>` is a built-in alias defined at the top of each lang file as `"<gray>[<gold>Kreiscraft</gold>]</gray>"`. It is resolved automatically by `ConfigurateMessageService`. `<message>` is the placeholder you pass from `GreetListener`.

---

## 6. Register the module

Open `KreiscraftBootstrap.java` and add one line in `start()` alongside the other `modules.register(...)` calls:

```java
modules.register(new de.kreiscraft.modules.greet.GreetModule());
```

Placement does not matter for correctness — `ModuleRegistry` performs a topological sort on `dependsOn()` before enabling. Alphabetical order by id is the convention used in the existing list.

---

## 7. Build and test

```bash
./gradlew build
./gradlew runServer
```

Connect with a Minecraft client. You should see the join message in chat:

```
[Kreiscraft] Willkommen auf Kreiscraft!
```

To verify live reload works, change `message` in `config.yml`, then run `/kreiscraft reload` in-game or in the server console. Rejoin (or use another account) to see the updated message. No server restart is required.

---

## What happened under the hood

When the server starts, `ModuleRegistry.enableAll()` checks `modules.greet.enabled` in the config root. Finding `true`, it calls `GreetModule.enable(ctx)` where `ctx` is a `ModuleContext` pre-scoped to the `modules.greet` node. `EventBus.register()` delegates to Bukkit's `PluginManager.registerEvents()` and stores a reference for clean teardown when the plugin disables.

Continue with [Writing a Command](/tutorials/writing-a-command/) to extend the `greet` module with a `/greet <player>` command.
