package de.kreiscraft.core.message;

import java.util.Optional;
import org.bukkit.command.CommandSender;

/**
 * Resolves the locale a message recipient prefers.
 *
 * <p>The resolver only reports values that were persisted for a player. It returns {@link
 * Optional#empty()} when nothing is stored so that {@link MessageService} can fall back to the
 * server language. The core message layer stays independent of the always-available preferences
 * boundary: the composition root adapts the preferences service to this interface.
 */
@FunctionalInterface
public interface RecipientLocaleResolver {

    /** A resolver that never reports a preference: every recipient uses the server language. */
    RecipientLocaleResolver EMPTY = recipient -> Optional.empty();

    /**
     * Returns the canonical locale code persisted for {@code recipient}, or {@link Optional#empty()}
     * when no preference is stored.
     */
    Optional<String> localeCodeFor(CommandSender recipient);
}
