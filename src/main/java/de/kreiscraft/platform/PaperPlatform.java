package de.kreiscraft.platform;

import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dependency-injection holder for the platform layer. Every other
 * {@code platform/*} class takes an instance of this record instead of
 * calling {@code Bukkit.getServer()} directly, so tests can substitute a
 * mock and production code never touches static Bukkit state.
 */
public record PaperPlatform(JavaPlugin plugin, Server server) {
}
