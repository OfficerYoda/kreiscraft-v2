package de.kreiscraft.core.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCommandRegistrar implements CommandRegistrar {

    private final JavaPlugin plugin;
    private final List<Pending> pending = new ArrayList<>();

    public PaperCommandRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
        LifecycleEventManager<org.bukkit.plugin.Plugin> lifecycle = plugin.getLifecycleManager();
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (Pending p : pending) {
                event.registrar().register(p.node, p.description, List.of(p.aliases));
            }
            pending.clear();
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

    private record Pending(LiteralCommandNode<CommandSourceStack> node, String description, String[] aliases) {
    }
}
