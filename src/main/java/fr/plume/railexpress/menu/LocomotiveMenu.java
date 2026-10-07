package fr.plume.railexpress.menu;

import fr.plume.railexpress.entity.CarType;
import fr.plume.railexpress.entity.TrainCarEntity;
import fr.plume.railexpress.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Pupitre de conduite d'une locomotive. */
public class LocomotiveMenu extends AbstractContainerMenu {
	public static final int DATA_COUNT = 13;
	public static final int WIDTH = 214;
	public static final int HEIGHT = 222;
	public static final int FUEL_X = 166;
	public static final int FUEL_Y = 84;
	public static final int INV_X = (WIDTH - 162) / 2;
	public static final int INV_Y = 140;

	private final Container fuel;
	private final ContainerData data;
	@Nullable
	private final TrainCarEntity locomotive;

	/** Constructeur client. */
	public LocomotiveMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SimpleContainer(TrainCarEntity.FUEL_SLOTS), new SimpleContainerData(DATA_COUNT), null);
	}

	public LocomotiveMenu(int containerId, Inventory playerInventory, Container fuel, ContainerData data, @Nullable TrainCarEntity locomotive) {
		super(ModMenus.LOCOMOTIVE, containerId);
		this.fuel = fuel;
		this.data = data;
		this.locomotive = locomotive;
		int fuelSlots = Math.min(TrainCarEntity.FUEL_SLOTS, fuel.getContainerSize());
		for (int i = 0; i < fuelSlots; i++) {
			this.addSlot(new Slot(fuel, i, FUEL_X + i * 18 - 18, FUEL_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return isFuelPowered() && TrainCarEntity.isFuel(stack);
				}

				@Override
				public boolean isActive() {
					return isFuelPowered();
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + 1 + col * 18, INV_Y + 1 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, INV_X + 1 + col * 18, INV_Y + 59));
		}
		this.addDataSlots(data);
	}

	public int get(int index) {
		return data.get(index);
	}

	public boolean isSteam() {
		return data.get(8) == CarType.Power.STEAM.ordinal();
	}

	/** Locomotive à vapeur ou diesel (foyer / réservoir). */
	public boolean isFuelPowered() {
		return isSteam() || data.get(8) == CarType.Power.DIESEL.ordinal();
	}

	public boolean isDiesel() {
		return data.get(8) == CarType.Power.DIESEL.ordinal();
	}

	public boolean isElectric() {
		return data.get(8) == CarType.Power.ELECTRIC.ordinal();
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (locomotive != null && locomotive.isAlive()) {
			locomotive.handleButton(id);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int fuelSlots = Math.min(TrainCarEntity.FUEL_SLOTS, fuel.getContainerSize());
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < fuelSlots) {
			if (!this.moveItemStackTo(stack, fuelSlots, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (isFuelPowered() && TrainCarEntity.isFuel(stack)) {
			if (!this.moveItemStackTo(stack, 0, fuelSlots, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return locomotive == null || (locomotive.isAlive() && player.distanceToSqr(locomotive) < 144.0);
	}
}
