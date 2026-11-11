package com.example.godzillamod.registry;

import com.example.godzillamod.GodzillaMod;
import com.example.godzillamod.entity.GodzillaUltima;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GodzillaMod.MODID);

    public static final RegistryObject<EntityType<GodzillaUltima>> GODZILLA_ULTIMA =
            ENTITIES.register("godzilla_ultima", () ->
                    EntityType.Builder.of(GodzillaUltima::new, MobCategory.MONSTER)
                            .sized(14.0F, 32.0F)
                            .fireImmune()
                            .clientTrackingRange(32)
                            .updateInterval(3)
                            .build("godzilla_ultima"));
}
