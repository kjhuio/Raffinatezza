package io.github.kjhuio.raffinatezza.block;

import io.github.kjhuio.raffinatezza.Raffinatezza;
import io.github.kjhuio.raffinatezza.item.RafItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RafBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Raffinatezza.MODID);

    public static final DeferredBlock<Block> BLACK_BALLOON = registerBlock("black_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> BLUE_BALLOON = registerBlock("blue_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> BROWN_BALLOON = registerBlock("brown_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> CYAN_BALLOON = registerBlock("cyan_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> GRAY_BALLOON = registerBlock("gray_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> GREEN_BALLOON = registerBlock("green_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> LIGHT_BLUE_BALLOON = registerBlock("light_blue_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> LIGHT_GRAY_BALLOON = registerBlock("light_gray_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> LIME_BALLOON = registerBlock("lime_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> MAGENTA_BALLOON = registerBlock("magenta_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> ORANGE_BALLOON = registerBlock("orange_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> PINK_BALLOON = registerBlock("pink_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> PURPLE_BALLOON = registerBlock("purple_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> RED_BALLOON = registerBlock("red_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> WHITE_BALLOON = registerBlock("white_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> YELLOW_BALLOON = registerBlock("yellow_balloon",() -> new Block(BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL)));

    public static final DeferredBlock<Block> HELM = registerBlock("helm", () -> new HelmBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name,Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name,block);
        registerBlockItem(name,toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name,DeferredBlock<T> block) {
        RafItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
