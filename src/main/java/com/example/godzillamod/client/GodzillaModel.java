package com.example.godzillamod.client;

import com.example.godzillamod.GodzillaMod;
import com.example.godzillamod.entity.GodzillaUltima;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class GodzillaModel extends GeoModel<GodzillaUltima> {
    @Override
    public ResourceLocation getModelResource(GodzillaUltima animatable) {
        return new ResourceLocation(GodzillaMod.MODID, "geo/godzilla_ultima.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GodzillaUltima animatable) {
        return new ResourceLocation(GodzillaMod.MODID, "textures/entity/godzilla_ultima.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GodzillaUltima animatable) {
        return new ResourceLocation(GodzillaMod.MODID, "animations/godzilla_ultima.animation.json");
    }

    // La cabeza sigue la mirada (se suma a lo que ya anima la animacion).
    @Override
    public void setCustomAnimations(GodzillaUltima animatable, long instanceId, AnimationState<GodzillaUltima> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        if (animatable.isDeadOrDying()) return;
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head != null) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            float pitch = Mth.clamp(data.headPitch(), -25.0F, 25.0F);
            float yaw = Mth.clamp(data.netHeadYaw(), -40.0F, 40.0F);
            head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD * 0.6F);
            head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD * 0.6F);
        }
    }
}
