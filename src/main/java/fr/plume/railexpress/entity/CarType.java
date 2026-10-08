package fr.plume.railexpress.entity;

/**
 * Tous les véhicules ferroviaires du mod et leurs caractéristiques.
 * Les longueurs sont en blocs, les vitesses en blocs par tick (1 b/t = 72 km/h).
 */
public enum CarType {
	// ---------------- Locomotives ----------------
	STEAM_LOCOMOTIVE(spec("steam_locomotive").power(Power.STEAM).length(8.5).mass(9).force(0.075).speed(1.25).inventory(3).layout(Layout.STEAM_CAB)),
	ORIENT_EXPRESS_LOCOMOTIVE(spec("orient_express_locomotive").power(Power.STEAM).length(10.5).mass(11).force(0.095).speed(1.5).inventory(3).layout(Layout.STEAM_CAB)),
	DIESEL_LOCOMOTIVE(spec("diesel_locomotive").power(Power.DIESEL).length(10.0).mass(10).force(0.11).speed(1.4).inventory(3).layout(Layout.DIESEL_CAB)),
	ELECTRIC_LOCOMOTIVE(spec("electric_locomotive").power(Power.ELECTRIC).length(9.5).mass(9).force(0.12).speed(2.2).inventory(3).layout(Layout.ELECTRIC_CAB)),
	TGV_POWER_CAR(spec("tgv_power_car").power(Power.ELECTRIC).length(10.0).mass(7).force(0.11).speed(2.5).inventory(3).layout(Layout.STREAMLINER_CAB)),
	TGV_ORANGE_POWER_CAR(spec("tgv_orange_power_car").power(Power.ELECTRIC).length(10.0).mass(7).force(0.1).speed(2.3).inventory(3).layout(Layout.STREAMLINER_CAB)),
	EUROSTAR_POWER_CAR(spec("eurostar_power_car").power(Power.ELECTRIC).length(10.5).mass(7).force(0.11).speed(2.5).inventory(3).layout(Layout.STREAMLINER_CAB)),
	ICE_POWER_CAR(spec("ice_power_car").power(Power.ELECTRIC).length(10.5).mass(7).force(0.115).speed(2.6).inventory(3).layout(Layout.STREAMLINER_CAB)),
	SHINKANSEN_HEAD(spec("shinkansen_head").power(Power.ELECTRIC).length(11.0).mass(6.5).force(0.12).speed(2.75).inventory(3).layout(Layout.STREAMLINER_CAB)),

	// ---------------- Voitures voyageurs ----------------
	PASSENGER_COACH(spec("passenger_coach").length(9.0).inventory(27).layout(Layout.SEATS)),
	SLEEPER_CAR(spec("sleeper_car").length(9.5).inventory(27).layout(Layout.SLEEPER)),
	DINING_CAR(spec("dining_car").length(9.5).inventory(27).layout(Layout.DINING)),
	LOUNGE_CAR(spec("lounge_car").length(9.0).inventory(27).layout(Layout.LOUNGE)),
	LUXURY_CAR(spec("luxury_car").length(9.5).inventory(27).layout(Layout.FIRST_CLASS)),
	OBSERVATION_CAR(spec("observation_car").length(9.0).inventory(27).layout(Layout.OBSERVATION)),
	BAGGAGE_CAR(spec("baggage_car").length(8.0).inventory(54).layout(Layout.BAGGAGE)),
	POST_CAR(spec("post_car").length(8.5).inventory(54).layout(Layout.POST)),
	ORIENT_EXPRESS_SLEEPER(spec("orient_express_sleeper").length(10.0).inventory(27).layout(Layout.SLEEPER)),
	ORIENT_EXPRESS_DINING(spec("orient_express_dining").length(10.0).inventory(27).layout(Layout.DINING)),
	ORIENT_EXPRESS_SALON(spec("orient_express_salon").length(10.0).inventory(27).layout(Layout.SALON)),
	ORIENT_EXPRESS_BAGGAGE(spec("orient_express_baggage").length(8.0).inventory(54).layout(Layout.BAGGAGE)),
	TGV_CAR(spec("tgv_car").length(9.0).inventory(27).layout(Layout.SEATS)),
	TGV_BAR_CAR(spec("tgv_bar_car").length(9.0).inventory(27).layout(Layout.BAR)),
	TGV_DUPLEX_CAR(spec("tgv_duplex_car").length(9.0).inventory(27).layout(Layout.DUPLEX).height(3.55)),
	TGV_ORANGE_CAR(spec("tgv_orange_car").length(9.0).inventory(27).layout(Layout.SEATS)),
	EUROSTAR_CAR(spec("eurostar_car").length(9.5).inventory(27).layout(Layout.FIRST_CLASS)),
	ICE_CAR(spec("ice_car").length(9.5).inventory(27).layout(Layout.SEATS)),
	SHINKANSEN_CAR(spec("shinkansen_car").length(10.0).inventory(27).layout(Layout.SEATS)),
	SHINKANSEN_GREEN_CAR(spec("shinkansen_green_car").length(10.0).inventory(27).layout(Layout.FIRST_CLASS)),

	// ---------------- Wagons de marchandises ----------------
	TENDER(spec("tender").length(5.5).mass(5).inventory(9).layout(Layout.NONE)),
	FREIGHT_WAGON(spec("freight_wagon").length(7.0).mass(4).inventory(54).layout(Layout.NONE)),
	TANK_WAGON(spec("tank_wagon").length(7.0).mass(4).inventory(27).layout(Layout.NONE)),
	HOPPER_WAGON(spec("hopper_wagon").length(7.0).mass(4).inventory(54).layout(Layout.NONE)),
	CONTAINER_WAGON(spec("container_wagon").length(8.5).mass(4).inventory(54).layout(Layout.NONE)),
	LOG_WAGON(spec("log_wagon").length(8.0).mass(4).inventory(54).layout(Layout.NONE)),
	LIVESTOCK_WAGON(spec("livestock_wagon").length(7.0).mass(4).inventory(9).layout(Layout.LIVESTOCK)),
	CABOOSE(spec("caboose").length(6.0).mass(3).inventory(9).layout(Layout.CABOOSE));

	public enum Power {
		NONE, STEAM, ELECTRIC, DIESEL;

		/** Locomotive fonctionnant avec du combustible (charbon ou carburant). */
		public boolean burnsFuel() {
			return this == STEAM || this == DIESEL;
		}
	}

	/** Aménagement intérieur (voir {@link CarLayout}). */
	public enum Layout {
		NONE, STEAM_CAB, DIESEL_CAB, ELECTRIC_CAB, STREAMLINER_CAB,
		SEATS, FIRST_CLASS, SLEEPER, DINING, BAR, LOUNGE, OBSERVATION, SALON, DUPLEX, BAGGAGE, POST, CABOOSE, LIVESTOCK
	}

	public final String id;
	public final Power power;
	public final double length;
	public final double mass;
	/** Force de traction à plein régime. */
	public final double tractiveForce;
	public final double maxSpeed;
	public final int inventorySize;
	public final Layout layout;
	public final double height;
	private CarLayout carLayout;

	CarType(Spec s) {
		this.id = s.id;
		this.power = s.power;
		this.length = s.length;
		this.mass = s.mass;
		this.tractiveForce = s.force;
		this.maxSpeed = s.speed;
		this.inventorySize = s.inventory;
		this.layout = s.layout;
		this.height = s.height;
	}

	/** Longueur réelle dans le monde (modèle mis à l'échelle). */
	public double worldLength() {
		return length * CarLayout.SCALE;
	}

	public boolean isLocomotive() {
		return power != Power.NONE;
	}

	public CarLayout layout() {
		if (carLayout == null) {
			carLayout = CarLayout.build(this);
		}
		return carLayout;
	}

	/** Position du pantographe le long du véhicule. */
	public float pantographZ() {
		return switch (this) {
			case TGV_POWER_CAR, TGV_ORANGE_POWER_CAR -> -2.4F;
			case EUROSTAR_POWER_CAR, ICE_POWER_CAR -> -2.6F;
			case ELECTRIC_LOCOMOTIVE -> 2.2F;
			default -> -3.0F;
		};
	}

	public int seatCount() {
		return layout().seats.size();
	}

	private static Spec spec(String id) {
		return new Spec(id);
	}

	private static final class Spec {
		final String id;
		Power power = Power.NONE;
		double length = 9;
		double mass = 3.5;
		double force;
		double speed;
		int inventory;
		Layout layout = Layout.NONE;
		double height = 2.85;

		Spec(String id) {
			this.id = id;
		}

		Spec power(Power p) {
			this.power = p;
			return this;
		}

		Spec length(double l) {
			this.length = l;
			return this;
		}

		Spec mass(double m) {
			this.mass = m;
			return this;
		}

		Spec force(double f) {
			this.force = f;
			return this;
		}

		Spec speed(double s) {
			this.speed = s;
			return this;
		}

		Spec inventory(int i) {
			this.inventory = i;
			return this;
		}

		Spec layout(Layout l) {
			this.layout = l;
			return this;
		}

		Spec height(double h) {
			this.height = h;
			return this;
		}
	}
}
