package de.kreiscraft.core.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface CommandRegistrar {

    void register(LiteralCommandNode<CommandSourceStack> node, String description);

    void register(LiteralCommandNode<CommandSourceStack> node, String description, String... aliases);

}
