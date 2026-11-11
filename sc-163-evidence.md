# #163 integrated release-candidate evidence

**Agent-executable verification complete.** Ready for the parent's Standards+Spec review and
final delivery build; the separate four-human presentation/balance session remains unperformed.

## Authority and artifact

- Branch: `feat/sc-163`; integration: `feat/shattered-crown-redesign` at
  `a73e772263cbc7d841e9f7d0835cc92310f0c5e0`. `git fetch origin` and
  `git merge --no-edit feat/shattered-crown-redesign` reported already up to date.
- Full Forgejo #163/comments/dependencies and approved #149–#162 requirements were read through MCP.
  Those accepted features supersede historical encounter/balance requirements. No integration edits,
  nested agents, push, PR, issue changes or closure.
- Production shadow JAR: `build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar`, SHA256
  `5ef6df0ac337826a452ee69259cb2272b180beffc11fecac5d07b7c439bc89c7`.
  Gameplay production code is unchanged; obsolete EN/DE configuration labels are explicitly ignored.
- Evidence prefix **T**:
  `/private/var/folders/r7/r9tq2bhs3ylf3nwcg_jr6_xr0000gn/T/opencode`.
  Final pinned production/probe bytes: `T/sc-163-artifacts-14/manifest.json`; earlier sets2–13 retained.
  Runner `artifacts.json` records deployed bytes, not merely HEAD (changes were uncommitted during runs).
- Paper `26.2-132-ver/26.2@19ebc4a`, API `26.2.build.132-stable`, Minecraft26.2;
  launcher SHA256 `5ab560a769c1ab413cb7f637dd0dc697974571f2db0a667cfbac511422e51b26`.
  Temurin Java25 `25.0.4.1+1-LTS`, Gradle9.6.1, macOS ARM64.

## Required checks executed

`T/sc-163-check-11.log` records successful final Java25 execution; all30 tasks executed in55s:

```sh
./gradlew check build releaseCandidateProbeJar rainProbeJar finalWaveProbeJar \
  dragonWaveProbeJar openingRosterProbeJar secondWaveProbeJar rotationProbeJar \
  diveEncounterProbeJar smashEncounterProbeJar dragonDamageProbeJar dragonFlightProbeJar \
  defenderRoamingProbeJar --rerun-tasks --max-workers=2 --console=plain
```

`T/sc-163-final-verification.json` contains the actual XML report counts. Spotless apply preceded
the final check; final Spotless/static/test/NMS/build tasks ran, not cached acceptance claims.

| Task | Suites | Cases | Failures/errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| `test` | 185 | 1839 | 0/0 | 5 |
| `testNms` | 3 | 16 | 0/0 | 0 |

The five existing skips are `ServerMetricsCollectorTest` MockBukkit
`UnimplementedOperationException` cases, unrelated to Crown. No NMS exemption or acceptance-stage
skip is claimed. Spotless, Checkstyle, production compilation, shadow build and all listed probe
builds ran. Production is byte-identical across the successful replays. Final comparison
`T/sc-163-final-probe-class-comparison.json` proves all11 executed slice entry/nested classes
are byte-identical to the final build. Some legacy all-class JARs additionally bundle unused
updated release classes; this is content equality, not a newly executed slice replay.

## Fresh native slice results on the production hash above

All directories below are under **T**; full consoles, owned PID/exit records and artifacts remain there.
Unless stated otherwise, two production modules started: Crown and enhanced Wither;23 disabled,0 failed.

| Directory | Genuine result | Scope/limit |
| --- | --- | --- |
| `sc-163-replay-rain-1` | `RAIN_PROBE PASS`,102584 checks; restart PASS,4 checks | Real LargeFireball collision/descent/attribution,4HP calibration, no deflection or native double explosion, all-target/cover/co-location/timeout cases, permanent crater/debris, shared pools and cleanup. Saved-orphan restart is narrower than active interruption. |
| `sc-163-replay-wave-1` | `DRAGON_WAVE_PROBE PASS`,24ticks | Native battle adoption, frozen populations, real deaths/crystal hits,65/30% clamps, attribution and all debug waves/cleanup; not full native rewards. |
| `sc-163-replay-opening-2` | `OPENING_ROSTER_PROBE PASS`,298 checks,1591ticks | Current mounted/tower roster and native movement/melee/arrows, equipment/HP loss, ownership/recovery. Inactive calibration groups parked in clear air only after original native spawn/health checks; not production spawn changes. |
| `sc-163-replay-second-2` | `SECOND_WAVE_PROBE PASS`,251 checks,1179ticks | All six native species movement/targeting/attributed attacks, population8 roster and recovery. |
| `sc-163-replay-smash-3` | `SMASH_ENCOUNTER_PROBE PASS`,2190 checks; restart3 checks | Actual12HP Easy/Normal/Hard, full4s warning, physical flight, crater/debris collision, ordinary native follow-up Dive/Bombardment/Prey over permanent damage. Automatic shuffled Smash now waits for actual selection; unrelated earlier damage isolated. |
| `sc-163-dive-10` | `DIVE_ENCOUNTER_PROBE PASS`,2703 checks | Debug Dive8HP all difficulties, automatic Dive, cancellation/invalid targets; debug+automatic Bombardment6HP all difficulties. No teleport/HOVER/noAI. Actual lingering owned Prey pool breath packets counted separately; zero Dive particles. |
| `sc-163-replay-damage-5` | `DRAGON_DAMAGE_PROBE PASS` | Native damage boundary/reference calibration, not a registered-player whole fight. |
| `sc-163-replay-flight-5` | `DRAGON_FLIGHT_PROBE PASS`,80ticks | Native unrelated driver dragon/body motion and zero teleports; not Encounter whole-fight proof. |
| `sc-163-replay-defender-5` | `DEFENDER_ROAMING_PROBE PASS`,30 checks,285ticks |43-member opening, native distant movement/attributed melee, ally/mode policies and cleanup. |
| `sc-163-pools` | `POOLS_NATIVE PASS`,843 checks,1250ticks | Fresh adapted historical pool fixture: automatic4/8/20→2/3/7 marks, eligible2 cap, real death/quit/mode/world/arrival invalidation, radius5/5.01, native HP,20tick global pulse gate, cancellation/startup failure, independent6/8s expiry, final3s/explicit6s, connected-dead snapshots19/current final bosses. |
| `sc-163-pools-rain/server3`, `sc-163-pools-rain/console3.log` | `POOLS_NATIVE PASS`,1296 checks,1166ticks | Automatic shuffled native Rain overlaps actual Prey pools: four real4HP impacts while Prey still has7.8s, then four globally gated mixed-pool pulses. Explicit supported15s Prey configuration; Rain retains6s. Not default-lifetime overlap or synthetic damage/deposition. |
| `sc-163-replay-rotation-6` | `ROTATION_PROBE PASS`,1704ticks | Opening grace/shuffled rounds/exact100tick completion gaps; automatic native arrival→departure120ticks, six old-pool pulses/six actual HP drops, AI retained and cleanup. |
| `sc-163-replay-final-6` | `FINAL_WAVE_PROBE PASS`,72 checks,2847ticks | Current population5→8 Wardens+8 enhanced Withers; three native spawn/half/death blasts,four pre-owned minions/original gate,no owned stars,ordinary reward control; real Warden sonic/native skull impacts,65s idle persistence,relevant native unload/recovery and delayed cleanup. |
| `sc-163-population` | `POPULATION_NATIVE PASS`,1967 checks,2064ticks | Fresh current43-member opening, radius191.9/192.1/full build height[0,256), four server-online frozen entry/66 connected scaling,600tick native grace/phase/fireball/cloud veto and debug bypass, actual protected-origin outgoing arrow after owner leaves,incoming owned arrow arrival veto,protected-source native TNT breaks9 glass blocks,60s absence and disable. |

Exact reusable commands use built pinned bytes and **new** evidence directories:

```sh
export JAVA_HOME=/Users/I767799/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home
export CACHE="$T/sc-157-native-merged153/server"
export PROBE_JAR="$T/sc-163-artifacts-14/release-candidate-probe-0.1.0-SNAPSHOT.jar"
python3 scripts/run-dragon-wave-probe.py NEW_DIR "$CACHE" \
  "$T/sc-163-artifacts-14/Kreiscraft-0.1.0-SNAPSHOT.jar" release
# Continue ONLY a genuine same-production interrupted backup in another fresh directory:
RELEASE_SNAPSHOT="$T/sc-163-release-14" python3 scripts/run-dragon-wave-probe.py \
  NEW_RESTART_DIR "$CACHE" "$T/sc-163-artifacts-14/Kreiscraft-0.1.0-SNAPSHOT.jar" release
# Other modes: wave, rain, opening, second, smash, dive, rotation, damage, flight, defender.
# Set PROBE_JAR to that mode's exact manifest filename, not a wildcard.
PRODUCTION_JAR=PINNED_SHADOW PROBE_JAR=PINNED_FINAL_PROBE \
  python3 scripts/run-final-wave-probe.py NEW_DIR "$CACHE"
python3 "$T/sc-163-pools/run-native.py"
python3 "$T/sc-163-pools-rain/run-native.py"
python3 "$T/sc-163-population/run-native.py"
# External adapters also require fresh copied directories; retain their exact source/config/log evidence.
```

## Integrated native attempt and actual interrupted restart

`ReleaseCandidateProbe` discovers the **production** listener/controller. It registers native
`ServerPlayer`s with actual `PlayerList` and ticks real connections; synthetic transport ACKs
native teleports/keepalive and records packets. It does not create another combat engine.

The executed main phase of `sc-163-release-14/console.log` verifies initial30s grace, opening shuffled turns,
four independent/out-of-order native objective gates, actual native death/respawn through
`PERFORM_RESPAWN` and same-profile quit/rejoin, population4→8→20 (including connected dead),
all six species and40 final bosses/four half-health minions, all-five shuffled turns and
100tick post-completion gaps. Native respawn on Paper132 reuses the `ServerPlayer`; fixture
follows `connection.player`/UUID rather than manufacturing a replacement.

Main-phase evidence is followed by **successful continuations of its exact saved interruption**,
not a relabeled failed whole-run exit:

| Phase / files under T | Actual assertions/result |
| --- | --- |
| `sc-163-release-14/console.log` |23902 checks,11438ticks, two genuine native final deaths; initial12000/subsequent500 actual source-attributed merged orb units; both200tick DYING animations, portals/gateways (19→18 remaining), first egg formation/subsequent native veto; two complete unforced four-crystal rituals;77 committed native columns remain destroyed through retry. |
| Same main console, stages13–26 | Actual concurrent enhanced bosses/minions, native Rain impacts/projectiles/debris/shared pools, queued Wither death effects; invalid `.NaN` reload rejected atomically; corrected900HP/Rain22 snapshot applies next attempt; phase/debug replacement; return before60s retains attempt/full60s resets original200HP; actual relevant EntitiesUnloadEvent/recovery; real saved main-End WorldUnloadEvent/WorldLoadEvent, same dragon/control UUID and fresh later attempt. |
| Same main console, interruption | `RELEASE_INTERRUPT_READY`, all seven kinds `[defender-effect,rain-warning,anchor,pool,debris,rain-projectile,defender]`; automatic chat/actionbars/titles0; native world/boss cues/debug replies retained; saved own JVM SIGSTOP/backup/SIGKILL, exit−9. `backup-key-entities.json` verifies actual native dragon, persistent unrelated control, minion+control in deliberately later-loaded chunk11,2. |
| `sc-163-release-restart-18/{console.log,console-disabled-restart.log}` | Enabled `RELEASE_RESTART_PROBE PASS`,619 checks/600ticks; disabled `RELEASE_DISABLED_RESTART_PROBE PASS`,350 checks/340ticks; aggregate `RELEASE_CANDIDATE_VERIFICATION PASS`. |
| `sc-163-release-restart-19/{console.log,console-disabled-restart.log}` | Same successful619/350-check enabled/disabled repeat on final pinned probe. Genuine later-load event for minion `078fde42-b51a-41b5-843e-4703b124264d`; minion removed/unrelated control retained; same native dragon `6c8ff5c7-aa34-48eb-8d2d-7a2dd823573a` restored200HP/native AI/cleared health+phase ownership; clean43-member retry, old callbacks inert; Wither false remains restart-only but live650HP tuning works; Crown false aborts immediately/true later never hot-registers; actual disable. |

`T/sc-163-final-verification.json` compares **all133 backup files byte-for-byte** between source14
and both successful continuations, validates artifacts, XML counts and released ports. The runner
rejects snapshot input unless source console reached genuine interruption, source exit is−9 and
production SHA256 matches. It never constructs replacement rewards, rituals or saved entities.

Source14's original follow-on restart failed an over-strict fixed40tick/`CIRCLING` sample;
it remains a failed overall runner result. Native loading is asynchronous, and vanilla AI may
legally choose `LAND_ON_PORTAL` after recovery. Final checks wait bounded200ticks for actual
200HP/max200/native AI and cleared phase/health ownership, then exercise a real clean retry.
Nothing forces a phase/health value to manufacture that verdict.

Fresh protected-arrow geometry uses an actual owned Skeleton detached from its mount;
mounted combat is verified separately by the current opening-roster probe.

## Failures preserved and fixture corrections

All failed consoles remain; no failed run is relabeled as success.

- Release1: assumed a replacement respawn player; native Paper132 reuses the same object.
- Release2/3: manual trigger correctly clears prior attack hazards; pool must be deposited during
  actual approach. Synchronous rejected selection completed before the next sampling tick.
- Release4: native merged-orb `count` was omitted. Count units, not orb entities.
- Release5: registered player killed before real60tick client-load immunity ended.
- Release6: default Easy scales raw3 pool events to2.5; set explicit Normal for this measurement.
- Release7: approved subsequent Wither terrain destruction can remove the first egg. Observe real
  `DragonEggFormEvent` first formation/subsequent native veto, never manufacture or protect an egg.
- Release8: actual Rain resolves before a later30tick artifact sample. Stage native airborne
  recipients so one real impact produces debris while siblings still fly; never manually tag fake debris.
- Release9:12000 payload but11693 End-only orbs. Final source-aware cross-world merged-unit
  measurement actually passes12000/500. The earlier loss mechanism was not conclusively diagnosed;
  no production reward fix is claimed.
- Release10: real destruction removed the selected column completely. Select surviving native
  footing, leaving every committed crater untouched.
- Release11/12: explicit fixture save-command broadcast was not Encounter chat; subsequent real
  interrupted restart exposed ordinary control despawn and same-tick far-chunk save timing.
  Preserve the failed interrupted backup; stage persistent controls before the live Rain checkpoint.
- Release13: outside-arena minion was correctly returned to Anchor; use a native in-arena later-load
  chunk, with non-persistent ticket and saved-location assertion rather than changing production policy.
- Release14/16: fixed startup sampling/always-CIRCLING is not native recovery semantics. Disabled
  repeat16 actually observed200HP/max200/AItrue/cleared metadata and vanilla `LAND_ON_PORTAL`.
  Require cleared ownership and native control, not a permanently frozen phase.
- Restart17: separate plugin classloaders cannot access package-private `EncounterRecovery`;
  inspect its existing public persistent marker instead. Restart18/19 passed with final corrected checks.
- Opening1: AI-disabled inactive Spider suffocated on uneven native terrain after initial health
  assertions. Park frozen groups separately in clear air; native active combat still runs unchanged.
- Smash2: automatic now shuffles; wait real Smash selection before measuring. Earlier unrelated
  pool damage is not a Smash hit. Native charged-Wither death clearing re-enumerates descendants.
- Dive3/4: automatic preceding Prey leaves breath particles. Identify actual type/owned pool;
  calibration remains8HP once and no Dive particles.
- Rotation5: pool measurement did not pin difficulty/ACK teleport. Final5: idle AI Warden selected
  arbitrarily outside calibration floor; the idle-persistence fixture now uses native podium footing.
- Natural Rain/Prey overlap exploratory1 rejected20s pool config (supported maximum15s);
  exploratory2 lacked Protection IV and thus could not satisfy4HP reference. Full logs retained.

## Resource and evidence boundaries

The release probe logs100tick samples: actual owned species/entity counts, pending production tasks,
heap committed/used, GC counts/time, Paper TPS/average tick time and outgoing packet counts.
Runs use loopback ephemeral ports, fresh copied caches/worlds,2GiB heap,view/simulation3, force-loaded
fixture chunks, natural generated island terrain and production-destructible foundations.
Synthetic endurance replenishes health/food only **after** observing genuine pool HP loss; this is
not four-human survival/balance evidence. Some runs overlapped another owned JVM/Gradle and host
pauses; measured resources must not be marketed as production MSPT or capacity benchmarks.

Run14 recorded114 samples over ticks100–11400: owned entities0–152, pending production tasks2–27,
used heap395759256–719455368 bytes, committed heap675282944–793772032 bytes, observed Paper
average-tick samples2.246–11.512ms. Full TPS/GC/packet/species samples remain in the console.
The66-player boundary replay overlapped part of this run; these are observations under fixture load,
not human fight capacity or production throughput claims. No owned recorded PID remains alive:
`T/sc-163-final-owned-processes.json`. Source14 port64972 and successful continuation ports
50528/50560/50764/50804 were checked released; all continuation JVM exits0.

First/next native reward orbs are not fabricated. Fixture pickup cancellation preserves measurement;
merged counts and source UUIDs are checked. Production terrain destruction is not reverted.
Only saved owned disposable JVMs may be frozen/killed. Release runner records
`save-all flush → SIGSTOP owned PID → world backup → SIGKILL owned PID`; no plugin-disable callback
is substituted for an interruption. Disabled startup forks the same interrupted backup, not residue
already cleaned by enabled startup.

## Documentation and separate human work

`docs/shattered-crown-release-candidate.md` supplies current four-maxed-player equipment,
uncapped populations/rosters, reference6/8/12/4HP, commands, lifecycle/config/restart and permanent
terrain checks. Encounter/flight/hunt/lifecycle/validation and config/command/module references
link current acceptance and explicitly distinguish historical20-player/final-pair/WIP statements.

No four-human rendered readability/audio-optional comprehension/model alignment/melee reach,
combat feel or balance session is available or fabricated. Outgoing packet assertions do not prove
rendered presentation, authenticated login or a real client reconnect. Parent retains final
Standards+Spec review, final build/delivery/merge and issue closure. No external agent-executable
blocker remains; no push/PR/issue closure was performed.
