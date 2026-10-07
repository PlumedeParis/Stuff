package fr.plume.railexpress.client.screen;

import fr.plume.railexpress.entity.TrainCarEntity;
import fr.plume.railexpress.menu.LocomotiveMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Pupitre de conduite : tachymètre, manipulateur de traction, réserve d'énergie et commandes. */
public class LocomotiveScreen extends AbstractContainerScreen<LocomotiveMenu> {
	private Button directionButton;
	private Button brakeButton;

	public LocomotiveScreen(LocomotiveMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = LocomotiveMenu.WIDTH;
		this.imageHeight = LocomotiveMenu.HEIGHT;
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos + 6;
		int y = topPos + 110;
		addRenderableWidget(Button.builder(Component.literal("▼"), b -> click(0)).bounds(x, y, 20, 20)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.railexpress.throttle_down"))).build());
		addRenderableWidget(Button.builder(Component.literal("▲"), b -> click(1)).bounds(x + 24, y, 20, 20)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.railexpress.throttle_up"))).build());
		brakeButton = addRenderableWidget(Button.builder(Component.translatable("gui.railexpress.brake"), b -> click(2)).bounds(x + 48, y, 44, 20).build());
		directionButton = addRenderableWidget(Button.builder(Component.translatable("gui.railexpress.forward"), b -> click(3)).bounds(x + 96, y, 52, 20)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.railexpress.direction_hint"))).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.railexpress.horn"), b -> click(4)).bounds(x + 152, y, 50, 20).build());
	}

	private void click(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		directionButton.setMessage(Component.translatable(menu.get(2) == 1 ? "gui.railexpress.backward" : "gui.railexpress.forward"));
		brakeButton.setMessage(Component.translatable(menu.get(3) == 1 ? "gui.railexpress.brake_on" : "gui.railexpress.brake"));
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		// Tous les textes sont dessinés dans renderBg.
	}

	// ------------------------------------------------------------------

	private int themeTop() {
		return menu.isSteam() ? 0xFF2F6B45 : 0xFF2C55A8;
	}

	private int themeBottom() {
		return menu.isSteam() ? 0xFF173A25 : 0xFF17305F;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		int w = imageWidth;
		int h = imageHeight;

		// Cadre et fond
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0B0D12);
		g.fillGradient(x, y, x + w, y + h, 0xFF2A303C, 0xFF1A1E26);
		g.fillGradient(x, y, x + w, y + 18, themeTop(), themeBottom());
		g.fill(x, y + 18, x + w, y + 19, 0xFFD2A93F);
		g.drawString(font, title, x + 7, y + 5, 0xFFFFFFFF, true);
		Component consist = Component.translatable("gui.railexpress.consist", menu.get(10));
		g.drawString(font, consist, x + w - 7 - font.width(consist), y + 5, 0xFFE8EEF5, true);

		// Panneaux
		panel(g, x + 6, y + 24, 80, 82);
		panel(g, x + 90, y + 24, 50, 82);
		panel(g, x + 144, y + 24, 64, 82);

		drawSpeedometer(g, x + 46, y + 72);
		drawThrottle(g, x + 90, y + 24);
		if (menu.isSteam()) {
			drawSteamPanel(g, x + 144, y + 24);
		} else {
			drawElectricPanel(g, x + 144, y + 24);
		}

		// Inventaire du joueur
		g.drawString(font, playerInventoryTitle, x + LocomotiveMenu.INV_X, y + LocomotiveMenu.INV_Y - 10, 0xFFB8C2D0, false);
		for (Slot slot : menu.slots) {
			if (slot.isActive()) {
				slotBackground(g, x + slot.x - 1, y + slot.y - 1);
			}
		}
	}

	private static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF0F1218);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF353C4A, 0xFF262C37);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
	}

	private static void slotBackground(GuiGraphics g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, 0xFF0D1015);
		g.fill(x + 1, y + 1, x + 18, y + 18, 0xFF4A5366);
		g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2B313D);
	}

	private void drawSpeedometer(GuiGraphics g, int cx, int cy) {
		int speed = menu.get(0);
		int max = menu.isSteam() ? 100 : 220;
		int radius = 32;
		// Graduations colorées
		for (int i = 0; i <= 60; i++) {
			float t = i / 60.0F;
			double a = Math.PI * (1 - t);
			int px = cx + (int) Math.round(Math.cos(a) * radius);
			int py = cy - (int) Math.round(Math.sin(a) * radius);
			int color = t < 0.6F ? 0xFF3DD68C : (t < 0.85F ? 0xFFF2C14E : 0xFFE5484D);
			boolean major = i % 10 == 0;
			g.fill(px - 1, py - 1, px + (major ? 2 : 1), py + (major ? 2 : 1), color);
			if (major) {
				int ix = cx + (int) Math.round(Math.cos(a) * (radius - 6));
				int iy = cy - (int) Math.round(Math.sin(a) * (radius - 6));
				g.fill(ix, iy, ix + 1, iy + 1, 0xFF9AA6B8);
			}
		}
		// Aiguille
		float t = Mth.clamp(speed / (float) max, 0, 1);
		double a = Math.PI * (1 - t);
		for (int r = 0; r < radius - 4; r++) {
			int px = cx + (int) Math.round(Math.cos(a) * r);
			int py = cy - (int) Math.round(Math.sin(a) * r);
			g.fill(px, py, px + 2, py + 2, 0xFFFF6B4A);
		}
		g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFD2A93F);
		// Valeur
		String value = String.valueOf(speed);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + 8);
		g.pose().scale(1.5F, 1.5F);
		g.drawString(font, value, -font.width(value) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
		Component unit = Component.literal("km/h");
		g.drawString(font, unit, cx - font.width(unit) / 2, cy + 22, 0xFF9AA6B8, false);
		g.drawString(font, "0", cx - radius - 2, cy + 3, 0xFF7D889A, false);
		String maxText = String.valueOf(max);
		g.drawString(font, maxText, cx + radius + 3 - font.width(maxText), cy + 3, 0xFF7D889A, false);
		int wait = menu.get(11);
		if (wait > 0) {
			Component station = Component.translatable("gui.railexpress.station", (wait + 19) / 20);
			g.drawString(font, station, cx - font.width(station) / 2, cy - 44, 0xFFF2C14E, true);
		}
	}

	private void drawThrottle(GuiGraphics g, int x, int y) {
		int notch = menu.get(1);
		Component label = Component.translatable("gui.railexpress.traction");
		g.drawString(font, label, x + 25 - font.width(label) / 2, y + 4, 0xFFB8C2D0, false);
		int railX = x + 22;
		int top = y + 16;
		int bottom = y + 74;
		g.fill(railX, top, railX + 6, bottom, 0xFF0D1015);
		g.fill(railX + 1, top + 1, railX + 5, bottom - 1, 0xFF1C2129);
		for (int i = 0; i <= TrainCarEntity.MAX_THROTTLE; i++) {
			int ny = bottom - 4 - i * (bottom - top - 8) / TrainCarEntity.MAX_THROTTLE;
			boolean active = i <= notch && notch > 0 && i > 0;
			g.fill(x + 8, ny, x + 18, ny + 1, active ? 0xFF3DD68C : 0xFF5A6476);
			g.drawString(font, String.valueOf(i), x + 33, ny - 3, i == notch ? 0xFFFFFFFF : 0xFF7D889A, false);
		}
		int hy = bottom - 4 - notch * (bottom - top - 8) / TrainCarEntity.MAX_THROTTLE;
		g.fill(railX - 4, hy - 3, railX + 10, hy + 4, 0xFF0D1015);
		g.fillGradient(railX - 3, hy - 2, railX + 9, hy + 3, menu.get(3) == 1 ? 0xFFE5484D : 0xFFF2C14E, menu.get(3) == 1 ? 0xFF9E2A2E : 0xFFB07F1E);
	}

	private void drawSteamPanel(GuiGraphics g, int x, int y) {
		Component label = Component.translatable("gui.railexpress.firebox");
		g.drawString(font, label, x + 32 - font.width(label) / 2, y + 4, 0xFFB8C2D0, false);
		int burn = menu.get(4);
		int burnMax = Math.max(1, menu.get(5));
		float level = Mth.clamp(burn / (float) burnMax, 0, 1);
		// Foyer : flammes animées proportionnelles au combustible restant
		int fx = x + 8;
		int fy = y + 16;
		g.fill(fx, fy, fx + 48, fy + 26, 0xFF120B08);
		long time = net.minecraft.util.Util.getMillis() / 120;
		if (burn > 0) {
			for (int i = 0; i < 11; i++) {
				int fh = (int) ((8 + ((i * 7 + time * 3 + i * time) % 13)) * (0.4F + level * 0.6F));
				int col = i % 3 == 0 ? 0xFFFFD45C : (i % 3 == 1 ? 0xFFFF8A2B : 0xFFE5482A);
				g.fillGradient(fx + 2 + i * 4, fy + 25 - Math.min(23, fh), fx + 5 + i * 4, fy + 25, col, 0xFF7A1C0C);
			}
		}
		// Jauge
		g.fill(fx, fy + 29, fx + 48, fy + 33, 0xFF0D1015);
		g.fill(fx + 1, fy + 30, fx + 1 + (int) (46 * level), fy + 32, 0xFFFF8A2B);
		int tender = menu.get(12);
		Component reserve = Component.translatable("gui.railexpress.tender", tender);
		g.drawString(font, reserve, x + 32 - font.width(reserve) / 2, y + 50, 0xFF9AA6B8, false);
	}

	private void drawElectricPanel(GuiGraphics g, int x, int y) {
		Component label = Component.translatable("gui.railexpress.energy");
		g.drawString(font, label, x + 32 - font.width(label) / 2, y + 4, 0xFFB8C2D0, false);
		int energy = menu.get(6);
		int max = Math.max(1, menu.get(7));
		float level = Mth.clamp(energy / (float) max, 0, 1);
		// Batterie verticale
		int bx = x + 10;
		int by = y + 18;
		int bh = 54;
		g.fill(bx + 5, by - 3, bx + 13, by, 0xFF5A6476);
		g.fill(bx, by, bx + 18, by + bh, 0xFF0D1015);
		int fill = (int) ((bh - 4) * level);
		int color = level > 0.5F ? 0xFF3DD68C : (level > 0.2F ? 0xFFF2C14E : 0xFFE5484D);
		g.fillGradient(bx + 2, by + bh - 2 - fill, bx + 16, by + bh - 2, color, darken(color));
		String pct = Math.round(level * 100) + "%";
		g.drawString(font, pct, bx + 9 - font.width(pct) / 2, by + bh + 2, 0xFFE8EEF5, false);
		// Témoin caténaire
		boolean live = menu.get(9) == 1;
		int lx = x + 36;
		int ly = y + 22;
		g.fill(lx, ly, lx + 22, ly + 22, 0xFF0D1015);
		g.fill(lx + 2, ly + 2, lx + 20, ly + 20, live ? 0xFF2ECC71 : 0xFF5A1D1D);
		if (live) {
			g.fill(lx + 5, ly + 5, lx + 17, ly + 17, 0xFF8CF5B5);
		}
		g.drawString(font, "⚡", lx + 8, ly + 7, live ? 0xFF103A20 : 0xFF2A0F0F, false);
		Component status = Component.translatable(live ? "gui.railexpress.live" : "gui.railexpress.dead");
		g.drawString(font, status, lx + 11 - font.width(status) / 2, ly + 26, live ? 0xFF3DD68C : 0xFFE5484D, false);
	}

	private static int darken(int argb) {
		int r = (int) (((argb >> 16) & 0xFF) * 0.6F);
		int gr = (int) (((argb >> 8) & 0xFF) * 0.6F);
		int b = (int) ((argb & 0xFF) * 0.6F);
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}
}
