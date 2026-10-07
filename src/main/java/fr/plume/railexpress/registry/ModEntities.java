package fr.plume.railexpress.registry;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.entity.TrainCarEntity;
import fr.plume.railexpress.entity.TrainPartEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final Map<CarType, EntityType<TrainCarEntity>> TYPES = new EnumMap<>(CarType.class);
	private static final ResourceKey<EntityType<?>> PART_KEY = ResourceKey.create(Registries.ENTITY_TYPE, RailExpress.id("train_part"));
	public static final EntityType<TrainPartEntity> PART = Registry.register(BuiltInRegistries.ENTITY_TYPE, PART_KEY,
			EntityType.Builder.<TrainPartEntity>of(TrainPartEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.noSave()
					.noSummon()
					.clientTrackingRange(16)
					.updateInterval(200)
					.build(PART_KEY));

	static {
		for (CarType carType : CarType.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, RailExpress.id(carType.id));
			EntityType<TrainCarEntity> type = EntityType.Builder.<TrainCarEntity>of(
							(entityType, level) -> new TrainCarEntity(entityType, level, carType), MobCategory.MISC)
					.sized(1.2F, 2.0F)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(key);
			TYPES.put(carType, Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type));
		}
	}

	private ModEntities() {
	}

	public static EntityType<TrainCarEntity> get(CarType carType) {
		return TYPES.get(carType);
	}

	public static void init() {
	}
}
