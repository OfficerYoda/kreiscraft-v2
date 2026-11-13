---
title: Shattered Crown Bombardment
---

# Bombardment (merged Fireball Rain)

Enable **both** `modules.shattered-crown.enabled` and `modules.wither.enabled` before startup.
The existing enhanced Wither module remains authoritative; the native runner stages both together.

After **all four wave-two crystals** are destroyed, Bombardment joins the shuffled rounds alongside
Prey and Dive. There is no separate Fireball Rain attack. Later shields do not relock it. Every online, living Survival/Adventure
player in the radius-192/full-height combat area outside protected arrival gets a projectile.
Scaling observers and Prey's percentage are irrelevant to Rain.

At **t0**, each ground position below the selected player is locked and warned with a fixed
**default radius-four** flame ring matching the eventual damage pool, growl and descending flame/purple-dust trail.
There is **no visible countdown**. The default warning lasts three seconds (or until collision/cancellation); moving players do not move
it. At **t1**, a real ghast `LargeFireball` launches twenty blocks above that ground. Native air
drag is balanced against acceleration for half a block per tick, about two seconds of descent.
Ground projection searches the full column below airborne players. A void column still gets a
locked floating warning/projectile at the captured feet position and bounded no-impact cleanup;
it is not silently excluded from targeting. There is no per-tick teleport, steering or homing. Native `Player.attack` deflection is cancelled
before velocity/owner changes. Terrain, cover and player collisions use the real native hit position;
earlier obstruction resolves immediately, not by detonating at the original target later.

Each actual collision resolves once. Native entity damage and explosion priming are suppressed;
the only impact hit is the existing cancellable, dragon-attributed reference-damage path:
**6 HP / three hearts** against full Protection IV netherite, no Resistance/absorption, across
Easy/Normal/Hard. The inclusive default radius-four impact uses a three-block vertical tolerance. Players
can dodge or use overhead cover. Arrival player protection and defended Anchor gates remain intact.

The actual impact projects onto nearby footing and calls `impactTerrain(center, bombardmentRadius, 1)` for a
permanent shallow crater and harmless, bounded owned debris. There is one small explosion particle
and sound cue, **no native explosion, fire, block restoration or terrain-preservation zones**.
Back up the End before enabling destructive encounters. Unsupported/void impact geometry may
skip deposition, but cannot keep the action busy.

After the crater, footing/surface geometry is recomputed. `depositBreathPool` creates the same
production Prey-style **matching-radius / six-second** pool, without the retired walking-route veto.
Damage retains raw `pool-damage` semantics, not reference-health-point calibration. Across all
Prey/Rain pools a player can receive at most one configured pulse per second (or the configured
cadence when slower), including staggered overlaps. Pool centers may lie in arrival terrain;
arrival **players** remain excluded independently. Each pool owns its rendering, damage and
exclusive expiry independently. Native flight-contact suppression does not cancel these custom
breath pulses while a later Dive/Smash movement owns the main slot.

The main action finishes after its **last projectile resolves or is cleaned up**. Removed entities
are detected on the next tick; a stopped/lost projectile has a bounded timeout (six seconds from t0
at default tuning). Pools/debris and any remaining three-second warning are sibling owned work,
not main-completion dependencies. Pools can span the next default 2–4-second gap/attack or melee landing.
Cancel, pause, wave replacement, reset, victory, disable and world unload close the applicable
ownership tree; stale tagged projectiles/displays/debris are recovered on startup/entity load.
Committed craters and unrelated entities are retained.

## Operator controls and next-attempt configuration

`/dragondebug trigger bombardment [player]` uses the existing OP-default
`kreiscraft.shatteredcrown.debug` permission. Without a name it selects all eligible combat players;
with a name it selects only that eligible player. It enters manual mode and cancels prior hazards,
like existing triggers. Replies are localized English/German. Automatic cues never use chat,
action bars or titles. `status` exposes `BOMBARDMENT`, available/unlocked families, main/hazard state;
`cancel` drains temporary work without rebuilding ground, and `resume` resumes the production rotation.

Under `modules.shattered-crown`, finite values are validated atomically in YAML/reload and the
English/German Server Configuration GUI. They are snapshotted for the **next attempt**, not a live
projectile: `bombardment-spawn-height` defaults20 (16–24 blocks), `bombardment-descent-seconds` defaults2 (1.5–2.5s),
`bombardment-warning-seconds` defaults3 (1–15s), `bombardment-damage` defaults6 (0–100 reference HP),
and `bombardment-radius` defaults4 (1–12 blocks). The retired `rain-*` settings no longer control attacks.
Height/descent are calibration knobs; native speed is height/(20×descent), with descent capped at the
warning duration. Launch delay is warning minus descent, default one second. Crater depth1 and pool6s
are fixed mechanics. Warning, immediate damage, crater and pool radii share the Bombardment radius.
Existing pool damage/cadence and debris lifetime settings apply.

## Executable verification

Controlled Encounter/public-event tests cover lock, all-target vs named selection, once-only impact,
terrain/pools/main completion, invalid tuning and next-attempt reload, native-hit suppression,
cancellation, stale recovery and before/after-impact termination. `rainProbeJar` is a separate,
version-pinned Paper probe beside the actual production shadow JAR, never bundled into production:

```sh
./gradlew check build rainProbeJar --max-workers=2
JAVA_HOME=... python3 scripts/run-dragon-wave-probe.py NEW_EVIDENCE_DIR PAPER132_CACHE PRODUCTION_JAR rain
```

It uses genuine PlayerList-registered ServerPlayers, natural managed DragonBattle, native collision
events/positions/velocity/health, real ground removal and shared pool expiry/rates, plugin disable
and a second JVM reopening the saved world. Synthetic packet sinks prove native conversion/emission,
**not authenticated client rendering/readability**. Full fight endurance, all roster interactions,
natural rewards and human multiplayer balance/presentation remain #163. #160 was accepted independently
on its pinned earlier artifact; Rain's paired-module/projectile checks are separate.
