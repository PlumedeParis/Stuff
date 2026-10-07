package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
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
		return this.stateDefinition.any()
				.setValue(this.getShapeProperty(), RailShape.NORTH_SOUTH)
				.setValue(BlockStateProperties.WATERLOGGED, false);
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
		builder.add(SHAPE, BlockStateProperties.WATERLOGGED);
	}
}
