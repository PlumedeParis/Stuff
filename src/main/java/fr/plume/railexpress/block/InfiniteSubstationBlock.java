package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Sous-station illimitée (mode créatif) : met sous tension toutes les voies électrifiées
 * reliées entre elles, sans limite de distance.
 */
public class InfiniteSubstationBlock extends Block {
	public static final MapCodec<InfiniteSubstationBlock> CODEC = simpleCodec(InfiniteSubstationBlock::new);

	public InfiniteSubstationBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<InfiniteSubstationBlock> codec() {
		return CODEC;
	}
}
