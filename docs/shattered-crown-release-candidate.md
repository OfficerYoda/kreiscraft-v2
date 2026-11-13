---
title: Shattered Crown release-candidate verification
---

# Four-player operator sequence (#163)

Use a **disposable End or a verified backup**. Smash, Bombardment and enhanced Wither explosions permanently
destroy terrain, including arrival, podium and objective foundations. Cancel/reset/restart do not
repair anything. Floating Anchors remain objectives; block destruction never clears their gates.
Do not install `build/paper-probe/` JARs on production.

## Setup

1. Java25/Paper26.2 build132; record `java -version`, `/version`, commit, shadow-JAR SHA256, seed,
   enabled plugins and redacted effective configuration. Build with `./gradlew check build --max-workers=2`
   (**includes `testNms`**), deploy `build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar`, not the thin JAR.
2. Set both `modules.shattered-crown.enabled: true` and `modules.wither.enabled: true` before startup.
   Crown cannot substitute vanilla Withers. A valid reload changes the next Encounter snapshot;
   Wither tuning changes subsequent roots/effects live. Crown false aborts immediately; true later
   requires process restart. Do not use `/reload` or hot plugin re-enable as restart evidence.
3. Four Survival/Adventure players: full netherite with **Protection IV on all four armor pieces**,
   maxed melee/ranged weapons, ordinary food, arrows and documented consumables. Record exact weapon
   enchantments and any bed/explosion use. For damage calibration remove **Resistance and absorption**
   (no beacon Resistance, enchanted/golden-apple absorption); start each single-hit case at20HP.
   Do not use Creative, god mode or potion damage immunity for calibration.
4. Use ordinary server travel into the real main End; Dimension Access/travel/death rules are unchanged.
   Defaults: radius192/full build height about native podium; arrival cylinder `(100,50,0)`, radius8,
   height12. Adjust only if the server's real arrival/podium differs.

## Expected progression and numbers

| Wave | Gate | Four-player complete roster | Population5–8 | Population20 |
| --- | --- | --- | --- | --- |
| One | 100% | Four Anchors,64 members: three mounted pairs per player, two three-member towers, individual undead | 80–128 members | 320 members |
| Two | 65% | Four Anchors,32 roots: eight Brutes, six Vexes, three Ravagers/three Breezes, four each of the other three species | 41–64 roots | 158 roots |
| Three | 30% | Four Anchors,2 Wardens+2 enhanced Withers, plus owned Wither minions | 3+2 at5–6;4+2 at7–8 | 12+6 roots |

All mob/boss rosters cap their scaling population at20; raw population snapshots and combat eligibility remain unchanged.
Wave one has16 mobs per scaling player. Wave two has `5*p + 2*ceil(0.7*p) + ceil(1.5*p)` members, where `p=min(snapshot,20)`.
Mounted pairs/towers remain intact; opening arrows disappear three seconds after their first accepted block hit.
Wave replacement/reset removes them immediately and cancels pending expiry tasks.
Towers have three members, with one tower per two players rounded up. All defenders retain vanilla species health;
encounter Withers keep other module enhancements and use a proportionally adjusted half-health threshold.
Wave three interpolates doubled four-player targets for Wardens, minimum2:
1–4→2, 5–6→3, 7–8→4, 12→6, 13→7, 14→8, 15→9, 16→10, 17–18→11, 19+→12.
Withers use half the Warden count rounded up, minimum2. Final bosses are spread evenly across Anchors,
balancing species too; 1–4 players have one boss per crystal, and twenty players have four or five per Anchor.
Opening freezes everyone online server-wide at first
End entry; connected dead arrivals retain that snapshot while awaiting respawn. Later waves freeze
everyone connected in the managed End, including Creative/Spectator,
outer-island and connected dead players. Those observers/dead players are never combat targets and
do not sustain absence. Joining/leaving does not resize a spawned wave. Prey requests distinct
`ceil(.35*all-End)` targets:4→2,8→3,20→7, capped only by eligible combat players. Bombardment targets ALL eligible.

The dragon defaults to1000HP. Oversized melee/ranged/explosive/multipart hits clamp at650/300;
all defender/minion lives must die before their own crystal glows. Destroy all four crystals in
any order; defender deaths alone do not complete a wave. Permanent unlocks: Prey initially,
then Dive after wave one, Bombardment after wave two, then Smash after wave three. Initial30-second grace has active defenders but no dragon attacks.
Shuffled rounds use every available family once, no immediate repeat; default gap2–4 seconds AFTER main
completion (one second after rejected initial setup). Landings reserve six seconds from actual arrival, not approach start; old pools remain.

| Action | Default warning/motion | Clean reference HP loss on Easy/Normal/Hard |
| --- | --- | --- |
| Bombardment | Fixed radius4,3-second warning; permanent shallow crater and six-second pool | 6HP/3 hearts |
| Dive | 2-second warning,1.5-second pass with early plunge,1-second climb;40×10 lane; NO particles | 8HP/4 hearts, once at actual low pass |
| Smash | FULL3-second visible warning,1-second plunge,3-second grounded recovery,2-second climb | 12HP/6 hearts, radius8 |
| Prey pool | 4-second mark;radius5;6-second ordinary lifetime,3 seconds in finale | Configured raw6HP/pulse, NOT calibrated6HP loss |

All Prey/Bombardment pools share maximum one pulse per player per20ticks; Bombardment pools always last6 seconds.
Smash crater radius5/depth1; Bombardment radius4/depth1/no fire/native double explosion. Harmless debris
has default4-second lifetime/cap128 per impact and cannot deal extra damage, place blocks or drop.
Wither defaults600HP/40attack/8armor,220-tick charge/power20 spawn,300HP threshold/power4 blast/four
minions,10-second power20 death countdown. No owned Nether Stars; ordinary Wither rewards unchanged.

## Runnable manual sequence

As OP (or `kreiscraft.shatteredcrown.debug`), record each command result and visible world behavior:

```text
/dragondebug status
/dragondebug pause
/dragondebug wave 1
/dragondebug trigger bombardment <player>
/dragondebug cancel
/dragondebug trigger prey <player>
/dragondebug status
/dragondebug cancel
/dragondebug trigger dive <player>
/dragondebug cancel
/dragondebug wave 2
/dragondebug trigger bombardment
/dragondebug cancel
/dragondebug trigger bombardment <player>
/dragondebug cancel
/dragondebug wave 3
/dragondebug trigger smash <player>
/dragondebug cancel
/dragondebug resume
```

Debug wave jumps replace owned roster/effects, set health1000/650/300, grant earlier unlocks, freeze
fresh population and leave automatic attacks paused. Manual triggers bypass unlock/scheduling only,
not target/geometry/protection. For natural progression do NOT wave-jump: defeat every assigned
root/mount/passenger/minion, destroy all four crystals, cross each health threshold, then legally kill
the final dragon. Wait through native DYING; compare initial12000/subsequent500XP, native portal,
egg/gateway and actual four-crystal ritual. Do not force ritual END or fabricate death/reward events.

Repeat each calibration difficulty with a fresh clean hit. Dodge Bombardment after warning; test real cover
and attempted deflection. Observe pools through actual expiry and old-pool landings; do not infer
health loss from configured raw arguments. Return through native death/respawn and quit/rejoin,
including during Prey warning. Old marks must not deposit after return.

## Reset and cleanup

- `pause`/`cancel`: main idle,hazards0,temporary entities gone,native AI restored,craters retained.
- Move all eligible participants out; return at59 seconds: same attempt. Start new absence and
  wait60 continuous seconds: old entities/callbacks gone,same native dragon full200HP,retryable.
  Creative/Spectator/connected dead/arrival-only occupancy is not presence.
- Reload invalid Bombardment/geometry/timing while warned: entire live tree/current snapshot unchanged.
  Correct and reload: current attempt unchanged,next attempt consumes new values.
- Exercise real relevant chunk/world unload/load and an owned saved active-process interruption/
  restart. Verify every artifact kind and countdown removed,unrelated control UUIDs/terrain retained,
  no late blast after10 seconds in a later attempt. Do not mistake synthetic unload events or
  graceful fake-orphan fixtures for interrupted active-process evidence.
- Repeat on accumulated craters,including destroyed objective/arrival foundations; no rebuild cheats.
  Never claim all terrain permutations or production performance from an isolated fixture.

## Native assertions versus human observations

The two-module final-wave fixture uses the shared runner:
`python3 scripts/run-dragon-wave-probe.py NEW_DIR PAPER_CACHE_DIR PRODUCTION_JAR final`.
`scripts/run-final-wave-probe.py` remains a compatibility entrypoint with the same artifact,
timeout, process-exit and port-release validation. `python3 scripts/check-final-wave-runner.py`
checks routing/staging with a fake JVM; it is not native combat evidence.

Pinned executable commands,versions,hashes,counts,failed attempts and runtime measurements are
recorded in `sc-163-evidence.md`. Registered native fixtures use genuine Paper worlds,AI,events,
damage,PlayerList and DragonBattle with synthetic packet transport/ACKs. Health replenishment,
terrain,view/simulation distance and any isolated damage conditions must remain stated.
Outgoing packets prove server emission,not client receipt. Mock scheduler timings are not MSPT.

The remaining **four-human** session records rendered flight/model/hitbox alignment,world countdown/
particle readability (including EN/DE),audio-optional comprehension,dodging/melee reach,combat feel,
death/rejoin experience and balance. Record video/timestamps,exact gear/attendance,outcome/durations,
deaths/objective contributions,TPS/MSPT/heap/GC and peak/repeated entities/tasks. No20-player or
15–20-minute promise supersedes the approved four-maxed-player reference. Unavailable humans are
not a hidden blocker to agent-executable RC verification; do not fabricate their observations.
