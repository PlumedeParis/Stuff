package fr.plume.railexpress.registry;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.menu.LocomotiveMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<LocomotiveMenu> LOCOMOTIVE = Registry.register(BuiltInRegistries.MENU,
			RailExpress.id("locomotive"), new MenuType<>(LocomotiveMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
	}
}
