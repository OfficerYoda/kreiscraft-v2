# Wiring: world-mgmt

## config.yml addition

```yaml
  world-mgmt:
    enabled: false
```

## lang/de.yml addition

```yaml
world-mgmt:
  teleported: "<prefix> <green>Nach <world></green>."
  unknown-world: "<prefix> <red>Welt <world></red> existiert nicht."
  spawn-set: "<prefix> <green>Weltspawn gesetzt."
  portal-converted: "<prefix> <gray>Portal-Koordinaten: <x> <y> <z></gray>"
  portal-no-target: "<prefix> <red>Keine Zielwelt vorhanden."
```

## lang/en.yml addition

```yaml
world-mgmt:
  teleported: "<prefix> <green>Teleported to <world></green>."
  unknown-world: "<prefix> <red>World <world></red> does not exist."
  spawn-set: "<prefix> <green>World spawn set."
  portal-converted: "<prefix> <gray>Portal coordinates: <x> <y> <z></gray>"
  portal-no-target: "<prefix> <red>No target world found."
```

## plugin.yml permissions

```yaml
  kreiscraft.worldmgmt.world:
    description: Teleport between worlds
    default: op
  kreiscraft.worldmgmt.setworldspawn:
    description: Set the spawn point of the current world
    default: op
  kreiscraft.worldmgmt.portal:
    description: Compute portal coordinate translations
    default: true
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.worldmgmt.WorldMgmtModule());

## Shared-file changes required outside your module

(none)
