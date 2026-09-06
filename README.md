# Chest Utils

A Minecraft Fabric mod. Dyed chests, and the buttons a chest should always have had.

## What This Mod Does

Two things, both about the time spent moving items rather than the items themselves.

**Every chest gets four buttons** down its right-hand side: sort what is in it, dump your pack into it, top up only the stacks it already has, or empty it into your pack. Your own inventory screen gets a sort button too, because tidying your pack is wanted standing in a field and not only while looking into somebody's chest.

**Chests can be painted.** Sixteen colours, applied with a dye you already have to a chest already placed. No recipe, no new item, no emptying and replacing the chest to recolour it.

## The Buttons

Sorting follows the creative menu's own order, so a tidied pack reads the way the menu you already know does. One exception: the bow is lifted to the head of the tools, because Combat files it behind every sword, axe and trident, and a bow is something you reach for without looking.

Solid arrows move everything. Outline arrows move only what the far side already has - the one you want when you are restocking a chest rather than dumping into it.

| On a chest | |
|---|---|
| Sort | Tidy the chest's contents |
| Dump (solid up) | Everything from your pack into the chest |
| Top up (outline up) | Only into stacks the chest already has |
| Empty (solid down) | Everything from the chest into your pack |
| Search (magnifier) | Swap the row for a search field |

Search takes the row's place with a field and a cross to bring the row back. Type, and every slot on the screen that does not match - chest, pack and hotbar alike - drops behind a dark veil, so what you are after is the thing still lit. A word matches an item's name or its id, so "log" finds Oak Log and "planks" finds every plank; more than one word has to match all of them. Items you move while searching are re-answered as they land.

A loot chest is a chest you can only take from, so it gets the pair pointing the other way: **take all** and **top off**, filling your own part-used stacks from it. Sort and Dump would be two controls that visibly do nothing.

The marks on the buttons are drawn pixel art, not font characters: at button size a unicode arrow is a one-pixel hairline, which reads as a web page next to vanilla's chunky widgets.

Both screens also carry the inventory sort button, beside your own hotbar.

## The Lock

The button beside a chest's name locks it: a locked chest can only be opened or broken by whoever
locked it, or an op. No key item, no configuration - locking is a claim, sized for a server where
the threat model is housemates. A double chest locks as a whole (including a chest placed against
a locked single later), and anyone refused is told whose lock it is.

Press it again and the lock goes public: anyone can open the chest, but only the owner or an op can
break it, paint it, or change the lock. That is the community chest at spawn, which everyone should
be able to use and nobody should be able to walk off with. A third press unlocks it. The switch
itself only ever answers to the owner or an op, whichever state it is in.

Hoppers still work a locked chest: the lock is against players, and automation is the owner's own
plumbing.

### Sharing one

Look at the chest and use `/chest-lock`:

| | |
|---|---|
| `/chest-lock share <player>` | Let them in |
| `/chest-lock unshare <player>` | Shut them out again |
| `/chest-lock list` | Who can use this chest |

The chest is the one under your crosshair, because that is how the lock went on in the first place
- a button on the chest's own screen - and asking for coordinates to undo something you did by
pointing at it is a different mod's idea of consistency.

Look at a locked chest with [Block Tip](https://github.com/fatlard1993/block-tip) installed and it
tells you whose it is before you try the lid - and whether it is one you can open. A lock that only
announces itself by refusing you is an answer arriving after the question.

Only the owner (or an op) changes the list; anyone who can open the chest can ask who else can.
Somebody let in gets the owner's access, breaking included: they can empty it by opening it
anyway, so withholding the pickaxe would protect nothing. Unlocking and re-locking starts the list
empty.

## Drag And Scroll

Hold the mouse down and drag across slots to send them all to the other side. Scroll over a slot to send that one. Neither picks the stack up, so nothing is left on the cursor when you close the screen.

## Sorting

Sorting a pack is opinionated, so here is the opinion.

**Order comes from the creative menu.** Not alphabetical, not by registry id - the same order the game already teaches you when you scroll a creative tab, taken from the tabs themselves at server start. Stone lands next to cobblestone because that is where the game has always put it. Ties fall back to name, then to stack size.

**The hotbar is not storage.** It is a set of controls: which key the pickaxe is under was decided once and is now reached for without looking. So sorting never rearranges it. What it does is refill it - the half-empty stack of torches in slot three simply becomes a full one. Positions are yours; quantities are the chore.

## The Hotbar Lock

Beside the sort button on your own inventory screen is a star, and it is a switch. It says which of two things sorting does to your hotbar.

**☆ Unlocked.** Every sort lays the hotbar out by one rule: your best tools from the left in the order a hand reaches for them - bow, sword, pickaxe, axe, shovel, hoe - with missing ones sliding left so there are no holes, then torches, and your best food at the far end where a mis-scroll mid-fight will not put dinner in your hand. The bow leads because it is the one tool whose moment is chosen for you - a creeper closing, a skeleton across a ravine - and fumbling to it is the only fumble that costs hearts.

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

Chest Utils runs server-side, and [Pandorical](https://github.com/fatlard1993/pandorical) is required: the server will not load this mod without it. Every screen, button, glyph and chest colour here is declared through Pandorical and drawn by it.

No Chest Utils jar is needed on a client. A player needs Pandorical and nothing else.

## Development

Installing, building and the map of the source are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
