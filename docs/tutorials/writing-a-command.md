---
title: Writing a Command
---

This tutorial adds a `/greet <player>` command to the `greet` module from the previous tutorial. When run, it sends the configured greeting to the named player and confirms to the sender. By the end you will understand how Paper's Brigadier integration works, how `CommandRegistrar` wraps it, and how to add a permission.

---

## How Brigadier commands work in Paper

Paper exposes Brigadier through the `LifecycleEvents.COMMANDS` lifecycle event. At that point you hand Mojang's `CommandDispatcher` a tree of `CommandNode` objects. Each `LiteralCommandNode` corresponds to one word in a command path; `ArgumentCommandNode` nodes consume user input.

The entry point for every command is a `LiteralCommandNode<CommandSourceStack>`. `CommandSourceStack` carries the sender and, for commands run from an entity, the entity's location. You get a `CommandSender` with `ctx.getSource().getSender()`.

You never touch the `CommandDispatcher` directly. `PaperCommandRegistrar` registers a lifecycle handler once in its constructor and queues nodes in a `pending` list. When `LifecycleEvents.COMMANDS` fires, all queued nodes are registered in bulk. This means you can call `ctx.commands().register(...)` at any point during module `enable()` without worrying about timing.

---

## 1. Create the command class

Create `src/main/java/de/kreiscraft/modules/greet/commands/GreetCommand.java`:

```java
package de.kreiscraft.modules.greet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.greet.GreetConfig;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GreetCommand {

    private GreetCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            GreetConfig config, MessageService messages, Server server) {
        return Commands.literal("greet")
                .requires(src -> src.getSender().hasPermission("kreiscraft.greet.use"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");
                            Player target = server.getPlayer(targetName);
                            if (target == null) {
                                messages.send(sender, "greet.target-not-found",
                                        "player", targetName);
                                return Command.SINGLE_SUCCESS;
                            }
                            messages.send(target, "greet.join", "message", config.message());
                            messages.send(sender, "greet.sent", "player", target.getName());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
```

Walk through the structure:

- `Commands.literal("greet")` — the root node that matches the literal string `greet`.
- `.requires(src -> ...)` — a predicate evaluated before the command tree is shown in tab-completion and before execution. Returning `false` hides the command entirely from that sender.
- `.then(Commands.argument("player", StringArgumentType.word()))` — appends a child node that consumes exactly one word as a string. The registered name `"player"` is the key you use when reading the value back with `StringArgumentType.getString(ctx, "player")`.
- `.executes(ctx -> { ... return Command.SINGLE_SUCCESS; })` — the executor attached to this node. `Command.SINGLE_SUCCESS` (value `1`) is the conventional success return.
- `.build()` — materialises the `LiteralCommandNode`. Without this call the builder returns a `LiteralArgumentBuilder`, not the `LiteralCommandNode` that `CommandRegistrar` expects.

---

## 2. Add the lang keys

Open `src/main/resources/lang/de.yml` and extend the `greet:` block:

```yaml
greet:
  join: "<prefix> <green><message>"
  sent: "<prefix> <green>Begrüßung an <player> gesendet."
  target-not-found: "<prefix> <red>Spieler <player></red> nicht gefunden."
```

Open `src/main/resources/lang/en.yml`:

```yaml
greet:
  join: "<prefix> <green><message>"
  sent: "<prefix> <green>Greeting sent to <player>."
  target-not-found: "<prefix> <red>Player <player></red> not found."
```

---

## 3. Wire the command into the module

Open `GreetModule.java` and update `enable()` to pass `Server` to the command builder:

```java
import de.kreiscraft.modules.greet.commands.GreetCommand;
import org.bukkit.Server;

@Override
public void enable(ModuleContext ctx) {
    try {
        this.config = ctx.moduleConfig().get(GreetConfig.class);
    } catch (SerializationException e) {
        throw new ModuleException("failed to load config for module " + id(), e);
    }
    if (config == null) config = new GreetConfig(null);

    Server server = ctx.plugin().getServer();

    ctx.events().register(new GreetListener(() -> this.config, ctx.messages()));
    ctx.commands().register(
            GreetCommand.build(config, ctx.messages(), server),
            "Send the configured greeting to a player");
}
```

`ctx.commands().register(node, description)` queues the node. Paper's lifecycle machinery picks it up on the next `LifecycleEvents.COMMANDS` event. Commands registered this way are fully mod-managed — they disappear when the plugin is reloaded or disabled without any extra teardown code.

---

## 4. Add the permission to plugin.yml

Open `src/main/resources/plugin.yml` and append to the `permissions:` block:

```yaml
  kreiscraft.greet.use:
    description: Send the configured greeting to a named player
    default: op
```

`default: op` means ops have the permission automatically. Use `default: true` to grant it to all players, or `default: false` to require explicit assignment.

---

## 5. Build and run

```bash
./gradlew build
./gradlew runServer
```

Log in with an op account. In the game chat:

```
/greet Steve
```

Steve (if online) receives the greeting in chat. You see the confirmation.

If Steve is offline:

```
[Kreiscraft] Player Steve not found.
```

Try running `/greet Steve` as a non-op account. The command should not appear in tab-completion and executing it produces no output — the `.requires()` predicate hid it entirely.

---

## How the pieces connect

```
GreetModule.enable()
  └─ ctx.commands().register(GreetCommand.build(...), "...")
        └─ PaperCommandRegistrar queues the LiteralCommandNode
              └─ LifecycleEvents.COMMANDS fires → Paper registers it
                    └─ Player types /greet <tab> or executes /greet Steve
                          └─ GreetCommand executor runs → MessageService.send()
```

The pattern — static `build()` factory taking only the dependencies it needs, returning a `LiteralCommandNode` — keeps commands unit-testable: you can call `build(mockConfig, mockMessages, mockServer)` in a test without a running server.

---

## Going further

- Add a second literal subcommand (e.g. `/greet reload`) by chaining another `.then(Commands.literal("reload").executes(...))` off the root node.
- Use `EntitySelectorArgumentType.players()` instead of `StringArgumentType.word()` to get proper player tab-completion from Paper's built-in argument types.
- Check how `PingCommand` in `modules/admin/commands/` handles the optional-vs-required argument pattern if you need `/greet` to greet the sender themselves and `/greet <player>` to target someone else.
