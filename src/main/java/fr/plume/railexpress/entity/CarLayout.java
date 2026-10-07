package fr.plume.railexpress.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Aménagement d'un véhicule, partagé entre le serveur (places, portes, collisions, rangements)
 * et le client (rendu de l'intérieur). Repère local : origine au centre du véhicule au niveau du rail,
 * +Z vers l'avant, +X à droite, +Y vers le haut, en blocs.
 */
public final class CarLayout {
	public static final double FLOOR = 0.6;
	public static final double HALF_WIDTH = 0.98;
	public static final double WALL = 0.08;

	public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
		public Box {
			if (x0 > x1) { double t = x0; x0 = x1; x1 = t; }
			if (y0 > y1) { double t = y0; y0 = y1; y1 = t; }
			if (z0 > z1) { double t = z0; z0 = z1; z1 = t; }
		}

		public boolean contains(double x, double y, double z) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
		}

		/** Distance d'intersection d'un rayon avec la boîte, ou -1. */
		public double clip(double ox, double oy, double oz, double dx, double dy, double dz) {
			double tMin = 0;
			double tMax = Double.MAX_VALUE;
			double[] o = {ox, oy, oz};
			double[] d = {dx, dy, dz};
			double[] lo = {x0, y0, z0};
			double[] hi = {x1, y1, z1};
			for (int i = 0; i < 3; i++) {
				if (Math.abs(d[i]) < 1.0E-9) {
					if (o[i] < lo[i] || o[i] > hi[i]) {
						return -1;
					}
				} else {
					double t1 = (lo[i] - o[i]) / d[i];
					double t2 = (hi[i] - o[i]) / d[i];
					tMin = Math.max(tMin, Math.min(t1, t2));
					tMax = Math.min(tMax, Math.max(t1, t2));
					if (tMin > tMax) {
						return -1;
					}
				}
			}
			return tMin;
		}
	}

	public enum SeatStyle {
		SEAT, ARMCHAIR, SOFA, STOOL, BED, DRIVER, BENCH
	}

	/** Une place : position d'assise, orientation (0 = regarde vers +Z), style et boîte cliquable. */
	public record Seat(double x, double y, double z, float yaw, SeatStyle style, Box box) {
		public boolean isBed() {
			return style == SeatStyle.BED;
		}
	}

	/** Porte latérale coulissante : centre en Z, côté (+1 droite, -1 gauche), largeur. */
	public record Door(double z, int side, double width, double top) {
		public Box box() {
			double x = side * (HALF_WIDTH - WALL / 2);
			return new Box(x - 0.12, FLOOR, z - width / 2, x + 0.12, top, z + width / 2);
		}
	}

	/** Fenêtre (côté 0 = les deux côtés). */
	public record Window(double z0, double z1, double y0, double y1, int side) {
	}

	public enum PropKind {
		TABLE, ROUND_TABLE, COUNTER, KITCHEN, SHELF, CABINET, LUGGAGE_RACK, DESK, LAMP, PLANT, PIANO, STOVE, BUNK_FRAME, PARTITION, HAY, STAIRS, MAIL_RACK, CHANDELIER
	}

	/** Élément de décor intérieur. */
	public record Prop(PropKind kind, Box box, float yaw) {
	}

	public final CarType type;
	public final boolean enterable;
	public final double halfLength;
	public final double top;
	/** Zone intérieure accessible (en Z). */
	public final double interiorZ0;
	public final double interiorZ1;
	public final boolean gangways;
	public final List<Seat> seats = new ArrayList<>();
	public final List<Door> doors = new ArrayList<>();
	public final List<Window> windows = new ArrayList<>();
	public final List<Prop> props = new ArrayList<>();
	public final List<Box> storages = new ArrayList<>();
	/** Volumes pleins (compartiment moteur, nez...). */
	public final List<Box> solids = new ArrayList<>();
	public Box control;
	/** Morceaux de la coque : sélection (clic) et collisions. */
	public final List<Piece> pieces = new ArrayList<>();

	/**
	 * Morceau de coque matérialisé par une entité {@code TrainPartEntity}.
	 * @param pickable cliquable par le joueur
	 * @param solid    bloque les déplacements
	 * @param axisOnly solide uniquement quand le véhicule est aligné sur un axe (parois)
	 * @param door     index de la porte correspondante, ou -1
	 */
	public record Piece(Box box, boolean pickable, boolean solid, boolean axisOnly, int door) {
	}

	private CarLayout(CarType type, boolean enterable, double z0, double z1, boolean gangways) {
		this.type = type;
		this.enterable = enterable;
		this.halfLength = type.length / 2;
		this.top = type.height;
		this.interiorZ0 = z0;
		this.interiorZ1 = z1;
		this.gangways = gangways;
	}

	// ------------------------------------------------------------------

	private void seat(double x, double z, float yaw, SeatStyle style) {
		double h = switch (style) {
			case BED -> 0.35;
			case STOOL -> 0.55;
			default -> 0.42;
		};
		double y = FLOOR + h;
		Box box = style == SeatStyle.BED
				? new Box(x - 0.38, y - 0.25, z - 0.95, x + 0.38, y + 0.15, z + 0.95)
				: new Box(x - 0.3, FLOOR, z - 0.3, x + 0.3, y + 0.55, z + 0.3);
		seats.add(new Seat(x, y, z, yaw, style, box));
	}

	private void bunk(double x, double y, double z) {
		Box box = new Box(x - 0.38, y - 0.12, z - 0.95, x + 0.38, y + 0.3, z + 0.95);
		seats.add(new Seat(x, y + 0.1, z, 0, SeatStyle.BED, box));
	}

	private void prop(PropKind kind, double x0, double y0, double z0, double x1, double y1, double z1) {
		props.add(new Prop(kind, new Box(x0, y0, z0, x1, y1, z1), 0));
	}

	private void storage(PropKind kind, double x0, double y0, double z0, double x1, double y1, double z1) {
		prop(kind, x0, y0, z0, x1, y1, z1);
		storages.add(new Box(x0, y0, z0, x1, y1, z1));
	}

	private void window(double zc, double width, double y0, double y1) {
		windows.add(new Window(zc - width / 2, zc + width / 2, y0, y1, 0));
	}

	/** Portes d'accès aux deux extrémités, des deux côtés. */
	private void endDoors(double fromEnd) {
		for (int side = -1; side <= 1; side += 2) {
			doors.add(new Door(halfLength - fromEnd, side, 0.75, 2.45));
			doors.add(new Door(-halfLength + fromEnd, side, 0.75, 2.45));
		}
	}

	private void windowRow(double z0, double z1, double pitch, double width, double y0, double y1) {
		int n = (int) Math.floor((z1 - z0) / pitch + 1.0E-6);
		double start = (z0 + z1) / 2 - n * pitch / 2 + pitch / 2;
		for (int i = 0; i < n; i++) {
			window(start + i * pitch, width, y0, y1);
		}
	}

	// ------------------------------------------------------------------

	public static CarLayout build(CarType type) {
		CarLayout layout = buildLayout(type);
		layout.buildPieces();
		return layout;
	}

	private void buildPieces() {
		double h = halfLength;
		// Volumes cliquables couvrant tout le véhicule
		int slices = (int) Math.ceil(type.length / 2.0);
		for (int i = 0; i < slices; i++) {
			double z0 = -h + type.length * i / slices;
			double z1 = -h + type.length * (i + 1) / slices;
			pieces.add(new Piece(new Box(-HALF_WIDTH - 0.04, 0.15, z0, HALF_WIDTH + 0.04, top, z1), true, false, false, -1));
		}
		for (Box solid : solids) {
			pieces.add(new Piece(solid, false, true, false, -1));
		}
		if (!enterable) {
			return;
		}
		// Plancher
		double len = interiorZ1 - interiorZ0;
		int floors = Math.max(1, (int) Math.ceil(len / 2.0));
		for (int i = 0; i < floors; i++) {
			double z0 = interiorZ0 + len * i / floors;
			double z1 = interiorZ0 + len * (i + 1) / floors;
			pieces.add(new Piece(new Box(-HALF_WIDTH, 0.0, z0, HALF_WIDTH, FLOOR, z1), false, true, false, -1));
		}
		// Parois latérales, interrompues par les portes
		double wallTop = Math.min(top - 0.2, 2.6);
		for (int side = -1; side <= 1; side += 2) {
			List<double[]> gaps = new ArrayList<>();
			for (Door door : doors) {
				if (door.side() == side) {
					gaps.add(new double[]{door.z() - door.width() / 2, door.z() + door.width() / 2});
				}
			}
			gaps.sort((a, b) -> Double.compare(a[0], b[0]));
			double x0 = side * (HALF_WIDTH - WALL);
			double x1 = side * HALF_WIDTH;
			double z = interiorZ0;
			for (double[] gap : gaps) {
				if (gap[0] > z + 0.05) {
					pieces.add(new Piece(new Box(x0, FLOOR, z, x1, wallTop, gap[0]), false, true, true, -1));
				}
				z = Math.max(z, gap[1]);
			}
			if (interiorZ1 > z + 0.05) {
				pieces.add(new Piece(new Box(x0, FLOOR, z, x1, wallTop, interiorZ1), false, true, true, -1));
			}
		}
		for (int i = 0; i < doors.size(); i++) {
			Door door = doors.get(i);
			double x0 = door.side() * (HALF_WIDTH - WALL);
			double x1 = door.side() * HALF_WIDTH;
			pieces.add(new Piece(new Box(x0, FLOOR, door.z() - door.width() / 2, x1, wallTop, door.z() + door.width() / 2), false, true, true, i));
		}
		// Parois d'extrémité (passage d'intercirculation au centre)
		for (double z : new double[]{interiorZ0, interiorZ1}) {
			double z0 = z < 0 ? z - WALL : z;
			double z1 = z < 0 ? z : z + WALL;
			boolean coversSolid = false;
			for (Box solid : solids) {
				if (z0 < solid.z1() && z1 > solid.z0()) {
					coversSolid = true;
				}
			}
			if (coversSolid) {
				continue;
			}
			if (gangways) {
				pieces.add(new Piece(new Box(-HALF_WIDTH, FLOOR, z0, -0.42, wallTop, z1), false, true, true, -1));
				pieces.add(new Piece(new Box(0.42, FLOOR, z0, HALF_WIDTH, wallTop, z1), false, true, true, -1));
			} else {
				pieces.add(new Piece(new Box(-HALF_WIDTH, FLOOR, z0, HALF_WIDTH, wallTop, z1), false, true, true, -1));
			}
		}
	}

	private static CarLayout buildLayout(CarType type) {
		double h = type.length / 2;
		return switch (type.layout) {
			case NONE -> {
				CarLayout l = new CarLayout(type, false, 0, 0, false);
				l.solids.add(new Box(-HALF_WIDTH, 0.2, -h, HALF_WIDTH, type == CarType.TENDER ? 2.2 : 2.6, h));
				yield l;
			}
			case LIVESTOCK -> {
				CarLayout l = new CarLayout(type, true, -h + 0.3, h - 0.3, false);
				l.doors.add(new Door(0, 1, 1.4, 2.3));
				l.doors.add(new Door(0, -1, 1.4, 2.3));
				l.prop(PropKind.HAY, -0.85, FLOOR, -h + 0.35, 0.85, FLOOR + 0.5, -h + 1.0);
				l.prop(PropKind.HAY, -0.85, FLOOR, h - 1.0, 0.85, FLOOR + 0.5, h - 0.35);
				for (int i = 0; i < 4; i++) {
					double z = -1.8 + i * 1.2;
					l.seats.add(new Seat(0, FLOOR, z, 0, SeatStyle.BENCH, new Box(-0.8, FLOOR, z - 0.5, 0.8, FLOOR + 1.6, z + 0.5)));
				}
				yield l;
			}
			case CABOOSE -> {
				CarLayout l = new CarLayout(type, true, -h + 0.9, h - 0.9, true);
				l.seat(0.5, 0.6, 90, SeatStyle.BENCH);
				l.seat(-0.5, 0.6, -90, SeatStyle.BENCH);
				l.seat(0.0, -1.2, 0, SeatStyle.ARMCHAIR);
				l.bunk(0.45, FLOOR + 0.25, -0.2);
				l.prop(PropKind.STOVE, -0.85, FLOOR, -1.6, -0.45, FLOOR + 1.0, -1.1);
				l.prop(PropKind.DESK, -0.85, FLOOR, 1.2, -0.3, FLOOR + 0.8, 1.9);
				l.storage(PropKind.CABINET, 0.45, FLOOR, 1.2, 0.85, FLOOR + 1.6, 1.9);
				for (double z : new double[]{-1.3, 0.0, 1.3}) {
					l.window(z, 0.6, 1.5, 2.1);
				}
				yield l;
			}
			case STEAM_CAB -> {
				double cab0 = -h + 0.35;
				double cab1 = -h + 2.6;
				CarLayout l = new CarLayout(type, true, cab0, cab1, false);
				l.solids.add(new Box(-0.72, 0.4, cab1, 0.72, 2.3, h));
				l.seats.add(new Seat(0.55, FLOOR + 0.5, cab0 + 1.2, 0, SeatStyle.DRIVER, new Box(0.3, FLOOR, cab0 + 0.9, 0.85, FLOOR + 1.0, cab0 + 1.5)));
				l.seat(-0.55, cab0 + 1.2, 0, SeatStyle.STOOL);
				l.control = new Box(-0.45, FLOOR + 0.6, cab1 - 0.45, 0.45, FLOOR + 1.6, cab1);
				l.doors.add(new Door(cab0 + 0.45, 1, 0.6, 2.3));
				l.doors.add(new Door(cab0 + 0.45, -1, 0.6, 2.3));
				l.windows.add(new Window(cab0 + 0.6, cab0 + 1.8, 1.95, 2.45, 0));
				yield l;
			}
			case DIESEL_CAB, ELECTRIC_CAB -> {
				boolean twin = type.layout == CarType.Layout.ELECTRIC_CAB;
				double cab1 = h - 0.35;
				double cab0 = cab1 - 2.0;
				CarLayout l = new CarLayout(type, true, twin ? -cab1 : cab0, cab1, false);
				l.solids.add(new Box(-0.75, 0.4, twin ? -cab0 : -h, 0.75, twin ? 2.6 : 2.3, cab0));
				for (int dir = twin ? -1 : 1; dir <= 1; dir += 2) {
					double front = dir * cab1;
					double c = dir * (cab1 - 0.9);
					l.seats.add(new Seat(0.45, FLOOR + 0.42, c, dir > 0 ? 0 : 180, SeatStyle.DRIVER,
							new Box(0.15, FLOOR, c - 0.3, 0.75, FLOOR + 1.0, c + 0.3)));
					l.seat(-0.45, c, dir > 0 ? 0 : 180, SeatStyle.ARMCHAIR);
					Box desk = new Box(-0.85, FLOOR, front - dir * 0.45, 0.85, FLOOR + 0.95, front);
					l.props.add(new Prop(PropKind.DESK, desk, dir > 0 ? 0 : 180));
					if (dir > 0 || l.control == null) {
						l.control = desk;
					}
					l.doors.add(new Door(dir * (cab0 + 0.55), 1, 0.65, 2.4));
					l.doors.add(new Door(dir * (cab0 + 0.55), -1, 0.65, 2.4));
					l.windows.add(new Window(Math.min(c, c + dir * 0.6), Math.max(c, c + dir * 0.6), 1.55, 2.25, 0));
				}
				yield l;
			}
			case STREAMLINER_CAB -> {
				double cab0 = h - 3.6;
				double cab1 = h - 1.7;
				CarLayout l = new CarLayout(type, true, cab0, cab1, false);
				l.solids.add(new Box(-0.8, 0.4, -h, 0.8, 2.6, cab0 - 0.05));
				l.solids.add(new Box(-0.6, 0.4, cab1 + 0.4, 0.6, 1.2, h - 0.2));
				double c = cab1 - 0.6;
				l.seats.add(new Seat(0.0, FLOOR + 0.42, c, 0, SeatStyle.DRIVER, new Box(-0.35, FLOOR, c - 0.3, 0.35, FLOOR + 1.0, c + 0.3)));
				l.seat(-0.55, cab0 + 0.6, 0, SeatStyle.STOOL);
				l.control = new Box(-0.8, FLOOR, cab1 - 0.15, 0.8, FLOOR + 1.0, cab1 + 0.5);
				l.props.add(new Prop(PropKind.DESK, l.control, 0));
				l.doors.add(new Door(cab0 + 0.5, 1, 0.65, 2.4));
				l.doors.add(new Door(cab0 + 0.5, -1, 0.65, 2.4));
				yield l;
			}
			default -> passenger(type, h);
		};
	}

	private static CarLayout passenger(CarType type, double h) {
		CarLayout l = new CarLayout(type, true, -h + 0.25, h - 0.25, true);
		l.endDoors(0.75);
		double s0 = -h + 1.35;
		double s1 = h - 1.35;
		double wy0 = 1.45;
		double wy1 = 2.2;
		// Porte-bagages au-dessus des fenêtres (accès au stockage)
		l.storage(PropKind.LUGGAGE_RACK, 0.62, 2.28, s0, 0.9, 2.42, s1);
		l.storage(PropKind.LUGGAGE_RACK, -0.9, 2.28, s0, -0.62, 2.42, s1);
		switch (type.layout) {
			case SEATS -> {
				int rows = (int) Math.floor((s1 - s0) / 0.95);
				double start = (s0 + s1) / 2 - (rows - 1) * 0.95 / 2;
				for (int i = 0; i < rows; i++) {
					double z = start + i * 0.95;
					float yaw = i % 2 == 0 ? 0 : 180;
					l.seat(0.52, z, yaw, SeatStyle.SEAT);
					l.seat(-0.52, z, yaw, SeatStyle.SEAT);
					if (i % 2 == 0 && i + 1 < rows) {
						l.prop(PropKind.TABLE, 0.3, FLOOR + 0.65, z + 0.3, 0.92, FLOOR + 0.72, z + 0.65);
						l.prop(PropKind.TABLE, -0.92, FLOOR + 0.65, z + 0.3, -0.3, FLOOR + 0.72, z + 0.65);
					}
				}
				l.windowRow(s0, s1, 0.95, 0.72, wy0, wy1);
			}
			case FIRST_CLASS -> {
				int rows = (int) Math.floor((s1 - s0) / 1.35);
				double start = (s0 + s1) / 2 - (rows - 1) * 1.35 / 2;
				for (int i = 0; i < rows; i++) {
					double z = start + i * 1.35;
					float yaw = i % 2 == 0 ? 0 : 180;
					l.seat(0.5, z, yaw, SeatStyle.ARMCHAIR);
					l.seat(-0.5, z, yaw, SeatStyle.ARMCHAIR);
					if (i % 2 == 0 && i + 1 < rows) {
						l.prop(PropKind.TABLE, 0.25, FLOOR + 0.68, z + 0.4, 0.92, FLOOR + 0.75, z + 0.95);
						l.prop(PropKind.TABLE, -0.92, FLOOR + 0.68, z + 0.4, -0.25, FLOOR + 0.75, z + 0.95);
						l.prop(PropKind.LAMP, 0.82, FLOOR + 0.75, z + 0.6, 0.9, FLOOR + 1.05, z + 0.68);
						l.prop(PropKind.LAMP, -0.9, FLOOR + 0.75, z + 0.6, -0.82, FLOOR + 1.05, z + 0.68);
					}
				}
				l.windowRow(s0, s1, 1.35, 1.0, wy0, wy1);
			}
			case SLEEPER -> {
				// Compartiments couchettes : lits superposés côté droit, couloir côté gauche
				int n = (int) Math.floor((s1 - s0) / 2.05);
				double start = (s0 + s1) / 2 - (n - 1) * 2.05 / 2;
				for (int i = 0; i < n; i++) {
					double z = start + i * 2.05;
					l.bunk(0.42, FLOOR + 0.35, z);
					l.bunk(0.42, FLOOR + 1.35, z);
					l.prop(PropKind.BUNK_FRAME, 0.02, FLOOR, z - 1.0, 0.9, FLOOR + 1.75, z + 1.0);
					l.prop(PropKind.PARTITION, -0.05, FLOOR, z + 1.0, 0.9, 2.5, z + 1.06);
					l.window(z, 0.9, wy0, wy1);
				}
				l.seat(-0.62, s0 + 0.4, 90, SeatStyle.STOOL);
				l.seat(-0.62, s1 - 0.4, 90, SeatStyle.STOOL);
			}
			case DINING -> {
				// Cuisine à l'avant, tables de quatre à l'arrière
				double k = s1 - 1.6;
				l.storage(PropKind.KITCHEN, 0.25, FLOOR, k, 0.9, FLOOR + 0.95, s1);
				l.prop(PropKind.SHELF, 0.55, FLOOR + 1.4, k, 0.9, FLOOR + 1.75, s1);
				l.prop(PropKind.COUNTER, -0.9, FLOOR, k + 0.6, -0.55, FLOOR + 0.95, s1);
				int tables = (int) Math.floor((k - 0.3 - s0) / 1.6);
				double start = s0 + 0.8;
				for (int i = 0; i < tables; i++) {
					double z = start + i * 1.6;
					for (int side = -1; side <= 1; side += 2) {
						l.seat(side * 0.55, z - 0.45, 0, SeatStyle.ARMCHAIR);
						l.seat(side * 0.55, z + 0.45, 180, SeatStyle.ARMCHAIR);
						l.prop(PropKind.TABLE, side * 0.25, FLOOR + 0.68, z - 0.22, side * 0.92, FLOOR + 0.75, z + 0.22);
						l.prop(PropKind.LAMP, side * 0.84, FLOOR + 0.75, z - 0.04, side * 0.9, FLOOR + 1.05, z + 0.04);
					}
					l.window(z, 1.0, wy0, wy1);
				}
			}
			case BAR -> {
				l.prop(PropKind.COUNTER, 0.3, FLOOR, s0 + 0.3, 0.9, FLOOR + 1.0, s1 - 2.2);
				l.storage(PropKind.SHELF, 0.75, FLOOR + 1.25, s0 + 0.3, 0.9, 2.2, s1 - 2.2);
				for (double z = s0 + 0.6; z < s1 - 2.4; z += 0.9) {
					l.seat(-0.05, z, 90, SeatStyle.STOOL);
				}
				l.prop(PropKind.ROUND_TABLE, -0.25, FLOOR + 0.95, s1 - 1.5, 0.25, FLOOR + 1.02, s1 - 1.0);
				l.seat(-0.6, s1 - 1.3, 90, SeatStyle.SOFA);
				l.seat(0.6, s1 - 1.3, -90, SeatStyle.SOFA);
				l.windowRow(s0, s1, 1.2, 0.95, wy0, wy1);
			}
			case LOUNGE, SALON -> {
				boolean salon = type.layout == CarType.Layout.SALON;
				int n = (int) Math.floor((s1 - s0) / 1.3);
				double start = (s0 + s1) / 2 - (n - 1) * 1.3 / 2;
				for (int i = 0; i < n; i++) {
					double z = start + i * 1.3;
					l.seat(0.6, z, -90, salon && i % 2 == 0 ? SeatStyle.ARMCHAIR : SeatStyle.SOFA);
					l.seat(-0.6, z, 90, salon && i % 2 == 1 ? SeatStyle.ARMCHAIR : SeatStyle.SOFA);
					if (i % 2 == 0) {
						l.prop(PropKind.ROUND_TABLE, -0.2, FLOOR + 0.45, z - 0.2, 0.2, FLOOR + 0.5, z + 0.2);
					}
					l.window(z, 1.0, wy0 - 0.05, wy1);
				}
				l.prop(PropKind.PLANT, 0.6, FLOOR, s0 - 0.05, 0.9, FLOOR + 1.0, s0 + 0.25);
				l.prop(PropKind.PLANT, -0.9, FLOOR, s1 - 0.25, -0.6, FLOOR + 1.0, s1 + 0.05);
				if (salon) {
					l.prop(PropKind.PIANO, -0.9, FLOOR, s1 - 0.05, -0.2, FLOOR + 1.1, s1 + 0.4);
					l.prop(PropKind.CHANDELIER, -0.2, 2.3, -0.2, 0.2, 2.6, 0.2);
				}
			}
			case OBSERVATION -> {
				int n = (int) Math.floor((s1 - s0) / 0.9);
				double start = (s0 + s1) / 2 - (n - 1) * 0.9 / 2;
				for (int i = 0; i < n; i++) {
					double z = start + i * 0.9;
					l.seat(0.25, z, 90, SeatStyle.ARMCHAIR);
					l.seat(-0.25, z, -90, SeatStyle.ARMCHAIR);
				}
				l.windows.add(new Window(s0 - 0.2, s1 + 0.2, 1.2, 2.4, 0));
			}
			case DUPLEX -> {
				// Deux niveaux : salle basse et salle haute
				int rows = (int) Math.floor((s1 - s0 - 1.2) / 0.95);
				double start = s0 + 1.2 + 0.95 / 2;
				for (int i = 0; i < rows; i++) {
					double z = start + i * 0.95;
					float yaw = i % 2 == 0 ? 0 : 180;
					l.seat(0.52, z, yaw, SeatStyle.SEAT);
					l.seat(-0.52, z, yaw, SeatStyle.SEAT);
					double up = 1.6;
					Box b1 = new Box(0.22, FLOOR + up, z - 0.3, 0.82, FLOOR + up + 0.97, z + 0.3);
					Box b2 = new Box(-0.82, FLOOR + up, z - 0.3, -0.22, FLOOR + up + 0.97, z + 0.3);
					l.seats.add(new Seat(0.52, FLOOR + up + 0.42, z, yaw, SeatStyle.SEAT, b1));
					l.seats.add(new Seat(-0.52, FLOOR + up + 0.42, z, yaw, SeatStyle.SEAT, b2));
				}
				l.prop(PropKind.STAIRS, -0.4, FLOOR, s0, 0.4, FLOOR + 1.6, s0 + 1.2);
				l.prop(PropKind.PARTITION, -0.9, FLOOR + 1.55, s0 + 1.2, 0.9, FLOOR + 1.62, s1);
				l.windowRow(s0 + 1.2, s1, 0.95, 0.7, 1.1, 1.6);
				l.windowRow(s0 + 1.2, s1, 0.95, 0.7, 2.6, 3.15);
			}
			case BAGGAGE, POST -> {
				boolean post = type.layout == CarType.Layout.POST;
				l.storages.clear();
				l.props.clear();
				l.storage(post ? PropKind.MAIL_RACK : PropKind.SHELF, 0.5, FLOOR, s0, 0.9, 2.3, s1);
				l.storage(post ? PropKind.CABINET : PropKind.SHELF, -0.9, FLOOR, s0, -0.5, 2.3, -0.9);
				l.seat(-0.3, 1.3, -90, SeatStyle.STOOL);
				l.prop(PropKind.DESK, -0.9, FLOOR, 0.9, -0.5, FLOOR + 0.8, s1);
				l.doors.clear();
				l.doors.add(new Door(0.0, 1, 1.4, 2.35));
				l.doors.add(new Door(0.0, -1, 1.4, 2.35));
				l.window(s0 + 0.5, 0.5, 1.6, 2.1);
				l.window(s1 - 0.5, 0.5, 1.6, 2.1);
			}
			default -> {
			}
		}
		return l;
	}
}
