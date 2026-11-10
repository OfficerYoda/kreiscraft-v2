---
title: Dragon AI control on Paper 26.2
---

# Dragon AI control on Paper 26.2

Research date: **2026-11-10**. This is source/API research, **not a live-server reproduction**.
No production or test code was changed. There was no existing `docs/research/` directory;
this note uses the title front matter and Markdown style of the encounter documentation.

## Conclusions

- **Yes, dragon AI can be modified.** Supported Bukkit APIs select/cancel/redirect the existing
  phase machine. Arbitrary waypoints or a replacement phase implementation require a
  version-specific native boundary; Bukkit has no custom-dragon-phase registration API. [A1, A2, S1, N1]
- **Do not use `setAI(false)` for a moving, hittable replacement fight.** In this exact dragon
  implementation it skips not only decisions/movement but also flight-history recording,
  multipart repositioning and the normal battle-update call in `aiStep`. [S2, N2]
- **Paper's HOVER does not fight scripted movement by flying back to its target.** Paper explicitly
  excludes `HOVERING` from its native steering/movement block. Keep AI enabled to retain the
  remainder of the dragon tick. Changing the stored hover target or setting velocity alone
  does not make that excluded movement block execute. [S3, N2, N3]
- **There is no blanket EnderDragon teleport rejection in the inspected build.** Its Bukkit wrapper
  inherits `CraftEntity.teleport`; the native dragon and its intermediate superclasses do not
  override that teleport path. A live, valid dragon's same-world teleport can proceed, but is
  cancellable/redirectable through `EntityTeleportEvent`. A successful return is not proof that
  the client animation is smooth or the parts have already moved during the callback. [S4, N4]
- Consequently **“native AI overrides our HOVER teleports” and “dragons cannot teleport” are not
  established explanations for the missing attacks**. First distinguish never selected, rejected
  before warning, interrupted on first flight tick, and invisible/poorly rendered movement.
  The current controller has many independent eligibility and safety gates. [R1, R2]

## Exact artifacts and evidence scope

| Artifact | Identifier |
| --- | --- |
| Repository dependency | `io.papermc.paper:paper-api:26.2.build.132-stable`; same Paper dev bundle, `gradle/libs.versions.toml` |
| Local mapped server | `.gradle/caches/paperweight/taskCache/mappedServerJar.jar` |
| Server manifest | `Implementation-Version: 26.2-132-19ebc4a`; branch `ver/26.2`; build 132; build time `2026-10-06T18:55:54Z` |
| Paper source commit | [`19ebc4a49b725b1ea813f95ac559a2ed3931ee4c`](https://github.com/PaperMC/Paper/commit/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c), resolved against GitHub's first-party commit API |
| Mapped server SHA-256 | `ab6f4a3ed6b496c2d1d0efd426f3737f2b75bb5abd9dceab4c818bcee797f754` |
| API sources | `~/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/26.2.build.132-stable/d64e9e396eeb8c13f8071bab3b149e9cb9ec0283/paper-api-26.2.build.132-stable-sources.jar` |
| API source SHA-256 | `02e0b37d9605e867093e29cae13c8dba7c2c16e9ccbe4940a137ffeb1934f88b` |
| Embedded Minecraft version | `version.json`: id/name `26.2`, protocol 776, world version 4903, Java 25, build time `2026-06-16T12:01:27+00:00` |
| Official stock client | Mojang Minecraft 26.2, [version metadata](https://piston-meta.mojang.com/v1/packages/f28ad96ed5d65e2438da836144a3de15bb9cafe7/26.2.json), release time `2026-06-16T12:03:33+00:00` |
| Client binary | [Mojang client.jar](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar), 39,193,383 bytes; verified SHA-1 `2dc72797acbc1b63fc16a11c4ac393605f453754` |

Server claims below refer to **this mapped build**, not all older Spigot/Paper versions or the
the live trace's **26.2 build 135 / `e4d6dfd`**, which was not inspected here. Client claims come from the actual official
26.2 client binary, not a server jar pretending to contain a renderer. The client was downloaded
to `/tmp/opencode/dragon-ai-research/client.jar` and inspected read-only with `javap`; its class
names are readable without a separate mappings download. Temporary disassemblies are not durable
citations: the artifact identifiers, class names and method/bytecode locations below are.

## 1. Supported API: select vanilla behavior, not arbitrary trajectories

`EnderDragon.Phase` has eleven fixed values, including portal approach/landing/takeoff,
breath/search/roar, charge, death and hover. `setPhase` selects the next behavior; it has no
target, route, speed or arrival-completion parameter. The API describes HOVER as hovering
without actions. `getPodium`/`setPodium` alter the podium location, not an arbitrary attack path. [A1]

`CraftEnderDragon.setPhase` delegates to `getHandle().getPhaseManager().setPhase(...)`.
Bukkit enum ordinals correspond to native IDs; `getPhase` reads synchronized `DATA_PHASE`,
and native-to-Bukkit conversion indexes `Phase.values()` by ID. [S1]

`EnderDragonChangePhaseEvent` is cancellable and permits replacing the proposed phase. The
manager's actual order is old phase `end()` → event → select instance → synchronize ID →
new phase `begin()`. Cancelled changes return **after the old `end()`**, so custom phases must
not assume that cancellation means their lifecycle was untouched. Same-phase requests normally
short-circuit without restarting `begin()`. `setPhase` returns `void`: verify the actual resulting
phase after synchronous listeners, and handle reentrant teardown. [A2, S5, N1]

Replacing ordinary Mob goals is not the demonstrated seam: the native dragon's own `aiStep`
directly ticks its phase manager and performs flight movement. A goal-selector edit alone does
not replace that code. This is an inference directly from that tick path, not a claim that every
possible Mob API is unusable on dragons. [N2]

## 2. `setAI(false)` disables too much

`LivingEntity.setAI(false)` is supported API and the API warns that no-AI entities cannot move.
`CraftLivingEntity` implements it as `Mob.setNoAi(true)`; the dragon is a Mob. [A3, S2]

In this build's `EnderDragon.aiStep`, bytecode offsets 398–412 check `isNoAi()` and jump to
the method return at 1952. That bypasses:

- `flightHistory.record(y, yaw)` at 415–427;
- phase ticks and native steering;
- multipart `tickPart` positioning;
- normal `checkWalls` calls and `dragonFight.updateDragon(this)` at 1816–1828. [N2]

This is not “turn off vanilla goals, keep the dragon physics and hitboxes as before.” The source
establishes that these updates are skipped; stale hitboxes and battle liveness problems during
prolonged no-AI control are **risks to live-test**, not reported observations from this research.
Base ticking, healing and damage/death have other paths; do not generalize this to “nothing ever
ticks or updates the fight.” Restore the original AI flag if experiments change it. [N2]

## 3. HOVER: enabled multipart ticks, disabled native translation

Native `DragonHoverPhase` captures the current position as its target on its first server tick,
clears the target in `begin`, returns fly speed 1, and returns **`isSitting() == true`**. [N3]

However, Paper/CraftBukkit patches the steering condition to:

```java
if (targetLocation != null && currentPhase.getPhase() != EnderDragonPhase.HOVERING) {
    // native acceleration, turning, move(MoverType.SELF, ...), damping
}
```

This is an explicit upstream “Don't move when hovering” patch, not conjecture. Both acceleration
and `move(...)` are inside the condition (native bytecode 510–987). The tick then continues to
multipart positioning and battle upkeep with AI enabled. [S3, N2]

Implications:

- Stock HOVER is a plausible **suppression phase** for direct scripted position updates.
- Stock HOVER + Bukkit velocity is not a demonstrated native-flight solution; the normal dragon
  movement integration is skipped.
- A custom phase that returns `HOVERING` while merely supplying waypoints is also skipped.
- HOVER's sitting semantics affect dragon damage/knockback and stock-client animation. It is
  not an airborne vanilla phase with only its goals paused. [N2, N3, C1]

## 4. EnderDragon teleport, specifically

In this build `CraftEnderDragon` has no teleport override. `CraftEntity.teleport0` rejects a dead
or invalid entity, otherwise creates a `TeleportTransition` with requested world/position/yaw/
pitch, zero motion and no relative flags by default, and returns whether native `teleport` returns
non-null. Neither native `EnderDragon`, `Mob` nor `LivingEntity` declares a replacement for this
`teleport(TeleportTransition)` method. [S1, S4, N4]

The inherited native path calls `EntityTeleportEvent` and returns null on cancellation or a null
destination. Listeners can change the destination. For a same-dimension move it calls
`teleportSetPosition`: position, yaw/head yaw, pitch, old-position state and delta movement are
updated, then movement bookkeeping is cleared. There is no dragon-type refusal on this path. [N4]

**Multipart nuance:** that generic teleport setter does not reposition the dragon's child parts
or rebuild its `DragonFlightHistory`; those are dragon-tick responsibilities. Thus a callback
immediately after teleport can observe the parent at the destination while parts still describe
the previous dragon tick. The next enabled dragon `aiStep` positions them. This is source-derived
ordering, not a measured player-hit/rendering failure. [N2, N4]

The current `DragonFlight.start` cancels and closes silently if `teleport(destination)` returns
false, before `step` or the attack continuation. It also closes on invalid participation, removed
indicator, invalid entity or a phase other than HOVER. This is a **concrete interruption path**,
but no evidence here establishes that teleport returned false on the live server. Log the actual
return and phase/event outcomes rather than replacing teleport based on old-version folklore. [R1]

Repeated teleport resets motion/old-position state and uses the teleport network path; it is
not the same operation as ordinary flight. Smoothness, tick-order effects and multiplayer
hitbox alignment remain live-unverified. Cross-world relocation is outside the encounter design:
do not use it to control a battle-associated dragon. [S4, N4, C2]

## 5. Native phase implementations and client rendering

The precise manager name here is **`EnderDragonPhaseManager`**, not `DragonPhaseManager`.
It has a private per-dragon `DragonPhaseInstance[] phases` cache and private `currentPhase`,
and public `setPhase`, `getPhase` and `getCurrentPhase`. `getPhase` lazily constructs/caches
instances. The phase-type constructor and global `create(Class, name)` registration helper are
private. There is **no public manager setter accepting a supplied instance**. [N1, N5]

`DragonPhaseInstance` exposes `doServerTick(ServerLevel)`, `doClientTick`, `begin`, `end`,
`getFlyTargetLocation`, `getFlySpeed`, `getTurnSpeed`, `isSitting`, damage transformation and
crystal-destruction callbacks. `AbstractDragonPhaseInstance` supplies no-op/default hooks.
The dragon ticks the current server instance, ticks a changed instance again in the same
`aiStep`, and consumes its target/speeds for native flight unless HOVERING. [N2, N6]

This is a genuine AI-control seam, but **injection is unsupported/version-specific**. Reflection
into the manager cache/current instance or a server patch is required; merely defining a
`DragonPhaseInstance` class does not install it. Replacing a cache entry while that entry is
already current also does not automatically replace `currentPhase`, and a same-phase
`setPhase` can short-circuit. Respect lifecycle, client ID and cleanup explicitly. [N1]

**Do not add phase ID 11 or modify the global registry as the default solution.** Bukkit indexes
an eleven-element enum by native ID; a stock client's own phase registry does not acquire your
plugin's registration and falls back to HOLDING_PATTERN on an unknown ID. The manager cache
also captures the registry size when each dragon is constructed. [S1, N1, N5, C3]

The official client's renderer takes its phase from the **client's native manager**, copies
client flight history, and uses interpolated flap state. Its body yaw comes from historical
sample 7; body tilt from the Y difference between samples 5 and 10 multiplied by 10 degrees.
Neck/tail model animation uses history too. `EnderDragonRenderState.getHeadPartYOffset`
branches on `isSitting`. The client's stock HOVER implementation reports sitting. [C1]

Therefore setting parent Bukkit pitch is not equivalent to commanding a rendered dive pose;
position/yaw history and the client's phase semantics matter. A custom **server-only** HOVER
instance reporting `isSitting=false` does not change the client's stock HOVER sitting semantics.
Likewise sending an existing airborne phase ID does not send custom Java behavior or a new
animation. Ordinary position packets are interpolated by the client; teleport packets are
handled by its position-packet path. “Every teleport looks smooth” or “teleports are always
ignored” are not supported conclusions. [C1, C2, C3]

## 6. What could explain the report?

### Playtest evidence and attack eligibility

The user confirmed that the new JAR was installed, the server fully restarted, and **neither the
new dive strip/arrows nor the ground-smash warning appeared** after clearing the opening crystals.
This favors rejection before attack initiation over failed movement after initiation, assuming the
deployed artifact/configuration match the inspected code. It does not establish the exact rejected
condition, and lack of a noticed visual alone is not a server-side trace.

Two concrete restrictions in the current implementation are particularly important:

- `warnLane` creates its rectangle at the player's exact position/height, without ground projection.
  `EncounterArena.laneSafe` samples a default **24×6-block rectangle at one fixed Y**, using 25×7
  sample points. Each sample checks nine body-width offsets for solid footing and two empty body
  blocks. A raised/lowered terrain block or a player high enough above the ground can reject both
  cardinal orientations **before `dive` announces or moves the dragon**.
- `smash` resolves the center to nearby footing but still requires an entire default **radius-eight
  disk at one fixed Y** to pass `areaSafe`, plus a straight walking escape route. Ordinary uneven End
  terrain, nearby native breath, other hazards or arrival/arena exclusions can reject it **before
  `smash-warning`**. Ground projection at the center does not make the disk terrain-following.

These are source-established restrictions, not proof of what was beneath the playtest players.
The competing cadence callbacks can also discard an opportunity during Prey, flight or a landing;
there is no owed-attack queue ensuring a skipped smash runs at the next safe interval. [R2]

The new movement tests run on a deliberately flat 81×81 end-stone plane. Their fixture defers
Prey/sweeps to 120 seconds; the dedicated smash test also defers lanes. They demonstrate isolated
movement but **not reliable selection on a generated island with the complete default schedule**.
Three existing geometry/dive/smash tests were rerun successfully during this research; this is
not a live reproduction of missing special attacks. [R4]

Priority: instrument started/deferred/rejected decisions, exercise real island terrain, replace
fixed-height footprint validation with bounded terrain-aware placement, and retain owed attack
intent through safe scheduling deferrals. Changing the flight AI alone cannot make a rejected
attack reach its warning or movement code.

**Verified repository facts, not a live diagnosis:** Hunt scheduling starts after the last owned
Anchor is cleared through the authorized player/objective path. `warnLane`, `warnSweep`,
`markPrey` and `requestLanding` serialize against flight, anchors, landing state, existing hazards,
participation and conservative terrain/escape checks. Dive/smash have further route/height and
safety checks. Warnings can be skipped without ever calling `DragonFlight`. [R2]

**Hypotheses to discriminate, in order:**

1. Hunt never started, movement attacks disabled in the active snapshotted configuration, or the
   running jar differs from the inspected code.
2. Due attacks are repeatedly rejected by participation, terrain/route safety or landing/hazard
   serialization. Fixed independent cadence opportunities are not guaranteed attack starts.
3. A flight starts but its first movement tick closes because HOVER was refused/changed,
   participation/indicator validity changed, or teleport was cancelled.
4. Server position moves but client motion/pose or multipart reach is visually/mechanically poor.

The AI architecture cannot fix hypotheses 1–2 by itself. Record decision reason, actual phase,
AI flag, attack lifecycle, parent/part positions and teleport result on the real build. This note
does not assert which hypothesis occurred. MockBukkit does not run/render the native dragon AI;
existing encounter docs already acknowledge this evidence gap. [R1, R2, R3]

## 7. Recommended architecture

**Recommendation, not an already validated implementation:** keep the battle-associated native
dragon and AI enabled; separate encounter policy from one version-pinned flight driver.

1. **Encounter policy owns intent.** A single attack coordinator selects a due safe attack with
   explicit bounded deferral/skip reasons; a flight lease serializes scripted flight, landing and
   cleanup. Existing warnings, safety geometry and controlled damage remain encounter concerns.
2. **Start with native waypoint flight where possible.** An installed custom phase supplies
   targets and turn/fly parameters and runs during the real dragon tick. Use an existing compatible
   airborne native phase identity so the stock client sees airborne semantics. Audit all concrete
   casts and phase-specific callbacks before substituting a vanilla cache slot; prefer a compatible
   subclass/delegating implementation rather than assuming generics make any instance safe.
   Do not use HOVERING identity with target-only steering. Native `getFlySpeed` is a steering
   parameter, not a promise of an exact blocks/second trajectory. [N1, N2, N6]
3. **For exact timed choreography, own movement inside the dragon tick.** A custom installed phase
   can directly apply bounded position/motion/rotation updates in `doServerTick` and return a null
   target (or use HOVER identity) to avoid a second native steering move. The rest of `aiStep`
   then positions the parts. Prefer normal movement/tracking over 20 generic teleports/second;
   confirm synchronization on real clients. HOVER is the simpler suppression option but retains
   the documented stock-client sitting-pose tradeoff. A null-target airborne identity is an
   option to prototype, not a supported drop-in API guarantee. [N2, N4, C1]
4. **Make version/lifecycle failure explicit.** Isolate private-manager access, assert the exact
   expected shape before mutation, save original instances/phase/AI state, avoid global registry
   changes, and restore only the instance this driver still owns. Respect permitted DYING,
   teardown during callbacks, unload/reload and abnormal restart. A cancelled phase change has
   already invoked `end`; arrange release/installation accordingly. Fall back visibly or disable
   movement attacks on an incompatible server; never silently leave the dragon frozen. [N1, S5]
5. **Prove both mechanics and presentation.** Test the native boundary against the pinned jar,
   then playtest a real battle on Paper 26.2 build 132 with two stock 26.2 clients: approach,
   warning, dive/smash, recovery, parent/part alignment, head/body sword reach, yaw/dive posture,
   rejected phase/teleport events, native contact/terrain suppression, death rewards and teardown.
   Record measured paths, outcomes and server/client versions before describing this as robust.

A smaller first experiment is AI-enabled HOVER plus bounded native position changes instead of
teleport, with recorded parent/part and two-client rendering evidence. That can establish whether
the current movement transport is the problem without prematurely replacing the entire phase
machine. It still cannot explain skipped attack-selection gates without instrumentation.

## Primary sources and reproducible inspections

All Paper links below are pinned to the full build commit, not `main`.

- **A1:** [Paper API `EnderDragon.java`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-api/src/main/java/org/bukkit/entity/EnderDragon.java); also exact local API source jar, same entry.
- **A2:** [Paper API `EnderDragonChangePhaseEvent.java`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-api/src/main/java/org/bukkit/event/entity/EnderDragonChangePhaseEvent.java).
- **A3:** [Paper API `LivingEntity.java`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-api/src/main/java/org/bukkit/entity/LivingEntity.java), `setAI`/`hasAI` documentation.
- **S1:** [Paper `CraftEnderDragon.java`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEnderDragon.java).
- **S2:** [Paper `CraftLivingEntity.java`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftLivingEntity.java), `setAI`.
- **S3:** [Paper `EnderDragon.java.patch`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-server/patches/sources/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java.patch), hunk at vanilla line 212, plus terrain/damage/death patches.
- **S4:** [Paper `CraftEntity.java`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java), `teleport0`; [native Entity patch](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-server/patches/sources/net/minecraft/world/entity/Entity.java.patch), teleport event/transition hunks.
- **S5:** [Paper `EnderDragonPhaseManager.java.patch`](https://github.com/PaperMC/Paper/blob/19ebc4a49b725b1ea813f95ac559a2ed3931ee4c/paper-server/patches/sources/net/minecraft/world/entity/boss/enderdragon/phases/EnderDragonPhaseManager.java.patch), event after `end`.
- **N1:** Exact mapped server, `net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager`: fields, constructor, `setPhase` bytecode 0–179, `getPhase` 0–33.
- **N2:** Exact mapped server, `net.minecraft.world.entity.boss.enderdragon.EnderDragon.aiStep`: no-AI 398–412 → return 1952; history 415–427; phase server ticks 450–491; HOVER exclusion 510–520; native movement through 984; child positioning 1023–1741; battle update 1816–1828.
- **N3:** Exact mapped server, `net.minecraft.world.entity.boss.enderdragon.phases.DragonHoverPhase`: `doServerTick`, `begin`, `isSitting`, `getFlySpeed`, `getFlyTargetLocation`.
- **N4:** Exact mapped server, `net.minecraft.world.entity.Entity.teleport(TeleportTransition)`, `teleportSameDimension`, `teleportSetPosition`; `CraftEnderDragon`, `Mob`, `LivingEntity` method declarations inspected for teleport overrides.
- **N5:** Exact mapped server, `net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase`: private constructor/create, ID count and `getById`.
- **R4:** Repository `src/test/java/de/kreiscraft/modules/shatteredcrown/HuntEncounterTest.java`, `setUp` and movement scenarios; local rerun: `JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 ./gradlew test --tests '*HuntEncounterTest.clearGroundInsideTheLaneStillNeedsAReachablePlayerSizedEscapeOutsideIt' --tests '*HuntEncounterTest.diveMovesAlongWarningAndHitsOnlyWhenItPassesThenRestoresFlight' --tests '*HuntEncounterTest.smashWarnsRisesImpactsAndLeavesAGroundedRecovery' --console=plain` — successful, three tests.
- **N6:** Exact mapped server, `net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance` and `AbstractDragonPhaseInstance`.
- **C1:** Exact official Mojang client above: `net.minecraft.client.renderer.entity.EnderDragonRenderer.submit` bytecode 4–65 and `extractRenderState` (phase/sitting/history); `net.minecraft.client.model.monster.dragon.EnderDragonModel.setupAnim`; `net.minecraft.client.renderer.entity.state.EnderDragonRenderState.getHeadPartYOffset`; client `DragonHoverPhase.isSitting`.
- **C2:** Same client, `net.minecraft.client.multiplayer.ClientPacketListener.handleTeleportEntity`, `setValuesFromPositionPacket`, `handleMoveEntity`; client `EnderDragon.aiStep` interpolation path.
- **C3:** Same client, `EnderDragon.onSyncedDataUpdated` and `phases.EnderDragonPhase.getById`, client phase-ID consumption and out-of-range fallback.
- **R1:** Repository [`DragonFlight.java`](../../src/main/java/de/kreiscraft/modules/shatteredcrown/DragonFlight.java), `start` and `close` as inspected on research date.
- **R2:** Repository [`EncounterController.java`](../../src/main/java/de/kreiscraft/modules/shatteredcrown/EncounterController.java), `startHunt`, `warnLane`, `warnSweep`, `markPrey`, `dive`, `smash`, `onDamage` objective-clear path, `onDragonPhase`.
- **R3:** Repository [`shattered-crown-hunt.md`](../shattered-crown-hunt.md), “Historical automated evidence and real-Paper gaps”. These repository links describe mutable working-tree code, not a pinned upstream release.

Reproduce native inspections without decompiling or executing Minecraft:

```sh
javap -classpath .gradle/caches/paperweight/taskCache/mappedServerJar.jar -c -p \
  net.minecraft.world.entity.boss.enderdragon.EnderDragon
javap -classpath .gradle/caches/paperweight/taskCache/mappedServerJar.jar -c -p \
  net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager
javap -classpath .gradle/caches/paperweight/taskCache/mappedServerJar.jar -c -p \
  net.minecraft.world.entity.Entity
javap -classpath /tmp/opencode/dragon-ai-research/client.jar -c -p \
  net.minecraft.client.renderer.entity.EnderDragonRenderer
```
