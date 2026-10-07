package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sous-station électrique : émet en permanence un signal de redstone maximal,
 * ce qui met sous tension les voies électrifiées reliées (jusqu'à 64 rails).
 */
public class SubstationBlock extends Block {
	public static final MapCodec<SubstationBlock> CODEC = simpleCodec(SubstationBlock::new);

	public SubstationBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<SubstationBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return 15;
	}
}
