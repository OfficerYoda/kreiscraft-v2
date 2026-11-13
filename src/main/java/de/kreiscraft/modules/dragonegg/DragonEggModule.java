package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.OfflinePlayerDataEditor;
import java.io.IOException;
import java.time.Duration;
import org.bukkit.Server;
import org.bukkit.event.HandlerList;

public final class DragonEggModule implements Module {
    private Server server;
    private DragonEggData data;
    private DragonEggTracker tracker;
    private DragonEggListener listener;
    private DragonEggOfflineReturn offlineReturn;
    private Scheduler.Task task;
    private Scheduler.Task buffsTask;

    @Override
    public String id() {
        return "dragon-egg";
    }

    @Override
    public void enable(ModuleContext ctx) {
        server = ctx.plugin().getServer();
        data = new DragonEggData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load dragon egg Activity Data", e);
        }
        ctx.services().get(ConfigService.class).register(data);
        tracker = new DragonEggTracker(server, data, System::currentTimeMillis);
        var compass = new DragonEggCompass(server, tracker, ctx.messages(), System::currentTimeMillis);
        listener = new DragonEggListener(server, tracker, compass, ctx.scheduler());
        offlineReturn = new DragonEggOfflineReturn(server, tracker, data,
                ctx.services().get(OfflinePlayerDataEditor.class), ctx.messages(), System::currentTimeMillis,
                ctx.services().get(de.kreiscraft.platform.PaperPlatform.class)::saveAndVerifyDragonEggDrop);
        ctx.events().register(offlineReturn);
        ctx.events().register(listener);
        listener.start();
        try {
            for (var player : server.getOnlinePlayers()) {
                offlineReturn.join(player);
            }
        } catch (IOException e) {
            throw new ModuleException("failed to restore online dragon egg carrier", e);
        }
        var buffs = new DragonEggBuffs(tracker);
        buffsTask = ctx.scheduler().runRepeating(Duration.ofMillis(50), Duration.ofMillis(50),
                () -> server.getOnlinePlayers().forEach(buffs::update));
        ctx.services().publish(DragonEggTracker.class, tracker);
        server.addRecipe(compass.recipe());
        task = ctx.scheduler().runRepeating(Duration.ZERO, Duration.ofSeconds(1), () -> {
            processOfflineReturn();
            tracker.sample();
            server.getOnlinePlayers().forEach(compass::updateInventory);
        });
    }

    private void processOfflineReturn() {
        try {
            offlineReturn.tick();
        } catch (IOException e) {
            throw new ModuleException("failed to return offline dragon egg; custody retained for recovery", e);
        }
    }

    @Override
    public void disable() {
        if (buffsTask != null) {
            buffsTask.cancel();
        }
        if (task != null) {
            task.cancel();
        }
        if (listener != null) {
            listener.stop();
            HandlerList.unregisterAll(listener);
        }
        if (offlineReturn != null) {
            HandlerList.unregisterAll(offlineReturn);
            try {
                for (var player : server.getOnlinePlayers()) {
                    offlineReturn.quit(player);
                }
            } catch (IOException e) {
                throw new ModuleException("failed to persist shutdown dragon egg carrier", e);
            }
        }
        if (server != null) {
            server.removeRecipe(DragonEggCompass.KEY);
        }
        if (tracker != null) {
            tracker.refresh();
        }
        if (data != null && data.get() != null) {
            try {
                data.flushIfDirty();
            } catch (IOException e) {
                throw new ModuleException("failed to save dragon egg Activity Data", e);
            }
        }
    }
}
