# Chest Utils - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `screen/ChestScreens.java` | Both screens, their buttons, and the hotbar switch |
| `action/ChestActions.java` | Sort, dump, top up, empty, and the hotbar refill |
| `action/ItemOrder.java` | The creative menu's own ordering, read at server start |
| `action/HotbarLocks.java` | What each hotbar slot is for, and how it gets filled |
| `action/HotbarRole.java` | The jobs a slot can be held for, and which of two fills one better |
| `action/PackSort.java` | The one entry point for tidying a pack |
| `block/DyedChests.java` | Which chests are painted what, and saying so to a client |
| `block/DyeInteraction.java` | Dye on a chest, water bucket off it, dye back on break |
| `mixin/ChestOpenMixin.java` | Opens our screen instead of the vanilla one |
| `HotbarCommand.java` | `/hotbar` |

## Building

Chest Utils builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
