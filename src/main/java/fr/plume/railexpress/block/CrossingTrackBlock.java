package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Croisement de deux voies perpendiculaires : les trains le traversent tout droit dans les deux sens. */
public class CrossingTrackBlock extends Block {
	public static final MapCodec<CrossingTrackBlock> CODEC = simpleCodec(CrossingTrackBlock::new);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

	public CrossingTrackBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<CrossingTrackBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}
}
