---
title: Verify the Server Configuration GUI
---

Operator checklist for the Server Configuration editor. These steps require a running server and
cannot be automated against MockBukkit, so run them by hand on a staging server before release.

Prerequisites: an account with `kreiscraft.admin` and `kreiscraft.settingsgui.admin`. Use a server
whose `config.yml` is the bundled copy (or a close derivative) so the presented values are known.

## Language coverage

Run the checklist once with `core.language: en` and once with `core.language: de` (restart to change
the server language). In each pass:

1. Open Player Settings with `/settings`.
2. Confirm every label, category name, close prompt and chat-input prompt appears in the configured
   server language, with no raw key such as `server-config.field...` visible.

## Navigation

1. From the Player Settings root, click the **Server Configuration** entry.
2. Confirm the category list opens and that the categories `core`, `modules`, `admin`, `anti-cheat`,
   `bedrock`, `chat-format`, `combat`, `effects`, `firework`, `jail`, `plugin-hider`, `positions`,
   `reboot`, `spawn`, `start-sequence`, `tablist`, `update-mode` and `wither` are present.
3. Open several categories (including a nested one such as `spawn` and the large `wither` page) and
   confirm the **Back** arrow returns to the category list.
4. From the category list, use **Back** to return to Player Settings.
5. Reopen the editor with `/settings admin` and confirm it opens directly on the category list.

## Typed controls

For each control, make one change and confirm the displayed value updates immediately:

- **Boolean** — toggle a field such as `wither → Spawn explosion fire`; the icon and value flip.
- **Whole number** — step `effects → Effect count` up and down.
- **Decimal number** — step `effects → Effect offset X` up and down.
- **Duration** — cycle `combat → Combat tag duration` and `start-sequence → Border duration`; each
  offers durations scaled to that field.
- **Enum** — cycle `combat → Combat display` and `effects → Default particle`.
- **Location** — stand somewhere distinctive and click `spawn → Spawn location` or `jail → Jail
  location`; the stored position matches where you stood.
- **Text** — click `chat-format → Chat format`, type a MiniMessage format in chat, and confirm it is
  saved; repeat with `cancel` to confirm nothing changes.
- **Text list** — click `combat → Excluded worlds`, enter a comma-separated list, and confirm it is
  saved.

Confirm an invalid value is rejected with an explanatory message and does not change the file: for
example, attempt a text value that is not valid MiniMessage for `chat-format → Chat format`.

## Hidden fields

Confirm the editor never shows a control for:

- any `modules.api.*` value (host, port, CORS origins, session cookie, timeouts, admin API key),
- the anti-cheat plugin-messaging channel,
- the tab-list header or footer,
- `core.timezone`, `core.auto-save-interval` or `core.debug`,
- `update-mode.active` or `start-sequence.has-started`.

The `tablist` category should only offer `Update interval`.

## Lifecycle and close prompts

1. Change a hot-reloadable field (for example `combat → Disable elytra in combat`), close the GUI,
   and confirm a message offers the clickable **Reload now** action. Click it and confirm the reload
   succeeds.
2. Change a restart-only field (for example toggle a module `enabled` flag, or change
   `tablist → Update interval`), close the GUI, and confirm the prompt states that a restart is
   required and does **not** offer a live reload.
3. Change both kinds in one session and confirm the close message reports both, with module
   enablement described only as restart-required.
4. Close the GUI without changing anything and confirm no prompt is shown.

## Persistence

1. After a change, inspect `plugins/Kreiscraft/config.yml` and confirm the new value is written,
   existing comments survive, and unrelated keys are untouched.
2. Run `/kreiscraft reload` (for hot-reloadable changes only) and confirm the running behaviour
   matches the new value.
