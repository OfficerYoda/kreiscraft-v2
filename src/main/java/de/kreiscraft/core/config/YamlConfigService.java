package de.kreiscraft.core.config;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class YamlConfigService implements ConfigService {

    private final Path configFile;
    private final YamlConfigurationLoader loader;
    private final List<DataFile<?>> dataFiles = new ArrayList<>();
    private ConfigurationNode root;

    public YamlConfigService(Path dataFolder, ClassLoader jarResourceLoader) throws IOException {
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
        this.loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .defaultOptions(ConfigurationOptions.defaults()
                        .serializers(KreiscraftSerializers.builder().build()))
                .build();
        reload();
    }

    @Override
    public ConfigurationNode root() {
        return root;
    }

    @Override
    public void reload() throws IOException {
        this.root = loader.load();
    }

    @Override
    public void register(DataFile<?> dataFile) {
        dataFiles.add(dataFile);
    }

    @Override
    public List<DataFile<?>> registeredDataFiles() {
        return Collections.unmodifiableList(dataFiles);
    }
}
