package fr.plume.railexpress.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Tous les véhicules ferroviaires du mod et leurs caractéristiques.
 * Les longueurs sont en blocs, les vitesses en blocs par tick (1 b/t = 72 km/h).
 */
public enum CarType {
	STEAM_LOCOMOTIVE("steam_locomotive", Power.STEAM, 8.5, 9.0, 0.075, 1.25, 0, new Vec3[]{new Vec3(0.0, 1.05, -2.7)}),
	TENDER("tender", Power.NONE, 5.5, 5.0, 0, 0, 9, new Vec3[0]),
	PASSENGER_COACH("passenger_coach", Power.NONE, 9.0, 3.5, 0, 0, 0, seats(9.0, 0.55, 1.05, 4)),
	FREIGHT_WAGON("freight_wagon", Power.NONE, 7.0, 4.0, 0, 0, 27, new Vec3[0]),
	TGV_POWER_CAR("tgv_power_car", Power.ELECTRIC, 10.0, 7.0, 0.11, 2.5, 0, new Vec3[]{new Vec3(0.0, 1.0, 3.2)}),
	TGV_CAR("tgv_car", Power.NONE, 9.0, 3.5, 0, 0, 0, seats(9.0, 0.5, 1.0, 5)),
	SHINKANSEN_HEAD("shinkansen_head", Power.ELECTRIC, 11.0, 6.5, 0.12, 2.75, 0, new Vec3[]{new Vec3(0.0, 1.0, 1.6), new Vec3(0.0, 1.0, -1.5)}),
	SHINKANSEN_CAR("shinkansen_car", Power.NONE, 10.0, 3.5, 0, 0, 0, seats(10.0, 0.5, 1.0, 5));

	public enum Power {
		NONE, STEAM, ELECTRIC
	}

	public final String id;
	public final Power power;
	public final double length;
	public final double mass;
	/** Force de traction à plein régime. */
	public final double tractiveForce;
	public final double maxSpeed;
	public final int inventorySize;
	public final Vec3[] seats;

	CarType(String id, Power power, double length, double mass, double tractiveForce, double maxSpeed, int inventorySize, Vec3[] seats) {
		this.id = id;
		this.power = power;
		this.length = length;
		this.mass = mass;
		this.tractiveForce = tractiveForce;
		this.maxSpeed = maxSpeed;
		this.inventorySize = inventorySize;
		this.seats = seats;
	}

	public boolean isLocomotive() {
		return power != Power.NONE;
	}

	/** Génère des places assises sur deux rangées, réparties sur la longueur de la voiture. */
	private static Vec3[] seats(double length, double side, double height, int rows) {
		Vec3[] result = new Vec3[rows * 2];
		double usable = length - 2.5;
		for (int i = 0; i < rows; i++) {
			double z = -usable / 2 + usable * (i + 0.5) / rows;
			result[i * 2] = new Vec3(side, height, z);
			result[i * 2 + 1] = new Vec3(-side, height, z);
		}
		return result;
	}
}
