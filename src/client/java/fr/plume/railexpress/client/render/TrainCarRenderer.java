package fr.plume.railexpress.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.client.render.TrainModels.Axle;
import fr.plume.railexpress.client.render.TrainModels.Model;
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

	public TrainCarRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.1F;
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
	}

	@Override
	public void submit(TrainRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Model model = TrainModels.get(state.carType);
		int light = state.lightCoords;
		int overlay = OverlayTexture.NO_OVERLAY;

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
		if (state.hurt > 0) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.hurt * 1.6F) * state.hurt * 0.25F));
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
				(pose, consumer) -> model.body().render(pose, consumer, light, overlay, -1));

		float driverAngle = 0;
		for (Axle axle : model.axles()) {
			float angle = state.wheelRot * 0.45F / axle.radius();
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
		if (model.steamRods()) {
			Mesh rods = steamRods(driverAngle);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE),
					(pose, consumer) -> rods.render(pose, consumer, light, overlay, -1));
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** Bielles d'accouplement et bielles motrices animées de la locomotive à vapeur. */
	private static Mesh steamRods(float angle) {
		Mesh m = new Mesh();
		float axleY = 0.66F;
		float crank = 0.24F;
		int steel = 0xFFB9BFC5;
		for (int side = -1; side <= 1; side += 2) {
			// Décalage de 90° entre les deux côtés, comme sur une vraie locomotive
			float a = angle + (side > 0 ? 0 : (float) Math.PI / 2);
			float dy = Mth.cos(a) * crank;
			float dz = Mth.sin(a) * crank;
			float x = side * 0.87F;
			m.box(x - 0.03F, axleY + dy - 0.05F, -0.9F + dz - 0.08F, x + 0.03F, axleY + dy + 0.05F, 1.9F + dz + 0.08F, steel);
			for (float z : new float[]{-0.9F, 0.5F, 1.9F}) {
				m.cylinder(0, axleY + dy, z + dz, x - 0.05F, x + 0.05F, 0.05F, 8, 0xFFD2A93F, 0xFFD2A93F);
			}
			// Bielle motrice : de la crosse du piston au maneton de l'essieu central
			float crossZ = 2.45F - 0.3F + dz * 0.3F;
			Vector3f crosshead = new Vector3f(x + side * 0.05F, 0.86F, crossZ);
			Vector3f pin = new Vector3f(x + side * 0.05F, axleY + dy, 0.5F + dz);
			m.beam(crosshead, pin, 0.08F, steel);
			m.box(x + side * 0.05F - 0.04F, 0.8F, crossZ - 0.12F, x + side * 0.05F + 0.04F, 0.92F, crossZ + 0.12F, 0xFF6A7076);
		}
		return m;
	}
}
