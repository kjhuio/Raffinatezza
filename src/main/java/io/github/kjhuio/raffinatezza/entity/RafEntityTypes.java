package io.github.kjhuio.raffinatezza.entity;

import io.github.kjhuio.raffinatezza.Raffinatezza;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RafEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Raffinatezza.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<HelmSeatEntity>> HELM_SEAT =
            ENTITY_TYPES.register("helm_seat", () ->
                    EntityType.Builder.<HelmSeatEntity>of(HelmSeatEntity::new, MobCategory.MISC)
                            .sized(0.75f, 0.35f)
                            .passengerAttachments(new Vec3(0.0, 0.0, 0.0))
                            .clientTrackingRange(64)
                            .build("helm_seat")
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
