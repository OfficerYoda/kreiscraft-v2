---
title: Getting Started
---

This tutorial takes you from a clean clone to a running local server with all modules active. By the end you will have verified the plugin loads, seen the startup summary, reloaded the config live, and run the test suite.

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java        | 25      |
| Git         | any     |

No other tools are needed. Gradle and the Paper server are downloaded automatically.

---

## 1. Clone and navigate

```bash
git clone https://forgejo.officeryoda.dev/OfficerYoda/kreiscraft-java.git
cd kreiscraft-java
```

All remaining commands in this tutorial are run from that directory.

---

## 2. Install the Git hook

```bash
./gradlew installGitHooks
```

This copies `scripts/pre-commit` into `.git/hooks/`. The hook runs `spotlessCheck` on every staged `.java` file before a commit is accepted. Installing it now means you will never push code that fails the formatter check.

Expected output:

```
BUILD SUCCESSFUL
```

---

## 3. Build the plugin

```bash
./gradlew build
```

Gradle compiles the sources, runs the test suite, and creates a fat jar via the Shadow plugin. The output artifact is:

```
build/libs/Kreiscraft-0.1.0-SNAPSHOT.jar
```

All third-party libraries (Configurate, Javalin, InvUI, etc.) are relocated into the `de.kreiscraft.libs.*` namespace inside that jar so they cannot conflict with other plugins.

---

## 4. Start a local server

```bash
./gradlew runServer
```

The `run-paper` Gradle plugin downloads a Paper server on first run and places everything under `run/`. Subsequent starts reuse the cached download.

On first run you will see Paper bootstrap output followed by plugin startup. Look for the `[Kreiscraft]` prefix in the log. The last line from the plugin is the startup summary:

```
[Kreiscraft] [INFO] 19 Module aktiv, 5 deaktiviert, 0 fehlgeschlagen.
```

If you have configured the language to `en` (in `config.yml` → `core.language: en`) it reads:

```
[Kreiscraft] [INFO] 19 modules enabled, 5 disabled, 0 failed.
```

The counts come from `StartupSummary` — enabled modules are those with `enabled: true` in `config.yml` under `modules.<id>`. The bundled `config.yml` ships 19 modules enabled; `effects`, `firework`, `glow`, `plugin-hider` and `positions` are disabled by default and only start when explicitly opted in, so a clean run reports 19 enabled and 5 disabled.

If any module fails to start, the log also prints a `SEVERE` line with the module id and exception before the summary. Check that line first when troubleshooting.

The server is ready when you see:

```
Done (Xs)! For help, type "help"
```

---

## 5. Verify the plugin loaded

Beyond the startup summary, you can inspect the server console directly. Type:

```
plugins
```

You should see `Kreiscraft` listed as enabled (green in Paper's colorized output).

---

## 6. Run a live reload

In the server console (or from a Minecraft client connected to `localhost:25565` with op), run:

```
/kreiscraft reload
```

This command requires the `kreiscraft.admin` permission, which ops hold by default. It triggers:

1. `ConfigService.reload()` — re-reads `config.yml` from disk.
2. `MessageService.reload()` — re-reads the active lang file.
3. `ModuleRegistry.reloadConfigs()` — calls `onConfigReload()` on every enabled module.

On success you will see:

```
[Kreiscraft] Konfiguration neu geladen.
```

(or the English variant if `core.language: en`).

---

## 7. Run the tests

Stop the server (`stop` in the console or Ctrl-C), then:

```bash
./gradlew test
```

The test suite uses MockBukkit, JUnit Jupiter, Mockito, and AssertJ. Tests do **not** spin up a real server — MockBukkit provides an in-memory Bukkit environment. A `MockScheduler` test-double stands in for the Bukkit scheduler.

Expected output:

```
BUILD SUCCESSFUL
X tests completed, 0 failed
```

If tests fail, Gradle writes HTML reports to `build/reports/tests/test/index.html`.

---

## What you have now

- A working local dev loop: edit → `./gradlew build` → `./gradlew runServer`.
- A pre-commit hook that prevents formatting regressions.
- A passing test suite as a baseline.

Continue with [Your First Module](/tutorials/your-first-module/) to learn how to add a new feature.
