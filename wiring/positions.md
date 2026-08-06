# Wiring: positions

## config.yml addition

```yaml
  positions:
    enabled: false
    gui-rows: 6
    teleport-on-click: true
```

## lang/de.yml addition

```yaml
positions:
  set: "<prefix> <green>Position <name></green> gespeichert."
  removed: "<prefix> <green>Position <name></green> gelöscht."
  not-found: "<prefix> <red>Position <name></red> unbekannt."
  gui-title: "<gray>Deine Positionen"
  list-header: "<prefix> <gray>Deine Positionen (<count>):"
  list-entry: "<gray>- <name> (<world> <x> <y> <z>)"
  teleported: "<prefix> <green>Zu <name></green> teleportiert."
```

## lang/en.yml addition

```yaml
positions:
  set: "<prefix> <green>Position <name></green> saved."
  removed: "<prefix> <green>Position <name></green> deleted."
  not-found: "<prefix> <red>Position <name></red> not found."
  gui-title: "<gray>Your Positions"
  list-header: "<prefix> <gray>Your Positions (<count>):"
  list-entry: "<gray>- <name> (<world> <x> <y> <z>)"
  teleported: "<prefix> <green>Teleported to <name></green>."
```

## plugin.yml permissions

```yaml
  kreiscraft.positions.use:
    description: Save/teleport to personal positions
    default: true
```

## KreiscraftBootstrap registration

```java
modules.register(new de.kreiscraft.modules.positions.PositionsModule());
```

## Shared-file changes required outside your module

(none)
