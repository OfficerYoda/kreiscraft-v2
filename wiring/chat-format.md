# Wiring: chat-format

## config.yml addition

```yaml
  chat-format:
    enabled: false
    event-priority: LOWEST
    allow-mini-message-in-body: false
    format: "<team-prefix><name-colored><team-suffix><gray>: <reset><body>"
```

## lang/de.yml addition

(none)

## lang/en.yml addition

(none)

## plugin.yml permissions

(none)

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.chatformat.ChatFormatModule());

## Shared-file changes required outside your module

**EventBus dynamic priority (concern, not a blocker):** `EventBus.register(Listener)` calls
`registerEvents(listener, plugin)` which reads event priority from `@EventHandler` annotations at
registration time. The `ChatFormatConfig.eventPriority` field is stored in config and applied as
the listener default (`EventPriority.LOWEST`) but cannot be dynamically wired through the current
`EventBus` API. If runtime-configurable chat event priority is required, `EventBus` would need a
`register(Listener, EventPriority)` overload that uses `PluginManager.registerEvent(...)` with an
`EventExecutor`. For now LOWEST is hardcoded on `@EventHandler`, matching the config default and
the correct value for a chat formatter that replaces the vanilla event.
