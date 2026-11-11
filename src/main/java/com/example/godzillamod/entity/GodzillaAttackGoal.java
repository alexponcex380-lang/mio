package com.example.godzillamod.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Persigue al objetivo hasta ~22 bloques y elige el ataque (coletazo, pisoton, rayo atomico). */
public class GodzillaAttackGoal extends Goal {
    private final GodzillaUltima mob;

    public GodzillaAttackGoal(GodzillaUltima mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;

        mob.getLookControl().setLookAt(t, 20.0F, 20.0F);

        double dx = t.getX() - mob.getX();
        double dz = t.getZ() - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Gira el cuerpo hacia el objetivo (3 grados por tick: es enorme y pesado).
        float want = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float yaw = Mth.approachDegrees(mob.getYRot(), want, mob.isBusy() ? 2.0F : 3.5F);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;

        if (mob.isBusy() || dist <= 22.0D) {
            // Detiene el movimiento: apunta a su propia posicion.
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        } else {
            mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0D);
        }

        mob.chooseAttack(dist);
    }

    @Override
    public void stop() {
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
    }
}
