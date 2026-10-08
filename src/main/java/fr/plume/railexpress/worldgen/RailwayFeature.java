package fr.plume.railexpress.worldgen;

import com.mojang.serialization.Codec;
import fr.plume.railexpress.block.DecorBlock;
import fr.plume.railexpress.block.ElectrifiedTrackBlock;
import fr.plume.railexpress.block.StationTrackBlock;
import fr.plume.railexpress.block.SwitchTrackBlock;
import fr.plume.railexpress.block.TrackBlock;
import fr.plume.railexpress.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Réseau ferroviaire généré dans le monde : un quadrillage de lignes rectilignes à altitude constante
 * (viaducs au-dessus des vallées et de la mer, tunnels dans les reliefs), des croisements avec raccordements
 * et aiguillages, des haltes, et une gare reliée par un embranchement près de chaque village.
 * Tout est déterministe : chaque tronçon est calculé indépendamment pour le chunk en cours de génération.
 */
public class RailwayFeature extends Feature<NoneFeatureConfiguration> {
	/** Espacement des lignes (en blocs). */
	public static final int GRID = 512;
	/** Décalage du quadrillage par rapport à l'origine du monde. */
	public static final int OFFSET = 160;
	/** Altitude des rails. */
	public static final int L = 70;
	/** Distance entre un croisement et ses aiguillages de raccordement. */
	private static final int D = 7;
	private static final int HALT_HALF = 14;

	public RailwayFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	// ------------------------------------------------------------------
	// Écriture limitée au chunk courant
	// ------------------------------------------------------------------

	private record Chunk(WorldGenLevel level, int x0, int z0) {
		boolean inside(int x, int z) {
			return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
		}

		void set(int x, int y, int z, BlockState state) {
			if (inside(x, z) && y > level.getMinY() && y < level.getMaxY()) {
				level.setBlock(new BlockPos(x, y, z), state, 2);
			}
		}

		BlockState get(int x, int y, int z) {
			return level.getBlockState(new BlockPos(x, y, z));
		}
	}

	private static int lineIndex(int coordinate) {
		return Math.floorDiv(coordinate - OFFSET, GRID);
	}

	private static int nearestLine(int coordinate) {
		return Math.round((coordinate - OFFSET) / (float) GRID) * GRID + OFFSET;
	}

	/** La plupart des lignes sont électrifiées (de façon déterministe). */
	private static boolean electrified(int lineCoordinate, boolean xAxis) {
		long h = lineCoordinate * 341873128712L + (xAxis ? 132897987541L : 9182736455L);
		h ^= (h >>> 17);
		// Environ 70 % des lignes sont électrifiées
		return Math.floorMod(h, 10) < 7;
	}

	private static boolean solid(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
				&& state.blocksMotion();
	}

	// ------------------------------------------------------------------

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		int x0 = (context.origin().getX() >> 4) << 4;
		int z0 = (context.origin().getZ() >> 4) << 4;
		Chunk c = new Chunk(level, x0, z0);
		int reach = 24;

		List<Integer> xLines = new ArrayList<>();
		List<Integer> zLines = new ArrayList<>();
		for (int k = lineIndex(z0 - reach); k <= lineIndex(z0 + 15 + reach) + 1; k++) {
			int z = k * GRID + OFFSET;
			if (z >= z0 - reach && z <= z0 + 15 + reach) {
				xLines.add(z);
			}
		}
		for (int k = lineIndex(x0 - reach); k <= lineIndex(x0 + 15 + reach) + 1; k++) {
			int x = k * GRID + OFFSET;
			if (x >= x0 - reach && x <= x0 + 15 + reach) {
				zLines.add(x);
			}
		}

		// 1) Lignes principales
		for (int zl : xLines) {
			line(c, zl, true);
		}
		for (int xl : zLines) {
			line(c, xl, false);
		}
		// 2) Gares de village et embranchements
		villages(context, c);
		// 3) Croisements (posés en dernier : ils remplacent les rails des lignes)
		for (int zl : xLines) {
			for (int xl : zLines) {
				crossing(c, xl, zl);
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Lignes
	// ------------------------------------------------------------------

	/** Pose du tronçon de ligne qui traverse le chunk. {@code fixed} est la coordonnée constante de la ligne. */
	private void line(Chunk c, int fixed, boolean xAxis) {
		boolean electric = electrified(fixed, xAxis);
		int from = xAxis ? c.x0 : c.z0;
		for (int along = from; along < from + 16; along++) {
			int rel = Math.floorMod(along - OFFSET, GRID);
			boolean nearCrossing = rel < D + 4 || rel > GRID - D - 4;
			int haltCenter = Math.floorDiv(along - OFFSET, GRID) * GRID + OFFSET + GRID / 2;
			boolean halt = Math.abs(along - haltCenter) <= HALT_HALF + 2;
			boolean tunnel = corridor(c, along, fixed, xAxis, halt ? 6 : 3, halt);
			BlockState rail = trackState(electric, xAxis ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
			if (along == haltCenter) {
				rail = ModBlocks.STATION_TRACK.defaultBlockState().setValue(StationTrackBlock.STRAIGHT_SHAPE, xAxis ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
			}
			put(c, along, L, fixed, xAxis, rail);
			if (electric && Math.floorMod(along, 48) == 0 && !nearCrossing) {
				put(c, along, L - 1, fixed, xAxis, ModBlocks.SUBSTATION.defaultBlockState());
			}
			if (electric && !tunnel && !halt && Math.floorMod(along, 16) == 8 && !nearCrossing) {
				mast(c, along, fixed, xAxis, Math.floorMod(along, 32) == 8 ? 3 : -3);
			}
			if (halt) {
				platformSlice(c, along, fixed, xAxis, along - haltCenter, along == haltCenter + HALT_HALF || along == haltCenter - HALT_HALF);
			}
		}
		int haltCenter = Math.floorDiv(from - OFFSET, GRID) * GRID + OFFSET + GRID / 2;
		for (int center : new int[]{haltCenter - GRID, haltCenter, haltCenter + GRID}) {
			if (Math.abs(center - from) < 40) {
				stationBuilding(c, center, fixed, xAxis, 1);
			}
		}
	}

	private static BlockState trackState(boolean electric, RailShape shape) {
		if (electric) {
			return ModBlocks.ELECTRIFIED_TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, shape)
					.setValue(ElectrifiedTrackBlock.POWERED, true).setValue(TrackBlock.DIAGONAL, TrackBlock.isCurve(shape));
		}
		return ModBlocks.HIGH_SPEED_TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, shape).setValue(TrackBlock.DIAGONAL, TrackBlock.isCurve(shape));
	}

	/** Écrit un bloc en coordonnées de ligne : {@code along} le long de la voie, {@code fixed + offset} en travers. */
	private static void put(Chunk c, int along, int y, int across, boolean xAxis, BlockState state) {
		if (xAxis) {
			c.set(along, y, across, state);
		} else {
			c.set(across, y, along, state);
		}
	}

	private static BlockState get(Chunk c, int along, int y, int across, boolean xAxis) {
		return xAxis ? c.get(along, y, across) : c.get(across, y, along);
	}

	/**
	 * Plate-forme de la voie : déblai, tunnel, ballast, viaduc et piles.
	 * @return vrai si la voie passe en tunnel à cet endroit
	 */
	private static boolean corridor(Chunk c, int along, int fixed, boolean xAxis, int half, boolean open) {
		boolean tunnel = !open && solid(get(c, along, L + 4, fixed, xAxis)) && solid(get(c, along, L + 7, fixed, xAxis));
		boolean viaduct = !solid(get(c, along, L - 2, fixed, xAxis));
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		for (int o = -half; o <= half; o++) {
			int across = fixed + o;
			for (int y = L; y <= L + 6; y++) {
				if (tunnel && (Math.abs(o) == 3 || y == L + 6)) {
					put(c, along, y, across, xAxis, Math.abs(o) == 3 && y == L + 2 && Math.floorMod(along, 4) == 0
							? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : bricks);
				} else if (!tunnel || Math.abs(o) < 3) {
					put(c, along, y, across, xAxis, Blocks.AIR.defaultBlockState());
				}
			}
			if (Math.abs(o) <= 3) {
				put(c, along, L - 1, across, xAxis, Math.abs(o) <= 2 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICK_SLAB.defaultBlockState()
						.setValue(SlabBlock.TYPE, SlabType.TOP));
			}
			if (viaduct && !tunnel && Math.abs(o) == 3 && !open) {
				put(c, along, L, across, xAxis, Blocks.STONE_BRICK_WALL.defaultBlockState());
			}
			if (viaduct && Math.floorMod(along, 12) == 0 && Math.abs(o) <= 1) {
				for (int y = L - 2; y > L - 120; y--) {
					if (solid(get(c, along, y, across, xAxis))) {
						break;
					}
					put(c, along, y, across, xAxis, bricks);
				}
			}
		}
		if (tunnel && Math.floorMod(along, 12) == 6) {
			put(c, along, L + 5, fixed, xAxis, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		return tunnel;
	}

	/** Poteau de caténaire (pylône en treillis surmonté d'un bras). */
	private static void mast(Chunk c, int along, int fixed, boolean xAxis, int offset) {
		for (int y = L; y <= L + 3; y++) {
			put(c, along, y, fixed + offset, xAxis, Blocks.IRON_BARS.defaultBlockState());
		}
		Direction towardTrack = xAxis ? (offset > 0 ? Direction.NORTH : Direction.SOUTH) : (offset > 0 ? Direction.WEST : Direction.EAST);
		put(c, along, L + 4, fixed + offset, xAxis, ModBlocks.CATENARY_MAST.defaultBlockState().setValue(DecorBlock.FACING, towardTrack));
	}

	// ------------------------------------------------------------------
	// Gares
	// ------------------------------------------------------------------

	/** Tranche de quai (des deux côtés de la voie) avec marquise, bancs et lanternes. */
	private static void platformSlice(Chunk c, int along, int fixed, boolean xAxis, int rel, boolean end) {
		for (int side = -1; side <= 1; side += 2) {
			Direction towardTrack = xAxis ? (side > 0 ? Direction.NORTH : Direction.SOUTH) : (side > 0 ? Direction.WEST : Direction.EAST);
			Direction away = towardTrack.getOpposite();
			for (int o = 2; o <= 5; o++) {
				int across = fixed + side * o;
				put(c, along, L - 1, across, xAxis, Blocks.STONE_BRICKS.defaultBlockState());
				put(c, along, L, across, xAxis, o == 2 ? ModBlocks.PLATFORM.defaultBlockState().setValue(DecorBlock.FACING, towardTrack)
						: Blocks.SMOOTH_STONE.defaultBlockState());
				if (!solid(get(c, along, L - 2, across, xAxis)) && Math.floorMod(along, 6) == 0) {
					for (int y = L - 2; y > L - 120 && !solid(get(c, along, y, across, xAxis)); y--) {
						put(c, along, y, across, xAxis, Blocks.STONE_BRICKS.defaultBlockState());
					}
				}
			}
			if (Math.abs(rel) <= 9) {
				for (int o = 2; o <= 5; o++) {
					put(c, along, L + 5, fixed + side * o, xAxis, Blocks.DARK_OAK_SLAB.defaultBlockState());
				}
				if (Math.floorMod(rel, 4) == 0) {
					for (int y = L + 1; y <= L + 4; y++) {
						put(c, along, y, fixed + side * 5, xAxis, Blocks.DARK_OAK_FENCE.defaultBlockState());
					}
				}
				if (Math.floorMod(rel, 4) == 2) {
					put(c, along, L + 4, fixed + side * 3, xAxis, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
					put(c, along, L + 1, fixed + side * 5, xAxis, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, away));
				}
			}
			if (end) {
				put(c, along, L + 1, fixed + side * 4, xAxis, Blocks.STONE_BRICK_WALL.defaultBlockState());
				put(c, along, L + 2, fixed + side * 4, xAxis, Blocks.LANTERN.defaultBlockState());
			}
		}
	}

	/** Bâtiment voyageurs derrière le quai (côté {@code side}). */
	private static void stationBuilding(Chunk c, int center, int fixed, boolean xAxis, int side) {
		BlockState bricks = Blocks.BRICKS.defaultBlockState();
		for (int a = center - 5; a <= center + 5; a++) {
			for (int o = 6; o <= 11; o++) {
				int across = fixed + side * o;
				boolean wall = a == center - 5 || a == center + 5 || o == 6 || o == 11;
				put(c, a, L, across, xAxis, Blocks.POLISHED_ANDESITE.defaultBlockState());
				put(c, a, L - 1, across, xAxis, Blocks.STONE_BRICKS.defaultBlockState());
				for (int y = L + 1; y <= L + 4; y++) {
					BlockState state = Blocks.AIR.defaultBlockState();
					if (wall) {
						boolean door = o == 6 && Math.abs(a - center) <= 0 && y <= L + 2;
						boolean window = y == L + 2 && (Math.floorMod(a - center, 3) == 1) && !(o == 6 && a == center);
						state = door ? Blocks.AIR.defaultBlockState() : window ? Blocks.GLASS_PANE.defaultBlockState() : bricks;
						if ((a == center - 5 || a == center + 5) && (o == 6 || o == 11)) {
							state = Blocks.STONE_BRICKS.defaultBlockState();
						}
					}
					put(c, a, y, across, xAxis, state);
				}
				put(c, a, L + 5, across, xAxis, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
				for (int y = L + 6; y <= L + 8; y++) {
					put(c, a, y, across, xAxis, Blocks.AIR.defaultBlockState());
				}
			}
			put(c, a, L + 1, fixed + side * 5, xAxis, Blocks.AIR.defaultBlockState());
		}
		put(c, center, L + 4, fixed + side * 8, xAxis, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(c, center - 3, L + 1, fixed + side * 10, xAxis, Blocks.SPRUCE_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, xAxis ? (side > 0 ? Direction.SOUTH : Direction.NORTH) : (side > 0 ? Direction.EAST : Direction.WEST)));
		put(c, center + 3, L + 1, fixed + side * 10, xAxis, Blocks.SPRUCE_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, xAxis ? (side > 0 ? Direction.SOUTH : Direction.NORTH) : (side > 0 ? Direction.EAST : Direction.WEST)));
		put(c, center, L + 1, fixed + side * 10, xAxis, Blocks.BARREL.defaultBlockState());
		put(c, center, L + 6, fixed + side * 6, xAxis, Blocks.BELL.defaultBlockState());
	}

	// ------------------------------------------------------------------
	// Croisements avec raccordements et aiguillages
	// ------------------------------------------------------------------

	private static RailShape curve(int[] a, int[] b) {
		boolean north = a[1] < 0 || b[1] < 0;
		boolean east = a[0] > 0 || b[0] > 0;
		return north ? (east ? RailShape.NORTH_EAST : RailShape.NORTH_WEST) : (east ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST);
	}

	private void crossing(Chunk c, int cx, int cz) {
		if (Math.abs(cx - (c.x0 + 8)) > 30 || Math.abs(cz - (c.z0 + 8)) > 30) {
			return;
		}
		// Croisement-aiguillage : clic droit pour choisir tout droit, à gauche ou à droite
		c.set(cx, L, cz, ModBlocks.CROSSING_TRACK.defaultBlockState());
		// Poste d'aiguillage
		for (int dx = 4; dx <= 6; dx++) {
			for (int dz = 4; dz <= 6; dz++) {
				int x = cx + dx + 4;
				int z = cz + dz + 4;
				c.set(x, L - 1, z, Blocks.STONE_BRICKS.defaultBlockState());
				boolean wall = dx == 4 || dx == 6 || dz == 4 || dz == 6;
				c.set(x, L, z, wall ? Blocks.BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
				for (int y = L + 1; y <= L + 3; y++) {
					c.set(x, y, z, wall ? (y == L + 2 ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.BRICKS.defaultBlockState()) : Blocks.AIR.defaultBlockState());
				}
				c.set(x, L + 4, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
			}
		}
		c.set(cx + 9, L + 3, cz + 9, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		c.set(cx + 8, L + 1, cz + 10, Blocks.AIR.defaultBlockState());
		c.set(cx + 8, L + 2, cz + 10, Blocks.AIR.defaultBlockState());
	}

	// ------------------------------------------------------------------
	// Gares de village
	// ------------------------------------------------------------------

	private void villages(FeaturePlaceContext<NoneFeatureConfiguration> context, Chunk c) {
		WorldGenLevel level = context.level();
		Holder<StructureSet> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(BuiltinStructureSets.VILLAGES).orElse(null);
		if (set == null || !(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) {
			return;
		}
		int regionSize = placement.spacing() * 16;
		int cx = c.x0 + 8;
		int cz = c.z0 + 8;
		int range = GRID / 2 + 64;
		for (int rx = Math.floorDiv(cx - range, regionSize); rx <= Math.floorDiv(cx + range, regionSize); rx++) {
			for (int rz = Math.floorDiv(cz - range, regionSize); rz <= Math.floorDiv(cz + range, regionSize); rz++) {
				ChunkPos chunk = placement.getPotentialStructureChunk(level.getSeed(), rx * placement.spacing(), rz * placement.spacing());
				int vx = chunk.getMiddleBlockX();
				int vz = chunk.getMiddleBlockZ();
				if (Math.abs(vx - cx) > range || Math.abs(vz - cz) > range || !isVillageBiome(context, vx, vz)) {
					continue;
				}
				villageStation(c, vx, vz);
			}
		}
	}

	private static boolean isVillageBiome(FeaturePlaceContext<NoneFeatureConfiguration> context, int x, int z) {
		Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(L),
				QuartPos.fromBlock(z), context.level().getLevel().getChunkSource().randomState().sampler());
		return biome.is(BiomeTags.HAS_VILLAGE_PLAINS) || biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)
				|| biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.is(BiomeTags.HAS_VILLAGE_TAIGA);
	}

	/** Embranchement depuis la ligne la plus proche jusqu'à une gare terminus au bord du village. */
	private void villageStation(Chunk c, int vx, int vz) {
		int zl = nearestLine(vz);
		int xl = nearestLine(vx);
		boolean toXLine = Math.abs(vz - zl) <= Math.abs(vx - xl);
		// Coordonnées « le long de la ligne principale » (a) et « vers le village » (b)
		int lineFixed = toXLine ? zl : xl;
		int a = toXLine ? vx : vz;
		int b = toXLine ? vz : vx;
		int crossingA = nearestLine(a);
		if (Math.abs(a - crossingA) < 30) {
			a = crossingA + (a >= crossingA ? 30 : -30);
		}
		int haltCenter = Math.floorDiv(a - OFFSET, GRID) * GRID + OFFSET + GRID / 2;
		if (Math.abs(a - haltCenter) < HALT_HALF + 12) {
			a = haltCenter + (a >= haltCenter ? HALT_HALF + 12 : -(HALT_HALF + 12));
		}
		int dir = b >= lineFixed ? 1 : -1;
		int stationCenter = b - dir * 40;
		if ((stationCenter - lineFixed) * dir < 30) {
			stationCenter = lineFixed + dir * 30;
		}
		int end = stationCenter + dir * HALT_HALF;
		boolean spurX = !toXLine; // l'embranchement est perpendiculaire à la ligne principale
		// Aiguillage sur la ligne principale
		if (toXLine) {
			c.set(a, L, lineFixed, ModBlocks.SWITCH_TRACK.defaultBlockState().setValue(SwitchTrackBlock.STRAIGHT_SHAPE, RailShape.EAST_WEST)
					.setValue(SwitchTrackBlock.DIVERGE, curve(new int[]{1, 0}, new int[]{0, dir})));
		} else {
			c.set(lineFixed, L, a, ModBlocks.SWITCH_TRACK.defaultBlockState().setValue(SwitchTrackBlock.STRAIGHT_SHAPE, RailShape.NORTH_SOUTH)
					.setValue(SwitchTrackBlock.DIVERGE, curve(new int[]{dir, 0}, new int[]{0, 1})));
		}
		// Voie de l'embranchement, quais et heurtoir
		RailShape shape = spurX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
		for (int s = lineFixed + dir; s != end + dir; s += dir) {
			if (!near(c, spurX ? s : a, spurX ? a : s)) {
				continue;
			}
			boolean platform = Math.abs(s - stationCenter) <= HALT_HALF;
			corridor(c, s, a, spurX, platform ? 6 : 3, platform || Math.abs(s - lineFixed) < 4);
			BlockState rail = s == stationCenter
					? ModBlocks.STATION_TRACK.defaultBlockState().setValue(StationTrackBlock.STRAIGHT_SHAPE, shape)
					: trackState(false, shape);
			put(c, s, L, a, spurX, rail);
			if (platform) {
				platformSlice(c, s, a, spurX, s - stationCenter, s == end || s == stationCenter - dir * HALT_HALF);
			}
		}
		put(c, end + dir, L, a, spurX, ModBlocks.BUFFER_STOP.defaultBlockState().setValue(DecorBlock.FACING,
				spurX ? (dir > 0 ? Direction.WEST : Direction.EAST) : (dir > 0 ? Direction.NORTH : Direction.SOUTH)));
		put(c, end + dir, L - 1, a, spurX, Blocks.STONE_BRICKS.defaultBlockState());
		if (Math.abs((spurX ? stationCenter : a) - (c.x0 + 8)) < 48 && Math.abs((spurX ? a : stationCenter) - (c.z0 + 8)) < 48) {
			stationBuilding(c, stationCenter, a, spurX, 1);
		}
	}

	private static boolean near(Chunk c, int x, int z) {
		return x >= c.x0 - 8 && x < c.x0 + 24 && z >= c.z0 - 8 && z < c.z0 + 24;
	}
}
