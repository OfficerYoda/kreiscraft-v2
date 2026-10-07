package de.kreiscraft.modules.bedrock;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.bedrock.commands.BedrockListCommand;
import de.kreiscraft.shared.whitelist.WhitelistService;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class BedrockModule implements Module {

    private BedrockConfig config;
    private DefaultBedrockService service;
    private BedrockData data;
    private BedrockAuthorizationData authorizationData;

    @Override
    public String id() {
        return "bedrock";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(BedrockConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        this.data = new BedrockData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load bedrock player data", e);
        }
        this.authorizationData =
                new BedrockAuthorizationData(ctx.plugin().getDataFolder().toPath());
        try {
            authorizationData.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load bedrock authorization data", e);
        }

        ConfigService configService = ctx.services().get(ConfigService.class);
        configService.register(data);
        configService.register(authorizationData);

        WhitelistService whitelist = ctx.services().get(WhitelistService.class);

        this.service = new DefaultBedrockService(
                data, authorizationData, config, whitelist, ctx.plugin().getLogger());
        ctx.services().publish(BedrockService.class, service);

        ctx.events().register(new BedrockListener(service, ctx.scheduler()));

        ctx.commands().register(
                BedrockListCommand.build(service, ctx.messages()),
                "Bedrock player whitelist management");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(BedrockConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        if (service != null) {
            service.updateConfig(config);
        }
    }
}
