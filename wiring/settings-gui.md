# Wiring: settings-gui

## config.yml addition

```yaml
  settings-gui:
    enabled: false
```

## lang/de.yml addition

```yaml
settings-gui:
  gui-title: "<gray>Einstellungen"
  click-through-on: "<green>Click-Through: An"
  click-through-off: "<red>Click-Through: Aus"
  combat-display-chat: "<gray>Combat-Anzeige: Chat"
  combat-display-actionbar: "<gray>Combat-Anzeige: Actionbar"
  combat-display-title: "<gray>Combat-Anzeige: Titel"
  wip: "<yellow>In Arbeit"
```

## lang/en.yml addition

```yaml
settings-gui:
  gui-title: "<gray>Settings"
  click-through-on: "<green>Click-Through: On"
  click-through-off: "<red>Click-Through: Off"
  combat-display-chat: "<gray>Combat Display: Chat"
  combat-display-actionbar: "<gray>Combat Display: Action Bar"
  combat-display-title: "<gray>Combat Display: Title"
  wip: "<yellow>Work in Progress"
```

## plugin.yml permissions

```yaml
  kreiscraft.settingsgui.use:
    description: Open the per-player settings GUI
    default: true
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.settingsgui.SettingsGuiModule());

## Shared-file changes required outside your module

(none)
