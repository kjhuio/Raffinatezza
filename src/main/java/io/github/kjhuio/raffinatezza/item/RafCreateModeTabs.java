package io.github.kjhuio.raffinatezza.item;

import io.github.kjhuio.raffinatezza.Raffinatezza;
import io.github.kjhuio.raffinatezza.block.RafBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RafCreateModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,Raffinatezza.MODID);

    public static final Supplier<CreativeModeTab> RAFFINATEZZA_TAB = CREATIVE_MODE_TAB.register("raffinatezza",() -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(RafBlocks.WHITE_BALLOON.get()))
            .title(Component.translatable("creativetab.raffinatezza.raffinatezza_tab"))
            .displayItems(((itemDisplayParameters, output) -> {
                output.accept(RafBlocks.BLACK_BALLOON);
                output.accept(RafBlocks.BLUE_BALLOON);
                output.accept(RafBlocks.BROWN_BALLOON);
                output.accept(RafBlocks.CYAN_BALLOON);
                output.accept(RafBlocks.GRAY_BALLOON);
                output.accept(RafBlocks.GREEN_BALLOON);
                output.accept(RafBlocks.LIGHT_BLUE_BALLOON);
                output.accept(RafBlocks.LIGHT_GRAY_BALLOON);
                output.accept(RafBlocks.LIME_BALLOON);
                output.accept(RafBlocks.MAGENTA_BALLOON);
                output.accept(RafBlocks.ORANGE_BALLOON);
                output.accept(RafBlocks.PINK_BALLOON);
                output.accept(RafBlocks.PURPLE_BALLOON);
                output.accept(RafBlocks.RED_BALLOON);
                output.accept(RafBlocks.WHITE_BALLOON);
                output.accept(RafBlocks.YELLOW_BALLOON);
                output.accept(RafBlocks.HELM);
            }))
            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
