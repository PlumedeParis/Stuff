package fr.plume.railexpress.entity;

import fr.plume.railexpress.menu.LocomotiveMenu;
import fr.plume.railexpress.registry.ModEntities;
import fr.plume.railexpress.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Un véhicule ferroviaire : locomotive (vapeur ou électrique) ou voiture/wagon.
 * Les véhicules peuvent être attelés entre eux pour former une rame ; la physique de toute la rame
 * est calculée par un seul véhicule « maître » (la locomotive au plus petit identifiant).
 */
public class TrainCarEntity extends Entity implements HasCustomInventoryScreen {
	private static final EntityDataAccessor<Integer> DATA_THROTTLE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_REVERSE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BRAKE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_LIVE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_STATION = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HURT = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_SEATS = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_DOORS = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);

	public static final int MAX_THROTTLE = 5;
	public static final int MAX_ENERGY = 4000;
	public static final int FUEL_SLOTS = 3;
	public static final int STATION_WAIT = 120;

	private final CarType carType;
	private final SimpleContainer inventory;
	private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

	// --- Attelages ---
	@Nullable
	private UUID frontLink;
	@Nullable
	private UUID backLink;
	@Nullable
	private TrainCarEntity frontCache;
	@Nullable
	private TrainCarEntity backCache;

	// --- État de conduite (serveur) ---
	private Vec3 facing = new Vec3(0, 0, 1);
	/** Vitesse le long de {@link #facing}, en blocs par tick. */
	private double velocity;
	private int throttle;
	private boolean reverse;
	private boolean brake;
	private int burnTime;
	private int burnTimeMax = 1;
	private int energy;
	private boolean live;
	private int stationTimer;
	private int departGrace;
	private boolean approachingStation;
	private int consistSize = 1;
	private int inputCooldown;
	private int hornCooldown;
	private float damage;

	// --- Places, portes et coque ---
	private final java.util.Map<UUID, Integer> seatAssignments = new java.util.HashMap<>();
	private final java.util.Map<Integer, Integer> decodedSeats = new java.util.HashMap<>();
	private String decodedSeatsSource = "";
	private int pendingSeat = -1;
	private final List<TrainPartEntity> parts = new ArrayList<>();

	// --- Rendu (client) ---
	/** Ouverture animée de chaque porte (0 = fermée, 1 = ouverte). */
	public final float[] doorOpen = new float[16];
	public final float[] doorOpenO = new float[16];
	public float wheelRot;
	public float wheelRotO;

	public TrainCarEntity(EntityType<? extends TrainCarEntity> type, Level level, CarType carType) {
		super(type, level);
		this.carType = carType;
		this.inventory = new SimpleContainer(Math.max(1, carType.inventorySize));
	}

	public CarType getCarType() {
		return carType;
	}

	public SimpleContainer getInventory() {
		return inventory;
	}

	// ------------------------------------------------------------------
	// Données synchronisées & sauvegarde
	// ------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_THROTTLE, 0);
		builder.define(DATA_REVERSE, false);
		builder.define(DATA_BRAKE, false);
		builder.define(DATA_SPEED, 0.0F);
		builder.define(DATA_FUEL, 0);
		builder.define(DATA_LIVE, false);
		builder.define(DATA_STATION, 0);
		builder.define(DATA_HURT, 0);
		builder.define(DATA_SEATS, "");
		builder.define(DATA_DOORS, 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.throttle = input.getIntOr("Throttle", 0);
		this.reverse = input.getBooleanOr("Reverse", false);
		this.brake = input.getBooleanOr("Brake", false);
		this.burnTime = input.getIntOr("BurnTime", 0);
		this.burnTimeMax = Math.max(1, input.getIntOr("BurnTimeMax", 1));
		this.energy = input.getIntOr("Energy", 0);
		this.entityData.set(DATA_DOORS, input.getIntOr("Doors", 0));
		this.velocity = input.getDoubleOr("Velocity", 0.0);
		this.frontLink = input.read("FrontLink", UUIDUtil.CODEC).orElse(null);
		this.backLink = input.read("BackLink", UUIDUtil.CODEC).orElse(null);
		this.inventory.clearContent();
		net.minecraft.world.ContainerHelper.loadAllItems(input, this.inventory.getItems());
		this.facing = Vec3.directionFromRotation(0.0F, this.getYRot());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("Throttle", throttle);
		output.putBoolean("Reverse", reverse);
		output.putBoolean("Brake", brake);
		output.putInt("BurnTime", burnTime);
		output.putInt("BurnTimeMax", burnTimeMax);
		output.putInt("Energy", energy);
		output.putInt("Doors", this.entityData.get(DATA_DOORS));
		output.putDouble("Velocity", velocity);
		if (frontLink != null) {
			output.store("FrontLink", UUIDUtil.CODEC, frontLink);
		}
		if (backLink != null) {
			output.store("BackLink", UUIDUtil.CODEC, backLink);
		}
		net.minecraft.world.ContainerHelper.saveAllItems(output, this.inventory.getItems());
	}

	// Accesseurs utilisés par le rendu, le HUD et l'interface
	public int getThrottle() {
		return this.entityData.get(DATA_THROTTLE);
	}

	public boolean isReversed() {
		return this.entityData.get(DATA_REVERSE);
	}

	public boolean isBraking() {
		return this.entityData.get(DATA_BRAKE);
	}

	/** Vitesse absolue en blocs par tick. */
	public float getSpeed() {
		return this.entityData.get(DATA_SPEED);
	}

	/** Réserve d'énergie ou de combustible, en pour mille. */
	public int getFuelLevel() {
		return this.entityData.get(DATA_FUEL);
	}

	public boolean isLive() {
		return this.entityData.get(DATA_LIVE);
	}

	public int getStationWait() {
		return this.entityData.get(DATA_STATION);
	}

	public int getHurtTicks() {
		return this.entityData.get(DATA_HURT);
	}

	// ------------------------------------------------------------------
	// Comportement de base de l'entité
	// ------------------------------------------------------------------

	public InterpolationHandler getInterpolation() {
		return interpolation;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return true;
		}
		if (!(source.getEntity() instanceof Player player)) {
			return false;
		}
		// Un joueur à bord ne casse pas son train par mégarde (sauf accroupi)
		if (!player.isShiftKeyDown()) {
			Vec3 local = toLocal(player.position());
			boolean inside = Math.abs(local.x) < CarLayout.HALF_WIDTH + 0.2 && Math.abs(local.z) < carType.length / 2 && local.y > -0.5 && local.y < carType.height;
			if (player.getVehicle() == this || inside) {
				return false;
			}
		}
		if (player.getAbilities().instabuild) {
			destroy(level, false);
			return true;
		}
		damage += amount;
		this.entityData.set(DATA_HURT, 10);
		if (damage > 12.0F) {
			destroy(level, true);
		}
		return true;
	}

	private void destroy(ServerLevel level, boolean drop) {
		uncoupleAll();
		if (drop) {
			this.spawnAtLocation(level, new ItemStack(ModItems.carItem(carType)));
		}
		Containers.dropContents(level, this, inventory);
		this.discard();
	}

	public ItemStack getPickResult() {
		return new ItemStack(ModItems.carItem(carType));
	}

	public AABB getBoundingBoxForCulling() {
		return this.getBoundingBox().inflate(carType.length / 2.0 + 1.0, 2.0, carType.length / 2.0 + 1.0);
	}

	// ------------------------------------------------------------------
	// Repère local du véhicule
	// ------------------------------------------------------------------

	/** Convertit une position monde en coordonnées locales du véhicule. */
	public Vec3 toLocal(Vec3 world) {
		return world.subtract(this.position()).yRot(this.getYRot() * ((float) Math.PI / 180.0F));
	}

	public Vec3 localToWorld(Vec3 local) {
		return this.position().add(local.yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
	}

	/** Boîte englobante monde d'une boîte locale. */
	public AABB worldBox(CarLayout.Box b) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			Vec3 p = localToWorld(new Vec3((i & 1) == 0 ? b.x0() : b.x1(), (i & 2) == 0 ? b.y0() : b.y1(), (i & 4) == 0 ? b.z0() : b.z1()));
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			minZ = Math.min(minZ, p.z);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
			maxZ = Math.max(maxZ, p.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** Vrai si le véhicule est orienté selon un axe (les parois deviennent alors solides). */
	public boolean isAxisAligned() {
		float yaw = ((this.getYRot() % 90.0F) + 90.0F) % 90.0F;
		return (yaw < 4.0F || yaw > 86.0F) && Math.abs(this.getXRot()) < 5.0F;
	}

	// ------------------------------------------------------------------
	// Parties de coque (sélection et collisions)
	// ------------------------------------------------------------------

	private void manageParts() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		int expected = carType.layout().pieces.size();
		boolean broken = parts.size() != expected;
		for (TrainPartEntity part : parts) {
			if (part.isRemoved()) {
				broken = true;
			}
		}
		if (broken) {
			for (TrainPartEntity part : parts) {
				part.discard();
			}
			parts.clear();
			for (int i = 0; i < expected; i++) {
				TrainPartEntity part = new TrainPartEntity(ModEntities.PART, serverLevel);
				part.setup(this, i);
				serverLevel.addFreshEntity(part);
				parts.add(part);
			}
		}
	}

	void refreshParts() {
		for (TrainPartEntity part : parts) {
			part.refresh(this);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		for (TrainPartEntity part : parts) {
			part.discard();
		}
		parts.clear();
	}

	// ------------------------------------------------------------------
	// Places assises et couchettes
	// ------------------------------------------------------------------

	private java.util.Map<Integer, Integer> seatsByEntityId() {
		String encoded = this.entityData.get(DATA_SEATS);
		if (!encoded.equals(decodedSeatsSource)) {
			decodedSeats.clear();
			if (!encoded.isEmpty()) {
				for (String entry : encoded.split(";")) {
					String[] kv = entry.split(":");
					if (kv.length == 2) {
						try {
							decodedSeats.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
			decodedSeatsSource = encoded;
		}
		return decodedSeats;
	}

	private void syncSeats() {
		StringBuilder sb = new StringBuilder();
		for (Entity passenger : this.getPassengers()) {
			Integer seat = seatAssignments.get(passenger.getUUID());
			if (seat != null) {
				if (!sb.isEmpty()) {
					sb.append(';');
				}
				sb.append(passenger.getId()).append(':').append(seat);
			}
		}
		this.entityData.set(DATA_SEATS, sb.toString());
	}

	/** Index de la place occupée par ce passager, ou -1. */
	public int seatOf(Entity passenger) {
		if (this.level().isClientSide()) {
			return seatsByEntityId().getOrDefault(passenger.getId(), -1);
		}
		return seatAssignments.getOrDefault(passenger.getUUID(), -1);
	}

	public CarLayout.Seat seatFor(Entity passenger) {
		int index = seatOf(passenger);
		List<CarLayout.Seat> seats = carType.layout().seats;
		return index >= 0 && index < seats.size() ? seats.get(index) : null;
	}

	private boolean seatTaken(int index) {
		return seatAssignments.containsValue(index);
	}

	private int firstFreeSeat(boolean allowBeds) {
		List<CarLayout.Seat> seats = carType.layout().seats;
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < seats.size(); i++) {
				boolean bed = seats.get(i).isBed();
				if (!seatTaken(i) && (pass == 0 ? seats.get(i).style() == CarLayout.SeatStyle.DRIVER || !bed && !carType.isLocomotive() : (allowBeds || !bed))) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Installe une entité à une place précise. */
	public boolean sitAt(Entity entity, int seat) {
		if (seat < 0 || seatTaken(seat)) {
			return false;
		}
		if (entity.getVehicle() == this) {
			seatAssignments.put(entity.getUUID(), seat);
			syncSeats();
			return true;
		}
		if (entity.isPassenger()) {
			entity.stopRiding();
		}
		pendingSeat = seat;
		boolean ok = entity.startRiding(this);
		pendingSeat = -1;
		return ok;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < carType.seatCount();
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!this.level().isClientSide()) {
			int seat = pendingSeat >= 0 && !seatTaken(pendingSeat) ? pendingSeat : firstFreeSeat(true);
			if (seat >= 0) {
				seatAssignments.put(passenger.getUUID(), seat);
			}
			syncSeats();
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!this.level().isClientSide()) {
			seatAssignments.remove(passenger.getUUID());
			syncSeats();
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
		CarLayout.Seat seat = seatFor(passenger);
		if (seat == null) {
			List<CarLayout.Seat> seats = carType.layout().seats;
			int index = Math.max(0, this.getPassengers().indexOf(passenger));
			seat = seats.isEmpty() ? null : seats.get(index % seats.size());
		}
		Vec3 local = seat == null ? new Vec3(0, 1.0, 0) : new Vec3(seat.x(), seat.y(), seat.z());
		return local.yRot(-this.getYRot() * ((float) Math.PI / 180.0F));
	}

	// ------------------------------------------------------------------
	// Portes
	// ------------------------------------------------------------------

	public boolean isDoorOpen(int index) {
		return (this.entityData.get(DATA_DOORS) & (1 << index)) != 0;
	}

	public int getDoorBits() {
		return this.entityData.get(DATA_DOORS);
	}

	public void toggleDoor(int index) {
		int bits = this.entityData.get(DATA_DOORS) ^ (1 << index);
		this.entityData.set(DATA_DOORS, bits);
		CarLayout.Door door = carType.layout().doors.get(index);
		Vec3 at = localToWorld(new Vec3(door.side() * CarLayout.HALF_WIDTH, 1.2, door.z()));
		this.level().playSound(null, at.x, at.y, at.z, (bits & (1 << index)) != 0 ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
				SoundSource.BLOCKS, 0.8F, 1.25F);
	}

	// ------------------------------------------------------------------
	// Interaction
	// ------------------------------------------------------------------

	private enum Target { NONE, SEAT, DOOR, STORAGE, CONTROL }

	private record Hit(Target target, int index) {
	}

	/** Lance un rayon depuis les yeux du joueur dans le repère du véhicule pour trouver l'élément visé. */
	private Hit raycast(Player player) {
		Vec3 eye = toLocal(player.getEyePosition());
		Vec3 look = player.getViewVector(1.0F).yRot(this.getYRot() * ((float) Math.PI / 180.0F));
		CarLayout layout = carType.layout();
		double best = 5.5;
		Hit hit = new Hit(Target.NONE, -1);
		for (int i = 0; i < layout.doors.size(); i++) {
			double t = layout.doors.get(i).box().clip(eye.x, eye.y, eye.z, look.x, look.y, look.z);
			if (t >= 0 && t < best) {
				best = t;
				hit = new Hit(Target.DOOR, i);
			}
		}
		for (int i = 0; i < layout.seats.size(); i++) {
			double t = layout.seats.get(i).box().clip(eye.x, eye.y, eye.z, look.x, look.y, look.z);
			if (t >= 0 && t < best) {
				best = t;
				hit = new Hit(Target.SEAT, i);
			}
		}
		for (int i = 0; i < layout.storages.size(); i++) {
			double t = layout.storages.get(i).clip(eye.x, eye.y, eye.z, look.x, look.y, look.z);
			if (t >= 0 && t < best) {
				best = t;
				hit = new Hit(Target.STORAGE, i);
			}
		}
		if (layout.control != null) {
			double t = layout.control.clip(eye.x, eye.y, eye.z, look.x, look.y, look.z);
			if (t >= 0 && t < best) {
				hit = new Hit(Target.CONTROL, 0);
			}
		}
		return hit;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(ModItems.COUPLER)) {
			if (player instanceof ServerPlayer serverPlayer) {
				Coupling.use(serverPlayer, this);
			}
			return InteractionResult.SUCCESS;
		}
		if (carType.power.burnsFuel() && fuelValue(carType.power, held) > 0) {
			if (!this.level().isClientSide()) {
				ItemStack rest = inventory.addItem(held.copy());
				if (rest.getCount() != held.getCount()) {
					if (!player.getAbilities().instabuild) {
						held.setCount(rest.getCount());
					}
					player.displayClientMessage(Component.translatable("message.railexpress.refuelled"), true);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (carType.layout == CarType.Layout.LIVESTOCK && boardLeashedAnimals(player)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			openCustomInventoryScreen(player);
			return InteractionResult.SUCCESS;
		}
		Hit hit = raycast(player);
		switch (hit.target()) {
			case DOOR -> {
				toggleDoor(hit.index());
				return InteractionResult.SUCCESS;
			}
			case STORAGE -> {
				openStorage(player);
				return InteractionResult.SUCCESS;
			}
			case CONTROL -> {
				openCustomInventoryScreen(player);
				return InteractionResult.SUCCESS;
			}
			case SEAT -> {
				CarLayout.Seat seat = carType.layout().seats.get(hit.index());
				if (seat.style() == CarLayout.SeatStyle.BENCH) {
					break;
				}
				if (seatTaken(hit.index())) {
					player.displayClientMessage(Component.translatable("message.railexpress.seat_taken"), true);
					return InteractionResult.SUCCESS;
				}
				if (sitAt(player, hit.index()) && seat.isBed()) {
					player.displayClientMessage(Component.translatable(TrainSleep.isNight(this.level())
							? "message.railexpress.sleeping" : "message.railexpress.resting"), true);
				}
				return InteractionResult.SUCCESS;
			}
			default -> {
			}
		}
		// Clic sur la coque : s'asseoir à une place libre, sinon ouvrir le chargement
		int free = firstFreeSeat(false);
		if (free >= 0 && !player.isPassenger() && carType.layout().seats.get(free).style() != CarLayout.SeatStyle.BENCH) {
			sitAt(player, free);
			return InteractionResult.SUCCESS;
		}
		if (carType.inventorySize > 0) {
			openCustomInventoryScreen(player);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Fait monter dans le wagon à bestiaux les animaux tenus en laisse par le joueur. */
	private boolean boardLeashedAnimals(Player player) {
		boolean any = false;
		for (Mob mob : this.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10.0), m -> m.getLeashHolder() == player)) {
			int seat = firstFreeSeat(false);
			if (seat < 0) {
				break;
			}
			mob.dropLeash();
			if (sitAt(mob, seat)) {
				any = true;
			}
		}
		if (any) {
			player.displayClientMessage(Component.translatable("message.railexpress.animals_boarded"), true);
		}
		return any;
	}

	private void openStorage(Player player) {
		if (carType.isLocomotive()) {
			openCustomInventoryScreen(player);
			return;
		}
		Component title = Component.translatable("entity.railexpress." + carType.id);
		int size = inventory.getContainerSize();
		if (size >= 54) {
			player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> ChestMenu.sixRows(id, playerInventory, this.inventory), title));
		} else if (size >= 27) {
			player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> ChestMenu.threeRows(id, playerInventory, this.inventory), title));
		} else if (size >= 9) {
			player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, playerInventory, this.inventory, 1), title));
		}
	}

	@Override
	public void openCustomInventoryScreen(Player player) {
		if (this.level().isClientSide()) {
			return;
		}
		if (carType.isLocomotive()) {
			Component title = Component.translatable("entity.railexpress." + carType.id);
			player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> new LocomotiveMenu(id, playerInventory, this.inventory, this.menuData, this), title));
		} else {
			openStorage(player);
		}
	}

	/** Valeur énergétique d'un combustible pour ce type de traction, en ticks. */
	public static int fuelValue(CarType.Power power, ItemStack stack) {
		if (power == CarType.Power.DIESEL && stack.is(ModItems.FUEL_CANISTER)) {
			return 12000;
		}
		if (stack.is(Items.COAL_BLOCK)) {
			return 24000;
		}
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return power == CarType.Power.DIESEL ? 1200 : 2400;
		}
		if (power == CarType.Power.DIESEL && stack.is(Items.BLAZE_ROD)) {
			return 3000;
		}
		return 0;
	}

	public static boolean isFuel(ItemStack stack) {
		return fuelValue(CarType.Power.STEAM, stack) > 0 || fuelValue(CarType.Power.DIESEL, stack) > 0;
	}

	// ------------------------------------------------------------------
	// Commandes de conduite
	// ------------------------------------------------------------------

	/** Applique une commande à cette locomotive et la recopie sur toutes les locomotives de la rame. */
	public void command(int newThrottle, boolean newReverse, boolean newBrake) {
		newThrottle = Math.max(0, Math.min(MAX_THROTTLE, newThrottle));
		List<TrainCarEntity> cars = buildConsist();
		Vec3[] tf = towardFront(cars);
		int me = cars.indexOf(this);
		double myOrientation = Math.signum(this.facing.dot(tf[me]));
		for (int i = 0; i < cars.size(); i++) {
			TrainCarEntity car = cars.get(i);
			if (!car.carType.isLocomotive()) {
				continue;
			}
			boolean sameWay = Math.signum(car.facing.dot(tf[i])) == myOrientation;
			car.throttle = newThrottle;
			car.reverse = sameWay ? newReverse : !newReverse;
			car.brake = newBrake;
			car.syncControls();
		}
	}

	/** Boutons de l'interface de conduite. */
	public void handleButton(int id) {
		switch (id) {
			case 0 -> command(throttle - 1, reverse, false);
			case 1 -> command(throttle + 1, reverse, false);
			case 2 -> command(0, reverse, !brake);
			case 3 -> {
				if (Math.abs(velocity) < 0.05) {
					command(throttle, !reverse, brake);
				}
			}
			case 4 -> horn();
			default -> {
				if (id >= 10 && id <= 10 + MAX_THROTTLE) {
					command(id - 10, reverse, false);
				}
			}
		}
	}

	public void horn() {
		if (hornCooldown > 0) {
			return;
		}
		hornCooldown = 30;
		if (carType.power == CarType.Power.STEAM) {
			this.level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 4.0F, 0.94F);
			this.level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 4.0F, 1.19F);
			this.level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 4.0F, 1.41F);
		} else {
			this.level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.NEUTRAL, 4.0F, 1.6F);
			this.level().playSound(null, getX(), getY() + 2, getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 2.0F, 0.84F);
		}
	}

	private void syncControls() {
		this.entityData.set(DATA_THROTTLE, throttle);
		this.entityData.set(DATA_REVERSE, reverse);
		this.entityData.set(DATA_BRAKE, brake);
	}

	/** Données affichées par l'interface de conduite (synchronisées par le menu). */
	public final ContainerData menuData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> Math.round((float) Math.abs(velocity) * 72.0F);
				case 1 -> throttle;
				case 2 -> reverse ? 1 : 0;
				case 3 -> brake ? 1 : 0;
				case 4 -> burnTime;
				case 5 -> burnTimeMax;
				case 6 -> energy;
				case 7 -> MAX_ENERGY;
				case 8 -> carType.power.ordinal();
				case 9 -> live ? 1 : 0;
				case 10 -> consistSize;
				case 11 -> stationTimer;
				case 12 -> Math.min(32000, countTenderFuel());
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return LocomotiveMenu.DATA_COUNT;
		}
	};

	private int countTenderFuel() {
		if (this.level().isClientSide()) {
			return 0;
		}
		int total = 0;
		for (TrainCarEntity car : buildConsist()) {
			if (car.carType == CarType.TENDER) {
				for (ItemStack stack : car.inventory.getItems()) {
					if (fuelValue(CarType.Power.STEAM, stack) > 0) {
						total += stack.getCount();
					}
				}
			}
		}
		return total;
	}

	// ------------------------------------------------------------------
	// Attelages
	// ------------------------------------------------------------------

	@Nullable
	private TrainCarEntity resolve(@Nullable UUID id, boolean front) {
		if (id == null || !(this.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		TrainCarEntity cached = front ? frontCache : backCache;
		if (cached == null || cached.isRemoved() || !cached.getUUID().equals(id)) {
			cached = serverLevel.getEntity(id) instanceof TrainCarEntity car ? car : null;
			if (front) {
				frontCache = cached;
			} else {
				backCache = cached;
			}
		}
		if (cached != null && cached.isRemoved()) {
			return null;
		}
		// Le lien doit être réciproque
		if (cached != null && !this.getUUID().equals(cached.frontLink) && !this.getUUID().equals(cached.backLink)) {
			return null;
		}
		return cached;
	}

	public List<TrainCarEntity> neighbours() {
		List<TrainCarEntity> list = new ArrayList<>(2);
		TrainCarEntity f = resolve(frontLink, true);
		TrainCarEntity b = resolve(backLink, false);
		if (f != null) {
			list.add(f);
		}
		if (b != null && b != f) {
			list.add(b);
		}
		return list;
	}

	public boolean hasFreeEnd(boolean front) {
		return front ? frontLink == null : backLink == null;
	}

	/** Vrai si l'extrémité avant de ce véhicule est la plus proche de {@code other}. */
	public boolean frontFaces(Entity other) {
		return other.position().subtract(this.position()).dot(facing) >= 0;
	}

	void setLink(boolean front, @Nullable UUID id) {
		if (front) {
			frontLink = id;
			frontCache = null;
		} else {
			backLink = id;
			backCache = null;
		}
	}

	public void uncoupleAll() {
		for (TrainCarEntity other : neighbours()) {
			if (this.getUUID().equals(other.frontLink)) {
				other.setLink(true, null);
			}
			if (this.getUUID().equals(other.backLink)) {
				other.setLink(false, null);
			}
		}
		setLink(true, null);
		setLink(false, null);
	}

	private void uncouple(TrainCarEntity other) {
		if (other.getUUID().equals(frontLink)) {
			setLink(true, null);
		}
		if (other.getUUID().equals(backLink)) {
			setLink(false, null);
		}
		if (this.getUUID().equals(other.frontLink)) {
			other.setLink(true, null);
		}
		if (this.getUUID().equals(other.backLink)) {
			other.setLink(false, null);
		}
	}

	/** Liste ordonnée des véhicules de la rame, d'une extrémité à l'autre. */
	public List<TrainCarEntity> buildConsist() {
		Set<TrainCarEntity> seen = new HashSet<>();
		TrainCarEntity prev = null;
		TrainCarEntity end = this;
		seen.add(this);
		while (seen.size() < 64) {
			TrainCarEntity next = null;
			for (TrainCarEntity n : end.neighbours()) {
				if (n != prev && !seen.contains(n)) {
					next = n;
				}
			}
			if (next == null) {
				break;
			}
			seen.add(next);
			prev = end;
			end = next;
		}
		List<TrainCarEntity> cars = new ArrayList<>();
		Set<TrainCarEntity> added = new HashSet<>();
		TrainCarEntity cur = end;
		while (cur != null && added.size() < 64) {
			cars.add(cur);
			added.add(cur);
			TrainCarEntity next = null;
			for (TrainCarEntity n : cur.neighbours()) {
				if (!added.contains(n)) {
					next = n;
				}
			}
			cur = next;
		}
		if (!cars.contains(this)) {
			cars.clear();
			cars.add(this);
		}
		return cars;
	}

	/** Pour chaque véhicule, direction horizontale pointant vers la tête de liste (index 0). */
	private static Vec3[] towardFront(List<TrainCarEntity> cars) {
		int n = cars.size();
		Vec3[] result = new Vec3[n];
		if (n == 1) {
			result[0] = cars.get(0).facing;
			return result;
		}
		for (int i = 0; i < n; i++) {
			Vec3 delta = i == 0
					? cars.get(0).position().subtract(cars.get(1).position())
					: cars.get(i - 1).position().subtract(cars.get(i).position());
			result[i] = TrackWalker.horizontal(delta);
		}
		return result;
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			clientTick();
			return;
		}
		if (this.entityData.get(DATA_HURT) > 0) {
			this.entityData.set(DATA_HURT, this.entityData.get(DATA_HURT) - 1);
		} else if (damage > 0) {
			damage = Math.max(0, damage - 0.05F);
		}
		if (hornCooldown > 0) {
			hornCooldown--;
		}
		manageParts();
		refreshParts();
		if (getSpeed() > 0.08F && this.entityData.get(DATA_DOORS) != 0) {
			// Fermeture automatique des portes au départ
			for (int i = 0; i < carType.layout().doors.size(); i++) {
				if (isDoorOpen(i)) {
					toggleDoor(i);
				}
			}
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			TrainSleep.tick(serverLevel);
		}
		handleDriverInput();

		List<TrainCarEntity> cars = buildConsist();
		consistSize = cars.size();
		TrainCarEntity master = null;
		for (TrainCarEntity car : cars) {
			if (master == null
					|| (car.carType.isLocomotive() && !master.carType.isLocomotive())
					|| (car.carType.isLocomotive() == master.carType.isLocomotive() && car.getId() < master.getId())) {
				master = car;
			}
		}
		if (master == this) {
			tickTrain(cars);
		}
	}

	private void handleDriverInput() {
		if (!carType.isLocomotive()) {
			return;
		}
		if (inputCooldown > 0) {
			inputCooldown--;
			return;
		}
		ServerPlayer driver = null;
		for (Entity passenger : this.getPassengers()) {
			CarLayout.Seat seat = seatFor(passenger);
			if (passenger instanceof ServerPlayer player && seat != null && seat.style() == CarLayout.SeatStyle.DRIVER) {
				driver = player;
			}
		}
		if (driver == null) {
			return;
		}
		Input input = driver.getLastClientInput();
		if (input.forward()) {
			command(throttle + 1, reverse, false);
			inputCooldown = 8;
		} else if (input.backward()) {
			if (throttle > 0) {
				command(throttle - 1, reverse, false);
			} else {
				command(0, reverse, true);
			}
			inputCooldown = 8;
		} else if (input.jump()) {
			horn();
			inputCooldown = 8;
		}
	}

	private boolean consumePower(List<TrainCarEntity> cars) {
		if (carType.power == CarType.Power.ELECTRIC) {
			int cost = 1 + throttle * 2;
			if (energy >= cost) {
				energy -= cost;
				return true;
			}
			return false;
		}
		if (carType.power.burnsFuel()) {
			if (burnTime <= 0) {
				refuel(cars);
			}
			if (burnTime > 0) {
				burnTime -= throttle >= 4 ? 2 : 1;
				return true;
			}
		}
		return false;
	}

	private void refuel(List<TrainCarEntity> cars) {
		if (takeFuelFrom(this.inventory)) {
			return;
		}
		for (TrainCarEntity car : cars) {
			if (car.carType == CarType.TENDER && takeFuelFrom(car.inventory)) {
				return;
			}
		}
	}

	private boolean takeFuelFrom(SimpleContainer container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			int value = fuelValue(carType.power, stack);
			if (value > 0) {
				stack.shrink(1);
				container.setChanged();
				burnTime = value;
				burnTimeMax = value;
				return true;
			}
		}
		return false;
	}

	/** Simulation de toute la rame : uniquement exécutée par le véhicule maître. */
	private void tickTrain(List<TrainCarEntity> cars) {
		Level level = this.level();
		int n = cars.size();
		if (n > 1) {
			Vec3[] tf0 = towardFront(cars);
			if (this.facing.dot(tf0[cars.indexOf(this)]) < 0) {
				Collections.reverse(cars);
			}
		}
		Vec3[] tf = towardFront(cars);
		double v = this.velocity * Math.signum(this.facing.dot(tf[cars.indexOf(this)]) == 0 ? 1 : this.facing.dot(tf[cars.indexOf(this)]));

		// Caténaire : la rame est alimentée si l'un de ses véhicules est sur une voie sous tension
		boolean trainLive = false;
		for (TrainCarEntity car : cars) {
			if (TrackWalker.isLiveWire(level, car.position())) {
				trainLive = true;
				break;
			}
		}

		double mass = 0;
		double force = 0;
		double maxSpeed = Double.MAX_VALUE;
		boolean anyLoco = false;
		boolean braking = false;
		boolean stationStop = false;
		for (int i = 0; i < n; i++) {
			TrainCarEntity car = cars.get(i);
			mass += car.carType.mass + car.cargoMass();
			car.live = trainLive;
			if (!car.carType.isLocomotive()) {
				continue;
			}
			anyLoco = true;
			maxSpeed = Math.min(maxSpeed, car.carType.maxSpeed);
			braking |= car.brake;
			if (car.carType.power == CarType.Power.ELECTRIC && trainLive) {
				car.energy = Math.min(MAX_ENERGY, car.energy + 60);
			}
			if (car.throttle > 0 && !car.brake && car.consumePower(cars)) {
				double sign = Math.signum(car.facing.dot(tf[i])) * (car.reverse ? -1 : 1);
				force += sign * car.carType.tractiveForce * car.throttle / (double) MAX_THROTTLE;
			}
			if (car.departGrace > 0) {
				car.departGrace--;
			} else if (TrackWalker.isStationStop(level, car.position())) {
				stationStop = true;
			}
		}
		if (!anyLoco) {
			maxSpeed = 1.0;
		}
		double limit = maxSpeed;
		for (TrainCarEntity car : cars) {
			limit = Math.min(limit, TrackWalker.speedLimit(level, car.position()));
		}

		// Arrêt en gare : le train freine, s'immobilise, attend puis repart
		if (!stationStop) {
			approachingStation = false;
		} else if (stationTimer == 0 && Math.abs(v) > 0.03) {
			approachingStation = true;
		}
		if (approachingStation && stationTimer == 0 && Math.abs(v) < 0.02) {
			approachingStation = false;
			stationTimer = STATION_WAIT;
			level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 2.0F, 1.2F);
		}
		if (stationTimer > 0) {
			stationTimer--;
			v = 0;
			force = 0;
			if (stationTimer == 0) {
				for (TrainCarEntity car : cars) {
					car.departGrace = 100;
				}
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 2.0F, 0.9F);
			}
		}

		// Dynamique : traction, résistance à l'avancement et freinage
		v += force / mass;
		double resistance = 0.0005 + Math.abs(v) * 0.0008;
		if (braking) {
			resistance += 0.04;
		}
		if (approachingStation) {
			resistance += 0.12;
		}
		if (Math.abs(v) <= resistance) {
			v = Math.abs(force) > resistance ? v : 0;
		} else {
			v -= Math.signum(v) * resistance;
		}
		if (Math.abs(v) > limit) {
			v = Math.signum(v) * Math.max(limit, Math.abs(v) - 0.06);
		}

		// Joueurs debout à l'intérieur : ils seront transportés avec leur véhicule
		List<Object[]> riders = new ArrayList<>();
		if (Math.abs(v) > 1.0E-4) {
			for (TrainCarEntity car : cars) {
				CarLayout layout = car.carType.layout();
				if (!layout.enterable) {
					continue;
				}
				for (Player player : level.getEntitiesOfClass(Player.class, car.getBoundingBoxForCulling(), p -> !p.isPassenger() && !p.isSpectator())) {
					Vec3 local = car.toLocal(player.position());
					if (Math.abs(local.x) < CarLayout.HALF_WIDTH && local.z > layout.interiorZ0 - 0.3 && local.z < layout.interiorZ1 + 0.3
							&& local.y > CarLayout.FLOOR - 0.35 && local.y < CarLayout.FLOOR + 0.9) {
						riders.add(new Object[]{player, car, local});
					}
				}
			}
		}

		// Déplacement : le véhicule de tête avance, les autres suivent
		if (Math.abs(v) > 1.0E-4) {
			TrainCarEntity lead = v > 0 ? cars.get(0) : cars.get(n - 1);
			Vec3 outward = v > 0 ? tf[0] : tf[n - 1].reverse();
			TrackWalker.Result r = TrackWalker.walk(level, lead.position(), outward, Math.abs(v));
			lead.applyTrackPosition(r);
			if (r.blocked()) {
				v = 0;
			}
		}
		if (v >= 0) {
			for (int i = 1; i < n; i++) {
				follow(cars.get(i), cars.get(i - 1));
			}
		} else {
			for (int i = n - 2; i >= 0; i--) {
				follow(cars.get(i), cars.get(i + 1));
			}
		}

		// Mise à jour de l'orientation, de la vitesse et des données synchronisées de chaque véhicule
		Vec3[] tfNew = towardFront(cars);
		for (int i = 0; i < n; i++) {
			TrainCarEntity car = cars.get(i);
			double orientation = car.facing.dot(tfNew[i]) >= 0 ? 1 : -1;
			car.velocity = v * orientation;
			car.consistSize = n;
			car.stationTimer = this.stationTimer;
			car.updateBodyOrientation();
			car.entityData.set(DATA_SPEED, (float) Math.abs(v));
			car.entityData.set(DATA_LIVE, trainLive);
			car.entityData.set(DATA_STATION, this.stationTimer);
			car.entityData.set(DATA_FUEL, car.computeFuelLevel());
			car.refreshParts();
		}
		for (Object[] rider : riders) {
			Player player = (Player) rider[0];
			TrainCarEntity car = (TrainCarEntity) rider[1];
			Vec3 target = car.localToWorld((Vec3) rider[2]);
			Vec3 delta = target.subtract(player.position());
			if (delta.lengthSqr() > 1.0E-6 && delta.lengthSqr() < 36.0) {
				player.teleportRelative(delta.x, delta.y, delta.z);
			}
		}
	}

	private double cargoMass() {
		if (carType.inventorySize == 0) {
			return 0;
		}
		int count = 0;
		for (ItemStack stack : inventory.getItems()) {
			count += stack.getCount();
		}
		return count / 256.0;
	}

	private int computeFuelLevel() {
		if (carType.power == CarType.Power.ELECTRIC) {
			return energy * 1000 / MAX_ENERGY;
		}
		if (carType.power.burnsFuel()) {
			return burnTime <= 0 ? 0 : Math.max(1, burnTime * 1000 / Math.max(1, burnTimeMax));
		}
		return 0;
	}

	private void follow(TrainCarEntity follower, TrainCarEntity target) {
		double gap = (follower.carType.length + target.carType.length) / 2.0 + 0.4;
		for (int k = 0; k < 4; k++) {
			Vec3 delta = target.position().subtract(follower.position());
			double dist = delta.length();
			double error = dist - gap;
			if (Math.abs(error) < 0.005) {
				return;
			}
			if (dist > gap + 16) {
				follower.uncouple(target);
				return;
			}
			Vec3 dir = TrackWalker.horizontal(delta);
			if (error < 0) {
				dir = dir.reverse();
			}
			TrackWalker.Result r = TrackWalker.walk(this.level(), follower.position(), dir, Math.abs(error));
			follower.applyTrackPosition(r);
			if (r.blocked()) {
				return;
			}
		}
	}

	private void applyTrackPosition(TrackWalker.Result r) {
		Vec3 tangent = r.tangent();
		if (tangent.dot(facing) < 0) {
			tangent = tangent.reverse();
		}
		this.facing = tangent;
		this.setPos(r.pos());
	}

	/** Oriente la caisse selon la corde entre ses deux bogies (rendu réaliste dans les courbes). */
	private void updateBodyOrientation() {
		double half = carType.length * 0.36;
		TrackWalker.Result front = TrackWalker.walk(this.level(), this.position(), facing, half);
		TrackWalker.Result back = TrackWalker.walk(this.level(), this.position(), facing.reverse(), half);
		Vec3 body = front.pos().subtract(back.pos());
		double horizontal = Math.sqrt(body.x * body.x + body.z * body.z);
		if (horizontal < 0.2) {
			body = facing;
			horizontal = 1;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-body.x, body.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(body.y, horizontal));
		this.setYRot(yaw);
		this.setXRot(pitch);
	}

	/** Place le véhicule sur la voie lors de sa pose. */
	public void placeOnTrack(Vec3 pos, Vec3 direction) {
		this.facing = TrackWalker.horizontal(direction);
		this.setPos(pos);
		TrackWalker.Result r = TrackWalker.walk(this.level(), pos, this.facing, 0);
		applyTrackPosition(r);
		updateBodyOrientation();
	}

	// ------------------------------------------------------------------
	// Client : particules et animation des roues
	// ------------------------------------------------------------------

	private void clientTick() {
		interpolation.interpolate();
		Vec3 delta = this.position().subtract(this.xo, this.yo, this.zo);
		Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
		double moved = Math.sqrt(delta.x * delta.x + delta.z * delta.z) * (delta.dot(look) >= 0 ? 1 : -1);
		wheelRotO = wheelRot;
		wheelRot += (float) (moved / 0.45);

		// Animation des portes
		int doors = Math.min(doorOpen.length, carType.layout().doors.size());
		for (int i = 0; i < doors; i++) {
			doorOpenO[i] = doorOpen[i];
			float target = isDoorOpen(i) ? 1.0F : 0.0F;
			doorOpen[i] += Math.signum(target - doorOpen[i]) * Math.min(0.125F, Math.abs(target - doorOpen[i]));
		}

		float speed = getSpeed();
		if (carType.power == CarType.Power.STEAM && getFuelLevel() > 0) {
			Vec3 chimney = localToWorld(carType == CarType.ORIENT_EXPRESS_LOCOMOTIVE ? new Vec3(0, 3.15, 4.5) : new Vec3(0, 3.0, 3.6));
			int puffs = getThrottle() > 0 ? 2 : (this.random.nextInt(4) == 0 ? 1 : 0);
			for (int i = 0; i < puffs; i++) {
				this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, chimney.x, chimney.y, chimney.z,
						(this.random.nextDouble() - 0.5) * 0.02, 0.06 + speed * 0.02, (this.random.nextDouble() - 0.5) * 0.02);
			}
			if (getThrottle() > 0 && this.random.nextInt(3) == 0) {
				double z = carType == CarType.ORIENT_EXPRESS_LOCOMOTIVE ? 3.7 : 2.9;
				Vec3 steam = localToWorld(new Vec3(this.random.nextBoolean() ? 0.85 : -0.85, 0.7, z));
				this.level().addParticle(ParticleTypes.CLOUD, steam.x, steam.y, steam.z, 0, 0.02, 0);
			}
		}
		if (carType.power == CarType.Power.DIESEL && getFuelLevel() > 0 && this.random.nextInt(getThrottle() > 0 ? 1 : 5) == 0) {
			Vec3 exhaust = localToWorld(new Vec3((this.random.nextDouble() - 0.5) * 0.2, 3.05, 0.6));
			this.level().addParticle(getThrottle() > 2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, exhaust.x, exhaust.y, exhaust.z,
					0, 0.05 + getThrottle() * 0.01, 0);
		}
		if (carType.power == CarType.Power.ELECTRIC && isLive() && speed > 0.2F && this.random.nextInt(12) == 0) {
			Vec3 pantograph = localToWorld(new Vec3((this.random.nextDouble() - 0.5) * 1.2, 3.95, carType.pantographZ()));
			this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, pantograph.x, pantograph.y, pantograph.z, 0, 0.05, 0);
		}
	}
}
