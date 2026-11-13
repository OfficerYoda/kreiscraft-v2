---
title: Shattered Crown hunt
---

# Weather the Hunt: coordinated attacks and melee windows

The current default replaces stationary lanes/sectors with moving dives and ground smashes.
See the [live-playtest follow-up](/shattered-crown/#live-playtest-follow-up-moving-attacks) for current
behavior and tuning. The stationary lane/sweep contract below remains available with
`movement-attacks: false`.

## Unlocked rotation (#157, native movement profile)

With the default `movement-attacks: true`, one coordinator runs throughout shielded and vulnerable
phases. Prey is initially available. Destroying all four crystals in waves one,
two and three permanently unlocks Dive, Bombardment and Smash respectively; later shields do not relock
them. Bombardment now includes the former Fireball Rain mechanics; see the [merged contract](/shattered-crown-rain/).
The compatibility stationary profile below retains its old diagnostic cadence/shape contract.

Each round shuffles every available family once. Newly available families join the unfinished
round once, without replaying its already consumed families. Round boundaries do not immediately
repeat a selection when more than one family is available. Failed/rejected placements consume their turn too, so an impossible family
cannot starve the others. A new attempt/manual resume starts a fresh round; ordinary wave changes
retain the unfinished round. The first warning starts **at Opening Grace expiry** (normally 30s),
not an additional cadence later. Manual triggers bypass grace; pause/resume never refill it.

After main completion, a uniformly selected **inclusive 40–80 server-tick (2–4s) delay** precedes
the next warning. Dive/Smash include recovery, Prey includes all deposition, Bombardment includes
resolution or cleanup of its last native projectile. Sibling pools do not hold
the main-action slot. Cancellation, native-flight/render failures and lost targets release
that slot and receive the same bounded delay. Rejected or failed initial setups consume their turn
but retry the next family after one second, avoiding a full idle combat gap. Pausing cancels all automatic work and hazards.
No per-family cadence or final cadence factor controls this native rotation.

Vulnerable landing opportunities use `landing-cadence-seconds` (default 30s, 10–120s). A due
opportunity takes the next idle main slot, independently of the ranged round/gap; it neither consumes
a family nor clears/waits out existing pools. Failed landing geometry/setup/approach consumes only
that opportunity. The next opportunity is due one configured cadence after landing completion,
and at least one ranged-family turn must intervene, including when cadence and gap deadlines tie.
An overdue opportunity cannot replace the first grace-expiry warning. Native approach has its existing
5–45s timeout (default20s). Long native paths can legitimately exceed20s; tune this existing knob
within its validated bounds rather than force movement or suppress timeout recovery.
The melee window is **exactly six seconds from actual podium arrival**, not from the
approach request. Native phase events start the clock immediately; a one-tick monitor handles
already-sitting arrivals. Paper's podium Y is normally the fight origin (zero), not portal height:
landing checks use current podium footing/headroom, without promising intact walking approaches over craters.
No new ranged/main attack starts until release; old owned pools keep their
rendering, damage and independent expiry. Release restores native flight and starts the next
post-completion delay. Normal wave-gate replacement cancels applicable work without stuck reservations.

`attack-gap-min-seconds` and `attack-gap-max-seconds` default to 2 and 4; each must be finite within
2–10 and minimum must not exceed maximum. Existing 5–10-second overrides remain valid. They are rounded to server ticks and snapshotted per
attempt. `landing-window-seconds` now validates **6 only**; replace old 12s configuration values.
Existing YAML retains explicit tuning: set `lane-length: 40.0`, `lane-width: 10.0`, `dive-pass-seconds: 1.5`,
`attack-gap-min-seconds: 2.0` and `attack-gap-max-seconds: 4.0` to adopt the new defaults next attempt.
Status includes `mode`, wave/permanent `unlocked`, actually `available`, executing `attack`, lingering
`hazards`, remaining round `queued`, `last-selection` (including rejected setups), and
`next-warning-seconds` (`paused` without a coordinator).
The remaining gap can reach zero while landing/main work owns the slot; zero does not override it.
Selection nonrepetition does not promise alternating successful warnings when geometry rejects the
other family; rejected turns remain bounded and cannot starve the currently usable attacks.

Controlled Encounter/public-event regressions cover seeded rounds, cross-round nonrepetition,
both exact delay endpoints, unlocks during unfinished rounds/later shields, Rain availability,
grace, and the six-second window with damaging old pools. `rotationProbeJar` is a disposable real
Paper probe, never part of the production JAR; it uses real registered native players/battle/AI,
production commands and public pool ownership. Its extended-lifetime pool is a bounded fixture for
natural landing arrival, not a claim that ordinary Prey lasts 120s. Synthetic transport is not human
rendering, multiplayer balance or a full-fight acceptance claim.

**#161 policy update:** [permanent terrain impacts](/shattered-crown-terrain/) supersede all older
walking-route, arrival-footprint and preserved-ground guarantees below, including stationary paths.
Supported target footing, bounds, existing-hazard restrictions and full warnings still apply.
Historical RED/GREEN/runtime reports retain their original meaning, not the current route policy.

Ticket #136 extends the four-Anchor opening of [The Shattered Crown](/shattered-crown/).
The final opening Anchor starts Hunt scheduling once, cancels pending bombardments and releases the
shield immediately. Permitted ordinary melee and ranged flight damage remain native combat events;
the controller neither substitutes health nor applies player attacks a second time.
Ticket #137 added Marked Prey and wing sweeps below. Ticket #138 now completes the once-only final
Anchor interlude and accelerated familiar Hunt. Historical #136/#137 evidence below stays labeled;
see the [current complete encounter and final safeguards](/shattered-crown/).

## Initial Server Configuration profile

All keys are under `modules.shattered-crown`, editable through the Server Configuration GUI with
English/German labels. The approved redesign uses four fully enchanted reference players with population
scaling, **not validated balance**. Values are finite, bounded and snapshotted per Encounter; ordinary
valid reloads affect only the next attempt. Invalid bounds/combinations reject the entire reload.

| Key | Default | Bounds / meaning |
| --- | --- | --- |
| `lane-length` | 40 | 8–40 blocks along the rectangle |
| `lane-width` | 10 | 2–12 blocks across the rectangle |
| `lane-warning-seconds` | 2 | 1–15 seconds before activation |
| `lane-lifetime-seconds` | 6 | 1–20 seconds of temporary active danger |
| `lane-cadence-seconds` | 10 | 2–120 seconds between warning opportunities; first after this interval |
| `lane-damage` | 8 | 0–100 reference HP for physical Dive; raw units for legacy lane pulses |
| `lane-damage-cadence-seconds` | 1 | 0.5–5 seconds; no pulse at exclusive expiry |
| `landing-cadence-seconds` | 30 | 10–120 seconds between landing opportunities |
| `landing-window-seconds` | 6 | exactly 6 seconds, starting **after actual native arrival**, not the approach request |
| `landing-approach-seconds` | 20 | 5–45 seconds to wait for native arrival, then abandon this opportunity |
| `landing-radius` | 12 | 8–24 blocks around the real podium reserved against native breath clouds |
| `prey-percentage` | 35 | 1–100%; ceil(percentage × everyone connected in the managed End), capped only by eligible targets |
| `prey-cadence-seconds` | 20 | 5–120 seconds between opportunities; first after this interval |
| `prey-countdown-seconds` | 4 | 2–15 seconds before depositing pools |
| `pool-radius` | 5 | 1–6 blocks, inclusive horizontal radius, at most three blocks vertically |
| `pool-damage` | 3 | 0–100 health points per native dragon-breath damage pulse |
| `pool-damage-cadence-seconds` | 1 | 0.5–5 seconds; globally at most one owned-pool pulse/player/second, exclusive expiry |
| `pool-lifetime-seconds` | 6 | 1–15 seconds, `[activation, expiry)` |
| `sweep-radius` | 8 | 4–16 blocks; physical Smash caps at eight |
| `sweep-angle` | 90 | 30–120 degrees, centered on the dragon's warning-time facing |
| `sweep-warning-seconds` | 3 | 3–15 seconds before the single hit |
| `sweep-cadence-seconds` | 25 | 5–120 seconds between opportunities; first after this interval |
| `sweep-damage` | 12 | 0–100 reference HP for physical Smash; raw units for legacy sectors |
| `sweep-knockback` | 1.2 | 0–2 horizontal velocity, vertical component one-third of this value |
| `final-health-fraction` | 0.3 | Deprecated compatibility value, ignored; waves are fixed at 100/65/30% |
| `final-cadence-factor` | 0.7 | 0.5–0.9; familiar cadence acceleration with warning/serialization floors |
| `final-pool-lifetime-factor` | 0.5 | 0.25–0.9; final pools expire sooner |
| `explosion-window-seconds` | 2 | 0.25–5; shared rolling managed-dragon budget window |
| `explosion-window-budget` | 40 | 1–200 health points across accepted explosions/parts |

Diagnostic stationary-profile final defaults: **lane 7s, prey 14s, sweep 17.5s, landing 21s**,
Prey pool lifetime **3s**. The native shuffled rotation instead retains its2–4-second default post-main gap
and permanently unlocked families throughout all shields; Rain pools always last6s. Ordinary
warning durations, lane lifetime and knockback stay
unchanged. Cadence opportunities still defer/omit unsafe combinations; no extra hazard family or
forced-wipe timer is introduced. Only the diagnostic stationary shield uses8s Bombardment cadence.
The complete profile remains finite, bounded and next-attempt only.

`lane-cadence-seconds >= lane-warning-seconds + 0.5` serializes warnings.
Prey cadence must be at least countdown + 0.5 seconds; Sweep cadence must exceed warning by 0.5 seconds.
Radius/width walking-escape budget vetoes were retired by #161. Invalid remaining combinations reject publication.
Lifetime is `[activation, expiry)`: by default six one-second damage opportunities, not a seventh
expiry hit. Damage uses Paper's armor, invulnerability frames and other plugins' combat cancellation;
native breath may independently damage according to native combat rules. Lanes do not stack on each
other or native clouds. A player moving between disjoint lanes is still subject to ordinary combat
protections, not a global damage-immunity rule. Scheduling uses the existing scheduler's game ticks
(50 ms resolution; its zero-delay callbacks execute on the next tick on Paper).

## Visible geometry and safe placement

This section records the historical stationary geometry. Its walking-route/arrival-footprint vetoes
are superseded by #161; current placement uses surviving supported ground, not preserved terrain.

- A target is selected in deterministic UUID-sorted round-robin order from eligible living online
  survival/adventure participants. Each lane fixes its ground-level center at warning time and
  alternates cardinal orientations, trying the other orientation if the first is unsafe. It never
  tracks movement after warning. A rectangle spans half its configured length/width in each direction;
  damage has inclusive horizontal edges and at most three blocks' vertical distance from its ground.
- A native flame rectangle (44 boundary particles per refresh) and a viewer-facing native TextDisplay
  countdown show shape and timing. The same immutable rectangle produces active dragon-breath
   boundary particles and a purple remaining-lifetime countdown. A dragon growl provides a secondary
   audio cue. No automatic chat, action bars or replacement titles are sent (including refreshed
   countdowns); see the [Encounter communication policy](/shattered-crown/#encounter-communication-policy-156).
   No client mods, resource packs or mandatory audio are required.
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

## Marked Prey and shared pools (#160)

Each automatic selection counts all connected players in the managed End, including dead players
awaiting respawn, Creative, Spectator, protected arrivals and outer-island occupants. Four request two marks, eight request three,
twenty request seven; only living online Survival/Adventure combat participants may actually be marked.
The old `prey-count` key is retired and ignored: remove it and use `prey-percentage`, without a fixed
three/four-target ceiling. Valid configuration changes take effect on the next attempt.

`/dragondebug trigger prey <player>` deliberately marks only that named eligible player; it runs the
same countdown, revalidation, ground projection and pool lifecycle as automatic selection. The unnamed
player command uses its sender as the single debug target. Use automatic scheduling to inspect scaling.

- UUID-sorted round-robin selection marks distinct current participants. Each has a native viewer-facing
  TextDisplay above their head with their name and remaining seconds, plus a moving flame circle of the
  future pool radius. Teammates see marks without a glowing-state mutation, resource pack or sound.
   The marked recipient hears a secondary warning growl. The in-world name/countdown refreshes every
   250 ms; it sends no chat or action-bar messages.
- At expiry, revalidate death, logout, world/mode/arena departure and protected arrival entry before
  projecting a pool at the player's **current** location. Entering an excluded area removes the mark;
  stepping back out cannot resurrect it. Invalid terrain or arrival overlap omits the pool, never
  moves it to stale coordinates.
- Pools are fixed circles rendered by purple dragon-breath boundaries and a purple lifetime display.
  Indicators do not deal vanilla damage: one controlled native damage pulse owns custom damage.
   Co-located marks retain independent pools, not stacked hits. Across every owned Prey/Rain pool,
   a shared rolling game-tick cooldown permits at most one configured pulse per player per second,
   even with staggered starts or movement between pools. A slower configured cadence stays slower;
   a cadence below one second does not bypass the global ceiling. Damage remains **raw native HP**
   (`pool-damage: 3`), not the reference-gear conversion used by single-impact attacks. Native
   cancellation, armor and invulnerability frames remain authoritative. Lifetime is exclusive at expiry.
- Sweeps require endangered participants and usable ground within 24 vertical blocks below the dragon.
  Warning fixes ground center, yaw, radius and inclusive sector edges. A flame arc **and both radial
  edges** warn the same sector later rendered with sweep particles and hit once. Turning or flying
  elsewhere does not relocate the warned impact. Damage height is ground ±3 blocks. The outward shove
  happens only after native damage reduces health, not after cancelled, zero-damage or invulnerability-
  rejected hits; disable during damage prevents post-cleanup knockback.
- Main actions serialize, but deposited pools no longer hold the main-action slot. Before each Prey
   deposition, validate the **entire proposed pool set**, including each endangered player's escape
   from the combined danger area. Owned pools may overlap and walking routes may cross their union.
   Shapes sample solid footing and player-body clearance;
  nearby native breath clouds and arrival geometry exclude footprints/routes. Unsafe combinations
  are omitted rather than promising pathfinding through arbitrary player-built obstructions.
- A due landing prevents all new warnings, waits for pending main actions and lanes, then
   reserves the complete native-arrival melee window. Already deposited pools remain active. Bounded deferral covers
  the longest configured lane, prey/pool or sweep lifetime plus native approach timeout. The automated
  full sequence demonstrates lane at 10s → marks at 20s → pools at 24s → clear landing approach at
   30.25s → native arrival and a six-second melee window. Historical cadence examples are not the new shuffled rotation. Callback ordering may defer an
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
- Marks scale with population and lingering pool count depends on cadence/lifetime, not a fixed cap.
   Rendering retains its 64-recipient limit and finite per-effect tasks; reset returns to discovery alone.
   Repeated 20-player fixtures now permit seven marks/pools and check a 30-task ceiling. Those fixture
   bounds are **not measured Paper tick/performance evidence**. The shorter compatibility landing now
   permits seven marks/pools plus one main warning and a 32-task ceiling after the #161 merge.

### Production pool boundary and verification

`EncounterController.depositBreathPool(ground, radius, lifetimeSeconds)` is the shared production
boundary for Fireball Rain: effect-specific radius `(0,32]` and duration `(0,120]` seconds, called on
the server thread. It snapshots the local surface and owns the display/render/damage/exclusive expiry
as a sibling hazard. It returns false for missing owners/footing or isolated startup failure. Rain
supplies its own duration; Prey uses its configured lifetime and existing final-phase factor.
Rendering, startup, refresh, removed-display and damage failures release only the affected pool.
Wider pause/cancel/phase/reset/victory/disable/unload cleanup releases all pools and rate membership.
Every breath emission continues through `CrownParticles` with Paper's required Float payload.

Current regressions cover 4/8/20 population rounding and eligibility capping, radius-five overlap,
staggered starts/different durations, unchanged raw damage, exclusive expiry, isolated failures,
target departures and same-tick returns, jumping projection, next Prey before old-pool expiry and
melee landing with lingering pools. Run Java 25 `./gradlew spotlessApply check build --max-workers=2`;
`check` includes the real-Paper particle conversion/native boundary tests. Integrated native combat
and fixture verification remains in #163; human readability/balance is a separate observation, not
a hidden prerequisite to the agent-executable candidate. Historical evidence below is not #160 acceptance.

A disposable Paper 26.2 build 132 / Java 25 native-player fixture also executed the actual production
named-Prey command on the naturally created, production-adopted main-End dragon. It observed the
warning before damage, ground projection after a jump, two raw base/final 3-HP pulses, real particle
packet conversion, idle main action with one lingering pool, then cancel with zero hazard/area
membership and no later damage. Dragon AI was disabled only after adoption to isolate this check;
natural health regeneration was disabled for exact damage measurement. This is server-side synthetic
transport evidence, **not** authenticated client rendering, native flight, the integrated Prey/Rain
overlap path, a full attempt or four-player balance acceptance. #163 still owns that integrated verification.

## Supported Paper landings, not a stationary replacement fight

At a landing opportunity the controller reserves the next clear interval: no new lane warnings start
while existing lanes expire. It projects real podium footing/headroom and checks nearby native breath
danger, without demanding four preserved walking approaches over craters. Terrain/native danger can defer or omit a landing rather than force
players onto a void perch. Stationary-profile deferral is bounded by the longest custom warning/hazard lifetime + approach timeout; native
movement is subsequently allowed its configured approach timeout. New warnings resume on omission.

The controller uses only supported `EnderDragon.setPhase` and `EnderDragonChangePhaseEvent`:

1. Request `FLY_TO_PORTAL`; native AI controls flight and `LAND_ON_PORTAL` movement. No teleport or
   velocity scripting occurs in production.
2. Observe a native sitting phase **and** proximity to the podium (within eight blocks horizontally
   and vertically). Only then begin the six-second melee window, without an automatic chat message. A premature
   sitting callback while still flying elsewhere cannot consume the attack window.
3. Hold `SEARCH_FOR_BREATH_ATTACK_TARGET` through the window, preventing competing native takeoff,
   charge/strafe and breath transitions from immediately cancelling or covering it. This is a native
   sitting phase, not midair `HOVER`. It leaves ordinary melee damage handling intact. Native sitting
   projectile behavior is unchanged; ranged contribution remains available during flight.
4. On window expiry release to `LEAVE_PORTAL`. A failed approach releases to native circling (or native
   takeoff if already sitting). Empty participation, unusable podium footing or new nearby native breath
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
Scheduling is per hazard/attempt, never per particle/player: one native rotation coordinator
(five cadence/coordinator tasks in the stationary compatibility profile);
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
