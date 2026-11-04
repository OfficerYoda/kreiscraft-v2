package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleRegistry;
import de.kreiscraft.modules.admin.AdminModule;
import de.kreiscraft.modules.api.ApiModule;
import de.kreiscraft.modules.anticheat.AntiCheatModule;
import de.kreiscraft.modules.bedrock.BedrockModule;
import de.kreiscraft.modules.chatformat.ChatFormatModule;
import de.kreiscraft.modules.clickthrough.ClickthroughModule;
import de.kreiscraft.modules.combat.CombatModule;
import de.kreiscraft.modules.effects.EffectsModule;
import de.kreiscraft.modules.firework.FireworkModule;
import de.kreiscraft.modules.glow.GlowModule;
import de.kreiscraft.modules.invsee.InvseeModule;
import de.kreiscraft.modules.jail.JailModule;
import de.kreiscraft.modules.positions.PositionsModule;
import de.kreiscraft.modules.reboot.RebootModule;
import de.kreiscraft.modules.settingsgui.SettingsGuiModule;
import de.kreiscraft.modules.signcolor.SignColorModule;
import de.kreiscraft.modules.sit.SitModule;
import de.kreiscraft.modules.spawn.SpawnModule;
import de.kreiscraft.modules.startsequence.StartSequenceModule;
import de.kreiscraft.modules.status.StatusModule;
import de.kreiscraft.modules.tablist.TablistModule;
import de.kreiscraft.modules.updatemode.UpdateModeModule;
import de.kreiscraft.modules.wither.WitherModule;
import de.kreiscraft.modules.worldmgmt.WorldMgmtModule;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

class KreiscraftBootstrapTest {

    /** Mirror of KreiscraftBootstrap.start() — sole source of truth for the registered set. */
    private static final int MODULE_COUNT = 24;

    private static List<Module> allModules() {
        return List.of(
                new AdminModule(),
                new ApiModule(),
                new AntiCheatModule(),
                new BedrockModule(),
                new ChatFormatModule(),
                new ClickthroughModule(),
                new CombatModule(),
                new EffectsModule(),
                new FireworkModule(),
                new GlowModule(),
                new InvseeModule(),
                new JailModule(),
                new PositionsModule(),
                new RebootModule(),
                new SettingsGuiModule(),
                new SignColorModule(),
                new SitModule(),
                new SpawnModule(),
                new StartSequenceModule(),
                new StatusModule(),
                new TablistModule(),
                new UpdateModeModule(),
                new WitherModule(),
                new WorldMgmtModule());
    }

    @Test
    void moduleRegistry_registersAllModulesWithoutDuplicates() {
        // Mirrors KreiscraftBootstrap.start(): build a registry, register every module,
        // verify no duplicates and the expected total count.
        ModuleRegistry registry =
                new ModuleRegistry(
                        m -> {
                            throw new UnsupportedOperationException(
                                    "enable() not invoked in this smoke test");
                        },
                        CommentedConfigurationNode.root(),
                        Logger.getLogger("test"));

        for (Module m : allModules()) {
            registry.register(m);
        }

        assertThat(registry.registeredIds()).hasSize(MODULE_COUNT);
    }

    @Test
    void everyRegisteredModule_hasKebabCaseId() {
        // Every module id must be non-blank and match the kebab-case pattern enforced by the
        // module template. This also verifies that every module has a working no-arg constructor.
        for (Module m : allModules()) {
            assertThat(m.id())
                    .as("module id for %s", m.getClass().getSimpleName())
                    .isNotBlank()
                    .matches("^[a-z]+(-[a-z]+)*$");
        }
    }
}
