# Wiring: effects

## config.yml addition

```yaml
  effects:
    enabled: false
    default-particle: HAPPY_VILLAGER
    count: 25
    offset-x: 0.9
    offset-y: 0.9
    offset-z: 0.9
    force: true
```

## lang/de.yml addition

```yaml
effects:
  enabled: "<prefix> <green>Effekte aktiviert."
  disabled: "<prefix> <yellow>Effekte deaktiviert."
  particle-set: "<prefix> <green>Partikel: <particle></green>."
  unknown-particle: "<prefix> <red>Unbekanntes Partikel: <particle></red>."
```

## lang/en.yml addition

```yaml
effects:
  enabled: "<prefix> <green>Effects enabled."
  disabled: "<prefix> <yellow>Effects disabled."
  particle-set: "<prefix> <green>Particle: <particle></green>."
  unknown-particle: "<prefix> <red>Unknown particle: <particle></red>."
```

## plugin.yml permissions

```yaml
  kreiscraft.effects.use:
    description: Toggle particle trail
    default: true
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.effects.EffectsModule());

## Shared-file changes required outside your module

(none)
