package fr.plume.railexpress.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.block.CrossingTrackBlock;
import fr.plume.railexpress.entity.TrackWalker;
import fr.plume.railexpress.entity.TrainCarEntity;
import fr.plume.railexpress.network.RoutePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Pilotage des croisements depuis le train : quand un croisement approche, un panneau s'affiche
 * en haut à droite et les flèches choisissent l'itinéraire (gauche / tout droit / droite).
 */
public final class RouteControl {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(RailExpress.id("controls"));
	public static final KeyMapping STRAIGHT = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.railexpress.route_straight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
	public static final KeyMapping LEFT = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.railexpress.route_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY));
	public static final KeyMapping RIGHT = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.railexpress.route_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY));

	private static TrackWalker.CrossingAhead ahead;
	private static int flash;

	private RouteControl() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RouteControl::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null || !(mc.player.getVehicle() instanceof TrainCarEntity car)) {
			ahead = null;
			return;
		}
		if (flash > 0) {
			flash--;
		}
		if (mc.player.tickCount % 2 == 0) {
			Vec3 delta = car.position().subtract(car.xo, car.yo, car.zo);
			Vec3 dir = delta.horizontalDistanceSqr() > 1.0E-4 ? delta
					: Vec3.directionFromRotation(0.0F, car.getYRot()).scale(car.isReversed() ? -1 : 1);
			double lookahead = Math.min(200.0, 30.0 + car.getSpeed() * 20.0 * 8.0) + car.getCarType().worldLength() / 2.0;
			ahead = TrackWalker.findCrossingAhead(mc.level, car.position(), dir, lookahead);
		}
		CrossingTrackBlock.Route chosen = null;
		while (STRAIGHT.consumeClick()) {
			chosen = CrossingTrackBlock.Route.STRAIGHT;
		}
		while (LEFT.consumeClick()) {
			chosen = CrossingTrackBlock.Route.LEFT;
		}
		while (RIGHT.consumeClick()) {
			chosen = CrossingTrackBlock.Route.RIGHT;
		}
		if (chosen != null && ahead != null && mc.getConnection() != null) {
			ClientPlayNetworking.send(new RoutePayload(ahead.pos(), chosen.ordinal()));
			flash = 10;
		}
	}

	public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (ahead == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		var state = mc.level.getBlockState(ahead.pos());
		if (!(state.getBlock() instanceof CrossingTrackBlock)) {
			return;
		}
		CrossingTrackBlock.Route route = state.getValue(CrossingTrackBlock.ROUTE);
		Font font = mc.font;
		int w = 150;
		int h = 64;
		int x = g.guiWidth() - w - 6;
		int y = 6;
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, flash > 0 ? 0xFFF2C14E : 0xC00B0D12);
		g.fillGradient(x, y, x + w, y + h, 0xE02A303C, 0xE01A1E26);
		g.fill(x, y, x + w, y + 2, 0xFFF2C14E);
		Component title = Component.translatable("gui.railexpress.crossing_ahead", Math.round(ahead.distance()));
		g.drawString(font, title, x + 6, y + 6, 0xFFF2C14E, false);
		String[] arrows = {"◀", "▲", "▶"};
		CrossingTrackBlock.Route[] order = {CrossingTrackBlock.Route.LEFT, CrossingTrackBlock.Route.STRAIGHT, CrossingTrackBlock.Route.RIGHT};
		String[] keys = {"gui.railexpress.route_left", "gui.railexpress.route_straight", "gui.railexpress.route_right"};
		for (int i = 0; i < 3; i++) {
			int bx = x + 6 + i * 47;
			int by = y + 19;
			boolean active = order[i] == route;
			g.fill(bx, by, bx + 44, by + 30, active ? 0xFF2E7D4A : 0xFF0D1015);
			g.fill(bx + 1, by + 1, bx + 43, by + 29, active ? 0xFF3DD68C : 0xFF2B313D);
			int color = active ? 0xFF0B2A17 : 0xFFB8C2D0;
			g.drawString(font, arrows[i], bx + 22 - font.width(arrows[i]) / 2, by + 5, color, false);
			Component label = Component.translatable(keys[i]);
			g.pose().pushMatrix();
			g.pose().translate(bx + 22, by + 18);
			g.pose().scale(0.75F, 0.75F);
			g.drawString(font, label, -font.width(label) / 2, 0, color, false);
			g.pose().popMatrix();
		}
		if (route != CrossingTrackBlock.Route.STRAIGHT) {
			g.pose().pushMatrix();
			g.pose().translate(x + 6, y + 53);
			g.pose().scale(0.75F, 0.75F);
			g.drawString(font, Component.translatable("gui.railexpress.turn_slow"), 0, 0, 0xFFE5A04D, false);
			g.pose().popMatrix();
		}
	}
}
