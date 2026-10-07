package fr.plume.railexpress.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Sommeil dans les couchettes : quand tous les joueurs du monde dorment (dans un lit
 * ou dans une couchette de train) pendant la nuit, le jour se lève.
 */
public final class TrainSleep {
	private static final Map<UUID, Integer> SLEEP_TICKS = new HashMap<>();
	private static final Map<ServerLevel, Long> LAST_CHECK = new java.util.WeakHashMap<>();

	private TrainSleep() {
	}

	public static boolean isNight(Level level) {
		long time = level.getDayTime() % 24000L;
		return time >= 12542L && time <= 23460L;
	}

	public static boolean isInBunk(ServerPlayer player) {
		if (player.getVehicle() instanceof TrainCarEntity car) {
			CarLayout.Seat seat = car.seatFor(player);
			return seat != null && seat.isBed();
		}
		return false;
	}

	public static void tick(ServerLevel level) {
		long now = level.getGameTime();
		Long last = LAST_CHECK.get(level);
		if (last != null && last == now) {
			return;
		}
		LAST_CHECK.put(level, now);
		if (level.players().isEmpty()) {
			return;
		}
		boolean night = isNight(level);
		boolean everyoneAsleep = night;
		boolean anyBunk = false;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (isInBunk(player)) {
				anyBunk = true;
				int ticks = SLEEP_TICKS.merge(player.getUUID(), 1, Integer::sum);
				if (ticks < 100) {
					everyoneAsleep = false;
				}
			} else {
				SLEEP_TICKS.remove(player.getUUID());
				if (!player.isSleepingLongEnough()) {
					everyoneAsleep = false;
				}
			}
		}
		if (anyBunk && everyoneAsleep) {
			long day = level.getDayTime();
			level.setDayTime(day + 24000L - day % 24000L);
			for (ServerPlayer player : level.players()) {
				if (isInBunk(player)) {
					player.displayClientMessage(Component.translatable("message.railexpress.good_morning"), true);
				}
			}
			SLEEP_TICKS.clear();
		}
	}

	/** Progression du sommeil d'un joueur (0 à 1), pour l'assombrissement de l'écran. */
	public static float progress(UUID player) {
		return Math.min(1.0F, SLEEP_TICKS.getOrDefault(player, 0) / 100.0F);
	}
}
