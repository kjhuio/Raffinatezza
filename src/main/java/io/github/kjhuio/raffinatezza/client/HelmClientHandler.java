package io.github.kjhuio.raffinatezza.client;

import io.github.kjhuio.raffinatezza.entity.HelmSeatEntity;
import io.github.kjhuio.raffinatezza.network.HelmInputPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "raffinatezza", value = Dist.CLIENT)
public class HelmClientHandler {

    private static boolean lastFwd, lastBack, lastLeft, lastRight, lastUp, lastDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // トロッコと同じ判定：getVehicle() で乗車確認
        if (!(mc.player.getVehicle() instanceof HelmSeatEntity seat)) {
            // 降車時に入力をリセット
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

        // 変化があった時だけ送信
        if (fwd   != lastFwd  || back  != lastBack
                || left  != lastLeft || right != lastRight
                || up    != lastUp   || down  != lastDown) {

            PacketDistributor.sendToServer(new HelmInputPacket(
                    seat.getId(), fwd, back, left, right, up, down
            ));

            lastFwd   = fwd;   lastBack  = back;
            lastLeft  = left;  lastRight = right;
            lastUp    = up;    lastDown  = down;
        }
    }
}
