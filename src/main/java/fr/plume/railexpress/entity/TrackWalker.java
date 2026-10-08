package fr.plume.railexpress.entity;

import fr.plume.railexpress.block.CrossingTrackBlock;
import fr.plume.railexpress.block.ElectrifiedTrackBlock;
import fr.plume.railexpress.block.SwitchTrackBlock;
import fr.plume.railexpress.block.StationTrackBlock;
import fr.plume.railexpress.block.TrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fait avancer un point le long des rails (vanilla ou du mod), en suivant les courbes et les pentes.
 * Fonctionne pour n'importe quelle distance, ce qui permet des vitesses élevées sans dérailler.
 */
public final class TrackWalker {
	/** Vitesse maximale sur des rails vanilla. */
	public static final double VANILLA_RAIL_LIMIT = 0.5;

	public record Result(Vec3 pos, Vec3 tangent, boolean blocked) {
	}

	private TrackWalker() {
	}

	public static boolean isRail(BlockState state) {
		return state.getBlock() instanceof BaseRailBlock || state.getBlock() instanceof CrossingTrackBlock;
	}

	@Nullable
	public static BlockPos findRail(Level level, Vec3 p) {
		BlockPos pos = BlockPos.containing(p.x, p.y + 1.0E-3, p.z);
		if (isRail(level.getBlockState(pos))) {
			return pos;
		}
		BlockPos below = pos.below();
		if (isRail(level.getBlockState(below))) {
			return below;
		}
		return null;
	}

	public static RailShape shapeOf(BlockState state) {
		if (state.getBlock() instanceof CrossingTrackBlock) {
			return RailShape.NORTH_SOUTH;
		}
		if (state.getBlock() instanceof SwitchTrackBlock) {
			return SwitchTrackBlock.activeShape(state);
		}
		BaseRailBlock rail = (BaseRailBlock) state.getBlock();
		return state.getValue(rail.getShapeProperty());
	}

	/** Forme suivie en arrivant dans la direction donnée (un croisement s'adapte au sens de passage). */
	public static RailShape shapeFor(BlockState state, Vec3 dir) {
		if (state.getBlock() instanceof CrossingTrackBlock) {
			return Math.abs(dir.x) > Math.abs(dir.z) ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
		}
		return shapeOf(state);
	}

	/** Les deux sorties d'un rail : {dx, dz, dy}. dy = 1 si la sortie est en haut d'une pente. */
	public static int[][] exits(RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH -> new int[][]{{0, -1, 0}, {0, 1, 0}};
			case EAST_WEST -> new int[][]{{1, 0, 0}, {-1, 0, 0}};
			case ASCENDING_EAST -> new int[][]{{1, 0, 1}, {-1, 0, 0}};
			case ASCENDING_WEST -> new int[][]{{-1, 0, 1}, {1, 0, 0}};
			case ASCENDING_NORTH -> new int[][]{{0, -1, 1}, {0, 1, 0}};
			case ASCENDING_SOUTH -> new int[][]{{0, 1, 1}, {0, -1, 0}};
			case SOUTH_EAST -> new int[][]{{0, 1, 0}, {1, 0, 0}};
			case SOUTH_WEST -> new int[][]{{0, 1, 0}, {-1, 0, 0}};
			case NORTH_WEST -> new int[][]{{0, -1, 0}, {-1, 0, 0}};
			case NORTH_EAST -> new int[][]{{0, -1, 0}, {1, 0, 0}};
		};
	}

	private static Vec3 exitPoint(BlockPos pos, int[] exit) {
		return new Vec3(pos.getX() + 0.5 + exit[0] * 0.5, pos.getY() + exit[2], pos.getZ() + 0.5 + exit[1] * 0.5);
	}

	public static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		double len = h.length();
		return len < 1.0E-6 ? new Vec3(0, 0, 1) : h.scale(1.0 / len);
	}

	/** Vrai si le rail situé en {@code p} est raccordé à une sortie d'altitude {@code y} venant de la direction {@code dir}. */
	private static boolean connects(Level level, Vec3 p, Vec3 dir, double y) {
		BlockPos pos = findRail(level, p);
		if (pos == null) {
			return false;
		}
		for (int[] exit : exits(shapeFor(level.getBlockState(pos), dir))) {
			if (exit[0] * dir.x + exit[1] * dir.z < -0.5 && Math.abs(pos.getY() + exit[2] - y) < 0.1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Avance de {@code distance} blocs depuis {@code start} dans la direction approximative {@code direction}.
	 */
	public static Result walk(Level level, Vec3 start, Vec3 direction, double distance) {
		Vec3 p = start;
		Vec3 dir = horizontal(direction);
		Vec3 tangent = dir;
		double remaining = Math.max(0, distance);
		for (int i = 0; i < 512; i++) {
			BlockPos pos = findRail(level, p);
			if (pos == null) {
				return new Result(p, tangent, true);
			}
			int[][] ex = exits(shapeFor(level.getBlockState(pos), dir));
			Vec3 d0 = new Vec3(ex[0][0], 0, ex[0][1]);
			Vec3 d1 = new Vec3(ex[1][0], 0, ex[1][1]);
			boolean toFirst = d0.dot(dir) > d1.dot(dir);
			Vec3 a = exitPoint(pos, toFirst ? ex[1] : ex[0]);
			Vec3 b = exitPoint(pos, toFirst ? ex[0] : ex[1]);
			Vec3 exitDir = toFirst ? d0 : d1;
			Vec3 seg = b.subtract(a);
			double len = seg.length();
			double t = Mth.clamp(p.subtract(a).dot(seg) / (len * len), 0.0, 1.0);
			tangent = horizontal(seg);
			double toEnd = (1.0 - t) * len;
			if (remaining <= toEnd) {
				return new Result(a.add(seg.scale(t + remaining / len)), tangent, false);
			}
			remaining -= toEnd;
			Vec3 next = b.add(exitDir.scale(1.0E-3));
			if (!connects(level, next, exitDir, b.y)) {
				return new Result(b, tangent, true);
			}
			p = next;
			dir = exitDir;
		}
		return new Result(p, tangent, true);
	}

	public static double speedLimit(Level level, Vec3 p) {
		BlockPos pos = findRail(level, p);
		if (pos == null) {
			return VANILLA_RAIL_LIMIT;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof TrackBlock track) {
			return track.getSpeedLimit();
		}
		if (state.getBlock() instanceof CrossingTrackBlock) {
			return 2.0;
		}
		return VANILLA_RAIL_LIMIT;
	}

	public static boolean isLiveWire(Level level, Vec3 p) {
		BlockPos pos = findRail(level, p);
		if (pos == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof ElectrifiedTrackBlock && state.getValue(ElectrifiedTrackBlock.POWERED);
	}

	public static boolean isStationStop(Level level, Vec3 p) {
		BlockPos pos = findRail(level, p);
		if (pos == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof StationTrackBlock && !state.getValue(StationTrackBlock.POWERED);
	}
}
