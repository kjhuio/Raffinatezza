package io.github.kjhuio.raffinatezza.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "raffinatezza", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HelmKeybinds {

    public static final String CATEGORY = "key.categories.raffinatezza.helm";

    public static KeyMapping FORWARD, BACKWARD, LEFT, RIGHT, ASCEND, DESCEND;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        FORWARD  = reg(event, "helm_forward",  GLFW.GLFW_KEY_W);
        BACKWARD = reg(event, "helm_backward", GLFW.GLFW_KEY_S);
        LEFT     = reg(event, "helm_left",     GLFW.GLFW_KEY_A);
        RIGHT    = reg(event, "helm_right",    GLFW.GLFW_KEY_D);
        ASCEND   = reg(event, "helm_ascend",   GLFW.GLFW_KEY_SPACE);
        DESCEND  = reg(event, "helm_descend",  GLFW.GLFW_KEY_V);
    }

    private static KeyMapping reg(RegisterKeyMappingsEvent event, String name, int defaultKey) {
        var km = new KeyMapping("key.raffinatezza." + name, defaultKey, CATEGORY);
        event.register(km);
        return km;
    }
}
