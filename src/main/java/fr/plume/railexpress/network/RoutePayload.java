package fr.plume.railexpress.network;

import fr.plume.railexpress.RailExpress;
import fr.plume.railexpress.block.CrossingTrackBlock;
import fr.plume.railexpress.entity.TrainCarEntity;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Envoyé par un joueur à bord d'un train pour régler le croisement qui arrive. */
public record RoutePayload(BlockPos pos, int route) implements CustomPacketPayload {
	public static final Type<RoutePayload> TYPE = new Type<>(RailExpress.id("crossing_route"));
	public static final StreamCodec<ByteBuf, RoutePayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, RoutePayload::pos,
			ByteBufCodecs.VAR_INT, RoutePayload::route,
			RoutePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, RoutePayload payload) {
		if (!(player.getVehicle() instanceof TrainCarEntity) || payload.route() < 0 || payload.route() >= CrossingTrackBlock.Route.values().length) {
			return;
		}
		if (player.blockPosition().distSqr(payload.pos()) > 220 * 220 || !player.level().isLoaded(payload.pos())) {
			return;
		}
		BlockState state = player.level().getBlockState(payload.pos());
		if (!(state.getBlock() instanceof CrossingTrackBlock)) {
			return;
		}
		CrossingTrackBlock.Route route = CrossingTrackBlock.Route.values()[payload.route()];
		if (state.getValue(CrossingTrackBlock.ROUTE) != route) {
			player.level().setBlock(payload.pos(), state.setValue(CrossingTrackBlock.ROUTE, route), Block.UPDATE_ALL);
			player.level().playSound(null, payload.pos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
		}
		player.displayClientMessage(Component.translatable("message.railexpress.route_" + route.getSerializedName()), true);
	}
}
