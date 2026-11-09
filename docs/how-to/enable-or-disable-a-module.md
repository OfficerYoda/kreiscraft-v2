---
title: How to enable or disable a module
---

## A. At startup via `config.yml`

Activation requires a server restart.

1. **In the repo** — edit `src/main/resources/config.yml`:

   ```yaml
   modules:
     spawn:
       enabled: true    # set to false to disable
   ```

2. **On the running server** — the deployed file is at
   `plugins/Kreiscraft/config.yml`. Edit it there for a live server, then restart.

3. **Restart the server.** Module enable/disable normally takes effect on startup; see the
   Shattered Crown abort exception below.

> **Default behaviour:** if the `enabled` key is absent from a module's config block,
> the module is **disabled**. You must explicitly set `enabled: true`.

> **Shipped defaults:** the bundled `config.yml` enables most modules, but
> `firework`, `positions`, and `shattered-crown` ship with `enabled: false`.
> Set `enabled: true` and restart to activate them. Before opting into Shattered Crown,
> read its [operator profile](/shattered-crown/) and [live acceptance checklist](/shattered-crown-validation/).

---

## B. During development — runtime reload

`/kreiscraft reload` **does not activate** modules.

It calls `config.reload()` and then `module.onConfigReload(moduleConfig)` on every
already-enabled module. This refreshes config values (timeouts, thresholds, etc.) without
restarting the server.

There is no runtime toggle command. A module that was not enabled at startup cannot be
enabled mid-session. Shattered Crown is the abort exception: reloading with
`modules.shattered-crown.enabled: false` immediately aborts and cleans up the encounter.
Re-enabling it still requires a restart; other modules require a restart to stop.

---

## Quick reference

| Goal | How |
|---|---|
| Enable a module | Add `enabled: true` under `modules.<id>` in `config.yml`, restart |
| Disable a module | Set `enabled: false` (or remove the block), restart |
| Abort Shattered Crown immediately | Set `modules.shattered-crown.enabled: false`, run `/kreiscraft reload` |
| Reload config values for running modules | `/kreiscraft reload` |
| Toggle a module without restart | Not supported |
