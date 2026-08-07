# Permissions

## Naming convention

Every permission follows the pattern `kreiscraft.<module-id>.<action>`, where `<module-id>` is the compact prefix used in the permission node (e.g. `anticheat`, not `anti-cheat`).

## Notes

- **Module disabled:** If a module has `enabled: false` in `config.yml` the module never registers, and its commands and permissions are never added to the server. A player cannot hold a permission that was never registered.
- **Op players:** Operators are granted the `*` wildcard, so every `default: op` permission is automatically available to them.
- **`admin-api-key`:** The `api` module config key `admin-api-key` is **not** a permission node. It is a shared-secret header required for `GET`/`POST /whitelist` HTTP requests. It has no relationship to Bukkit permissions.

**See also:** [`configuration.md`](configuration.md) for per-module config keys, [`modules.md`](modules.md) for the module registry.

---

## admin

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.admin.gamemode` | Change gamemode | `op` |
| `kreiscraft.admin.head` | Swap held item and helmet | `op` |
| `kreiscraft.admin.help` | Open Kreiscraft help links | `true` |
| `kreiscraft.admin.ip` | See a player's IP | `op` |
| `kreiscraft.admin.item` | Spawn items | `op` |
| `kreiscraft.admin.lastonline` | See a player's last-online time | `op` |
| `kreiscraft.admin.ping` | See a player's ping | `true` |
| `kreiscraft.admin.skull` | Get a player head | `op` |
| `kreiscraft.admin.spawnmob` | Spawn mobs | `op` |
| `kreiscraft.admin.statistic` | See a player's statistic | `op` |
| `kreiscraft.admin.sudo` | Force a player to run chat/command | `op` |
| `kreiscraft.admin.tps` | See server TPS | `true` |
| `kreiscraft.admin.uuid` | See a player's UUID | `op` |
| `kreiscraft.admin.weather` | Change weather | `op` |

## anticheat

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.anticheat.view` | View hack logs | `op` |

## api

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.api.link` | Link a browser session to a Bukkit account via /link-player | `true` |

## bedrock

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.bedrock.admin` | Manage the bedrock whitelist | `op` |

## combat

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.combat.admin` | Manually tag a player as in combat | `op` |

## effects

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.effects.use` | Toggle particle trail | `true` |

## glow

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.glow.use` | Toggle glow effect | `true` |

## invsee

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.invsee.edit` | Edit another player's inventory | `op` |
| `kreiscraft.invsee.view` | View another player's inventory | `op` |

## jail

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.jail.bypass` | Cannot be jailed | `op` |
| `kreiscraft.jail.use` | Jail and unjail players | `op` |

## pluginhider

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.pluginhider.admin` | Manage the plugin-hider allowlist | `op` |
| `kreiscraft.pluginhider.bypass` | See all commands regardless of the allowlist | `op` |

## positions

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.positions.use` | Save/teleport to personal positions | `true` |

## reboot

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.reboot.admin` | Trigger or cancel a server restart | `op` |

## settingsgui

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.settingsgui.use` | Open the per-player settings GUI | `true` |

## signcolor

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.signcolor.anvil` | Use MiniMessage in anvil rename | `true` |
| `kreiscraft.signcolor.use` | Use MiniMessage on signs | `true` |

## spawn

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.spawn.set` | Set the server spawn point | `op` |
| `kreiscraft.spawn.use` | Teleport to spawn | `true` |

## startsequence

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.startsequence.admin` | Trigger the season start sequence | `op` |

## status

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.status.set.other` | Set another player's status | `op` |
| `kreiscraft.status.set.self` | Set your own status prefix/suffix | `true` |

## updatemode

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.updatemode.admin` | Toggle maintenance mode | `op` |
| `kreiscraft.updatemode.bypass` | Log in during maintenance mode | `op` |

## worldmgmt

| Permission node | Description | Default |
|---|---|---|
| `kreiscraft.worldmgmt.portal` | Compute portal coordinate translations | `true` |
| `kreiscraft.worldmgmt.setworldspawn` | Set the spawn point of the current world | `op` |
| `kreiscraft.worldmgmt.world` | Teleport between worlds | `op` |

---

Total: 41 permission nodes across 19 modules.
