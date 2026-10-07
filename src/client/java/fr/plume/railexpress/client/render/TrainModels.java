package fr.plume.railexpress.client.render;

import fr.plume.railexpress.client.render.Mesh.Section;
import fr.plume.railexpress.entity.CarType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Modèles 3D procéduraux de tous les véhicules.
 * Repère : origine au centre du véhicule au niveau du rail, +Z vers l'avant, +X à droite, +Y vers le haut (en blocs).
 */
public final class TrainModels {
	/** Un essieu : position, rayon et style de roue. */
	public record Axle(float z, float radius, float gauge, WheelStyle style) {
	}

	public enum WheelStyle {
		MODERN, STEAM_DRIVER, STEAM_SMALL
	}

	public record Model(Mesh body, List<Axle> axles, boolean steamRods) {
	}

	private static final Map<CarType, Model> CACHE = new EnumMap<>(CarType.class);
	private static final Map<WheelStyle, Map<Float, Mesh>> WHEELS = new EnumMap<>(WheelStyle.class);

	// Palette
	private static final int GLASS = 0xFF101A26;
	private static final int GLASS_LIGHT = 0xFF2A3F55;
	private static final int BOGIE = 0xFF2B2E33;
	private static final int UNDERFRAME = 0xFF1C1E21;
	private static final int STEEL = 0xFF8A9096;
	private static final int BLACK = 0xFF18191B;
	private static final int HEADLIGHT = 0xFFFFF4C2;
	private static final int TAILLIGHT = 0xFFD8211B;

	private TrainModels() {
	}

	public static Model get(CarType type) {
		return CACHE.computeIfAbsent(type, TrainModels::build);
	}

	public static Mesh wheel(WheelStyle style, float radius) {
		return WHEELS.computeIfAbsent(style, s -> new java.util.HashMap<>()).computeIfAbsent(radius, r -> buildWheel(style, r));
	}

	private static Model build(CarType type) {
		return switch (type) {
			case STEAM_LOCOMOTIVE -> steamLocomotive();
			case TENDER -> tender();
			case PASSENGER_COACH -> passengerCoach();
			case FREIGHT_WAGON -> freightWagon();
			case TGV_POWER_CAR -> tgvPowerCar();
			case TGV_CAR -> tgvCar();
			case SHINKANSEN_HEAD -> shinkansenHead();
			case SHINKANSEN_CAR -> shinkansenCar();
		};
	}

	// ------------------------------------------------------------------
	// Outils communs
	// ------------------------------------------------------------------

	private static boolean periodic(float value, float start, float pitch, float width) {
		float m = (value - start) % pitch;
		if (m < 0) {
			m += pitch;
		}
		return m < width;
	}

	private static Section s(float z, float hw, float bottom, float top, float roof, float floor) {
		return new Section(z, hw, bottom, top, roof, floor);
	}

	/** Caisse droite : deux sections suffisent, les fenêtres sont des éléments séparés. */
	private static Section[] straight(float z0, float z1, float hw, float bottom, float top, float roof, float floor) {
		return new Section[]{s(z0, hw, bottom, top, roof, floor), s(z1, hw, bottom, top, roof, floor)};
	}

	/** Rangée de fenêtres (ou de portes) plaquées sur les deux flancs. */
	private static void windows(Mesh m, float hw, float z0, float z1, float pitch, float width, float y0, float y1, int color) {
		for (float z = z0; z + width <= z1 + 1.0E-3F; z += pitch) {
			m.mirroredBox(hw - 0.01F, y0, z, hw + 0.012F, y1, z + width, color);
		}
	}

	private static Section[] concat(Section[] a, Section[] b) {
		Section[] r = new Section[a.length + b.length - 1];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 1, r, a.length, b.length - 1);
		return r;
	}

	/** Interpole finement entre des sections clés (nez aérodynamique). */
	private static Section[] smooth(Section[] keys, int sub) {
		List<Section> out = new ArrayList<>();
		for (int i = 0; i + 1 < keys.length; i++) {
			Section a = keys[i];
			Section b = keys[i + 1];
			for (int k = 0; k < sub; k++) {
				float t = (float) k / sub;
				// Interpolation de Hermite (tangentes de Catmull-Rom) pour un profil lisse
				Section p0 = keys[Math.max(0, i - 1)];
				Section p3 = keys[Math.min(keys.length - 1, i + 2)];
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

	/** Bogie moderne : châssis, boîtes d'essieux, suspensions. */
	private static void bogie(Mesh m, float z, float axleSpacing, List<Axle> axles, float wheelRadius) {
		float half = axleSpacing / 2;
		m.mirroredBox(0.55F, 0.22F, z - half - 0.35F, 0.68F, 0.52F, z + half + 0.35F, BOGIE);
		m.box(-0.68F, 0.38F, z - 0.18F, 0.68F, 0.5F, z + 0.18F, BOGIE);
		m.mirroredBox(0.6F, 0.26F, z - half - 0.12F, 0.74F, 0.42F, z - half + 0.12F, 0xFF3C4046);
		m.mirroredBox(0.6F, 0.26F, z + half - 0.12F, 0.74F, 0.42F, z + half + 0.12F, 0xFF3C4046);
		m.cylinder(1, 0.62F, z, 0.5F, 0.62F, 0.07F, 8, 0xFFB0352A, 0xFFB0352A);
		m.cylinder(1, -0.62F, z, 0.5F, 0.62F, 0.07F, 8, 0xFFB0352A, 0xFFB0352A);
		axles.add(new Axle(z - half, wheelRadius, 0.6F, WheelStyle.MODERN));
		axles.add(new Axle(z + half, wheelRadius, 0.6F, WheelStyle.MODERN));
	}

	/** Tampons et attelage aux deux extrémités. */
	private static void buffers(Mesh m, float halfLength, float y, int beamColor) {
		for (int dir = -1; dir <= 1; dir += 2) {
			float zBeam = dir * halfLength;
			m.box(-0.95F, y - 0.18F, Math.min(zBeam, zBeam - dir * 0.14F), 0.95F, y + 0.18F, Math.max(zBeam, zBeam - dir * 0.14F), beamColor);
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 0.68F;
				float z0 = zBeam;
				float z1 = zBeam + dir * 0.26F;
				m.cylinder(2, x, y, Math.min(z0, z1), Math.max(z0, z1), 0.08F, 8, BLACK, BLACK);
				float h0 = zBeam + dir * 0.24F;
				float h1 = zBeam + dir * 0.3F;
				m.cylinder(2, x, y, Math.min(h0, h1), Math.max(h0, h1), 0.15F, 10, 0xFFB8BEC4, 0xFFC9CFD5);
			}
			m.box(-0.08F, y - 0.08F, Math.min(zBeam, zBeam + dir * 0.32F), 0.08F, y + 0.06F, Math.max(zBeam, zBeam + dir * 0.32F), 0xFF4A4D52);
		}
	}

	// ------------------------------------------------------------------
	// Locomotive à vapeur
	// ------------------------------------------------------------------

	private static Model steamLocomotive() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		int green = 0xFF1F5A35;
		int greenDark = 0xFF173F27;
		int brass = 0xFFD2A93F;
		int red = 0xFFB3261E;

		// Châssis et tabliers
		m.box(-0.5F, 0.42F, -4.0F, 0.5F, 0.78F, 4.0F, BLACK);
		m.mirroredBox(0.5F, 0.92F, -1.55F, 1.0F, 0.98F, 3.95F, 0xFF2A2B2D);
		m.mirroredBox(0.98F, 0.86F, -1.55F, 1.02F, 0.98F, 3.95F, red);
		// Traverses de tamponnement rouges
		m.box(-1.0F, 0.52F, 3.95F, 1.0F, 0.95F, 4.12F, red);
		m.box(-1.0F, 0.52F, -4.12F, 1.0F, 0.95F, -3.95F, red);
		for (int side = -1; side <= 1; side += 2) {
			for (int dir = -1; dir <= 1; dir += 2) {
				float zb = dir * 4.12F;
				m.cylinder(2, side * 0.68F, 0.74F, Math.min(zb, zb + dir * 0.24F), Math.max(zb, zb + dir * 0.24F), 0.07F, 8, BLACK, BLACK);
				m.cylinder(2, side * 0.68F, 0.74F, Math.min(zb + dir * 0.22F, zb + dir * 0.28F), Math.max(zb + dir * 0.22F, zb + dir * 0.28F), 0.15F, 10, 0xFFC0C4C8, 0xFFD0D4D8);
			}
		}
		// Chaudière, cerclages en laiton, boîte à fumée
		m.cylinder(2, 0, 1.62F, -1.6F, 3.35F, 0.62F, 20, green, green);
		for (float z : new float[]{-0.9F, 0.4F, 1.8F, 3.2F}) {
			m.cylinder(2, 0, 1.62F, z, z + 0.07F, 0.635F, 20, brass, brass);
		}
		m.cylinder(2, 0, 1.62F, 3.35F, 4.0F, 0.66F, 20, 0xFF1D1D1F, 0xFF262628);
		m.cylinder(2, 0, 1.62F, 4.0F, 4.05F, 0.46F, 18, 0xFF2E2E31, 0xFF2E2E31);
		m.cylinder(2, 0, 1.62F, 4.05F, 4.09F, 0.08F, 8, brass, brass);
		m.box(-0.2F, 1.0F, 4.0F, 0.2F, 1.12F, 4.06F, brass); // plaque
		// Cheminée
		m.cylinder(1, 0, 3.65F, 2.1F, 2.85F, 0.19F, 12, 0xFF151516, 0xFF080808);
		m.cylinder(1, 0, 3.65F, 2.78F, 2.96F, 0.26F, 12, 0xFF1F1F21, 0xFF0A0A0A);
		// Dômes
		m.cylinder(1, 0, 1.0F, 2.05F, 2.42F, 0.28F, 14, brass, 0xFFE0BC55);
		m.cylinder(1, 0, -0.35F, 2.05F, 2.3F, 0.22F, 12, green, green);
		m.cylinder(1, 0, 0.2F, 2.15F, 2.42F, 0.05F, 6, brass, brass);
		// Phare
		m.box(-0.14F, 2.12F, 3.86F, 0.14F, 2.38F, 4.1F, BLACK);
		m.box(-0.1F, 2.16F, 4.1F, 0.1F, 2.34F, 4.12F, HEADLIGHT);
		m.box(-0.62F, 0.96F, 4.04F, -0.42F, 1.16F, 4.14F, HEADLIGHT);
		m.box(0.42F, 0.96F, 4.04F, 0.62F, 1.16F, 4.14F, HEADLIGHT);

		// Cylindres et glissières
		m.cylinder(2, 0.82F, 0.86F, 2.45F, 3.45F, 0.24F, 12, 0xFF2A2A2D, 0xFF3A3A3D);
		m.cylinder(2, -0.82F, 0.86F, 2.45F, 3.45F, 0.24F, 12, 0xFF2A2A2D, 0xFF3A3A3D);
		m.mirroredBox(0.86F, 0.8F, 1.0F, 0.9F, 0.84F, 2.45F, STEEL);

		// Foyer et cabine
		m.box(-0.6F, 0.78F, -3.85F, 0.6F, 1.05F, -1.55F, BLACK);
		m.box(-0.98F, 1.0F, -3.95F, 0.98F, 2.72F, -1.55F, green);
		m.mirroredBox(0.981F, 1.95F, -3.45F, 0.99F, 2.45F, -2.25F, GLASS);
		m.mirroredBox(0.981F, 1.0F, -3.6F, 0.99F, 1.06F, -1.6F, brass);
		m.mirroredBox(0.25F, 2.0F, -1.551F, 0.75F, 2.45F, -1.54F, GLASS);
		m.box(-1.06F, 2.72F, -4.05F, 1.06F, 2.82F, -1.45F, 0xFF222325);
		m.box(-0.9F, 2.82F, -3.6F, 0.9F, 2.86F, -1.8F, 0xFF2B2C2E);
		m.box(-0.98F, 1.0F, -3.96F, 0.98F, 1.9F, -3.94F, greenDark);
		// Sifflet et soupapes
		m.cylinder(1, 0, -1.4F, 2.25F, 2.5F, 0.05F, 6, brass, brass);
		m.cylinder(1, 0.12F, -1.3F, 2.25F, 2.45F, 0.04F, 6, brass, brass);

		// Roues : bissel avant, trois essieux moteurs, essieu porteur arrière
		axles.add(new Axle(3.25F, 0.34F, 0.55F, WheelStyle.STEAM_SMALL));
		axles.add(new Axle(-0.9F, 0.6F, 0.74F, WheelStyle.STEAM_DRIVER));
		axles.add(new Axle(0.5F, 0.6F, 0.74F, WheelStyle.STEAM_DRIVER));
		axles.add(new Axle(1.9F, 0.6F, 0.74F, WheelStyle.STEAM_DRIVER));
		axles.add(new Axle(-3.0F, 0.4F, 0.6F, WheelStyle.STEAM_SMALL));
		// Garde-boue au-dessus des roues motrices
		for (float z : new float[]{-0.9F, 0.5F, 1.9F}) {
			m.mirroredBox(0.66F, 0.98F, z - 0.62F, 0.86F, 1.02F, z + 0.62F, 0xFF2A2B2D);
		}
		return new Model(m, axles, true);
	}

	private static Model tender() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		int green = 0xFF1F5A35;
		int brass = 0xFFD2A93F;
		m.box(-0.55F, 0.42F, -2.6F, 0.55F, 0.78F, 2.6F, BLACK);
		m.box(-0.98F, 0.78F, -2.5F, 0.98F, 2.05F, 2.5F, green);
		m.box(-1.02F, 0.78F, -2.54F, 1.02F, 0.86F, 2.54F, 0xFF2A2B2D);
		m.box(-1.02F, 2.0F, -2.54F, 1.02F, 2.12F, 2.54F, 0xFF151617);
		m.mirroredBox(0.981F, 1.1F, -2.2F, 0.99F, 1.14F, 2.2F, brass);
		m.mirroredBox(0.981F, 1.7F, -2.2F, 0.99F, 1.74F, 2.2F, brass);
		// Tas de charbon
		int coal = 0xFF141414;
		int coal2 = 0xFF242424;
		m.box(-0.9F, 2.05F, -0.6F, 0.9F, 2.3F, 2.35F, coal);
		m.box(-0.75F, 2.3F, -0.2F, 0.7F, 2.48F, 2.0F, coal2);
		m.box(-0.5F, 2.48F, 0.3F, 0.45F, 2.6F, 1.6F, coal);
		m.box(-0.2F, 2.6F, 0.7F, 0.25F, 2.66F, 1.2F, coal2);
		// Réservoir d'eau à l'arrière
		m.box(-0.9F, 2.05F, -2.4F, 0.9F, 2.15F, -0.6F, 0xFF1A4A2C);
		m.cylinder(1, 0, -1.7F, 2.15F, 2.25F, 0.22F, 12, BLACK, 0xFF2A2B2D);
		buffers(m, 2.6F, 0.7F, 0xFFB3261E);
		for (float z : new float[]{-1.6F, 0.0F, 1.6F}) {
			axles.add(new Axle(z, 0.4F, 0.6F, WheelStyle.STEAM_SMALL));
			m.mirroredBox(0.62F, 0.35F, z - 0.18F, 0.8F, 0.75F, z + 0.18F, 0xFF2A2B2D);
		}
		return new Model(m, axles, false);
	}

	// ------------------------------------------------------------------
	// Voiture voyageurs classique et wagon de marchandises
	// ------------------------------------------------------------------

	private static Model passengerCoach() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		float half = 4.4F;
		float hw = 0.98F;
		int maroon = 0xFF6E1F22;
		int cream = 0xFFEADFC2;
		int roof = 0xFF55595E;
		int gold = 0xFFC9A44A;
		m.loft(straight(-half, half, hw, 0.78F, 2.7F, 0.55F, 0.06F), 4, true, true, (x, y, z, nx, ny, nz) -> {
			if (y > 2.4F) {
				return roof;
			}
			if (Math.abs(nz) > 0.9F) {
				return maroon;
			}
			if (y > 1.47F && y < 1.56F) {
				return gold;
			}
			return y < 1.5F ? maroon : cream;
		});
		windows(m, hw, -half + 1.0F, half - 1.0F, 0.95F, 0.7F, 1.7F, 2.2F, GLASS);
		windows(m, hw, -half + 0.2F, -half + 0.8F, 1.0F, 0.6F, 0.85F, 2.3F, maroon);
		windows(m, hw, half - 0.8F, half - 0.2F, 1.0F, 0.6F, 0.85F, 2.3F, maroon);
		windows(m, hw + 0.012F, -half + 0.32F, -half + 0.7F, 1.0F, 0.36F, 1.8F, 2.2F, GLASS);
		windows(m, hw + 0.012F, half - 0.68F, half - 0.3F, 1.0F, 0.36F, 1.8F, 2.2F, GLASS);
		m.box(-0.6F, 0.52F, -3.0F, 0.6F, 0.78F, 3.0F, UNDERFRAME);
		m.box(-0.5F, 2.68F, -3.6F, 0.5F, 2.78F, 3.6F, 0xFF4A4E53);
		for (float z = -3.0F; z <= 3.01F; z += 1.5F) {
			m.cylinder(1, 0, z, 2.68F, 2.84F, 0.1F, 8, 0xFF3E4247, 0xFF2E3237);
		}
		buffers(m, half + 0.05F, 0.66F, BLACK);
		bogie(m, -3.0F, 1.6F, axles, 0.34F);
		bogie(m, 3.0F, 1.6F, axles, 0.34F);
		return new Model(m, axles, false);
	}

	private static Model freightWagon() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		float half = 3.4F;
		float hw = 0.98F;
		int wood = 0xFF7B4A2A;
		int woodDark = 0xFF5E3820;
		int roof = 0xFF6A6E73;
		int iron = 0xFF2E3033;
		m.loft(straight(-half, half, hw, 0.8F, 2.55F, 0.3F, 0.02F), 3, true, true, (x, y, z, nx, ny, nz) -> {
			if (y > 2.4F) {
				return roof;
			}
			if (Math.abs(nz) > 0.9F) {
				return woodDark;
			}
			return periodic(y, 0.8F, 0.25F, 0.08F) ? woodDark : wood;
		});
		// Montants métalliques
		windows(m, hw + 0.004F, -half, half, 1.1F, 0.08F, 0.8F, 2.45F, iron);
		// Porte coulissante avec renforts en croix
		m.mirroredBox(hw + 0.005F, 0.85F, -0.9F, hw + 0.03F, 2.4F, 0.9F, 0xFF8A5634);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * (hw + 0.035F);
			m.beam(new org.joml.Vector3f(x, 0.9F, -0.85F), new org.joml.Vector3f(x, 2.35F, 0.85F), 0.06F, iron);
			m.beam(new org.joml.Vector3f(x, 0.9F, 0.85F), new org.joml.Vector3f(x, 2.35F, -0.85F), 0.06F, iron);
		}
		m.mirroredBox(hw, 0.82F, -1.2F, hw + 0.045F, 0.88F, 1.2F, iron);
		m.mirroredBox(hw, 2.36F, -1.2F, hw + 0.045F, 2.42F, 1.2F, iron);
		m.box(-0.6F, 0.5F, -2.5F, 0.6F, 0.8F, 2.5F, UNDERFRAME);
		buffers(m, half + 0.05F, 0.66F, BLACK);
		bogie(m, -2.3F, 1.3F, axles, 0.32F);
		bogie(m, 2.3F, 1.3F, axles, 0.32F);
		return new Model(m, axles, false);
	}

	// ------------------------------------------------------------------
	// TGV
	// ------------------------------------------------------------------

	private static final int TGV_SILVER = 0xFFDCE1E6;
	private static final int TGV_ROOF = 0xFFC4CAD0;
	private static final int TGV_BLUE = 0xFF2C55A8;
	private static final int TGV_NAVY = 0xFF1E2F5E;
	private static final int TGV_ACCENT = 0xFFB0204A;

	private static int tgvLivery(float y) {
		if (y < 0.9F) {
			return TGV_NAVY;
		}
		if (y < 1.25F) {
			return TGV_SILVER;
		}
		if (y < 1.33F) {
			return TGV_ACCENT;
		}
		if (y < 2.2F) {
			return TGV_BLUE;
		}
		if (y < 2.3F) {
			return TGV_SILVER;
		}
		return TGV_ROOF;
	}

	private static Model tgvPowerCar() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		float back = -5.0F;
		Section[] body = straight(back, 1.8F, 0.98F, 0.55F, 2.85F, 0.5F, 0.15F);
		Section[] nose = smooth(new Section[]{
				s(1.8F, 0.98F, 0.55F, 2.85F, 0.5F, 0.15F),
				s(2.7F, 0.98F, 0.56F, 2.78F, 0.55F, 0.16F),
				s(3.5F, 0.95F, 0.58F, 2.45F, 0.62F, 0.18F),
				s(4.15F, 0.88F, 0.6F, 1.95F, 0.62F, 0.2F),
				s(4.6F, 0.76F, 0.62F, 1.45F, 0.5F, 0.2F),
				s(4.88F, 0.58F, 0.66F, 1.08F, 0.32F, 0.18F),
				s(5.0F, 0.34F, 0.72F, 0.9F, 0.12F, 0.1F)}, 6);
		Section[] all = concat(body, nose);
		m.loft(all, 5, true, true, (x, y, z, nx, ny, nz) -> {
			if (z > 1.8F) {
				// Nez : pare-brise, phares et bande rouge
				if (z > 2.95F && z < 4.35F && y > 1.65F && Math.abs(x) < 0.82F && ny > 0.15F) {
					return GLASS;
				}
				if (z > 4.55F && y > 0.82F && y < 1.02F && Math.abs(x) > 0.18F && Math.abs(x) < 0.55F) {
					return HEADLIGHT;
				}
				if (y < 0.9F) {
					return TGV_NAVY;
				}
				boolean side = Math.abs(nx) > 0.5F;
				if (y < 1.25F) {
					return TGV_SILVER;
				}
				if (y < 1.33F && side) {
					return TGV_ACCENT;
				}
				return y < 2.2F && side ? TGV_BLUE : TGV_SILVER;
			}
			if (z < back + 0.02F && Math.abs(nz) > 0.9F) {
				return y < 0.9F ? TGV_NAVY : 0xFF3A3E44;
			}
			return tgvLivery(y);
		});
		// Vitre latérale de la cabine et grilles d'aération
		windows(m, 0.98F, 0.7F, 1.7F, 1.0F, 1.0F, 1.62F, 2.1F, GLASS_LIGHT);
		windows(m, 0.98F, -3.8F, 0.2F, 0.2F, 0.08F, 1.55F, 2.05F, 0xFF15244A);
		// Équipements de toiture et pantographe
		m.box(-0.5F, 2.85F, -4.3F, 0.5F, 2.98F, -0.6F, 0xFF9DA3A9);
		pantograph(m, -2.4F, 2.95F);
		// Châssis, jupes et attelage arrière
		m.box(-0.7F, 0.42F, -4.6F, 0.7F, 0.6F, 2.4F, UNDERFRAME);
		m.box(-0.35F, 0.4F, -5.25F, 0.35F, 2.4F, -4.98F, 0xFF2B2F36);
		m.box(-0.08F, 0.55F, -5.4F, 0.08F, 0.7F, -5.0F, 0xFF4A4D52);
		m.box(-0.5F, 0.8F, -5.02F, -0.3F, 0.95F, -4.99F, TAILLIGHT);
		m.box(0.3F, 0.8F, -5.02F, 0.5F, 0.95F, -4.99F, TAILLIGHT);
		bogie(m, -3.3F, 1.6F, axles, 0.36F);
		bogie(m, 2.5F, 1.6F, axles, 0.36F);
		return new Model(m, axles, false);
	}

	private static void pantograph(Mesh m, float z, float roofY) {
		int metal = 0xFF9EA4AA;
		int dark = 0xFF3A3D42;
		org.joml.Vector3f a = new org.joml.Vector3f(0, roofY + 0.18F, z - 0.6F);
		org.joml.Vector3f b = new org.joml.Vector3f(0, roofY + 0.62F, z + 0.25F);
		org.joml.Vector3f c = new org.joml.Vector3f(0, roofY + 0.98F, z - 0.35F);
		m.box(-0.35F, roofY, z - 0.75F, 0.35F, roofY + 0.18F, z - 0.45F, dark);
		for (float x : new float[]{-0.3F, 0.3F}) {
			m.cylinder(1, x, z + 0.4F, roofY, roofY + 0.2F, 0.07F, 8, 0xFFB5463A, 0xFFB5463A);
			m.cylinder(1, x, z - 0.6F, roofY, roofY + 0.2F, 0.07F, 8, 0xFFB5463A, 0xFFB5463A);
		}
		m.beam(new org.joml.Vector3f(a).add(0.18F, 0, 0), new org.joml.Vector3f(b).add(0.12F, 0, 0), 0.06F, metal);
		m.beam(new org.joml.Vector3f(a).add(-0.18F, 0, 0), new org.joml.Vector3f(b).add(-0.12F, 0, 0), 0.06F, metal);
		m.beam(new org.joml.Vector3f(b).add(0.1F, 0, 0), new org.joml.Vector3f(c).add(0.03F, 0, 0), 0.05F, metal);
		m.beam(new org.joml.Vector3f(b).add(-0.1F, 0, 0), new org.joml.Vector3f(c).add(-0.03F, 0, 0), 0.05F, metal);
		m.box(-0.75F, c.y - 0.02F, c.z - 0.06F, 0.75F, c.y + 0.05F, c.z + 0.06F, 0xFF55595F);
		m.box(-0.8F, c.y - 0.04F, c.z - 0.04F, -0.7F, c.y + 0.02F, c.z + 0.04F, dark);
		m.box(0.7F, c.y - 0.04F, c.z - 0.04F, 0.8F, c.y + 0.02F, c.z + 0.04F, dark);
	}

	private static Model tgvCar() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		float half = 4.5F;
		Section[] body = straight(-half, half, 0.98F, 0.55F, 2.85F, 0.5F, 0.15F);
		m.loft(body, 5, true, true, (x, y, z, nx, ny, nz) -> Math.abs(nz) > 0.9F ? 0xFF3A3E44 : tgvLivery(y));
		windows(m, 0.98F, -half + 1.0F, half - 1.0F, 0.9F, 0.68F, 1.5F, 2.08F, GLASS);
		windows(m, 0.98F, -half + 0.3F, -half + 0.9F, 1.0F, 0.6F, 0.95F, 2.18F, 0xFF2A4A8C);
		windows(m, 0.98F, half - 0.9F, half - 0.3F, 1.0F, 0.6F, 0.95F, 2.18F, 0xFF2A4A8C);
		// Soufflets d'intercirculation
		m.box(-0.7F, 0.6F, half - 0.01F, 0.7F, 2.6F, half + 0.22F, 0xFF2A2C30);
		m.box(-0.7F, 0.6F, -half - 0.22F, 0.7F, 2.6F, -half + 0.01F, 0xFF2A2C30);
		m.box(-0.7F, 0.42F, -3.6F, 0.7F, 0.6F, 3.6F, UNDERFRAME);
		bogie(m, -3.2F, 1.6F, axles, 0.36F);
		bogie(m, 3.2F, 1.6F, axles, 0.36F);
		return new Model(m, axles, false);
	}

	// ------------------------------------------------------------------
	// Shinkansen (série N700)
	// ------------------------------------------------------------------

	private static final int SK_WHITE = 0xFFF4F6F9;
	private static final int SK_BLUE = 0xFF1546A0;
	private static final int SK_GREY = 0xFFB6BCC4;

	private static int shinkansenLivery(float y) {
		if (y < 0.78F) {
			return SK_GREY;
		}
		if (y > 0.98F && y < 1.2F) {
			return SK_BLUE;
		}
		if (y > 1.25F && y < 1.31F) {
			return SK_BLUE;
		}
		return SK_WHITE;
	}

	private static Model shinkansenHead() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		float back = -5.5F;
		Section[] body = straight(back, 0.4F, 0.99F, 0.5F, 2.8F, 0.6F, 0.25F);
		Section[] nose = smooth(new Section[]{
				s(0.4F, 0.99F, 0.5F, 2.8F, 0.6F, 0.25F),
				s(1.3F, 0.99F, 0.5F, 2.72F, 0.62F, 0.25F),
				s(2.2F, 0.98F, 0.52F, 2.42F, 0.7F, 0.26F),
				s(3.1F, 0.95F, 0.55F, 2.0F, 0.74F, 0.27F),
				s(3.9F, 0.9F, 0.57F, 1.58F, 0.68F, 0.27F),
				s(4.6F, 0.8F, 0.6F, 1.22F, 0.52F, 0.26F),
				s(5.1F, 0.62F, 0.62F, 0.98F, 0.33F, 0.2F),
				s(5.4F, 0.38F, 0.64F, 0.84F, 0.18F, 0.12F),
				s(5.5F, 0.18F, 0.67F, 0.76F, 0.06F, 0.05F)}, 6);
		Section[] all = concat(body, nose);
		m.loft(all, 6, true, true, (x, y, z, nx, ny, nz) -> {
			if (z > 0.4F) {
				if (z > 1.25F && z < 2.75F && y > 1.95F && Math.abs(x) < 0.85F && ny > 0.2F) {
					return GLASS;
				}
				if (z > 4.2F && z < 4.75F && y > 0.82F && y < 1.02F && Math.abs(nx) > 0.35F) {
					return HEADLIGHT;
				}
				if (y < 0.72F) {
					return SK_GREY;
				}
				return Math.abs(nx) > 0.45F && z < 3.6F ? shinkansenLivery(y) : SK_WHITE;
			}
			if (z < back + 0.02F && Math.abs(nz) > 0.9F) {
				return 0xFF3A3E44;
			}
			return shinkansenLivery(y);
		});
		windows(m, 0.99F, -0.6F, 0.3F, 1.0F, 0.9F, 1.62F, 2.05F, GLASS);
		windows(m, 0.99F, back + 0.9F, -1.0F, 0.72F, 0.5F, 1.55F, 2.0F, GLASS);
		m.box(-0.36F, 2.79F, -3.6F, 0.36F, 2.95F, -2.4F, 0xFFCDD2D8);
		pantograph(m, -3.0F, 2.85F);
		m.box(-0.75F, 0.4F, -5.0F, 0.75F, 0.55F, 1.8F, UNDERFRAME);
		m.box(-0.7F, 0.6F, -5.72F, 0.7F, 2.55F, -5.49F, 0xFF2A2C30);
		bogie(m, -3.8F, 1.6F, axles, 0.36F);
		bogie(m, 2.0F, 1.6F, axles, 0.36F);
		return new Model(m, axles, false);
	}

	private static Model shinkansenCar() {
		Mesh m = new Mesh();
		List<Axle> axles = new ArrayList<>();
		float half = 5.0F;
		Section[] body = straight(-half, half, 0.99F, 0.5F, 2.8F, 0.6F, 0.25F);
		m.loft(body, 6, true, true, (x, y, z, nx, ny, nz) -> Math.abs(nz) > 0.9F ? 0xFF3A3E44 : shinkansenLivery(y));
		windows(m, 0.99F, -half + 1.1F, half - 1.1F, 0.72F, 0.5F, 1.55F, 2.0F, GLASS);
		windows(m, 0.99F, -half + 0.45F, -half + 1.0F, 1.0F, 0.55F, 0.8F, 2.3F, 0xFFE2E6EB);
		windows(m, 0.99F, half - 1.0F, half - 0.45F, 1.0F, 0.55F, 0.8F, 2.3F, 0xFFE2E6EB);
		m.box(-0.7F, 0.6F, half - 0.01F, 0.7F, 2.55F, half + 0.22F, 0xFF2A2C30);
		m.box(-0.7F, 0.6F, -half - 0.22F, 0.7F, 2.55F, -half + 0.01F, 0xFF2A2C30);
		m.box(-0.75F, 0.4F, -3.6F, 0.75F, 0.55F, 3.6F, UNDERFRAME);
		bogie(m, -3.5F, 1.6F, axles, 0.36F);
		bogie(m, 3.5F, 1.6F, axles, 0.36F);
		return new Model(m, axles, false);
	}

	// ------------------------------------------------------------------
	// Roues (maillage centré sur l'essieu, axe X)
	// ------------------------------------------------------------------

	private static Mesh buildWheel(WheelStyle style, float r) {
		Mesh m = new Mesh();
		float w = 0.1F;
		switch (style) {
			case MODERN -> {
				m.cylinder(0, 0, 0, -w / 2, w / 2, r, 14, 0xFF3B3E42, 0xFF5A5E63);
				m.cylinder(0, 0, 0, -w / 2 - 0.02F, w / 2 + 0.02F, r * 0.35F, 10, 0xFF6E7378, 0xFF6E7378);
				m.box(-w / 2 - 0.021F, -0.03F, r * 0.4F, w / 2 + 0.021F, 0.03F, r * 0.85F, 0xFF494C51);
			}
			case STEAM_DRIVER, STEAM_SMALL -> {
				int red = 0xFFB3261E;
				m.cylinder(0, 0, 0, -w / 2, w / 2, r, 18, 0xFF1A1A1B, 0xFF4A1210);
				m.cylinder(0, 0, 0, -w / 2 - 0.01F, w / 2 + 0.01F, r * 0.86F, 18, red, 0xFF5A1612);
				int spokes = style == WheelStyle.STEAM_DRIVER ? 12 : 8;
				for (int i = 0; i < spokes; i++) {
					double a = Math.PI * 2 * i / spokes;
					org.joml.Vector3f from = new org.joml.Vector3f(0, (float) Math.cos(a) * r * 0.15F, (float) Math.sin(a) * r * 0.15F);
					org.joml.Vector3f to = new org.joml.Vector3f(0, (float) Math.cos(a) * r * 0.84F, (float) Math.sin(a) * r * 0.84F);
					m.beam(from.add(w / 2 + 0.012F, 0, 0), to.add(w / 2 + 0.012F, 0, 0), 0.045F, red);
					m.beam(new org.joml.Vector3f(from).sub(w + 0.024F, 0, 0), new org.joml.Vector3f(to).sub(w + 0.024F, 0, 0), 0.045F, red);
				}
				m.cylinder(0, 0, 0, -w / 2 - 0.04F, w / 2 + 0.04F, r * 0.18F, 10, 0xFFD2A93F, 0xFFD2A93F);
				if (style == WheelStyle.STEAM_DRIVER) {
					// Contrepoids
					m.box(-w / 2 - 0.025F, r * 0.35F, -r * 0.3F, w / 2 + 0.025F, r * 0.8F, r * 0.3F, 0xFF8E1D17);
				}
			}
		}
		return m;
	}
}
