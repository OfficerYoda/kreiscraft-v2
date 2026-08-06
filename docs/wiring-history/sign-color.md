# Wiring: sign-color

## config.yml addition

  sign-color:
    enabled: true

## lang/de.yml addition

(none)

## lang/en.yml addition

(none)

## plugin.yml permissions

  kreiscraft.signcolor.use:
    description: Use MiniMessage on signs
    default: true
  kreiscraft.signcolor.anvil:
    description: Use MiniMessage in anvil rename
    default: true

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.signcolor.SignColorModule());

## Shared-file changes required outside your module

(none)
