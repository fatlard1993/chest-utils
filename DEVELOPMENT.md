# Chest Utils - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `Main.java` | Event wiring: break guard, paint and lock cleanup, the hotbar read-back, mods-menu entries |
| `screen/ChestScreens.java` | Both screens, their buttons, search, the chest lock switch, and the hotbar switch |
| `action/ChestActions.java` | Sort, dump, top up, empty, and the hotbar refill |
| `action/ItemOrder.java` | The creative menu's own ordering, read at server start |
| `action/HotbarLocks.java` | What each hotbar slot is for, and how it gets filled |
| `action/HotbarRole.java` | The jobs a slot can be held for, and which of two fills one better |
| `action/PackSort.java` | The one entry point for tidying a pack |
| `block/DyedChests.java` | Which chests are painted what, and saying so to a client |
| `block/DyeInteraction.java` | Sneak-dyeing a chest, and the dye back on repaint or break |
| `block/ChestLocks.java` | Which chests are locked, by whom, public or not, and who they are shared with |
| `mixin/ChestOpenMixin.java` | Opens our screen instead of the vanilla one for chests and barrels, or refuses a locked one |
| `mixin/MinecartOpenMixin.java` | The same screen for chest minecarts and chest boats |
| `integration/ChestLockTipRegistration.java` | The lock line on Block Tip's card, when Block Tip is installed |
| `LockedChests.java` | The mods-menu list of a player's locked chests |
| `ChestShareCommand.java` | `/chest-lock` |
| `HotbarCommand.java` | `/hotbar` |

## Building

Chest Utils builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
