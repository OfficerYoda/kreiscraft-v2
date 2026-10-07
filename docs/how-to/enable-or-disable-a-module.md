---
title: How to enable or disable a module
---

## A. At startup via `config.yml`

This is the only supported way to toggle a module.

1. **In the repo** — edit `src/main/resources/config.yml`:

   ```yaml
   modules:
     spawn:
       enabled: true    # set to false to disable
   ```

2. **On the running server** — the deployed file is at
   `plugins/Kreiscraft/config.yml`. Edit it there for a live server, then restart.

3. **Restart the server.** Module enable/disable only takes effect on startup.

> **Default behaviour:** if the `enabled` key is absent from a module's config block,
> the module is **disabled**. You must explicitly set `enabled: true`.

> **Shipped defaults:** the bundled `config.yml` enables most modules, but
> `effects`, `firework`, `glow` and `positions` ship with `enabled: false`.
> Set `enabled: true` to activate any of them. `positions` is intentionally disabled
> by default because it is a server-specific policy choice.

---

## B. During development — runtime reload

`/kreiscraft reload` **does not** enable or disable modules.

It calls `config.reload()` and then `module.onConfigReload(moduleConfig)` on every
already-enabled module. This refreshes config values (timeouts, thresholds, etc.) without
restarting the server.

There is no runtime toggle command. A module that was not enabled at startup cannot be
enabled mid-session, and a running module cannot be stopped without a full restart.

---

## Quick reference

| Goal | How |
|---|---|
| Enable a module | Add `enabled: true` under `modules.<id>` in `config.yml`, restart |
| Disable a module | Set `enabled: false` (or remove the block), restart |
| Reload config values for running modules | `/kreiscraft reload` |
| Toggle a module without restart | Not supported |
