# Wiring: reboot

## config.yml addition

```yaml
  reboot:
    enabled: false
    default-countdown: 60s
    title-seconds: [500, 60, 30, 10]
    chat-seconds: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]
```

## lang/de.yml addition

```yaml
reboot:
  started: "<prefix> <yellow>Neustart in <seconds></yellow>s — <reason>"
  cancelled: "<prefix> <green>Neustart abgebrochen."
  not-running: "<prefix> <red>Kein Neustart läuft."
  countdown-title: "<red>Neustart in <seconds></red>"
  countdown-chat: "<prefix> <red>Neustart in <seconds></red> Sekunden."
  seed-blocked: "<prefix> <red>/seed ist deaktiviert."
  kick-message: "<red>Server startet neu</red>"
```

## lang/en.yml addition

```yaml
reboot:
  started: "<prefix> <yellow>Reboot in <seconds></yellow>s — <reason>"
  cancelled: "<prefix> <green>Reboot cancelled."
  not-running: "<prefix> <red>No reboot is running."
  countdown-title: "<red>Reboot in <seconds></red>"
  countdown-chat: "<prefix> <red>Reboot in <seconds></red> seconds."
  seed-blocked: "<prefix> <red>/seed is disabled."
  kick-message: "<red>Server restarting</red>"
```

## plugin.yml permissions

```yaml
  kreiscraft.reboot.admin:
    description: Trigger or cancel a server restart
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.reboot.RebootModule());

## Shared-file changes required outside your module

(none)
