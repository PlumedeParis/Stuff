package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import fr.plume.railexpress.entity.TrackWalker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * Voie ferrée du mod. Supporte les courbes et les pentes comme un rail vanilla,
 * mais autorise une vitesse maximale bien plus élevée.
 */
public class TrackBlock extends BaseRailBlock {
	public static final MapCodec<TrackBlock> CODEC = simpleCodec(p -> new TrackBlock(1.0, p));
	public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE;
	/** Courbe faisant partie d'une diagonale (zigzag) : dessinée comme une ligne droite de travers. */
	public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");

	/** Vitesse maximale autorisée sur cette voie, en blocs par tick. */
	private final double speedLimit;

	public TrackBlock(double speedLimit, Properties properties) {
		this(false, speedLimit, properties);
	}

	protected TrackBlock(boolean straight, double speedLimit, Properties properties) {
		super(straight, properties);
		this.speedLimit = speedLimit;
		this.registerDefaultState(this.defaultStateWithShape());
	}

	protected BlockState defaultStateWithShape() {
		BlockState state = this.stateDefinition.any()
				.setValue(this.getShapeProperty(), RailShape.NORTH_SOUTH)
				.setValue(BlockStateProperties.WATERLOGGED, false);
		return state.hasProperty(DIAGONAL) ? state.setValue(DIAGONAL, false) : state;
	}

	public static boolean isCurve(RailShape shape) {
		return shape == RailShape.NORTH_EAST || shape == RailShape.NORTH_WEST || shape == RailShape.SOUTH_EAST || shape == RailShape.SOUTH_WEST;
	}

	/** Vrai si une courbe est reliée à une autre courbe : elle appartient alors à une diagonale. */
	public static boolean computeDiagonal(Level level, BlockPos pos, RailShape shape) {
		if (!isCurve(shape)) {
			return false;
		}
		for (int[] exit : TrackWalker.exits(shape)) {
			BlockState neighbour = level.getBlockState(pos.offset(exit[0], 0, exit[1]));
			if (TrackWalker.isRail(neighbour) && isCurve(TrackWalker.shapeOf(neighbour))) {
				return true;
			}
		}
		return false;
	}

	protected void refreshDiagonal(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(this) || !state.hasProperty(DIAGONAL)) {
			return;
		}
		boolean diagonal = computeDiagonal(level, pos, state.getValue(this.getShapeProperty()));
		if (state.getValue(DIAGONAL) != diagonal) {
			level.setBlock(pos, state.setValue(DIAGONAL, diagonal), Block.UPDATE_CLIENTS);
		}
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
		refreshDiagonal(level, pos);
	}

	@Override
	protected void updateState(BlockState state, Level level, BlockPos pos, Block neighborBlock) {
		refreshDiagonal(level, pos);
	}

	public double getSpeedLimit() {
		return speedLimit;
	}

	@Override
	public MapCodec<? extends TrackBlock> codec() {
		return CODEC;
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, BlockStateProperties.WATERLOGGED, DIAGONAL);
	}
}
