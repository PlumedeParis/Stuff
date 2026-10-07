package fr.plume.railexpress.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Gestion de l'outil d'attelage : clic sur un véhicule, puis sur un second pour les atteler. */
public final class Coupling {
	private static final Map<UUID, UUID> PENDING = new HashMap<>();
	private static final double EXTRA_DISTANCE = 5.0;

	private Coupling() {
	}

	public static void use(ServerPlayer player, TrainCarEntity car) {
		if (player.isSecondaryUseActive()) {
			car.uncoupleAll();
			PENDING.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.railexpress.uncoupled"), true);
			car.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 1.0F, 1.0F);
			return;
		}
		UUID firstId = PENDING.get(player.getUUID());
		TrainCarEntity first = firstId == null || !(player.level() instanceof ServerLevel level)
				? null
				: level.getEntity(firstId) instanceof TrainCarEntity c && !c.isRemoved() ? c : null;
		if (first == null || first == car) {
			PENDING.put(player.getUUID(), car.getUUID());
			player.displayClientMessage(Component.translatable("message.railexpress.selected"), true);
			return;
		}
		PENDING.remove(player.getUUID());

		double maxDistance = (first.getCarType().length + car.getCarType().length) / 2.0 + EXTRA_DISTANCE;
		if (first.distanceTo(car) > maxDistance) {
			player.displayClientMessage(Component.translatable("message.railexpress.too_far"), true);
			return;
		}
		if (first.buildConsist().contains(car)) {
			player.displayClientMessage(Component.translatable("message.railexpress.already_coupled"), true);
			return;
		}
		boolean firstEnd = first.frontFaces(car);
		boolean secondEnd = car.frontFaces(first);
		if (!first.hasFreeEnd(firstEnd) || !car.hasFreeEnd(secondEnd)) {
			player.displayClientMessage(Component.translatable("message.railexpress.no_free_end"), true);
			return;
		}
		first.setLink(firstEnd, car.getUUID());
		car.setLink(secondEnd, first.getUUID());
		player.displayClientMessage(Component.translatable("message.railexpress.coupled"), true);
		car.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5F, 1.6F);
	}
}
