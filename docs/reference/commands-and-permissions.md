---
title: Commands and Permissions
---

Complete reference for all Kreiscraft commands and permission nodes.

---

## Part 1: Commands Table

| Command | Aliases | Module | Description | Permission | Default |
|---------|---------|--------|-------------|-----------|---------|
| `/kreiscraft reload` | `kc` | core | Reload config, messages, and all module configs. | `kreiscraft.admin` | op¹ |
| `/kreiscraftgamemode <mode> [player]` | `kreiscraftgm`, `kcgamemode`, `kcgm` | `admin` | Set a player's gamemode (`survival`/`creative`/`adventure`/`spectator` or `0`–`3`). | `kreiscraft.admin.gamemode` | op |
| `/item <material> [count]` | `i` | `admin` | Give yourself the specified item (up to 1 024). | `kreiscraft.admin.item` | op |
| `/skull [player]` | `kopf` | `admin` | Give yourself a player-head skull. | `kreiscraft.admin.skull` | op |
| `/head` | — | `admin` | Swap the item in your main hand with your helmet slot. | `kreiscraft.admin.head` | op |
| `/kreiscraftweather <clear\|rain\|thunder>` | `kcweather` | `admin` | Change the weather in your current world. | `kreiscraft.admin.weather` | op |
| `/spawnmob <entityType> [count]` | `sm` | `admin` | Spawn mobs at your location (up to 50). | `kreiscraft.admin.spawnmob` | op |
| `/ip [player]` | — | `admin` | Show a player's IP address. | `kreiscraft.admin.ip` | op |
| `/uuid [player]` | — | `admin` | Show a player's UUID. | `kreiscraft.admin.uuid` | op |
| `/lastonline <player>` | — | `admin` | Show when a player was last online. | `kreiscraft.admin.lastonline` | op |
| `/ping [player]` | — | `admin` | Show a player's ping in milliseconds. | `kreiscraft.admin.ping` | true |
| `/tps` | — | `admin` | Show server TPS. | `kreiscraft.admin.tps` | true |
| `/statistic <player> <stat>` | — | `admin` | Show a player's Bukkit `Statistic` value. | `kreiscraft.admin.statistic` | op |
| `/sudo <target\|all> <text>` | — | `admin` | Force a player (or all players) to send a chat message. | `kreiscraft.admin.sudo` | op |
| `/kreiscrafthelp` | `?`, `kchelp` | `admin` | Show configured website/Discord/YouTube help links. | `kreiscraft.admin.help` | true |
| `/hackerlogs <target> [cheats\|xray]` | — | `anti-cheat` | View stored hack-log entries for a player. | `kreiscraft.anticheat.view` | op |
| `/bedrocklist add <name>` | — | `bedrock` | Add a known Bedrock player to the whitelist. | `kreiscraft.bedrock.admin` | op |
| `/bedrocklist list` | — | `bedrock` | List every known Bedrock player name in deterministic order. | `kreiscraft.bedrock.admin` | op |
| `/combat <target> <seconds>` | — | `combat` | Manually tag a player as in combat for the given duration (1–100 s). | `kreiscraft.combat.admin` | op |
| `/effects` | — | `effects` | Toggle your particle trail on/off. | `kreiscraft.effects.use` | true |
| `/effects <particle>` | — | `effects` | Set your particle type by Bukkit `Particle` name. | `kreiscraft.effects.use` | true |
| `/glow` | — | `glow` | Toggle the Glowing effect on yourself. | `kreiscraft.glow.use` | true |
| `/invsee <player>` | — | `invsee` | View (or edit) a player's inventory. | `kreiscraft.invsee.view` | op |
| `/enderchest <player>` | `ec`, `echest` | `invsee` | View (or edit) a player's ender chest (online only). | `kreiscraft.invsee.view` | op |
| `/jail <target> [reason...]` | `kerker` | `jail` | Jail an unjailed player, or release an already-jailed player back to their original location. | `kreiscraft.jail.use` | op |
| `/pluginhider add <command>` | — | `plugin-hider` | Add a command to the tab-completion allowlist. | `kreiscraft.pluginhider.admin` | op |
| `/pluginhider remove <command>` | — | `plugin-hider` | Remove a command from the allowlist. | `kreiscraft.pluginhider.admin` | op |
| `/pluginhider list` | — | `plugin-hider` | List all allowed commands. | `kreiscraft.pluginhider.admin` | op |
| `/pos` | — | `positions` | Open the positions GUI. | `kreiscraft.positions.use` | true |
| `/pos set <name>` | — | `positions` | Save current location as a named position. | `kreiscraft.positions.use` | true |
| `/pos get <name>` | — | `positions` | Print coordinates for a named position. | `kreiscraft.positions.use` | true |
| `/pos delete <name>` | — | `positions` | Delete a named position. | `kreiscraft.positions.use` | true |
| `/pos list` | — | `positions` | List all saved positions with coordinates. | `kreiscraft.positions.use` | true |
| `/reboot` | `restart` | `reboot` | Start a restart countdown with the default duration. | `kreiscraft.reboot.admin` | op |
| `/reboot <seconds> [reason]` | `restart` | `reboot` | Start a countdown with a custom duration and optional reason. | `kreiscraft.reboot.admin` | op |
| `/reboot cancel` | `restart` | `reboot` | Cancel an active restart countdown. | `kreiscraft.reboot.admin` | op |
| `/settings` | — | `settings-gui` | Open the per-player settings GUI. | `kreiscraft.settingsgui.use` | true |
| `/spawn` | — | `spawn` | Teleport to the server spawn (within `teleport-range` blocks). | `kreiscraft.spawn.use` | true |
| `/setspawn` | — | `spawn` | Set the server spawn to your current location. | `kreiscraft.spawn.set` | op |
| `/start` | — | `start-sequence` | Trigger the season start sequence for your world. | `kreiscraft.startsequence.admin` | op |
| `/start reset` | — | `start-sequence` | Reset the started flag so the sequence can run again. | `kreiscraft.startsequence.admin` | op |
| `/status prefix <text>` | — | `status` | Set your own chat/overhead/tab-list prefix (≤32 visible chars). | `kreiscraft.status.set.self` | true |
| `/status suffix <text>` | — | `status` | Set your own suffix. | `kreiscraft.status.set.self` | true |
| `/updatemode on` | (sub-cmd `an`) | `update-mode` | Activate maintenance mode and disconnect all non-bypassed players. | `kreiscraft.updatemode.admin` | op |
| `/updatemode off` | (sub-cmd `aus`) | `update-mode` | Deactivate maintenance mode. | `kreiscraft.updatemode.admin` | op |
| `/updatemode status` | — | `update-mode` | Show whether maintenance mode is active. | `kreiscraft.updatemode.admin` | op |
| `/world <name>` | — | `world-mgmt` | Teleport to a loaded world's spawn. | `kreiscraft.worldmgmt.world` | op |
| `/setworldspawn` | `sws` | `world-mgmt` | Set the current world's spawn to your location. | `kreiscraft.worldmgmt.setworldspawn` | op |
| `/portal nether [x z]` | — | `world-mgmt` | Compute and display Nether coords (÷8) for given/current position; the no-args form requires you to be in the Overworld. | `kreiscraft.worldmgmt.portal` | true |
| `/portal overworld [x z]` | — | `world-mgmt` | Compute and display Overworld coords (×8) for given/current position; the no-args form requires you to be in the Nether. | `kreiscraft.worldmgmt.portal` | true |

> ¹ `kreiscraft.admin` is used by `ReloadCommand` but is **not declared** in `plugin.yml`. Bukkit treats undeclared permissions as `op` by default.

> **Tab-completion.** Player, entity, and particle arguments complete through the shared `SuggestionProviders` in `core/command`. Online-only arguments complete online player names; profile arguments complete online plus known-offline names; `/spawnmob` completes spawnable entity types; `/effects` completes particle names. A provider emits nothing to a sender who lacks the command's permission.

---

## Part 2: Permissions Tree

Nodes are listed exactly as declared in `plugin.yml`, grouped by module. The **Default** column reflects the `default:` value in `plugin.yml`.

### Core

- `kreiscraft.admin` — *(not declared in `plugin.yml`; defaults to op)* — Access to `/kreiscraft reload`.

### `admin` module

- `kreiscraft.admin.gamemode` — **(op)** — Change gamemode.
- `kreiscraft.admin.item` — **(op)** — Spawn items.
- `kreiscraft.admin.skull` — **(op)** — Get a player head.
- `kreiscraft.admin.head` — **(op)** — Swap held item and helmet.
- `kreiscraft.admin.weather` — **(op)** — Change weather.
- `kreiscraft.admin.spawnmob` — **(op)** — Spawn mobs.
- `kreiscraft.admin.ip` — **(op)** — See a player's IP.
- `kreiscraft.admin.uuid` — **(op)** — See a player's UUID.
- `kreiscraft.admin.lastonline` — **(op)** — See a player's last-online time.
- `kreiscraft.admin.ping` — **(true)** — See a player's ping.
- `kreiscraft.admin.tps` — **(true)** — See server TPS.
- `kreiscraft.admin.statistic` — **(op)** — See a player's statistic.
- `kreiscraft.admin.sudo` — **(op)** — Force a player to run chat/command.
- `kreiscraft.admin.help` — **(true)** — Open Kreiscraft help links.

### `anti-cheat` module

- `kreiscraft.anticheat.view` — **(op)** — View hack logs.

### `api` module

- `kreiscraft.api.link` — **(true)** — Link a browser session to a Bukkit account via `/link-player`.

### `bedrock` module

- `kreiscraft.bedrock.admin` — **(op)** — Manage the bedrock whitelist.

### `combat` module

- `kreiscraft.combat.admin` — **(op)** — Manually tag a player as in combat.

### `effects` module

- `kreiscraft.effects.use` — **(true)** — Toggle particle trail.

### `glow` module

- `kreiscraft.glow.use` — **(true)** — Toggle glow effect.

### `invsee` module

- `kreiscraft.invsee.view` — **(op)** — View another player's inventory.
- `kreiscraft.invsee.edit` — **(op)** — Edit another player's inventory.

### `jail` module

- `kreiscraft.jail.use` — **(op)** — Jail or release players.
- `kreiscraft.jail.bypass` — **(op)** — Cannot be jailed.

### `plugin-hider` module

- `kreiscraft.pluginhider.admin` — **(op)** — Manage the plugin-hider allowlist.
- `kreiscraft.pluginhider.bypass` — **(op)** — See all commands regardless of the allowlist.

### `positions` module

- `kreiscraft.positions.use` — **(true)** — Save/teleport to personal positions.

### `reboot` module

- `kreiscraft.reboot.admin` — **(op)** — Trigger or cancel a server restart.

### `settings-gui` module

- `kreiscraft.settingsgui.use` — **(true)** — Open the per-player settings GUI.

### `sign-color` module

- `kreiscraft.signcolor.use` — **(true)** — Format sign lines with legacy ampersand codes, Kreiscraft placeholders, and safe MiniMessage.
- `kreiscraft.signcolor.anvil` — **(true)** — Format anvil rename text with legacy ampersand codes, Kreiscraft placeholders, and safe MiniMessage.

### `spawn` module

- `kreiscraft.spawn.use` — **(true)** — Teleport to spawn.
- `kreiscraft.spawn.set` — **(op)** — Set the server spawn point.

### `start-sequence` module

- `kreiscraft.startsequence.admin` — **(op)** — Trigger the season start sequence.

### `status` module

- `kreiscraft.status.set.self` — **(true)** — Set your own status prefix/suffix.

### `update-mode` module

- `kreiscraft.updatemode.admin` — **(op)** — Toggle maintenance mode.
- `kreiscraft.updatemode.bypass` — **(op)** — Stay online when maintenance mode is activated and log in while it is active.

### `world-mgmt` module

- `kreiscraft.worldmgmt.world` — **(op)** — Teleport between worlds.
- `kreiscraft.worldmgmt.setworldspawn` — **(op)** — Set the spawn point of the current world.
- `kreiscraft.worldmgmt.portal` — **(true)** — Compute portal coordinate translations.

---

## Part 3: Permission Inheritance / Children

`plugin.yml` declares **no permission `children` groups**. All permission nodes are independent — there is no wildcard parent such as `kreiscraft.*`.
