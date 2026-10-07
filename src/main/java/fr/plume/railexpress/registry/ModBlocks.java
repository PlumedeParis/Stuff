package fr.plume.railexpress.registry;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.block.DecorBlock;
import fr.plume.railexpress.block.ElectrifiedTrackBlock;
import fr.plume.railexpress.block.StationTrackBlock;
import fr.plume.railexpress.block.SubstationBlock;
import fr.plume.railexpress.block.TrackBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;

public final class ModBlocks {
	/** Voie standard : 1 bloc/tick max (72 km/h). */
	public static final Block TRACK = register("track",
			p -> new TrackBlock(1.0, p), BlockBehaviour.Properties.ofFullCopy(Blocks.RAIL));
	/** Voie LGV (ligne à grande vitesse) : 2,75 blocs/tick max (198 km/h). */
	public static final Block HIGH_SPEED_TRACK = register("high_speed_track",
			p -> new TrackBlock(2.75, p), BlockBehaviour.Properties.ofFullCopy(Blocks.RAIL));
	/** Voie LGV électrifiée : alimente les TGV et Shinkansen. */
	public static final Block ELECTRIFIED_TRACK = register("electrified_track",
			p -> new ElectrifiedTrackBlock(2.75, p), BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL));
	/** Voie de gare : arrêt automatique. */
	public static final Block STATION_TRACK = register("station_track",
			StationTrackBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL));

	public static final Block SUBSTATION = register("substation",
			SubstationBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
	public static final Block CATENARY_MAST = register("catenary_mast",
			p -> new DecorBlock(Block.box(6, 0, 6, 10, 16, 10), p), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS));
	public static final Block BUFFER_STOP = register("buffer_stop",
			p -> new DecorBlock(Block.box(1, 0, 1, 15, 14, 15), p), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS));
	public static final Block PLATFORM = register("platform",
			p -> new DecorBlock(Shapes.block(), p), BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE));

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, RailExpress.id(name));
		Block block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	public static void init() {
	}
}
