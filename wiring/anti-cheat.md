# Wiring: anti-cheat

## config.yml addition

```yaml
  anti-cheat:
    enabled: false
    channel: "kreiscraft:mod_checker"
    kick-on-fabric-forge: true
    handshake-grace-duration: 5s
```

## lang/de.yml addition

```yaml
anti-cheat:
  cheat-kick: "<red>Bitte entferne deinen Cheat-Client (<detail>)."
  xray-kick: "<red>X-Ray erkannt. Bitte entfernen."
  freecam-kick: "<red>Bitte deaktiviere IgnoreAll-Collision."
  fabric-forge-kick: "<red>Fabric/Forge nicht erlaubt."
  no-logs: "<prefix> <yellow>Keine Einträge für <player></yellow>."
  log-header: "<prefix> <gray>Einträge für <player> (<kind>):"
  log-entry: "<gray>- <when>: <detail>"
  invalid-kind: "<red>Unbekannte Art '<kind>'. Bitte 'cheats' oder 'xray' verwenden."
```

## lang/en.yml addition

```yaml
anti-cheat:
  cheat-kick: "<red>Please remove your cheat client (<detail>)."
  xray-kick: "<red>X-Ray detected. Please remove it."
  freecam-kick: "<red>Please disable IgnoreAll-Collision."
  fabric-forge-kick: "<red>Fabric/Forge is not allowed."
  no-logs: "<prefix> <yellow>No entries found for <player></yellow>."
  log-header: "<prefix> <gray>Entries for <player> (<kind>):"
  log-entry: "<gray>- <when>: <detail>"
  invalid-kind: "<red>Unknown kind '<kind>'. Use 'cheats' or 'xray'."
```

## plugin.yml permissions

```yaml
  kreiscraft.anticheat.view:
    description: View hack logs
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.anticheat.AntiCheatModule());

## Shared-file changes required outside your module

`PlayerBrandProvider` must be published on `ServiceRegistry` before `anti-cheat` enables.
Per the dispatch note this is already done by Plan 2 platform wiring — no additional change needed here.
