package io.github.kjhuio.raffinatezza.network;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;
import io.github.kjhuio.raffinatezza.entity.HelmSeatEntity;

import java.util.UUID;

public record HelmInputPacket(
        UUID subLevelId,
        boolean forward,  boolean backward,
        boolean left,     boolean right,
        boolean up,       boolean down
) implements CustomPacketPayload {

    public static final Type<HelmInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("raffinatezza", "helm_input"));

    public static final StreamCodec<FriendlyByteBuf, HelmInputPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.subLevelId());
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
                        UUID subLevelId = buf.readUUID();
                        int flags    = buf.readByte();
                        return new HelmInputPacket(
                                subLevelId,
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

    private static Vector3d helmForwardVector(ServerSubLevel subLevel) {
        net.minecraft.core.BlockPos helmLocalPos = subLevel.getPlot().getCenterBlock();
        net.minecraft.world.level.block.state.BlockState helmState = subLevel.getLevel().getBlockState(helmLocalPos);

        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        if (helmState.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            facing = helmState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        }

        Vector3d helmLocalForward = switch (facing) {
            case NORTH -> new Vector3d(0, 0, -1);
            case SOUTH -> new Vector3d(0, 0, 1);
            case WEST -> new Vector3d(-1, 0, 0);
            case EAST -> new Vector3d(1, 0, 0);
            default -> new Vector3d(0, 0, -1);
        };
        return subLevel.logicalPose().transformNormal(helmLocalForward);
    }

    public static void handle(HelmInputPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ServerLevel serverLevel = player.serverLevel();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;

            ServerSubLevel subLevel = (ServerSubLevel) container.getSubLevel(pkt.subLevelId());
            if (subLevel == null || subLevel.isRemoved()) return;

            // Find the HelmSeatEntity associated with this sub-level and update its input state.
            HelmSeatEntity seat = null;
            for (var entity : serverLevel.getAllEntities()) {
                if (entity instanceof HelmSeatEntity helmSeat) {
                    if (helmSeat.getSubLevelId() != null && helmSeat.getSubLevelId().equals(subLevel.getUniqueId())) {
                        seat = helmSeat;
                        break;
                    }
                }
            }
            if (seat != null) {
                seat.inputForward = pkt.forward();
                seat.inputBackward = pkt.backward();
                seat.inputLeft = pkt.left();
                seat.inputRight = pkt.right();
                seat.inputUp = pkt.up();
                seat.inputDown = pkt.down();
            }
        });
    }
}
