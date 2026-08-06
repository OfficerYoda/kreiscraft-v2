# Wiring: update-mode

## config.yml addition

```yaml
  update-mode:
    enabled: false
    bypass-permission: kreiscraft.updatemode.bypass
    bypass-names: []
    kick-message: "<red>Server im Wartungsmodus</red>"
```

## lang/de.yml addition

```yaml
update-mode:
  activated: "<prefix> <yellow>Wartungsmodus aktiviert."
  deactivated: "<prefix> <green>Wartungsmodus deaktiviert."
  status-on: "<prefix> <yellow>Wartungsmodus ist AN."
  status-off: "<prefix> <green>Wartungsmodus ist AUS."
```

## lang/en.yml addition

```yaml
update-mode:
  activated: "<prefix> <yellow>Maintenance mode activated."
  deactivated: "<prefix> <green>Maintenance mode deactivated."
  status-on: "<prefix> <yellow>Maintenance mode is ON."
  status-off: "<prefix> <green>Maintenance mode is OFF."
```

## plugin.yml permissions

```yaml
  kreiscraft.updatemode.admin:
    description: Toggle maintenance mode
    default: op
  kreiscraft.updatemode.bypass:
    description: Log in during maintenance mode
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.updatemode.UpdateModeModule());

## Shared-file changes required outside your module

(none)
