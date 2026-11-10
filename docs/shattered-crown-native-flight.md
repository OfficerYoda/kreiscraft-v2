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
