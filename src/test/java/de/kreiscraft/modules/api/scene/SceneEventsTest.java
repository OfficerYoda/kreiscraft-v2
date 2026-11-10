package de.kreiscraft.modules.api.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SceneEventsTest {
    private ServerMock server;
    private PlayerMock player;
    private MockScheduler scheduler;
    private SceneEvents scene;
    private final List<SceneEvents.Message> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world").setTime(12000);
        player = server.addPlayer("Alice");
        scheduler = new MockScheduler();
        scene = new SceneEvents(server, scheduler);
        scene.start(messages::add);
    }

    @AfterEach
    void tearDown() {
        scene.stop();
        MockBukkit.unmock();
    }

    @Test
    void snapshotsIncludeHiddenPlayersAndFractionalSharedState() throws Exception {
        player.setHealth(6.5);
        player.setAbsorptionAmount(4);
        player.setInvisible(true);
        player.setLevel(31);
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        var snapshot = scene.snapshot();
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(snapshot));
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(Instant.parse(json.get("occurredAt").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(json.at("/data/overworldTimeTicks").asLong()).isEqualTo(12000);
        assertThat(json.at("/data/players/0/playerId").asText()).isEqualTo(player.getUniqueId().toString());
        assertThat(json.at("/data/players/0/isInvisible").asBoolean()).isTrue();
        assertThat(json.at("/data/players/0/isPumpkinWorn").asBoolean()).isTrue();
        assertThat(json.at("/data/players/0/health").asDouble()).isEqualTo(6.5);
        assertThat(json.at("/data/players/0/maxHealth").asDouble()).isEqualTo(20);
        assertThat(json.at("/data/players/0/absorption").asDouble()).isEqualTo(4);
        assertThat(json.at("/data/players/0/effects/0").asText()).isEqualTo("poison");
        assertThat(json.at("/data/players/0/level").asInt()).isEqualTo(31);
        scheduler.advance(Duration.ofSeconds(30));
        assertThat(types()).contains("scene_snapshot");
    }

    @Test
    void fireThresholdPumpkinAndVisibilityChangesAreTransitionsNotRepeatedEvents() throws Exception {
        player.setHealth(6.5);
        player.setFireTicks(200);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(jsonOf("fire_state_changed")).contains("\"state\":\"onfire\"");
        messages.clear();
        scheduler.advance(Duration.ofMillis(50));
        assertThat(messages).isEmpty();
        player.setHealth(6);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(jsonOf("fire_state_changed")).contains("\"state\":\"firepanic\"");
        messages.clear();
        player.setFireTicks(0);
        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).contains("pumpkin_equipped", "player_state_changed");
        assertThat(jsonOf("fire_state_changed")).contains("\"state\":\"none\"");
        messages.clear();
        player.setInvisible(true);
        player.setFireTicks(200);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).containsExactly("player_state_changed");
    }

    @Test
    void pvpUsesFinalDamageAndSuppressesLateCancellationAndInvisibleParticipants() {
        var attacker = server.addPlayer("Bob");
        var hit = hit(attacker, 4);
        scene.onDamage(hit);
        assertThat(messages).isEmpty();
        player.setHealth(16);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).contains("damage", "player_state_changed");
        messages.clear();
        var cancelled = hit(attacker, 10);
        scene.onDamage(cancelled);
        when(cancelled.isCancelled()).thenReturn(true);
        var small = hit(attacker, 3.99);
        scene.onDamage(small);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).doesNotContain("damage");
        messages.clear();
        attacker.setInvisible(true);
        scene.onDamage(hit(attacker, 5));
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).doesNotContain("damage");
    }

    @Test
    void explosionAndConsumptionEventsAreQualifiedAndUseFinalState() throws Exception {
        scene.onDamage(hit(mock(Creeper.class), 1));
        scene.onDamage(hit(mock(TNTPrimed.class), 0.99));
        scene.onDamage(hit(mock(TNTPrimed.class), 1));
        for (Material material : List.of(Material.APPLE, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
                Material.POTION)) {
            var event = mock(PlayerItemConsumeEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getItem()).thenReturn(new ItemStack(material));
            scene.onConsume(event);
        }
        var cancelled = mock(PlayerItemConsumeEvent.class);
        when(cancelled.getPlayer()).thenReturn(player);
        scene.onConsume(cancelled);
        when(cancelled.isCancelled()).thenReturn(true);
        player.setHealth(18);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types().stream().filter("tnt_damage"::equals).count()).isEqualTo(1);
        assertThat(types()).contains("creeper_damage", "potion_drank");
        assertThat(types().stream().filter("food_eaten"::equals).count()).isEqualTo(3);
        assertThat(jsonOf("food_eaten")).contains("\"kind\":\"food\"", "\"health\":18.0");
        assertThat(new ObjectMapper().writeValueAsString(messages).split("\"kind\":\"golden_apple\"", -1)).hasSize(3);
    }

    @Test
    void deathTotemAndLevelMilestonesRespectCancellationAndFinalLevel() {
        var death = mock(PlayerDeathEvent.class);
        when(death.getPlayer()).thenReturn(player);
        scene.onDeath(death);
        when(death.isCancelled()).thenReturn(true);
        var totem = mock(EntityResurrectEvent.class);
        when(totem.getEntity()).thenReturn(player);
        when(totem.getHand()).thenReturn(EquipmentSlot.HAND);
        scene.onResurrect(totem);
        var level = mock(PlayerLevelChangeEvent.class);
        when(level.getPlayer()).thenReturn(player);
        when(level.getOldLevel()).thenReturn(9);
        when(level.getNewLevel()).thenReturn(31);
        player.setLevel(20);
        scene.onLevel(level);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).doesNotContain("death");
        assertThat(types()).contains("totem_used");
        assertThat(types().stream().filter("level_milestone_reached"::equals).count()).isEqualTo(2);
        messages.clear();
        when(death.isCancelled()).thenReturn(false);
        scene.onDeath(death);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(types()).contains("death");
    }

    @Test
    void presenceHidesInvisiblePlayersAndRestartDropsPendingEvents() {
        var join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        scene.onJoin(join);
        assertThat(types()).containsExactly("player_joined");
        messages.clear();
        player.setInvisible(true);
        scene.onJoin(join);
        var quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        scene.onQuit(quit);
        assertThat(messages).isEmpty();
        player.setInvisible(false);
        scene.onJoin(join);
        scene.onQuit(quit);
        assertThat(types()).contains("player_joined", "player_left");
        scene.onDamage(hit(server.addPlayer("Bob"), 5));
        scene.stop();
        messages.clear();
        scene.start(messages::add);
        scheduler.advance(Duration.ofMillis(50));
        assertThat(messages).isEmpty();
    }

    private EntityDamageByEntityEvent hit(org.bukkit.entity.Entity attacker, double damage) {
        var hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getEntity()).thenReturn(player);
        when(hit.getDamager()).thenReturn(attacker);
        when(hit.getFinalDamage()).thenReturn(damage);
        return hit;
    }

    private List<String> types() {
        return messages.stream().map(SceneEvents.Message::type).toList();
    }

    private String jsonOf(String type) throws Exception {
        return new ObjectMapper()
                .writeValueAsString(messages.stream().filter(m -> m.type().equals(type)).findFirst().orElseThrow());
    }
}
