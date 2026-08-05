package de.kreiscraft.core.module;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurationNode;

public record ModuleContext(JavaPlugin plugin,ConfigurationNode moduleConfig,MessageService messages,ServiceRegistry services,Scheduler scheduler,CommandRegistrar commands,EventBus events){}
