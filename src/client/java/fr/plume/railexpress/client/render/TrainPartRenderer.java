package fr.plume.railexpress.client.render;

import fr.plume.railexpress.entity.TrainPartEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Les morceaux de coque sont invisibles : seul le véhicule parent est dessiné. */
public class TrainPartRenderer extends EntityRenderer<TrainPartEntity, EntityRenderState> {
	public TrainPartRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public boolean shouldRender(TrainPartEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return false;
	}
}
