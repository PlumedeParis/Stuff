package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Aiguillage : voie droite qui peut dévier vers une courbe.
 * Clic droit : basculer l'aiguillage. Accroupi + clic droit : changer le côté de la déviation.
 * Un signal de redstone force la position déviée.
 */
public class SwitchTrackBlock extends TrackBlock {
	public static final MapCodec<SwitchTrackBlock> CODEC = simpleCodec(SwitchTrackBlock::new);
	public static final EnumProperty<RailShape> STRAIGHT_SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
	public static final EnumProperty<RailShape> DIVERGE = EnumProperty.create("diverge", RailShape.class,
			RailShape.NORTH_EAST, RailShape.NORTH_WEST, RailShape.SOUTH_EAST, RailShape.SOUTH_WEST);
	public static final BooleanProperty THROWN = BooleanProperty.create("thrown");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public SwitchTrackBlock(Properties properties) {
		super(true, 2.0, properties);
	}

	@Override
	protected BlockState defaultStateWithShape() {
		return super.defaultStateWithShape().setValue(DIVERGE, RailShape.SOUTH_EAST).setValue(THROWN, false).setValue(POWERED, false);
	}

	@Override
	public MapCodec<SwitchTrackBlock> codec() {
		return CODEC;
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return STRAIGHT_SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STRAIGHT_SHAPE, BlockStateProperties.WATERLOGGED, DIVERGE, THROWN, POWERED);
	}

	/** Forme réellement suivie par les trains. */
	public static RailShape activeShape(BlockState state) {
		return state.getValue(THROWN) || state.getValue(POWERED) ? state.getValue(DIVERGE) : state.getValue(STRAIGHT_SHAPE);
	}

	@Override
	protected void updateState(BlockState state, Level level, BlockPos pos, Block neighborBlock) {
		boolean powered = level.hasNeighborSignal(pos);
		if (state.getValue(POWERED) != powered) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockState next;
		if (player.isSecondaryUseActive()) {
			RailShape[] curves = {RailShape.NORTH_EAST, RailShape.SOUTH_EAST, RailShape.SOUTH_WEST, RailShape.NORTH_WEST};
			int i = 0;
			while (curves[i] != state.getValue(DIVERGE)) {
				i++;
			}
			next = state.setValue(DIVERGE, curves[(i + 1) % curves.length]);
		} else {
			next = state.setValue(THROWN, !state.getValue(THROWN));
		}
		level.setBlock(pos, next, Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, next.getValue(THROWN) ? 0.6F : 0.5F);
		player.displayClientMessage(Component.translatable(activeShape(next) == next.getValue(STRAIGHT_SHAPE)
				? "message.railexpress.switch_straight" : "message.railexpress.switch_diverging"), true);
		return InteractionResult.SUCCESS;
	}
}
