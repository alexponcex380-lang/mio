package com.example.godzillamod.client;

import com.example.godzillamod.entity.GodzillaUltima;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GodzillaRenderer extends GeoEntityRenderer<GodzillaUltima> {
    public GodzillaRenderer(EntityRendererProvider.Context context) {
        super(context, new GodzillaModel());
        this.shadowRadius = 8.0F;
    }

    // La caida al morir la hace la animacion "death", no la rotacion de vanilla.
    @Override
    protected float getDeathMaxRotation(GodzillaUltima animatable) {
        return 0.0F;
    }
}
