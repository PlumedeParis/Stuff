package fr.plume.railexpress;

import fr.plume.railexpress.network.RoutePayload;
import fr.plume.railexpress.registry.ModBlocks;
import fr.plume.railexpress.registry.ModEntities;
import fr.plume.railexpress.registry.ModItems;
import fr.plume.railexpress.registry.ModMenus;
import fr.plume.railexpress.worldgen.RailwayFeature;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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
		RoutePayload.register();
		// Réseau ferroviaire généré dans l'Overworld
		Registry.register(BuiltInRegistries.FEATURE, id("railway"), new RailwayFeature(NoneFeatureConfiguration.CODEC));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES,
				ResourceKey.create(Registries.PLACED_FEATURE, id("railway")));
		LOGGER.info("Rail Express : en voiture !");
	}
}
