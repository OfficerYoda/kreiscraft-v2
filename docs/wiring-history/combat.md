# Wiring: combat

## config.yml addition

```yaml
  combat:
    enabled: true
    tag-duration: 10s
    disable-elytra: true
    excluded-worlds: ["Lobby"]
    default-display: ACTIONBAR
```

## lang/de.yml addition

```yaml
combat:
  tagged: "<prefix> <red>Im Kampf!"
  remaining: "<red>Im Kampf: <seconds>s</red>"
  cleared: "<prefix> <green>Kein Kampf mehr."
  elytra-denied: "<prefix> <red>Elytra im Kampf deaktiviert."
  manual-tag: "<prefix> <yellow><player></yellow> für <seconds>s getaggt."
```

## lang/en.yml addition

```yaml
combat:
  tagged: "<prefix> <red>In combat!"
  remaining: "<red>In combat: <seconds>s</red>"
  cleared: "<prefix> <green>No longer in combat."
  elytra-denied: "<prefix> <red>Elytra disabled while in combat."
  manual-tag: "<prefix> Tagged <yellow><player></yellow> for <seconds>s."
```

## plugin.yml permissions

```yaml
  kreiscraft.combat.admin:
    description: Manually tag a player as in combat
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.combat.CombatModule());

## Shared-file changes required outside your module

(none)
