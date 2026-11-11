package com.example.godzillamod;

import net.minecraftforge.common.ForgeConfigSpec;

public class GodzillaConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue BEAM_EXPLOSIONS;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_BLOCK_HARDNESS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("godzilla");
        BREAK_BLOCKS = b.comment("Godzilla destroys blocks while walking and with stomp/tail attacks (also needs mobGriefing = true)")
                .define("breakBlocks", true);
        BEAM_EXPLOSIONS = b.comment("The atomic breath creates explosions where it hits")
                .define("beamExplosions", true);
        DAMAGE_MULTIPLIER = b.comment("Multiplier for all Godzilla damage")
                .defineInRange("damageMultiplier", 1.0, 0.0, 100.0);
        MAX_BLOCK_HARDNESS = b.comment("Blocks harder than this survive (obsidian = 50, bedrock is never broken)")
                .defineInRange("maxBlockHardness", 50.0, 0.0, 10000.0);
        b.pop();
        SPEC = b.build();
    }
}
