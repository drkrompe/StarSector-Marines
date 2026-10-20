package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.List;

/**
 * How hard a mech's loaded mounts can hit one target, as expected damage per second: the
 * reading its torso target is chosen by beyond close-threat range ({@code mechs-nouns.md}, Missile warheads).
 *
 * <p>Each mount counts its round's warhead (the contact payload when it has one, otherwise the
 * area payload) at its armor efficiency against the target, times rounds per trigger over its
 * mechanical cycle, times its range-falloff accuracy at that distance. A mount that would not fire
 * counts nothing: out of range, an indirect rack inside the arms' reach (it lobs only beyond them),
 * or an anti-armor rack at a target that is not hardened. Flux and ammunition depth are left out:
 * the question is which target, not whether to fire.
 *
 * <p>Pure: no simulation and no state.
 */
public final class MechTargetPressure {

    private MechTargetPressure() {}

    /**
     * Expected damage per second from {@code loaded} at a target {@code distance} cells away whose
     * plate is rated {@code targetRating} (zero for none) and which is or is not hardened.
     */
    public static float pressure(List<MechWeaponComponent> loaded, float distance,
                                 float targetRating, boolean hardened) {
        float armsRange = 0f;
        for (MechWeaponComponent component : loaded) {
            if (component.mountFamily == MechWeaponComponent.MountFamily.ARMS) {
                armsRange = Math.max(armsRange, component.weaponDef().range);
            }
        }
        float sum = 0f;
        for (MechWeaponComponent component : loaded) {
            sum += pressure(component, distance, targetRating, hardened, armsRange);
        }
        return sum;
    }

    static float pressure(MechWeaponComponent component, float distance, float targetRating,
                          boolean hardened, float armsRange) {
        if (component.antiArmorOnly() && !hardened) return 0f;
        WeaponDef def = component.weaponDef();
        if (distance > def.range || distance < def.minRange) return 0f;
        if (def.indirectFire && distance <= armsRange) return 0f;
        float perRound = def.contactDamage > 0f
                ? def.contactDamage * efficiency(def.contactPenetration, targetRating)
                : def.damage * efficiency(def.penetration, targetRating);
        float accuracy = RangeFalloff.accuracy(def.accuracy, def.accuracyFalloff, distance, def.range);
        return perRound * component.projectilesPerTrigger / Math.max(0.1f, component.cycleSeconds()) * accuracy;
    }

    private static float efficiency(float penetration, float rating) {
        return rating > 0f ? DurabilityModel.armorEfficiency(penetration, rating) : 1f;
    }
}
