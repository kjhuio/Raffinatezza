package io.github.kjhuio.raffinatezza.client;

import io.github.kjhuio.raffinatezza.entity.HelmSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HelmSeatRenderer extends EntityRenderer<HelmSeatEntity> {

    public HelmSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HelmSeatEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/block/air.png");
    }
}
