# Kreiscraft Documentation

Documentation for the Kreiscraft Paper plugin — a single-JAR replacement for the previous standalone plugins, maintained by the two developer-admins of kreiscraft.de.

Organised according to the [Diátaxis](https://diataxis.fr/) framework. Pick the section that matches what you need to do.

---

## Tutorials — learning by doing

Start here if you are setting up the project for the first time or building something new.

| Document | What you will do |
|----------|-----------------|
| [Getting Started](tutorials/getting-started.md) | Clone → build → run a local Paper server and verify all 25 modules load |
| [Your First Module](tutorials/your-first-module.md) | Build a minimal end-to-end module with config, listener, and lang keys |
| [Writing a Command](tutorials/writing-a-command.md) | Build a Brigadier command using `CommandRegistrar`, step by step |

---

## How-to Guides — solving specific problems

Terse recipes. You know what you want to do; these tell you how.

| Document | Problem it solves |
|----------|-------------------|
| [Add a New Module](how-to/add-a-new-module.md) | Checklist: class → config → lang → register → permissions |
| [Enable or Disable a Module](how-to/enable-or-disable-a-module.md) | Flip `modules.<id>.enabled` in config; understand when a restart is required |
| [Persist Module State](how-to/persist-module-state.md) | Use `DataFile<T>` for dirty-tracked, auto-saved YAML state |
| [Add and Use Messages](how-to/add-and-use-messages.md) | Add lang keys, send MiniMessage text, use placeholders |
| [Reload Configuration](how-to/reload-configuration.md) | Use `/kreiscraft reload`; implement `onConfigReload` with `AtomicReference` |
| [Configure the HTTP API](how-to/configure-the-http-api.md) | Enable the `api` module, set port/CORS, understand the linking flow |
| [Write Tests](how-to/write-tests.md) | Unit tests, MockBukkit listener tests, Javalin route tests, lang parity |

---

## Reference — look things up

Precise, complete descriptions of the system's components.

| Document | What it covers |
|----------|---------------|
| [Module Catalog](reference/module-catalog.md) | All 25 modules: ID, commands, config keys, services published, events fired |
| [Commands and Permissions](reference/commands-and-permissions.md) | Every command, alias, permission node, and default |
| [Configuration](reference/configuration.md) | Every `config.yml` key — core and per-module — with types and defaults |
| [HTTP and WebSocket API](reference/http-api.md) | All 8 HTTP endpoints, the `/chat` WebSocket protocol, the account-linking flow |
| [SDK Reference](reference/sdk.md) | `Module`, `ModuleContext`, `ServiceRegistry`, `EventBus`, `CommandRegistrar`, `Scheduler`, `MessageService`, `DataFile`, and more |
| [Module Template](reference/module-template.md) | Copy-paste scaffold: module class, config, data file, listener, command, snippets for config/plugin.yml/lang |

---

## Explanation — understanding why

Background reading. Useful when something feels surprising or when you need to make a design-level decision.

| Document | What it explains |
|----------|-----------------|
| [Architecture Overview](explanation/architecture.md) | Layer model, composition root, module lifecycle, topological sort, `ChatBridge`, hot-reload flow |
| [Design Decisions](explanation/design-decisions.md) | Eight ADRs covering: single Gradle project, no DI framework, Brigadier commands, embedded Javalin, `DataFile` vs config, live config propagation, Java 25 + `-Werror`, `ChatBridge` decoupling |

---

## Quick links

- Main README: [`../README.md`](../README.md)
- Source entry point: `src/main/java/de/kreiscraft/Kreiscraft.java`
- Composition root: `src/main/java/de/kreiscraft/KreiscraftBootstrap.java`
- Default config: `src/main/resources/config.yml`
- Default lang: `src/main/resources/lang/de.yml`
