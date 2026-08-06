# Wiring: bedrock

## config.yml addition

```yaml
  bedrock:
    enabled: true
    prefix: "."
```

## lang/de.yml addition

```yaml
bedrock:
  added: "<prefix> <green><player></green> zur Whitelist hinzugefügt (Bedrock)."
  unknown: "<prefix> <red>Kein Bedrock-Spieler namens <player></red> gesehen."
  list-header: "<prefix> <gray>Bekannte Bedrock-Spieler (<count>):"
```

## lang/en.yml addition

```yaml
bedrock:
  added: "<prefix> <green><player></green> added to whitelist (Bedrock)."
  unknown: "<prefix> <red>No bedrock player named <player></red> seen."
  list-header: "<prefix> <gray>Known bedrock players (<count>):"
```

## plugin.yml permissions

```yaml
  kreiscraft.bedrock.admin:
    description: Manage the bedrock whitelist
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.bedrock.BedrockModule());

## Shared-file changes required outside your module

(none)
