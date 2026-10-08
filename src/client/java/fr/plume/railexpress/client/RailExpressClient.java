package fr.plume.railexpress.client;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.client.render.TrainCarRenderer;
import fr.plume.railexpress.client.render.TrainPartRenderer;
import fr.plume.railexpress.client.screen.LocomotiveScreen;
import fr.plume.railexpress.client.screen.RouteControl;
import fr.plume.railexpress.client.screen.TrainHud;
import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.item.TrainCarItem;
import fr.plume.railexpress.registry.ModBlocks;
import fr.plume.railexpress.registry.ModEntities;
import fr.plume.railexpress.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RailExpressClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (CarType carType : CarType.values()) {
			EntityRendererRegistry.register(ModEntities.get(carType), TrainCarRenderer::new);
		}
		EntityRendererRegistry.register(ModEntities.PART, TrainPartRenderer::new);
		MenuScreens.register(ModMenus.LOCOMOTIVE, LocomotiveScreen::new);
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT,
				ModBlocks.TRACK, ModBlocks.HIGH_SPEED_TRACK, ModBlocks.ELECTRIFIED_TRACK, ModBlocks.STATION_TRACK,
				ModBlocks.CATENARY_MAST, ModBlocks.BUFFER_STOP);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, RailExpress.id("train_hud"), TrainHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, RailExpress.id("route_control"), RouteControl::render);
		RouteControl.init();

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (!RailExpress.MOD_ID.equals(id.getNamespace())) {
				return;
			}
			String key = "tooltip.railexpress." + id.getPath();
			for (int i = 0; i < 3; i++) {
				String lineKey = key + "." + i;
				Component line = Component.translatable(lineKey);
				if (line.getString().equals(lineKey)) {
					break;
				}
				lines.add(line.copy().withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			}
			if (stack.getItem() instanceof TrainCarItem car) {
				CarType type = car.getCarType();
				if (type.isLocomotive()) {
					lines.add(Component.translatable("tooltip.railexpress.max_speed", Math.round(type.maxSpeed * 72)).withStyle(ChatFormatting.AQUA));
				}
				if (type.seatCount() > 0) {
					lines.add(Component.translatable("tooltip.railexpress.seats", type.seatCount()).withStyle(ChatFormatting.DARK_AQUA));
				}
				if (type.layout().enterable) {
					lines.add(Component.translatable("tooltip.railexpress.interior").withStyle(ChatFormatting.GRAY));
				}
			}
		});
	}
}
