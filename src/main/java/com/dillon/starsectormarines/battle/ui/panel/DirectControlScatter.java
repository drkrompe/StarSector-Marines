package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

/**
 * The cursor ring a pilot reads before throwing rockets or an LRM salvo: the widest landing disk among the armed
 * mounts whose rounds burst where they are thrown, and whether the point lies past any of their rated ranges.
 * Guns have no ring; their rounds fly a lane, which {@link DirectControlAimLane} already draws.
 */
public final class DirectControlScatter {
    private DirectControlScatter() {}

    /** {@code beyondRange} is true when the point lies past the nominal range of a mount the ring covers. */
    public record Ring(float radiusCells, boolean beyondRange) {}

    /** The ring for a cursor {@code distance} cells from the chassis centre, or null when no armed mount bursts. */
    public static Ring mech(MechLoadoutComponent loadout, int selected, float distance) {
        MechWeaponMount[] mounts = loadout.mounts();
        int armed = MechFireGroups.leftMask(mounts, selected) | MechFireGroups.rightMask(mounts, selected);
        float radius = -1f;
        boolean beyond = false;
        for (MechWeaponMount mount : mounts) {
            if (mount == null || !MechFireGroups.contains(armed, mount)) continue;
            float r = HeavyWeapons.manualScatterRadiusCells(loadout, mount, distance);
            if (r < 0f) continue;
            radius = Math.max(radius, r);
            beyond |= distance > mount.weaponDef().range;
        }
        return radius < 0f ? null : new Ring(radius, beyond);
    }
}
