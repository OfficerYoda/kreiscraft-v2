---
title: Shattered Crown native flight
---

# Native flight and attack selection

The November 2026 follow-up replaces the generic teleport/HOVER driver with a per-dragon
`DragonChargePlayerPhase` subclass. `NmsDragonFlight` installs it in the existing charge cache
slot, retaining the stock airborne phase ID. Its callback advances choreography inside
`EnderDragon.aiStep`, calls ordinary native movement, and returns a null fly target to prevent a
second steering pass. The rest of the tick updates parts and the battle normally.

Private phase-manager access is isolated in `platform/NmsDragonFlight.java` and checked before
mutation. The original charge instance is restored on close, including same-phase requests and
cancelled restoration events. A native DYING phase or later owner is not replaced. A watchdog closes
the encounter's flight scope if participation, its warning, or native phase ownership is lost.
The global phase registry and AI flag are untouched. See the [primary-source research](research/dragon-ai-control.md).

Dive retains the native eight-block step ceiling across the expanded arena. If the current
dragon-to-lane distance cannot fit within the configured warning ticks, it first approaches at
at most 7.5 blocks per native tick, then begins the full visible warning. Both stages remain
owned/cancellable; lost targets, changed ground or replaced phases release the main action.

## Selection and terrain

`HuntRotation` maintains one due timestamp per family, retries failed placement after one second,
and selects the oldest eligible due family. Busy attacks retain their due timestamp; a failed
candidate cannot monopolize the coordinator. Queue state belongs to one Hunt attack scope and
is discarded at the finale gate, victory or reset. Legacy stationary mode retains its old scheduler.

`AttackSurface` captures nearby supported ground per column, within six vertical blocks of the
projected center. Dive, smash and Prey visuals/hits share those frozen heights. Body-sized escape
checks allow one-block steps and account for uphill time, excluding void, protected arrival and
existing danger. Changed footing invalidates the attack. A generated island need not provide a
perfectly flat 24×6 rectangle or a flat 16-block-wide disk anymore.

## Verification

The later particle fix adds `CrownParticlesTest`, which calls the production renderer through
Paper's real `CraftPlayer.spawnParticle` implementation and captures outgoing particle packets.
The native registry and conversion code are real; only the player handle/network transport is
mocked. It reproduced `missing required data class java.lang.Float` before the Float payload fix.
Encounter fault-injection tests cover pool activation/refresh failures and the next attack starting.
The movement-only disposable probe below did not cover player-facing particle emission.

- Encounter tests cover stepped terrain, jumping targets, void and cloud rejection, owed smash
  after Prey, and repeated dive/Prey/smash/landing participation with the full default timings.
- `NmsDragonFlightTest` uses Paper's real manager/phase implementations, with mocked world/entity
  transport. It verifies installation, airborne identity, native movement invocation, cache
  restoration, same-ID acquisition, cancelled acquisition/release and native DYING retention.
- `DragonFlightProbe` runs the production driver on a **real Paper 26.2 build 135 (`e4d6dfd`) server
  and real dragon**, matching the version from the user's trace, with
  forced-loaded End chunks. The disposable run passed 80 native ticks, position `(24, 102, 4)`,
  moving head hitbox `(17.5, 102, 4)`, zero teleport events, and original phase/cache restoration.
  Raw run log: `/tmp/opencode/dragon-flight-probe-20261110/probe.log` (ephemeral local evidence).

Build verification/probe artifact:

```sh
JAVA_HOME=/home/officeryoda/.local/share/mise/installs/java/25.0.1 \
  ./gradlew spotlessApply check dragonFlightProbeJar --console=plain
```

The probe JAR is under `build/paper-probe/`, separate from `build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar`.
Install it **only in a disposable Paper server**, start that JVM with
`-Dkreiscraft.dragon-probe=true`, and use a separate world directory and loopback-only port.
It creates an End test world, reports `DRAGON_FLIGHT_PROBE PASS` or `FAIL`, and shuts down that JVM.
The test artifact contains the production native driver and does not require loading Kreiscraft.

This is clientless evidence. Stock-client flight animation, rendered warning alignment, melee
reach during the moving attacks, and representative multiplayer balance still need in-game testing.

## Integrated physical Dive probe

Build `./gradlew shadowJar diveEncounterProbeJar --max-workers=2 --console=plain` with Java 25.
Install the production shadow JAR and `build/paper-probe/dive-encounter-probe-0.1.0-SNAPSHOT.jar`
only in a disposable Paper 26.2 build 132 server with fresh worlds and a loopback-only port.
Enable Crown alone; defer Prey/sweep/landing cadences to 120 seconds to isolate automatic Dive.
Start with `-Dkreiscraft.dive-probe=true` and require `DIVE_ENCOUNTER_PROBE PASS` in the log.
The probe shuts down the server itself. Never install this test plugin in production.

The fixture registers a real native player using the existing clientless transport recipe,
lets the real main-End battle spawn its dragon, verifies production adoption, and drives
actual `/dragondebug` commands. It checks the three-difficulty Protection IV netherite matrix,
automatic dispatch after the real opening objectives are destroyed, native moving body/low-hit
timing, once-only damage, zero particle packets/dragon teleports, public cancellation, invalid
target cleanup and native AI restoration. A raised isolated pad supplies deterministic footing;
this is not rendered-client, representative terrain or human multiplayer acceptance.
