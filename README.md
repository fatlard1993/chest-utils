# Chest Utils

A Minecraft Fabric mod. Dyed chests, and the buttons a chest should always have had.

## What This Mod Does

Two things, both about the time spent moving items rather than the items themselves.

**Every chest gets four buttons** down its right-hand side: sort what is in it, dump your pack into it, top up only the stacks it already has, or empty it into your pack. Your own inventory screen gets a sort button too, because tidying your pack is wanted standing in a field and not only while looking into somebody's chest.

**Chests can be painted.** Sixteen colours, applied with a dye you already have to a chest already placed. No recipe, no new item, no emptying and replacing the chest to recolour it.

## The Buttons

Solid arrows move everything. Hollow arrows move only what the far side already has - the one you want when you are restocking a chest rather than dumping into it.

| | On a chest | |
|---|---|---|
| ⇅ | Sort | Tidy the chest's contents |
| ↑ | Dump | Everything from your pack into the chest |
| ⇧ | Top up | Only into stacks the chest already has |
| ↓ | Empty | Everything from the chest into your pack |

A loot chest is a chest you can only take from, so it gets the pair pointing the other way: **↓ take all** and **⇩ top off**, filling your own part-used stacks from it. Sort and Dump would be two controls that visibly do nothing.

Both screens also carry the inventory sort button, beside your own hotbar.

## Drag And Scroll

Hold the mouse down and drag across slots to send them all to the other side. Scroll over a slot to send that one. Neither picks the stack up, so nothing is left on the cursor when you close the screen.

## Sorting

Sorting a pack is opinionated, so here is the opinion.

**Order comes from the creative menu.** Not alphabetical, not by registry id - the same order the game already teaches you when you scroll a creative tab, taken from the tabs themselves at server start. Stone lands next to cobblestone because that is where the game has always put it. Ties fall back to name, then to stack size.

**The hotbar is not storage.** It is a set of controls: which key the pickaxe is under was decided once and is now reached for without looking. So sorting never rearranges it. What it does is refill it - the half-empty stack of torches in slot three simply becomes a full one. Positions are yours; quantities are the chore.

## The Hotbar Lock

Beside the sort button on your own inventory screen is a star, and it is a switch. It says which of two things sorting does to your hotbar.

**☆ Unlocked.** Every sort lays the hotbar out by one rule: your best tools from the left in the order a hand reaches for them - sword, pickaxe, axe, shovel, hoe - with missing ones sliding left so there are no holes, then torches, and your best food at the far end where a mis-scroll mid-fight will not put dinner in your hand.

**★ Locked.** Sorting keeps the arrangement you have and only fills it. Whatever is in a slot says what that slot is *for*: a slot holding a pickaxe is a pickaxe slot and gets the best pickaxe you own when it empties, so an upgrade lands under the key your hand already knows. A slot holding torches is pinned to torches exactly, and will never decide that cobblestone is close enough. Food is a role rather than an item, so running out of cooked beef and carrying bread still fills the food slot.

A locked hotbar is still an ordinary hotbar. Drag things about, swap them, drop them, exactly as vanilla - the new arrangement is read back within the tick and becomes what the lock means from then on. The lock is not a thing that stops you; it is only the difference between sorting filling your hotbar and sorting rebuilding it.

Nothing is ever moved *out* of a locked hotbar, and armour and offhand are never touched by a sort at all.

`/hotbar` says what each slot is currently held for.

## Painting A Chest

Right-click a placed chest with any of the sixteen dyes. One dye per coat; painting a chest the colour it already is does nothing and keeps the dye.

A water bucket strips the paint back off.

**Colour belongs to the placed chest, not to an item.** Break a painted chest and the dye comes back to you along with a plain chest, so there is no such thing as a coloured chest in your inventory and no sixteen extra items to carry.

The colour is drawn by Pandorical. A client without it sees ordinary chests and loses nothing but the colour.

## Loot Ender

Where [Loot Ender](https://github.com/fatlard1993/loot-ender) is installed, your private copy of a structure chest opens with the take-only buttons on it. Neither mod needs to know how the other builds a screen.

## Pandorical

Chest Utils runs server-side, and [Pandorical](https://github.com/fatlard1993/pandorical) is a hard dependency (`fabric.mod.json`): the server will not load this mod without it. Every screen, button, glyph and chest colour here is declared through Pandorical and drawn by it.

No Chest Utils jar is needed on a client. A player needs Pandorical and nothing else.

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

## License

MIT, see [LICENSE](LICENSE).
