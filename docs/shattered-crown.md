---
title: The Shattered Crown
---

# The Shattered Crown: four-Anchor opening

Implementation of tickets #134–#135, following spec #132. **Break the Crown** creates four
independently defended ground-level Anchors and warned, finite bombardments. Destroying all four
immediately releases the shield and cancels opening attacks for **Weather the Hunt**. Hunt attacks
and the finale are subsequent slices; the dragon can currently be killed normally after the opening.
Numerical balance is **unvalidated**.

## Enable and tune

Set `modules.shattered-crown.enabled: true` and restart. The module has no feature-module
dependencies and does not enable Dimension Access. `world-key` defaults to `minecraft:the_end`;
use the actual namespaced key for the designated main End, not its legacy Bukkit directory name.
Only that world's `DragonBattle.getEnderDragon()` is managed, including subsequent vanilla ritual
respawns. Command-spawned dragons and other End worlds are not selected.

The Server Configuration GUI exposes combat and geometry tuning. World identity stays YAML-only.
`/kreiscraft reload` validates the whole module section before replacing the next-attempt profile.
An existing attempt keeps its original profile, including geometry and replacement defenders.
Setting `enabled: false` and reloading aborts immediately; enabling again requires a restart, as
with the repository's other module enable flags.

### Shipped profile

All keys below are under `modules.shattered-crown`. These are fixed settings intended as a starting
point for about 20 mixed enchanted diamond/netherite players, not dynamically scaled by attendance.

| Key | Default | Accepted bounds / meaning |
| --- | --- | --- |
| `max-health` | 1000 | 1–1024; conservative native attribute range, no virtual health |
| `defender-count` | 4 | 1–32, per Anchor |
| `defender-type` | `HUSK` | `HUSK` or `ZOMBIE`, melee defenders |
| `defender-health` | 40 | 1–1000 |
| `defender-damage` | 6 | 0–100, native attack attribute |
| `defender-leash` | 16 | 4–32 blocks from the Anchor |
| `bombardment-warning-seconds` | 3 | 1–15 seconds before impact |
| `bombardment-cadence-seconds` | 8 | 2–120 seconds between warning starts; first warning after this interval |
| `bombardment-damage` | 6 | 0–100 health points, native dragon-breath damage and normal combat protections |
| `bombardment-radius` | 4 | 1–12 blocks; circular impact, at most 3 blocks vertically from warned ground |
| `arena-radius` | 80 | 16–256 blocks, horizontal cylinder around the battle podium |
| `arena-min-y` / `arena-max-y` | 40 / 160 | World heights, −64–320, strictly increasing |
| `arrival-x` / `arrival-z` | 100 / 0 | X/Z offsets from the actual battle podium; −512–512 |
| `arrival-y` | 50 | Arrival center world height; −64–320 |
| `arrival-radius` | 8 | Protected cylinder radius, 3–32 |
| `arrival-height` | 12 | Cylinder's total height, 4–64 |
| `anchor-distance` | 24 | Desired cardinal ground objectives east/north/west/south of the podium, 8–192 |
| `ground-search-radius` | 8 | Bounded horizontal search around each position, 0–16 |
| `ground-max-y` | 90 | Highest allowed objective feet Y; inside arena height bounds |

Damage is in health points (two per heart). All numeric values must be finite. The complete Anchor
search footprint must fit within the arena. Cadence must exceed warning duration by at least
0.5 seconds; `radius + 2 <= warning-seconds * 3` reserves conservative walking time to escape.
Invalid combinations reject the reload atomically, leaving the running profile unchanged.
The absence reset is fixed at **60 continuous seconds**
of scheduler time (1200 game ticks), not a population-based timer or a forced fight duration.

Paper has no public arrival-platform-position getter. The configured cylinder explicitly describes
the actual arrival area: defaults cover the vanilla platform at `(100, 50, 0)` for the ordinary
podium at the island origin. On servers relocating the podium or arrival destination, adjust these
offsets and height to the real platform. Arena X/Z are resolved from `EnderDragon.getPodium()`.
Arrival protection is excluded even where it overlaps the combat cylinder.

Ground resolution requires solid footing and three clear blocks over an entire 5×5 patch inside
the combat arena and outside arrival protection. The four sites must be more than 12 blocks apart,
and all four are preflighted before changing the dragon or spawning objectives.
It never builds platforms or places blocks. If any site lacks usable ground, no encounter starts,
the existing dragon remains unchanged, and a
warning identifies the geometry/terrain problem. Discovery retries after 30 seconds or a reload.

## Behavior and ownership

- Living, online survival/adventure players in the arena start and sustain an attempt. Dead players,
  creative/spectator players and protected arrivals do not. Movement, teleport, quit, death and
  game-mode events update absence timing; a one-second module discovery/maintenance task also
  catches late world/dragon availability and returning players. No teleport, inventory or travel
  policy is changed.
- The opening has exactly four independent Anchors, each with four defenders by default (16 total).
  Teams can clear them in any order or concurrently. Only an Anchor's own remaining wave gates its
  crystal; only encounter-owned crystals advance progress. Duplicate deaths/hits cannot count twice.
  Defenders use native AI. Their own deaths expose the crystal; an exposed crystal glows. Reinforcement
  spawning and transformations are disabled for owned defenders. Missing defenders are replaced
  within the remaining wave budget, escaped defenders return to the objective, and missing Anchors
  are recreated without clearing the defender gate. Refused spawns abort and restore the battle.
- Essential per-recipient localized instructions and a once-per-second action bar communicate the
  phase, shield, remaining Anchors, exposed count, total defenders and damage window. They use the existing message
  service's Player Settings language resolution. The vanilla dragon boss bar is untouched.
- Shield cancellation handles normal and multipart damage events, including explosions and arrows.
  Ordinary crystal healing is cancelled only for the currently managed dragon. Exposed Anchor hits
  from eligible players remove just that objective and suppress its explosion and ignition.
- Protected arrivals cannot attack encounter enemies. Current source/shooter location is checked,
  and projectiles/primed TNT originating in protection retain a persistent origin marker even after
  moving into the arena. Owned defenders also cannot damage protected arrivals. Future custom
  attacks share `EncounterController.isParticipant` and `isProtectedArrival`.
- Bombardments choose eligible players in deterministic UUID-sorted round-robin order. One fixed
  ground circle is warned at a time: a 32-point native flame ring refreshes four times per second,
  a native TextDisplay shows a numeric countdown, and localized action-bar timing plus a dragon
  growl announces the warning. Impact uses a distinct explosion visual/sound, not a real explosion.
  Moving out avoids damage; the warned area does not chase the target. Revalidation cancels the
  attack if its selected player dies, disconnects, changes worlds/mode, leaves combat or enters
  arrival protection, or if its warning disappears. No impact footprint may overlap the protected
  arrival cylinder. Native damage is applied once and honors other plugins' combat cancellation.
- Placement and resolution check actual footing and a reachable straight walking corridor for
  every endangered participant (16 directions, half-block samples with player-body clearance,
  conservative 3 blocks/second). Point-sized diagonal gaps between walls do not count as routes.
  Nearby native dragon-breath clouds are considered existing danger: overlapping bombardments
  and routes through those pools are omitted. More than 32 nearby clouds also refuses the pattern.
  Terrain that changes during the warning is checked again at impact. Unsafe placements are
  omitted rather than stacking danger or constructing terrain; another cadence may try again.
  Custom bombardments leave no persistent damaging pool and never change blocks. This conservative
  placement can skip attacks on steep or obstructed terrain; live geometry/rendering remains unvalidated.
- Victory clears custom artifacts without editing XP, drops, portal, egg, gateway or ritual state.
  It never removes or simulates a kill of the dragon. Reset restores the existing dragon's original
  native maximum and full health; the next participant begins a fresh configured-health attempt.
  Disable/unload restore its original maximum while retaining its health fraction.
- One module-level discovery task remains while enabled to discover new attempts; the attempt's
  absence callback is cancelled on every termination. Disable unregisters its listeners and cancels
  discovery too. Opening cadence, warning refresh and pending damage are all cancelled on the last
  Anchor, victory, reset, disable or unload. A synchronous combat event ending the attempt stops the
  remaining impact audience too. No world-wide per-tick scans.
- Active ownership is bounded at four crystals, 16 shipped defenders (128 at maximum configuration),
  and one warning display. At most five active module/attempt scheduler tasks coexist, including discovery
  and absence; completed attacks do not accumulate active tasks. Particle audiences are capped at 64,
  with 32 ring points per refresh (2,560 ring particles/second for 20 recipients while warned).
  The nearby cloud query runs only at planning/resolution, not every tick.
- PDC ownership keys are `kreiscraft:crown_artifact` (`anchor`/`defender`/`warning`) and
  `kreiscraft:crown_original_health` (original dragon maximum-health base value).
  `kreiscraft:crown_protected_shot` records an indirect attack's protected origin, not entity ownership.
  Recovery on module startup and entity/world load removes stale owned artifacts and restores the
  same surviving dragon, without rewards or duplicate dragons. It resumes no attack or phase.
  Recovery now runs independently of combat enablement, including disabled startup and later-loaded
  chunks. See the [reload/restart lifecycle contract and evidence](/shattered-crown-lifecycle/).

## Automated evidence

`EncounterTest` and `OpeningEncounterTest` drive module enable/reload/disable, Bukkit events,
real MockBukkit world entities, player movement and `MockScheduler` at the single approved
encounter-level seam. The four-objective test demonstrates the complete playable opening through
shield release, including one team's independently exposed Anchor and arbitrary/concurrent completion.

Red→green cycles covered: objective-to-victory path; participation/geometry; exact 59.999/60-second
reset and return cancellation; damage/healing/protected arrows; defender escape/removal; localized
instructions; atomic reload snapshots/disable; unload/reload; restart ownership recovery; missing
Anchor recovery; cancelled damage/death and untouched reward event; crystal ignition; refused spawns;
health bounds; protected TNT; finite defender waves; pre-start protected projectiles; first-tick
damage and idle-dragon platform protection. Additional acceptance scenarios cover disabled
behavior, unusable ground, another End world, a subsequent ritual dragon, duplicate callbacks, and
eight repeated resets with spectator/dead-player occupancy. Opening coverage adds native warning
entity location/countdown/lifetime and sounds, localized timing, exact warning-before-impact damage,
impact footprint bounds, moving targets, six invalidation routes, arrival overlap refusal,
void/corridor and existing breath danger, refused/unloaded warnings, combat-event cancellation,
all phase/termination cancellations, invalid bombardment tuning and next-attempt snapshots.
Another eight retries run 96 rapid, serialized warning/impact cycles and partial objective progress
without accumulating owned entities or carrying gates into the next attempt.

MockBukkit limitations are isolated and explicit:

- `WorldMock.getEnderDragonBattle()` is unimplemented. `DragonBattleAccess` substitutes only that
  lookup; production uses supported `World::getEnderDragonBattle` directly.
- `LivingEntityMock.setRemoveWhenFarAway(false)` is unimplemented. The adapter's default production
  method calls it; the fixture omits that one setter and provides missing native Husk attribute
  metadata. Real removal/replacement is tested separately.
- `EnderDragonMock.getParts()` is unimplemented. A single `ComplexEntityPart` envelope refers to the
  real mocked-world dragon in multipart damage tests.
- `DisplayMock.setBillboard()` is unimplemented. The adapter omits only viewer-facing orientation;
  TextDisplay location, text, lifetime and cleanup are real observable world effects in these tests.
- `LivingEntityMock.damage(double, DamageSource)` is unimplemented. The adapter uses MockBukkit's
  existing `LivingEntitySimulation.simulateDamage` with the same native dragon-breath DamageSource,
  exercising actual public combat events, cancellation and health changes. Production calls Paper's
  native damage API directly; the tests do not establish real armor/AI behavior.
- MockBukkit validates particle calls but does not record/render particles. Real ring appearance,
  visibility, terrain alignment and particle performance require Paper testing.

The encounter suite explicitly fails on unexpected MockBukkit test-abort exceptions rather than
silently reporting skipped acceptance tests. No new fake combat engine or NMS layer is introduced.

Final checks for this slice:

```sh
JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 \
  ./gradlew spotlessApply check -x testNms --console=plain
git diff --check
```

Use a Java 25 Gradle launcher: this environment's default
Java 27 launcher is incompatible with Spotless's unused-import remover.

Passed for #135: **35 opening tests + 22 existing encounter tests**, none skipped; **1,365 total
tests**, zero failures/errors, with five existing `ServerMetricsCollectorTest` skips. Spotless,
Checkstyle, production compilation with `-Werror`, shadow JAR creation and InvUI compatibility
verification passed. Version-specific NMS test execution was excluded because no NMS code changed.

## Paper evidence and remaining live checklist

Target: Java 25 / Paper 26.2. No running Paper server or multiplayer participants were available in
this worktree environment; **no live combat, rendering, AI or balance evidence is claimed**.

Before deployment validation, run this slice on the supported Paper server and record:

1. Main-End initial and ritual dragon discovery, real podium/platform alignment, all four accessible Anchor
   terrain, visible locale/status cues, defender targeting/leash and despawn behavior.
2. Real multipart sword/arrow/explosion shield rejection; exposed crystal breaking; crystal healing
   suppression and restoration; protected-arrow/TNT origins; ignition/explosion suppression.
3. Kill through vanilla completion and verify initial vs repeated XP, portal, egg/gateway and ritual
   eligibility. Then reset, unload and disable without a rewarded kill or leftover entities.
4. Representative ~20-player mixed-gear fight: attendance, gear, duration, deaths, objective
   participation, melee/ranged contribution and tick/performance observations. Inspect every circle's
   countdown, visible area and escape route, plus phase-boundary and protected-arrival cancellation.
   Full spec #132 balance/15–20-minute validation also requires the later Hunt attack and finale slices.
