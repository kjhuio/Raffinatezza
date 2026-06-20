package io.github.kjhuio.raffinatezza.client;

import io.github.kjhuio.raffinatezza.network.HelmInputPacket;
import io.github.kjhuio.raffinatezza.network.HelmPilotStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

@EventBusSubscriber(modid = "raffinatezza", value = Dist.CLIENT)
public class HelmClientHandler {

    private static boolean lastFwd, lastBack, lastLeft, lastRight, lastUp, lastDown;
    private static UUID pilotingSubLevelId = null;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LocalPlayer player = mc.player;

        if (pilotingSubLevelId == null) {
            if (lastFwd || lastBack || lastLeft || lastRight || lastUp || lastDown) {
                lastFwd = lastBack = lastLeft = lastRight = lastUp = lastDown = false;
            }
            return;
        }

        boolean fwd   = HelmKeybinds.FORWARD.isDown();
        boolean back  = HelmKeybinds.BACKWARD.isDown();
        boolean left  = HelmKeybinds.LEFT.isDown();
        boolean right = HelmKeybinds.RIGHT.isDown();
        boolean up    = HelmKeybinds.ASCEND.isDown();
        boolean down  = HelmKeybinds.DESCEND.isDown();

        if (fwd   != lastFwd  || back  != lastBack
                || left  != lastLeft || right != lastRight
                || up    != lastUp   || down  != lastDown) {

            PacketDistributor.sendToServer(new HelmInputPacket(
                    pilotingSubLevelId, fwd, back, left, right, up, down
            ));

            lastFwd   = fwd;   lastBack  = back;
            lastLeft  = left;  lastRight = right;
            lastUp    = up;    lastDown  = down;
        }
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                HelmPilotStatePacket.TYPE,
                HelmPilotStatePacket.CODEC,
                (pkt, ctx) -> handlePilotStatePacket(pkt, ctx)
        );
    }

    private static void handlePilotStatePacket(HelmPilotStatePacket pkt, IPayloadContext ctx) {
        if (pkt.piloting()) {
            pilotingSubLevelId = pkt.subLevelId();
        } else {
            pilotingSubLevelId = null;
            lastFwd = lastBack = lastLeft = lastRight = lastUp = lastDown = false;
        }
    }
}
