package fr.plume.railexpress.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Morceau de coque d'un véhicule : permet de cliquer sur toute la longueur du train
 * et de marcher sur son plancher, entre ses parois. Sa position est entièrement déduite du véhicule parent.
 */
public class TrainPartEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_PARENT = SynchedEntityData.defineId(TrainPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PIECE = SynchedEntityData.defineId(TrainPartEntity.class, EntityDataSerializers.INT);

	private int orphanTicks;

	public TrainPartEntity(EntityType<? extends TrainPartEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setup(TrainCarEntity parent, int piece) {
		this.entityData.set(DATA_PARENT, parent.getId());
		this.entityData.set(DATA_PIECE, piece);
		refresh(parent);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_PARENT, -1);
		builder.define(DATA_PIECE, 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Nullable
	public TrainCarEntity getParent() {
		return this.level().getEntity(this.entityData.get(DATA_PARENT)) instanceof TrainCarEntity car ? car : null;
	}

	private CarLayout.Piece piece(TrainCarEntity parent) {
		if (parent == null) {
			return null;
		}
		int index = this.entityData.get(DATA_PIECE);
		var pieces = parent.getCarType().layout().pieces;
		return index >= 0 && index < pieces.size() ? pieces.get(index) : null;
	}

	/** Recalcule la position et la boîte à partir du véhicule parent. */
	public void refresh(TrainCarEntity parent) {
		CarLayout.Piece piece = piece(parent);
		if (piece == null) {
			return;
		}
		AABB box = parent.worldBox(piece.box());
		this.setPosRaw(box.getCenter().x, box.minY, box.getCenter().z);
		this.setBoundingBox(box);
	}

	@Override
	public void tick() {
		TrainCarEntity parent = getParent();
		if (parent == null || parent.isRemoved()) {
			if (!this.level().isClientSide() && ++orphanTicks > 40) {
				this.discard();
			}
			return;
		}
		orphanTicks = 0;
		refresh(parent);
	}

	@Override
	public boolean isPickable() {
		CarLayout.Piece piece = piece(getParent());
		return piece != null && piece.pickable();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		TrainCarEntity parent = getParent();
		CarLayout.Piece piece = piece(parent);
		if (piece == null || !piece.solid()) {
			return false;
		}
		if (other != null && (other.getVehicle() == parent || other instanceof TrainCarEntity || other instanceof TrainPartEntity)) {
			return false;
		}
		if (piece.axisOnly() && !parent.isAxisAligned()) {
			return false;
		}
		return piece.door() < 0 || !parent.isDoorOpen(piece.door());
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		TrainCarEntity parent = getParent();
		return parent != null && parent.hurtServer(level, source, amount);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		TrainCarEntity parent = getParent();
		return parent != null ? parent.interact(player, hand) : InteractionResult.PASS;
	}

	public ItemStack getPickResult() {
		TrainCarEntity parent = getParent();
		return parent != null ? parent.getPickResult() : ItemStack.EMPTY;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
