package de.kreiscraft.core.config;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.loader.ParsingException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public abstract class DataFile<T> {

    private final Path file;
    private final String relativePath;
    private final YamlConfigurationLoader loader;
    private T state;
    private boolean dirty;

    protected DataFile(Path dataFolder, String relativePath) {
        this.relativePath = relativePath;
        this.file = dataFolder.resolve(relativePath);
        this.loader = YamlConfigurationLoader.builder()
                .path(file)
                .defaultOptions(ConfigurationOptions.defaults()
                        .serializers(KreiscraftSerializers.builder().build()))
                .build();
    }

    public final String relativePath() {
        return relativePath;
    }

    public final void load() throws IOException {
        if (Files.exists(file)) {
            try {
                this.state = load(loader.load());
            } catch (SerializationException e) {
                throw new IOException("failed to load " + relativePath, e);
            } catch (ParsingException e) {
                this.state = onUnreadable(e);
            }
        } else {
            this.state = empty();
        }
        this.dirty = false;
    }

    /**
     * Called when an existing file cannot be parsed. The default rethrows so genuine corruption is
     * surfaced; a subclass may instead return a safe fallback state.
     */
    protected T onUnreadable(IOException failure) throws IOException {
        throw failure;
    }

    public final T get() {
        return state;
    }

    public final void update(Consumer<T> mutator) {
        mutator.accept(state);
        dirty = true;
    }

    public final void flushIfDirty() throws IOException {
        if (!dirty) {
            return;
        }
        Files.createDirectories(file.getParent());
        ConfigurationNode node = loader.createNode();
        try {
            save(state, node);
            loader.save(node);
        } catch (SerializationException e) {
            throw new IOException("failed to save " + relativePath, e);
        }
        dirty = false;
    }

    protected abstract T load(ConfigurationNode node) throws SerializationException;

    protected abstract void save(T state, ConfigurationNode node) throws SerializationException;

    protected abstract T empty();
}
