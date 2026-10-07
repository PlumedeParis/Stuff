package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Voie électrifiée (ligne à grande vitesse avec caténaire).
 * Elle est sous tension lorsqu'elle est reliée, par d'autres voies électrifiées,
 * à une source de redstone (par exemple une sous-station) située à moins de {@link #MAX_DISTANCE} rails.
 */
public class ElectrifiedTrackBlock extends TrackBlock {
	public static final MapCodec<ElectrifiedTrackBlock> CODEC = simpleCodec(p -> new ElectrifiedTrackBlock(2.5, p));
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final int MAX_DISTANCE = 64;

	public ElectrifiedTrackBlock(double speedLimit, Properties properties) {
		super(false, speedLimit, properties);
	}

	@Override
	protected BlockState defaultStateWithShape() {
		return super.defaultStateWithShape().setValue(POWERED, false);
	}

	@Override
	public MapCodec<ElectrifiedTrackBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(POWERED);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !oldState.is(this)) {
			level.scheduleTick(pos, this, 2);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		refreshPower(level.getBlockState(pos), level, pos);
	}

	@Override
	protected void updateState(BlockState state, Level level, BlockPos pos, Block neighborBlock) {
		refreshPower(state, level, pos);
	}

	private void refreshPower(BlockState state, Level level, BlockPos pos) {
		if (!state.is(this)) {
			return;
		}
		boolean powered = isFed(level, pos);
		if (state.getValue(POWERED) != powered) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
			level.updateNeighborsAt(pos.below(), this);
			level.updateNeighborsAt(pos.above(), this);
		}
	}

	/** Parcourt le réseau de voies électrifiées à la recherche d'une source d'énergie. */
	private boolean isFed(Level level, BlockPos start) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		ArrayDeque<Integer> depth = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		queue.add(start);
		depth.add(0);
		visited.add(start);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int d = depth.poll();
			if (level.hasNeighborSignal(pos)) {
				return true;
			}
			if (d >= MAX_DISTANCE) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(dir);
				for (BlockPos candidate : new BlockPos[]{side, side.above(), side.below()}) {
					if (!visited.contains(candidate) && level.getBlockState(candidate).getBlock() instanceof ElectrifiedTrackBlock) {
						visited.add(candidate);
						queue.add(candidate);
						depth.add(d + 1);
					}
				}
			}
		}
		return false;
	}
}
