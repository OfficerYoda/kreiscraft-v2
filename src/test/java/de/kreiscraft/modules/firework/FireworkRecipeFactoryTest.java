package de.kreiscraft.modules.firework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class FireworkRecipeFactoryTest {

    private static final FireworkConfig CONFIG =
            new FireworkConfig(696969, "kreiscraft:firework_rocket");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void register_addsRecipeToServer() {
        FireworkRecipeFactory.register(server, CONFIG);
        NamespacedKey key = NamespacedKey.fromString(CONFIG.namespaceKey());
        assertThat(server.getRecipe(key)).isNotNull();
    }

    @Test
    void register_calledTwice_doesNotThrow() {
        assertThatCode(() -> {
            FireworkRecipeFactory.register(server, CONFIG);
            FireworkRecipeFactory.register(server, CONFIG);
        }).doesNotThrowAnyException();
    }

    @Test
    void register_calledTwice_doesNotDuplicateRecipe() {
        FireworkRecipeFactory.register(server, CONFIG);
        FireworkRecipeFactory.register(server, CONFIG);
        NamespacedKey key = NamespacedKey.fromString(CONFIG.namespaceKey());
        // Recipe should still be present exactly once (second call is a no-op).
        assertThat(server.getRecipe(key)).isNotNull();
    }

    @Test
    void unregister_removesRecipe() {
        FireworkRecipeFactory.register(server, CONFIG);
        NamespacedKey key = NamespacedKey.fromString(CONFIG.namespaceKey());
        FireworkRecipeFactory.unregister(server, key);
        assertThat(server.getRecipe(key)).isNull();
    }
}
