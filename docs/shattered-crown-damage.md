---
title: Shattered Crown damage calibration
---

# Reference-health damage

`bombardment-damage: 6` means **three hearts lost** by a clean hit on full Protection IV
netherite, without Resistance or absorption. Both automatic Bombardment and
`/dragondebug trigger bombardment` use `DragonBattleAccess.referenceDamage` once per eligible
recipient, after the existing fixed warning/footprint and impact-time eligibility checks.
No terrain destruction was added. The existing validated field (finite 0–100), atomic edits,
reload rejection and per-Encounter configuration snapshot remain in use.

On supported Paper **26.2 build 132**, attributed `minecraft:dragon_breath` bypasses armor,
but **not** enchantments, Resistance, invulnerability or cooldown. It scales with world
difficulty before enchantments. Four Protection IV pieces have EPF 16, giving **0.36**
remaining damage. The source retains the dragon as both direct and causing entity, even
when that dragon is invulnerable during an Anchor wave. Paper owns the public cancellable
combat event and all actual health/absorption changes; there is no direct health subtraction,
extra damage call, forced cooldown reset or per-player gear compensation in production.

For a reference-health target `H`, Normal damage before Protection is `N = H / 0.36`.
The raw Bukkit argument is:

| Difficulty | Raw argument | Paper scaling |
| --- | --- | --- |
| Easy | `N` when `N <= 2`, otherwise `2 * (N - 1)` | `min(raw / 2 + 1, raw)` |
| Normal | `N` | unchanged |
| Hard | `N / 1.5` | `raw * 1.5` |
| Peaceful | no damage call | preserves native no-damage behavior |

Zero also makes no damage call. Less Protection increases damage; Resistance and absorption
still reduce health loss. This is a fixed reference calibration, **not** constant damage
regardless of defenses. Existing configurations retain their numeric value but Bombardment's
unit now means reference health loss; `6` previously lost only about `2.16` health on Normal.

## Boundary for subsequent attacks

Use `referenceDamage` for the approved single-hit targets: **Dive 8**, **Ground Smash 12**,
**Fireball Rain impact 4** reference health points. This ticket establishes and tests their
units; it does not change those attack families' existing implementation or configure their
future defaults. The legacy `bombardmentDamage` operation remains **raw native dragon-breath
damage**, including existing lane/pool/sweep calls. Pool damage keeps its separately configured
raw units and cadence; do not pass pool damage through the reference-health converter.

## Runnable supported-Paper regression

`DragonDamageProbe` is a separate disposable-server plugin; never install it in production.
Build with Java 25:

```sh
./gradlew check dragonDamageProbeJar --max-workers=2 --console=plain
```

Copy `build/paper-probe/dragon-damage-probe-0.1.0-SNAPSHOT.jar` to an **isolated disposable**
Paper 26.2 build 132 server's `plugins/`, accept its EULA, use a fresh world and a loopback-only
unused port, and run:

```sh
java -Xms256M -Xmx768M -Dkreiscraft.damage-probe=true -jar paper.jar --nogui > damage.log 2>&1
grep 'DRAGON_DAMAGE_PROBE PASS' damage.log
```

The server shuts down itself. **Require PASS and inspect any FAIL/error lines**; server exit
status alone is not the probe's result. The probe constructs a real `ServerPlayer`, native
equipment and loaded-state acknowledgement in a real server, then calls the production
Bukkit damage boundary. Only network/client transport is absent; damage and health are real.
It checks live damage tags, actual health deltas for 6/8/12/4 on Easy/Normal/Hard, low-damage
Easy crossover, one combat event per clean hit, dragon attribution, shielded source,
Resistance, absorption, public cancellation, player invulnerability, repeated-hit cooldown,
unenchanted armor/unarmored controls, zero/creative/spectator targets, unchanged raw pool
units and Peaceful behavior.

Observed 2026-11-10: reference losses **6/8/12/4 exactly** on all three combat difficulties;
Resistance I **4.799999237**, four absorption points **2 health lost / 0 absorption left**,
same-tick repeated hits **6 total**, cancelled/invulnerable hits **0**, raw pool input 4
**1.440000534**, unenchanted netherite and unarmored controls **16.666666031** for target 6.
Tolerance is 0.001 health points (native float arithmetic).

Encounter tests separately cover automatic/debug dispatch, frozen warning/impact, protected
arrivals/invalid targets, cancellation, cleanup and reload snapshots. Their simulated damage
is **not native armor evidence**. Integrated automatic/debug Bombardment on actual connected
players, final quieter UI/rendering and representative multiplayer acceptance remain for
**#163**; the clientless damage probe does not claim those observations.
