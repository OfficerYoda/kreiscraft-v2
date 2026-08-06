# Wiring: spawn

## config.yml addition

```yaml
  spawn:
    enabled: true
    teleport-range: 100
    glide:
      enabled: true
      multiply-value: 3
      spawn-radius: 30
      launch-location: { world: world, x: -35, y: 141, z: -101, yaw: 0, pitch: 0 }
    swap-hand-boost:
      enabled: false
      vector-multiplier: 3.0
    spawn-launch:
      enabled: false
      trigger: { world: world, x: -35, y: 141, z: -101, yaw: 0, pitch: 0 }
      velocity: { x: 0.0, y: 5.0, z: 0.0 }
```

## lang/de.yml addition

```yaml
spawn:
  teleported: "<prefix> <green>Zum Spawn teleportiert."
  out-of-range: "<prefix> <red>Du bist zu weit vom Spawn entfernt."
  no-spawn-set: "<prefix> <red>Kein Spawn gesetzt."
  spawn-set: "<prefix> <green>Spawn gesetzt."
```

## lang/en.yml addition

```yaml
spawn:
  teleported: "<prefix> <green>Teleported to spawn."
  out-of-range: "<prefix> <red>You are too far from spawn."
  no-spawn-set: "<prefix> <red>No spawn has been set."
  spawn-set: "<prefix> <green>Spawn set."
```

## plugin.yml permissions

```yaml
  kreiscraft.spawn.use:
    description: Teleport to spawn
    default: true
  kreiscraft.spawn.set:
    description: Set the server spawn point
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.spawn.SpawnModule());

## Shared-file changes required outside your module

### VectorSerializer for org.bukkit.util.Vector (optional, low priority)

The brief specifies `org.bukkit.util.Vector` for `SpawnLaunchConfig.velocity`, but no
`TypeSerializer<Vector>` exists in `KreiscraftSerializers`. To avoid this gap, the
implementation uses a module-local `VelocityRecord @ConfigSerializable` record in place
of `Vector`. The YAML format (`{ x: 0.0, y: 5.0, z: 0.0 }`) is identical to what a
Vector serializer would produce.

If Batch C or a future reviewer wants to unify this, adding:

```java
.register(org.bukkit.util.Vector.class, VectorSerializer.INSTANCE)
```

to `KreiscraftSerializers.builder()` (with a corresponding `VectorSerializer` in
`core/config/serializers/`) would allow `SpawnLaunchConfig` to use `Vector` directly.
This is not strictly required for module correctness.

### LocationSerializer null-guard (informational)

`core/config/serializers/LocationSerializer` throws `SerializationException("expected
location map")` when the node is null (explicit YAML `~`), and throws NPE via
`map.get("x").doubleValue()` when the node is virtual (key absent). This makes nullable
`Location` config fields unworkable. The spawn module works around this by using
`LocationSpec` (a `@ConfigSerializable` record) for optional location fields. Future
modules with nullable location config fields will need the same workaround or a fix to
`LocationSerializer.deserialize()` to handle virtual/null nodes gracefully.
