package com.example.godzillamod.registry;

import com.example.godzillamod.GodzillaMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GodzillaMod.MODID);

    public static final RegistryObject<Item> GODZILLA_SPAWN_EGG =
            ITEMS.register("godzilla_ultima_spawn_egg", () ->
                    new ForgeSpawnEggItem(ModEntities.GODZILLA_ULTIMA, 0x1B1D21, 0xFF4A10, new Item.Properties()));
}
