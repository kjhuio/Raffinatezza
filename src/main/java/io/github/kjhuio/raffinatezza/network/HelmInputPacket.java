package io.github.kjhuio.raffinatezza.network;

import io.github.kjhuio.raffinatezza.entity.HelmSeatEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmInputPacket(
        int entityId,
        boolean forward,  boolean backward,
        boolean left,     boolean right,
        boolean up,       boolean down
) implements CustomPacketPayload {

    public static final Type<HelmInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("raffinatezza", "helm_input"));

    public static final StreamCodec<FriendlyByteBuf, HelmInputPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeInt(pkt.entityId());
                        buf.writeByte(
                                (pkt.forward()   ? 1  : 0) |
                                        (pkt.backward()  ? 2  : 0) |
                                        (pkt.left()      ? 4  : 0) |
                                        (pkt.right()     ? 8  : 0) |
                                        (pkt.up()        ? 16 : 0) |
                                        (pkt.down()      ? 32 : 0)
                        );
                    },
                    buf -> {
                        int entityId = buf.readInt();
                        int flags    = buf.readByte();
                        return new HelmInputPacket(
                                entityId,
                                (flags & 1)  != 0,
                                (flags & 2)  != 0,
                                (flags & 4)  != 0,
                                (flags & 8)  != 0,
                                (flags & 16) != 0,
                                (flags & 32) != 0
                        );
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmInputPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (player.level().getEntity(pkt.entityId()) instanceof HelmSeatEntity seat
                    && seat.getFirstPassenger() == player) {
                seat.inputForward  = pkt.forward();
                seat.inputBackward = pkt.backward();
                seat.inputLeft     = pkt.left();
                seat.inputRight    = pkt.right();
                seat.inputUp       = pkt.up();
                seat.inputDown     = pkt.down();
            }
        });
    }
}
