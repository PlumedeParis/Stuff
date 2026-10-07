package fr.plume.railexpress.client.render;

import fr.plume.railexpress.entity.CarType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class TrainRenderState extends EntityRenderState {
	public CarType carType = CarType.PASSENGER_COACH;
	public float yaw;
	public float pitch;
	public float wheelRot;
	public float hurt;
	public final float[] doors = new float[16];
}
