package fr.plume.railexpress.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/**
 * Petit maillage de quadrilatères colorés (sans texture), construit une seule fois
 * puis rendu à chaque image. Les unités sont des blocs ; +Z est l'avant du véhicule, +Y le haut.
 */
public final class Mesh {
	/** Couleur spéciale : la facette n'est pas dessinée (ouverture de fenêtre ou de porte). */
	public static final int HOLE = 0;

	/** Pour chaque quad : 12 coordonnées, 3 composantes de normale. */
	private final List<float[]> quads = new ArrayList<>();
	private final List<Integer> colors = new ArrayList<>();

	public interface ColorFunction {
		/** Couleur ARGB d'une facette selon la position de son centre et sa normale. */
		int color(float x, float y, float z, float nx, float ny, float nz);
	}

	public int size() {
		return quads.size();
	}

	public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int color) {
		Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
		if (n.lengthSquared() < 1.0E-12F) {
			n.set(0, 1, 0);
		}
		n.normalize();
		quads.add(new float[]{a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, n.x, n.y, n.z});
		colors.add(color);
	}

	public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, ColorFunction fn) {
		Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
		if (n.lengthSquared() < 1.0E-12F) {
			n.set(0, 1, 0);
		}
		n.normalize();
		float cx = (a.x + b.x + c.x + d.x) / 4;
		float cy = (a.y + b.y + c.y + d.y) / 4;
		float cz = (a.z + b.z + c.z + d.z) / 4;
		int color = fn.color(cx, cy, cz, n.x, n.y, n.z);
		if (color != HOLE) {
			quad(a, b, c, d, color);
		}
	}

	/** Quad dont la normale est forcée du côté indiqué. */
	public void quadFacing(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float nx, float ny, float nz, int color) {
		Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
		if (n.x * nx + n.y * ny + n.z * nz < 0) {
			quad(a, d, c, b, color);
		} else {
			quad(a, b, c, d, color);
		}
	}

	/** Pavé aligné sur les axes. */
	public Mesh box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		Vector3f p000 = new Vector3f(x0, y0, z0), p100 = new Vector3f(x1, y0, z0);
		Vector3f p010 = new Vector3f(x0, y1, z0), p110 = new Vector3f(x1, y1, z0);
		Vector3f p001 = new Vector3f(x0, y0, z1), p101 = new Vector3f(x1, y0, z1);
		Vector3f p011 = new Vector3f(x0, y1, z1), p111 = new Vector3f(x1, y1, z1);
		quad(p010, p011, p111, p110, color); // haut
		quad(p000, p100, p101, p001, darker(color, 0.75F)); // bas
		quad(p001, p101, p111, p011, color); // avant (+z)
		quad(p100, p000, p010, p110, color); // arrière (-z)
		quad(p101, p100, p110, p111, color); // droite (+x)
		quad(p000, p001, p011, p010, color); // gauche (-x)
		return this;
	}

	/** Repère local tourné autour de l'axe Y (pour les sièges et meubles orientés). */
	public record Frame(float ox, float oy, float oz, float yawDeg) {
		public Vector3f map(float x, float y, float z) {
			double a = Math.toRadians(yawDeg);
			float c = (float) Math.cos(a);
			float s = (float) Math.sin(a);
			// yaw 0 : +Z local = +Z ; yaw 90 : +Z local = +X
			return new Vector3f(ox + x * c + z * s, oy + y, oz - x * s + z * c);
		}
	}

	/** Pavé exprimé dans un repère local tourné. */
	public Mesh box(Frame f, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		Vector3f p000 = f.map(x0, y0, z0), p100 = f.map(x1, y0, z0);
		Vector3f p010 = f.map(x0, y1, z0), p110 = f.map(x1, y1, z0);
		Vector3f p001 = f.map(x0, y0, z1), p101 = f.map(x1, y0, z1);
		Vector3f p011 = f.map(x0, y1, z1), p111 = f.map(x1, y1, z1);
		quad(p010, p011, p111, p110, color);
		quad(p000, p100, p101, p001, darker(color, 0.75F));
		quad(p001, p101, p111, p011, color);
		quad(p100, p000, p010, p110, color);
		quad(p101, p100, p110, p111, color);
		quad(p000, p001, p011, p010, color);
		return this;
	}

	/** Pavé symétrique de part et d'autre de l'axe X=0. */
	public Mesh mirroredBox(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		box(x0, y0, z0, x1, y1, z1, color);
		box(-x1, y0, z0, -x0, y1, z1, color);
		return this;
	}

	/** Poutre de section carrée entre deux points quelconques. */
	public Mesh beam(Vector3f from, Vector3f to, float thickness, int color) {
		Vector3f axis = new Vector3f(to).sub(from);
		if (axis.lengthSquared() < 1.0E-8F) {
			return this;
		}
		axis.normalize();
		Vector3f helper = Math.abs(axis.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		Vector3f u = new Vector3f(axis).cross(helper).normalize().mul(thickness / 2);
		Vector3f v = new Vector3f(axis).cross(u).normalize().mul(thickness / 2);
		Vector3f[] s = new Vector3f[4];
		Vector3f[] e = new Vector3f[4];
		float[][] signs = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		for (int i = 0; i < 4; i++) {
			Vector3f off = new Vector3f(u).mul(signs[i][0]).add(new Vector3f(v).mul(signs[i][1]));
			s[i] = new Vector3f(from).add(off);
			e[i] = new Vector3f(to).add(off);
		}
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			quad(s[i], s[j], e[j], e[i], color);
		}
		quad(s[3], s[2], s[1], s[0], color);
		quad(e[0], e[1], e[2], e[3], color);
		return this;
	}

	/**
	 * Cylindre. axis : 0 = X, 1 = Y, 2 = Z. (a, b) sont les coordonnées du centre dans le plan perpendiculaire,
	 * [start, end] l'étendue le long de l'axe.
	 */
	public Mesh cylinder(int axis, float a, float b, float start, float end, float radius, int segments, int sideColor, int capColor) {
		Vector3f[] s = new Vector3f[segments];
		Vector3f[] e = new Vector3f[segments];
		for (int i = 0; i < segments; i++) {
			double ang = Math.PI * 2 * i / segments;
			float ca = (float) Math.cos(ang) * radius;
			// Pour l'axe Y, l'orientation est inversée afin que les normales restent tournées vers l'extérieur
			float sa = (float) Math.sin(ang) * radius * (axis == 1 ? -1 : 1);
			s[i] = point(axis, a + ca, b + sa, start);
			e[i] = point(axis, a + ca, b + sa, end);
		}
		Vector3f cs = point(axis, a, b, start);
		Vector3f ce = point(axis, a, b, end);
		for (int i = 0; i < segments; i++) {
			int j = (i + 1) % segments;
			quad(s[i], s[j], e[j], e[i], sideColor);
			quad(cs, cs, s[j], s[i], capColor);
			quad(ce, ce, e[i], e[j], capColor);
		}
		return this;
	}

	private static Vector3f point(int axis, float a, float b, float along) {
		return switch (axis) {
			case 0 -> new Vector3f(along, a, b);
			case 1 -> new Vector3f(a, along, b);
			default -> new Vector3f(a, b, along);
		};
	}

	/** Section transversale d'une caisse : demi-largeur, bas, haut, rayons des arrondis de toit et de bas de caisse. */
	public record Section(float z, float halfWidth, float bottom, float top, float roofRadius, float floorRadius) {
	}

	/** Caisse lissée passant par une suite de sections (permet les nez profilés des trains à grande vitesse). */
	public Mesh loft(Section[] sections, int arcSegments, boolean capStart, boolean capEnd, ColorFunction fn) {
		return loft(sections, arcSegments, capStart, capEnd, fn, null);
	}

	/**
	 * @param sideBreaks altitudes supplémentaires où découper les flancs (bords de fenêtres, bandes de livrée) ;
	 *                   uniquement pour des sections de même hauteur.
	 */
	public Mesh loft(Section[] sections, int arcSegments, boolean capStart, boolean capEnd, ColorFunction fn, float[] sideBreaks) {
		List<Vector3f[]> rings = new ArrayList<>();
		for (Section section : sections) {
			rings.add(ring(section, arcSegments, sideBreaks));
		}
		for (int i = 0; i + 1 < rings.size(); i++) {
			Vector3f[] r0 = rings.get(i);
			Vector3f[] r1 = rings.get(i + 1);
			for (int k = 0; k < r0.length; k++) {
				int l = (k + 1) % r0.length;
				quad(r0[k], r0[l], r1[l], r1[k], fn);
			}
		}
		if (capStart) {
			cap(rings.get(0), sections[0].z(), false, fn);
		}
		if (capEnd) {
			cap(rings.get(rings.size() - 1), sections[sections.length - 1].z(), true, fn);
		}
		return this;
	}

	private void cap(Vector3f[] ring, float z, boolean front, ColorFunction fn) {
		float cy = 0;
		for (Vector3f p : ring) {
			cy += p.y;
		}
		Vector3f center = new Vector3f(0, cy / ring.length, z);
		for (int k = 0; k < ring.length; k++) {
			int l = (k + 1) % ring.length;
			if (front) {
				quad(center, center, ring[k], ring[l], fn);
			} else {
				quad(center, center, ring[l], ring[k], fn);
			}
		}
	}

	private static final int SIDE_STEPS = 22;
	private static final int ROOF_STEPS = 6;
	private static final int FLOOR_STEPS = 3;

	public static Vector3f[] ring(Section s, int seg, float[] sideBreaks) {
		List<Vector3f> pts = new ArrayList<>();
		float rT = Math.min(s.roofRadius, Math.min(s.halfWidth, (s.top - s.bottom) / 2) * 0.999F);
		float rB = Math.min(s.floorRadius, Math.min(s.halfWidth, (s.top - s.bottom) / 2) * 0.999F);
		// Parcours dans le sens trigonométrique vu de l'avant ; les flancs sont subdivisés pour les bandes de livrée
		line(pts, 0, s.bottom, s.halfWidth - rB, s.bottom, FLOOR_STEPS, s.z);
		arc(pts, s.halfWidth - rB, s.bottom + rB, rB, -90, 0, seg, s.z);
		side(pts, s.halfWidth, s.bottom + rB, s.top - rT, sideBreaks, s.z, true);
		arc(pts, s.halfWidth - rT, s.top - rT, rT, 0, 90, seg, s.z);
		line(pts, s.halfWidth - rT, s.top, -s.halfWidth + rT, s.top, ROOF_STEPS, s.z);
		arc(pts, -s.halfWidth + rT, s.top - rT, rT, 90, 180, seg, s.z);
		side(pts, -s.halfWidth, s.bottom + rB, s.top - rT, sideBreaks, s.z, false);
		arc(pts, -s.halfWidth + rB, s.bottom + rB, rB, 180, 270, seg, s.z);
		line(pts, -s.halfWidth + rB, s.bottom, 0, s.bottom, FLOOR_STEPS, s.z);
		return pts.toArray(new Vector3f[0]);
	}

	/** Points intermédiaires d'un flanc vertical, avec découpes supplémentaires. */
	private static void side(List<Vector3f> pts, float x, float y0, float y1, float[] breaks, float z, boolean up) {
		List<Float> clean = new ArrayList<>();
		for (int i = 1; i < SIDE_STEPS; i++) {
			clean.add(y0 + (y1 - y0) * i / SIDE_STEPS);
		}
		if (breaks != null) {
			for (float b : breaks) {
				boolean near = false;
				for (float y : clean) {
					near |= Math.abs(y - b) < 0.004F;
				}
				if (!near && b > y0 + 0.005F && b < y1 - 0.005F) {
					clean.add(b);
				}
			}
		}
		clean.sort(Float::compare);
		if (!up) {
			java.util.Collections.reverse(clean);
		}
		for (float y : clean) {
			pts.add(new Vector3f(x, y, z));
		}
	}

	/**
	 * Face d'extrémité percée d'une ouverture rectangulaire (passage d'intercirculation).
	 * @param facing +1 si la face regarde vers +Z
	 */
	public Mesh endFrame(Section s, int seg, float hx, float hy0, float hy1, int facing, int color) {
		Vector3f[] ring = ring(s, seg, null);
		float cy = (hy0 + hy1) / 2;
		Vector3f[] inner = new Vector3f[ring.length];
		for (int k = 0; k < ring.length; k++) {
			float dx = ring[k].x;
			float dy = ring[k].y - cy;
			float tx = Math.abs(dx) < 1.0E-6F ? Float.MAX_VALUE : hx / Math.abs(dx);
			float ty = Math.abs(dy) < 1.0E-6F ? Float.MAX_VALUE : ((hy1 - hy0) / 2) / Math.abs(dy);
			float t = Math.min(tx, ty);
			inner[k] = new Vector3f(dx * t, cy + dy * t, s.z);
		}
		for (int k = 0; k < ring.length; k++) {
			int l = (k + 1) % ring.length;
			quadFacing(ring[k], ring[l], inner[l], inner[k], 0, 0, facing, color);
		}
		return this;
	}

	/** Points intermédiaires d'un segment, extrémités exclues (elles sont fournies par les arcs). */
	private static void line(List<Vector3f> pts, float x0, float y0, float x1, float y1, int steps, float z) {
		for (int i = 1; i < steps; i++) {
			float t = (float) i / steps;
			pts.add(new Vector3f(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z));
		}
	}

	private static void arc(List<Vector3f> pts, float cx, float cy, float r, float from, float to, int seg, float z) {
		for (int i = 0; i <= seg; i++) {
			double ang = Math.toRadians(from + (to - from) * i / seg);
			pts.add(new Vector3f(cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, z));
		}
	}

	public static int darker(int argb, float factor) {
		int a = argb >>> 24;
		int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, (int) ((argb & 0xFF) * factor));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Émet tous les quads dans le tampon de sommets. */
	public void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int tint) {
		for (int i = 0; i < quads.size(); i++) {
			float[] q = quads.get(i);
			int color = tint == -1 ? colors.get(i) : multiply(colors.get(i), tint);
			for (int v = 0; v < 4; v++) {
				consumer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2])
						.setColor(color)
						.setUv(0.5F, 0.5F)
						.setOverlay(overlay)
						.setLight(light)
						.setNormal(pose, q[12], q[13], q[14]);
			}
		}
	}

	private static int multiply(int a, int b) {
		int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
		int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
		int bl = (a & 0xFF) * (b & 0xFF) / 255;
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
