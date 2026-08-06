# Wiring: tablist

## config.yml addition

```yaml
  tablist:
    enabled: false
    update-interval: 20t
    header: "<gradient:#5555ff:#55ffff>Kreiscraft</gradient>"
    footer: "TPS: %tps% | Players: %server_players%"
```

## lang/de.yml addition

(none)

## lang/en.yml addition

(none)

## plugin.yml permissions

(none)

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.tablist.TablistModule());

## Shared-file changes required outside your module

(none)
