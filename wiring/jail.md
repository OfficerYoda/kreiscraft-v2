# Wiring: jail

## config.yml addition

```yaml
  jail:
    enabled: true
    location: { world: world, x: 126, y: 91, z: 92, yaw: 0, pitch: 0 }
    blocked-commands: [msg, tell, w, pay, r]
```

## lang/de.yml addition

```yaml
jail:
  jailed: "<prefix> <yellow><player></yellow> wurde eingesperrt: <reason>"
  released: "<prefix> <green><player></green> wurde freigelassen."
  already-jailed: "<prefix> <red><player></red> ist bereits eingesperrt."
  not-jailed: "<prefix> <red><player></red> ist nicht eingesperrt."
  target-has-bypass: "<prefix> <red><player></red> hat Bypass-Berechtigung."
  command-blocked: "<prefix> <red>Dieser Befehl ist im Gefängnis nicht erlaubt."
  target-not-found: "<prefix> <red>Spieler <player> ist nicht online."
```

## lang/en.yml addition

```yaml
jail:
  jailed: "<prefix> <yellow><player></yellow> has been jailed: <reason>"
  released: "<prefix> <green><player></green> has been released."
  already-jailed: "<prefix> <red><player></red> is already jailed."
  not-jailed: "<prefix> <red><player></red> is not jailed."
  target-has-bypass: "<prefix> <red><player></red> has the bypass permission."
  command-blocked: "<prefix> <red>This command is not allowed in jail."
  target-not-found: "<prefix> <red>Player <player> is not online."
```

## plugin.yml permissions

```yaml
  kreiscraft.jail.use:
    description: Jail and unjail players
    default: op
  kreiscraft.jail.bypass:
    description: Cannot be jailed
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.jail.JailModule());

## Shared-file changes required outside your module

`jail.target-not-found` was added to both `lang/de.yml` and `lang/en.yml` above. The brief did not include this key but it is needed when the `/jail` or `/unjail` target player is offline. Batch C should add it alongside the other `jail.*` keys.
