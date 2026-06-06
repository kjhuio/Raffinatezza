package io.github.kjhuio.raffinatezza.item;

import io.github.kjhuio.raffinatezza.Raffinatezza;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Items {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Raffinatezza.MODID);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
