package de.kreiscraft.modules.sit;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.modules.sit.commands.SitCommand;

public final class SitModule implements Module {

    private SitService service;

    @Override
    public String id() {
        return "sit";
    }

    @Override
    public void enable(ModuleContext ctx) {
        service = new SitService(ctx.messages());
        ctx.events().register(service);
        ctx.commands().register(SitCommand.build(service, ctx.messages()), "Sit down or stand up");
    }

    @Override
    public void disable() {
        if (service != null) {
            service.standAll();
        }
    }
}
