---
title: The Shattered Crown
---

# The Shattered Crown: complete encounter

## Encounter communication policy (#156)

Automatic Encounter output stays **in the world**, not in chat or above the hotbar. Opening,
Anchor exposure, shield changes, wave/finale transitions, attack announcements and landing states
produce no automatic chat, action bars or replacement title feed. This applies to new waves and
attacks too, including debug-triggered attacks and both moving and legacy profiles; no opt-in
chat/actionbar setting is provided.

Keep floating labels/countdowns, sounds and applicable Attack Warning visuals. Particle redesign
belongs to individual attacks, not this policy. The vanilla dragon boss bar is untouched; the
enhanced-Wither boss/countdown presentation remains available and is not suppressed by Crown UI.
Only **explicitly requested** `/dragondebug` results, errors and status use chat, through the
recipient-localized message service. Status fields/attack enum names and low-level placement or
exception diagnostics remain technical identifiers; the surrounding reply and eligibility guidance
are translated in English/German. Replies go only to the requesting operator, not attack targets.

When adding an attack, observe its world indicators, sounds, native phases and damage at the
encounter seam; do not use announcement text as evidence that it ran. Assert no unsolicited chat or
action bars over a complete sequence, and retain command-reply coverage. Existing independent UI
modules (for example a player's Combat Display preference) are not disabled by this Encounter rule.

## Three Anchor Waves (#151)

Exactly four defended Anchors appear at **100%, 65% and 30%** of the effective maximum health
snapshotted at attempt start. Every gate is once-only. All damage routes (including oversized hits,
explosions and multipart callbacks) stop at the next uncleared gate before native damage/death;
accepted damage installs the shield before any objective spawn callback can reenter combat.
Crystal healing is disabled. Defender deaths expose only their assigned crystal; all four crystals
must then be destroyed by eligible player attacks, in any order. Mob damage, explosions, unrelated
crystals and protected-origin attacks cannot complete an objective.

Each Anchor receives **ceil(wave population / 4) complete Roster Units**, without a population cap:
0→0, 4→1, 5/8→2, 12→3, 20→5, 68→17. An empty debug End still has four exposed crystals to destroy.
Wave one freezes #150's first-End-entry server-wide population;
waves two and three freeze everyone connected in the managed End at their starts, including creative,
spectator and outside-arena players. Later joins/leaves never resize existing defenders.

Bombardment and Prey are unlocked initially; Dive after wave one, Fireball Rain after wave two,
Ground Smash after wave three. Unlocks survive later shields. Rain choreography and complete
rotation redesign remain downstream work. Wave one is the mounted-undead roster below; the configured
defender roster remains the playable placeholder in waves two/three until #154–155 supply content.
The explicit extension point is
`EncounterController.spawnRosterUnit(Anchor objective, int wave, int unit)`: replace one unit's
content there using `spawnCombatant(location, type, objective, initializer)` for roots. That helper
registers ownership and membership before public spawn callbacks; never add the returned Mob again.
Use the parent overload for descendants and `inheritOwnership` in projectile/effect pre-spawn consumers.
All are owned by `waveWork`; do not scale again or equate defender deaths with crystal completion.

### Wave one: mounted undead

Each complete unit at **each** Anchor contains four Defender Groups:

- Adult Zombie with an unenchanted `IRON_SPEAR` riding a Zombie Horse.
- Skeleton with a bow riding a Spider.
- Bogged with a bow riding a Cave Spider.
- An actual chain of4–6 undead passengers, rotating through Husk, Zombie, Drowned, Skeleton,
  Stray, Bogged and Wither Skeleton across the wave. Archers retain bows; Wither Skeletons have swords.

Towers end in a Skeleton, Stray or Bogged archer. Native passengers steer the stack; a melee-only
crown several bodies above the ground cannot reliably hit players below. This retains native
movement/combat without custom attack goals or a second damage engine.

At12 initial online players each Anchor has three units:12 groups, not12 individual defenders.
Four online players produce16 groups/43 members across all four Anchors;12 produce48 groups/132 members.
Tower height/species rotate deterministically by Anchor and unit. About35% of armor-capable undead
receive one or two unenchanted leather/chainmail pieces with occasional iron (11/31 at one unit/Anchor).
No full sets, diamond/netherite or armor enchantments; natural equipment is cleared and item pickup
disabled. Armor choice is captured for species-preserving recovery, never rerolled into another life.
`defender-health` applies to every mount/passenger; `defender-damage` tunes supported native melee
attributes, not bow/spear item damage. The roster is active during the30-second Opening Grace.

Every mount and passenger must die before its crystal glows. Dismounting, broken towers and roaming
do not clear a member; missing/unloaded members get only the existing bounded replacements, and
accepted deaths are never replenished. Zombie reinforcements are zero and owned transformations
are cancelled. Reset/debug replacement/disable/unload clean all members and inherited projectiles.
The native build132 probe exercises real mount pursuit, spear melee and physical mounted/tower arrows;
it does not substitute teleports or direct scripted damage for AI combat.

Operator native check, using a fresh owned evidence directory and accepted Paper132 cache:

```sh
./gradlew check build openingRosterProbeJar --max-workers=2
python3 scripts/run-dragon-wave-probe.py NEW_EVIDENCE_DIR PAPER_CACHE_DIR \
  build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar opening
```

Set `JAVA_HOME` to Java25. The probe's native registered player uses synthetic transport, not a human
client; its raised calibration path leaves Anchor terrain unchanged and proves server AI/physical
damage, not rendered readability, multiplayer balance or a complete fight. `/dragondebug wave 1`
uses the same production roster and pauses automatic dragon attacks, not defenders.

All waves reuse four fixed horizontal sites. Crystals use the current highest block plus one-block
placement clearance, or their last known height for an empty column (podium height on first use).
No flat foundation is required, preserved or rebuilt. Defenders prefer nearby surviving footing;
missing/unloaded members are replaced, never counted as defeated. Refused spawns abort safely to a
retryable native battle rather than leaving partial objectives. Reset, victory, unload, disable and
restart recovery remove only Encounter-owned artifacts. Native dragon completion/rewards are retained.

`/dragondebug wave <1|2|3>` uses the existing OP-default permission, replaces owned enemies,
pending gate acceptance and attacks/effects, sets 100/65/30% health, grants earlier-wave unlocks and
takes fresh scaling snapshots. It pauses automatic attacks; `/dragondebug resume` resumes the
selected production shield/hunt phase. Opening Grace is not restarted or bypassed by phase jumps.
Status reports wave, shield, destroyed crystals out of four, defenders, frozen population/units,
unlocks and manual/automatic mode alongside existing population/grace/attack diagnostics.
Explicit replies remain recipient-localized; automatic chat/actionbars remain absent.

```text
/dragondebug status
/dragondebug pause
/dragondebug wave 1
/dragondebug wave 2
/dragondebug wave 3
/dragondebug trigger dive <player>
/dragondebug cancel
/dragondebug resume
```

Native fixture check (Java25/Paper26.2 build132, zero human clients):
`./gradlew check build dragonWaveProbeJar --max-workers=2`, then
`python3 scripts/run-dragon-wave-probe.py NEW_EVIDENCE_DIR PAPER_CACHE_DIR` with `JAVA_HOME` set.
The runner refuses existing evidence directories, copies only Paper caches/EULA, uses fresh worlds
and loopback ports, stages the real shadow plugin plus a test-only probe, and requires an explicit
PASS. Registered native ServerPlayers use a packet sink, not authenticated clients. This exercises
native main-End battle creation/adoption, population snapshots, native damage/death/crystal paths,
debug replacement and disable cleanup; it does not prove rendered warnings, defender AI, human
balance, ritual completion or native reward delivery. Those remain integrated #163 evidence.

## Live-playtest follow-up: moving attacks

The bundled module is **enabled by default**, with `movement-attacks: true`
(YAML-only, snapshotted per attempt).
This replaces stationary Hunt lanes with a **marked-strip dive** and stationary wing sectors
with a **ground smash**. Set it to `false` to use the earlier stationary attacks described below.

- **Dive:** a **two-second Attack Warning**, **1.5-second physical pass** and **one-second climb**.
  Defaults are a six-block-wide, 24-block-long lane. `lane-length`, `lane-width`,
  `lane-warning-seconds`, `dive-pass-seconds` and `dive-climb-seconds` are validated tuning.
  There are **no Dive particles**: only the floating countdown, growl and real approaching dragon.
  A low passing dragon hits each eligible player at most once; `lane-damage: 8` targets four hearts
  lost against full Protection IV netherite on Easy/Normal/Hard, preserving other defenses and
  public combat cancellation. Existing numeric damage overrides retain their values but physical
  Dive now interprets them as reference-health units. Lane lifetime/damage cadence and raw damage
  semantics apply only to legacy stationary lanes. Knockback uses `sweep-knockback`.
  Very long approaches stage native movement before the visible warning, keeping every native step
  below eight blocks without stretching the two-second countdown. Main action completes only after
  the climb restores native flight; the rotation's next gap can begin then.
- **Smash:** uses sweep cadence, warning, damage and knockback. Its circular radius is capped at eight
  blocks; warning time is at least `(radius + 2) / 3` seconds. The dragon rises, plunges for one second,
  impacts with debris and sound, stays grounded for three seconds, then climbs for two seconds.
  `sweep-angle` applies only to legacy sectors.
- Flight uses an owned native **airborne charge phase**, applying movement inside the dragon tick
  before multipart repositioning, with no repeated teleports or no-AI state. Native contact
  damage, push and block destruction are suppressed during the scripted move. Custom hits follow
  encounter eligibility and combat cancellation. Attack cleanup restores native flight; existing
  persistent phase recovery handles restarts. New attack warnings wait while flight is active.
- Dive strips, smash disks and Prey pools now snapshot local ground heights (up to six blocks of
  relief from the center). Warning geometry and damage use the same surface. Escape checks follow
  one-block steps with player-body clearance, additional uphill time, and void/cloud exclusion.
  Changed footing cancels the attack instead of silently moving its warning.
- The default Hunt rotation retains overdue attacks through other attacks and landings. Oldest due
  attacks get priority; refused placement retries after one second and does not block other families.
  `INFO` diagnostics identify deferred attack families and busy/placement categories at most once per
  family per 30 seconds. Native driver acquisition/interruption failures are logged with their cause.
- Prey resolves nearby ground below a jumping player and renders a filled purple breath disk
  with an activation sound. The shared damage source supplies both direct and causing dragon entities,
  fixing Paper's `Direct entity must be set if causing entity is set` exception. Unsafe placements
  still cancel; reasons are available through the plugin logger at `FINE` level.

Automated checks cover production damage-source attribution, terrain-aware dive timing/dodging/disable,
smash impact/recovery, Prey expiry, and all attack families with the complete default rotation.
A disposable real-Paper probe verifies native ticking, multipart movement and phase restoration;
see [native flight implementation and evidence](/shattered-crown-native-flight/).
**Client-visible flight smoothness, model alignment,
real combat and multiplayer balance still require a new live Paper playtest.**

### Manual attack debugging

Operators (or holders of `kreiscraft.shatteredcrown.debug`) can test production attacks:

```text
/dragondebug status
/dragondebug pause
/dragondebug wave <1|2|3>
/dragondebug trigger <dive|smash|prey|bombardment|landing> [player]
/dragondebug cancel
/dragondebug resume
```

The target defaults to the player running the command; console must name an online player.
The target must be alive in survival/adventure inside the main-End combat arena. An eligible player
entering the arena starts the encounter as usual. Triggers bypass cooldown and phase selection, so
dives and smashes can be tested while the opening Anchors still shield the dragon. Terrain, escape
routes, arrival protection, combat cancellation and the actual warning/damage paths still apply.

`trigger` cancels the previous custom attack and enters manual mode. `pause`/`cancel` clear active
effects, restore flight and leave automatic attacks paused. `resume` restarts the current phase's
automatic schedule with fresh cadences. Neither command clears Anchors or changes dragon health.
`status` reports mode, encounter phase/finale, active attack, queued families, hazard counts, landing
and native flight state. Manual mode ends with the encounter; a reset starts normally.

Paper 26.2 requires a Float payload for `DRAGON_BREATH`. Every Crown breath emission now goes through
`CrownParticles` with an explicit `1.0f`. Tests exercise real Paper `CraftPlayer.spawnParticle`,
registry conversion and outgoing particle construction, with the network connection mocked.
Pool setup, refresh, damage and expiry share an isolated effect scope: a rendering/setup failure
removes its indicator and active-area entry, releasing the rotation instead of leaving it busy.
Fault-injection tests cover both initial rendering and later refresh failures.

## Historical earlier encounter contract (superseded by #151)

Implementation of tickets #134–#139, following spec #132. **Break the Crown** creates four
independently defended ground-level Anchors and warned, finite bombardments. Destroying all four
immediately releases the shield and cancels opening attacks for **Weather the Hunt**. Hunt now adds
warned temporary breath lanes, Marked Prey pools, fixed wing-sweep sectors and supported-Paper melee landing windows. See the
[hunt geometry, tuning and verification contract](/shattered-crown-hunt/).
At 30% health **Shatter the Last Stand** reactivates exactly two cleared sites once, shields the
dragon until their independent defender gates and crystals are cleared, then accelerates familiar
Hunt attacks with shorter pools. Oversized/lethal/multipart hits cannot skip that gate; all managed
dragon parts share a rolling explosion budget. Completion stays with the native battle, without
extra rewards. **Full implementation is not live validation:** numerical balance, actual Paper
combat/AI/rendering and native reward generation remain unvalidated.

## Enable and tune

New configurations default to `modules.shattered-crown.enabled: true`. Existing configurations retain
their saved flag; set it to true and restart to activate. The module has no feature-module
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
| `defender-count` | 4 | 1–32, per later-wave placeholder unit; ignored by wave one |
| `defender-type` | `HUSK` | `HUSK` or `ZOMBIE`, later-wave placeholder only |
| `opening-armor-percentage` | 35 | integer0–50; balanced armor-capable opening undead with1–2 light pieces |
| `defender-health` | 40 | 1–1000 |
| `defender-damage` | 6 | 0–100, native attack attribute |
| `bombardment-warning-seconds` | 3 | 1–15 seconds before impact |
| `bombardment-cadence-seconds` | 8 | 2–120 seconds between warning starts; first warning after this interval |
| `bombardment-damage` | 6 | 0–100 [reference health points](shattered-crown-damage.md) lost with full Protection IV netherite, no Resistance/absorption; calibrated across Easy/Normal/Hard, not raw damage |
| `bombardment-radius` | 4 | 1–12 blocks; circular impact, at most 3 blocks vertically from warned ground |
| `arena-radius` | 192 | 16–256 blocks, horizontal cylinder around the battle podium over the full world build height |
| `opening-grace-seconds` | 30 | 0–120 seconds; preparation before the first automatic Attack Warning |
| `arrival-x` / `arrival-z` | 100 / 0 | X/Z offsets from the actual battle podium; −512–512 |
| `arrival-y` | 50 | Arrival center world height; −64–320 |
| `arrival-radius` | 8 | Protected cylinder radius, 3–32 |
| `arrival-height` | 12 | Cylinder's total height, 4–64 |
| `anchor-distance` | 24 | Desired cardinal ground objectives east/north/west/south of the podium, 8–192 |
| `ground-search-radius` | 8 | Bounded horizontal search around each position, 0–16 |
| `ground-max-y` | 90 | Deprecated compatibility field, ignored; objective placement uses world build height |
| `final-health-fraction` | 0.3 | Deprecated compatibility field; ignored. The 65% and 30% wave gates are fixed. |
| `final-cadence-factor` | 0.7 | 0.5–0.9 multiplier for familiar Hunt/landing cadence opportunities |
| `final-pool-lifetime-factor` | 0.5 | 0.25–0.9 multiplier for pool lifetime; warning and damage cadence unchanged |
| `explosion-window-seconds` | 2 | 0.25–5 seconds, aggregate rolling game-time window for the managed dragon |
| `explosion-window-budget` | 40 | 1–200 health points total across accepted explosions and all dragon parts |

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

### Shatter the Last Stand: final defaults and safeguards

- Damage is capped **before native damage/death** to stop at 300/1000 health with the default
  unmodified dragon attribute. In-flight multipart hits share that health-floor allowance. MONITOR
  observes final combat acceptance without editing the event; encounter-owned deferred work checks
  actual native health before consuming the once-only gate or stopping Hunt. Later HIGHEST cancellation
  or reduction cannot activate the finale above its threshold. The health floor protects every hit
  until that work runs, and shielding is installed before spawning final objectives.
  Premature native dying/death callbacks are rejected until all three waves are cleared; an already
  cancelled combat, phase or death event remains cancelled.
  Native maximum-health modifiers are preserved: for a 1000 base with a −50 modifier, the attempt
  begins at 950 and the final gate is 285, not 300. Abort/reset keep those modifiers intact.
- All four established sites reactivate at each gate, with independent defender/exposure rules.
  Waves can clear in any order or the same tick. Eligibility is consumed before spawning;
  healing and duplicate callbacks cannot replay it. Missing defenders/crystals use established
  recovery. Missing footing permits floating crystals; a refused spawn aborts to a full-health
  retryable native battle and awards nothing. The accepted, capped hit has already
  applied before setup/abort, so it cannot subsequently kill the restored native dragon. Termination
  cancels pending acceptance work with the rest of the attempt.
- If disable, disabling reload, unload or reset happens synchronously inside a later combat listener,
  cleanup cancels still-in-flight managed hits before restoring native health, including multipart
  and explosive hits after the final shield has cleared. Accepted events are never edited at MONITOR.
- Entering the gate closes only the attack scope: pending Hunt warnings, hazards and landings stop.
  Attempt ownership, native phase restoration, the continuous absence timeout and explosion budget
  remain intact. Familiar 8-second bombardment opportunities with 3-second visible warnings pressure
  the four objectives, using the same walking-route/native-cloud checks. Clearing the final wave
  cancels pending bombardments immediately and starts the final Hunt once.
- Default final cadence opportunities are **7s lanes, 14s Marked Prey, 17.5s sweeps and 21s landings**;
  default breath pools last **3s**, not 6s. Dive/Prey/sweep warnings are 2s/4s/4s; legacy lanes last 6s, landing approach
  remains 20s and the melee window remains a full 12s **after actual native arrival**. Cadences are
  opportunities, not guaranteed simultaneous attacks. The same serial composition, route checks,
  hazard ceilings and landing reservation apply. Final cadence never falls below warning + 0.5s,
  prey countdown + shortened pool lifetime + 0.5s, or 10s for landings. No new attack or forced wipe.
- The budget counts accepted final explosion damage, not each bed independently. A fixed-size tick
  ring aggregates simultaneous block/entity explosions and multipart callbacks; reservations also
  cover nested callbacks and are released on later event cancellation. Unrelated dragons/entities
  and ordinary melee/projectile/magic damage are not explosion-capped. The window rounds up to game
  ticks and includes one conservative extra tick so mid-tick hits never expire early: default damage
  returns after 2–2.05 seconds, not at a wall-clock fixed-window boundary. At most 101 buckets and one
  50ms clock task; fractional-hit spam creates no per-hit scheduled work. Budget persists across the
  final interlude and resets only with a new attempt/termination.
- Explicit command replies use existing Player Settings localization; phase/shield/attack output
  follows the quiet communication policy above. The Server Configuration GUI exposes all five new tuning fields. Reload validation
  is atomic and the complete combat profile is snapshotted per attempt. Three waves of four Anchors
  are fixed mechanics, not extra configuration knobs. The old health-fraction field is compatibility-only.

Ground resolution uses four fixed horizontal sites and highest-block placement, with last-height
fallback for empty columns. It never builds platforms or places blocks. No 5×5 foundation or
terrain preservation is required. Spawn refusal aborts safely; discovery retries after 30 seconds.

## Behavior and ownership

- Living, online survival/adventure players in the arena start and sustain an attempt. Dead players,
  creative/spectator players and protected arrivals do not. Movement, teleport, quit, death and
  game-mode events update absence timing; a one-second module discovery/maintenance task also
  catches late world/dragon availability and returning players. No teleport, inventory or travel
  policy is changed.
- Every wave has four independent Anchors with population-scaled complete Roster Units. At four
  online players wave one has four groups per Anchor (43 members total); later placeholders have
  four configured defenders per Anchor (16 total).
  Teams can clear them in any order or concurrently. Only an Anchor's own remaining wave gates its
  crystal; only encounter-owned crystals advance progress. Duplicate deaths/hits cannot count twice.
  Defenders use native AI. Their own deaths expose the crystal; an exposed crystal glows. Reinforcement
  spawning and transformations are disabled for owned defenders. Missing defenders are replaced
  within the remaining wave budget, only arena/arrival escape causes recovery (not roaming), and missing Anchors
  are recreated without clearing the defender gate. Refused spawns abort and restore the battle.
- Glowing exposed crystals, floating Attack Warning countdowns, visuals and sounds communicate
  Encounter state without automatic chat or action bars. The vanilla dragon boss bar is untouched.
- Shield cancellation handles normal and multipart damage events, including explosions and arrows.
  Ordinary crystal healing is cancelled only for the currently managed dragon. Exposed Anchor hits
  from eligible players remove just that objective and suppress its explosion and ignition.
- Protected arrivals cannot attack encounter enemies. Current source/shooter location is checked,
  and projectiles/primed TNT originating in protection retain a persistent origin marker even after
  moving into the arena. Owned defenders also cannot damage protected arrivals. All custom
  attacks share `EncounterController.isParticipant` and `isProtectedArrival`.
- Bombardments choose eligible players in deterministic UUID-sorted round-robin order. One fixed
  ground circle is warned at a time: a 32-point native flame ring refreshes four times per second,
  a native TextDisplay shows a numeric countdown, and a dragon growl accompanies the warning.
  Impact uses a distinct explosion visual/sound, not a real explosion.
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
- During the opening, ownership is bounded at four crystals, 16 shipped defenders (128 at maximum configuration),
  and one warning display. At most six opening module/attempt scheduler tasks coexist, including discovery,
  absence and the rolling-budget clock; completed attacks do not accumulate active tasks. Particle audiences are capped at 64,
  with 32 ring points per refresh (2,560 ring particles/second for 20 recipients while warned).
  The nearby cloud query runs only at planning/resolution, not every tick.
- PDC ownership keys are `kreiscraft:crown_artifact` (`anchor`/`defender`/`warning`/`lane`/`prey`/`pool`/`sweep`),
  `kreiscraft:crown_original_health` (original dragon maximum-health base value), and
  `kreiscraft:crown_original_phase` (restore/recover temporary native landing ownership).
  `kreiscraft:crown_protected_shot` records an indirect attack's protected origin, not entity ownership.
  Recovery on module startup and entity/world load removes stale owned artifacts and restores the
  same surviving dragon, without rewards or duplicate dragons. It resumes no attack or phase.
  Recovery now runs independently of combat enablement, including disabled startup and later-loaded
  chunks. See the [reload/restart lifecycle contract and evidence](/shattered-crown-lifecycle/).

## Historical opening evidence (#134–#135)

The following records the earlier opening slice, not the current complete-encounter test count.

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

## Historical #138 automated evidence and verification

These counts describe the #138 checkpoint; see the
[current agent verification and required live acceptance](/shattered-crown-validation/).

`FinalEncounterTest` drives the same approved module/public-event/MockScheduler boundary. Nine
vertical red→green slices established: exact/oversized/lethal threshold gate; accelerated prey and
exclusive short-pool expiry; shared multipart explosion budget; premature-death rejection and untouched
legal victory payload; refused final spawn cleanup; sliding half-tick budget edge; and changed-footing
refusal; effective-maximum gating/native modifier restoration; and refusing early crystal clears
from synchronous defender-spawn callbacks before the complete wave has been installed.
A tenth red→green slice verifies all 50 tuning controls and EN/DE final labels fit the
existing chest without replacing the enable toggle; no pagination framework was needed.
Additional cases cover both independent final orders, healing/duplicates, all seven damage
routes, cancellations, full default lane/sweep/landing timings, EN/DE presentation, atomic invalid/valid
reload snapshots, continuous absence across the transition, synchronous spawn damage/disable, every
finale reset/abort stage and repeated bounded 20-player final Hunts.

The full opening → Hunt → final pair → victory demo runs for initial and ritual-battle profiles on
the **available MockBukkit server setup**, retaining 12000/500 XP event payloads and vanilla-owned
completion. It does not generate a real exit portal, egg, XP or ritual: those remain Paper acceptance.
The existing unsupported-operation envelopes are unchanged; no new dragon adapter method, NMS,
test engine or real-server automation framework was added.

Verification at that checkpoint used the installed macOS Java **25.0.4** launcher:

```sh
JAVA_HOME=/Users/I767799/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home \
  ./gradlew test --tests 'de.kreiscraft.modules.shatteredcrown.*' --max-workers=2 --console=plain
JAVA_HOME=/Users/I767799/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home \
  ./gradlew spotlessApply check -x testNms --max-workers=2 --console=plain
git diff --check
```

Durable execution evidence is saved under `~/.local/state/kreiscraft-java-runs/138-*.log`.
Passed for #138: **83 final tests, 269 total encounter tests and 35 Server Configuration GUI tests**,
none skipped; the targeted run covers 304 tests. Full check passes **1,581 tests**, zero failures/errors
and five existing metrics-collector skips, including Spotless, Checkstyle, production `-Werror`, shadow
JAR and InvUI compatibility. `testNms` execution is excluded because no version-specific code changed.
The repeated-final-Hunt check uses 20 mock participants over three three-minute final Hunts, with
1000 fractional explosions per attempt: at most four hazard displays, 21 active scheduled tasks and
discovery alone after reset. These are automated bounds, not measured Paper performance or balance.

## Paper evidence and remaining live checklist

Target: Java 25 / Paper 26.2. At the #138 checkpoint, no running Paper server or multiplayer
participants were available in this worktree environment; **no live combat, rendering, AI or balance
evidence is claimed**.

At #138 verification, no disposable Paper server existed in this worktree and no listener was
available on local port 25565. The final `789e29d` clientless Paper retest now **passed** on the
reviewed artifact; see the [current evidence and limits](/shattered-crown-validation/).
It verifies startup, configuration and native stale-artifact cleanup, not combat, managed-dragon
health/phase restoration, active-interruption restart or multiplayer acceptance. No multiplayer
session was performed. Before deployment validation, run the
**complete encounter** on the supported Paper server and record:

1. Main-End initial and ritual dragon discovery, real podium/platform alignment, all four accessible Anchor
   terrain, visible locale/status cues, arena-wide defender targeting/roaming and despawn behavior.
2. Real multipart sword/arrow/explosion shield rejection; exposed crystal breaking; crystal healing
   suppression and restoration; protected-arrow/TNT origins; ignition/explosion suppression.
3. Kill through vanilla completion and verify initial vs repeated XP, portal, egg/gateway and ritual
   eligibility. Then reset, unload and disable without a rewarded kill or leftover entities.
4. Representative ~20-player mixed-gear fight: attendance, gear, duration, deaths, objective
   participation, melee/ranged contribution and tick/performance observations. Inspect every circle's
   countdown, visible area and escape route, plus phase-boundary and protected-arrival cancellation.
    All phases are now implemented, but the ~20-player mixed enchanted diamond/netherite, 15–20-minute
    target remains unvalidated. Death and normal rejoining are allowed. Follow the existing
    [complete live acceptance checklist](/shattered-crown-validation/); #140 remains separate human work.
