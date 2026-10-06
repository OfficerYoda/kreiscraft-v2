package de.kreiscraft.modules.signcolor;

import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class SignColorModule implements Module {

    @Override
    public String id() {
        return "sign-color";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            ctx.moduleConfig().get(SignColorConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        LegacyTextFormatter formatter =
                new LegacyTextFormatter(ctx.services().get(PlaceholderResolver.class));
        ctx.events().register(new SignEditListener(formatter));
        ctx.events().register(new AnvilRenameListener(formatter));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            moduleConfig.get(SignColorConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
    }
}
