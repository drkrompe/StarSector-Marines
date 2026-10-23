package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * The cursor ring a pilot reads before throwing rockets or an LRM salvo: the widest landing disk among the armed
 * mounts whose rounds burst where they are thrown, and whether the point lies past any of their rated ranges.
 * Guns have no ring; their rounds fly a lane, which {@link DirectControlAimLane} already draws.
 */
public final class DirectControlScatter {
    private DirectControlScatter() {}

    /**
     * {@code beyondRange} is true when the point lies past the nominal range of a mount the ring covers, and
     * {@code tooClose} when it lies inside the minimum of one (an arcing rack refuses such a point).
     */
    public record Ring(float radiusCells, boolean beyondRange, boolean tooClose) {
        /** Either condition draws the ring as a warning. */
        public boolean warning() { return beyondRange || tooClose; }
    }

    /** The ring for a cursor {@code distance} cells from the chassis centre, or null when no armed mount bursts. */
    public static Ring mech(MechLoadoutComponent loadout, int selected, float distance) {
        MechWeaponMount[] mounts = loadout.mounts();
        int armed = MechFireGroups.leftMask(mounts, selected) | MechFireGroups.rightMask(mounts, selected);
        float radius = -1f;
        boolean beyond = false;
        boolean close = false;
        for (MechWeaponMount mount : mounts) {
            if (mount == null || !MechFireGroups.contains(armed, mount)) continue;
            float r = HeavyWeapons.manualScatterRadiusCells(loadout, mount, distance);
            if (r < 0f) continue;
            radius = Math.max(radius, r);
            beyond |= distance > mount.weaponDef().range;
            close |= HeavyWeapons.tooClose(mount, distance);
        }
        return radius < 0f ? null : new Ring(radius, beyond, close);
    }

    /**
     * The ring for a vehicle turret's weapon with the cursor {@code distance} cells from its turret ring, or null
     * for a gun. The same rules as a mech rack's: the LRM carrier's launcher reads TOO CLOSE inside its minimum.
     */
    public static Ring vehicle(WeaponDef weapon, float distance) {
        float radius = ManualPointFire.scatterRadiusCells(weapon, weapon.accuracy, distance, 0f);
        return radius < 0f ? null
                : new Ring(radius, distance > weapon.range, ManualPointFire.tooClose(weapon, distance));
    }
}
