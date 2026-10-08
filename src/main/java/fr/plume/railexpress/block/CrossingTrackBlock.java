package fr.plume.railexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Croisement-aiguillage : quatre voies s'y rejoignent. Selon le réglage, les trains
 * le traversent tout droit, ou tournent à gauche / à droite (par rapport à leur sens de marche).
 * Clic droit pour changer le réglage.
 */
public class CrossingTrackBlock extends Block {
	public static final MapCodec<CrossingTrackBlock> CODEC = simpleCodec(CrossingTrackBlock::new);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

	public enum Route implements StringRepresentable {
		STRAIGHT("straight"), LEFT("left"), RIGHT("right");

		private final String name;

		Route(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Route> ROUTE = EnumProperty.create("route", Route.class);

	public CrossingTrackBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(ROUTE, Route.STRAIGHT));
	}

	@Override
	public MapCodec<CrossingTrackBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROUTE);
	}

	/**
	 * Côté de sortie pour un train entrant par le côté {@code entry} (vecteur {dx, dz} du centre vers le bord d'entrée).
	 */
	public static int[] exitFor(BlockState state, int[] entry) {
		int tx = -entry[0];
		int tz = -entry[1];
		return switch (state.getValue(ROUTE)) {
			case STRAIGHT -> new int[]{tx, tz};
			case LEFT -> new int[]{tz, -tx};
			case RIGHT -> new int[]{-tz, tx};
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		Route next = Route.values()[(state.getValue(ROUTE).ordinal() + 1) % Route.values().length];
		level.setBlock(pos, state.setValue(ROUTE, next), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
		player.displayClientMessage(Component.translatable("message.railexpress.route_" + next.getSerializedName()), true);
		return InteractionResult.SUCCESS;
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
