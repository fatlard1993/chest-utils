package justfatlard.chest_utils.action;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The jobs a hotbar slot can be held for, in the order a hand reaches for them.
 *
 * <p>A role is a category deep enough that any member of it will do: a slot kept for a pickaxe
 * wants <em>a</em> pickaxe, and getting the best one you own is an upgrade rather than a
 * surprise. Everything else a hotbar carries - torches, blocks, buckets - is wanted exactly, and
 * is pinned by item id instead.
 *
 * <p>Recognised by the vanilla item tags rather than by class or by name, so a modded pickaxe is
 * a pickaxe on the strength of the mod having said so - which the emerald and quartz tools in
 * this suite already do.
 */
public enum HotbarRole {
	SWORD(ItemTags.SWORDS),
	PICKAXE(ItemTags.PICKAXES),
	AXE(ItemTags.AXES),
	SHOVEL(ItemTags.SHOVELS),
	HOE(ItemTags.HOES),
	/**
	 * Food is a role and not a pinned item because the whole of what the slot is for is having
	 * something to eat in it. Pinning it to cooked beef leaves it empty on the trip you brought
	 * bread.
	 */
	FOOD(null);

	private final TagKey<Item> tag;

	HotbarRole(TagKey<Item> tag) {
		this.tag = tag;
	}

	public boolean matches(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (this == FOOD) return stack.has(DataComponents.FOOD);
		return stack.is(this.tag);
	}

	/** What this stack is for, if it is for anything in particular. */
	public static HotbarRole of(ItemStack stack) {
		for (HotbarRole role : values()) {
			if (role.matches(stack)) return role;
		}
		return null;
	}

	/**
	 * How good this one is, for picking between two that fill the same role.
	 *
	 * <p>Mining speed for anything that digs and damage for anything that does not, because those
	 * are the numbers the material exists to raise. Durability only breaks ties: given two
	 * diamond picks you want the less worn one, but a fresh stone pick never beats a chipped
	 * diamond.
	 */
	public long score(ItemStack stack) {
		if (this == FOOD) {
			var food = stack.get(DataComponents.FOOD);
			if (food == null) return Long.MIN_VALUE;
			return (long) (food.nutrition() * 1000 + food.saturation() * 10);
		}

		float quality = 0F;

		var tool = stack.get(DataComponents.TOOL);
		if (tool != null) {
			for (var rule : tool.rules()) {
				quality = Math.max(quality, rule.speed().orElse(0F));
			}
		}

		var weapon = stack.get(DataComponents.WEAPON);
		if (weapon != null) {
			quality = Math.max(quality, weapon.itemDamagePerAttack());
		}

		int left = stack.getMaxDamage() - stack.getDamageValue();
		return ((long) (quality * 1000)) * 100_000L + Math.max(0, left);
	}
}
