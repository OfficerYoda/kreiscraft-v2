---
title: Shattered Crown hunt
---

# Weather the Hunt: coordinated attacks and melee windows

The current default replaces stationary lanes/sectors with moving dives and ground smashes.
See the [live-playtest follow-up](/shattered-crown/#live-playtest-follow-up-moving-attacks) for current
behavior and tuning. The stationary lane/sweep contract below remains available with
`movement-attacks: false`.

Ticket #136 extends the four-Anchor opening of [The Shattered Crown](/shattered-crown/).
The final opening Anchor starts Hunt scheduling once, cancels pending bombardments and releases the
shield immediately. Permitted ordinary melee and ranged flight damage remain native combat events;
the controller neither substitutes health nor applies player attacks a second time.
Ticket #137 added Marked Prey and wing sweeps below. Ticket #138 now completes the once-only final
Anchor interlude and accelerated familiar Hunt. Historical #136/#137 evidence below stays labeled;
see the [current complete encounter and final safeguards](/shattered-crown/).

## Initial Server Configuration profile

All keys are under `modules.shattered-crown`, editable through the Server Configuration GUI with
English/German labels. The shipped profile targets approximately 20 mixed enchanted diamond/netherite
players, **not validated balance**. Values are finite, bounded and snapshotted per Encounter; ordinary
valid reloads affect only the next attempt. Invalid bounds/combinations reject the entire reload.

| Key | Default | Bounds / meaning |
| --- | --- | --- |
| `lane-length` | 24 | 8–40 blocks along the rectangle |
| `lane-width` | 6 | 2–12 blocks across the rectangle |
| `lane-warning-seconds` | 3 | 1–15 seconds before activation |
| `lane-lifetime-seconds` | 6 | 1–20 seconds of temporary active danger |
| `lane-cadence-seconds` | 10 | 2–120 seconds between warning opportunities; first after this interval |
| `lane-damage` | 4 | 0–100 health points per eligible cadence pulse, native dragon-breath damage |
| `lane-damage-cadence-seconds` | 1 | 0.5–5 seconds; no pulse at exclusive expiry |
| `landing-cadence-seconds` | 30 | 10–120 seconds between landing opportunities |
| `landing-window-seconds` | 12 | 8–30 seconds, starting **after actual native arrival**, not the approach request |
| `landing-approach-seconds` | 20 | 5–45 seconds to wait for native arrival, then abandon this opportunity |
| `landing-radius` | 12 | 8–24 blocks around the real podium reserved against native breath clouds |
| `prey-count` | 3 | 1–4 distinct eligible players per countdown, or fewer if attendance is lower |
| `prey-cadence-seconds` | 20 | 5–120 seconds between opportunities; first after this interval |
| `prey-countdown-seconds` | 4 | 2–15 seconds before depositing pools |
| `pool-radius` | 3 | 1–6 blocks, inclusive horizontal radius, at most three blocks vertically |
| `pool-damage` | 3 | 0–100 health points per native dragon-breath damage pulse |
| `pool-damage-cadence-seconds` | 1 | 0.5–5 seconds, first pulse at activation, exclusive expiry |
| `pool-lifetime-seconds` | 6 | 1–15 seconds, `[activation, expiry)` |
| `sweep-radius` | 12 | 4–16 blocks from the fixed ground-projected dragon center |
| `sweep-angle` | 90 | 30–120 degrees, centered on the dragon's warning-time facing |
| `sweep-warning-seconds` | 4 | 2–15 seconds before the single hit |
| `sweep-cadence-seconds` | 25 | 5–120 seconds between opportunities; first after this interval |
| `sweep-damage` | 6 | 0–100 health points through the native damage/cancellation pathway |
| `sweep-knockback` | 1.2 | 0–2 horizontal velocity, vertical component one-third of this value |
| `final-health-fraction` | 0.3 | 0.1–0.5; exactly two cleared sites return once |
| `final-cadence-factor` | 0.7 | 0.5–0.9; familiar cadence acceleration with warning/serialization floors |
| `final-pool-lifetime-factor` | 0.5 | 0.25–0.9; final pools expire sooner |
| `explosion-window-seconds` | 2 | 0.25–5; shared rolling managed-dragon budget window |
| `explosion-window-budget` | 40 | 1–200 health points across accepted explosions/parts |

Final defaults: **lane 7s, prey 14s, sweep 17.5s, landing 21s**, pool lifetime **3s**. Ordinary
warning durations, lane lifetime, knockback and the complete 12s actual-arrival melee window stay
unchanged. Cadence opportunities still defer/omit unsafe combinations; no extra hazard family or
forced-wipe timer is introduced. During the two-Anchor shield, only learned opening bombardments
run (8s cadence, 3s warning). The complete profile remains finite, bounded and next-attempt only.

`lane-cadence-seconds >= lane-warning-seconds + 0.5` serializes warnings.
`lane-width / 2 + 2 <= lane-warning-seconds * 3` reserves conservative walking escape time.
Prey cadence must be at least countdown + pool lifetime + 0.5 seconds; pool radius + 2 must fit
within countdown × 3. Sweep cadence must exceed warning by 0.5 seconds; radius × sin(angle / 2) + 2
must fit within warning × 3. Invalid cross-field combinations reject the entire configuration publication.
Lifetime is `[activation, expiry)`: by default six one-second damage opportunities, not a seventh
expiry hit. Damage uses Paper's armor, invulnerability frames and other plugins' combat cancellation;
native breath may independently damage according to native combat rules. Lanes do not stack on each
other or native clouds. A player moving between disjoint lanes is still subject to ordinary combat
protections, not a global damage-immunity rule. Scheduling uses the existing scheduler's game ticks
(50 ms resolution; its zero-delay callbacks execute on the next tick on Paper).

## Visible geometry and safe placement

- A target is selected in deterministic UUID-sorted round-robin order from eligible living online
  survival/adventure participants. Each lane fixes its ground-level center at warning time and
  alternates cardinal orientations, trying the other orientation if the first is unsafe. It never
  tracks movement after warning. A rectangle spans half its configured length/width in each direction;
  damage has inclusive horizontal edges and at most three blocks' vertical distance from its ground.
- A native flame rectangle (44 boundary particles per refresh) and a viewer-facing native TextDisplay
  countdown show shape and timing. The same immutable rectangle produces active dragon-breath
  boundary particles and a purple remaining-lifetime countdown. English/German action bars announce
  warning time and the short-side escape; a dragon growl provides a secondary audio cue. No client
  mods, resource packs, mandatory audio or repeated chat instruction spam are required.
- Before warning **and** activation, placement checks solid footing, clear player-sized body space,
  actual arena boundaries and arrival exclusion over the full footprint. No custom effect changes
  blocks or creates terrain. The implementation conservatively skips uneven/obstructed areas rather
  than claiming empty space over the void is usable ground.
- Every threatened participant must have a real straight walking corridor out of the rectangle plus
  a one-block margin, within warning time at 3 blocks/second. Sixteen directions use half-block samples
  and player-body clearance. Diagonal point gaps do not qualify. Existing custom lanes and nearby
  native dragon-breath clouds exclude candidate footprints and escape paths. More than 32 nearby
  native clouds or invalid/unbounded cloud radii veto placement. New pressure is also deferred while
  any participant is still in existing danger. This is intentionally conservative, not a guarantee
  against player mistakes or arbitrary player-built obstructions.
- At most two disjoint lanes coexist and at most one is warning. A third or overlapping pattern is
  omitted until another opportunity. Expiry removes its indicator and stops all damage/refresh work;
  the ground remains untouched. A refused or missing indicator never resolves invisible damage.
  Warning targets are revalidated for death, logout, mode/world changes and arrival entry. Once a
  valid fixed lane activates, target movement does not relocate or permanently prolong it.
- Only current participants receive custom damage. Protected arrivals, creative/spectator players,
  dead/offline players and players outside the arena are excluded from planning/damage. The existing
  protected projectile/TNT-origin rejection and ordinary cancelled-combat handling remain in force.

## Marked Prey, pools and fixed wing sweeps (#137)

- UUID-sorted round-robin selection marks distinct current participants. Each has a native viewer-facing
  TextDisplay above their head with their name and remaining seconds, plus a moving flame circle of the
  future pool radius. Teammates see marks without a glowing-state mutation, resource pack or sound.
  The marked recipient receives an English/German action-bar countdown and a secondary warning growl.
  Refresh is 250 ms; no attack countdown floods chat.
- At expiry, revalidate death, logout, world/mode/arena departure and protected arrival entry before
  projecting a pool at the player's **current** location. Entering an excluded area removes the mark;
  stepping back out cannot resurrect it. Invalid terrain or arrival overlap omits the pool, never
  moves it to stale coordinates.
- Pools are fixed circles rendered by purple dragon-breath boundaries and a purple lifetime display.
  Indicators do not deal vanilla damage: one controlled native damage pulse owns custom damage.
  Co-located marks yield one pool, not stacked hits. Native cancellation, armor and invulnerability
  frames remain authoritative. The lifetime is exclusive at expiry.
- Sweeps require endangered participants and usable ground within 24 vertical blocks below the dragon.
  Warning fixes ground center, yaw, radius and inclusive sector edges. A flame arc **and both radial
  edges** warn the same sector later rendered with sweep particles and hit once. Turning or flying
  elsewhere does not relocate the warned impact. Damage height is ground ±3 blocks. The outward shove
  happens only after native damage reduces health, not after cancelled, zero-damage or invulnerability-
  rejected hits; disable during damage prevents post-cleanup knockback.
- Attack families are conservatively serialized: no new lane during marks/pools/sweeps; no new mark or
  sweep while a lane or another family is pending/active. Multiple marks and disjoint pools are the
  intentional combination. Before each pool, validate the **entire proposed pool set**, including each
  existing endangered player's escape, not merely the new target's route. A second pool is omitted if
  it seals the first pool's only corridor. Shapes sample solid footing and player-body clearance;
  nearby native breath clouds and arrival geometry exclude footprints/routes. Unsafe combinations
  are omitted rather than promising pathfinding through arbitrary player-built obstructions.
- A due landing prevents all new warnings, waits for marks/pools/sweeps and lanes to finish, then
  reserves the complete native-arrival melee window with no custom hazards. Bounded deferral covers
  the longest configured lane, prey/pool or sweep lifetime plus native approach timeout. The automated
  full sequence demonstrates lane at 10s → marks at 20s → pools at 24s → clear landing approach at
  30.25s → native arrival and 12s melee window → fixed sweep at 50s. Callback ordering may defer an
  opportunity by one 250 ms coordinator interval. Cadences are opportunities, not guaranteed hits.
- Arrival-origin arrows, primed TNT and attributable clouds cannot damage the managed dragon,
  Anchors or defenders after moving into combat. Lingering-potion splash transfers protected
  projectile provenance to its cloud. Native splash intensity and cloud effect audiences exclude
  encounter enemies from protected sources, preventing delayed unattributed poison effects rather
  than guessing their origin at a later damage event. Ordinary entities retain their effects.
  Provenance is not an ownership/removal marker.
- All new work uses EncounterWork. Reset, victory, unload and disable cancel marks, pools, sweeps and
  callbacks; missing/refused displays never cause invisible damage. Existing `crown_artifact`
  recovery handles `prey`, `pool` and `sweep` kinds. There are no custom temporary block changes or
  additional native damaging entities.
- Ceilings: at most four marks **or** four pools **or** one sweep (or the existing two lanes), 64
  particle recipients, 32 circle or 50 sector-boundary particles per 250 ms refresh. Four circles for
  20 recipients emit at most 10,240 boundary particles/second; one sweep emits 4,000. Five Hunt
  cadence/coordinator tasks plus discovery coexist with at most two tasks per mark, three per pool,
  two per sweep, and finite absence/landing callbacks. Repeated 20-player attempts observe at most
  20 active scheduler tasks, returning to discovery alone on reset. These are bounds, **not measured
  Paper tick/performance evidence**.

## Supported Paper landings, not a stationary replacement fight

At a landing opportunity the controller reserves the next clear interval: no new lane warnings start
while existing lanes expire. It checks four usable ground-level approaches around the real podium
and nearby native breath danger. Terrain/native danger can defer or omit a landing rather than force
players onto a void perch. Deferral is bounded by the longest custom warning/hazard lifetime + approach timeout; native
movement is subsequently allowed its configured approach timeout. New warnings resume on omission.

The controller uses only supported `EnderDragon.setPhase` and `EnderDragonChangePhaseEvent`:

1. Request `FLY_TO_PORTAL`; native AI controls flight and `LAND_ON_PORTAL` movement. No teleport or
   velocity scripting occurs in production.
2. Observe a native sitting phase **and** proximity to the podium (within eight blocks horizontally
   and vertically). Only then announce arrival and begin the complete melee window. A premature
   sitting callback while still flying elsewhere cannot consume the attack window.
3. Hold `SEARCH_FOR_BREATH_ATTACK_TARGET` through the window, preventing competing native takeoff,
   charge/strafe and breath transitions from immediately cancelling or covering it. This is a native
   sitting phase, not midair `HOVER`. It leaves ordinary melee damage handling intact. Native sitting
   projectile behavior is unchanged; ranged contribution remains available during flight.
4. On window expiry release to `LEAVE_PORTAL`. A failed approach releases to native circling (or native
   takeoff if already sitting). Empty participation, unusable approaches or new nearby native breath
   danger terminate the reservation safely. `DYING` and already-cancelled phase events are not
   overridden. Unrelated worlds/dragons retain their native behavior.

All new cadence, warning, damage, expiry, deferral and landing callbacks use the existing #139
EncounterWork ownership boundary. Native phase ownership is recorded **before** modification,
restored on reset/disable/unload and recovered after restart through the existing persistent phase
marker. Victory never restores an obsolete flight phase onto the dying dragon or simulates a kill.
Cleanup removes only owned lane displays and cancels pending effects. A synchronous combat/phase
event ending the Encounter prevents further work from continuing after cleanup.

Entity and particle load is bounded: two lane displays maximum, 64 particle recipients maximum,
44 particles per lane per 250 ms refresh (3,520 particles/second per lane for 20 recipients).
Scheduling is per hazard/attempt, never per particle/player: five Hunt cadence/coordinator tasks;
at most one landing deferral or monitor plus deadline/window; each lane has bounded refresh,
activation/expiry and damage tasks. Despawned displays can retain only their finite expiry callbacks,
never an unbounded repeating damage task. There are no every-world per-tick entity scans. Nearby
cloud and ground checks run at planning/activation and during the 250 ms landing monitor, bounded
by configured geometry/search budgets. Live performance is not established by these bounds.

## Historical automated evidence and real-Paper gaps

The #136/#137 results below describe their original integration points. They do not claim that
the finale was implemented at those dates. #138 now implements it; current evidence and scope are
in the [complete encounter guide](/shattered-crown/).

### #137 red→green and additional acceptance evidence

Four vertical encounter-level checks were run red before their implementations and then green:

1. `multiplePreyHaveVisibleMovingCountdownsThenPoolsAtTheirCurrentLocations`: initially no marks;
   green observes two named 4s displays, movement before resolution, two current-location pools,
   one pulse per player and finite cleanup.
2. `sweepWarnsAFixedSectorAndHitsWithKnockbackEvenIfTheDragonTurnsAndMoves`: initially no sweep;
   green observes warning-before-hit, immutable warned position/facing and successful outward velocity.
3. `protectedLingeringProjectileKeepsItsProvenanceAfterTheShooterAndCloudLeaveArrival`: initially
   permitted magic damage; green rejects attributed damage and excludes only encounter enemies from
   cloud effect application, leaving ordinary mobs untouched.
4. `aSecondWarnedPoolCannotCutOffTheFirstPoolsOnlyReachableEscape`: red creates two disjoint pools
   on two usable platforms joined by one body-width corridor, sealing the first player's only exit;
   green omits the second pool and preserves the first player's reachable safe ground.

Additional public module/event/time cases cover eight prey invalidations (including transient arrival
entry/return), stacked marks, inclusive circle/sector and vertical hit/miss boundaries, exclusive expiry,
all 12 termination/stage combinations, cancellation and synchronous disable during damage, missing/refused
indicators, native-cloud/void/arrival/escape refusals, all new invalid settings, next-attempt pool tuning,
both locales, the full lane/prey/sweep/landing sequence and four five-minute 20-player attempts with
four-mark and 20-active-task ceilings plus clean resets. Existing arrow/TNT, melee, multipart, defender,
opening and recovery tests remain part of the same approved encounter seam.

No new adapter operation or fake engine was needed: TextDisplay teleport, native projectile/cloud events,
and player velocity are supported by the existing MockBukkit fixture. These tests still do not render
particles, run dragon AI or establish real armor/knockback movement or melee reach.

**Live gaps:** no Paper server/multiplayer session was available. Before calling #137 live-validated,
record real moving-name/countdown visibility for teammates and both locales; circle/sector boundary
alignment; actual knockback/cancellation and arrival-origin potion/TNT/projectile behavior; native flight
→ approach → complete melee window → takeoff under all three attack families; cleanup at each stage;
and a representative ~20-player mixed enchanted diamond/netherite session. Death and normal rejoining
remain allowed. Record duration, deaths, phase/objective/melee/ranged contribution and tick/particle load.
The parent 15–20-minute target remained **unvalidated**; finale #138 was not implemented at that
#137 evidence point. It is now implemented, with live combat and balance still unvalidated.

Mac verification uses the installed Java 25 launcher rather than assuming a Linux Java path:

```sh
JAVA_HOME="$(mise where java@25)/Contents/Home" \
  ./gradlew test --tests 'de.kreiscraft.modules.shatteredcrown.*' --console=plain
JAVA_HOME="$(mise where java@25)/Contents/Home" \
  ./gradlew spotlessApply check -x testNms --console=plain
git diff --check
```

Passed for #137 on Temurin Java **25.0.5** on macOS: **111 Hunt tests**, **179 total encounter
tests**, none skipped; full check **1,489 tests**, zero failures/errors, five existing metrics-collector
skips. Spotless, Checkstyle, production `-Werror` compilation, shadow JAR and InvUI compatibility passed.
`testNms` execution was excluded: no version-specific/NMS code changed or new adapter need arose.

`HuntEncounterTest` uses the approved primary encounter boundary: module enable/reload/disable,
the four opening objectives, public damage/death/world/phase events, real MockBukkit player movement,
health and world indicators, and controlled `MockScheduler` time. Red→green slices established
warning → damage → expiry; overlap rejection; native-arrival window timing; non-starved delayed
landings; terrain escape selection; exclusive expiry; and rejecting premature distant sitting phases.
Additional coverage includes exact footprint hit/miss, native breath danger, two disjoint lanes and
third-pattern limits, all warning target invalidations, arrival/mode exclusions, locale cues,
cancelled damage, refused/removed indicators, reentrant disable, atomic reload snapshots, termination
during warning/active/landing stages, and four 20-player five-minute Hunts with repeated clean resets.

The existing narrow Paper adapter still isolates only operations absent from MockBukkit:
battle lookup, defender native metadata/despawn setter, billboard orientation and native damage
(tested with MockBukkit's existing event/health simulator). Native phase setters remain real public
world-entity mutations in the test. **MockBukkit has no dragon AI, and its phase setter does not emit
the Paper phase-change event**: tests explicitly supply public phase events and physical arrival
movement. This verifies encounter policy/cancellation, **not real flight, sitting or melee reach**.
MockBukkit also does not render or record particles. No NMS or new fake game/test framework is added.

No running Paper server or representative multiplayer session was available in this worktree.
**Actual dragon movement, rendering, armor/AI behavior and balance remain unvalidated.** Before
claiming the ticket's live acceptance, record on Java 25 / supported Paper 26.2:

- Native approach and portal landing from multiple flight positions, real podium altitude, full
  measured approach/melee time, repeated takeoffs and airborne ranged contribution. Confirm native
  phase callbacks, sitting damage-triggered takeoff and breath transitions cannot truncate windows.
- Player sword reach on the actual dragon parts; preserve multipart damage, normal death rewards,
  initial-versus-ritual battle behavior and external combat/phase cancellation.
- Visible rectangle/footing alignment, countdown readability in both languages, hit/miss boundaries,
  active/expired contrast, cloud/terrain escape deferrals and protected-arrival behavior.
- Mid-warning/active/landed reset, disable, unload/reload and restart with no delayed damage, stuck
  flight phase, unrelated deletion or duplicate reward.
- Approximately 20 mixed-gear participants: record gear, deaths, duration, melee/ranged participation,
  objective and phase durations, first-time readability, tick timings and particle/server load.
   Tune the now-complete encounter toward the parent spec's 15–20-minute target using real evidence;
  do not infer fun, difficulty, performance or first-attempt win rates from automated tests.

Verification commands (Java 25 launcher; no version-specific code changed):

```sh
JAVA_HOME="$(mise where java@25)/Contents/Home" \
  ./gradlew test --tests 'de.kreiscraft.modules.shatteredcrown.*' --console=plain
JAVA_HOME="$(mise where java@25)/Contents/Home" \
  ./gradlew spotlessApply check -x testNms --console=plain
git diff --check
```

Passed for #136: **48 Hunt tests**, **115 total encounter tests**, none skipped. Full Gradle check
passed with **1,425 tests**, zero failures/errors and five existing `ServerMetricsCollectorTest`
skips. Production compilation (`-Werror`), Spotless, Checkstyle, shadow JAR and InvUI compatibility
verification passed. `testNms` execution was excluded because no version-specific code changed.
