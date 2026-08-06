# Wiring: clickthrough

## config.yml addition

```yaml
  clickthrough:
    enabled: false
    target-block-max-distance: 6
```

## lang/de.yml addition

(none)

## lang/en.yml addition

(none)

## plugin.yml permissions

(none)

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.clickthrough.ClickthroughModule());

## Shared-file changes required outside your module

(none)
