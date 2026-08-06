# Wiring: admin

## config.yml addition

```yaml
  admin:
    enabled: true
    timezone: "UTC"
    links:
      website: "https://kreiscraft.de"
      discord: "https://discord.gg/example"
      youtube: "https://youtube.com/@example"
```

## lang/de.yml addition

```yaml
admin:
  gamemode-set: "<prefix> <green>Spielmodus für <player></green> auf <mode></green>."
  item-given: "<prefix> <green><count>x <item></green>."
  weather-set: "<prefix> <green>Wetter: <weather></green>."
  spawnmob-spawned: "<prefix> <green><count>x <entity></green> spawned."
  ip: "<prefix> <gray><player></gray> IP: <ip>"
  uuid: "<prefix> <gray><player></gray> UUID: <uuid>"
  lastonline: "<prefix> <gray><player></gray> zuletzt gesehen: <when>"
  ping: "<prefix> <gray><player></gray> Ping: <ping> ms"
  tps: "<prefix> <gray>TPS:</gray> <tps>"
  statistic: "<prefix> <gray><player></gray> <stat>: <value>"
  sudo-executed: "<prefix> <gray>sudo <player>: <text>"
  help:
    text: "<prefix> <gradient:#5555ff:#55ffff>Kreiscraft</gradient> — <click:open_url:'<website>'>Web</click> | <click:open_url:'<discord>'>Discord</click> | <click:open_url:'<youtube>'>YouTube</click>"
  target-not-found: "<prefix> <red>Spieler <player></red> nicht gefunden."
  usage: "<prefix> <red>Verwendung: <usage>"
```

## lang/en.yml addition

```yaml
admin:
  gamemode-set: "<prefix> <green>Gamemode for <player></green> set to <mode></green>."
  item-given: "<prefix> <green><count>x <item></green>."
  weather-set: "<prefix> <green>Weather: <weather></green>."
  spawnmob-spawned: "<prefix> <green><count>x <entity></green> spawned."
  ip: "<prefix> <gray><player></gray> IP: <ip>"
  uuid: "<prefix> <gray><player></gray> UUID: <uuid>"
  lastonline: "<prefix> <gray><player></gray> last seen: <when>"
  ping: "<prefix> <gray><player></gray> Ping: <ping> ms"
  tps: "<prefix> <gray>TPS:</gray> <tps>"
  statistic: "<prefix> <gray><player></gray> <stat>: <value>"
  sudo-executed: "<prefix> <gray>sudo <player>: <text>"
  help:
    text: "<prefix> <gradient:#5555ff:#55ffff>Kreiscraft</gradient> — <click:open_url:'<website>'>Web</click> | <click:open_url:'<discord>'>Discord</click> | <click:open_url:'<youtube>'>YouTube</click>"
  target-not-found: "<prefix> <red>Player <player></red> not found."
  usage: "<prefix> <red>Usage: <usage>"
```

## plugin.yml permissions

```yaml
  kreiscraft.admin.gamemode: { description: Change gamemode, default: op }
  kreiscraft.admin.item: { description: Spawn items, default: op }
  kreiscraft.admin.skull: { description: Get a player head, default: op }
  kreiscraft.admin.head: { description: Swap held item and helmet, default: op }
  kreiscraft.admin.weather: { description: Change weather, default: op }
  kreiscraft.admin.spawnmob: { description: Spawn mobs, default: op }
  kreiscraft.admin.ip: { description: See a player's IP, default: op }
  kreiscraft.admin.uuid: { description: See a player's UUID, default: op }
  kreiscraft.admin.lastonline: { description: See a player's last-online time, default: op }
  kreiscraft.admin.ping: { description: See a player's ping, default: true }
  kreiscraft.admin.tps: { description: See server TPS, default: true }
  kreiscraft.admin.statistic: { description: See a player's statistic, default: op }
  kreiscraft.admin.sudo: { description: Force a player to run chat/command, default: op }
  kreiscraft.admin.help: { description: Open Kreiscraft help links, default: true }
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.admin.AdminModule());

## Shared-file changes required outside your module

(none)
