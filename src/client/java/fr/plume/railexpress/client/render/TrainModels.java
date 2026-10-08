package fr.plume.railexpress.client.render;

import fr.plume.railexpress.client.render.Mesh.Frame;
import fr.plume.railexpress.client.render.Mesh.Section;
import fr.plume.railexpress.entity.CarLayout;
import fr.plume.railexpress.entity.CarLayout.Box;
import fr.plume.railexpress.entity.CarLayout.Door;
import fr.plume.railexpress.entity.CarLayout.Prop;
import fr.plume.railexpress.entity.CarLayout.Seat;
import fr.plume.railexpress.entity.CarLayout.Window;
import fr.plume.railexpress.entity.CarType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.joml.Vector3f;

/**
 * Modèles 3D procéduraux de tous les véhicules (extérieur, vitrages, intérieur, portes).
 * Repère : origine au centre du véhicule au niveau du rail, +Z vers l'avant, +X à droite, +Y vers le haut (en blocs).
 */
public final class TrainModels {
	/** Demi-écartement des roues : aligné sur les rails 3D (voie de 14 pixels entre axes). */
	public static final float GAUGE = 0.35F;

	public record Axle(float z, float radius, float gauge, WheelStyle style) {
	}

	public enum WheelStyle {
		MODERN, STEAM_DRIVER, STEAM_SMALL
	}

	/** Panneau de porte animé : glisse de {@code slide} blocs le long de Z une fois ouvert. */
	public record DoorPanel(int index, Mesh mesh, float slide) {
	}

	/** Bielles de locomotive à vapeur. */
	public record Rods(float[] drivers, float radius, float crank, float crossheadZ, float cylinderY, float x) {
	}

	public record Model(Mesh body, Mesh glass, Mesh lamps, List<DoorPanel> doors, List<Axle> axles, Rods rods) {
	}

	private static final class Builder {
		final Mesh body = new Mesh();
		final Mesh glass = new Mesh();
		final Mesh lamps = new Mesh();
		final List<DoorPanel> doors = new ArrayList<>();
		final List<Axle> axles = new ArrayList<>();
		Rods rods;

		Model build() {
			return new Model(body, glass, lamps, doors, axles, rods);
		}
	}

	private static final Map<CarType, Model> CACHE = new EnumMap<>(CarType.class);
	private static final Map<WheelStyle, Map<Float, Mesh>> WHEELS = new EnumMap<>(WheelStyle.class);

	// Palette commune
	static final int GLASS = 0x6098C4DC;
	static final int GLASS_DARK = 0xFF101A26;
	static final int BOGIE = 0xFF2B2E33;
	static final int UNDERFRAME = 0xFF1C1E21;
	static final int STEEL = 0xFF8A9096;
	static final int BLACK = 0xFF18191B;
	static final int RUBBER = 0xFF232427;
	static final int HEADLIGHT = 0xFFFFF4C2;
	static final int TAILLIGHT = 0xFFE0251C;
	static final int WARM_LIGHT = 0xFFFFE6A8;
	static final int BRASS = 0xFFD2A93F;
	static final float FLOOR = (float) CarLayout.FLOOR;
	static final float HW = (float) CarLayout.HALF_WIDTH;

	private TrainModels() {
	}

	public static Model get(CarType type) {
		return CACHE.computeIfAbsent(type, TrainModels::build);
	}

	public static Mesh wheel(WheelStyle style, float radius) {
		return WHEELS.computeIfAbsent(style, s -> new HashMap<>()).computeIfAbsent(radius, r -> buildWheel(style, r));
	}

	private static Model build(CarType type) {
		Builder b = new Builder();
		switch (type) {
			case STEAM_LOCOMOTIVE -> steamLocomotive(b, type, false);
			case ORIENT_EXPRESS_LOCOMOTIVE -> steamLocomotive(b, type, true);
			case DIESEL_LOCOMOTIVE -> dieselLocomotive(b, type);
			case ELECTRIC_LOCOMOTIVE -> electricLocomotive(b, type);
			case TGV_POWER_CAR -> streamliner(b, type, Nose.TGV, Livery.TGV);
			case TGV_ORANGE_POWER_CAR -> streamliner(b, type, Nose.TGV, Livery.TGV_ORANGE);
			case EUROSTAR_POWER_CAR -> streamliner(b, type, Nose.EUROSTAR, Livery.EUROSTAR);
			case ICE_POWER_CAR -> streamliner(b, type, Nose.ICE, Livery.ICE);
			case SHINKANSEN_HEAD -> streamliner(b, type, Nose.SHINKANSEN, Livery.SHINKANSEN);
			case PASSENGER_COACH, SLEEPER_CAR, DINING_CAR, LOUNGE_CAR, LUXURY_CAR, OBSERVATION_CAR -> coach(b, type, Livery.CLASSIC, Profile.CLASSIC);
			case BAGGAGE_CAR -> coach(b, type, Livery.BAGGAGE, Profile.CLASSIC);
			case POST_CAR -> coach(b, type, Livery.POST, Profile.CLASSIC);
			case ORIENT_EXPRESS_SLEEPER, ORIENT_EXPRESS_DINING, ORIENT_EXPRESS_SALON, ORIENT_EXPRESS_BAGGAGE -> coach(b, type, Livery.ORIENT, Profile.ORIENT);
			case TGV_CAR, TGV_BAR_CAR -> coach(b, type, Livery.TGV, Profile.MODERN);
			case TGV_DUPLEX_CAR -> coach(b, type, Livery.TGV, Profile.DUPLEX);
			case TGV_ORANGE_CAR -> coach(b, type, Livery.TGV_ORANGE, Profile.MODERN);
			case EUROSTAR_CAR -> coach(b, type, Livery.EUROSTAR, Profile.MODERN);
			case ICE_CAR -> coach(b, type, Livery.ICE, Profile.MODERN);
			case SHINKANSEN_CAR -> coach(b, type, Livery.SHINKANSEN, Profile.SHINKANSEN);
			case SHINKANSEN_GREEN_CAR -> coach(b, type, Livery.SHINKANSEN_GREEN, Profile.SHINKANSEN);
			case TENDER -> tender(b, type);
			case FREIGHT_WAGON -> boxcar(b, type);
			case TANK_WAGON -> tankWagon(b, type);
			case HOPPER_WAGON -> hopperWagon(b, type);
			case CONTAINER_WAGON -> containerWagon(b, type);
			case LOG_WAGON -> logWagon(b, type);
			case LIVESTOCK_WAGON -> livestockWagon(b, type);
			case CABOOSE -> caboose(b, type);
		}
		return b.build();
	}

	// ==================================================================
	// Outils communs
	// ==================================================================

	static boolean periodic(float value, float start, float pitch, float width) {
		float m = (value - start) % pitch;
		if (m < 0) {
			m += pitch;
		}
		return m < width;
	}

	static Section s(float z, float hw, float bottom, float top, float roof, float floor) {
		return new Section(z, hw, bottom, top, roof, floor);
	}

	static Vector3f v(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	/** Sections d'une caisse droite découpée aux altitudes Z demandées. */
	static Section[] straight(TreeSet<Float> zs, float hw, float bottom, float top, float roof, float floor) {
		Section[] out = new Section[zs.size()];
		int i = 0;
		for (float z : zs) {
			out[i++] = s(z, hw, bottom, top, roof, floor);
		}
		return out;
	}

	static Section[] concat(Section[] a, Section[] b) {
		Section[] r = new Section[a.length + b.length - 1];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 1, r, a.length, b.length - 1);
		return r;
	}

	/** Interpole finement entre des sections clés (nez aérodynamique). */
	static Section[] smooth(Section[] keys, int sub) {
		List<Section> out = new ArrayList<>();
		for (int i = 0; i + 1 < keys.length; i++) {
			Section a = keys[i];
			Section b = keys[i + 1];
			Section p0 = keys[Math.max(0, i - 1)];
			Section p3 = keys[Math.min(keys.length - 1, i + 2)];
			for (int k = 0; k < sub; k++) {
				float t = (float) k / sub;
				out.add(new Section(
						cr(p0.z(), a.z(), b.z(), p3.z(), t),
						cr(p0.halfWidth(), a.halfWidth(), b.halfWidth(), p3.halfWidth(), t),
						cr(p0.bottom(), a.bottom(), b.bottom(), p3.bottom(), t),
						cr(p0.top(), a.top(), b.top(), p3.top(), t),
						cr(p0.roofRadius(), a.roofRadius(), b.roofRadius(), p3.roofRadius(), t),
						cr(p0.floorRadius(), a.floorRadius(), b.floorRadius(), p3.floorRadius(), t)));
			}
		}
		out.add(keys[keys.length - 1]);
		return out.toArray(new Section[0]);
	}

	private static float cr(float p0, float p1, float p2, float p3, float t) {
		float t2 = t * t;
		float t3 = t2 * t;
		return 0.5F * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}

	static float f(double d) {
		return (float) d;
	}

	/** Bogie moderne : longerons, traverse, boîtes d'essieux, ressorts et freins. */
	static void bogie(Builder b, float z, float spacing, float wheelRadius) {
		Mesh m = b.body;
		float half = spacing / 2;
		float x0 = GAUGE + 0.08F;
		m.mirroredBox(x0, 0.2F, z - half - 0.38F, x0 + 0.1F, 0.46F, z + half + 0.38F, BOGIE);
		m.mirroredBox(x0 + 0.02F, 0.46F, z - half - 0.2F, x0 + 0.08F, 0.5F, z + half + 0.2F, 0xFF3C4046);
		m.box(-x0 - 0.1F, 0.36F, z - 0.2F, x0 + 0.1F, 0.48F, z + 0.2F, BOGIE);
		for (float az : new float[]{z - half, z + half}) {
			m.mirroredBox(x0 - 0.02F, wheelRadius - 0.04F, az - 0.13F, x0 + 0.14F, wheelRadius + 0.16F, az + 0.13F, 0xFF3C4046);
			m.mirroredBox(x0 + 0.1F, wheelRadius - 0.02F, az - 0.06F, x0 + 0.16F, wheelRadius + 0.1F, az + 0.06F, 0xFFC9A44A);
			// Freins à disque
			m.cylinder(0, wheelRadius + 0.06F, az, -GAUGE + 0.08F, GAUGE - 0.08F, wheelRadius * 0.65F, 10, 0xFF55595F, 0xFF6A6E73);
		}
		for (float sz : new float[]{z - 0.25F, z + 0.25F}) {
			m.cylinder(1, x0 + 0.05F, sz, 0.46F, 0.58F, 0.07F, 8, 0xFFB0352A, 0xFFB0352A);
			m.cylinder(1, -x0 - 0.05F, sz, 0.46F, 0.58F, 0.07F, 8, 0xFFB0352A, 0xFFB0352A);
		}
		b.axles.add(new Axle(z - half, wheelRadius, GAUGE, WheelStyle.MODERN));
		b.axles.add(new Axle(z + half, wheelRadius, GAUGE, WheelStyle.MODERN));
	}

	/** Tampons et attelage à vis à une extrémité. */
	static void buffers(Mesh m, float zBeam, int dir, float y, int beamColor) {
		m.box(-0.95F, y - 0.18F, Math.min(zBeam, zBeam - dir * 0.14F), 0.95F, y + 0.18F, Math.max(zBeam, zBeam - dir * 0.14F), beamColor);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 0.68F;
			float z1 = zBeam + dir * 0.26F;
			m.cylinder(2, x, y, Math.min(zBeam, z1), Math.max(zBeam, z1), 0.08F, 8, BLACK, BLACK);
			float h0 = zBeam + dir * 0.24F;
			float h1 = zBeam + dir * 0.3F;
			m.cylinder(2, x, y, Math.min(h0, h1), Math.max(h0, h1), 0.15F, 12, 0xFFB8BEC4, 0xFFC9CFD5);
		}
		m.box(-0.08F, y - 0.08F, Math.min(zBeam, zBeam + dir * 0.32F), 0.08F, y + 0.06F, Math.max(zBeam, zBeam + dir * 0.32F), 0xFF4A4D52);
		m.box(-0.12F, y - 0.12F, Math.min(zBeam + dir * 0.28F, zBeam + dir * 0.36F), 0.12F, y + 0.02F, Math.max(zBeam + dir * 0.28F, zBeam + dir * 0.36F), 0xFF4A4D52);
	}

	/** Soufflet d'intercirculation autour du passage. */
	static void bellows(Mesh m, float z, int dir, float top) {
		float z0 = Math.min(z + dir * 0.006F, z + dir * 0.22F);
		float z1 = Math.max(z + dir * 0.006F, z + dir * 0.22F);
		m.box(-0.62F, 0.62F, z0, -0.42F, top, z1, RUBBER);
		m.box(0.42F, 0.62F, z0, 0.62F, top, z1, RUBBER);
		m.box(-0.62F, top - 0.18F, z0, 0.62F, top, z1, RUBBER);
		for (float zz = z0 + 0.04F; zz < z1; zz += 0.06F) {
			m.box(-0.63F, 0.62F, zz, 0.63F, top + 0.01F, zz + 0.015F, 0xFF34363A);
		}
	}

	/** Cadre en saillie autour d'une ouverture rectangulaire sur un flanc. */
	static void frame(Mesh m, int side, float z0, float z1, float y0, float y1, float t, int color) {
		float xo = side * (HW + 0.018F);
		float xi = side * (HW - 0.085F);
		m.box(Math.min(xo, xi), y0 - t, z0 - t, Math.max(xo, xi), y0, z1 + t, color);
		m.box(Math.min(xo, xi), y1, z0 - t, Math.max(xo, xi), y1 + t, z1 + t, color);
		m.box(Math.min(xo, xi), y0, z0 - t, Math.max(xo, xi), y1, z0, color);
		m.box(Math.min(xo, xi), y0, z1, Math.max(xo, xi), y1, z1 + t, color);
	}

	/** Phare (lentille émissive dans un boîtier). */
	static void lamp(Builder b, float x, float y, float z, float r, int color, int dir) {
		float z0 = Math.min(z, z + dir * 0.06F);
		float z1 = Math.max(z, z + dir * 0.06F);
		b.body.cylinder(2, x, y, z0, z1, r + 0.03F, 10, BLACK, BLACK);
		float l0 = dir > 0 ? z1 : z0 - 0.012F;
		b.lamps.cylinder(2, x, y, l0, l0 + 0.012F, r, 10, color, color);
	}

	// ==================================================================
	// Livrées et profils des voitures voyageurs
	// ==================================================================

	/** Couleurs extérieures et intérieures d'une famille de voitures. */
	enum Livery {
		CLASSIC(0xFF2A2526, 0xFF6E1F22, 0xFFC9A44A, 0xFFEADFC2, 0xFFEADFC2, 0xFF55595E, 0xFF3A2E22, 0xFF6E1F22,
				0xFF8A5A35, 0xFF6B4226, 0xFFF0E6CF, 0xFF8E1F2A, 0xFFC9A44A),
		BAGGAGE(0xFF2A2526, 0xFF6E1F22, 0xFFC9A44A, 0xFF6E1F22, 0xFF6E1F22, 0xFF55595E, 0xFF3A2E22, 0xFF5E1A1D,
				0xFF9C8B70, 0xFF6B5B45, 0xFFD8D0BC, 0xFF5B4A3A, 0xFFC9A44A),
		POST(0xFF2A2526, 0xFFF2C14E, 0xFF1C3F94, 0xFFF2C14E, 0xFFF2C14E, 0xFF55595E, 0xFF1C3F94, 0xFFE0AE3C,
				0xFFB9A98A, 0xFF7A6A50, 0xFFE8E2D2, 0xFF2D4A8A, 0xFF1C3F94),
		ORIENT(0xFF15162A, 0xFF1B2A57, 0xFFD4AF37, 0xFF1B2A57, 0xFF1B2A57, 0xFF3A3D42, 0xFFD4AF37, 0xFF182450,
				0xFF7A4A22, 0xFF5A1E24, 0xFFF3E7C6, 0xFF23305E, 0xFFD4AF37),
		TGV(0xFF1E2F5E, 0xFFDCE1E6, 0xFFB0204A, 0xFF2C55A8, 0xFFDCE1E6, 0xFFC4CAD0, 0xFF3A3E44, 0xFF2A4A8C,
				0xFF3B4A6A, 0xFFD8DCE0, 0xFFEFF1F3, 0xFF2C4A9A, 0xFF8EA4D2),
		TGV_ORANGE(0xFF3B3E44, 0xFFF07A1F, 0xFFF4F4F4, 0xFF3B3E44, 0xFFF07A1F, 0xFFB9BEC4, 0xFF3A3E44, 0xFFE06A12,
				0xFF6B4A3A, 0xFFE6D8C4, 0xFFF3EEE6, 0xFFC4511A, 0xFFF0A060),
		EUROSTAR(0xFF2B3240, 0xFFF2F2EE, 0xFFFDD000, 0xFF2E3A4F, 0xFFF2F2EE, 0xFFD9DAD6, 0xFF3A3E44, 0xFFE8E8E2,
				0xFF4A4E58, 0xFFD6D6D0, 0xFFF4F4EE, 0xFF6E7684, 0xFFFDD000),
		ICE(0xFF5E636A, 0xFFF5F5F5, 0xFFEC0016, 0xFF2A2E33, 0xFFF5F5F5, 0xFFDADDE0, 0xFF3A3E44, 0xFFE8E8E8,
				0xFF7A6E62, 0xFFE4E2DC, 0xFFF6F6F2, 0xFF4E5560, 0xFFEC0016),
		SHINKANSEN(0xFFB6BCC4, 0xFFF4F6F9, 0xFF1546A0, 0xFFF4F6F9, 0xFFF4F6F9, 0xFFE4E8EC, 0xFF3A3E44, 0xFFE2E6EB,
				0xFFC8BFA8, 0xFFEDEBE4, 0xFFF8F8F4, 0xFF2E4C9E, 0xFF8EA4D2),
		DIESEL(0xFF4A4E55, 0xFFF2B21C, 0xFFC0392B, 0xFFF2B21C, 0xFFF2B21C, 0xFFD9D9D2, 0xFF2E3033, 0xFFE0A418,
				0xFF4A4E55, 0xFFB8B4A8, 0xFFE8E4D8, 0xFF2B2E33, 0xFFC0392B),
		ELECTRIC(0xFF2F333A, 0xFFC9CED4, 0xFFB0283A, 0xFFC9CED4, 0xFFC9CED4, 0xFF9EA4AA, 0xFF2F333A, 0xFFC9CED4,
				0xFF4A4E55, 0xFFB8B4A8, 0xFFE8E4D8, 0xFF2B2E33, 0xFFB0283A),
		SHINKANSEN_GREEN(0xFFB6BCC4, 0xFFF4F6F9, 0xFF1546A0, 0xFFF4F6F9, 0xFFF4F6F9, 0xFFE4E8EC, 0xFF3A3E44, 0xFFE2E6EB,
				0xFF7A2E2E, 0xFFE8E2D4, 0xFFF8F6EE, 0xFF8A3A2A, 0xFF2E8A4A);

		final int skirt;
		final int lower;
		final int stripe;
		final int band;
		final int upper;
		final int roof;
		final int frame;
		final int door;
		final int floor;
		final int wall;
		final int ceiling;
		final int seat;
		final int accent;

		Livery(int skirt, int lower, int stripe, int band, int upper, int roof, int frame, int door,
				int floor, int wall, int ceiling, int seat, int accent) {
			this.skirt = skirt;
			this.lower = lower;
			this.stripe = stripe;
			this.band = band;
			this.upper = upper;
			this.roof = roof;
			this.frame = frame;
			this.door = door;
			this.floor = floor;
			this.wall = wall;
			this.ceiling = ceiling;
			this.seat = seat;
			this.accent = accent;
		}

		int outer(float y, float top) {
			if (y < 0.72F) {
				return skirt;
			}
			if (y < 1.3F) {
				return lower;
			}
			if (y < 1.4F) {
				return stripe;
			}
			if (y < 2.25F) {
				return band;
			}
			if (y < top - 0.42F) {
				return upper;
			}
			return roof;
		}
	}

	/** Gabarit de caisse. */
	enum Profile {
		CLASSIC(0.5F, 2.75F, 0.55F, 0.08F),
		ORIENT(0.5F, 2.82F, 0.6F, 0.08F),
		MODERN(0.45F, 2.85F, 0.5F, 0.18F),
		DUPLEX(0.4F, 3.55F, 0.5F, 0.18F),
		SHINKANSEN(0.45F, 2.8F, 0.6F, 0.25F);

		final float bottom;
		final float top;
		final float roofR;
		final float floorR;

		Profile(float bottom, float top, float roofR, float floorR) {
			this.bottom = bottom;
			this.top = top;
			this.roofR = roofR;
			this.floorR = floorR;
		}
	}

	private static boolean inWindow(CarLayout l, float x, float y, float z) {
		for (Window w : l.windows) {
			if ((w.side() == 0 || w.side() == (x > 0 ? 1 : -1)) && z > w.z0() && z < w.z1() && y > w.y0() && y < w.y1()) {
				return true;
			}
		}
		return false;
	}

	private static boolean inDoor(CarLayout l, float x, float y, float z) {
		for (Door d : l.doors) {
			if (d.side() == (x > 0 ? 1 : -1) && Math.abs(z - d.z()) < d.width() / 2 && y < d.top() && y > FLOOR - 0.12F) {
				return true;
			}
		}
		return false;
	}

	private static TreeSet<Float> zBreaks(CarLayout l, float z0, float z1) {
		TreeSet<Float> zs = new TreeSet<>();
		zs.add(z0);
		zs.add(z1);
		for (Window w : l.windows) {
			add(zs, f(w.z0()), z0, z1);
			add(zs, f(w.z1()), z0, z1);
		}
		for (Door d : l.doors) {
			add(zs, f(d.z() - d.width() / 2), z0, z1);
			add(zs, f(d.z() + d.width() / 2), z0, z1);
		}
		return zs;
	}

	private static void add(TreeSet<Float> zs, float z, float z0, float z1) {
		if (z > z0 + 0.01F && z < z1 - 0.01F) {
			zs.add(z);
		}
	}

	private static float[] yBreaks(CarLayout l, float... extra) {
		TreeSet<Float> ys = new TreeSet<>();
		for (Window w : l.windows) {
			ys.add(f(w.y0()));
			ys.add(f(w.y1()));
		}
		for (Door d : l.doors) {
			ys.add(f(d.top()));
		}
		for (float e : extra) {
			ys.add(e);
		}
		float[] out = new float[ys.size()];
		int i = 0;
		for (float y : ys) {
			out[i++] = y;
		}
		return out;
	}

	/**
	 * Coque creuse : paroi extérieure percée de fenêtres et de portes, vitrages, habillage intérieur.
	 */
	private static void hull(Builder b, CarLayout l, Section[] outer, int arcSeg, float[] breaks, Mesh.ColorFunction color,
			float innerZ0, float innerZ1, float innerTop, float roofR, Livery lv) {
		b.body.loft(outer, arcSeg, false, false, (x, y, z, nx, ny, nz) -> {
			if (Math.abs(nx) > 0.5F && (inWindow(l, x, y, z) || inDoor(l, x, y, z))) {
				return Mesh.HOLE;
			}
			return color.color(x, y, z, nx, ny, nz);
		}, breaks);
		b.glass.loft(outer, arcSeg, false, false, (x, y, z, nx, ny, nz) ->
				Math.abs(nx) > 0.5F && inWindow(l, x, y, z) && !inDoor(l, x, y, z) ? GLASS : Mesh.HOLE, breaks);
		// Encadrements
		for (Window w : l.windows) {
			for (int side = -1; side <= 1; side += 2) {
				if (w.side() == 0 || w.side() == side) {
					frame(b.body, side, f(w.z0()), f(w.z1()), f(w.y0()), f(w.y1()), 0.035F, lv.frame);
				}
			}
		}
		for (Door d : l.doors) {
			frame(b.body, d.side(), f(d.z() - d.width() / 2), f(d.z() + d.width() / 2), FLOOR - 0.04F, f(d.top()), 0.04F, lv.frame);
			b.body.box(d.side() * (HW - 0.1F), 0.26F, f(d.z() - d.width() / 2), d.side() * (HW + 0.06F), 0.32F, f(d.z() + d.width() / 2), 0xFF4A4D52);
			doorPanel(b, l, l.doors.indexOf(d), lv.door, lv.frame);
		}
		// Habillage intérieur
		TreeSet<Float> zs = zBreaks(l, innerZ0, innerZ1);
		Section[] inner = straight(zs, HW - 0.07F, FLOOR, innerTop, Math.max(0.05F, roofR - 0.06F), 0.01F);
		b.body.loft(inner, Math.max(3, arcSeg), false, false, (x, y, z, nx, ny, nz) -> {
			if (Math.abs(nx) > 0.5F && (inWindow(l, x, y, z) || inDoor(l, x, y, z))) {
				return Mesh.HOLE;
			}
			if (ny > 0.6F && y < FLOOR + 0.05F) {
				return lv.floor;
			}
			if (y > innerTop - 0.45F) {
				return lv.ceiling;
			}
			return y < FLOOR + 0.85F ? Mesh.darker(lv.wall, 0.85F) : lv.wall;
		}, breaks);
	}

	/** Panneau de porte coulissante (avec hublot et poignée). */
	private static void doorPanel(Builder b, CarLayout l, int index, int color, int frameColor) {
		Door d = l.doors.get(index);
		Mesh m = new Mesh();
		int side = d.side();
		float z0 = f(d.z() - d.width() / 2);
		float z1 = f(d.z() + d.width() / 2);
		float top = f(d.top());
		float xi = side * (HW + 0.022F);
		float xo = side * (HW + 0.05F);
		float x0 = Math.min(xi, xo);
		float x1 = Math.max(xi, xo);
		m.box(x0, FLOOR - 0.02F, z0, x1, top - 0.6F, z1, color);
		m.box(x0, top - 0.12F, z0, x1, top, z1, color);
		m.box(x0, top - 0.6F, z0, x1, top - 0.12F, z0 + 0.1F, color);
		m.box(x0, top - 0.6F, z1 - 0.1F, x1, top - 0.12F, z1, color);
		m.box(x0 + side * 0.005F, top - 0.6F, z0 + 0.1F, x1 - side * 0.005F, top - 0.12F, z1 - 0.1F, GLASS_DARK);
		float hz = d.z() > 0 ? z0 + 0.08F : z1 - 0.12F;
		m.box(side > 0 ? x1 : x0 - 0.03F, 1.35F, hz, side > 0 ? x1 + 0.03F : x0, 1.55F, hz + 0.04F, BRASS);
		m.box(side > 0 ? -x1 - 0.02F : -x0, 1.35F, hz, side > 0 ? -x1 : -x0 + 0.02F, 1.55F, hz + 0.04F, frameColor);
		float slide = f(d.z() > 0 ? -d.width() * 0.92 : d.width() * 0.92);
		b.doors.add(new DoorPanel(index, m, slide));
	}

	/** Voiture voyageurs complète : caisse, vitrages, portes, intérieur, bogies, attelages. */
	private static void coach(Builder b, CarType type, Livery lv, Profile p) {
		CarLayout l = type.layout();
		float h = f(l.halfLength);
		Mesh m = b.body;
		TreeSet<Float> zs = zBreaks(l, -h, h);
		float[] breaks = yBreaks(l, 0.72F, 1.3F, 1.4F, 2.25F);
		Section[] outer = straight(zs, HW, p.bottom, p.top, p.roofR, p.floorR);
		hull(b, l, outer, 5, breaks, (x, y, z, nx, ny, nz) -> lv.outer(y, p.top), f(l.interiorZ0), f(l.interiorZ1),
				p.top - 0.09F, p.roofR, lv);
		// Extrémités avec passage d'intercirculation
		for (int dir = -1; dir <= 1; dir += 2) {
			m.endFrame(s(dir * h, HW, p.bottom, p.top, p.roofR, p.floorR), 5, 0.42F, FLOOR, 2.32F, dir, lv.skirt);
			m.endFrame(s(f(dir > 0 ? l.interiorZ1 : l.interiorZ0), HW - 0.07F, FLOOR, p.top - 0.09F, Math.max(0.05F, p.roofR - 0.06F), 0.01F),
					4, 0.42F, FLOOR, 2.32F, -dir, lv.wall);
			bellows(m, dir * h, dir, 2.45F);
			// Passerelle entre caisse et soufflet
			m.box(-0.42F, FLOOR - 0.05F, Math.min(f(dir > 0 ? l.interiorZ1 : l.interiorZ0), dir * (h + 0.22F)), 0.42F, FLOOR,
					Math.max(f(dir > 0 ? l.interiorZ1 : l.interiorZ0), dir * (h + 0.22F)), 0xFF4A4D52);
			m.box(-0.42F, 2.32F, Math.min(f(dir > 0 ? l.interiorZ1 : l.interiorZ0), dir * h), 0.42F, 2.36F,
					Math.max(f(dir > 0 ? l.interiorZ1 : l.interiorZ0), dir * h), lv.ceiling);
			if (p == Profile.CLASSIC || p == Profile.ORIENT) {
				buffers(m, dir * h, dir, 0.66F, BLACK);
			} else {
				m.box(-0.12F, 0.52F, Math.min(dir * h, dir * (h + 0.3F)), 0.12F, 0.68F, Math.max(dir * h, dir * (h + 0.3F)), 0xFF4A4D52);
			}
			// Feux de fin de convoi
			lamp(b, 0.7F, 0.95F, dir * h, 0.05F, TAILLIGHT, dir);
			lamp(b, -0.7F, 0.95F, dir * h, 0.05F, TAILLIGHT, dir);
		}
		// Châssis et équipements sous caisse
		m.box(-0.62F, 0.34F, -h + 1.0F, 0.62F, p.bottom + 0.02F, h - 1.0F, UNDERFRAME);
		for (float z = -1.4F; z <= 1.41F; z += 1.4F) {
			m.box(-0.5F, 0.28F, z - 0.4F, 0.5F, 0.36F, z + 0.4F, 0xFF2E3136);
		}
		m.cylinder(2, 0.35F, 0.32F, -0.9F, 0.9F, 0.08F, 8, 0xFF3A3D42, 0xFF4A4D52);
		// Toiture : aérateurs ou climatisation
		if (p == Profile.CLASSIC || p == Profile.ORIENT) {
			for (float z = -h + 1.6F; z < h - 1.4F; z += 1.3F) {
				m.cylinder(1, 0, z, p.top - 0.02F, p.top + 0.1F, 0.11F, 10, Mesh.darker(lv.roof, 0.85F), lv.roof);
				m.cylinder(1, 0, z, p.top + 0.1F, p.top + 0.13F, 0.15F, 10, Mesh.darker(lv.roof, 0.7F), Mesh.darker(lv.roof, 0.9F));
			}
		} else {
			m.box(-0.45F, p.top - 0.03F, -h + 1.4F, 0.45F, p.top + 0.08F, -h + 3.0F, Mesh.darker(lv.roof, 0.9F));
			m.box(-0.45F, p.top - 0.03F, h - 3.0F, 0.45F, p.top + 0.08F, h - 1.4F, Mesh.darker(lv.roof, 0.9F));
		}
		if (lv == Livery.ORIENT) {
			// Filets dorés et armoiries
			m.mirroredBox(HW + 0.004F, 0.95F, -h + 0.3F, HW + 0.012F, 0.98F, h - 0.3F, lv.stripe);
			m.mirroredBox(HW + 0.004F, 2.3F, -h + 0.3F, HW + 0.012F, 2.33F, h - 0.3F, lv.stripe);
			m.mirroredBox(HW + 0.004F, 1.05F, -0.25F, HW + 0.015F, 1.3F, 0.25F, lv.stripe);
		}
		if (lv == Livery.SHINKANSEN_GREEN) {
			m.mirroredBox(HW + 0.004F, 1.75F, h - 1.3F, HW + 0.015F, 1.95F, h - 1.1F, 0xFF2E8A4A);
		}
		if (type == CarType.OBSERVATION_CAR) {
			// Dôme panoramique
			b.glass.loft(new Section[]{s(-2.4F, 0.8F, p.top - 0.5F, p.top + 0.35F, 0.6F, 0.01F), s(2.4F, 0.8F, p.top - 0.5F, p.top + 0.35F, 0.6F, 0.01F)},
					5, true, true, (x, y, z, nx, ny, nz) -> y > p.top - 0.15F ? GLASS : Mesh.HOLE);
			m.mirroredBox(0.78F, p.top - 0.2F, -2.45F, 0.84F, p.top + 0.05F, 2.45F, lv.frame);
		}
		details(b, l, lv, p, h);
		// Bogies
		float bogieZ = h - 1.65F;
		bogie(b, -bogieZ, 1.6F, 0.36F);
		bogie(b, bogieZ, 1.6F, 0.36F);
		interior(b, l, lv);
	}

	/** Détails extérieurs : joints de caisse, gouttières, mains courantes, girouettes, équipements sous caisse. */
	private static void details(Builder b, CarLayout l, Livery lv, Profile p, float h) {
		Mesh m = b.body;
		boolean classic = p == Profile.CLASSIC || p == Profile.ORIENT;
		int seam = Mesh.darker(lv.lower, 0.7F);
		// Joints verticaux entre les panneaux de caisse (sous les fenêtres)
		for (float z = -h + 1.2F; z < h - 1.0F; z += 1.5F) {
			if (!nearDoor(l, z)) {
				m.mirroredBox(HW + 0.002F, 0.75F, z, HW + 0.008F, 1.28F, z + 0.025F, seam);
			}
		}
		// Gouttières de toiture
		m.mirroredBox(HW - 0.02F, p.top - 0.44F, -h + 0.15F, HW + 0.02F, p.top - 0.41F, h - 0.15F, Mesh.darker(lv.roof, 0.7F));
		// Mains courantes et lanternes de porte
		for (Door d : l.doors) {
			float x = d.side() * (HW + 0.06F);
			for (float z : new float[]{f(d.z() - d.width() / 2 - 0.08), f(d.z() + d.width() / 2 + 0.08)}) {
				m.beam(v(x, 0.9F, z), v(x, 2.2F, z), 0.035F, classic ? BRASS : 0xFFB8BEC4);
				m.box(Math.min(x, d.side() * HW), 0.9F, z - 0.02F, Math.max(x, d.side() * HW), 0.93F, z + 0.02F, 0xFF8A9096);
				m.box(Math.min(x, d.side() * HW), 2.17F, z - 0.02F, Math.max(x, d.side() * HW), 2.2F, z + 0.02F, 0xFF8A9096);
			}
			float zl = f(d.z());
			if (classic) {
				b.lamps.box(d.side() * (HW + 0.01F) - 0.03F, f(d.top()) + 0.08F, zl - 0.05F, d.side() * (HW + 0.01F) + 0.03F, f(d.top()) + 0.16F, zl + 0.05F, WARM_LIGHT);
			} else {
				// Girouette lumineuse au-dessus de la porte
				float xo = d.side() * (HW + 0.012F);
				m.box(Math.min(xo, d.side() * HW), f(d.top()) + 0.06F, zl - 0.32F, Math.max(xo, d.side() * HW), f(d.top()) + 0.2F, zl + 0.32F, 0xFF15171A);
				b.lamps.box(Math.min(xo + d.side() * 0.002F, xo), f(d.top()) + 0.09F, zl - 0.28F, Math.max(xo + d.side() * 0.002F, xo), f(d.top()) + 0.17F, zl + 0.28F, 0xFFFF9A2E);
			}
		}
		// Plaques et numéros de voiture
		m.mirroredBox(HW + 0.004F, 1.02F, -0.35F, HW + 0.01F, 1.18F, 0.35F, classic ? 0xFFF0E6CF : 0xFFFFFFFF);
		m.mirroredBox(HW + 0.008F, 1.06F, -0.3F, HW + 0.012F, 1.14F, -0.1F, 0xFF22252A);
		m.mirroredBox(HW + 0.008F, 1.06F, 0.0F, HW + 0.012F, 1.14F, 0.12F, 0xFF22252A);
		// Équipements sous caisse : réservoirs d'air, conduites, coffres
		m.cylinder(2, -0.45F, 0.38F, -1.6F, -0.4F, 0.13F, 10, 0xFF4A4D52, 0xFF5A5E63);
		m.cylinder(2, -0.45F, 0.38F, 0.4F, 1.6F, 0.13F, 10, 0xFF4A4D52, 0xFF5A5E63);
		m.cylinder(2, 0.62F, 0.47F, -h + 0.4F, h - 0.4F, 0.025F, 6, 0xFF7A1F1F, 0xFF7A1F1F);
		m.cylinder(2, -0.62F, 0.47F, -h + 0.4F, h - 0.4F, 0.025F, 6, 0xFF2E5A2E, 0xFF2E5A2E);
		m.box(0.25F, 0.26F, -0.3F, 0.7F, 0.42F, 0.3F, 0xFF2E3136);
		m.box(0.27F, 0.3F, -0.25F, 0.71F, 0.38F, -0.22F, 0xFFF2C14E);
	}

	private static boolean nearDoor(CarLayout l, float z) {
		for (Door d : l.doors) {
			if (Math.abs(z - d.z()) < d.width() / 2 + 0.15) {
				return true;
			}
		}
		return false;
	}

	// ==================================================================
	// Intérieurs
	// ==================================================================

	private static void interior(Builder b, CarLayout l, Livery lv) {
		Mesh m = b.body;
		for (Seat seat : l.seats) {
			seat(b, seat, lv);
		}
		for (Prop prop : l.props) {
			prop(b, prop, lv);
		}
		if (l.gangways) {
			// Plafonniers
			for (float z = f(l.interiorZ0) + 1.0F; z < l.interiorZ1 - 0.6F; z += 1.5F) {
				b.lamps.box(-0.2F, f(l.top) - 0.13F, z - 0.15F, 0.2F, f(l.top) - 0.11F, z + 0.15F, WARM_LIGHT);
				m.box(-0.23F, f(l.top) - 0.11F, z - 0.18F, 0.23F, f(l.top) - 0.09F, z + 0.18F, BRASS);
			}
		}
	}

	private static void seat(Builder b, Seat s, Livery lv) {
		Mesh m = b.body;
		Frame fr = new Frame(f(s.x()), f(s.y()), f(s.z()), s.yaw());
		int c = lv.seat;
		int dark = Mesh.darker(c, 0.75F);
		switch (s.style()) {
			case SEAT -> {
				m.box(fr, -0.22F, -0.42F, -0.18F, 0.22F, -0.3F, 0.18F, 0xFF3A3D42);
				m.box(fr, -0.23F, -0.3F, -0.22F, 0.23F, -0.2F, 0.22F, c);
				m.box(fr, -0.23F, -0.3F, -0.3F, 0.23F, 0.38F, -0.22F, c);
				m.box(fr, -0.2F, 0.3F, -0.29F, 0.2F, 0.38F, -0.21F, Mesh.darker(c, 1.2F) | 0xFF000000);
				m.box(fr, -0.25F, -0.2F, -0.2F, -0.22F, -0.05F, 0.12F, dark);
				m.box(fr, 0.22F, -0.2F, -0.2F, 0.25F, -0.05F, 0.12F, dark);
			}
			case ARMCHAIR, DRIVER -> {
				int col = s.style() == CarLayout.SeatStyle.DRIVER ? 0xFF2B2E33 : c;
				m.box(fr, -0.2F, -0.42F, -0.16F, 0.2F, -0.3F, 0.16F, 0xFF3A3D42);
				m.box(fr, -0.26F, -0.3F, -0.24F, 0.26F, -0.16F, 0.24F, col);
				m.box(fr, -0.26F, -0.3F, -0.34F, 0.26F, 0.45F, -0.24F, col);
				m.box(fr, -0.32F, -0.3F, -0.34F, -0.26F, 0.0F, 0.22F, Mesh.darker(col, 0.85F));
				m.box(fr, 0.26F, -0.3F, -0.34F, 0.32F, 0.0F, 0.22F, Mesh.darker(col, 0.85F));
				m.box(fr, -0.33F, 0.0F, -0.34F, -0.25F, 0.03F, 0.24F, lv.accent);
				m.box(fr, 0.25F, 0.0F, -0.34F, 0.33F, 0.03F, 0.24F, lv.accent);
			}
			case SOFA -> {
				m.box(fr, -0.45F, -0.42F, -0.22F, 0.45F, -0.18F, 0.22F, c);
				m.box(fr, -0.45F, -0.42F, -0.34F, 0.45F, 0.35F, -0.22F, c);
				m.box(fr, -0.52F, -0.42F, -0.34F, -0.45F, 0.02F, 0.22F, dark);
				m.box(fr, 0.45F, -0.42F, -0.34F, 0.52F, 0.02F, 0.22F, dark);
				m.box(fr, -0.3F, -0.18F, -0.2F, -0.05F, -0.12F, 0.18F, Mesh.darker(c, 1.15F) | 0xFF000000);
				m.box(fr, 0.05F, -0.18F, -0.2F, 0.3F, -0.12F, 0.18F, Mesh.darker(c, 1.15F) | 0xFF000000);
			}
			case STOOL -> {
				m.cylinder(1, f(s.x()), f(s.z()), FLOOR, f(s.y()) - 0.06F, 0.04F, 6, STEEL, STEEL);
				m.cylinder(1, f(s.x()), f(s.z()), f(s.y()) - 0.08F, f(s.y()), 0.17F, 10, c, Mesh.darker(c, 1.1F) | 0xFF000000);
				m.cylinder(1, f(s.x()), f(s.z()), FLOOR, FLOOR + 0.03F, 0.14F, 10, STEEL, STEEL);
			}
			case BED -> {
				float y = f(s.y());
				m.box(fr, -0.38F, -0.2F, -0.95F, 0.38F, -0.06F, 0.95F, 0xFFEDEBE6);
				m.box(fr, -0.37F, -0.06F, -0.4F, 0.37F, 0.0F, 0.94F, c);
				m.box(fr, -0.3F, -0.06F, -0.92F, 0.3F, 0.04F, -0.62F, 0xFFFFFFFF);
				m.box(fr, -0.37F, -0.065F, -0.42F, 0.37F, 0.005F, -0.36F, 0xFFF4F0E6);
				if (y < FLOOR + 0.6F) {
					m.box(fr, -0.4F, -0.36F, -0.97F, 0.4F, -0.2F, 0.97F, lv.wall);
				}
			}
			case BENCH -> {
			}
		}
	}

	private static void prop(Builder b, Prop p, Livery lv) {
		Mesh m = b.body;
		Box x = p.box();
		float x0 = f(x.x0()), y0 = f(x.y0()), z0 = f(x.z0()), x1 = f(x.x1()), y1 = f(x.y1()), z1 = f(x.z1());
		float cx = (x0 + x1) / 2;
		float cz = (z0 + z1) / 2;
		switch (p.kind()) {
			case TABLE -> {
				boolean classic = lv == Livery.CLASSIC || lv == Livery.ORIENT;
				m.box(x0, y0, z0, x1, y1, z1, classic ? 0xFFF4F0E6 : 0xFFD8D0C2);
				m.box(x0 - 0.01F, y0 - 0.02F, z0 - 0.01F, x1 + 0.01F, y0, z1 + 0.01F, classic ? 0xFFE8E2D4 : 0xFF8A8F96);
				m.box(cx - 0.03F, FLOOR, cz - 0.03F, cx + 0.03F, y0, cz + 0.03F, STEEL);
				if (classic) {
					m.cylinder(1, cx, cz, y1, y1 + 0.1F, 0.025F, 6, 0xC0E8F4FF, 0xC0E8F4FF);
				}
			}
			case ROUND_TABLE -> {
				m.cylinder(1, cx, cz, y0, y1, (x1 - x0) / 2, 14, 0xFF6B4226, 0xFF7A4E2E);
				m.cylinder(1, cx, cz, FLOOR, y0, 0.04F, 6, BRASS, BRASS);
				m.cylinder(1, cx, cz, FLOOR, FLOOR + 0.03F, 0.15F, 10, BRASS, BRASS);
			}
			case COUNTER -> {
				m.box(x0, y0, z0, x1, y1 - 0.05F, z1, 0xFF5A3A22);
				m.box(x0 - 0.03F, y1 - 0.05F, z0, x1 + 0.03F, y1, z1, 0xFF2E2F33);
				for (float z = z0 + 0.15F; z < z1 - 0.1F; z += 0.32F) {
					int col = ((int) (z * 10) & 1) == 0 ? 0xFF2E7D4A : 0xFF8A2A1E;
					m.cylinder(1, cx + 0.05F, z, y1, y1 + 0.18F, 0.035F, 6, col, col);
				}
			}
			case KITCHEN -> {
				m.box(x0, y0, z0, x1, y1, z1, 0xFFC8CDD2);
				m.box(x0, y1, z0, x1, y1 + 0.02F, z1, 0xFF2E2F33);
				for (float z = z0 + 0.25F; z < z1 - 0.1F; z += 0.45F) {
					m.cylinder(1, cx, z, y1 + 0.02F, y1 + 0.04F, 0.1F, 10, 0xFF55595F, 0xFF1A1A1A);
				}
				m.box(x0 - 0.01F, y0 + 0.15F, z0 + 0.1F, x0, y1 - 0.15F, z1 - 0.1F, 0xFF8A9096);
				m.box(x0 + 0.2F, y1 + 0.8F, z0, x1, y1 + 1.15F, z1, 0xFFB0B6BC);
			}
			case SHELF -> {
				m.box(x1 - 0.04F, y0, z0, x1, y1, z1, 0xFF6B4A2E);
				for (float y = y0; y < y1; y += 0.42F) {
					m.box(x0, y, z0, x1, y + 0.03F, z1, 0xFF7A5634);
					for (float z = z0 + 0.05F; z < z1 - 0.3F; z += 0.38F) {
						int col = (((int) (z * 7 + y * 3)) & 3) == 0 ? 0xFF8A5A2E : (((int) (z * 7 + y * 3)) & 3) == 1 ? 0xFF3A5A8A : 0xFFB89A6A;
						m.box(x0 + 0.04F, y + 0.03F, z, x1 - 0.06F, y + 0.03F + Math.min(0.32F, y1 - y - 0.05F), z + 0.3F, col);
					}
				}
			}
			case CABINET -> {
				m.box(x0, y0, z0, x1, y1, z1, 0xFF7A4E2E);
				float face = x0 > 0 ? x0 - 0.005F : x1;
				m.box(face, y0 + 0.05F, cz - 0.005F, face + 0.005F, y1 - 0.05F, cz + 0.005F, 0xFF4A2E1A);
				m.box(face - 0.01F, (y0 + y1) / 2, cz - 0.08F, face + 0.015F, (y0 + y1) / 2 + 0.04F, cz - 0.04F, BRASS);
			}
			case LUGGAGE_RACK -> {
				m.box(x0, y0, z0, x1, y0 + 0.025F, z1, 0xFF9AA0A6);
				for (float z = z0; z < z1; z += 0.6F) {
					m.box(x0 > 0 ? x1 - 0.03F : x0, y0 - 0.1F, z, x0 > 0 ? x1 : x0 + 0.03F, y0, z + 0.03F, 0xFF9AA0A6);
				}
				int i = 0;
				for (float z = z0 + 0.3F; z < z1 - 0.5F; z += 1.15F) {
					int col = switch (i++ % 4) {
						case 0 -> 0xFF7A3A22;
						case 1 -> 0xFF2C4A7A;
						case 2 -> 0xFF5A5E66;
						default -> 0xFF8A6A3A;
					};
					m.box(x0 + 0.02F, y0 + 0.025F, z, x1 - 0.02F, y1 + 0.02F, z + 0.45F, col);
				}
			}
			case DESK -> {
				Frame fr = new Frame(cx, y0, cz, p.yaw());
				float hw = (x1 - x0) / 2;
				float hd = (z1 - z0) / 2;
				m.box(fr, -hw, 0, -hd, hw, (y1 - y0) * 0.75F, hd, 0xFF2B2E33);
				m.box(fr, -hw, (y1 - y0) * 0.75F, -hd, hw, y1 - y0, hd * 0.2F, 0xFF3A3D42);
				b.lamps.box(fr, -hw * 0.6F, (y1 - y0) * 0.78F, -hd + 0.02F, -hw * 0.1F, (y1 - y0) * 0.98F, -hd + 0.03F, 0xFF48C8F0);
				b.lamps.box(fr, hw * 0.1F, (y1 - y0) * 0.78F, -hd + 0.02F, hw * 0.6F, (y1 - y0) * 0.98F, -hd + 0.03F, 0xFF7CE08A);
				m.box(fr, -0.05F, (y1 - y0) * 0.75F, -hd * 0.6F, 0.05F, (y1 - y0) * 0.75F + 0.14F, -hd * 0.45F, 0xFFC0392B);
				b.lamps.box(fr, hw * 0.7F, (y1 - y0) * 0.76F, -hd * 0.3F, hw * 0.8F, (y1 - y0) * 0.78F, -hd * 0.2F, 0xFFFF5040);
			}
			case LAMP -> {
				m.cylinder(1, cx, cz, y0, y1 - 0.12F, 0.015F, 6, BRASS, BRASS);
				b.lamps.cylinder(1, cx, cz, y1 - 0.12F, y1, 0.07F, 8, 0xFFFFD98A, 0xFFFFE6A8);
			}
			case PLANT -> {
				m.cylinder(1, cx, cz, y0, y0 + 0.3F, 0.13F, 10, 0xFF8A4A2A, 0xFF4A2E1A);
				for (int i = 0; i < 5; i++) {
					double a = i * 1.26;
					float lx = cx + (float) Math.cos(a) * 0.08F;
					float lz = cz + (float) Math.sin(a) * 0.08F;
					m.beam(v(cx, y0 + 0.3F, cz), v(lx * 1.6F - cx * 0.6F, y1, lz * 1.6F - cz * 0.6F), 0.08F, i % 2 == 0 ? 0xFF2E7D32 : 0xFF43A047);
				}
			}
			case PIANO -> {
				m.box(x0, y0 + 0.1F, z0, x1, y1, z1, 0xFF111111);
				m.box(x1 - 0.02F, y0 + 0.62F, z0 + 0.05F, x1 + 0.12F, y0 + 0.68F, z1 - 0.05F, 0xFFF8F8F0);
				m.box(cx - 0.04F, y0, cz - 0.04F, cx + 0.04F, y0 + 0.1F, cz + 0.04F, BRASS);
			}
			case STOVE -> {
				m.box(x0, y0, z0, x1, y1 * 0.7F + y0 * 0.3F, z1, 0xFF2A2A2C);
				b.lamps.box(cx - 0.12F, y0 + 0.15F, z0 - 0.005F, cx + 0.12F, y0 + 0.4F, z0, 0xFFFF7A1E);
				m.cylinder(1, cx, cz, y1 * 0.7F + y0 * 0.3F, f(2.8), 0.07F, 8, 0xFF2A2A2C, 0xFF1A1A1A);
			}
			case BUNK_FRAME -> {
				for (float z : new float[]{z0, z1 - 0.05F}) {
					for (float xx : new float[]{x0, x1 - 0.05F}) {
						m.box(xx, y0, z, xx + 0.05F, y1, z + 0.05F, 0xFF6B6F75);
					}
				}
				// Échelle
				m.box(x0, y0, cz - 0.2F, x0 + 0.03F, y1, cz - 0.17F, 0xFF9AA0A6);
				m.box(x0, y0, cz + 0.17F, x0 + 0.03F, y1, cz + 0.2F, 0xFF9AA0A6);
				for (float y = y0 + 0.3F; y < y1; y += 0.3F) {
					m.box(x0, y, cz - 0.2F, x0 + 0.03F, y + 0.03F, cz + 0.2F, 0xFF9AA0A6);
				}
			}
			case PARTITION -> {
				m.box(x0, y0, z0, x1, y1, z1, lv.wall);
				m.box(x0, y0 + 0.9F, z0 - 0.005F, x1, y0 + 0.93F, z1 + 0.005F, lv.accent);
			}
			case HAY -> {
				for (float z = z0; z < z1 - 0.05F; z += 0.34F) {
					m.box(x0, y0, z, x1, y1, z + 0.32F, 0xFFD8B84A);
					m.box(x0 - 0.005F, y0 + 0.1F, z + 0.1F, x1 + 0.005F, y0 + 0.13F, z + 0.13F, 0xFF8A6A2A);
				}
			}
			case STAIRS -> {
				int steps = 6;
				for (int i = 0; i < steps; i++) {
					float sz = z0 + (z1 - z0) * i / steps;
					float sy = y0 + (y1 - y0) * (i + 1) / steps;
					m.box(x0, y0, sz, x1, sy, sz + (z1 - z0) / steps, lv.floor);
				}
				m.box(x1, y0, z0, x1 + 0.03F, y1 + 0.8F, z1, STEEL);
			}
			case MAIL_RACK -> {
				m.box(x1 - 0.04F, y0, z0, x1, y1, z1, 0xFF6B4A2E);
				for (float y = y0 + 0.4F; y < y1; y += 0.22F) {
					m.box(x0, y, z0, x1, y + 0.02F, z1, 0xFF8A6A42);
				}
				for (float z = z0; z < z1; z += 0.25F) {
					m.box(x0, y0 + 0.4F, z, x1, y1, z + 0.02F, 0xFF8A6A42);
				}
				for (float y = y0 + 0.42F; y < y1 - 0.2F; y += 0.44F) {
					for (float z = z0 + 0.04F; z < z1 - 0.2F; z += 0.5F) {
						m.box(x0 + 0.06F, y, z, x1 - 0.06F, y + 0.12F, z + 0.15F, 0xFFF4F0E6);
					}
				}
			}
			case CHANDELIER -> {
				m.cylinder(1, cx, cz, y0 + 0.15F, y1, 0.02F, 6, BRASS, BRASS);
				m.cylinder(1, cx, cz, y0 + 0.1F, y0 + 0.15F, 0.2F, 12, BRASS, BRASS);
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3;
					b.lamps.cylinder(1, cx + (float) Math.cos(a) * 0.17F, cz + (float) Math.sin(a) * 0.17F, y0, y0 + 0.12F, 0.025F, 6, 0xFFFFF0C8, 0xFFFFF0C8);
				}
			}
		}
	}

	// ==================================================================
	// Locomotives à vapeur
	// ==================================================================

	private static void steamLocomotive(Builder b, CarType type, boolean orient) {
		CarLayout l = type.layout();
		Mesh m = b.body;
		float h = f(l.halfLength);
		int body = orient ? 0xFF1B2A57 : 0xFF1F5A35;
		int bodyDark = orient ? 0xFF121C3C : 0xFF173F27;
		int band = orient ? 0xFFD4AF37 : BRASS;
		int red = 0xFFB3261E;
		float cab0 = f(l.interiorZ0);
		float cab1 = f(l.interiorZ1);
		float boilerY = orient ? 1.72F : 1.62F;
		float boilerR = orient ? 0.66F : 0.62F;
		float smoke0 = h - (orient ? 1.15F : 0.75F);
		float driverR = orient ? 0.66F : 0.6F;
		float[] drivers = orient ? new float[]{-1.0F, 0.45F, 1.9F} : new float[]{-0.9F, 0.5F, 1.9F};
		float axleY = driverR + 0.06F;

		// Châssis, tabliers
		m.box(-0.36F, 0.42F, -h + 0.2F, 0.36F, 0.82F, h - 0.15F, BLACK);
		m.mirroredBox(0.5F, 0.94F, cab1, 1.0F, 1.0F, h - 0.15F, 0xFF2A2B2D);
		m.mirroredBox(0.985F, 0.88F, cab1, 1.02F, 0.99F, h - 0.15F, red);
		for (int dir = -1; dir <= 1; dir += 2) {
			m.box(-1.0F, 0.52F, dir > 0 ? h - 0.17F : -h, 1.0F, 0.95F, dir > 0 ? h : -h + 0.17F, red);
			buffers(m, dir * h, dir, 0.74F, red);
		}
		// Chaudière
		m.cylinder(2, 0, boilerY, cab1, smoke0, boilerR, 22, body, body);
		for (float z = cab1 + 0.5F; z < smoke0 - 0.1F; z += 1.3F) {
			m.cylinder(2, 0, boilerY, z, z + 0.07F, boilerR + 0.015F, 22, band, band);
		}
		m.cylinder(2, 0, boilerY, smoke0, h - 0.25F, boilerR + 0.04F, 22, 0xFF1D1D1F, 0xFF262628);
		m.cylinder(2, 0, boilerY, h - 0.25F, h - 0.2F, boilerR - 0.18F, 20, 0xFF2E2E31, 0xFF2E2E31);
		m.cylinder(2, 0, boilerY, h - 0.2F, h - 0.16F, 0.08F, 8, band, band);
		m.box(-0.2F, boilerY - 0.62F, h - 0.25F, 0.2F, boilerY - 0.5F, h - 0.18F, band);
		// Cheminée, dômes, sifflet
		float chimneyZ = h - (orient ? 0.75F : 0.6F);
		m.cylinder(1, 0, chimneyZ, boilerY + 0.45F, boilerY + 1.3F, orient ? 0.24F : 0.19F, 14, 0xFF151516, 0xFF080808);
		m.cylinder(1, 0, chimneyZ, boilerY + 1.22F, boilerY + 1.36F, orient ? 0.3F : 0.26F, 14, 0xFF1F1F21, 0xFF0A0A0A);
		m.cylinder(1, 0, cab1 + 2.2F, boilerY + 0.4F, boilerY + 0.82F, 0.28F, 16, band, 0xFFE0BC55);
		m.cylinder(1, 0, cab1 + 1.1F, boilerY + 0.4F, boilerY + 0.7F, 0.22F, 14, body, body);
		m.cylinder(1, 0.12F, cab1 + 0.2F, boilerY + 0.5F, boilerY + 0.85F, 0.045F, 6, band, band);
		m.cylinder(1, -0.12F, cab1 + 0.2F, boilerY + 0.5F, boilerY + 0.78F, 0.035F, 6, band, band);
		// Rambardes le long de la chaudière
		m.mirroredBox(boilerR + 0.06F, boilerY + 0.2F, cab1 + 0.1F, boilerR + 0.09F, boilerY + 0.23F, smoke0, 0xFFC8CDD2);
		// Phares et plaque
		lamp(b, 0, boilerY + 0.55F, h - 0.18F, 0.1F, HEADLIGHT, 1);
		lamp(b, -0.55F, 1.08F, h, 0.08F, HEADLIGHT, 1);
		lamp(b, 0.55F, 1.08F, h, 0.08F, HEADLIGHT, 1);
		if (orient) {
			// Écrans pare-fumée
			m.mirroredBox(0.86F, 1.0F, smoke0 - 0.45F, 0.89F, boilerY + 0.3F, h - 0.45F, 0xFF121C3C);
			m.mirroredBox(0.89F, boilerY + 0.2F, smoke0 - 0.4F, 0.9F, boilerY + 0.24F, h - 0.5F, band);
			m.mirroredBox(boilerR + 0.04F, boilerY - 0.05F, smoke0 + 0.1F, 0.86F, boilerY, smoke0 + 0.15F, 0xFF121C3C);
		}
		// Cylindres et glissières
		float cylZ = drivers[2] + 0.75F;
		m.cylinder(2, 0.78F, 0.88F, cylZ, cylZ + 1.0F, 0.25F, 14, 0xFF2A2A2D, 0xFF3A3A3D);
		m.cylinder(2, -0.78F, 0.88F, cylZ, cylZ + 1.0F, 0.25F, 14, 0xFF2A2A2D, 0xFF3A3A3D);
		m.mirroredBox(0.6F, 0.82F, drivers[1], 0.64F, 0.86F, cylZ, STEEL);
		m.mirroredBox(0.6F, 0.9F, drivers[1], 0.64F, 0.94F, cylZ, STEEL);
		// Garde-boue
		for (float z : drivers) {
			m.mirroredBox(0.36F, 0.98F, z - driverR, 0.62F, 1.02F, z + driverR, 0xFF2A2B2D);
		}

		// Cabine ouverte à l'arrière, avec portes et intérieur
		float roof = 2.78F;
		Door door = l.doors.get(0);
		float d0 = f(door.z() - door.width() / 2);
		float d1 = f(door.z() + door.width() / 2);
		m.box(-0.98F, 0.42F, cab0 - 0.1F, 0.98F, FLOOR, cab1, 0xFF3A2E22);
		for (int side = -1; side <= 1; side += 2) {
			float xi = side * 0.9F;
			float xo = side * 0.98F;
			float x0 = Math.min(xi, xo);
			float x1 = Math.max(xi, xo);
			m.box(x0, FLOOR, d1, x1, 1.92F, cab1, body);
			m.box(x0, 2.48F, d1, x1, roof, cab1, body);
			m.box(x0, 1.92F, cab1 - 0.15F, x1, 2.48F, cab1, body);
			m.box(x0, 1.92F, d1, x1, 2.48F, d1 + 0.12F, body);
			m.box(x0, FLOOR, cab0, x1, roof, d0, body);
			m.box(x0, 2.35F, d0, x1, roof, d1, body);
			m.box(side > 0 ? xo : xo - 0.01F, 1.0F, cab0, side > 0 ? xo + 0.01F : xo, 1.04F, cab1, band);
			b.glass.box(x0 + 0.02F, 1.92F, d1 + 0.12F, x1 - 0.02F, 2.48F, cab1 - 0.15F, GLASS);
			// Demi-porte de cabine
			Mesh dm = new Mesh();
			dm.box(x0 - side * 0.01F, FLOOR, d0, x1 + side * 0.01F, 1.55F, d1, bodyDark);
			dm.box(side > 0 ? x1 + 0.01F : x0 - 0.03F, 1.2F, d1 - 0.12F, side > 0 ? x1 + 0.03F : x0 - 0.01F, 1.3F, d1 - 0.08F, band);
			int index = 0;
			for (int i = 0; i < l.doors.size(); i++) {
				if (l.doors.get(i).side() == side) {
					index = i;
				}
			}
			b.doors.add(new DoorPanel(index, dm, f(-door.width() * 0.9)));
		}
		// Façade avant de cabine (fond de foyer) avec hublots
		m.box(-0.98F, FLOOR, cab1 - 0.06F, -0.75F, roof, cab1, body);
		m.box(0.75F, FLOOR, cab1 - 0.06F, 0.98F, roof, cab1, body);
		m.box(-0.75F, FLOOR, cab1 - 0.06F, 0.75F, 1.95F, cab1, body);
		m.box(-0.75F, 2.45F, cab1 - 0.06F, 0.75F, roof, cab1, body);
		m.box(-0.25F, 1.95F, cab1 - 0.06F, 0.25F, 2.45F, cab1, body);
		b.glass.box(-0.75F, 1.95F, cab1 - 0.04F, -0.25F, 2.45F, cab1 - 0.02F, GLASS);
		b.glass.box(0.25F, 1.95F, cab1 - 0.04F, 0.75F, 2.45F, cab1 - 0.02F, GLASS);
		m.box(-1.06F, roof, cab0 - 0.15F, 1.06F, roof + 0.08F, cab1 + 0.05F, 0xFF222325);
		m.box(-0.9F, roof + 0.08F, cab0 + 0.3F, 0.9F, roof + 0.12F, cab1 - 0.3F, 0xFF2B2C2E);
		// Plaque de foyer : porte rougeoyante, manomètres, régulateur
		m.box(-0.6F, FLOOR, cab1 - 0.2F, 0.6F, 1.9F, cab1 - 0.06F, 0xFF2A2A2C);
		b.lamps.box(-0.2F, FLOOR + 0.35F, cab1 - 0.21F, 0.2F, FLOOR + 0.65F, cab1 - 0.2F, 0xFFFF7A1E);
		m.box(-0.24F, FLOOR + 0.3F, cab1 - 0.22F, 0.24F, FLOOR + 0.33F, cab1 - 0.2F, band);
		for (float x : new float[]{-0.35F, 0.0F, 0.35F}) {
			m.cylinder(2, x, 1.65F, cab1 - 0.26F, cab1 - 0.2F, 0.09F, 10, band, 0xFFF4F0E6);
		}
		m.beam(v(0.3F, 1.3F, cab1 - 0.22F), v(0.35F, 1.55F, cab1 - 0.45F), 0.04F, 0xFFC0392B);
		m.box(-0.98F, FLOOR, cab0 - 0.1F, 0.98F, FLOOR + 0.05F, cab0, 0xFF2A2B2D);

		// Roues : bissel / bogie avant, roues motrices, essieu porteur arrière
		if (orient) {
			b.axles.add(new Axle(h - 1.05F, 0.36F, GAUGE, WheelStyle.STEAM_SMALL));
			b.axles.add(new Axle(h - 1.85F, 0.36F, GAUGE, WheelStyle.STEAM_SMALL));
		} else {
			b.axles.add(new Axle(h - 1.0F, 0.34F, GAUGE, WheelStyle.STEAM_SMALL));
		}
		for (float z : drivers) {
			b.axles.add(new Axle(z, driverR, GAUGE, WheelStyle.STEAM_DRIVER));
		}
		b.axles.add(new Axle(-h + 1.3F, 0.4F, GAUGE, WheelStyle.STEAM_SMALL));
		b.rods = new Rods(drivers, driverR, driverR * 0.4F, cylZ, 0.88F, GAUGE + 0.13F);
		interior(b, l, orient ? Livery.ORIENT : Livery.CLASSIC);
	}

	private static void tender(Builder b, CarType type) {
		Mesh m = b.body;
		int green = 0xFF1F5A35;
		float h = f(type.length / 2);
		m.box(-0.55F, 0.42F, -h + 0.1F, 0.55F, 0.78F, h - 0.1F, BLACK);
		m.box(-0.98F, 0.78F, -h + 0.25F, 0.98F, 2.05F, h - 0.25F, green);
		m.box(-1.02F, 0.78F, -h + 0.21F, 1.02F, 0.86F, h - 0.21F, 0xFF2A2B2D);
		m.box(-1.02F, 2.0F, -h + 0.21F, 1.02F, 2.12F, h - 0.21F, 0xFF151617);
		m.mirroredBox(0.981F, 1.1F, -h + 0.55F, 0.99F, 1.14F, h - 0.55F, BRASS);
		m.mirroredBox(0.981F, 1.7F, -h + 0.55F, 0.99F, 1.74F, h - 0.55F, BRASS);
		for (float z = -h + 0.7F; z < h - 0.5F; z += 0.8F) {
			m.mirroredBox(0.98F, 0.86F, z, 1.0F, 2.0F, z + 0.04F, Mesh.darker(green, 0.85F));
		}
		int coal = 0xFF141414;
		int coal2 = 0xFF242424;
		m.box(-0.9F, 2.05F, -0.6F, 0.9F, 2.3F, h - 0.4F, coal);
		m.box(-0.75F, 2.3F, -0.2F, 0.7F, 2.48F, h - 0.6F, coal2);
		m.box(-0.5F, 2.48F, 0.3F, 0.45F, 2.6F, h - 1.0F, coal);
		m.box(-0.2F, 2.6F, 0.7F, 0.25F, 2.66F, 1.2F, coal2);
		m.box(-0.9F, 2.05F, -h + 0.35F, 0.9F, 2.15F, -0.6F, 0xFF1A4A2C);
		m.cylinder(1, 0, -1.7F, 2.15F, 2.25F, 0.22F, 12, BLACK, 0xFF2A2B2D);
		// Échelles arrière
		m.mirroredBox(0.6F, 0.9F, -h + 0.21F, 0.64F, 2.1F, -h + 0.25F, 0xFFC8CDD2);
		for (int dir = -1; dir <= 1; dir += 2) {
			buffers(m, dir * h, dir, 0.7F, 0xFFB3261E);
		}
		for (float z : new float[]{-1.6F, 0.0F, 1.6F}) {
			b.axles.add(new Axle(z, 0.4F, GAUGE, WheelStyle.STEAM_SMALL));
			m.mirroredBox(GAUGE + 0.1F, 0.35F, z - 0.18F, GAUGE + 0.26F, 0.75F, z + 0.18F, 0xFF2A2B2D);
		}
		lamp(b, 0.7F, 1.0F, -h, 0.06F, TAILLIGHT, -1);
		lamp(b, -0.7F, 1.0F, -h, 0.06F, TAILLIGHT, -1);
	}

	// ==================================================================
	// Locomotive diesel (style « cowl », jaune et rouge)
	// ==================================================================

	private static void dieselLocomotive(Builder b, CarType type) {
		CarLayout l = type.layout();
		Mesh m = b.body;
		float h = f(l.halfLength);
		int yellow = 0xFFF2B21C;
		int red = 0xFFC0392B;
		int grey = 0xFF4A4E55;
		float cab0 = f(l.interiorZ0);
		float cab1 = f(l.interiorZ1);
		Livery lv = Livery.DIESEL;
		Mesh.ColorFunction paint = (x, y, z, nx, ny, nz) -> {
			if (y < 0.95F) {
				return grey;
			}
			if (y > 1.05F && y < 1.25F) {
				return red;
			}
			if (ny > 0.7F && y > 2.6F) {
				return 0xFFD9D9D2;
			}
			return yellow;
		};
		// Capot moteur (plein)
		Section[] hood = smooth(new Section[]{
				s(-h + 0.15F, 0.9F, 0.85F, 2.4F, 0.35F, 0.05F),
				s(-h + 0.4F, 0.95F, 0.85F, 2.62F, 0.4F, 0.05F),
				s(cab0, 0.95F, 0.85F, 2.62F, 0.4F, 0.05F)}, 2);
		m.loft(hood, 4, true, false, paint);
		// Cabine avec fenêtres et portes, nez incliné
		TreeSet<Float> zs = zBreaks(l, cab0, cab1 - 0.25F);
		Section[] cab = straight(zs, HW, 0.85F, 2.95F, 0.35F, 0.05F);
		Section[] nose = new Section[]{
				s(cab1 - 0.25F, HW, 0.85F, 2.95F, 0.35F, 0.05F),
				s(cab1 + 0.05F, HW, 0.85F, 2.55F, 0.3F, 0.05F),
				s(cab1 + 0.3F, HW, 0.85F, 1.85F, 0.2F, 0.05F),
				s(h - 0.12F, HW, 0.85F, 1.7F, 0.15F, 0.05F)};
		float[] breaks = yBreaks(l, 0.95F, 1.05F, 1.25F);
		Mesh.ColorFunction windshield = (x, y, z, nx, ny, nz) -> z > cab1 - 0.3F && z < cab1 + 0.2F && y > 1.95F && y < 2.75F && Math.abs(x) < 0.85F
				&& Math.abs(x) > 0.06F && nz > 0.3F ? Mesh.HOLE : paint.color(x, y, z, nx, ny, nz);
		hull(b, l, cab, 4, breaks, paint, cab0, cab1 - 0.3F, 2.86F, 0.35F, lv);
		m.loft(nose, 4, false, true, windshield);
		b.glass.loft(nose, 4, false, false, (x, y, z, nx, ny, nz) ->
				windshield.color(x, y, z, nx, ny, nz) == Mesh.HOLE ? GLASS : Mesh.HOLE);
		m.endFrame(s(cab0, HW - 0.07F, FLOOR, 2.86F, 0.3F, 0.01F), 4, 0.0F, FLOOR, FLOOR, -1, 0xFFB8B4A8);
		// Calandre, grilles et passerelles avec garde-corps
		for (float z = -h + 0.8F; z < cab0 - 0.4F; z += 1.6F) {
			m.mirroredBox(0.95F, 1.4F, z, 0.97F, 2.3F, z + 1.1F, 0xFF3A3D42);
			for (float zz = z + 0.06F; zz < z + 1.05F; zz += 0.12F) {
				m.mirroredBox(0.97F, 1.42F, zz, 0.985F, 2.28F, zz + 0.04F, 0xFF55595F);
			}
		}
		m.box(-0.35F, 2.62F, -1.0F, 0.35F, 2.8F, 0.8F, 0xFF3A3D42);
		m.cylinder(1, 0, 0.6F, 2.62F, 3.0F, 0.12F, 10, 0xFF2A2A2D, 0xFF0A0A0A);
		for (float z = -2.8F; z < -1.2F; z += 0.55F) {
			m.cylinder(1, 0, z, 2.62F, 2.66F, 0.22F, 12, 0xFF2E3136, 0xFF55595F);
		}
		// Châssis, réservoir, chasse-neige
		m.box(-1.02F, 0.72F, -h, 1.02F, 0.85F, h, 0xFF2A2B2D);
		m.mirroredBox(1.0F, 0.85F, -h + 0.1F, 1.04F, 0.88F, h - 0.1F, yellow);
		m.box(-0.75F, 0.28F, -1.3F, 0.75F, 0.72F, 1.3F, 0xFF2E3136);
		m.box(-0.76F, 0.5F, -1.3F, 0.76F, 0.52F, 1.3F, yellow);
		for (int dir = -1; dir <= 1; dir += 2) {
			float zEnd = dir * h;
			m.box(-1.0F, 0.3F, Math.min(zEnd, zEnd - dir * 0.15F), 1.0F, 0.72F, Math.max(zEnd, zEnd - dir * 0.15F), 0xFF2A2B2D);
			for (float x = -0.9F; x < 0.95F; x += 0.3F) {
				m.box(x, 0.3F, Math.min(zEnd, zEnd + dir * 0.05F), x + 0.15F, 0.72F, Math.max(zEnd, zEnd + dir * 0.05F), yellow);
			}
			m.box(-0.12F, 0.5F, Math.min(zEnd, zEnd + dir * 0.32F), 0.12F, 0.68F, Math.max(zEnd, zEnd + dir * 0.32F), 0xFF4A4D52);
			// Garde-corps aux extrémités
			for (int side = -1; side <= 1; side += 2) {
				m.beam(v(side * 0.98F, 0.88F, zEnd - dir * 0.05F), v(side * 0.98F, 1.75F, zEnd - dir * 0.05F), 0.05F, yellow);
			}
			m.beam(v(-0.98F, 1.75F, zEnd - dir * 0.05F), v(-0.4F, 1.75F, zEnd - dir * 0.05F), 0.05F, yellow);
			m.beam(v(0.4F, 1.75F, zEnd - dir * 0.05F), v(0.98F, 1.75F, zEnd - dir * 0.05F), 0.05F, yellow);
		}
		// Mains courantes le long du capot et sablières
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 0.99F;
			m.beam(v(x, 1.35F, -h + 0.3F), v(x, 1.35F, cab0), 0.035F, 0xFFF2C14E);
			for (float z = -h + 0.3F; z < cab0; z += 1.4F) {
				m.box(side > 0 ? 0.95F : -1.0F, 1.33F, z - 0.02F, side > 0 ? 1.0F : -0.95F, 1.37F, z + 0.02F, 0xFFF2C14E);
			}
			m.box(side * 0.82F - 0.12F, 0.5F, h - 2.9F, side * 0.82F + 0.12F, 0.72F, h - 2.6F, 0xFF2E3136);
		}
		m.cylinder(2, 0.15F, 3.02F, cab1 - 0.9F, cab1 - 0.5F, 0.06F, 8, 0xFFB8BEC4, 0xFF2A2A2D);
		m.cylinder(2, -0.15F, 3.02F, cab1 - 0.95F, cab1 - 0.55F, 0.05F, 8, 0xFFB8BEC4, 0xFF2A2A2D);
		// Phares
		lamp(b, -0.5F, 2.75F, h - 0.85F, 0.09F, HEADLIGHT, 1);
		lamp(b, 0.5F, 2.75F, h - 0.85F, 0.09F, HEADLIGHT, 1);
		lamp(b, -0.6F, 1.45F, h - 0.12F, 0.1F, HEADLIGHT, 1);
		lamp(b, 0.6F, 1.45F, h - 0.12F, 0.1F, HEADLIGHT, 1);
		lamp(b, -0.6F, 1.6F, -h + 0.15F, 0.08F, TAILLIGHT, -1);
		lamp(b, 0.6F, 1.6F, -h + 0.15F, 0.08F, TAILLIGHT, -1);
		m.cylinder(1, 0.3F, cab1 - 0.8F, 2.95F, 3.08F, 0.05F, 6, 0xFFB8BEC4, 0xFFB8BEC4);
		m.cylinder(2, 0.3F, 3.1F, cab1 - 1.0F, cab1 - 0.6F, 0.05F, 8, 0xFFB8BEC4, 0xFFB8BEC4);
		// Bogies à trois essieux
		for (float bz : new float[]{-h + 2.0F, h - 2.0F}) {
			float x0 = GAUGE + 0.08F;
			m.mirroredBox(x0, 0.18F, bz - 1.25F, x0 + 0.12F, 0.5F, bz + 1.25F, BOGIE);
			for (float az : new float[]{bz - 0.85F, bz, bz + 0.85F}) {
				b.axles.add(new Axle(az, 0.36F, GAUGE, WheelStyle.MODERN));
				m.mirroredBox(x0 - 0.02F, 0.3F, az - 0.13F, x0 + 0.16F, 0.52F, az + 0.13F, 0xFF3C4046);
				m.cylinder(1, x0 + 0.06F, az + 0.4F, 0.5F, 0.72F, 0.07F, 8, 0xFF6A6E73, 0xFF6A6E73);
				m.cylinder(1, -x0 - 0.06F, az + 0.4F, 0.5F, 0.72F, 0.07F, 8, 0xFF6A6E73, 0xFF6A6E73);
			}
		}
		interior(b, l, Livery.DIESEL);
	}

	// ==================================================================
	// Locomotive électrique (type BB, deux cabines)
	// ==================================================================

	private static void electricLocomotive(Builder b, CarType type) {
		CarLayout l = type.layout();
		Mesh m = b.body;
		float h = f(l.halfLength);
		int silver = 0xFFC9CED4;
		int red = 0xFFB0283A;
		int dark = 0xFF2F333A;
		Mesh.ColorFunction paint = (x, y, z, nx, ny, nz) -> {
			if (y < 0.9F) {
				return dark;
			}
			if (Math.abs(z) > h - 1.0F && nz * Math.signum(z) > 0.2F) {
				return red;
			}
			if (y > 1.2F && y < 1.35F) {
				return red;
			}
			if (ny > 0.7F) {
				return 0xFF9EA4AA;
			}
			return silver;
		};
		float front = h - 0.35F;
		float[] breaks = yBreaks(l, 0.9F, 1.2F, 1.35F);
		Section[] body = new Section[]{
				s(-h + 0.05F, 0.8F, 0.75F, 2.1F, 0.25F, 0.08F),
				s(-front, HW, 0.75F, 2.6F, 0.3F, 0.08F),
				s(-front + 0.25F, HW, 0.75F, 2.95F, 0.4F, 0.1F)};
		TreeSet<Float> zs = zBreaks(l, -front + 0.25F, front - 0.25F);
		Section[] mid = straight(zs, HW, 0.75F, 2.95F, 0.4F, 0.1F);
		Section[] tail = new Section[]{
				s(front - 0.25F, HW, 0.75F, 2.95F, 0.4F, 0.1F),
				s(front, HW, 0.75F, 2.6F, 0.3F, 0.08F),
				s(h - 0.05F, 0.8F, 0.75F, 2.1F, 0.25F, 0.08F)};
		Mesh.ColorFunction wind = (x, y, z, nx, ny, nz) -> Math.abs(z) > front - 0.3F && y > 2.0F && y < 2.85F && Math.abs(x) < 0.85F
				&& Math.abs(x) > 0.05F && Math.abs(nz) > 0.3F ? Mesh.HOLE : paint.color(x, y, z, nx, ny, nz);
		m.loft(body, 4, true, false, wind);
		m.loft(tail, 4, false, true, wind);
		b.glass.loft(body, 4, false, false, (x, y, z, nx, ny, nz) -> wind.color(x, y, z, nx, ny, nz) == Mesh.HOLE ? GLASS : Mesh.HOLE);
		b.glass.loft(tail, 4, false, false, (x, y, z, nx, ny, nz) -> wind.color(x, y, z, nx, ny, nz) == Mesh.HOLE ? GLASS : Mesh.HOLE);
		// Les cabines sont aux deux bouts ; le compartiment machine est opaque
		b.body.loft(mid, 4, false, false, (x, y, z, nx, ny, nz) -> {
			if (Math.abs(nx) > 0.5F && (inWindow(l, x, y, z) || inDoor(l, x, y, z))) {
				return Mesh.HOLE;
			}
			if (Math.abs(nx) > 0.5F && Math.abs(z) < 2.4F && y > 1.55F && y < 2.4F) {
				return periodic(z, -2.4F, 0.24F, 0.08F) ? 0xFF55595F : 0xFF8A9096;
			}
			return paint.color(x, y, z, nx, ny, nz);
		}, breaks);
		b.glass.loft(mid, 4, false, false, (x, y, z, nx, ny, nz) ->
				Math.abs(nx) > 0.5F && inWindow(l, x, y, z) && !inDoor(l, x, y, z) ? GLASS : Mesh.HOLE, breaks);
		for (int i = 0; i < l.doors.size(); i++) {
			Door d = l.doors.get(i);
			frame(m, d.side(), f(d.z() - d.width() / 2), f(d.z() + d.width() / 2), FLOOR - 0.04F, f(d.top()), 0.04F, dark);
			doorPanel(b, l, i, silver, dark);
		}
		for (Window w : l.windows) {
			for (int side = -1; side <= 1; side += 2) {
				frame(m, side, f(w.z0()), f(w.z1()), f(w.y0()), f(w.y1()), 0.03F, dark);
			}
		}
		// Cloisons des cabines et plancher
		float cabInner = h - 2.35F;
		for (int dir = -1; dir <= 1; dir += 2) {
			m.box(-0.9F, FLOOR - 0.05F, Math.min(dir * cabInner, dir * front), 0.9F, FLOOR, Math.max(dir * cabInner, dir * front), 0xFF4A4E55);
			m.box(-0.9F, FLOOR, Math.min(dir * cabInner, dir * (cabInner - 0.05F)), 0.9F, 2.85F, Math.max(dir * cabInner, dir * (cabInner - 0.05F)), 0xFFB8B4A8);
			m.box(-0.9F, 2.8F, Math.min(dir * cabInner, dir * front), 0.9F, 2.85F, Math.max(dir * cabInner, dir * front), 0xFFE8E4D8);
			lamp(b, -0.55F, 1.15F, dir * (h - 0.05F), 0.09F, dir > 0 ? HEADLIGHT : TAILLIGHT, dir);
			lamp(b, 0.55F, 1.15F, dir * (h - 0.05F), 0.09F, dir > 0 ? HEADLIGHT : TAILLIGHT, dir);
			lamp(b, 0.0F, 2.55F, dir * (front + 0.02F), 0.07F, HEADLIGHT, dir);
			buffers(m, dir * h, dir, 0.62F, dark);
		}
		// Toiture : pantographes, isolateurs, disjoncteur
		pantograph(m, 2.2F, 2.95F);
		pantograph(m, -2.2F, 2.95F);
		m.box(-0.3F, 2.95F, -0.8F, 0.3F, 3.1F, 0.8F, 0xFF9EA4AA);
		for (float z = -1.2F; z <= 1.2F; z += 0.6F) {
			m.cylinder(1, 0.45F, z, 2.95F, 3.15F, 0.06F, 8, 0xFFB5463A, 0xFFB5463A);
		}
		m.box(-0.75F, 0.3F, -1.2F, 0.75F, 0.75F, 1.2F, 0xFF2E3136);
		m.beam(v(0.45F, 3.12F, -2.0F), v(0.45F, 3.12F, 2.0F), 0.04F, 0xFFB5463A);
		m.beam(v(-0.45F, 3.12F, -1.2F), v(-0.45F, 3.12F, 1.2F), 0.04F, 0xFFB5463A);
		for (int dir = -1; dir <= 1; dir += 2) {
			// Mains courantes et marchepieds de cabine
			for (int side = -1; side <= 1; side += 2) {
				float x = side * (HW + 0.05F);
				float z = dir * (h - 2.05F);
				m.beam(v(x, 0.9F, z), v(x, 2.3F, z), 0.035F, 0xFFB8BEC4);
				m.box(side * (HW - 0.08F), 0.3F, z - 0.25F, side * (HW + 0.06F), 0.35F, z + 0.25F, 0xFF4A4D52);
			}
			m.box(-0.4F, 2.25F, dir * (front + 0.01F) - 0.01F, 0.4F, 2.35F, dir * (front + 0.01F) + 0.01F, 0xFF22252A);
		}
		bogie(b, -h + 2.1F, 1.7F, 0.38F);
		bogie(b, h - 2.1F, 1.7F, 0.38F);
		interior(b, l, Livery.ELECTRIC);
	}

	private static void pantograph(Mesh m, float z, float roofY) {
		int metal = 0xFF9EA4AA;
		int dark = 0xFF3A3D42;
		Vector3f a = v(0, roofY + 0.18F, z - 0.6F);
		Vector3f bb = v(0, roofY + 0.62F, z + 0.25F);
		Vector3f c = v(0, roofY + 0.98F, z - 0.35F);
		m.box(-0.35F, roofY, z - 0.75F, 0.35F, roofY + 0.18F, z - 0.45F, dark);
		for (float x : new float[]{-0.3F, 0.3F}) {
			m.cylinder(1, x, z + 0.4F, roofY, roofY + 0.2F, 0.07F, 8, 0xFFB5463A, 0xFFB5463A);
			m.cylinder(1, x, z - 0.6F, roofY, roofY + 0.2F, 0.07F, 8, 0xFFB5463A, 0xFFB5463A);
		}
		m.beam(new Vector3f(a).add(0.18F, 0, 0), new Vector3f(bb).add(0.12F, 0, 0), 0.06F, metal);
		m.beam(new Vector3f(a).add(-0.18F, 0, 0), new Vector3f(bb).add(-0.12F, 0, 0), 0.06F, metal);
		m.beam(new Vector3f(bb).add(0.1F, 0, 0), new Vector3f(c).add(0.03F, 0, 0), 0.05F, metal);
		m.beam(new Vector3f(bb).add(-0.1F, 0, 0), new Vector3f(c).add(-0.03F, 0, 0), 0.05F, metal);
		m.box(-0.75F, c.y - 0.02F, c.z - 0.06F, 0.75F, c.y + 0.05F, c.z + 0.06F, 0xFF55595F);
		m.box(-0.8F, c.y - 0.04F, c.z - 0.04F, -0.7F, c.y + 0.02F, c.z + 0.04F, dark);
		m.box(0.7F, c.y - 0.04F, c.z - 0.04F, 0.8F, c.y + 0.02F, c.z + 0.04F, dark);
	}

	// ==================================================================
	// Motrices à grande vitesse (TGV, Eurostar, ICE, Shinkansen)
	// ==================================================================

	enum Nose {
		TGV(0.55F, 2.85F, 0.5F, 0.15F, new float[][]{
				{0.0F, 0.98F, 0.55F, 2.85F, 0.5F, 0.15F},
				{0.9F, 0.98F, 0.56F, 2.78F, 0.55F, 0.16F},
				{1.7F, 0.95F, 0.58F, 2.45F, 0.62F, 0.18F},
				{2.35F, 0.88F, 0.6F, 1.95F, 0.62F, 0.2F},
				{2.8F, 0.76F, 0.62F, 1.45F, 0.5F, 0.2F},
				{3.08F, 0.58F, 0.66F, 1.08F, 0.32F, 0.18F},
				{3.2F, 0.34F, 0.72F, 0.9F, 0.12F, 0.1F}}, 1.15F, 2.55F, 1.65F),
		EUROSTAR(0.55F, 2.85F, 0.5F, 0.15F, new float[][]{
				{0.0F, 0.98F, 0.55F, 2.85F, 0.5F, 0.15F},
				{1.0F, 0.98F, 0.56F, 2.72F, 0.55F, 0.16F},
				{1.9F, 0.95F, 0.58F, 2.3F, 0.62F, 0.18F},
				{2.6F, 0.86F, 0.6F, 1.8F, 0.6F, 0.2F},
				{3.1F, 0.72F, 0.62F, 1.35F, 0.45F, 0.2F},
				{3.4F, 0.52F, 0.66F, 1.0F, 0.28F, 0.16F},
				{3.5F, 0.3F, 0.72F, 0.85F, 0.1F, 0.08F}}, 1.3F, 2.75F, 1.6F),
		ICE(0.5F, 2.85F, 0.6F, 0.25F, new float[][]{
				{0.0F, 0.98F, 0.5F, 2.85F, 0.6F, 0.25F},
				{0.8F, 0.98F, 0.52F, 2.75F, 0.65F, 0.27F},
				{1.6F, 0.95F, 0.55F, 2.4F, 0.75F, 0.3F},
				{2.3F, 0.88F, 0.58F, 1.95F, 0.72F, 0.3F},
				{2.8F, 0.74F, 0.62F, 1.5F, 0.6F, 0.28F},
				{3.15F, 0.52F, 0.68F, 1.15F, 0.42F, 0.22F},
				{3.3F, 0.22F, 0.78F, 0.98F, 0.1F, 0.08F}}, 1.0F, 2.45F, 1.7F),
		SHINKANSEN(0.5F, 2.8F, 0.6F, 0.25F, new float[][]{
				{0.0F, 0.99F, 0.5F, 2.8F, 0.6F, 0.25F},
				{0.9F, 0.99F, 0.5F, 2.72F, 0.62F, 0.25F},
				{1.8F, 0.98F, 0.52F, 2.42F, 0.7F, 0.26F},
				{2.7F, 0.95F, 0.55F, 2.0F, 0.74F, 0.27F},
				{3.5F, 0.9F, 0.57F, 1.58F, 0.68F, 0.27F},
				{4.2F, 0.8F, 0.6F, 1.22F, 0.52F, 0.26F},
				{4.7F, 0.62F, 0.62F, 0.98F, 0.33F, 0.2F},
				{5.0F, 0.38F, 0.64F, 0.84F, 0.18F, 0.12F},
				{5.1F, 0.18F, 0.67F, 0.76F, 0.06F, 0.05F}}, 0.85F, 2.35F, 1.95F);

		final float bottom;
		final float top;
		final float roofR;
		final float floorR;
		final float[][] keys;
		/** Pare-brise : début et fin (distance depuis la base du nez) et altitude minimale. */
		final float shieldStart;
		final float shieldEnd;
		final float shieldY;

		Nose(float bottom, float top, float roofR, float floorR, float[][] keys, float shieldStart, float shieldEnd, float shieldY) {
			this.bottom = bottom;
			this.top = top;
			this.roofR = roofR;
			this.floorR = floorR;
			this.keys = keys;
			this.shieldStart = shieldStart;
			this.shieldEnd = shieldEnd;
			this.shieldY = shieldY;
		}

		float length() {
			return keys[keys.length - 1][0];
		}
	}

	private static void streamliner(Builder b, CarType type, Nose nose, Livery lv) {
		CarLayout l = type.layout();
		Mesh m = b.body;
		float h = f(l.halfLength);
		float noseStart = h - nose.length();
		float back = -h;
		Section[] keys = new Section[nose.keys.length];
		for (int i = 0; i < keys.length; i++) {
			float[] k = nose.keys[i];
			keys[i] = s(noseStart + k[0], k[1], k[2], k[3], k[4], k[5]);
		}
		Section[] noseSections = smooth(keys, 6);
		float shield0 = noseStart + nose.shieldStart;
		float shield1 = noseStart + nose.shieldEnd;
		boolean shinkansen = nose == Nose.SHINKANSEN;
		Mesh.ColorFunction noseColor = (x, y, z, nx, ny, nz) -> {
			if (z > shield0 && z < shield1 && y > nose.shieldY && Math.abs(x) < 0.84F && ny > 0.12F) {
				return Mesh.HOLE;
			}
			return noseLivery(lv, nose, x, y, z, nx, ny, noseStart, h);
		};
		m.loft(noseSections, 6, false, true, noseColor);
		b.glass.loft(noseSections, 6, false, false, (x, y, z, nx, ny, nz) ->
				noseColor.color(x, y, z, nx, ny, nz) == Mesh.HOLE ? 0x80283C50 : Mesh.HOLE);
		// Encadrement du pare-brise
		// Caisse : cabine creuse, compartiment machine opaque
		TreeSet<Float> zs = zBreaks(l, back, noseStart);
		float[] breaks = yBreaks(l, 0.9F, 1.25F, 1.33F, 2.2F, 2.3F, 0.98F, 1.2F);
		Section[] body = straight(zs, HW, nose.bottom, nose.top, nose.roofR, nose.floorR);
		Mesh.ColorFunction bodyColor = (x, y, z, nx, ny, nz) -> {
			if (z < back + 0.02F && Math.abs(nz) > 0.9F) {
				return 0xFF3A3E44;
			}
			if (Math.abs(nx) > 0.6F && z > back + 1.0F && z < l.interiorZ0 - 0.6F && y > 1.55F && y < 2.05F) {
				if (shinkansen) {
					return lv.upper;
				}
				return periodic(z, back, 0.2F, 0.08F) ? Mesh.darker(lv.band, 0.6F) : lv.band;
			}
			return bodyLivery(lv, y, nose.top);
		};
		hull(b, l, body, 6, breaks, bodyColor, f(l.interiorZ0), noseStart, nose.top - 0.09F, nose.roofR, lv);
		if (shinkansen) {
			// Motrice voyageurs : rangée de fenêtres avec encadrement
			for (float z = back + 1.0F; z + 0.5F < l.interiorZ0 - 0.6F; z += 0.72F) {
				m.mirroredBox(HW - 0.01F, 1.58F, z, HW + 0.012F, 2.0F, z + 0.5F, Mesh.darker(lv.upper, 0.82F));
				m.mirroredBox(HW, 1.61F, z + 0.03F, HW + 0.016F, 1.97F, z + 0.47F, GLASS_DARK);
			}
		}
		m.endFrame(s(back, HW, nose.bottom, nose.top, nose.roofR, nose.floorR), 6, 0.001F, 1.0F, 1.0F, -1, 0xFF3A3E44);
		m.box(-0.9F, FLOOR, f(l.interiorZ0) - 0.06F, 0.9F, nose.top - 0.1F, f(l.interiorZ0), 0xFFB8B4A8);
		m.box(-0.8F, FLOOR - 0.06F, f(l.interiorZ1), 0.8F, FLOOR, shield1, 0xFF4A4E55);
		// Toiture et pantographe
		m.box(-0.5F, nose.top, back + 0.7F, 0.5F, nose.top + 0.13F, noseStart - 2.6F, 0xFF9DA3A9);
		pantograph(m, type.pantographZ(), nose.top + 0.1F);
		// Phares
		float lx = shinkansen ? 0.55F : 0.42F;
		float ly = shinkansen ? 0.95F : 0.92F;
		float lz = noseStart + nose.length() * (shinkansen ? 0.84F : 0.9F);
		b.lamps.box(-lx - 0.1F, ly - 0.05F, lz, -lx + 0.1F, ly + 0.05F, lz + 0.12F, HEADLIGHT);
		b.lamps.box(lx - 0.1F, ly - 0.05F, lz, lx + 0.1F, ly + 0.05F, lz + 0.12F, HEADLIGHT);
		// Arrière : attelage et feux
		m.box(-0.35F, 0.4F, back - 0.25F, 0.35F, 2.4F, back + 0.02F, 0xFF2B2F36);
		m.box(-0.08F, 0.55F, back - 0.4F, 0.08F, 0.7F, back, 0xFF4A4D52);
		lamp(b, -0.6F, 0.9F, back, 0.06F, TAILLIGHT, -1);
		lamp(b, 0.6F, 0.9F, back, 0.06F, TAILLIGHT, -1);
		// Câbles de toiture, avertisseur et grilles latérales
		float pz = type.pantographZ();
		m.beam(v(0.25F, nose.top + 0.16F, pz + 0.45F), v(0.25F, nose.top + 0.16F, back + 0.8F), 0.04F, 0xFFB5463A);
		m.beam(v(-0.25F, nose.top + 0.16F, pz + 0.45F), v(-0.25F, nose.top + 0.16F, back + 0.8F), 0.04F, 0xFFB5463A);
		for (float z = back + 1.0F; z < pz - 0.8F; z += 0.6F) {
			m.cylinder(1, 0.25F, z, nose.top + 0.12F, nose.top + 0.17F, 0.05F, 6, 0xFFE8E4D8, 0xFFE8E4D8);
			m.cylinder(1, -0.25F, z, nose.top + 0.12F, nose.top + 0.17F, 0.05F, 6, 0xFFE8E4D8, 0xFFE8E4D8);
		}
		m.box(-0.18F, nose.top - 0.02F, noseStart - 0.5F, 0.18F, nose.top + 0.08F, noseStart - 0.1F, 0xFF2B2E33);
		m.mirroredBox(HW + 0.003F, 0.95F, back + 1.6F, HW + 0.01F, 1.1F, back + 2.6F, 0xFF22252A);
		m.mirroredBox(HW + 0.004F, 0.98F, back + 1.7F, HW + 0.011F, 1.07F, back + 2.5F, 0xFFF2F2EE);
		// Jupes et châssis
		m.box(-0.7F, 0.42F, back + 0.4F, 0.7F, 0.6F, noseStart + 0.6F, UNDERFRAME);
		bogie(b, back + 1.7F, 1.6F, 0.36F);
		bogie(b, noseStart - 0.6F, 1.6F, 0.36F);
		interior(b, l, lv);
	}

	private static int bodyLivery(Livery lv, float y, float top) {
		return switch (lv) {
			case SHINKANSEN, SHINKANSEN_GREEN -> y < 0.78F ? lv.skirt : (y > 0.98F && y < 1.2F) || (y > 1.25F && y < 1.31F) ? lv.stripe : (y > top - 0.4F ? lv.roof : lv.upper);
			case ICE -> y < 0.8F ? lv.skirt : (y > 1.0F && y < 1.15F) ? lv.stripe : (y > 1.5F && y < 2.15F) ? lv.band : (y > top - 0.4F ? lv.roof : lv.upper);
			case EUROSTAR -> y < 0.8F ? lv.skirt : (y > 1.0F && y < 1.12F) ? lv.stripe : (y > 1.5F && y < 2.15F) ? lv.band : (y > top - 0.4F ? lv.roof : lv.upper);
			case TGV_ORANGE -> y < 0.9F ? lv.skirt : (y > 1.2F && y < 1.35F && ((int) (y * 40) % 2 == 0)) ? lv.stripe : (y > 1.5F && y < 2.15F) ? lv.band : (y > top - 0.42F ? lv.roof : lv.lower);
			default -> lv.outer(y, top);
		};
	}

	private static int noseLivery(Livery lv, Nose nose, float x, float y, float z, float nx, float ny, float noseStart, float h) {
		// Les bandes de la caisse s'arrêtent net à la base du nez (pas d'effet « dents de scie » sur les pentes)
		float t = (z - noseStart) / nose.length();
		boolean base = t < 0.12F && Math.abs(nx) > 0.5F;
		return switch (lv) {
			case SHINKANSEN -> y < 0.72F ? lv.skirt : base ? bodyLivery(lv, y, nose.top) : lv.upper;
			case EUROSTAR -> t > 0.82F ? lv.stripe : y < 0.8F ? lv.skirt : base ? bodyLivery(lv, y, nose.top) : lv.upper;
			case ICE -> y < 0.78F ? lv.skirt : base ? bodyLivery(lv, y, nose.top) : lv.upper;
			case TGV_ORANGE -> y < 0.9F ? lv.skirt : base ? bodyLivery(lv, y, nose.top) : lv.lower;
			default -> y < 0.9F ? lv.skirt : base ? bodyLivery(lv, y, nose.top) : (t > 0.8F && y < 1.3F ? lv.stripe : lv.lower);
		};
	}

	// ==================================================================
	// Wagons de marchandises
	// ==================================================================

	private static void freightBase(Builder b, float h, float deck, int frameColor) {
		Mesh m = b.body;
		m.box(-0.98F, deck - 0.18F, -h + 0.1F, 0.98F, deck, h - 0.1F, frameColor);
		m.box(-0.6F, 0.42F, -h + 0.5F, 0.6F, deck - 0.18F, h - 0.5F, UNDERFRAME);
		for (int dir = -1; dir <= 1; dir += 2) {
			buffers(m, dir * h, dir, 0.66F, BLACK);
		}
		float bz = h - 1.35F;
		bogie(b, -bz, 1.3F, 0.32F);
		bogie(b, bz, 1.3F, 0.32F);
		// Marchepieds et mains courantes
		for (int dir = -1; dir <= 1; dir += 2) {
			b.body.mirroredBox(0.85F, 0.35F, dir * (h - 0.35F) - 0.15F, 1.02F, 0.38F, dir * (h - 0.35F) + 0.15F, 0xFF4A4D52);
		}
	}

	private static void boxcar(Builder b, CarType type) {
		Mesh m = b.body;
		float h = f(type.length / 2) - 0.1F;
		int wood = 0xFF7B4A2A;
		int woodDark = 0xFF5E3820;
		int iron = 0xFF2E3033;
		freightBase(b, h + 0.1F, 0.8F, 0xFF2A2B2D);
		m.loft(new Section[]{s(-h, HW, 0.8F, 2.55F, 0.3F, 0.02F), s(h, HW, 0.8F, 2.55F, 0.3F, 0.02F)}, 3, true, true,
				(x, y, z, nx, ny, nz) -> y > 2.4F ? 0xFF6A6E73 : Math.abs(nz) > 0.9F ? woodDark : periodic(y, 0.8F, 0.25F, 0.08F) ? woodDark : wood);
		for (float z = -h; z <= h; z += 1.1F) {
			m.mirroredBox(HW, 0.8F, z, HW + 0.02F, 2.45F, z + 0.08F, iron);
		}
		m.mirroredBox(HW + 0.005F, 0.85F, -0.9F, HW + 0.03F, 2.4F, 0.9F, 0xFF8A5634);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * (HW + 0.035F);
			m.beam(v(x, 0.9F, -0.85F), v(x, 2.35F, 0.85F), 0.06F, iron);
			m.beam(v(x, 0.9F, 0.85F), v(x, 2.35F, -0.85F), 0.06F, iron);
		}
		m.mirroredBox(HW, 0.82F, -1.2F, HW + 0.045F, 0.88F, 1.2F, iron);
		m.mirroredBox(HW, 2.36F, -1.2F, HW + 0.045F, 2.42F, 1.2F, iron);
		m.box(-0.2F, 2.55F, -h, 0.2F, 2.6F, h, 0xFF55595E);
	}

	private static void tankWagon(Builder b, CarType type) {
		Mesh m = b.body;
		float h = f(type.length / 2);
		freightBase(b, h, 0.85F, 0xFF2A2B2D);
		int tank = 0xFFE8E8E4;
		m.cylinder(2, 0, 1.68F, -h + 0.5F, h - 0.5F, 0.84F, 24, tank, 0xFFD8D8D2);
		m.cylinder(2, 0, 1.68F, h - 0.5F, h - 0.35F, 0.7F, 20, 0xFFD8D8D2, 0xFFCFCFC8);
		m.cylinder(2, 0, 1.68F, -h + 0.35F, -h + 0.5F, 0.7F, 20, 0xFFD8D8D2, 0xFFCFCFC8);
		m.cylinder(2, 0, 1.68F, -0.4F, 0.4F, 0.855F, 24, 0xFFE07B1F, 0xFFE07B1F);
		m.cylinder(1, 0, 0, 2.45F, 2.68F, 0.3F, 14, 0xFFB8BEC4, 0xFFC9CFD5);
		m.box(-1.0F, 2.5F, -0.7F, 1.0F, 2.54F, 0.7F, 0xFF4A4D52);
		for (int side = -1; side <= 1; side += 2) {
			m.beam(v(side * 0.98F, 2.54F, -0.7F), v(side * 0.98F, 2.95F, -0.7F), 0.04F, 0xFFF2C14E);
			m.beam(v(side * 0.98F, 2.54F, 0.7F), v(side * 0.98F, 2.95F, 0.7F), 0.04F, 0xFFF2C14E);
			m.beam(v(side * 0.98F, 2.95F, -0.7F), v(side * 0.98F, 2.95F, 0.7F), 0.04F, 0xFFF2C14E);
			m.beam(v(side * 1.0F, 0.9F, 0.2F), v(side * 0.9F, 2.5F, 0.2F), 0.04F, 0xFFB8BEC4);
			m.beam(v(side * 1.0F, 0.9F, -0.2F), v(side * 0.9F, 2.5F, -0.2F), 0.04F, 0xFFB8BEC4);
		}
		for (float z = -h + 1.0F; z < h - 0.8F; z += 1.4F) {
			m.cylinder(2, 0, 1.68F, z, z + 0.05F, 0.86F, 24, 0xFF2A2B2D, 0xFF2A2B2D);
		}
	}

	private static void hopperWagon(Builder b, CarType type) {
		Mesh m = b.body;
		float h = f(type.length / 2);
		freightBase(b, h, 0.85F, 0xFF2A2B2D);
		int body = 0xFF5D6B4A;
		m.mirroredBox(0.92F, 0.85F, -h + 0.25F, 0.98F, 2.4F, h - 0.25F, body);
		m.box(-0.98F, 0.85F, h - 0.31F, 0.98F, 2.4F, h - 0.25F, body);
		m.box(-0.98F, 0.85F, -h + 0.25F, 0.98F, 2.4F, -h + 0.31F, body);
		for (float z = -h + 0.6F; z < h - 0.4F; z += 0.9F) {
			m.mirroredBox(0.98F, 0.85F, z, 1.02F, 2.42F, z + 0.08F, Mesh.darker(body, 0.75F));
		}
		m.mirroredBox(0.98F, 2.3F, -h + 0.25F, 1.03F, 2.42F, h - 0.25F, Mesh.darker(body, 0.75F));
		// Trémies de déchargement
		for (float z : new float[]{-1.6F, 0.0F, 1.6F}) {
			m.beam(v(0, 0.5F, z), v(0, 1.2F, z - 0.6F), 0.5F, Mesh.darker(body, 0.85F));
			m.beam(v(0, 0.5F, z), v(0, 1.2F, z + 0.6F), 0.5F, Mesh.darker(body, 0.85F));
		}
		// Chargement de charbon
		int coal = 0xFF161616;
		m.box(-0.9F, 2.0F, -h + 0.3F, 0.9F, 2.3F, h - 0.3F, coal);
		for (float z = -h + 0.6F; z < h - 0.5F; z += 0.7F) {
			m.box(-0.6F, 2.3F, z, 0.5F, 2.45F, z + 0.5F, 0xFF222222);
			m.box(-0.3F, 2.45F, z + 0.1F, 0.25F, 2.55F, z + 0.35F, coal);
		}
	}

	private static void containerWagon(Builder b, CarType type) {
		Mesh m = b.body;
		float h = f(type.length / 2);
		freightBase(b, h, 0.85F, 0xFF3A3D42);
		int[] colors = {0xFF1E5AA8, 0xFFC0392B};
		float[][] spans = {{-h + 0.2F, -0.08F}, {0.08F, h - 0.2F}};
		for (int i = 0; i < 2; i++) {
			float z0 = spans[i][0];
			float z1 = spans[i][1];
			int c = colors[i];
			m.box(-0.95F, 0.86F, z0, 0.95F, 2.55F, z1, c);
			for (float z = z0 + 0.1F; z < z1 - 0.05F; z += 0.14F) {
				m.mirroredBox(0.95F, 0.95F, z, 0.97F, 2.45F, z + 0.06F, Mesh.darker(c, 0.8F));
			}
			m.box(-0.97F, 2.45F, z0, 0.97F, 2.57F, z1, Mesh.darker(c, 0.85F));
			m.box(-0.97F, 0.86F, z0, 0.97F, 0.96F, z1, Mesh.darker(c, 0.7F));
			for (float zc : new float[]{z0, z1 - 0.06F}) {
				m.mirroredBox(0.9F, 0.86F, zc, 0.98F, 2.57F, zc + 0.06F, Mesh.darker(c, 0.6F));
			}
			// Portes et crémones
			float endZ = i == 0 ? z0 - 0.005F : z1;
			m.box(-0.9F, 0.95F, endZ, 0.9F, 2.45F, endZ + 0.005F, Mesh.darker(c, 0.9F));
			for (float x : new float[]{-0.5F, -0.2F, 0.2F, 0.5F}) {
				m.box(x - 0.02F, 0.95F, endZ - 0.02F, x + 0.02F, 2.45F, endZ + 0.025F, 0xFFB8BEC4);
			}
		}
	}

	private static void logWagon(Builder b, CarType type) {
		Mesh m = b.body;
		float h = f(type.length / 2);
		freightBase(b, h, 0.85F, 0xFF3A2E22);
		for (float z = -h + 0.4F; z < h - 0.2F; z += 1.15F) {
			m.mirroredBox(0.88F, 0.85F, z, 0.96F, 2.35F, z + 0.1F, 0xFF2A2B2D);
		}
		int bark = 0xFF5A3A22;
		int cut = 0xFFC8A06A;
		float[][] logs = {{-0.6F, 1.15F}, {0.0F, 1.15F}, {0.6F, 1.15F}, {-0.3F, 1.68F}, {0.3F, 1.68F}, {0.0F, 2.18F}};
		for (float[] lg : logs) {
			m.cylinder(2, lg[0], lg[1], -h + 0.25F, h - 0.25F, 0.28F, 10, bark, cut);
			m.cylinder(2, lg[0], lg[1], -h + 0.24F, -h + 0.25F, 0.12F, 8, 0xFFA07A48, 0xFFA07A48);
		}
		for (float z : new float[]{-1.5F, 1.5F}) {
			m.beam(v(-0.95F, 0.9F, z), v(-0.6F, 2.45F, z), 0.04F, 0xFFC8CDD2);
			m.beam(v(0.95F, 0.9F, z), v(0.6F, 2.45F, z), 0.04F, 0xFFC8CDD2);
			m.beam(v(-0.6F, 2.45F, z), v(0.6F, 2.45F, z), 0.04F, 0xFFC8CDD2);
		}
	}

	private static void livestockWagon(Builder b, CarType type) {
		CarLayout l = type.layout();
		Mesh m = b.body;
		float h = f(l.halfLength);
		int wood = 0xFF8A3A2A;
		int iron = 0xFF2E3033;
		freightBase(b, h, FLOOR, 0xFF2A2B2D);
		m.box(-0.98F, FLOOR - 0.04F, -h + 0.2F, 0.98F, FLOOR, h - 0.2F, 0xFF6B4A2E);
		// Parois à claire-voie (lattes horizontales) avec portes centrales
		for (int side = -1; side <= 1; side += 2) {
			float x0 = side > 0 ? HW - 0.06F : -HW;
			float x1 = side > 0 ? HW : -HW + 0.06F;
			for (float y = FLOOR + 0.05F; y < 2.3F; y += 0.32F) {
				m.box(x0, y, -h + 0.25F, x1, y + 0.17F, -0.72F, wood);
				m.box(x0, y, 0.72F, x1, y + 0.17F, h - 0.25F, wood);
			}
			for (float z = -h + 0.25F; z < h - 0.2F; z += 0.85F) {
				if (Math.abs(z) > 0.6F) {
					m.box(x0 - side * 0.02F, FLOOR, z, x1 + side * 0.02F, 2.4F, z + 0.07F, iron);
				}
			}
		}
		for (int dir = -1; dir <= 1; dir += 2) {
			float z0 = dir > 0 ? h - 0.31F : -h + 0.25F;
			for (float y = FLOOR + 0.05F; y < 2.3F; y += 0.32F) {
				m.box(-HW, y, z0, HW, y + 0.17F, z0 + 0.06F, wood);
			}
		}
		m.loft(new Section[]{s(-h + 0.2F, HW + 0.02F, 2.3F, 2.65F, 0.3F, 0.01F), s(h - 0.2F, HW + 0.02F, 2.3F, 2.65F, 0.3F, 0.01F)}, 3, true, true,
				(x, y, z, nx, ny, nz) -> 0xFF6A6E73);
		for (int i = 0; i < l.doors.size(); i++) {
			Door d = l.doors.get(i);
			Mesh dm = new Mesh();
			int side = d.side();
			float x0 = side > 0 ? HW : -HW - 0.04F;
			float x1 = x0 + 0.04F;
			for (float y = FLOOR + 0.05F; y < 2.3F; y += 0.32F) {
				dm.box(x0, y, -0.7F, x1, y + 0.17F, 0.7F, Mesh.darker(wood, 1.1F) | 0xFF000000);
			}
			dm.beam(v(x0 + side * 0.03F, FLOOR + 0.1F, -0.65F), v(x0 + side * 0.03F, 2.2F, 0.65F), 0.06F, iron);
			b.doors.add(new DoorPanel(i, dm, 1.4F));
		}
		interior(b, l, Livery.BAGGAGE);
	}

	private static void caboose(Builder b, CarType type) {
		CarLayout l = type.layout();
		Mesh m = b.body;
		float h = f(l.halfLength);
		int red = 0xFFA8261E;
		Livery lv = Livery.BAGGAGE;
		freightBase(b, h, FLOOR, 0xFF2A2B2D);
		float z0 = f(l.interiorZ0);
		float z1 = f(l.interiorZ1);
		TreeSet<Float> zs = zBreaks(l, z0, z1);
		Section[] body = straight(zs, HW, FLOOR - 0.1F, 2.65F, 0.3F, 0.02F);
		float[] breaks = yBreaks(l);
		hull(b, l, body, 3, breaks, (x, y, z, nx, ny, nz) -> y > 2.55F ? 0xFF3A3D42 : (periodic(y, 0.5F, 0.2F, 0.02F) ? Mesh.darker(red, 0.8F) : red),
				z0, z1, 2.55F, 0.25F, lv);
		for (int dir = -1; dir <= 1; dir += 2) {
			m.endFrame(s(dir > 0 ? z1 : z0, HW, FLOOR - 0.1F, 2.65F, 0.3F, 0.02F), 3, 0.32F, FLOOR, 2.25F, dir, red);
			m.endFrame(s(dir > 0 ? z1 - 0.07F : z0 + 0.07F, HW - 0.07F, FLOOR, 2.55F, 0.25F, 0.01F), 3, 0.32F, FLOOR, 2.25F, -dir, lv.wall);
			// Plateformes avec garde-corps
			float pz0 = Math.min(dir * h, dir > 0 ? z1 : z0);
			float pz1 = Math.max(dir * h, dir > 0 ? z1 : z0);
			m.box(-HW, FLOOR - 0.06F, pz0, HW, FLOOR, pz1, 0xFF3A3D42);
			float zr = dir * (h - 0.05F);
			for (int side = -1; side <= 1; side += 2) {
				m.beam(v(side * 0.95F, FLOOR, zr), v(side * 0.95F, 1.6F, zr), 0.04F, 0xFFF2C14E);
				m.beam(v(side * 0.95F, 1.6F, zr), v(side * 0.95F, 1.6F, dir > 0 ? z1 : z0), 0.04F, 0xFFF2C14E);
				m.beam(v(side * 0.95F, 1.6F, zr), v(side * 0.35F, 1.6F, zr), 0.04F, 0xFFF2C14E);
			}
			m.box(-1.02F, 2.65F, Math.min(dir * (h - 0.05F), dir > 0 ? z1 : z0), 1.02F, 2.7F, Math.max(dir * (h - 0.05F), dir > 0 ? z1 : z0), 0xFF3A3D42);
			lamp(b, 0.7F, 2.3F, dir > 0 ? z1 : z0, 0.06F, TAILLIGHT, dir);
		}
		// Vigie (lanterneau) sur le toit
		m.box(-0.6F, 2.6F, -0.7F, 0.6F, 3.25F, 0.7F, red);
		b.glass.box(-0.61F, 2.85F, -0.5F, 0.61F, 3.1F, 0.5F, GLASS);
		m.box(-0.7F, 3.25F, -0.8F, 0.7F, 3.32F, 0.8F, 0xFF3A3D42);
		m.cylinder(1, -0.6F, -1.6F, 2.6F, 3.2F, 0.06F, 8, 0xFF2A2A2C, 0xFF0A0A0A);
		interior(b, l, lv);
	}

	// ==================================================================
	// Roues (maillage centré sur l'essieu, axe X)
	// ==================================================================

	private static Mesh buildWheel(WheelStyle style, float r) {
		Mesh m = new Mesh();
		float w = 0.1F;
		switch (style) {
			case MODERN -> {
				m.cylinder(0, 0, 0, -w / 2, w / 2, r, 16, 0xFF3B3E42, 0xFF5A5E63);
				m.cylinder(0, 0, 0, -w / 2 - 0.015F, -w / 2 + 0.01F, r + 0.035F, 16, 0xFF2E3034, 0xFF44474C);
				m.cylinder(0, 0, 0, -w / 2 - 0.02F, w / 2 + 0.02F, r * 0.35F, 10, 0xFF6E7378, 0xFF6E7378);
				m.box(-w / 2 - 0.021F, -0.03F, r * 0.4F, w / 2 + 0.021F, 0.03F, r * 0.85F, 0xFF494C51);
			}
			case STEAM_DRIVER, STEAM_SMALL -> {
				int red = 0xFFB3261E;
				m.cylinder(0, 0, 0, -w / 2, w / 2, r, 20, 0xFF1A1A1B, 0xFF4A1210);
				m.cylinder(0, 0, 0, -w / 2 - 0.015F, -w / 2 + 0.01F, r + 0.035F, 20, 0xFF222224, 0xFF2A2A2C);
				m.cylinder(0, 0, 0, -w / 2 - 0.01F, w / 2 + 0.01F, r * 0.86F, 20, red, 0xFF5A1612);
				int spokes = style == WheelStyle.STEAM_DRIVER ? 14 : 9;
				for (int i = 0; i < spokes; i++) {
					double a = Math.PI * 2 * i / spokes;
					Vector3f from = v(0, (float) Math.cos(a) * r * 0.15F, (float) Math.sin(a) * r * 0.15F);
					Vector3f to = v(0, (float) Math.cos(a) * r * 0.84F, (float) Math.sin(a) * r * 0.84F);
					m.beam(new Vector3f(from).add(w / 2 + 0.012F, 0, 0), new Vector3f(to).add(w / 2 + 0.012F, 0, 0), 0.045F, red);
					m.beam(new Vector3f(from).sub(w / 2 + 0.012F, 0, 0), new Vector3f(to).sub(w / 2 + 0.012F, 0, 0), 0.045F, red);
				}
				m.cylinder(0, 0, 0, -w / 2 - 0.04F, w / 2 + 0.04F, r * 0.18F, 10, BRASS, BRASS);
				if (style == WheelStyle.STEAM_DRIVER) {
					m.box(-w / 2 - 0.025F, r * 0.35F, -r * 0.3F, w / 2 + 0.025F, r * 0.8F, r * 0.3F, 0xFF8E1D17);
				}
			}
		}
		return m;
	}
}
