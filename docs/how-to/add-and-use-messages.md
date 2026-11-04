---
title: How to add and use messages
---

All user-facing strings go through `MessageService` (MiniMessage-based i18n).

---

## Steps

### 1. Add keys to both lang files

Keys must be **identical** in both `src/main/resources/lang/de.yml` and `lang/en.yml`.
`LangParityTest` fails the build if the sets diverge.

Naming convention: `<module-id>.<action>` (dot-separated, kebab-case segments).

```yaml
# lang/en.yml
my-feature:
  action-done: "<prefix> <green>Done: <result>"
  not-found: "<prefix> <red>Could not find <name>.</red>"
```

```yaml
# lang/de.yml  — same keys, translated values
my-feature:
  action-done: "<prefix> <green>Fertig: <result>"
  not-found: "<prefix> <red><name> wurde nicht gefunden.</red>"
```

`<prefix>` is a built-in placeholder resolved to the value of the `prefix` key at the
root of the active lang file (e.g. `<gray>[<gold>Kreiscraft</gold>]</gray>`). You do not
need to pass it manually.

### 2. Use MiniMessage tags in the string value

Standard MiniMessage formatting tags work: `<green>`, `<red>`, `<gradient:#aaa:#bbb>`,
`<click:...>`, etc. User-supplied runtime values are inserted via named placeholders:
`<result>`, `<name>`, etc. — these are resolved at render time from the varargs you pass
(see step 3).

### 3. Send a message to a `CommandSender`

```java
// No runtime placeholders:
ctx.messages().send(sender, "my-feature.not-found");

// With runtime placeholders (key/value pairs, varargs):
ctx.messages().send(sender, "my-feature.action-done", "result", "some value");
ctx.messages().send(sender, "my-feature.not-found", "name", playerName);
```

Signature: `void send(CommandSender sender, String key, Object... placeholders)`

`send` renders in the recipient's persisted language when one is stored in `data/settings.yml`,
otherwise in the server language (`core.language`). An invalid stored language warns once and falls
back to the server language.

Placeholders are interleaved `name, value` pairs. Every `<name>` tag in the message
string is replaced with the corresponding `value`. Values are converted with
`String.valueOf()`.

### 4. Render as `Component` (without sending)

```java
// Server language — use for console output or when no recipient is available:
Component c = ctx.messages().render("my-feature.action-done", "result", "42");

// Recipient language — use whenever the component is shown to a specific player
// (GUI titles and labels, action bars, titles, kick messages, boss bars):
Component forViewer =
    ctx.messages().renderFor(viewer, "my-feature.action-done", "result", "42");
```

Signatures: `Component render(String key, Object... placeholders)` and
`Component renderFor(CommandSender recipient, String key, Object... placeholders)`.

### 5. Broadcast to all online players

```java
ctx.messages().broadcast("core.startup.summary",
    "enabled", 5, "disabled", 2, "failed", 0);
```

`broadcast` renders separately per online player so each receives their own language; the console
receives the server-language rendering.

### 6. Built-in `%token%` placeholders (`PlaceholderResolver`)

These are **not** MiniMessage tags. They use the `%token%` pattern and are resolved by
`PlaceholderResolver` for raw string templates (e.g. tablist header/footer strings). They
are **not** available inside the lang YAML files via `MessageService`.

| Token | Value |
|---|---|
| `%tps%` | TPS (first sample), capped at 20.0 with two decimals |
| `%mspt%` | Average server tick time in milliseconds with two decimals |
| `%server_players%` | Online player count |
| `%system_time%` | Current server time `HH:mm:ss` |
| `%player_name%` | Player's display name (requires `Player` context) |
| `%ping%` | Player's ping in ms (requires `Player` context) |
| `%playtime%` | Player's playtime as `HH:MM:SS` (requires `Player` context) |

Access via `ctx.services().get(PlaceholderResolver.class).resolveString(template, player)`.

### 7. Run tests

```
./gradlew test
```

`LangParityTest` — both lang files must have identical key sets.  
`MessageKeyReferenceTest` — keys referenced in code must exist in the lang files.
