---
title: Shattered Crown lifecycle and recovery
---

# Shattered Crown lifecycle and recovery

Ticket #139 extends the playable #134 baseline without requiring unfinished attack tickets.
The [encounter guide](/shattered-crown/) lists the shipped profile and current combat scope.

## Server Configuration

Use the existing permission-controlled Server Configuration GUI or YAML and `/kreiscraft reload`.
Combat/geometry fields remain hot-reloadable **for the next attempt**. World identity remains
YAML-only, and enabling combat still requires a restart. An explicit reload with `enabled: false`
aborts immediately; a later reload with `enabled: true` does not start the stopped module.
Removing the entire section also aborts/disables combat, matching the bundled default-off policy.
Missing sections deserialize default tuning without inserting a section into the live YAML tree.
Dimension Access and travel/respawn policy are untouched.

Both GUI edits and disk reloads validate the complete supplied Shattered Crown profile before
publication, including cross-field geometry and the enable flag. Invalid GUI edits do not change
the live tree or file. Invalid disk reloads leave the previous entire live configuration tree and
the running attempt intact; correct the file and retry. Invalid module startup identifies the
offending setting without spawning objectives or changing the dragon. A valid profile is
snapshotted once per attempt, including replacement defenders and geometry.

## Disabled-at-startup policy

Bootstrap installs a cleanup-only recovery listener **independently of module enablement**. This
listener remains available after combat is disabled, until normal plugin shutdown unregisters
the shared EventBus. It never discovers/starts combat, protects the platform, changes Dimension
Access, or issues rewards. It checks loaded entities once at startup and on world/entity-chunk
discovery, not every tick. This also handles artifacts in chunks that load after startup.

Persisted keys:

| Key | Purpose |
| --- | --- |
| `kreiscraft:crown_artifact` | String ownership marker on Anchors, defenders and other owned persistent effects/indicators |
| `kreiscraft:crown_original_health` | Original managed dragon maximum-health base value |
| `kreiscraft:crown_original_phase` | Original native phase before an owned landing/flight modification |
| `kreiscraft:crown_protected_shot` | Protected attack origin only; not ownership and not a deletion marker |

Recovery excludes entities belonging to the live attempt and never removes player entities.
Unmarked ordinary crystals, ritual crystals and mobs remain untouched. Tagged stale artifacts
are removed. The existing surviving dragon is restored to its original maximum and full health;
damaged/non-finite recovery metadata emits a warning and falls back to vanilla maximum 200.
A surviving dragon with either managed-health or native-phase metadata normalizes to `CIRCLING`,
rather than resuming a landing, warning, or pre-death `DYING` flight. Already-dead dragons are never
revived. Discovery does not start an Encounter on an unowned dragon in native `DYING` flight.
Markers are cleared after restoration. No replacement dragon, rewarded death, portal/egg change,
or simulated respawn ritual is involved.
Native maximum-health modifiers remain intact; attempts start at the effective maximum, not the
configured base value when modifiers change it.

## Attempt ownership contract for subsequent slices

Keep later attacks inside the same `EncounterController` boundary:

- `runLater` / `runRepeating`: use these instead of the raw module scheduler for warnings, damage,
  pools and landing timing. Tasks are cancelled on every termination. Their callbacks also check
  the old attempt's closed/cancelled ownership, so an already queued callback is inert even after
  another attempt has begun. Completed/cancelled tasks leave the ownership set.
- `own(entity, kind)`: tag and register every spawned persistent hazard/indicator/objective.
  Cleanup removes them without touching unrelated entities. Invalid expired references are
  pruned when registering more entities. The managed dragon and participants are not artifacts.
- `onCleanup(restore)`: register temporary player effects, UI and other reversible changes. The
  returned action can be run on normal expiry; it removes itself and runs exactly once. Any
  outstanding restorations run on termination. A failed restoration is logged without suppressing
  the remaining effect/entity cleanup.
- `ownDragonPhase()`: call once before changing native flight/landing phases with supported Paper
  APIs. Abort restores the original phase; restart normalizes a fresh battle. Normal victory does
  not overwrite the vanilla completion phase.

Reset, victory, disable and world unload close ownership before discarding the attempt. The
module discovery task is separately cancelled and guarded on disable. World unload blocks
rediscovery until a matching world-load event. Repeated startup/discovery/cleanup is idempotent.
Regular reset still means 60 continuous seconds without eligible living combat participants;
the existing dragon returns to full original health and waits for a participant before a new
configured-health opening. Abort is not victory.

## Automated evidence

`LifecycleEncounterTest` adds ten encounter-level behavior cases, alongside the 22 existing
`EncounterTest` tests. All 32 passed with no skips. Tests use real module enable/reload/disable,
real MockBukkit entities/events, and the existing controlled scheduler. One scheduler spy captures
an already queued callback solely to exercise Paper's cancellation race; no fake combat engine
or attack-specific fixture is introduced. The existing narrow `DragonBattleAccess` adapter is
unchanged.

Red→green evidence includes disabled startup recovery, duplicate enablement, cross-field GUI
rejection, detached disk-reload rejection, malformed enablement preserving the next-attempt
snapshot, pending owned effects on unload, exactly-once temporary-effect cleanup, damaged stale
metadata, native phase ownership/recovery, and useful startup configuration errors.

```sh
JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 \
  ./gradlew spotlessApply check -x testNms --console=plain
git diff --check
```

Full check passed: 1,342 tests, zero failures/errors, five existing metrics-collector skips.
Bootstrap, module-independence, configuration, GUI and localized-message regressions are included.
Spotless, Checkstyle, `-Werror` production compilation, shadow JAR and InvUI compatibility passed.
No NMS code changed; version-specific execution remains excluded.

## Supported-Paper verification still required

Target is Java 25 / Paper 26.2. No running Paper/Minecraft process was available on this machine
during implementation. **No live restart, ritual-respawn, AI/rendering or multiplayer balance
evidence is claimed.** On the supported deployment, record:

1. Stop/crash during an active attempt and native landing; restart enabled and disabled. Verify
   stale loaded and later-loaded chunk artifacts disappear, one full-health dragon survives,
   phase is retryable, and no XP/portal/egg/ritual completion is awarded.
2. Disable/reload and world unload/load while warnings are pending. Confirm no later damage,
   indicators or temporary effects; re-enable after restart without duplicate listeners/objectives.
3. Initial dragon and a normal vanilla ritual respawn, including ritual crystals outside combat,
   real multipart damage, native flight/landing, and normal initial-versus-respawn rewards.
4. The representative approximately 20-player mixed-gear performance/balance session from spec
   #132; record actual observations rather than claiming automated tests establish combat feel.
