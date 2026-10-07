package de.kreiscraft.modules.clickthrough;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class ClickthroughModule implements Module {

    /**
     * Module default applied when a player has no stored click-through preference. Click-through is
     * opt-out, so players who have never configured it get the feature.
     */
    static final boolean CLICK_THROUGH_DEFAULT_ENABLED = true;

    @Override
    public String id() {
        return "clickthrough";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            ctx.moduleConfig().get(ClickthroughConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        PlayerPreferencesService preferences = ctx.services().get(PlayerPreferencesService.class);
        ctx.events().register(
                new ClickthroughListener(preferences, CLICK_THROUGH_DEFAULT_ENABLED, ctx.scheduler()));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            moduleConfig.get(ClickthroughConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
