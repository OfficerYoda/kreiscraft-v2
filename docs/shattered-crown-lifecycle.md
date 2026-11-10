---
title: Shattered Crown lifecycle and recovery
---

# Shattered Crown lifecycle and recovery

Ticket #139 extends the playable #134 baseline without requiring unfinished attack tickets.
The [encounter guide](/shattered-crown/) lists the shipped profile and current combat scope.

## Combat coverage, population and opening grace (#150)

#151 consumes these APIs at three fixed four-Anchor boundaries (100%, 65%, 30%). It freezes
`firstEntryPopulation()` for wave one and `scalingPopulation()` for later waves. Each Anchor receives
`ceil(population/4)` complete Roster Units. Missing/unloaded members are replaced, not defeated;
only destruction of all four exposed crystals completes a wave. A wave owns its crystal/defender
scope inside the attempt scope. Phase replacement closes attack, objective and extension-owned
work, cancels accepted-but-pending damage and resets explosion reservations. Debug snapshots are fresh,
but the remaining Opening Grace clock is preserved, not restarted. Native phase restoration,
unrelated entities and reward payloads remain intact. See [wave rules and probe](/shattered-crown/).

The combat area is a horizontal cylinder of radius 192 by default, centered on the actual managed
main-End battle podium, across that world's entire build height. Protected arrival is excluded;
other End worlds and outer islands beyond the configured radius are not combat areas. The retired
`arena-min-y` / `arena-max-y` keys no longer restrict combat.

Only online, living Survival/Adventure players inside that area can be attack targets or keep an
attempt alive. Creative/spectator occupancy and waiting in protected arrival do not prevent the
existing 60-second absence reset.

Population for difficulty is different: the first entry into the managed End freezes **everyone
online server-wide**, including creative/spectator players, before combat starts. Waiting on arrival
does not delay or replace this snapshot. If enable/restart discovers an already-occupied End, recovery
uses everyone online at that discovery; it cannot reconstruct a historical entry. An empty End before
an attempt, reset, victory, disable, world unload or a changed next-attempt world clears stale entry
state. Later wave snapshots and Prey use the live **all-End connected population**, including dead
players awaiting respawn, observers and players outside combat, without target or defender count caps.
This counts the server's connected roster by managed-world identity, not the world's living entity roster;
dead players still cannot be combat targets. `/dragondebug status` reports
`combat-population`, `scaling-population`, `entry-snapshot` and `grace-seconds` even before combat.

The first eligible arena entry starts a fresh Encounter's 30-second Opening Grace. Anchors and
defenders are active immediately. The dragon circles: native attack phase changes, dragon projectile
launches, contact/indirect damage and breath effects are suppressed, as are automatic custom attacks.
The first automatic Attack Warning begins at expiry, not one cadence later. Later arrivals, waves and
pause/resume do not restart grace. A manual `/dragondebug trigger` explicitly bypasses scheduling/grace
but still requires an eligible target and safe geometry; cancel/resume does not replenish time.

Arrival combat protection works in both directions, including marked protected-origin projectiles,
TNT and lingering effects after the shooter leaves arrival. Enemy potions/clouds and direct/attributed
damage cannot hit protected arrivals. This is **not terrain protection**: normal destructive
explosions may still destroy the platform and its surroundings.

`arena-radius` and `opening-grace-seconds` are validated Server Configuration fields (also available
in the GUI); reload affects only the next fresh attempt. Disable/unload cancels pending grace work.

## Server Configuration

### Defender roaming and allegiance (#152)

Owned defenders pursue only living, online Survival/Adventure participants throughout the configured
radius-192, full-build-height combat cylinder. Moving away from an Anchor inside that cylinder never
teleports a defender home. Leaving the cylinder/world or entering protected arrival does recover it
to its original objective. Mode, arrival, world, death and quit transitions clear stale targets;
native retaliation/retargeting cannot choose nonparticipants. The retired `defender-leash` setting
is ignored and no longer exposed in Server Configuration.

Anchor membership is independent of position and passengers. Owned descendants also gate the original
Anchor after their parent dies; projectiles/effects carry allegiance but are not extra defender lives.
Direct, projectile, attributed delayed damage, potion splash and cloud effects cannot harm Endermen,
other owned combatants or the managed dragon. Ordinary mobs and separate Wither gameplay are unchanged.

Later rosters must use the controller's existing attempt ownership:

- `spawnCombatant(location, type, parent, initializer)` creates a recoverable descendant with ownership
  and tuning installed **before** public Paper spawn callbacks. The initializer is replayed on recovery.
- `inheritOwnership(child, parent)` belongs in Paper's pre-spawn consumer for owned projectiles/effects
  or externally spawned descendants. It returns false for unrelated or expired parents. It never
  transfers an entity to another Anchor. `ownsCombatant` and `anchorId` expose scoped attribution.
- Native projectile launch/source-bearing spawn callbacks inherit ownership at LOWEST as a backstop;
  custom spawning must still use the pre-spawn consumer. Keep a real causing/direct owned entity on
  delayed `DamageSource` events, even after parent death, and schedule through `runLater/runRepeating`.
  Unattributed source-less explosions cannot be distinguished safely from unrelated gameplay.

Missing/unloaded defenders are replaced with their original species/tuning, not credited as defeats.
Actual accepted death events remove lives permanently. Each Anchor allows three recovery passes;
continued disappearance aborts cleanly and retries after 30 seconds instead of leaving a locked gate.
Reset/disable/unload cancels owned delayed work and removes descendants/effects, preserving unowned mobs.

The executable build132 native check is `defenderRoamingProbeJar`. Build with Java 25:
`./gradlew spotlessApply check build defenderRoamingProbeJar --max-workers=2`.
Stage its JAR **alongside** the production shadow JAR in a fresh disposable, loopback-only Paper
server with Crown enabled (other modules disabled), then launch with `-Dkreiscraft.defender-probe=true`.
It registers a genuine native player with synthetic transport, lets the native main-End battle create
its dragon, and observes the production controller; it never instantiates a second encounter. The
probe exits after `DEFENDER_ROAMING_PROBE PASS/FAIL`. Exit zero alone is not success. This fixture
checks the current 16-Husk root profile; extend the selection/counts when later roster tickets land.
It proves far-target native pursuit on an isolated elevated pad, actual attributed melee, public
native ally/projectile/cloud damage cancellation before/after parent death, mode exclusion, no death
replenishment, and owned-only cleanup. It does not prove human login/rendering or all-species natural
terrain navigation; #163 must exercise the integrated mounted/Guardian/Warden/Wither roster and real
projectile collisions, arrival/retaliation transitions and unload/reload on actual End terrain.

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
  Cleanup removes them without touching unrelated entities. Attack children release expired
  references when they close. The managed dragon and participants are not artifacts.
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

The completed #138 encounter reuses the same `EncounterWork` guard in a private attack scope.
Closing that scope at the final gate cancels Hunt indicators/damage/landing work without closing
attempt-owned restoration, the continuous absence timer or the rolling explosion clock/budget.
Every full termination closes both scopes; recovery recognizes live artifacts from either scope.
The finale threshold snapshots the effective native maximum at attempt start, preserving modifiers.

## Main actions and lingering hazards (#149)

The attack scope owns a **main-action child** and separate **hazard children**. All current attack
entry points, including debug triggers, run setup and subsequent callbacks through these existing
`EncounterWork` scopes. Closing a main child signals completion once by releasing its executing
membership. Repeated completion/cancellation is harmless; it does not close sibling hazards.

| Main action | Completion boundary | Lingering work |
| --- | --- | --- |
| Dive / Ground Smash | Native movement recovery releases flight control; cancellation/failure also completes | None in the current moving attacks |
| Prey | All countdown deposition callbacks finish (including skipped targets) | Each pool owns its display/render/expiry independently; all owned pools share the per-player damage-rate rule |
| Bombardment | Warning resolves or is cancelled, and the impact damage callback finishes | None in the current single-impact attack |
| Landing | Native approach deadline or melee-window end releases the reservation | None |
| Legacy stationary lane | Warning resolves into an active lane | Lane rendering/damage/expiry remain separately owned; existing two-lane limit is retained |

Setup, native-flight, rendering, refresh and damage exceptions close the applicable child and log
the failure. Pool failure removes pool membership without aborting the Encounter coordinator.
Ownership is installed before metadata/rendering that may fail. Spawn callbacks revalidate the
specific owner, not merely the same dragon or phase: synchronous pause/phase replacement cannot
attach a new warning to an already-closed owner. Cancelled scheduler callbacks are inert even if
Paper queued them before cancellation. Cleanup is idempotent and logs failures independently,
so one failed cancellation/restoration/removal does not suppress other work or native-flight release.

`/dragondebug status` shows `main=executing|idle`, `attack=<name>|none`, and `hazards=<count>`
alongside the existing lane/mark/area/native-phase details. For example, trigger Prey, wait for
deposition, and inspect `main=idle, attack=none, hazards=1`: the visible pool still damages until
expiry. `cancel` or `pause` removes both executing and lingering work and leaves automatic attacks
paused. `trigger` replaces all existing attack work before starting its requested production action;
`resume` does the same cleanup before re-enabling automatic coordination. Final phase replacement,
60-second reset, victory, disable and world unload close both main actions and hazards.

No new lifecycle toggle or timing knob is needed. Existing validated, per-attempt warning,
movement, pool-lifetime/damage and landing tuning remains authoritative; rotation redesign follows
in the approved successor tickets. Automated command/event tests are not live Paper combat evidence;
integrated #163 must verify active pool cancellation, native release and real rendering on Paper.

Prey #160 now uses this separation for scheduling: existing pools do not hold the executing slot or
delay a melee landing, and are not removed by ordinary main completion/landing. The shared production
`depositBreathPool(ground, radius, lifetimeSeconds)` boundary lets Rain supply its own size/lifetime
without duplicating ownership or damage policy. Overlapping/staggered pools share one rolling
per-player cooldown on the attempt's game-tick clock; cleanup clears both hazard and rate membership.
See the [Prey configuration and operator checks](/shattered-crown-hunt/#marked-prey-and-shared-pools-160).

## Historical automated evidence (#139 baseline)

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
