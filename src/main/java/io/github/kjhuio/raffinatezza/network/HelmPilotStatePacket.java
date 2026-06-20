package io.github.kjhuio.raffinatezza.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record HelmPilotStatePacket(
        UUID subLevelId,
        boolean piloting
) implements CustomPacketPayload {

    public static final Type<HelmPilotStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("raffinatezza", "helm_pilot_state"));

    public static final StreamCodec<FriendlyByteBuf, HelmPilotStatePacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.subLevelId());
                        buf.writeBoolean(pkt.piloting());
                    },
                    buf -> new HelmPilotStatePacket(buf.readUUID(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmPilotStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
        });
    }

    public static void sendToClient(ServerPlayer player, UUID subLevelId, boolean piloting) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new HelmPilotStatePacket(subLevelId, piloting));
    }
}
