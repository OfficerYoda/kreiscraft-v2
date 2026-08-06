# Wiring: glow

## config.yml addition

  glow:
    enabled: false

## lang/de.yml addition

glow:
  enabled: "<prefix> <green>Glow aktiviert."
  disabled: "<prefix> <yellow>Glow deaktiviert."

## lang/en.yml addition

glow:
  enabled: "<prefix> <green>Glow enabled."
  disabled: "<prefix> <yellow>Glow disabled."

## plugin.yml permissions

  kreiscraft.glow.use:
    description: Toggle glow effect
    default: true

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.glow.GlowModule());

## Shared-file changes required outside your module

(none)
