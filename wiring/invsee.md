# Wiring: invsee

## config.yml addition

```yaml
  invsee:
    enabled: false
```

## lang/de.yml addition

```yaml
invsee:
  gui-title-inv: "<gray>Inventar von <player>"
  gui-title-ec: "<gray>Enderchest von <player>"
  offline-empty: "<prefix> <yellow>Kein Inventardaten für <player></yellow> gefunden."
  target-not-found: "<prefix> <red>Spieler <player></red> nicht gefunden."
  read-error: "<prefix> <red>Fehler beim Lesen der Offline-Daten."
```

## lang/en.yml addition

```yaml
invsee:
  gui-title-inv: "<gray>Inventory of <player>"
  gui-title-ec: "<gray>Ender Chest of <player>"
  offline-empty: "<prefix> <yellow>No inventory data found for <player>.</yellow>"
  target-not-found: "<prefix> <red>Player <player></red> not found."
  read-error: "<prefix> <red>Error reading offline data."
```

## plugin.yml permissions

```yaml
  kreiscraft.invsee.view:
    description: View another player's inventory
    default: op
  kreiscraft.invsee.edit:
    description: Edit another player's inventory
    default: op
```

## KreiscraftBootstrap registration

    modules.register(new de.kreiscraft.modules.invsee.InvseeModule());

## Shared-file changes required outside your module

(none)

---

### Design notes (for Batch C reviewer)

**Editable path (InvUI `ReferencingInventory`):** Per PRE-FLIGHT ROUTING, the editable view uses
`xyz.xenondevs.invui.inventory.ReferencingInventory.fromReversedPlayerStorageContents(inv)` for
player inventories and `fromStorageContents(inv)` for ender chests. This wraps the live Bukkit
inventory so changes in the GUI write back immediately. No modification to `shared/gui/Gui` was
needed; the editable Gui is passed straight to `Gui.open(viewer, invuiGui, title)`.

**Armor/offhand in editable view:** The `fromReversedPlayerStorageContents` factory covers only
the 36 storage slots (main + hotbar). Armor and offhand are visible in the read-only 5-row view
but are not editable in the current implementation. If direct armor editing is required in future,
a `ReferencingInventory.fromContents(inv)` wrapper over all 41 slots would need a matching 5-row
layout structure — this can be added without touching `shared/`.

**Offline ender chest:** `OfflineInventoryReader.readInventory()` reads the main player inventory
NBT. Ender chest data is stored separately (in the world's `playerdata/<uuid>.dat` as the
`EnderItems` tag). The current `OfflineInventoryReader` does not expose ender chest data; offline
ender chest view is therefore not supported. `/enderchest` on an offline target sends
`invsee.target-not-found`. If offline ender chest support is needed, `OfflineInventoryReader`
would need a `readEnderChest(UUID)` method — this is a `platform/` change outside this module.
