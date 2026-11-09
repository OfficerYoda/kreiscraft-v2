---
title: The Shattered Crown
---

# The Shattered Crown: single-Anchor slice

Implementation of ticket #134, following spec #132. This opt-in intermediate slice has **one**
Anchor and no custom dragon attacks or finale yet. After its defenders and crystal are defeated,
the normal dragon is damageable and can be killed normally. Numerical balance is **unvalidated**.

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
| `arena-radius` | 80 | 16–256 blocks, horizontal cylinder around the battle podium |
| `arena-min-y` / `arena-max-y` | 40 / 160 | World heights, −64–320, strictly increasing |
| `arrival-x` / `arrival-z` | 100 / 0 | X/Z offsets from the actual battle podium; −512–512 |
| `arrival-y` | 50 | Arrival center world height; −64–320 |
| `arrival-radius` | 8 | Protected cylinder radius, 3–32 |
| `arrival-height` | 12 | Cylinder's total height, 4–64 |
| `anchor-distance` | 24 | Desired ground objective east of the podium, 8–192 |
| `ground-search-radius` | 8 | Bounded horizontal search around that position, 0–16 |
| `ground-max-y` | 90 | Highest allowed objective feet Y; inside arena height bounds |

Damage is in health points (two per heart). All numeric values must be finite. The complete Anchor
search footprint must fit within the arena. The absence reset is fixed at **60 continuous seconds**
of scheduler time (1200 game ticks), not a population-based timer or a forced fight duration.

Paper has no public arrival-platform-position getter. The configured cylinder explicitly describes
the actual arrival area: defaults cover the vanilla platform at `(100, 50, 0)` for the ordinary
podium at the island origin. On servers relocating the podium or arrival destination, adjust these
offsets and height to the real platform. Arena X/Z are resolved from `EnderDragon.getPodium()`.
Arrival protection is excluded even where it overlaps the combat cylinder.

Ground resolution requires solid footing and three clear blocks over an entire 5×5 patch inside
the combat arena and outside arrival protection. It never builds platforms or places blocks.
If no usable ground exists, no encounter starts, the existing dragon remains unchanged, and a
warning identifies the geometry/terrain problem. Discovery retries after 30 seconds or a reload.

## Behavior and ownership

- Living, online survival/adventure players in the arena start and sustain an attempt. Dead players,
  creative/spectator players and protected arrivals do not. Movement, teleport, quit, death and
  game-mode events update absence timing; a one-second module discovery/maintenance task also
  catches late world/dragon availability and returning players. No teleport, inventory or travel
  policy is changed.
- Defenders use native AI. Their own deaths expose the crystal; an exposed crystal glows. Reinforcement
  spawning and transformations are disabled for owned defenders. Missing defenders are replaced
  within the remaining wave budget, escaped defenders return to the objective, and missing Anchors
  are recreated without clearing the defender gate. Refused spawns abort and restore the battle.
- Essential per-recipient localized instructions and a once-per-second action bar communicate the
  shield, remaining defenders, exposed Anchor and damage window. They use the existing message
  service's Player Settings language resolution. The vanilla dragon boss bar is untouched.
- Shield cancellation handles normal and multipart damage events, including explosions and arrows.
  Ordinary crystal healing is cancelled only for the currently managed dragon. Exposed Anchor hits
  from eligible players remove just that objective and suppress its explosion and ignition.
- Protected arrivals cannot attack encounter enemies. Current source/shooter location is checked,
  and projectiles/primed TNT originating in protection retain a persistent origin marker even after
  moving into the arena. Owned defenders also cannot damage protected arrivals. Future custom
  attacks can share `EncounterController.isParticipant` and `isProtectedArrival`.
- Victory clears custom artifacts without editing XP, drops, portal, egg, gateway or ritual state.
  It never removes or simulates a kill of the dragon. Reset restores the existing dragon's original
  native maximum and full health; the next participant begins a fresh configured-health attempt.
  Disable/unload restore its original maximum while retaining its health fraction.
- One module-level discovery task remains while enabled to discover new attempts; the attempt's
  absence callback is cancelled on every termination. Disable unregisters its listeners and cancels
  discovery too. There are no custom delayed damage tasks in this slice. No world-wide per-tick scans.
- PDC ownership keys are `kreiscraft:crown_artifact` (`anchor`/`defender`) and
  `kreiscraft:crown_original_health` (original dragon maximum-health base value).
  `kreiscraft:crown_protected_shot` records an indirect attack's protected origin, not entity ownership.
  Recovery on module startup and entity/world load removes stale owned artifacts and restores the
  same surviving dragon, without rewards or duplicate dragons. It resumes no attack or phase.
  Recovery listeners run only while this module is enabled; disabled-at-startup recovery policy is
  part of the later full restart lifecycle work.

## Automated evidence

`EncounterTest` drives module enable/reload/disable, Bukkit events, real MockBukkit world entities,
player movement and `MockScheduler`. It is the single approved encounter-level seam.

Red→green cycles covered: objective-to-victory path; participation/geometry; exact 59.999/60-second
reset and return cancellation; damage/healing/protected arrows; defender escape/removal; localized
instructions; atomic reload snapshots/disable; unload/reload; restart ownership recovery; missing
Anchor recovery; cancelled damage/death and untouched reward event; crystal ignition; refused spawns;
health bounds; protected TNT; finite defender waves; pre-start protected projectiles; first-tick
damage and idle-dragon platform protection. Additional acceptance scenarios cover disabled
behavior, unusable ground, another End world, a subsequent ritual dragon, duplicate callbacks, and
eight repeated resets with spectator/dead-player occupancy.

MockBukkit limitations are isolated and explicit:

- `WorldMock.getEnderDragonBattle()` is unimplemented. `DragonBattleAccess` substitutes only that
  lookup; production uses supported `World::getEnderDragonBattle` directly.
- `LivingEntityMock.setRemoveWhenFarAway(false)` is unimplemented. The adapter's default production
  method calls it; the fixture omits that one setter and provides missing native Husk attribute
  metadata. Real removal/replacement is tested separately.
- `EnderDragonMock.getParts()` is unimplemented. A single `ComplexEntityPart` envelope refers to the
  real mocked-world dragon in multipart damage tests.

The encounter suite explicitly fails on unexpected MockBukkit test-abort exceptions rather than
silently reporting skipped acceptance tests. No new fake combat engine or NMS layer is introduced.

Final checks for this slice:

```sh
JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 \
  ./gradlew spotlessApply check -x testNms --console=plain
git diff --check
```

Passed: 22 encounter tests, none skipped; 1,330 total tests, zero failures, with five existing
`ServerMetricsCollectorTest` skips. Spotless, Checkstyle, production compilation with `-Werror`,
shadow JAR creation and InvUI compatibility verification passed. Version-specific NMS execution was
excluded because no NMS code changed. Use a Java 25 Gradle launcher: this environment's default
Java 27 launcher is incompatible with Spotless's unused-import remover.

## Paper evidence and remaining live checklist

Target: Java 25 / Paper 26.2. No running Paper server or multiplayer participants were available in
this worktree environment; **no live combat, rendering, AI or balance evidence is claimed**.

Before deployment validation, run this slice on the supported Paper server and record:

1. Main-End initial and ritual dragon discovery, real podium/platform alignment, accessible Anchor
   terrain, visible locale/status cues, defender targeting/leash and despawn behavior.
2. Real multipart sword/arrow/explosion shield rejection; exposed crystal breaking; crystal healing
   suppression and restoration; protected-arrow/TNT origins; ignition/explosion suppression.
3. Kill through vanilla completion and verify initial vs repeated XP, portal, egg/gateway and ritual
   eligibility. Then reset, unload and disable without a rewarded kill or leftover entities.
4. Representative ~20-player mixed-gear fight: attendance, gear, duration, deaths, objective
   participation, melee/ranged contribution and tick/performance observations. Full spec #132
   balance/15–20-minute validation also requires the later four-Anchor, attack and finale slices.
