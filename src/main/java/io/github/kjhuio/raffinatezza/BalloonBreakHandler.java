package io.github.kjhuio.raffinatezza;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "raffinatezza")
public class BalloonBreakHandler {
    private static final TagKey<Block> BALLOONS_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("raffinatezza", "balloons")
    );

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().getMainHandItem().is(Items.SHEARS)) {
            if (event.getState().is(BALLOONS_TAG)) {
                event.setNewSpeed(15.0f);
            }
        }
    }
}