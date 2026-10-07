package de.kreiscraft.core.command;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCommandRegistrar implements CommandRegistrar {

    private final JavaPlugin plugin;
    private final List<Pending> pending = new ArrayList<>();
    private final List<Redirect> redirects = new ArrayList<>();

    public PaperCommandRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
        LifecycleEventManager<org.bukkit.plugin.Plugin> lifecycle = plugin.getLifecycleManager();
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (Pending p : pending) {
                event.registrar().register(p.node, p.description, List.of(p.aliases));
            }
            pending.clear();
            for (Redirect r : redirects) {
                CommandNode<CommandSourceStack> target =
                        resolve(event.registrar().getDispatcher().getRoot(), r.target);
                if (target == null) {
                    plugin.getLogger()
                            .warning(
                                    "cannot register /"
                                            + r.alias
                                            + ": forwarding target /"
                                            + r.target
                                            + " is not present in the server dispatcher");
                    continue;
                }
                event.registrar()
                        .register(
                                RedirectCommand.build(r.alias, target),
                                r.description,
                                List.of(r.aliases));
            }
            redirects.clear();
        });
    }

    @Override
    public void register(LiteralCommandNode<CommandSourceStack> node, String description) {
        register(node, description, new String[0]);
    }

    @Override
    public void register(LiteralCommandNode<CommandSourceStack> node, String description, String... aliases) {
        pending.add(new Pending(node, description, aliases));
    }

    @Override
    public void registerRedirect(String alias, String target, String description, String... aliases) {
        redirects.add(new Redirect(alias, target, description, aliases));
    }

    /**
     * Resolves a forwarding target by its plain label, falling back to the {@code minecraft:}
     * namespaced form so vanilla commands resolve regardless of how the server labels them.
     */
    private static CommandNode<CommandSourceStack> resolve(
            com.mojang.brigadier.tree.RootCommandNode<CommandSourceStack> root, String label) {
        CommandNode<CommandSourceStack> target = root.getChild(label);
        if (target == null) {
            target = root.getChild("minecraft:" + label);
        }
        return target;
    }

    private record Pending(LiteralCommandNode<CommandSourceStack> node, String description, String[] aliases) {
    }

    private record Redirect(String alias, String target, String description, String[] aliases) {
    }
}
