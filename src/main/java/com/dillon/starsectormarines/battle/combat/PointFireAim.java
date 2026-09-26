package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/** A world bearing for a primary trigger, with no target identity or automatic lead. */
public record PointFireAim(float x, float y) {
    /** Invalid input is consumed as a held shot at the serial firing boundary. */
    public boolean validFrom(float fromX, float fromY) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return false;
        float dx = x - fromX;
        float dy = y - fromY;
        float distance = (float) Math.hypot(dx, dy);
        return Float.isFinite(distance) && distance > 1e-6f;
    }

    /** Other carriers and area/indirect weapons require their own firing adapters. */
    public static boolean supports(WeaponDef weapon) {
        return weapon != null && weapon.mount == MountClass.MARINE_PRIMARY
                && !weapon.indirectFire && !weapon.interceptableProjectile
                && weapon.aoeRadius == 0f && weapon.arcHeight == 0f
                && weapon.aimDuration == 0f && weapon.minRange == 0f;
    }
}
