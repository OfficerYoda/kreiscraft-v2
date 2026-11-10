---
title: Shattered Crown terrain impacts
---

# Permanent Ground Smash (#161)

Ground Smash unlocks after all four wave-three crystals are destroyed. `/dragondebug trigger smash`
uses the same production action; manual triggers bypass the unlock, not player eligibility.
The FULL four-second warning has particles every quarter-second, a floating countdown and a growl.
The native, AI-enabled dragon rises during that warning, plunges for one second, remains grounded
for three seconds, then climbs for two. Main completion follows the climb, never the impact.
Very distant approaches stage bounded native movement before the warning. Native moves remain at
most eight blocks per tick throughout the radius-192 combat area.

At actual physical impact, one hit covers an inclusive eight-block radius. `sweep-damage: 12`
means **12 HP / six hearts lost by full Protection IV netherite without Resistance or absorption**,
on Easy/Normal/Hard, through the calibrated dragon-attributed, cancellable damage boundary.
Other defenses and arrival PLAYER protection remain effective. Damage cancellation alone does not
undo the crater. Cancelling the action before impact prevents impact and all later queued work.

## Ground and debris

The impact removes the supported top layer within roughly five blocks and launches those actual
block materials as falling blocks. Columns use current supported ground within six vertical blocks;
absent columns are skipped. The default one-block depth follows terrain relief rather than excavating
a sphere. There is no native explosion or second damage source.

**No terrain-preservation zones exist for arrival, the portal, towers or Anchor foundations.**
Bedrock/obsidian are not specially exempted. Permanent removal can destroy builds and create holes.
Back up the End before enabling the Encounter. Backup restoration or repair is an operator decision,
not an Encounter cleanup feature. Victory, reset, cancellation, unload, disable and process restart
never rebuild craters. Objective gates still depend on defender deaths and player crystal attacks,
not block changes. Later Anchors reuse fixed X/Z with current highest-ground/empty-column fallback
and may float.

Native falling-block flags `hurtEntities=false`, `dropItem=false`, `cancelDrop=true` prevent damage,
drops and collision placement. Work registration and persistent `kreiscraft:crown_artifact=debris`
tagging happen before spawn callbacks. A sibling hazard/debris child owns expiry/entities; cancellation removes
them. Startup/chunk recovery removes stale tagged debris, preserving unrelated falling blocks.
Closed callbacks cannot restart work. There is no terrain restoration journal.

## Server Configuration

Values are validated on atomic reload/editor publication and snapshotted for the next attempt.
English/German controls include crater tuning. Crown uses installed InvUI pagination so all controls
remain reachable without overwriting the module toggle or back button.

| Key | Default | Bounds / units |
| --- | --- | --- |
| `sweep-radius` | 8 | 4–16; physical Smash caps at 8, legacy sectors use the full value |
| `sweep-warning-seconds` | 4 | 4–15 seconds; cadence must exceed warning by 0.5 seconds |
| `sweep-damage` | 12 | 0–100 reference HP for physical Smash; raw units for legacy sectors |
| `smash-crater-radius` | 5 | 1–8 blocks |
| `smash-crater-depth` | 1 | Integer 1–3 removed layers |
| `debris-lifetime-seconds` | 4 | 0.5–10 seconds; cancellation may remove debris sooner |

At most 128 visible debris entities launch per impact. Larger tuned patches still remove all selected
ground. Normal defaults make shallow readable craters, not mass excavation.

## Placement policy and rationale

The approved destructive redesign replaces the former preservation/strict walking-route guarantee.
Bombardment, Dive, Smash, Prey pools and native landing no longer veto valid attacks solely because
cratered ground lacks a straight body-sized walking escape or podium approach. Supported target
footing, build-height/arena bounds, existing-hazard overlap restrictions, warning durations and
native-flight limits remain. Surviving columns define projected geometry; holes do not invalidate
an entire footprint. Changes to captured supported footing during a warning still invalidate that
warning rather than silently move it. Arrival exclusion applies to PLAYERS, not footprint rims/blocks.
Defender shield/crystal gates and player eligibility remain independent.

Destruction should be consequential while later attacks/objectives remain playable without hidden
islands of preserved footing or automatic repairs. Safe walking escape is no longer promised.
Older #136–138 historical reports describe their original contract, not this destruction policy.

## Shared terrain seam for Fireball Rain (#162)

`EncounterController.impactTerrain(center, radius, depth)` is the smallest production entry point:
call it with **2, 1** for Rain. It registers a sibling hazard with configured debris lifetime, so
main completion cannot remove longer-lived debris. Attack cancellation still closes every sibling.

`TerrainImpact.apply(center, radius, depth, lifetimeSeconds, owner)` is package-local and server-thread
only. Rain can pass radius **2**, depth **1**, configured debris lifetime and an existing owned effect
scope at each actual impact. It removes ground and creates a debris child; it does **not** deal damage,
create explosions/pools, close the supplied owner, signal main completion or restore ground.
The caller owns exactly-once impact timing and its separate calibrated 4 HP hit. If debris must
outlive the Rain main action, pass a sibling hazard scope, not the main-action child.

## Runnable evidence

Encounter/public-event tests cover eight-block boundaries, timing, once-only calibrated damage,
arrival/Anchor footing with independent protection/gates, damaged-ground follow-ups, termination
matrices, stale recovery and atomic configuration. Paper132 exercises actual native behavior:

```sh
./gradlew check build smashEncounterProbeJar --max-workers=2
JAVA_HOME=... python3 scripts/run-dragon-wave-probe.py NEW_EVIDENCE_DIR PAPER132_CACHE \
  build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar smash
```

The probe uses genuine registered native players with synthetic network sinks, an AI-enabled battle,
debug Easy/Normal/Hard and automatic final-unlock Smash, full warning/choreography, real flying-block
collisions/no drops/placement/extra damage, cancellation and damaged-ground production follow-ups.
The runner restarts the same saved world to verify crater persistence, stale debris recovery and an
unrelated falling block surviving. No production gameplay is copied into the probe. External
`sc-161-evidence.md` records artifact/log hashes and RED/GREEN results. Packet-sink emission is not
authenticated human rendering or four-human balance.
