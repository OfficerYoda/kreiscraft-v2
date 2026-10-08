# Testing Findings Remediation Plan

## Outcome

Resolve every finding in `notes.md` through staged, independently releasable changes. Protect
persistent player/activity data, remove obsolete commands immediately, and make player-facing
behavior consistent across commands and GUIs.

## Settled product rules

- Ship in small vertical slices; startup/data safety comes first.
- Removed commands and aliases receive no deprecation period.
- `/settings` opens Player Settings. Administrators see a button to Server Configuration, which is
  also available through `/settings admin`.
- Player language applies to all output addressed to that player, including GUIs and timers.
- Disabled modules do not appear in Player Settings.
- Server Configuration saves each valid edit immediately. On close, send a context-aware clickable
  reload prompt; module lifecycle changes explicitly require a full restart.
- Low-level/infrastructure values and tab-list header/footer remain YAML-only. Other non-secret
  values may use nested GUI editors.
- Player and gameplay activity remains in `data/*.yml`. Operator-owned state such as spawn,
  update-mode, and start-sequence state moves to `config.yml` with fresh defaults and no migration.
- The one `spawn-radius` defaults to 50. `/spawn` checks an XZ cylinder; glide checks a 3D sphere.
- The Start Sequence shows the complete countdown and then performs one 30,000-second border
  expansion to 30,000,000 blocks. Minecraft owns the in-progress transition across restarts.
- Offline inventory writes are staged and atomically committed on close only while the target is
  still offline.
- Command message colors are chosen through a temporary in-game preview GUI, then applied globally;
  the preview command is removed afterward.

## Stage 1 — Persistence and service safety

### 1.1 Make player preferences independent of the GUI module

- Extract the persistent settings store/service from `settingsgui` into an always-available player
  preferences boundary. The GUI becomes one client rather than the owner of combat/click-through
  behavior.
- Make combat and click-through consume the preference service optionally and fall back to their
  module defaults. Remove their hard dependency on `settings-gui`.
- Extend the persisted preference model with language, effects enabled/particle, and glow enabled.
- Preserve the current typed enum write (`set(CombatDisplay.class, value)`) and add a real YAML
  round-trip test for every enum value. Per decision, do not add recovery for the legacy malformed
  `!!CombatDisplay` file; deployment must start from a compatible `settings.yml`.

### 1.2 Add safe writable configuration

- Add validated, atomic `ConfigService` writes (temporary file, flush, atomic replace where the
  filesystem supports it). Preserve comments and unrelated keys.
- Classify every editable setting with value type, validation, hot-reload support, restart
  requirement, and GUI exposure.
- Move spawn location, update-mode state, and start-sequence completion into `config.yml`; initialize
  from fresh defaults and retire their old data files without importing values.
- Keep positions, jail entries/restore locations, Bedrock identities, hack logs, statuses, and
  player settings as activity data.
- Add failure tests proving an invalid edit neither mutates the in-memory tree nor damages the file.

**Release gate:** settings persistence round-trips; combat/click-through load without the GUI module;
config writes survive restart; the complete automated test suite passes.

## Stage 2 — Localization and settings GUIs

### 2.1 Per-player localization

- Load both `de` and `en` message trees and select by recipient preference, falling back to the
  server language with a warning for invalid stored values.
- Add recipient-aware render APIs and migrate direct messages, GUI labels/titles, combat display,
  and per-recipient broadcasts.
- Use `core.timezone` as the sole timezone, set to `Europe/Berlin`; remove
  `modules.admin.timezone`.
- Add German and British-head controls to Player Settings. Keep canonical stored values stable
  (`de`, `en`) and keep both language files structurally identical.

### 2.2 Player Settings

- Fix the double-click defect by characterizing the InvUI event sequence and consuming one logical
  click instead of relying on a one-second time debounce.
- Add controls for language, click-through, combat display, effects state/particle, and glow.
- Hide controls whose modules are disabled.
- Apply and persist personal changes immediately.
- Show the Server Configuration button only to authorized administrators.

### 2.3 Server Configuration

- Add `/settings admin` and the linked admin-only GUI.
- Organize by core/module, then use typed controls:
  - left/right click for numeric decrement/increment (including effects interval),
  - direct toggles for booleans,
  - cycling/selectors for enums and materials,
  - current-position buttons for locations,
  - nested list editors and anvil/sign input for unavoidable text.
- Include advanced non-secret values, including MiniMessage chat formats and lists. Exclude API
  credentials/network settings, other low-level integration values, and tab-list header/footer.
- Save each validated click. Track whether the session changed hot-reloadable or restart-only
  fields; on close, send the appropriate clickable reload action and/or restart warning.
- Module `enabled` toggles are editable but never presented as hot-applied.

**Release gate:** language changes affect every personal surface; GUI writes are durable; restart
boundaries are never mislabeled; parity/key-reference tests pass.

## Stage 3 — `/invsee` and `/ec`

- Replace `kreiscraft.invsee.view` and `.edit` with one `kreiscraft.invsee.use` permission granting
  both commands and edit access.
- Add capability-based completion: online and known-offline profiles for both commands.
- Use a five-row inventory layout with helmet, chestplate, leggings, boots, and offhand in the top
  row; main inventory follows and the hotbar remains on the bottom row.
- Render structurally invalid/filler slots as non-movable barrier items.
- Implement version-isolated offline player-data reading/writing for inventory slots (including
  armor and offhand) and `EnderItems`.
- Open offline data as a snapshot. On close, verify the target is still offline, create a backup,
  and atomically replace the player data. If the player joined, abort the write, notify the admin,
  and offer to reopen the live view.
- Add fixture-based NBT round-trip tests covering ordinary inventory, armor, offhand, ender chest,
  empty slots, metadata-rich items, failed writes, and concurrent login.
- Apply the selected Command Message Style to both GUI titles instead of hard-coded gray.

**Release gate:** online and offline edits survive restart without item loss; collision handling
never overwrites live state.

## Stage 4 — Command correctness and completion

Create shared suggestion providers rather than repeating Bukkit scans in each command. Commands
that support historical profiles suggest online plus known-offline names; online-only commands
suggest online names.

- `/combat`: run the display task immediately so a five-second tag first shows `5`; use a
  multi-player argument and apply selectors such as `@a` to every result.
- `/hackerlogs`: accept multi-player selectors and render each selected player's results.
- `/ping`, `/sudo`: add online-player suggestions (`/sudo` also suggests `all`).
- `/lastonline`, `/uuid`: add online plus known-offline suggestions.
- `/lastonline`: show localized absolute Berlin time plus a localized relative duration.
- `/spawnmob`: suggest valid entity types.
- `/effects`: suggest valid particle names.
- Rename `KHelpCommand` and its test to `KcHelpCommand` without changing the public command names.
- Add command-tree/suggestion tests in addition to executor tests so selector cardinality and
  completions cannot regress.

## Stage 5 — Player-facing feature behavior

### Effects

- Replace movement-only emission with a scheduled emitter configurable in ticks, default `5t`.
- New players default to effects enabled whenever the module is enabled.
- `/effects toggle` toggles; bare `/effects` reports current state, particle, and usage.
- Persist effects state and particle through Player Settings; remove the quit-time state loss.

### Glow

- Persist the desired glow setting but treat `Player#isGlowing()` as the runtime truth when
  toggling; reconcile the entity and stored preference on join.
- Add optional online target completion to `/glow [player]`; toggling another player requires a
  dedicated `.other` permission and notifies both parties.

### Status/name color

- Make the status color drive chat name, Bukkit display name, scoreboard/overhead name, and tab-list
  name consistently.
- Test join ordering and status changes against every online viewer's scoreboard.

### Jail and Bedrock administration

- Add `/setjail` to persist the sender's current location in server configuration.
- Notify a jailed player directly and include a separately styled reason.
- Let `/bedrocklist add <name>` create a Pending Bedrock Authorization. Normalize the configured
  prefix/case, bind it to the first matching UUID at login, and never auto-rebind; replacement
  requires explicit removal and re-registration.

## Stage 6 — Spawn, startup sequence, and Wither

### Spawn and glide

- Replace `teleport-range` and nested `glide.spawn-radius` with module-level `spawn-radius: 50`.
- `/setspawn` takes no radius and writes the operator-owned spawn location to `config.yml`.
- `/spawn` remains overworld-only and succeeds only inside the XZ Spawn Access Zone; Y is ignored.
- Glide remains limited by the 3D Glide Zone. Add unequal-Y tests and reproduce the reported
  double-jump case on the test server. If the player is outside the 50-block sphere, report/observe
  that explicitly rather than silently attributing it to gliding state.
- Remove spawn-launch code/config/tests and dead `glide.launch-location`.
- Verify the directional `multiply-value` behavior and correct its misleading documentation.

### Startup sequence

- Remove the pre-display decrement so `10s` renders `10, 9, …, 1` and starts after the full ten
  seconds.
- Replace both phases, both inter-phase delays, and the final instant snap with one
  `expansion-duration: 30000s` setting and one `WorldBorder.changeSize(30_000_000, ...)` call.
- Keep duration parsing as `Duration` internally but document and emit this setting in seconds,
  converting to ticks only at the Bukkit API boundary.
- Do not add custom phase/timestamp resume state. Verify with a real restart integration test that
  Minecraft retains and continues the active world-border transition; document the supported
  server behavior.
- Update all scheduler expectations and configuration/module documentation.

### Wither

- First prove which Paper event represents the vanilla post-charge spawn explosion. Customize that
  event, or cancel/replace it at the same lifecycle point if necessary; never create a second
  immediate blast.
- Give spawn, half-health, and death explosions consistent power/fire/block-damage configuration.
  Defaults are destructive for every phase: fire and block damage enabled; spawn and death power
  `20`, half-health power `4`.
- Add event-level tests proving exactly one spawn blast and independent honoring of every field,
  including after config reload.

## Stage 7 — Immediate removals and repository cleanup

- Remove `/i`.
- Remove custom gamemode commands and aliases.
- Remove the custom weather, command-hiding, and world-spawn command surfaces, including modules/classes,
  permissions, messages, tests, configuration, bootstrap wiring, and documentation.
- Remove German `/updatemode an|aus` subcommands.
- Remove the dead core debug setting and run a repository-wide reachability/config audit.
- Remove every stale former-project-name reference from source comments, tests, and documentation; describe
  behavior directly in Kreiscraft terminology instead.
- For every dead item found, remove code, source config, live-template assumptions, docs, language
  keys, permissions, bootstrap registrations, and obsolete tests as one atomic change.
- Do not delete legitimate activity data merely because its module is currently disabled.

## Stage 8 — Command Message Style approval and rollout

1. Add a temporary admin-only preview command that opens a GUI of several named semantic palettes.
2. Each palette previews ordinary text, player names, reasons, values, success, warning, and error
   in realistic command messages and inventory titles.
3. Pause for in-game human selection; record the chosen palette as the Command Message Style.
4. Centralize semantic message styling and migrate all language files/messages, including jail
   messages and `/invsee`/`/ec` titles.
5. Remove the temporary preview command, permission, messages, and tests before the release closes.

This is an explicit blocking checkpoint: do not guess the final colors in code review or from chat.

## Cross-cutting verification

- Run formatting, compilation with `-Xlint:all -Werror`, unit tests, language parity, message-key
  reference tests, and the shaded-JAR build after every stage.
- Add integration coverage for command registration/removal, permissions, selector multiplicity,
  config save/reload/restart classification, and module operation without `settings-gui`.
- Use a disposable Paper server/player-data fixture for offline inventory writes and Wither spawn
  explosion behavior; unit mocks alone are insufficient for those boundaries.
- Manually verify both languages, all GUI navigation/back actions, clickable reload prompts,
  reconnect behavior for effects/glow, `/spawn` at same XZ with different Y, and rollback after a
  simulated failed file write.
- Update `docs/reference/configuration.md`, `docs/reference/module-catalog.md`, command/permission
  documentation, and both language files in the same stage as each behavior change.

## Recommended execution order

`Stage 1 → Stage 2 → Stage 3 → Stages 4 and 5 → Stage 6 → Stage 7 → Stage 8`

Stages 4 and 5 can proceed in parallel after the shared player-preference and localization work.
Stage 8 must remain last because it requires an in-game visual decision.
