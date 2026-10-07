package de.kreiscraft.modules.jail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.config.YamlConfigService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultJailServiceTest {

    private static YamlConfigService config(Path tmp) throws IOException {
        return new YamlConfigService(tmp, DefaultJailServiceTest.class.getClassLoader());
    }

    private DefaultJailService service(Path tmp) throws IOException {
        JailData data = new JailData(tmp);
        data.load();
        return new DefaultJailService(data, config(tmp));
    }

    @Test
    void isJailed_whenNotJailed_returnsFalse(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        assertThat(svc.isJailed(UUID.randomUUID())).isFalse();
    }

    @Test
    void isJailed_afterJail_returnsTrue(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid = UUID.randomUUID();
        Location loc = mock(Location.class);
        svc.jail(uuid, loc, "test");
        assertThat(svc.isJailed(uuid)).isTrue();
    }

    @Test
    void release_whenJailed_returnsEntry(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid = UUID.randomUUID();
        Location loc = mock(Location.class);
        svc.jail(uuid, loc, "griefing");

        Optional<JailEntry> entry = svc.release(uuid);

        assertThat(entry).isPresent();
        assertThat(entry.get().restoreLocation()).isEqualTo(loc);
        assertThat(entry.get().reason()).isEqualTo("griefing");
    }

    @Test
    void release_whenNotJailed_returnsEmpty(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        assertThat(svc.release(UUID.randomUUID())).isEmpty();
    }

    @Test
    void release_removesPlayerFromJailedSet(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid = UUID.randomUUID();
        svc.jail(uuid, mock(Location.class), "");
        svc.release(uuid);
        assertThat(svc.isJailed(uuid)).isFalse();
    }

    @Test
    void jail_roundTrip_restoreLocationPreserved(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid = UUID.randomUUID();
        Location original = mock(Location.class);
        svc.jail(uuid, original, "reason");

        Optional<JailEntry> released = svc.release(uuid);

        assertThat(released).isPresent();
        assertThat(released.get().restoreLocation()).isSameAs(original);
    }

    @Test
    void allJailed_returnsAllCurrentlyJailed(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        svc.jail(uuid1, mock(Location.class), "");
        svc.jail(uuid2, mock(Location.class), "");

        assertThat(svc.allJailed()).containsKeys(uuid1, uuid2);
    }

    @Test
    void allJailed_afterRelease_doesNotContainReleased(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid = UUID.randomUUID();
        svc.jail(uuid, mock(Location.class), "");
        svc.release(uuid);

        assertThat(svc.allJailed()).doesNotContainKey(uuid);
    }

    @Test
    void jail_entry_hasNonNullJailedAt(@TempDir Path tmp) throws IOException {
        DefaultJailService svc = service(tmp);
        UUID uuid = UUID.randomUUID();
        svc.jail(uuid, mock(Location.class), "");
        assertThat(svc.allJailed().get(uuid).jailedAt()).isNotNull();
    }

    @Test
    void jail_persistsToDisk_reloadedServiceSeesJailedPlayer(@TempDir Path tmp) throws IOException {
        UUID uuid = UUID.randomUUID();
        JailData data = new JailData(tmp);
        data.load();
        new DefaultJailService(data, config(tmp)).jail(uuid, null, "griefing");
        data.flushIfDirty();

        // Reconstruct from disk — simulates a server restart
        JailData reloadedData = new JailData(tmp);
        reloadedData.load();
        DefaultJailService reloaded = new DefaultJailService(reloadedData, config(tmp));
        assertThat(reloaded.isJailed(uuid)).isTrue();
        assertThat(reloaded.allJailed().get(uuid).reason()).isEqualTo("griefing");
    }

    @Test
    void release_persistsToDisk_reloadedServiceDoesNotSeeReleasedPlayer(@TempDir Path tmp)
            throws IOException {
        UUID uuid = UUID.randomUUID();
        JailData data = new JailData(tmp);
        data.load();
        DefaultJailService svc = new DefaultJailService(data, config(tmp));
        svc.jail(uuid, null, "");
        svc.release(uuid);
        data.flushIfDirty();

        JailData reloadedData = new JailData(tmp);
        reloadedData.load();
        assertThat(new DefaultJailService(reloadedData, config(tmp)).isJailed(uuid)).isFalse();
    }
}
