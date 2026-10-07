package fr.plume.railexpress;

import fr.plume.railexpress.registry.ModBlocks;
import fr.plume.railexpress.registry.ModEntities;
import fr.plume.railexpress.registry.ModItems;
import fr.plume.railexpress.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RailExpress implements ModInitializer {
	public static final String MOD_ID = "railexpress";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModEntities.init();
		ModItems.init();
		ModMenus.init();
		LOGGER.info("Rail Express : en voiture !");
	}
}
