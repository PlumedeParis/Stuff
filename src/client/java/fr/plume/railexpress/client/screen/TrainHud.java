package fr.plume.railexpress.client.screen;

import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.entity.TrainCarEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Affichage tête haute lorsque le joueur est à bord d'un train. */
public final class TrainHud {
	private TrainHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof TrainCarEntity car)) {
			return;
		}
		Font font = mc.font;
		CarType type = car.getCarType();
		boolean driver = type.isLocomotive();
		int w = 170;
		int h = driver ? 70 : 34;
		int x = g.guiWidth() - w - 6;
		int y = g.guiHeight() - h - 6;

		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC00B0D12);
		g.fillGradient(x, y, x + w, y + h, 0xC02A303C, 0xC01A1E26);
		int accent = type.power == CarType.Power.STEAM ? 0xFF2F6B45 : (type.power == CarType.Power.ELECTRIC ? 0xFF2C55A8 : 0xFF6E1F22);
		g.fill(x, y, x + 3, y + h, accent);

		Component name = Component.translatable("entity.railexpress." + type.id);
		g.drawString(font, name, x + 8, y + 5, 0xFFB8C2D0, false);

		int kmh = Math.round(car.getSpeed() * 72.0F);
		String speed = String.valueOf(kmh);
		g.pose().pushMatrix();
		g.pose().translate(x + 8, y + 16);
		g.pose().scale(2.0F, 2.0F);
		g.drawString(font, speed, 0, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
		g.drawString(font, "km/h", x + 12 + font.width(speed) * 2, y + 23, 0xFF9AA6B8, false);

		// Statut
		Component status = null;
		int statusColor = 0xFFF2C14E;
		if (car.getStationWait() > 0) {
			status = Component.translatable("gui.railexpress.station", (car.getStationWait() + 19) / 20);
		} else if (car.isBraking()) {
			status = Component.translatable("gui.railexpress.brake_on");
			statusColor = 0xFFE5484D;
		} else if (type.power == CarType.Power.ELECTRIC) {
			status = Component.translatable(car.isLive() ? "gui.railexpress.live" : "gui.railexpress.dead");
			statusColor = car.isLive() ? 0xFF3DD68C : 0xFFE5484D;
		}
		if (status != null) {
			g.drawString(font, status, x + w - 6 - font.width(status), y + 5, statusColor, false);
		}

		if (!driver) {
			return;
		}
		// Traction
		int notch = car.getThrottle();
		for (int i = 0; i < TrainCarEntity.MAX_THROTTLE; i++) {
			int bx = x + 80 + i * 12;
			g.fill(bx, y + 18, bx + 10, y + 30, 0xFF0D1015);
			if (i < notch) {
				g.fillGradient(bx + 1, y + 19, bx + 9, y + 29, 0xFF5BE8A4, 0xFF2A9D63);
			}
		}
		g.drawString(font, Component.translatable(car.isReversed() ? "gui.railexpress.hud_backward" : "gui.railexpress.hud_forward"), x + 80, y + 33, 0xFFB8C2D0, false);
		// Énergie / combustible
		float level = car.getFuelLevel() / 1000.0F;
		int barColor = type.power == CarType.Power.STEAM ? 0xFFFF8A2B : 0xFF4FA3FF;
		g.fill(x + 8, y + 44, x + w - 6, y + 48, 0xFF0D1015);
		g.fill(x + 9, y + 45, x + 9 + (int) ((w - 16) * level), y + 47, barColor);
		g.pose().pushMatrix();
		g.pose().translate(x + 8, y + 52);
		g.pose().scale(0.75F, 0.75F);
		g.drawString(font, Component.translatable("gui.railexpress.hud_hint_1"), 0, 0, 0xFF8B96A8, false);
		g.drawString(font, Component.translatable("gui.railexpress.hud_hint_2"), 0, 11, 0xFF8B96A8, false);
		g.pose().popMatrix();
	}
}
