package de.kreiscraft.modules.settingsgui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.ItemWrapper;

/**
 * Builds the InvUI {@link Gui} pages for the Player Settings menu.
 *
 * <p>The GUI is module-aware: a control is only placed when its owning module is enabled, so a
 * player never sees a setting for a feature the server does not run. Preferences are read and
 * written through {@link SettingsService}; there is no time-based debounce, because InvUI dispatches
 * exactly one click to exactly one item.
 *
 * <p>Layout is intentionally fixed so tests (and players) can rely on stable slots:
 *
 * <pre>
 * Root page (3 rows)                 Effects page (3 rows)
 * slot  8  Server Config             slot 11  Effects on/off
 * slot 10  Click-Through             slot 15  Particle
 * slot 12  Combat display            slot 22  Back
 * slot 14  Effects (opens page)
 * slot 16  Glow
 * slot 26  Language (bottom-right, flag head)
 * </pre>
 */
public final class SettingsGui {

    // German flag head texture (minecraft-heads.com ID 522, texture only, no ad metadata)
    static final String TEXTURE_DE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1"
                    + "cmUvNWU3ODk5YjQ4MDY4NTg2OTdlMjgzZjA4NGQ5MTczZmU0ODc4ODY0NTM3NzQ2MjZiMjRiZDhjZmVjYzc3"
                    + "YjNmIn19fQ==";

    // UK flag head texture (minecraft-heads.com ID 23237, texture only, no ad metadata)
    static final String TEXTURE_EN =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1"
                    + "cmUvODc5ZDk5ZDljNDY0NzRlMjcxM2E3ZTg0YTk1ZTRjZTdlOGZmOGVhNGQxNjQ0MTNhNTkyZTQ0MzVkMmM2"
                    + "ZjlkYyJ9fX0=";

    // ---- Root-page slots (package-private for tests) ----
    static final int SLOT_LANGUAGE = 26;
    static final int SLOT_SERVER_CONFIG = 8;
    static final int SLOT_CLICK_THROUGH = 10;
    static final int SLOT_COMBAT_DISPLAY = 12;
    static final int SLOT_EFFECTS = 14;
    static final int SLOT_GLOW = 16;

    /** Permission that reveals and opens the authorized Server Configuration editor. */
    public static final String SERVER_CONFIG_PERMISSION = "kreiscraft.settingsgui.admin";

    // ---- Effects-page slots ----
    static final int SLOT_EFFECTS_ENABLED = 11;
    static final int SLOT_EFFECTS_PARTICLE = 15;
    static final int SLOT_BACK = 22;

    /** Ordered particle choices cycled by the effects-particle control. */
    static final List<Particle> PARTICLE_OPTIONS = List.of(
            Particle.HAPPY_VILLAGER,
            Particle.FLAME,
            Particle.SOUL_FIRE_FLAME,
            Particle.END_ROD,
            Particle.HEART,
            Particle.CRIT,
            Particle.ENCHANT,
            Particle.CLOUD,
            Particle.NOTE,
            Particle.LAVA,
            Particle.ELECTRIC_SPARK,
            Particle.TOTEM_OF_UNDYING);

    private SettingsGui() {}

    /** A page of the Player Settings menu. */
    public enum Page {
        ROOT("settings-gui.gui-title"), EFFECTS("settings-gui.effects-title"), SERVER_CONFIG("server-config.title");

        private final String titleKey;

        Page(String titleKey) {
            this.titleKey = titleKey;
        }

        /** The language key for this page's inventory title. */
        public String titleKey() {
            return titleKey;
        }
    }

    /** Opens a page for the player; the command wires this to the shared GUI facade. */
    @FunctionalInterface
    public interface Navigator {
        void open(Player player, Page page);
    }

    /** Builds the root settings page for {@code player}. */
    public static Gui buildRoot(
            SettingsService settings,
            ModuleEnablement modules,
            MessageService messages,
            Player player,
            Navigator navigator) {
        return buildRoot(settings, modules, messages, player, navigator, false);
    }

    /**
     * Builds the root settings page, optionally including the authorized Server Configuration
     * entry. The entry is only added when {@code serverConfig} is {@code true}, so an unauthorized
     * player never sees a control for server policy.
     */
    public static Gui buildRoot(
            SettingsService settings,
            ModuleEnablement modules,
            MessageService messages,
            Player player,
            Navigator navigator,
            boolean serverConfig) {
        Gui gui = Gui.empty(9, 3);
        if (SettingsControl.LANGUAGE.isAvailable(modules)) {
            gui.setItem(SLOT_LANGUAGE, languageItem(settings, messages, navigator));
        }
        if (serverConfig) {
            gui.setItem(SLOT_SERVER_CONFIG, serverConfigItem(messages, navigator));
        }
        if (SettingsControl.CLICK_THROUGH.isAvailable(modules)) {
            gui.setItem(SLOT_CLICK_THROUGH, clickThroughItem(settings, messages));
        }
        if (SettingsControl.COMBAT_DISPLAY.isAvailable(modules)) {
            gui.setItem(SLOT_COMBAT_DISPLAY, combatDisplayItem(settings, messages));
        }
        if (SettingsControl.EFFECTS.isAvailable(modules)) {
            gui.setItem(SLOT_EFFECTS, effectsLinkItem(messages, navigator));
        }
        if (SettingsControl.GLOW.isAvailable(modules)) {
            gui.setItem(SLOT_GLOW, glowItem(settings, messages));
        }
        return gui;
    }

    /** Builds the effects page (enabled toggle, particle choice, back). */
    public static Gui buildEffects(
            SettingsService settings, MessageService messages, Navigator navigator) {
        Gui gui = Gui.empty(9, 3);
        gui.setItem(SLOT_EFFECTS_ENABLED, effectsEnabledItem(settings, messages));
        gui.setItem(SLOT_EFFECTS_PARTICLE, effectsParticleItem(settings, messages));
        gui.setItem(SLOT_BACK, backItem(messages, navigator));
        return gui;
    }

    // ------------------------------------------------------------------
    // Item factories
    // ------------------------------------------------------------------

    private static AbstractItem languageItem(
            SettingsService settings, MessageService messages, Navigator navigator) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                PlayerLanguage language = settings.language(viewer.getUniqueId());
                Component name = messages.renderFor(
                        viewer,
                        "settings-gui.language",
                        "language",
                        messages.renderFor(viewer, languageNameKey(language)));
                return new ItemWrapper(flagHead(language, name));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                settings.setLanguage(player.getUniqueId(), nextLanguage(settings.language(player.getUniqueId())));
                navigator.open(player, Page.ROOT);
            }
        };
    }

    /** Creates a textured player-head for the given language using the approved flag textures. */
    static ItemStack flagHead(PlayerLanguage language, Component displayName) {
        String texture = language == PlayerLanguage.DE ? TEXTURE_DE : TEXTURE_EN;
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(
                UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        meta.displayName(displayName.decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static AbstractItem serverConfigItem(MessageService messages, Navigator navigator) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(
                        named(
                                Material.COMMAND_BLOCK,
                                messages.renderFor(viewer, "server-config.entry")));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                navigator.open(player, Page.SERVER_CONFIG);
            }
        };
    }

    private static AbstractItem clickThroughItem(SettingsService settings, MessageService messages) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                boolean on = settings.clickThrough(viewer.getUniqueId());
                String key =
                        on ? "settings-gui.click-through-on" : "settings-gui.click-through-off";
                Material material = on ? Material.LIME_DYE : Material.RED_DYE;
                return new ItemWrapper(named(material, messages.renderFor(viewer, key)));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                UUID id = player.getUniqueId();
                settings.setClickThrough(id, !settings.clickThrough(id));
                notifyWindows();
            }
        };
    }

    private static AbstractItem combatDisplayItem(
            SettingsService settings, MessageService messages) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                CombatDisplay display = settings.combatDisplay(viewer.getUniqueId());
                return new ItemWrapper(named(
                        combatMaterial(display), messages.renderFor(viewer, combatKey(display))));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                UUID id = player.getUniqueId();
                settings.setCombatDisplay(id, nextDisplay(settings.combatDisplay(id)));
                notifyWindows();
            }
        };
    }

    private static AbstractItem effectsLinkItem(MessageService messages, Navigator navigator) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(
                        named(Material.NETHER_STAR, messages.renderFor(viewer, "settings-gui.effects")));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                navigator.open(player, Page.EFFECTS);
            }
        };
    }

    private static AbstractItem glowItem(SettingsService settings, MessageService messages) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                boolean on = settings.glowEnabled(viewer.getUniqueId());
                String key = on ? "settings-gui.glow-on" : "settings-gui.glow-off";
                Material material = on ? Material.GLOW_INK_SAC : Material.INK_SAC;
                return new ItemWrapper(named(material, messages.renderFor(viewer, key)));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                UUID id = player.getUniqueId();
                settings.setGlowEnabled(id, !settings.glowEnabled(id));
                notifyWindows();
            }
        };
    }

    private static AbstractItem effectsEnabledItem(
            SettingsService settings, MessageService messages) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                boolean on = settings.effectsEnabled(viewer.getUniqueId());
                String key =
                        on
                                ? "settings-gui.effects-enabled-on"
                                : "settings-gui.effects-enabled-off";
                Material material = on ? Material.BLAZE_POWDER : Material.GUNPOWDER;
                return new ItemWrapper(named(material, messages.renderFor(viewer, key)));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                UUID id = player.getUniqueId();
                settings.setEffectsEnabled(id, !settings.effectsEnabled(id));
                notifyWindows();
            }
        };
    }

    private static AbstractItem effectsParticleItem(
            SettingsService settings, MessageService messages) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                Particle particle = settings.effectsParticle(viewer.getUniqueId());
                Component name = messages.renderFor(
                        viewer,
                        "settings-gui.effects-particle",
                        "particle",
                        particle.name());
                return new ItemWrapper(named(Material.FIREWORK_STAR, name));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                UUID id = player.getUniqueId();
                settings.setEffectsParticle(id, nextParticle(settings.effectsParticle(id)));
                notifyWindows();
            }
        };
    }

    private static AbstractItem backItem(MessageService messages, Navigator navigator) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(
                        named(Material.ARROW, messages.renderFor(viewer, "settings-gui.back")));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                navigator.open(player, Page.ROOT);
            }
        };
    }

    // ------------------------------------------------------------------
    // Value helpers (package-private for tests)
    // ------------------------------------------------------------------

    static PlayerLanguage nextLanguage(PlayerLanguage current) {
        PlayerLanguage[] values = PlayerLanguage.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    static CombatDisplay nextDisplay(CombatDisplay current) {
        CombatDisplay[] values = CombatDisplay.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    static Particle nextParticle(Particle current) {
        int index = PARTICLE_OPTIONS.indexOf(current);
        if (index < 0) {
            return PARTICLE_OPTIONS.get(0);
        }
        return PARTICLE_OPTIONS.get((index + 1) % PARTICLE_OPTIONS.size());
    }

    private static String languageNameKey(PlayerLanguage language) {
        return switch (language) {
            case DE -> "settings-gui.language-de";
            case EN -> "settings-gui.language-en";
        };
    }

    static String combatKey(CombatDisplay display) {
        return switch (display) {
            case CHAT -> "settings-gui.combat-display-chat";
            case ACTIONBAR -> "settings-gui.combat-display-actionbar";
            case TITLE -> "settings-gui.combat-display-title";
        };
    }

    static Material combatMaterial(CombatDisplay display) {
        return switch (display) {
            case CHAT -> Material.IRON_SWORD;
            case ACTIONBAR -> Material.DIAMOND_SWORD;
            case TITLE -> Material.NETHERITE_SWORD;
        };
    }

    private static ItemStack named(Material material, Component displayName) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(displayName));
        return stack;
    }
}
