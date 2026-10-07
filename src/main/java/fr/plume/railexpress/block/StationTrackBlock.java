package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * Voie d'arrêt en gare : les trains qui la franchissent freinent, s'arrêtent quelques
 * secondes puis repartent. Alimentée par un signal de redstone, elle laisse passer les trains (train direct).
 */
public class StationTrackBlock extends TrackBlock {
	public static final MapCodec<StationTrackBlock> CODEC = simpleCodec(StationTrackBlock::new);
	public static final EnumProperty<RailShape> STRAIGHT_SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public StationTrackBlock(Properties properties) {
		super(true, 0.6, properties);
	}

	@Override
	protected BlockState defaultStateWithShape() {
		return super.defaultStateWithShape().setValue(POWERED, false);
	}

	@Override
	public MapCodec<StationTrackBlock> codec() {
		return CODEC;
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return STRAIGHT_SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STRAIGHT_SHAPE, BlockStateProperties.WATERLOGGED, POWERED);
	}

	@Override
	protected void updateState(BlockState state, Level level, BlockPos pos, Block neighborBlock) {
		boolean powered = level.hasNeighborSignal(pos);
		if (state.getValue(POWERED) != powered) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
		}
	}
}
