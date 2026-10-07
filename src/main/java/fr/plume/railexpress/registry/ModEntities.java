package fr.plume.railexpress.registry;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.entity.TrainCarEntity;
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

	static {
		for (CarType carType : CarType.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, RailExpress.id(carType.id));
			EntityType<TrainCarEntity> type = EntityType.Builder.<TrainCarEntity>of(
							(entityType, level) -> new TrainCarEntity(entityType, level, carType), MobCategory.MISC)
					.sized(2.2F, 2.6F)
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
