package fr.plume.railexpress.registry;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.item.TrainCarItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	private static final List<Item> TAB_ITEMS = new ArrayList<>();
	private static final Map<CarType, Item> CAR_ITEMS = new EnumMap<>(CarType.class);

	// Matériaux
	public static final Item STEEL_INGOT = register("steel_ingot", Item::new, new Item.Properties());
	public static final Item WHEEL_SET = register("wheel_set", Item::new, new Item.Properties());
	public static final Item BOILER = register("boiler", Item::new, new Item.Properties());
	public static final Item ELECTRIC_MOTOR = register("electric_motor", Item::new, new Item.Properties());
	public static final Item PANTOGRAPH = register("pantograph", Item::new, new Item.Properties());
	public static final Item TRAIN_SEAT = register("train_seat", Item::new, new Item.Properties());

	public static final Item FUEL_CANISTER = register("fuel_canister", Item::new, new Item.Properties().stacksTo(16));

	// Outils
	public static final Item COUPLER = register("coupler", Item::new, new Item.Properties().stacksTo(1));

	// Voies et blocs
	public static final Item TRACK = registerBlock(ModBlocks.TRACK);
	public static final Item HIGH_SPEED_TRACK = registerBlock(ModBlocks.HIGH_SPEED_TRACK);
	public static final Item ELECTRIFIED_TRACK = registerBlock(ModBlocks.ELECTRIFIED_TRACK);
	public static final Item STATION_TRACK = registerBlock(ModBlocks.STATION_TRACK);
	public static final Item SUBSTATION = registerBlock(ModBlocks.SUBSTATION);
	public static final Item CATENARY_MAST = registerBlock(ModBlocks.CATENARY_MAST);
	public static final Item BUFFER_STOP = registerBlock(ModBlocks.BUFFER_STOP);
	public static final Item PLATFORM = registerBlock(ModBlocks.PLATFORM);

	static {
		for (CarType carType : CarType.values()) {
			CAR_ITEMS.put(carType, register(carType.id, p -> new TrainCarItem(carType, p), new Item.Properties().stacksTo(1)));
		}
	}

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, RailExpress.id("rail_express"));

	private ModItems() {
	}

	public static Item carItem(CarType carType) {
		return CAR_ITEMS.get(carType);
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RailExpress.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		TAB_ITEMS.add(item);
		return item;
	}

	private static Item registerBlock(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
		TAB_ITEMS.add(item);
		return item;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.railexpress"))
				.icon(() -> new ItemStack(CAR_ITEMS.get(CarType.TGV_POWER_CAR)))
				.displayItems((context, entries) -> {
					for (CarType carType : CarType.values()) {
						entries.accept(CAR_ITEMS.get(carType));
					}
					for (Item item : TAB_ITEMS) {
						if (!(item instanceof TrainCarItem)) {
							entries.accept(item);
						}
					}
				})
				.build());
	}
}
