package de.kreiscraft.core.message;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ConfigurateMessageService implements MessageService {

    private static final Logger LOGGER =
            Logger.getLogger(ConfigurateMessageService.class.getName());
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Path langFolder;
    private final ClassLoader jarLoader;
    private final String defaultLanguage;
    private final Server server;
    private final Set<String> warnedKeys = new HashSet<>();
    private ConfigurationNode root;

    public ConfigurateMessageService(
            Path dataFolder, ClassLoader jarLoader, String defaultLanguage, Server server)
            throws IOException {
        this.langFolder = dataFolder.resolve("lang");
        this.jarLoader = jarLoader;
        this.defaultLanguage = defaultLanguage;
        this.server = server;
        Files.createDirectories(langFolder);
        copyIfMissing("de.yml");
        copyIfMissing("en.yml");
        reload();
    }

    private void copyIfMissing(String name) throws IOException {
        Path target = langFolder.resolve(name);
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = jarLoader.getResourceAsStream("lang/" + name)) {
            if (in == null) {
                throw new IOException("missing JAR resource lang/" + name);
            }
            Files.copy(in, target);
        }
    }

    @Override
    public void reload() throws IOException {
        Path file = langFolder.resolve(defaultLanguage + ".yml");
        this.root = YamlConfigurationLoader.builder().path(file).build().load();
    }

    @Override
    public Component render(String key, Object... placeholders) {
        ConfigurationNode node = navigate(root, key);
        String raw = node.getString();
        if (raw == null) {
            if (warnedKeys.add(key)) {
                LOGGER.log(Level.WARNING, "missing message key: {0}", key);
            }
            return MM.deserialize("<red>[?" + key + "]</red>");
        }
        TagResolver[] resolvers = buildResolvers(placeholders);
        return MM.deserialize(raw, resolvers);
    }

    private static ConfigurationNode navigate(ConfigurationNode root, String key) {
        String[] parts = key.split("\\.");
        return root.node((Object[]) parts);
    }

    private TagResolver[] buildResolvers(Object[] placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be name/value pairs");
        }
        TagResolver[] out = new TagResolver[(placeholders.length / 2) + 1];
        // add global <prefix>
        String prefix = root.node("prefix").getString("");
        out[0] = Placeholder.parsed("prefix", prefix);
        for (int i = 0; i < placeholders.length; i += 2) {
            String name = (String) placeholders[i];
            Object value = placeholders[i + 1];
            out[1 + (i / 2)] = Placeholder.parsed(name, String.valueOf(value));
        }
        return out;
    }

    @Override
    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(render(key, placeholders));
    }

    @Override
    public void broadcast(String key, Object... placeholders) {
        server.sendMessage(render(key, placeholders));
    }
}
