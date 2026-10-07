package fr.plume.railexpress.item;

import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.entity.TrackWalker;
import fr.plume.railexpress.entity.TrainCarEntity;
import fr.plume.railexpress.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** Objet permettant de poser un véhicule ferroviaire sur une voie. */
public class TrainCarItem extends Item {
	private final CarType carType;

	public TrainCarItem(CarType carType, Properties properties) {
		super(properties);
		this.carType = carType;
	}

	public CarType getCarType() {
		return carType;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!TrackWalker.isRail(state)) {
			return InteractionResult.FAIL;
		}
		RailShape shape = TrackWalker.shapeOf(state);
		if (shape.isSlope()) {
			if (context.getPlayer() != null && !level.isClientSide()) {
				context.getPlayer().displayClientMessage(Component.translatable("message.railexpress.flat_only"), true);
			}
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			Player player = context.getPlayer();
			Vec3 look = player != null ? player.getLookAngle() : new Vec3(0, 0, 1);
			int[][] exits = TrackWalker.exits(shape);
			Vec3 a = new Vec3(exits[0][0], 0, exits[0][1]);
			Vec3 b = new Vec3(exits[1][0], 0, exits[1][1]);
			Vec3 direction = a.dot(look) >= b.dot(look) ? a : b;
			TrainCarEntity car = new TrainCarEntity(ModEntities.get(carType), level, carType);
			car.placeOnTrack(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), direction);
			level.addFreshEntity(car);
			if (player == null || !player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
