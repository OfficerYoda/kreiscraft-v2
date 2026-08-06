# Wiring: wither

## config.yml addition

```yaml
  wither:
    enabled: true
    max-health: 600.0
    attack-damage: 40.0
    armor: 8.0
    half-health-threshold: 300.0
    minion-count: 4
    half-health-explosion-power: 4.0
    death-boss-bar-radius: 50
    death-explosion-power: 20.0
```

## lang/de.yml addition

```yaml
wither:
  explosion-title: "<red>Explosion</red>"
```

## lang/en.yml addition

```yaml
wither:
  explosion-title: "<red>Explosion</red>"
```

## plugin.yml permissions

(none)

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.wither.WitherModule());

## Shared-file changes required outside your module

(none)
