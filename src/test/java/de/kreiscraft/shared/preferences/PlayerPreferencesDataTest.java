package de.kreiscraft.shared.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerPreferencesDataTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void missingFile_loadsAsEmpty(@TempDir Path tmp) throws IOException {
        PlayerPreferencesData data = new PlayerPreferencesData(tmp);
        data.load();

        assertThat(data.get()).isEmpty();
    }

    @Test
    void roundTrip_persistsEveryPreferenceAndEveryCombatDisplay(@TempDir Path tmp)
            throws IOException {
        PlayerPreferencesData data = new PlayerPreferencesData(tmp);
        data.load();
        DefaultPlayerPreferencesService service = new DefaultPlayerPreferencesService(data);

        service.setLanguage(ALICE, PlayerLanguage.EN);
        service.setClickThrough(ALICE, false);
        service.setEffectsEnabled(ALICE, true);
        service.setEffectsParticle(ALICE, Particle.FLAME);
        service.setGlowEnabled(ALICE, true);

        Map<UUID, CombatDisplay> displays = new HashMap<>();
        for (CombatDisplay display : CombatDisplay.values()) {
            UUID id = UUID.randomUUID();
            displays.put(id, display);
            service.setCombatDisplay(id, display);
        }

        data.flushIfDirty();

        PlayerPreferencesData reloaded = new PlayerPreferencesData(tmp);
        reloaded.load();
        DefaultPlayerPreferencesService afterReconnect =
                new DefaultPlayerPreferencesService(reloaded);

        PlayerPreferences alice = afterReconnect.preferences(ALICE);
        assertThat(alice.language()).contains(PlayerLanguage.EN);
        assertThat(alice.clickThrough()).contains(false);
        assertThat(alice.effectsEnabled()).contains(true);
        assertThat(alice.effectsParticle()).contains(Particle.FLAME);
        assertThat(alice.glowEnabled()).contains(true);

        for (Map.Entry<UUID, CombatDisplay> entry : displays.entrySet()) {
            assertThat(afterReconnect.preferences(entry.getKey()).combatDisplay())
                    .as("combat display %s must survive a reconnect", entry.getValue())
                    .contains(entry.getValue());
        }
    }

    @Test
    void language_isPersistedAsCanonicalLowerCaseCode(@TempDir Path tmp) throws IOException {
        PlayerPreferencesData data = new PlayerPreferencesData(tmp);
        data.load();
        new DefaultPlayerPreferencesService(data).setLanguage(ALICE, PlayerLanguage.DE);
        data.flushIfDirty();

        String raw = Files.readString(tmp.resolve("data/settings.yml"));
        assertThat(raw).contains("language: de");
        assertThat(raw).doesNotContain("language: DE");
    }

    @Test
    void malformedAndUnknownStoredValues_areDiscardedAndNotMigrated(@TempDir Path tmp)
            throws IOException {
        Path file = tmp.resolve("data/settings.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, ALICE + ":\n"
                + "  click-through: false\n"
                + "  language: fr\n"
                + "  combat-display: LEGACY_ORB\n"
                + "  effects-particle: NOT_A_PARTICLE\n"
                + "  glow-enabled: true\n");

        PlayerPreferencesData data = new PlayerPreferencesData(tmp);
        data.load();

        PlayerPreferences stored = data.get().get(ALICE);
        assertThat(stored).isNotNull();
        assertThat(stored.clickThrough()).contains(false);
        assertThat(stored.language()).isEmpty();
        assertThat(stored.combatDisplay()).isEmpty();
        assertThat(stored.effectsParticle()).isEmpty();
        assertThat(stored.glowEnabled()).contains(true);

        // Reading the file must not repair or rewrite the malformed values.
        assertThat(Files.readString(file)).contains("LEGACY_ORB", "NOT_A_PARTICLE");
    }

    @Test
    void legacyTaggedCombatDisplayFile_isAbandonedNotMigrated(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("data/settings.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, ALICE + ":\n"
                + "  click-through: false\n"
                + "  combat-display: !!de.kreiscraft.modules.settingsgui.CombatDisplay 'CHAT'\n");

        PlayerPreferencesData data = new PlayerPreferencesData(tmp);
        // Must not throw: recovery of legacy !!CombatDisplay data is explicitly out of scope.
        data.load();

        assertThat(data.get()).isEmpty();
    }

    @Test
    void unknownPlayer_hasNoStoredPreferences(@TempDir Path tmp) throws IOException {
        PlayerPreferencesData data = new PlayerPreferencesData(tmp);
        data.load();

        assertThat(data.get().get(BOB)).isNull();
    }
}
