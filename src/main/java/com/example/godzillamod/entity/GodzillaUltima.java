package com.example.godzillamod.entity;

import com.example.godzillamod.GodzillaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GodzillaUltima extends Monster implements GeoEntity {
    public static final int NONE = 0, TAIL = 1, BREATH = 2, STOMP = 3, ROAR = 4;
    private static final UUID RAGE_UUID = UUID.fromString("5b1d0f1e-7a7c-4d6b-9d2e-6f4a3c9e1a11");

    private static final String P = "animation.godzilla_ultima.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");
    private static final RawAnimation TAIL_ANIM = RawAnimation.begin().thenPlay(P + "tail_swipe");
    private static final RawAnimation STOMP_ANIM = RawAnimation.begin().thenPlay(P + "stomp");
    private static final RawAnimation ROAR_ANIM = RawAnimation.begin().thenPlay(P + "roar");
    private static final RawAnimation BREATH_ANIM = RawAnimation.begin().thenPlay(P + "atomic_breath");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.godzillamod.godzilla_ultima"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    private int attackType = NONE;
    private int attackTick = 0;
    private int tailCd = 40, stompCd = 60, breathCd = 80;
    private boolean enraged = false;
    private boolean hasRoared = false;
    private Vec3 beamPoint = null;

    public GodzillaUltima(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5000;
        this.setMaxUpStep(6.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0D)      // maximo permitido por Minecraft
                .add(Attributes.ATTACK_DAMAGE, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ARMOR, 30.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D);
    }

    // ------------------------------------------------------------------ IA
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new GodzillaAttackGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public boolean isBusy() {
        return attackType != NONE;
    }

    public void chooseAttack(double dist) {
        if (attackType != NONE) return;
        if (tailCd <= 0 && dist < 24.0D) startAttack(TAIL);
        else if (stompCd <= 0 && dist < 18.0D) startAttack(STOMP);
        else if (breathCd <= 0 && dist > 10.0D && dist < 95.0D) startAttack(BREATH);
    }

    public void startAttack(int type) {
        if (level().isClientSide || attackType != NONE) return;
        attackType = type;
        attackTick = 0;
        beamPoint = null;
        switch (type) {
            case TAIL -> triggerAnim("attack", "tail");
            case STOMP -> triggerAnim("attack", "stomp");
            case BREATH -> triggerAnim("attack", "breath");
            case ROAR -> triggerAnim("attack", "roar");
            default -> { }
        }
    }

    private void endAttack() {
        switch (attackType) {
            case TAIL -> tailCd = enraged ? 70 : 120;
            case STOMP -> stompCd = enraged ? 80 : 150;
            case BREATH -> breathCd = enraged ? 160 : 260;
            default -> { }
        }
        tailCd = Math.max(tailCd, 20);
        stompCd = Math.max(stompCd, 20);
        breathCd = Math.max(breathCd, 30);
        attackType = NONE;
        attackTick = 0;
        beamPoint = null;
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientSmoke();
        } else {
            serverTick();
        }
    }

    private void serverTick() {
        if (isDeadOrDying()) return;
        if (tailCd > 0) tailCd--;
        if (stompCd > 0) stompCd--;
        if (breathCd > 0) breathCd--;
        bossBar.setProgress(getHealth() / getMaxHealth());

        if (attackType == NONE) {
            if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
                enterRage();
                startAttack(ROAR);
            } else if (!hasRoared && getTarget() != null) {
                hasRoared = true;
                startAttack(ROAR);
            }
        }
        if (attackType != NONE) tickAttack();

        if (tickCount % 3 == 0 && allowGrief()) {
            AABB bb = getBoundingBox();
            clearBox(bb.minX, Mth.ceil(getY() - 0.01D), bb.minZ, bb.maxX, bb.maxY, bb.maxZ, 400);
        }

        Vec3 mv = getDeltaMovement();
        if (onGround() && mv.horizontalDistanceSqr() > 0.0004D && tickCount % 20 == 0) {
            footstep();
        }
    }

    private void enterRage() {
        enraged = true;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(RAGE_UUID) == null) {
            speed.addPermanentModifier(new AttributeModifier(RAGE_UUID, "godzilla_rage", 0.35D,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void tickAttack() {
        attackTick++;
        ServerLevel sl = (ServerLevel) level();
        LivingEntity t = getTarget();

        switch (attackType) {
            case TAIL -> {
                if (attackTick == 6) snd(SoundEvents.ENDER_DRAGON_FLAP, 8.0F, 0.5F);
                if (attackTick == 18) tailSwipe(sl);
                if (attackTick >= 30) endAttack();
            }
            case STOMP -> {
                if (attackTick == 15) stomp(sl);
                if (attackTick >= 30) endAttack();
            }
            case ROAR -> {
                if (attackTick == 8) roar(sl);
                if (attackTick >= 60) endAttack();
            }
            case BREATH -> {
                Vec3 mouth = mouthPos();
                Vec3 tp = t != null
                        ? t.position().add(0, t.getBbHeight() * 0.5D, 0)
                        : mouth.add(Vec3.directionFromRotation(0.0F, getYRot()).scale(40.0D));
                if (attackTick < 40) {
                    if (attackTick == 2) snd(SoundEvents.ENDER_DRAGON_GROWL, 10.0F, 0.5F);
                    if (attackTick % 10 == 0) snd(SoundEvents.BEACON_ACTIVATE, 8.0F, 0.5F + attackTick * 0.015F);
                    chargeFx(sl, mouth, attackTick);
                    beamPoint = tp;
                } else if (attackTick < 92) {
                    if (beamPoint == null) beamPoint = tp;
                    Vec3 diff = tp.subtract(beamPoint);
                    double len = diff.length();
                    beamPoint = len > 1.0D ? beamPoint.add(diff.scale(1.0D / len)) : tp;
                    if (attackTick == 40) snd(SoundEvents.WARDEN_SONIC_BOOM, 12.0F, 0.5F);
                    if (attackTick % 12 == 0) snd(SoundEvents.GENERIC_EXPLODE, 8.0F, 0.4F);
                    fireBeam(sl, mouth, beamPoint);
                } else if (attackTick >= 100) {
                    endAttack();
                }
            }
            default -> endAttack();
        }
    }

    // ------------------------------------------------------------------ habilidades
    private float dmg(float mult) {
        double base = getAttributeValue(Attributes.ATTACK_DAMAGE) * (enraged ? 1.4D : 1.0D);
        return (float) (base * mult * GodzillaConfig.DAMAGE_MULTIPLIER.get());
    }

    /** Posicion aproximada de la boca (la cabeza sobresale ~16 bloques por delante del centro). */
    public Vec3 mouthPos() {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        return new Vec3(getX() + fwd.x * 16.5D, getY() + 28.0D, getZ() + fwd.z * 16.5D);
    }

    /** Coletazo giratorio de 360 grados. */
    private void tailSwipe(ServerLevel sl) {
        snd(SoundEvents.GENERIC_EXPLODE, 10.0F, 0.5F);
        AABB area = new AABB(getX() - 26, getY() - 3, getZ() - 26, getX() + 26, getY() + 16, getZ() + 26);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 26.0D) continue;
            Vec3 dir = d < 1.0E-3D ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
            e.hurt(damageSources().mobAttack(this), dmg(0.9F));
            e.setDeltaMovement(dir.x * 3.0D, 1.1D, dir.z * 3.0D);
            e.hurtMarked = true;
        }
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            for (double r : new double[]{8, 14, 20}) {
                fx(sl, ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + 1, getZ() + Math.sin(a) * r, 3, 1, 0.5, 1, 0.1);
            }
        }
        smash(sl, 18, 12, 3.0F);
    }

    /** Pisoton con onda expansiva. */
    private void stomp(ServerLevel sl) {
        snd(SoundEvents.GENERIC_EXPLODE, 12.0F, 0.4F);
        AABB area = new AABB(getX() - 16, getY() - 3, getZ() - 16, getX() + 16, getY() + 8, getZ() + 16);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            Vec3 dir = d < 1.0E-3D ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
            e.hurt(damageSources().mobAttack(this), dmg(0.7F));
            e.setDeltaMovement(dir.x * 1.5D, 1.0D, dir.z * 1.5D);
            e.hurtMarked = true;
        }
        for (int i = 0; i < 20; i++) {
            double a = i * (Math.PI * 2 / 20);
            for (double r : new double[]{4, 9, 14}) {
                fx(sl, ParticleTypes.POOF, getX() + Math.cos(a) * r, getY() + 0.5, getZ() + Math.sin(a) * r, 4, 1, 0.3, 1, 0.1);
            }
        }
        fx(sl, ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        smash(sl, 10, 8, 3.0F);
    }

    /** Rugido: empuja a todos los cercanos (sin dano). */
    private void roar(ServerLevel sl) {
        snd(SoundEvents.ENDER_DRAGON_GROWL, 16.0F, 0.4F);
        snd(SoundEvents.LIGHTNING_BOLT_THUNDER, 12.0F, 0.5F);
        AABB area = new AABB(getX() - 40, getY() - 5, getZ() - 40, getX() + 40, getY() + 40, getZ() + 40);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
            e.setDeltaMovement(e.getDeltaMovement().add(dx / d * 1.8D, 0.5D, dz / d * 1.8D));
            e.hurtMarked = true;
        }
        Vec3 m = mouthPos();
        for (int i = 0; i < 6; i++) {
            fx(sl, enraged ? ParticleTypes.FLAME : ParticleTypes.CLOUD, m.x, m.y, m.z, 20, 6, 4, 6, 0.3);
        }
    }

    private void chargeFx(ServerLevel sl, Vec3 mouth, int tick) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.35F, 0.08F), Math.min(1.5F + tick * 0.05F, 4.0F));
        fx(sl, dust, mouth.x, mouth.y, mouth.z, 6, 2.5, 2.5, 2.5, 0);
        fx(sl, ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 3, 3, 3, 3, 0.02);
        // Las puas del lomo "se cargan" con chispas.
        Vec3 back = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0D);
        for (int i = 0; i < 4; i++) {
            double a = 2.0D + random.nextDouble() * 14.0D;
            fx(sl, ParticleTypes.ELECTRIC_SPARK, getX() + back.x * a, getY() + 14 + random.nextDouble() * 14,
                    getZ() + back.z * a, 2, 1.5, 1.5, 1.5, 0.1);
        }
    }

    /** Rayo atomico: avanza por la linea boca -> objetivo, dania, prende fuego y explota donde impacta. */
    private void fireBeam(ServerLevel sl, Vec3 mouth, Vec3 point) {
        Vec3 dir = point.subtract(mouth);
        if (dir.lengthSqr() < 1.0E-4D) return;
        dir = dir.normalize();

        double maxLen = 96.0D;
        double len = maxLen;
        Vec3 impact = null;
        for (double d = 4.0D; d <= maxLen; d += 2.0D) {
            Vec3 p = mouth.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
            BlockState bs = sl.getBlockState(bp);
            if (!bs.isAir() && !bs.getCollisionShape(sl, bp).isEmpty()) {
                impact = p;
                len = d;
                break;
            }
        }

        DustParticleOptions core = new DustParticleOptions(new Vector3f(1.0F, 0.30F, 0.05F), 4.0F);
        for (double d = 2.0D; d <= len; d += 2.0D) {
            Vec3 p = mouth.add(dir.scale(d));
            fx(sl, core, p.x, p.y, p.z, 5, 0.8, 0.8, 0.8, 0);
            fx(sl, ParticleTypes.FLAME, p.x, p.y, p.z, 2, 1.0, 1.0, 1.0, 0.05);
        }

        if (attackTick % 5 == 0) {
            Set<LivingEntity> hit = new HashSet<>();
            for (double d = 4.0D; d <= len; d += 6.0D) {
                Vec3 p = mouth.add(dir.scale(d));
                hit.addAll(sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(5.0D),
                        e -> e != this && e.isAlive()));
            }
            for (LivingEntity e : hit) {
                e.hurt(damageSources().mobAttack(this), dmg(0.6F));
                e.setSecondsOnFire(8);
            }
        }

        if (impact != null) {
            fx(sl, ParticleTypes.LAVA, impact.x, impact.y, impact.z, 10, 3, 3, 3, 0.1);
            fx(sl, ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 2, impact.z, 8, 3, 3, 3, 0.05);
            if (attackTick % 4 == 0 && GodzillaConfig.BEAM_EXPLOSIONS.get()) {
                sl.explode(this, impact.x, impact.y, impact.z, 5.5F, true, Level.ExplosionInteraction.MOB);
            }
        }
    }

    private void footstep() {
        if (!(level() instanceof ServerLevel sl)) return;
        snd(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.4F);
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            fx(sl, ParticleTypes.POOF, getX() + Math.cos(a) * 5, getY() + 0.3, getZ() + Math.sin(a) * 5, 3, 1, 0.2, 1, 0.05);
        }
        AABB area = new AABB(getX() - 9, getY() - 2, getZ() - 9, getX() + 9, getY() + 4, getZ() + 9);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            e.hurt(damageSources().mobAttack(this), dmg(0.25F));
        }
    }

    // ------------------------------------------------------------------ destruccion de bloques
    private boolean allowGrief() {
        return GodzillaConfig.BREAK_BLOCKS.get() && ForgeEventFactory.getMobGriefingEvent(level(), this);
    }

    private void clearBox(double x0, int y0, double z0, double x1, double y1, double z1, int maxBlocks) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float maxH = GodzillaConfig.MAX_BLOCK_HARDNESS.get().floatValue();
        int count = 0;
        for (int x = Mth.floor(x0); x <= Mth.floor(x1); x++) {
            for (int z = Mth.floor(z0); z <= Mth.floor(z1); z++) {
                for (int y = y0; y <= Mth.floor(y1); y++) {
                    pos.set(x, y, z);
                    BlockState s = level().getBlockState(pos);
                    if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                    float h = s.getDestroySpeed(level(), pos);
                    if (h < 0.0F || h > maxH) continue;
                    level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
                    if (++count >= maxBlocks) return;
                }
            }
        }
    }

    /** Destruye bloques blandos en un circulo (usa el suelo donde esta parado). */
    private void smash(ServerLevel sl, int radius, int height, float maxHardness) {
        if (!allowGrief()) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = Mth.floor(getX()), cz = Mth.floor(getZ());
        int y0 = Mth.ceil(getY() - 0.01D);
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = 0; dy <= height; dy++) {
                    pos.set(cx + dx, y0 + dy, cz + dz);
                    BlockState s = sl.getBlockState(pos);
                    if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                    float h = s.getDestroySpeed(sl, pos);
                    if (h < 0.0F || h > maxHardness) continue;
                    sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
                    if (++count >= 3000) return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ efectos
    private void snd(SoundEvent e, float vol, float pitch) {
        level().playSound(null, getX(), getY() + 10.0D, getZ(), e, SoundSource.HOSTILE, vol, pitch);
    }

    /** Particulas visibles desde lejos (hasta 192 bloques). */
    private void fx(ServerLevel sl, ParticleOptions o, double x, double y, double z,
                    int n, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(x, y, z) < 192.0D * 192.0D) {
                sl.sendParticles(p, o, true, x, y, z, n, dx, dy, dz, speed);
            }
        }
    }

    /** Humo constante que sale del cuerpo y la espalda. */
    private void clientSmoke() {
        if (tickCount % 2 != 0) return;
        Vec3 back = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0D);
        int n = isDeadOrDying() ? 6 : 3;
        for (int i = 0; i < n; i++) {
            double along = random.nextDouble() * 14.0D - 2.0D;
            double x = getX() + back.x * along + (random.nextDouble() - 0.5D) * 8.0D;
            double z = getZ() + back.z * along + (random.nextDouble() - 0.5D) * 8.0D;
            double y = getY() + 6.0D + random.nextDouble() * 22.0D;
            level().addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0.0D, 0.04D, 0.0D);
            if (random.nextInt(3) == 0) {
                level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------ vida / jefe / muerte
    @Override
    public boolean isInvulnerableTo(DamageSource s) {
        return s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_FALL)
                || s.is(DamageTypeTags.IS_DROWNING) || s.is(DamageTypeTags.IS_FIRE)
                || s.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(s);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, Math.min(amount, 150.0F));
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        if (deathTime == 1) snd(SoundEvents.ENDER_DRAGON_DEATH, 14.0F, 0.5F);
        if (deathTime % 8 == 0) {
            fx(sl, ParticleTypes.EXPLOSION_EMITTER,
                    getX() + (random.nextDouble() - 0.5D) * 14, getY() + random.nextDouble() * 20,
                    getZ() + (random.nextDouble() - 0.5D) * 14, 1, 0, 0, 0, 0);
            snd(SoundEvents.GENERIC_EXPLODE, 8.0F, 0.6F);
        }
        fx(sl, ParticleTypes.LARGE_SMOKE, getX(), getY() + 10, getZ(), 6, 6, 8, 6, 0.05);
        if (deathTime >= 110 && !isRemoved()) {
            fx(sl, ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 6, getZ(), 6, 8, 6, 8, 0);
            snd(SoundEvents.GENERIC_EXPLODE, 16.0F, 0.4F);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bossBar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bossBar.removePlayer(p);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged);
        tag.putBoolean("HasRoared", hasRoared);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("Enraged");
        hasRoared = tag.getBoolean("HasRoared");
    }

    // ------------------------------------------------------------------ varios
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 28.0F;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(30.0D, 4.0D, 30.0D);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override protected float getSoundVolume() { return 5.0F; }
    @Override public float getVoicePitch() { return 0.5F; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENDER_DRAGON_GROWL; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENDER_DRAGON_HURT; }
    @Override protected SoundEvent getDeathSound() { return null; }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 8, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "attack", 3, state -> PlayState.STOP)
                .triggerableAnim("tail", TAIL_ANIM)
                .triggerableAnim("stomp", STOMP_ANIM)
                .triggerableAnim("breath", BREATH_ANIM)
                .triggerableAnim("roar", ROAR_ANIM));
    }

    private PlayState movementPredicate(AnimationState<GodzillaUltima> state) {
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
