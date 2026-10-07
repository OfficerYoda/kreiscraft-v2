package de.kreiscraft.core.message;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * MiniMessage-backed {@link MessageService} that loads every bundled language tree and renders each
 * recipient in the language they persisted.
 *
 * <p>Recipient preference takes precedence over the server language ({@code core.language}). An
 * unknown stored locale, or a locale with no matching tree, warns once and falls back to the server
 * language so malformed data never blanks a player's output. Broadcasts render per online player so
 * each recipient sees their own language; the console receives the server language.
 */
public final class ConfigurateMessageService implements MessageService {

    private static final Logger LOGGER =
            Logger.getLogger(ConfigurateMessageService.class.getName());
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Path langFolder;
    private final ClassLoader jarLoader;
    private final String defaultLanguage;
    private final Server server;
    private final RecipientLocaleResolver localeResolver;
    private final Set<String> warnedKeys = new HashSet<>();
    private final Set<String> warnedLocales = new HashSet<>();
    private final Map<String, ConfigurationNode> trees = new HashMap<>();

    public ConfigurateMessageService(
            Path dataFolder, ClassLoader jarLoader, String defaultLanguage, Server server)
            throws IOException {
        this(dataFolder, jarLoader, defaultLanguage, server, RecipientLocaleResolver.EMPTY);
    }

    public ConfigurateMessageService(
            Path dataFolder,
            ClassLoader jarLoader,
            String defaultLanguage,
            Server server,
            RecipientLocaleResolver localeResolver)
            throws IOException {
        this.langFolder = dataFolder.resolve("lang");
        this.jarLoader = jarLoader;
        this.defaultLanguage = defaultLanguage;
        this.server = server;
        this.localeResolver = localeResolver;
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
        trees.clear();
        for (Path file : listLanguageFiles()) {
            String name = file.getFileName().toString();
            String code = name.substring(0, name.length() - ".yml".length()).toLowerCase(Locale.ROOT);
            trees.put(code, YamlConfigurationLoader.builder().path(file).build().load());
        }
        if (treeForLocale(defaultLanguage) == null && !trees.isEmpty()) {
            LOGGER.log(
                    Level.WARNING,
                    "no language file for server language ''{0}''; available: {1}",
                    new Object[] {defaultLanguage, trees.keySet()});
        }
    }

    private List<Path> listLanguageFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(langFolder)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    private ConfigurationNode treeForLocale(String code) {
        if (code == null) {
            return null;
        }
        return trees.get(code.toLowerCase(Locale.ROOT));
    }

    /** The server-language tree, used whenever no recipient preference applies. */
    private ConfigurationNode serverTree() {
        ConfigurationNode tree = treeForLocale(defaultLanguage);
        if (tree == null && !trees.isEmpty()) {
            tree = trees.values().iterator().next();
        }
        return tree;
    }

    @Override
    public Component render(String key, Object... placeholders) {
        return renderFrom(serverTree(), key, placeholders);
    }

    @Override
    public Component renderFor(CommandSender recipient, String key, Object... placeholders) {
        String code = localeResolver.localeCodeFor(recipient).orElse(null);
        ConfigurationNode tree = treeForLocale(code);
        if (code != null && tree == null) {
            if (warnedLocales.add(code)) {
                LOGGER.log(
                        Level.WARNING,
                        "stored language ''{0}'' has no language file; falling back to ''{1}''",
                        new Object[] {code, defaultLanguage});
            }
        }
        return renderFrom(tree != null ? tree : serverTree(), key, placeholders);
    }

    private Component renderFrom(ConfigurationNode root, String key, Object... placeholders) {
        if (root == null) {
            return MM.deserialize("<red>[?" + key + "]</red>");
        }
        ConfigurationNode node = navigate(root, key);
        String raw = node.getString();
        if (raw == null) {
            if (warnedKeys.add(key)) {
                LOGGER.log(Level.WARNING, "missing message key: {0}", key);
            }
            return MM.deserialize("<red>[?" + key + "]</red>");
        }
        TagResolver[] resolvers = buildResolvers(root, placeholders);
        return MM.deserialize(raw, resolvers);
    }

    private static ConfigurationNode navigate(ConfigurationNode root, String key) {
        String[] parts = key.split("\\.");
        return root.node((Object[]) parts);
    }

    private static TagResolver[] buildResolvers(ConfigurationNode root, Object[] placeholders) {
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
        sender.sendMessage(renderFor(sender, key, placeholders));
    }

    @Override
    public void broadcast(String key, Object... placeholders) {
        for (Player player : server.getOnlinePlayers()) {
            player.sendMessage(renderFor(player, key, placeholders));
        }
        CommandSender console = server.getConsoleSender();
        if (console != null) {
            console.sendMessage(render(key, placeholders));
        }
    }
}
