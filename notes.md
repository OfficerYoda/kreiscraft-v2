# Notes

## /invsee and /ec

autocomplete names; online and offline

make title not this gray

add offline editin

## /setings

always need to click twice

add language toggle with german/britain player head

maybe in inventory view show invalid slots as barrier blocks and not just empty

also move armor bar to the top row if easily possible

in settings.yml it saves e.g.

```yml
dc9315f3-159a-4207-991c-f495ecb91b68:
    {
        click-through: true,
        combat-display: !!de.kreiscraft.modules.settingsgui.CombatDisplay "ACTIONBAR"
    }
```

which my ide shows an error of

`Unresolved tag: tag:yaml.org,2002:de.kreiscraft.modules.settingsgui.CombatDisplay `

this causes to fail the settings module while loading, which inturn causes to
load module clickthrough and combat.

```text
 failed to enable module settings-gui
de.kreiscraft.core.module.ModuleException: failed to load settings data for module settings-gui
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.modules.settingsgui.SettingsGuiModule.enable(SettingsGuiModule.java:42) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.core.module.ModuleRegistry.enableAll(ModuleRegistry.java:59) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.KreiscraftBootstrap.start(KreiscraftBootstrap.java:129) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.Kreiscraft.onEnable(Kreiscraft.java:13) ~[?:?]
        at org.bukkit.plugin.java.JavaPlugin.setEnabled(JavaPlugin.java:279) ~[paper-api-26.2.build.132-stable.jar:?]
        at io.papermc.paper.plugin.manager.PaperPluginInstanceManager.enablePlugin(PaperPluginInstanceManager.java:201) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at io.papermc.paper.plugin.manager.PaperPluginManagerImpl.enablePlugin(PaperPluginManagerImpl.java:109) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at org.bukkit.plugin.SimplePluginManager.enablePlugin(SimplePluginManager.java:519) ~[paper-api-26.2.build.132-stable.jar:?]
        at org.bukkit.craftbukkit.CraftServer.enablePlugin(CraftServer.java:633) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at org.bukkit.craftbukkit.CraftServer.enablePlugins(CraftServer.java:590) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.initPostWorld(MinecraftServer.java:648) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.dedicated.DedicatedServer.initServer(DedicatedServer.java:343) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1266) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]
Caused by: de.kreiscraft.libs.configurate.loader.ParsingException: []: Unknown error occurred while loading
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.loader.AbstractConfigurationLoader.load(AbstractConfigurationLoader.java:165) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.loader.AbstractConfigurationLoader.load(AbstractConfigurationLoader.java:63) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.loader.ConfigurationLoader.load(ConfigurationLoader.java:56) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.core.config.DataFile.load(DataFile.java:38) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.modules.settingsgui.SettingsGuiModule.enable(SettingsGuiModule.java:40) ~[?:?]
        ... 14 more
Caused by: Global tag is not allowed: tag:yaml.org,2002:de.kreiscraft.modules.settingsgui.CombatDisplay
 in 'reader', line 1, column 77:
     ... -through: true, combat-display: !!de.kreiscraft.modules.settings ...
                                         ^

        at org.yaml.snakeyaml.composer.Composer.composeScalarNode(Composer.java:239) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:214) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeValueNode(Composer.java:396) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeMappingChildren(Composer.java:361) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeMappingNode(Composer.java:329) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:218) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeValueNode(Composer.java:396) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeMappingChildren(Composer.java:361) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeMappingNode(Composer.java:329) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.composeNode(Composer.java:218) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.getNode(Composer.java:141) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.composer.Composer.getSingleNode(Composer.java:167) ~[snakeyaml-2.6.jar:?]
        at org.yaml.snakeyaml.constructor.BaseConstructor.getSingleData(BaseConstructor.java:179) ~[snakeyaml-2.6.jar:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.yaml.ConfigurateYaml.loadConfigurate(ConfigurateYaml.java:39) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.yaml.YamlConfigurationLoader.loadInternal(YamlConfigurationLoader.java:158) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.yaml.YamlConfigurationLoader.loadInternal(YamlConfigurationLoader.java:42) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.libs.configurate.loader.AbstractConfigurationLoader.load(AbstractConfigurationLoader.java:155) ~[?:?]
        ... 18 more
[20:08:28 ERROR]: [Kreiscraft] failed to enable module combat
de.kreiscraft.core.service.ServiceNotFoundException: no service registered for de.kreiscraft.modules.settingsgui.SettingsService
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.core.service.ServiceRegistry.get(ServiceRegistry.java:18) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.modules.combat.CombatModule.enable(CombatModule.java:36) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.core.module.ModuleRegistry.enableAll(ModuleRegistry.java:59) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.KreiscraftBootstrap.start(KreiscraftBootstrap.java:129) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.Kreiscraft.onEnable(Kreiscraft.java:13) ~[?:?]
        at org.bukkit.plugin.java.JavaPlugin.setEnabled(JavaPlugin.java:279) ~[paper-api-26.2.build.132-stable.jar:?]
        at io.papermc.paper.plugin.manager.PaperPluginInstanceManager.enablePlugin(PaperPluginInstanceManager.java:201) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at io.papermc.paper.plugin.manager.PaperPluginManagerImpl.enablePlugin(PaperPluginManagerImpl.java:109) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at org.bukkit.plugin.SimplePluginManager.enablePlugin(SimplePluginManager.java:519) ~[paper-api-26.2.build.132-stable.jar:?]
        at org.bukkit.craftbukkit.CraftServer.enablePlugin(CraftServer.java:633) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at org.bukkit.craftbukkit.CraftServer.enablePlugins(CraftServer.java:590) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.initPostWorld(MinecraftServer.java:648) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.dedicated.DedicatedServer.initServer(DedicatedServer.java:343) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1266) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]
[20:08:28 ERROR]: [Kreiscraft] failed to enable module clickthrough
de.kreiscraft.core.service.ServiceNotFoundException: no service registered for de.kreiscraft.modules.settingsgui.SettingsService
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.core.service.ServiceRegistry.get(ServiceRegistry.java:18) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.modules.clickthrough.ClickthroughModule.enable(ClickthroughModule.java:30) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.core.module.ModuleRegistry.enableAll(ModuleRegistry.java:59) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.KreiscraftBootstrap.start(KreiscraftBootstrap.java:129) ~[?:?]
        at Kreiscraft-0.1.0-SNAPSHOT.jar//de.kreiscraft.Kreiscraft.onEnable(Kreiscraft.java:13) ~[?:?]
        at org.bukkit.plugin.java.JavaPlugin.setEnabled(JavaPlugin.java:279) ~[paper-api-26.2.build.132-stable.jar:?]
        at io.papermc.paper.plugin.manager.PaperPluginInstanceManager.enablePlugin(PaperPluginInstanceManager.java:201) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at io.papermc.paper.plugin.manager.PaperPluginManagerImpl.enablePlugin(PaperPluginManagerImpl.java:109) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at org.bukkit.plugin.SimplePluginManager.enablePlugin(SimplePluginManager.java:519) ~[paper-api-26.2.build.132-stable.jar:?]
        at org.bukkit.craftbukkit.CraftServer.enablePlugin(CraftServer.java:633) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at org.bukkit.craftbukkit.CraftServer.enablePlugins(CraftServer.java:590) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.initPostWorld(MinecraftServer.java:648) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.dedicated.DedicatedServer.initServer(DedicatedServer.java:343) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1266) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:303) ~[paper-26.2.jar:26.2-132-19ebc4a]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]
```

> NOTE not everything that is configurable through settings gui should have a
> hard dependency on it.

also way more configuration through the settings gui. should be basically
everything possible; maybe even stuff that is not hot reloadable

## /combat

1 second delay. probably because it doesnt show 5

command shows @a autocomplete but only allows one; should be able to tag
multiple

## /item

remove /i alias

## admin

rename KHelp class to KcHelp

## /kcgamemode

remove and just make /gm alias for vanilla /gamemore

## /ping

autocomplete players

## /lastonline

autocomplete players, online and offline

better formatting not just utc formatting

## /spawnmob

autocomplete entityType

## /sudo

autocomplete players

## /uuid

autocomplete players

## /kreiscraftweather

remove

## /hackerlogs

command shows @a autocomplete but only allows one

## /bedrocklist

cant add unseen players?

## chatformat

playername color chnage aswell

## /effects

change to always show not just while moving; also default enabled; also add
toggle subcommand instead of just root as toggle

## /glow

add optional command to glow other players

also when leaving and joining player is still glowing but running /glow will say
its just getting enabled => look up player metadata and change based on what
currently is

## /jail

command to set jail location

jailed player doesnt get notification why they were jailed

> NOTE in general make a general language of how commands should be colored.
> e.g. in jail when player is released the name is green and when jailed its
> yellow. should be a consistent color. also the reason should be a different
> color from the other text so stuff like this is highlighetd. dont just ask me
> how i want it. create a command which i can run and which shows multiple ways
> of how it could be handled and let me choose. mainly use gui for interaction
> and only fall back to chat if absolutely necessary

## /pluginhider

remove

## /updatemode

remove german subcommands

## /setspawn && /spawn

the teleport range sholdnt exist and /spawn should teleport to spawn when in the
radius (ignoring the y coordinate)

spawn launch should also be removed

also launch-location is dead config and should be remove. also scan for other
dead code/config

cant seem to open glide; double jump only launches me without any glide

also there is a telerport-range in config.yml and one in data/spawn.yml

> NOTE In general there are multiple things under /data/\*.yml which could just
> be saved under config.yml; config.yml is not immutable

## /setworldspawn

remove; its just the same as vanilla /spawnpoint (not sure. make some research)
