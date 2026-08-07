# Wiring: api

## config.yml addition

```yaml
  api:
    enabled: true
    host: "0.0.0.0"
    port: 8080
    cors-origins:
      - "https://kreiscraft.de"
    session-cookie:
      name: "website_user_uuid"
      max-age: 30d
    heartbeat-interval: 20s
    async-timeout: 10s
    metrics-refresh-interval: 1s
    verification-timeout: 2m
    admin-api-key: ""
```

## lang/de.yml addition

```yaml
api:
  link-prompt-title: "<gray>Verknüpfen mit <browser>"
  link-prompt-info: "<yellow>Website möchte sich mit deinem Account verknüpfen"
  link-prompt-accept: "<green>Akzeptieren"
  link-prompt-decline: "<red>Ablehnen"
  chat-format-from-web: "<gray>[WEB]</gray> <content>"
  unlinked: "<prefix> <yellow>Deine Website-Verknüpfung wurde aufgehoben."
```

## lang/en.yml addition

```yaml
api:
  link-prompt-title: "<gray>Link to <browser>"
  link-prompt-info: "<yellow>Website wants to link with your account"
  link-prompt-accept: "<green>Accept"
  link-prompt-decline: "<red>Decline"
  chat-format-from-web: "<gray>[WEB]</gray> <content>"
  unlinked: "<prefix> <yellow>Your website link has been removed."
```

## plugin.yml permissions

```yaml
  kreiscraft.api.link:
    description: Link a browser session to a Bukkit account via /link-player
    default: true
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.api.ApiModule());

## Shared-file changes required outside your module

`ChatBridge`, `WhitelistService`, and `Gui` must be published on `ServiceRegistry` before the `api`
module enables. These are all published by Plan 2's platform/shared wiring in
`KreiscraftBootstrap.start()` — no additional change needed here.
