package fr.plume.railexpress.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.client.render.TrainModels.Axle;
import fr.plume.railexpress.client.render.TrainModels.DoorPanel;
import fr.plume.railexpress.client.render.TrainModels.Model;
import fr.plume.railexpress.client.render.TrainModels.Rods;
import fr.plume.railexpress.entity.CarLayout;
import fr.plume.railexpress.entity.TrainCarEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Rendu des véhicules à partir des maillages procéduraux de {@link TrainModels}. */
public class TrainCarRenderer extends EntityRenderer<TrainCarEntity, TrainRenderState> {
	private static final Identifier WHITE = RailExpress.id("textures/entity/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	public TrainCarRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.1F;
	}

	/** Le train est long : on teste la visibilité sur toute sa longueur, pas seulement sur sa petite boîte centrale. */
	@Override
	public boolean shouldRender(TrainCarEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		if (!entity.shouldRender(x, y, z)) {
			return false;
		}
		double reach = entity.getCarType().worldLength() / 2.0 + 1.5;
		net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(reach, 3.0, reach);
		return frustum.isVisible(box);
	}

	@Override
	public TrainRenderState createRenderState() {
		return new TrainRenderState();
	}

	@Override
	public void extractRenderState(TrainCarEntity entity, TrainRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.carType = entity.getCarType();
		state.yaw = entity.getYRot(partialTick);
		state.pitch = entity.getXRot(partialTick);
		state.wheelRot = Mth.lerp(partialTick, entity.wheelRotO, entity.wheelRot);
		state.hurt = entity.getHurtTicks() > 0 ? entity.getHurtTicks() - partialTick : 0;
		for (int i = 0; i < state.doors.length; i++) {
			state.doors[i] = Mth.lerp(partialTick, entity.doorOpenO[i], entity.doorOpen[i]);
		}
	}

	@Override
	public void submit(TrainRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Model model = TrainModels.get(state.carType);
		int light = state.lightCoords;
		int overlay = OverlayTexture.NO_OVERLAY;

		poseStack.pushPose();
		poseStack.translate(0, CarLayout.Y_OFFSET, 0);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
		float scale = (float) CarLayout.SCALE;
		poseStack.scale(scale, scale, scale);
		if (state.hurt > 0) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.hurt * 1.6F) * state.hurt * 0.25F));
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
				(pose, consumer) -> model.body().render(pose, consumer, light, overlay, -1, true));
		if (model.lamps().size() > 0) {
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
					(pose, consumer) -> model.lamps().render(pose, consumer, FULL_BRIGHT, overlay, -1));
		}
		for (DoorPanel door : model.doors()) {
			float open = door.index() < state.doors.length ? state.doors[door.index()] : 0;
			poseStack.pushPose();
			poseStack.translate(0, 0, door.slide() * smooth(open));
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
					(pose, consumer) -> door.mesh().render(pose, consumer, light, overlay, -1, true));
			poseStack.popPose();
		}

		float driverAngle = 0;
		for (Axle axle : model.axles()) {
			float angle = state.wheelRot * 0.45F / (axle.radius() * scale);
			if (axle.style() == TrainModels.WheelStyle.STEAM_DRIVER) {
				driverAngle = angle;
			}
			Mesh wheel = TrainModels.wheel(axle.style(), axle.radius());
			for (int side = -1; side <= 1; side += 2) {
				poseStack.pushPose();
				poseStack.translate(side * axle.gauge(), axle.radius() + 0.06F, axle.z());
				poseStack.mulPose(Axis.XP.rotation(angle));
				if (side < 0) {
					poseStack.mulPose(Axis.YP.rotationDegrees(180));
				}
				collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
						(pose, consumer) -> wheel.render(pose, consumer, light, overlay, -1));
				poseStack.popPose();
			}
		}
		if (model.rods() != null) {
			Mesh rods = steamRods(model.rods(), driverAngle);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
					(pose, consumer) -> rods.render(pose, consumer, light, overlay, -1));
		}
		// Vitrages en dernier (translucides)
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WHITE),
				(pose, consumer) -> model.glass().render(pose, consumer, light, overlay, -1));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Bielles d'accouplement et bielles motrices animées de la locomotive à vapeur. */
	private static Mesh steamRods(Rods r, float angle) {
		Mesh m = new Mesh();
		float axleY = r.radius() + 0.06F;
		int steel = 0xFFB9BFC5;
		float first = r.drivers()[0];
		float last = r.drivers()[r.drivers().length - 1];
		float middle = r.drivers()[r.drivers().length / 2];
		for (int side = -1; side <= 1; side += 2) {
			// Décalage de 90° entre les deux côtés, comme sur une vraie locomotive
			float a = angle + (side > 0 ? 0 : (float) Math.PI / 2);
			float dy = Mth.cos(a) * r.crank();
			float dz = Mth.sin(a) * r.crank();
			float x = side * r.x();
			m.box(x - 0.03F, axleY + dy - 0.05F, first + dz - 0.08F, x + 0.03F, axleY + dy + 0.05F, last + dz + 0.08F, steel);
			for (float z : r.drivers()) {
				m.cylinder(0, axleY + dy, z + dz, x - 0.05F, x + 0.05F, 0.05F, 8, 0xFFD2A93F, 0xFFD2A93F);
			}
			float crossZ = r.crossheadZ() - 0.3F + dz * 0.3F;
			Vector3f crosshead = new Vector3f(x + side * 0.06F, r.cylinderY(), crossZ);
			Vector3f pin = new Vector3f(x + side * 0.06F, axleY + dy, middle + dz);
			m.beam(crosshead, pin, 0.08F, steel);
			m.box(x + side * 0.06F - 0.04F, r.cylinderY() - 0.06F, crossZ - 0.12F, x + side * 0.06F + 0.04F, r.cylinderY() + 0.06F, crossZ + 0.12F, 0xFF6A7076);
			m.beam(new Vector3f(x + side * 0.06F, r.cylinderY(), crossZ), new Vector3f(x + side * 0.06F, r.cylinderY(), r.crossheadZ() + 0.2F), 0.05F, 0xFFC8CDD2);
		}
		return m;
	}
}
