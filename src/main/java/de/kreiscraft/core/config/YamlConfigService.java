package de.kreiscraft.core.config;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Configurate-backed {@link ConfigService}.
 *
 * <p>Reads stay with Configurate (including the project's custom serializers). Durable writes use
 * a detached candidate tree, validate it, render it, flush a temporary file and then replace the
 * live file. Rendering through SnakeYAML lets the writer carry the existing comments and unrelated
 * keys forward, because Configurate's YAML backend discards them.
 */
public final class YamlConfigService implements ConfigService {

    private final Path configFile;
    private final YamlConfigurationLoader loader;
    private final ConfigurationOptions options;
    private final ConfigFileReplacer replacer;
    private final Yaml commentYaml;
    private final List<DataFile<?>> dataFiles = new ArrayList<>();
    private ConfigurationNode root;
    private ServerConfigSchema schema;

    public YamlConfigService(Path dataFolder, ClassLoader jarResourceLoader) throws IOException {
        this(dataFolder, jarResourceLoader, ConfigFileReplacer.ATOMIC_WHEN_SUPPORTED);
    }

    YamlConfigService(Path dataFolder, ClassLoader jarResourceLoader, ConfigFileReplacer replacer)
            throws IOException {
        Files.createDirectories(dataFolder);
        this.configFile = dataFolder.resolve("config.yml");
        if (!Files.exists(configFile)) {
            try (InputStream in = jarResourceLoader.getResourceAsStream("config.yml")) {
                if (in == null) {
                    throw new IOException("no config.yml in JAR resources");
                }
                Files.copy(in, configFile);
            }
        }
        this.replacer = replacer;
        this.options =
                ConfigurationOptions.defaults()
                        .serializers(KreiscraftSerializers.builder().build());
        this.loader =
                YamlConfigurationLoader.builder().path(configFile).defaultOptions(options).build();
        this.commentYaml = commentPreservingYaml();
        reload();
    }

    @Override
    public ConfigurationNode root() {
        return root;
    }

    @Override
    public void reload() throws IOException {
        this.root = loader.load();
        this.schema = ServerConfigSchema.fromRoot(root);
    }

    @Override
    public ServerConfigSchema configSchema() {
        return schema;
    }

    @Override
    public ConfigEditResult edit(ConfigEdit edit) {
        Objects.requireNonNull(edit, "edit");
        ConfigField field = edit.field();
        ConfigurationNode candidate = root.copy();
        ConfigurationNode target = candidate.node(field.pathArray());
        try {
            target.set(edit.value());
        } catch (SerializationException e) {
            return new ConfigEditResult.Rejected(e.getMessage());
        }
        Optional<String> invalid = field.validator().validate(target);
        if (invalid.isPresent()) {
            return new ConfigEditResult.Rejected(invalid.get());
        }
        try {
            persist(candidate);
        } catch (IOException e) {
            return new ConfigEditResult.Failed(e);
        }
        this.root = candidate;
        return new ConfigEditResult.Applied();
    }

    @Override
    public void register(DataFile<?> dataFile) {
        dataFiles.add(dataFile);
    }

    @Override
    public List<DataFile<?>> registeredDataFiles() {
        return Collections.unmodifiableList(dataFiles);
    }

    /**
     * Writes the complete candidate to a flushed temporary file and replaces the live file. The
     * temporary file is removed if the replacement does not happen.
     */
    private void persist(ConfigurationNode candidate) throws IOException {
        Path directory = configFile.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        String original = Files.exists(configFile) ? Files.readString(configFile) : "";
        String rendered = render(candidate, original);
        Path temp = Files.createTempFile(directory, "config-", ".yml.tmp");
        if (Files.exists(configFile)) {
            copyPermissions(configFile, temp);
        }
        boolean replaced = false;
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(rendered);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            replacer.replace(temp, configFile);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /** Renders the candidate, carrying comments from the file it is about to replace. */
    private String render(ConfigurationNode candidate, String original) throws IOException {
        String body = serialize(candidate);
        if (original.isBlank()) {
            return body;
        }
        Node previous = commentYaml.compose(new StringReader(original));
        Node updated = commentYaml.compose(new StringReader(body));
        if (previous == null || updated == null) {
            return body;
        }
        copyComments(previous, updated);
        StringWriter out = new StringWriter();
        commentYaml.serialize(updated, out);
        return out.toString();
    }

    private String serialize(ConfigurationNode candidate) throws IOException {
        StringWriter out = new StringWriter();
        YamlConfigurationLoader fragment =
                YamlConfigurationLoader.builder()
                        .sink(() -> new BufferedWriter(out))
                        .headerMode(HeaderMode.NONE)
                        .nodeStyle(NodeStyle.BLOCK)
                        .indent(2)
                        .defaultOptions(options)
                        .build();
        try {
            fragment.save(candidate);
        } catch (ConfigurateException e) {
            throw new IOException("failed to render configuration", e);
        }
        return out.toString();
    }

    /**
     * Copies comment metadata from {@code from} onto the matching nodes of {@code to}. Nodes are
     * matched by mapping key or sequence index, so comments on unrelated keys survive a value edit.
     */
    private static void copyComments(Node from, Node to) {
        to.setBlockComments(from.getBlockComments());
        to.setInLineComments(from.getInLineComments());
        to.setEndComments(from.getEndComments());
        if (from instanceof MappingNode fromMap && to instanceof MappingNode toMap) {
            for (NodeTuple toTuple : toMap.getValue()) {
                for (NodeTuple fromTuple : fromMap.getValue()) {
                    if (sameKey(fromTuple.getKeyNode(), toTuple.getKeyNode())) {
                        copyComments(fromTuple.getKeyNode(), toTuple.getKeyNode());
                        copyComments(fromTuple.getValueNode(), toTuple.getValueNode());
                        break;
                    }
                }
            }
        } else if (from instanceof SequenceNode fromSeq && to instanceof SequenceNode toSeq) {
            List<Node> fromItems = fromSeq.getValue();
            List<Node> toItems = toSeq.getValue();
            for (int i = 0; i < toItems.size() && i < fromItems.size(); i++) {
                copyComments(fromItems.get(i), toItems.get(i));
            }
        }
    }

    private static boolean sameKey(Node a, Node b) {
        return a instanceof ScalarNode left
                && b instanceof ScalarNode right
                && Objects.equals(left.getValue(), right.getValue());
    }

    /** Best-effort carry of the original file's POSIX permissions onto the temporary file. */
    private static void copyPermissions(Path source, Path target) {
        try {
            Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem or unreadable source: keep the temporary file's defaults.
        }
    }

    private static Yaml commentPreservingYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setProcessComments(true);
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setProcessComments(true);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        return new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions);
    }
}
