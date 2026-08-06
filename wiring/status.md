# Wiring: status

## config.yml addition

```yaml
  status:
    enabled: false
```

## lang/de.yml addition

```yaml
status:
  prefix-set: "<prefix> <green>Prefix aktualisiert."
  suffix-set: "<prefix> <green>Suffix aktualisiert."
  too-long: "<prefix> <red>Zu lang (max. 32 sichtbare Zeichen)."
  no-permission-other: "<prefix> <red>Keine Berechtigung, andere zu ändern."
```

## lang/en.yml addition

```yaml
status:
  prefix-set: "<prefix> <green>Prefix updated."
  suffix-set: "<prefix> <green>Suffix updated."
  too-long: "<prefix> <red>Too long (max. 32 visible characters)."
  no-permission-other: "<prefix> <red>No permission to change others."
```

## plugin.yml permissions

```yaml
  kreiscraft.status.set.self:
    description: Set your own status prefix/suffix
    default: true
  kreiscraft.status.set.other:
    description: Set another player's status
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.status.StatusModule());

## Shared-file changes required outside your module

(none)
