package de.kreiscraft.core.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public interface CommandRegistrar {

    void register(LiteralCommandNode<CommandSourceStack> node, String description);

    void register(LiteralCommandNode<CommandSourceStack> node, String description, String... aliases);

    /**
     * Registers {@code alias} as a redirect to an existing command already present in the server
     * dispatcher, inheriting that command's syntax, completions, and authorization. Resolution is
     * deferred to command-registration time, when the active dispatcher is available.
     *
     * @param alias the new command name
     * @param target the existing command label to forward to (for example {@code gamemode})
     * @param description the help description shown for the alias
     * @param aliases additional names for the alias
     */
    void registerRedirect(String alias, String target, String description, String... aliases);
}
