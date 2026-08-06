# Wiring: start-sequence

## config.yml addition

```yaml
  start-sequence:
    enabled: true
    countdown: 10s
    initial-border-size: 50.0
    target-border-size: 30000000.0
    phase-1-duration: 550000s
    phase-2-duration: 300000s
    phase-1-to-2-delay: 7500ms
    phase-2-to-instant-delay: 15000ms
```

## lang/de.yml addition

```yaml
start-sequence:
  already-started: "<prefix> <red>Season läuft bereits."
  countdown-title: "<gradient:#5555ff:#55ffff>Kreiscraft startet in <seconds></gradient>"
  started: "<gradient:#5555ff:#55ffff>Kreiscraft startet jetzt</gradient>"
  reset: "<prefix> <yellow>Season zurückgesetzt."
  not-started: "<prefix> <red>Season läuft nicht."
```

## lang/en.yml addition

```yaml
start-sequence:
  already-started: "<prefix> <red>Season is already running."
  countdown-title: "<gradient:#5555ff:#55ffff>Kreiscraft starts in <seconds></gradient>"
  started: "<gradient:#5555ff:#55ffff>Kreiscraft starts now</gradient>"
  reset: "<prefix> <yellow>Season reset."
  not-started: "<prefix> <red>Season is not running."
```

## plugin.yml permissions

```yaml
  kreiscraft.startsequence.admin:
    description: Trigger the season start sequence
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.startsequence.StartSequenceModule());

## Shared-file changes required outside your module

(none)
