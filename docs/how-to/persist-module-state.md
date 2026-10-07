---
title: How to persist module state with DataFile
---

`DataFile<T>` wraps a YAML file with dirty-tracking and integrates with the auto-save loop
(`core.auto-save-interval`, default `5m`).

---

## Steps

### 1. Create a class extending `DataFile<T>`

`T` is the in-memory state type (a record, POJO, or `AtomicReference<X>`).

```java
package de.kreiscraft.modules.myfeature;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class MyFeatureData extends DataFile<MyFeatureState> {

    public MyFeatureData(Path dataFolder) {
        // dataFolder is ctx.plugin().getDataFolder().toPath()
        // Second arg is the path relative to the plugin data folder.
        super(dataFolder, "data/my-feature.yml");
    }

    @Override
    protected MyFeatureState empty() {
        return new MyFeatureState(/* defaults */);
    }

    @Override
    protected MyFeatureState load(ConfigurationNode node) throws SerializationException {
        return node.get(MyFeatureState.class);
    }

    @Override
    protected void save(MyFeatureState state, ConfigurationNode node) throws SerializationException {
        node.set(MyFeatureState.class, state);
    }
}
```

The state type must be `@ConfigSerializable` for the `node.get()`/`node.set()` calls above
to work with Configurate. Example:

```java
@ConfigSerializable
public record MyFeatureState(String lastValue, int counter) {}
```

See `JailData.java` and `HackLogData.java` for real examples that store record and collection
state via explicit node paths rather than a single top-level record.

> **Operator-owned state is not a `DataFile`.** Values that represent operator choices — the spawn
> location, update-mode state, start-sequence completion — live in writable `config.yml` and are
> written through `ConfigService` (see ADR-10). Use `DataFile` only for player and gameplay
> Activity Data such as jail records, saved positions, Bedrock identities, and anti-cheat logs.

### 2. Load the file in `enable()`

```java
@Override
public void enable(ModuleContext ctx) {
    MyFeatureData data = new MyFeatureData(ctx.plugin().getDataFolder().toPath());
    try {
        data.load();          // reads file or calls empty() if the file doesn't exist yet
    } catch (IOException e) {
        throw new ModuleException("failed to load my-feature data", e);
    }

    // Register with ConfigService so AutoSaveTask flushes it periodically.
    ctx.services().get(ConfigService.class).register(data);

    // Pass data to services/listeners that need it.
    ctx.services().publish(MyFeatureService.class, new DefaultMyFeatureService(data));
}
```

### 3. Read state

```java
MyFeatureState state = data.get();
```

### 4. Mutate state

```java
data.update(s -> s.setCounter(s.counter() + 1));
```

`update()` calls `mutator.accept(state)` and marks the file dirty. The next
`AutoSaveTask` tick calls `flushIfDirty()`, which writes YAML only when dirty.

### 5. Auto-save

No action required. `AutoSaveTask` runs on the interval set by `core.auto-save-interval`
and calls `flushIfDirty()` on every registered `DataFile`.

### 6. Immediate flush (optional)

Call `data.flushIfDirty()` in `disable()` only if you want to guarantee the file is
written before the plugin unloads, rather than waiting for the next auto-save tick.
For most modules this is not necessary — `AutoSaveTask` is stopped before modules are
disabled.

---

## Summary

| Operation | Call |
|---|---|
| Declare | `extends DataFile<T>`, implement `empty()`, `load(node)`, `save(state, node)` |
| Load on startup | `data.load()` (throws `IOException`) |
| Register for auto-save | `ctx.services().get(ConfigService.class).register(data)` |
| Read | `data.get()` |
| Write (dirty-marks) | `data.update(state -> ...)` |
| Force flush | `data.flushIfDirty()` |
