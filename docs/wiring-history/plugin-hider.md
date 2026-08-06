# Wiring: plugin-hider

## config.yml addition

```yaml
  plugin-hider:
    enabled: true
    default-allowed-commands:
      - help
      - spawn
      - msg
      - tell
      - w
      - r
      - tps
      - ping
      - status
```

## lang/de.yml addition

```yaml
plugin-hider:
  added: "<prefix> <green><command></green> zur Allowlist hinzugefügt."
  removed: "<prefix> <green><command></green> von der Allowlist entfernt."
  already-present: "<prefix> <yellow><command></yellow> ist bereits erlaubt."
  not-present: "<prefix> <yellow><command></yellow> war nicht auf der Allowlist."
  list-header: "<prefix> <gray>Erlaubte Befehle:"
  list-entry: "<gray>- <command></gray>"
  list-empty: "<prefix> <yellow>(keine)"
```

## lang/en.yml addition

```yaml
plugin-hider:
  added: "<prefix> <green><command></green> added to the allowlist."
  removed: "<prefix> <green><command></green> removed from the allowlist."
  already-present: "<prefix> <yellow><command></yellow> is already allowed."
  not-present: "<prefix> <yellow><command></yellow> was not on the allowlist."
  list-header: "<prefix> <gray>Allowed commands:"
  list-entry: "<gray>- <command></gray>"
  list-empty: "<prefix> <yellow>(none)"
```

## plugin.yml permissions

```yaml
  kreiscraft.pluginhider.admin:
    description: Manage the plugin-hider allowlist
    default: op
  kreiscraft.pluginhider.bypass:
    description: See all commands regardless of the allowlist
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.pluginhider.PluginHiderModule());

## Shared-file changes required outside your module

(none)
