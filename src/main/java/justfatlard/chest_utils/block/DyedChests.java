package justfatlard.chest_utils.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which chests have been painted, and what the client should draw them as.
 *
 * <p>A dyed chest is an ordinary chest that has been told to draw itself differently: no new
 * block, no new item, nothing to break into or lose in a hopper. It keeps its contents through
 * being recoloured, it doubles up with an undyed chest beside it, and every other mod that knows
 * what a chest is still recognises it. The whole difference is a texture, which is exactly what
 * Pandorical's chest overlays are for.
 *
 * <p>Kept as saved data against the level rather than in the block entity, because the block
 * entity belongs to vanilla and a chest that stopped being a vanilla chest would give up all of
 * the above to store one word.
 */
public final class DyedChests extends SavedData {
	private static final String STORAGE_KEY = "chest-utils:dyed";

	private record Entry(long pos, String colour) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
			Codec.STRING.fieldOf("colour").forGetter(Entry::colour)
		).apply(instance, Entry::new));
	}

	public static final Codec<DyedChests> CODEC = Entry.CODEC.listOf()
		.xmap(DyedChests::fromEntries, DyedChests::toEntries);

	private static final SavedDataType<DyedChests> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), DyedChests::new, CODEC, DataFixTypes.LEVEL);

	private final Map<Long, String> painted = new HashMap<>();

	public static DyedChests get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The sprite id for a colour, in the form the chest atlas names its own. */
	public static Identifier texture(String colour) {
		return Identifier.fromNamespaceAndPath("chest-utils", "entity/chest/" + colour);
	}

	public String colourAt(BlockPos pos) {
		return this.painted.get(pos.asLong());
	}

	/** Paint one, and show everybody who is near enough to see it. */
	public void paint(ServerLevel level, BlockPos pos, String colour) {
		this.painted.put(pos.asLong(), colour);
		this.setDirty();

		for (ServerPlayer player : level.players()) {
			PandoricalApi.chestOverlays().add(player, texture(colour), List.of(pos));
		}
	}

	/** A chest that is no longer there, or no longer painted. */
	public void strip(ServerLevel level, BlockPos pos) {
		if (this.painted.remove(pos.asLong()) == null) return;
		this.setDirty();

		for (ServerPlayer player : level.players()) {
			PandoricalApi.chestOverlays().remove(player, List.of(pos));
		}
	}

	/**
	 * State every painted chest in full, for a client that remembers nothing.
	 *
	 * <p>One call per colour, because the overlay api keeps a texture's positions as a set of
	 * their own and replacing one must not disturb the other fifteen.
	 */
	public void restate(ServerPlayer player) {
		Map<String, List<BlockPos>> byColour = new HashMap<>();
		this.painted.forEach((packed, colour) ->
			byColour.computeIfAbsent(colour, key -> new ArrayList<>()).add(BlockPos.of(packed)));

		byColour.forEach((colour, positions) ->
			PandoricalApi.chestOverlays().replace(player, texture(colour), positions));
	}

	private static DyedChests fromEntries(List<Entry> entries) {
		DyedChests data = new DyedChests();
		for (Entry entry : entries) data.painted.put(entry.pos(), entry.colour());
		return data;
	}

	private static List<Entry> toEntries(DyedChests data) {
		List<Entry> entries = new ArrayList<>(data.painted.size());
		data.painted.forEach((pos, colour) -> entries.add(new Entry(pos, colour)));
		return entries;
	}
}
