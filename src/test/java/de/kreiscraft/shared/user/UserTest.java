package de.kreiscraft.shared.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class UserTest {

    private ServerMock server;
    private PlayerMock alice;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructor_capturesIdentity() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        User user = new User(alice, now);
        assertThat(user.uuid()).isEqualTo(alice.getUniqueId());
        assertThat(user.name()).isEqualTo("Alice");
        assertThat(user.joinedAt()).isEqualTo(now);
        assertThat(user.player()).isSameAs(alice);
    }

    record Combat(int ticksLeft) {
    }

    record Jail(String reason) {
    }

    @Test
    void attribute_setThenGet_returnsValue() {
        User user = new User(alice, Instant.now());
        user.setAttribute(Combat.class, new Combat(200));
        assertThat(user.attribute(Combat.class)).contains(new Combat(200));
    }

    @Test
    void attribute_notSet_returnsEmpty() {
        User user = new User(alice, Instant.now());
        assertThat(user.attribute(Combat.class)).isEmpty();
    }

    @Test
    void attribute_remove_returnsPreviousValueAndClears() {
        User user = new User(alice, Instant.now());
        user.setAttribute(Jail.class, new Jail("griefing"));
        assertThat(user.removeAttribute(Jail.class)).contains(new Jail("griefing"));
        assertThat(user.attribute(Jail.class)).isEmpty();
    }

    @Test
    void attribute_differentKeys_areIndependent() {
        User user = new User(alice, Instant.now());
        user.setAttribute(Combat.class, new Combat(5));
        user.setAttribute(Jail.class, new Jail("test"));
        assertThat(user.attribute(Combat.class)).contains(new Combat(5));
        assertThat(user.attribute(Jail.class)).contains(new Jail("test"));
    }
}
