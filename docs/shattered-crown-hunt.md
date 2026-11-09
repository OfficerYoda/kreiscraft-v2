---
title: Shattered Crown hunt
---

# Weather the Hunt: breath lanes and melee windows

Ticket #136 extends the four-Anchor opening of [The Shattered Crown](/shattered-crown/).
The final opening Anchor starts Hunt scheduling once, cancels pending bombardments and releases the
shield immediately. Permitted ordinary melee and ranged flight damage remain native combat events;
the controller neither substitutes health nor applies player attacks a second time.
The finale and remaining attack vocabulary belong to subsequent tickets.

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

`lane-cadence-seconds >= lane-warning-seconds + 0.5` serializes warnings.
`lane-width / 2 + 2 <= lane-warning-seconds * 3` reserves conservative walking escape time.
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

## Supported Paper landings, not a stationary replacement fight

At a landing opportunity the controller reserves the next clear interval: no new lane warnings start
while existing lanes expire. It checks four usable ground-level approaches around the real podium
and nearby native breath danger. Terrain/native danger can defer or omit a landing rather than force
players onto a void perch. Deferral is bounded by lane warning + lifetime + approach timeout; native
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
Scheduling is per lane/attempt, never per particle/player: three Hunt cadence/coordinator tasks;
at most one landing deferral or monitor plus deadline/window; each lane has bounded refresh,
activation/expiry and damage tasks. Despawned displays can retain only their finite expiry callbacks,
never an unbounded repeating damage task. There are no every-world per-tick entity scans. Nearby
cloud and ground checks run at planning/activation and during the 250 ms landing monitor, bounded
by configured geometry/search budgets. Live performance is not established by these bounds.

## Automated evidence and real-Paper gaps

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
  Tune toward the parent spec's 15–20-minute target only after remaining attacks/finale are integrated;
  do not infer fun, difficulty, performance or first-attempt win rates from automated tests.

Verification commands (Java 25 launcher; no version-specific code changed):

```sh
JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 \
  ./gradlew test --tests 'de.kreiscraft.modules.shatteredcrown.*' --console=plain
JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 \
  ./gradlew spotlessApply check -x testNms --console=plain
git diff --check
```

Passed for #136: **48 Hunt tests**, **115 total encounter tests**, none skipped. Full Gradle check
passed with **1,425 tests**, zero failures/errors and five existing `ServerMetricsCollectorTest`
skips. Production compilation (`-Werror`), Spotless, Checkstyle, shadow JAR and InvUI compatibility
verification passed. `testNms` execution was excluded because no version-specific code changed.
