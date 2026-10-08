package de.kreiscraft.testsupport;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.CommandRegistrar;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link CommandRegistrar} that records every registration, name, and alias instead of touching a
 * live dispatcher. Repository-wide assertions use it to observe exactly which command surfaces a
 * module would register.
 */
public final class CapturingCommandRegistrar implements CommandRegistrar {

    private final List<String> names = new ArrayList<>();
    private final List<String> aliases = new ArrayList<>();

    @Override
    public void register(LiteralCommandNode<CommandSourceStack> node, String description) {
        names.add(node.getName());
    }

    @Override
    public void register(
            LiteralCommandNode<CommandSourceStack> node, String description, String... commandsAliases) {
        names.add(node.getName());
        aliases.addAll(List.of(commandsAliases));
    }

    /** Every primary command name registered, in registration order. */
    public Set<String> names() {
        return new LinkedHashSet<>(names);
    }

    /** Every alias registered for any command or redirect, including redirect aliases. */
    public Set<String> aliases() {
        return new LinkedHashSet<>(aliases);
    }

}
