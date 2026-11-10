---
title: Shattered Crown live validation
---

# Required live acceptance: The Shattered Crown

**Not validated by automated tests or zero-player startup.** Spec #132 and ticket #140 require
actual supported-Paper combat and a representative multiplayer session before PR #133 can leave
WIP. Completing an agent run is not completing this acceptance. Do not merge while it is pending.

## Current agent verification checkpoint (2026-11-10)

Reviewed runtime: `789e29db5a1084f4088f91b70781efade32619cb`. Java 25 `check -x testNms`
passed **1,611 tests**, zero failures/errors, with five existing `ServerMetricsCollectorTest` skips.
The targeted run passed **524 tests**, including **296 encounter tests**, zero failures/errors/skips.
Spotless, Checkstyle, production `-Werror`, shadow JAR, InvUI compatibility and `git diff --check`
passed; NMS execution was excluded because no version-specific code changed. Shaded JAR SHA-256:
`34eb6d56dfffd9061a1bee63321a6715b27226904ac94d304481c1f1b37c21be` (matches the reviewed runtime).

Final independent Standards + Spec reviews covered the **entire PR** against
`13c36cf37ab8a080ee83372bf7bb4c6f3ac7c972`: all three Standards findings and both Spec P2 findings
are fixed, including the additional synchronous-abort follow-up; neither axis has remaining
agent-fix findings. See [PR #133 evidence](https://forgejo.officeryoda.dev/OfficerYoda/kreiscraft-java/pulls/133)
for reports and actual native artifact snapshots. The earlier `937` clientless Paper run passed,
but does **not** verify this new hash. The **`789e29d` clientless Paper retest passed** on this exact
artifact with Temurin Java **25.0.4.1** / Paper **26.2.build.132-stable**: nine owned processes exited
0 with zero module failures and all isolated ports released. Both crown-only and full-default setups
rejected four invalid profiles and recovered after each corrected reload. Native stale-PDC recovery
removed **16 tagged fixtures → 0** across enabled/disabled restarts and later-loaded chunks,
preserving all **16 original unrelated control UUIDs**. Each full-default process retained exactly
four known SLF4J ERROR-level stderr payloads; there were no additional unexpected ERROR or SEVERE
lines. This is **clientless startup/configuration/stale-artifact evidence only**: the in-flight abort
guard still has automated public-event proof, not actual native combat proof. Managed-dragon
health/phase recovery, active-interruption restart, combat/AI/landing/rendering/rewards and
representative performance remain unvalidated. None of these checks validates the approximately 20-player
mixed enchanted diamond/netherite **15–20-minute** target; death and normal rejoining remain allowed.
No tuning changed. Keep WIP and #140 open; hand off as `ready-for-human` only after remaining agent
work is finalized. The functional, lifecycle and representative multiplayer checklist below remains
required, not full gameplay/spec acceptance already completed.

## Historical #140 agent-executable integration evidence (`81da751`)

Base: `81da751` with native #138/#139 implementation integrated. This work adds **11 encounter
cases** and expands the existing repeated-attempt and shortened-pool regressions, at the approved module/public-event/
MockBukkit/MockScheduler seam. No production behavior, tuning, adapter, server helper or framework
was changed. No concrete production defect was found in these checks.

- `FinalEncounterTest.twentyParticipantsCompleteTimedOpeningHuntAndFinalRaceBeforeUntouchedNativeCompletion`
  runs both initial and previously-killed battle profiles with 20 mock participants: all four opening
  gates, opening warning/escape, Hunt lane warning/escape, three moving marks yielding one non-stacking
  pool, a complete 12-second public-native-arrival melee window, fixed sweep/escape, shared multipart
  explosion limiting, oversized ranged threshold crossing, exactly two final gates with familiar
  bombardment, accelerated final lane/sweep and another full melee window. Native completion occurs
  with a fresh lane warning pending; duplicate death notifications leave supplied XP (12000/500) and
  drop payloads untouched. Two further virtual minutes produce no artifacts or player damage.
- `FinalEncounterTest.repeatedTwentyPlayerFullAttemptsReturnsAndCancelledAttacksKeepArtifactsAndExplosionWorkBounded`
  now runs three 20-participant attempts through warned opening, at least 60 virtual seconds of Hunt,
  the final pair and at least 180 seconds of final race, supplying public podium arrival events.
  Real MockBukkit combat-event cancellation vetoes custom player damage, and canceled sweeps never
  apply knockback. Each attempt dispatches 1000 fractional explosions without adding scheduled work,
  checks at most four displays and 21 active scheduler tasks, returns at 59.999 seconds to preserve
  the race, then resets after a new complete 60-second absence. Reset restores the same dragon to
  native 200 health, removes owned artifacts and leaves discovery alone; return starts four fresh gates.
- `HuntEncounterTest.publicDepartureEventsCancelTheSameMarkEvenIfEligibilityReturnsBeforeTheNextTick`
  adds eight same-tick departures/returns: arrival via move and teleport, world, arena, creative,
  spectator, death and logout. Outgoing public event destination/new mode differs from current player
  state; the mark must disappear **before** that state is committed. Eligibility returns before any
  scheduler poll/countdown expiry, but only the teammate's pool resolves. Death and join are public
  event envelopes, not proof of real Paper death travel or network reconnection.
- `HuntEncounterTest.aMarkedParticipantCanWalkOutAfterDepositBeforeTheThirdPoolPulse` advances actual
  fixture movement by 0.75 blocks per 250 ms on flat footing (3 blocks/second): the deposited default
  pool deals two pulses, then movement beyond its radius prevents subsequent damage through exclusive
  expiry. This verifies configured policy at the seam, not armor, client reaction or native movement.
- The existing `FinalEncounterTest.finalFinishUsesEarlierFamiliarWarningsAndThreeSecondPools` now
  observes real simulated damage as well as display lifetime: no damage during the mark warning,
  exactly three one-second pulses during the three-second final pool, and no fourth pulse at expiry.

### Required automated acceptance audit

| Required behavior | Existing plus integrated public-seam evidence |
| --- | --- |
| Activation, ritual/duplicate discovery, disabled/unrelated scope | `EncounterTest`, `LifecycleEncounterTest`; both initial/ritual full timed attempts above |
| Four independent opening gates, arbitrary/concurrent clear, unrelated crystals | `OpeningEncounterTest` four-site regression; timed full attempts |
| Shield, melee/ranged/explosion/multipart, aggregate cap and lethal threshold | `EncounterTest` shield/provenance cases; `FinalEncounterTest` all-route, rolling-window/nested-event and timed full-attempt cases |
| Exactly two final gates once, healing/duplicates, native completion | `FinalEncounterTest` independent orders, all-route/large-hit and untouched initial/ritual completion payloads |
| Warning before damage, geometry/routes, exclusive expiry, landing windows | `OpeningEncounterTest` geometry/route cases; `HuntEncounterTest` lane/pool/sweep boundary and exclusive-expiry cases; `FinalEncounterTest` shortened-pool/default accelerated timings and both integrated windows |
| Eligibility revalidation and protected indirect attacks | Existing opening/lane/prey invalidation and arrow/TNT/potion/cloud cases; eight new pre-commit public-event quick returns; existing sweep/pool damage audience and cancellation cases |
| Continuous absence, dead/spectator occupancy, fresh reset | `EncounterTest` timeout/dead/spectator cases; final-transition absence test; repeated full-attempt 59.999-second return and new full timeout |
| Pending/active cleanup, disable/unload/restart, unrelated preservation | Existing opening/Hunt/finale termination-stage matrices and lifecycle stale loaded/later-loaded entity recovery; timed victory with pending warning and no late damage |
| Atomic reload/snapshots, localized instructions/configuration integration | Existing opening/Hunt/finale valid/invalid reload cases, lifecycle/bootstrap/configuration/GUI/message regressions in full `check` |
| Bounded full-attempt/retry growth | Expanded three-attempt check above plus existing eight opening retries and four 20-participant Hunts; scheduler/display limits are checked bounds, **not live performance measurements** |

### Reproduction and red/green limits

Use the Java 25 launcher below; no version-specific code changed, so `testNms` execution is excluded.

```sh
JAVA_HOME=/Users/I767799/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home \
  ./gradlew test --tests 'de.kreiscraft.modules.shatteredcrown.*' --max-workers=2 --console=plain
JAVA_HOME=/Users/I767799/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home \
  ./gradlew spotlessApply check -x testNms --max-workers=2 --console=plain
git diff --check
```

Durable local logs/XMLs: `~/.local/state/kreiscraft-java-runs/140-integration-81da751/`, with `140-`
run names. The initial timed-attempt red run exposed fixture assumptions (cardinal escape orientation
and a cadence opportunity suppressed by a landing), not a production defect; these were corrected
without changing gameplay. New quick-return and repeated-attempt checks pass existing behavior.
A separately retained **negative-control red** temporarily made movement translation consult the
old player location instead of the event destination: four of eight quick-return cases failed.
Restoring the original translation makes them green. This is regression-sensitivity evidence,
**not a claimed production red→green fix**; the mutation is not shipped.

Targeted integration: **280 tests**, zero failures/errors/skips (23 encounter, 35 opening, 120 Hunt,
85 finale, 17 lifecycle). Full `spotlessApply check -x testNms`: **1,592 tests**, zero failures/errors,
five existing `ServerMetricsCollectorTest` skips. Spotless, Checkstyle (main/test/NMS source sets),
production compilation with `-Werror`, shadow JAR and InvUI compatibility passed; `git diff --check`
passed. Existing native-event deprecation, Mockito/ByteBuddy/Unsafe, Shadow Kotlin metadata and Gradle
deprecation warnings remain in the raw logs; a successful check does not mean warning-free output.
MockBukkit does not run dragon AI or render effects, and public death-event payloads do not generate
XP, portals, eggs, gateways or rituals. Simulated timing and scripted fixture movement are not a
15–20-minute fight, native armor evidence or numerical balance validation. The shipped fixed ~20-player
mixed enchanted diamond/netherite profile is unchanged; death and normal rejoining remain allowed.
At this earlier checkpoint, real supported-Paper validation and final Standards + Spec reviews were
still pending. The current agent verification above supersedes only that review/check status, not
the live acceptance requirement; this document does not mark #140 complete or authorize merge.

## Prepare a disposable supported-Paper server

1. Use Java 25 and the supported Paper 26.2 release. Record Java/Paper versions, integration commit,
   plugin JAR SHA256, world seed, plugin list, and the complete effective
   `plugins/Kreiscraft/config.yml`. Use a disposable world or a verified backup, not production data.
   Keep credentials private; redact API keys and other secrets from published evidence.
2. Build the reviewed integration branch with a Java 25 launcher:
   `./gradlew check -x testNms --console=plain`. No version-specific code is expected to change;
   if it does, run `testNms` too. Copy the resulting shadow JAR to the disposable server's `plugins/`.
   Confirm prerequisite implementations #138 and #139 are integrated before full-fight validation;
   retain automated regression/check results separately from live observations.
3. Enable `modules.shattered-crown.enabled`, verify `world-key` against the real main End, and restart.
   Verify the configured arena, arrival platform and all four objective sites have usable terrain.
   Follow the [operator profile](/shattered-crown/) and [hunt geometry contract](/shattered-crown-hunt/).
4. Record `version`, `plugins`, `list`, successful startup, and module-failure count. Keep normal
   Dimension Access, respawn, death penalties and inventory behavior; the encounter must not grant
   travel access. Use the server's existing legitimate route to enter the End.
5. Use real survival/adventure clients. A command-spawned unrelated dragon, creative clients, a mock
   phase setter, or a console-only startup cannot validate the normal managed dragon battle.

## Functional combat session

Capture console logs and timestamped observer notes/video where possible. Record pass/fail and
evidence for each step; fix failures and repeat affected steps rather than merely ticking boxes.

| Step | Action and expected observation |
| --- | --- |
| Activation | Enter with one eligible player, then others. Only the designated main-End normal battle starts; initial and ritual-respawned dragons each start one attempt. Other dragons/worlds remain unchanged. |
| Opening | Split four teams. All four Anchors are reachable in ordinary melee, independently defender-gated, visibly exposed after their own defenders die, and clearable in arbitrary/concurrent order. Unrelated crystals do not advance objectives. Opening bombardment warnings precede matching impact damage. Shield ends immediately only after the last Anchor. |
| Native defenders | Observe targeting, leash/escape recovery, finite waves, despawn/chunk discovery and crystal recovery. No missing entity may leave an impossible gate. |
| Damage routes | Try swords, arrows, multipart hits and simultaneous bed/explosion bursts while shielded and damageable. Cancellation/protection must be respected, ordinary combat applied once, and explosion restrictions scoped to the managed dragon. |
| Crystal interactions | Confirm ordinary crystal healing cannot reverse active progression; outside an attempt, vanilla ritual crystals remain usable. |
| Hunting attacks | Observe lane warning → matching damage area → expiry, recognizable multi-player Marked Prey countdowns → pools at current valid positions → expiry, and fixed-direction warned wing sectors → matching damage/knockback. Verify both English and German recipient instructions and native visuals without mandatory audio/resource packs. |
| Safe placement | React at normal walking speed on representative island terrain. Check existing native/custom danger, overlapping authored attacks, protected arrival exclusion and route deferrals; unsafe combinations must be omitted rather than fill remaining routes. |
| Landings | Observe repeated native flight, approach, actual podium arrival, meaningful approach/melee duration, sitting hit/phase callbacks, takeoff and ranged flight contribution. A window must not expire before arrival or be cancelled immediately by competing phases. |
| Final gate | Cross 30% and also try an oversized lethal hit, multipart callbacks and simultaneous explosions. Exactly two Anchors reactivate once before death; both independently gate shielding. After both clear, healing/duplicate events cannot replay the interlude. |
| Final race | Verify familiar attacks accelerate and pools expire sooner. No new vocabulary, forced-duration wipe or permanent terrain changes from custom effects; objective routes and landing opportunities stay usable. |
| Arrival/provenance | Attack from protection with melee/arrows/primed TNT and any supported attributable indirect source. Fire from protection, then step into the arena before impact. Dragon, Anchors and defenders must reject safe-origin damage; custom danger cannot strike arrivals. |
| Mark invalidation | During a countdown separately die, disconnect, change world/mode, leave the arena and enter arrival protection. No stale pool may resolve. Rejoining through normal server travel resumes participation without teleport/inventory assistance. |
| Late arrivals/departures | Join and leave during each phase. Dragon maximum health and attack counts must not dynamically rescale; new eligible arrivals can participate without restarting the attempt. |
| Native victory | Complete through normal dragon death. Record XP, exit portal, egg/gateway eligibility and ritual eligibility on initial versus subsequent kills. No custom duplicate rewards. |

## Lifecycle and repeated-attempt session

Repeat with attacks pending, hazards active, during native landing, during the final shield and during
the final race. Follow the [lifecycle contract](/shattered-crown-lifecycle/).

1. Kill/leave all eligible players; creative/spectator/arrival-only occupants must not keep the
   attempt alive. Return just before 60 continuous seconds: progress remains and a later absence
   starts a new full timeout. Remain absent for the full timeout: cleanup and a full-health retryable
   battle, no rewards or delayed damage. It waits for an eligible participant; return to start a
   fresh opening with four independent gates.
2. Reload valid timing/damage while an attack is warned: the running attempt retains its snapshot;
   next attempt uses the new values. Try invalid finite/bounds/combinations: clear error and no
   partial application or loss of the running valid profile.
3. Reload with `enabled: false`: immediate abort, cleanup and usable native dragon restoration.
   Re-enable only by restart. Observe normal native flight after abort.
4. Unload/reload the main-End world/chunks using existing supported operator facilities. Pending
   effects must cancel, owned artifacts recover, unrelated entities remain, and no duplicate dragon
   or rewarded kill occurs.
5. Test actual process restart with stale owned artifacts in a disposable world, including loaded
   and later-loaded chunks. Graceful shutdown alone does not prove stale crash recovery. If testing
   an abrupt process stop, back up this disposable server first and record the stop/restart method.
   No half-finished warning/phase resumes; the same surviving battle becomes fresh/retryable.
6. Repeat full fights and resets. Record entity/display/hazard counts and scheduler/particle
   observations using existing server inspection/profiling tools. Check stale UI, missing displays,
   late damage, stuck shields and native phases after each termination. Mocked bounded-task checks
   are not actual server performance evidence.

## Representative multiplayer balance session

Recruit approximately **20 players**, mixed combat experience, with mixed enchanted diamond and
netherite equipment. Death and normal rejoining are allowed. Explain the visible objectives, not an
undocumented strategy that first-time players cannot infer. The **15–20-minute** target is a tuning
goal, not a forced minimum or an automatic failure timer.

Record the following, including unsuccessful attempts:

```text
Date/time/timezone:
Commit / JAR SHA256 / Java / Paper build:
World seed / plugin list / effective configuration attachment:
Participant count (start, min, max) / experience mix:
Armor, enchantments, melee/ranged weapons, consumables and bed assumptions:
Attempt outcome / total elapsed combat time:
Opening / Hunt / final-Anchor / final-race durations:
Deaths / rejoin count / absence resets:
Players contributing at each Anchor; melee and ranged damage participation:
First-time warning comprehension and observed reaction time:
Safe-route/arrival/explosion failures (timestamps and reproduction):
Landing approach and usable melee-window observations:
Actual tick/MSPT/TPS samples or profile attachment and collection method:
Peak/repeated-attempt entities, displays, hazards and particle/load observations:
Initial versus ritual kill reward observations:
Before/after configuration changes and reason supported by these observations:
Retest outcome and remaining failures:
```

Tune only documented numeric health, damage, defender, timing and geometry values from observed
evidence. Preserve four opening Anchors, the once-only two-Anchor 30% interlude, rejoining and native
rewards. A required design change needs explicit approval, not silent numerical evasion.

The final Standards + Spec reviews at the current checkpoint are complete; repeat them if the
integration changes, address findings, and publish
actual test/live evidence, shipped configuration assumptions, remaining limitations and the gameplay
blast-radius assessment in #140 and PR #133. Leave final merge to the normal project workflow.
Until this session and functional acceptance are complete,
leave #140 open, set `ready-for-human` only after remaining agent work is finalized, retain WIP,
and state that balance, first-attempt readability,
real AI/rendering/rewards and representative performance remain unvalidated where not observed.
