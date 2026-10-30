package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of a mech's mounts the duel band reads ({@link MechDuelRange#ownWeapons}): a rack between salvos is still in
 * the fight, a magazine nothing refills is not.
 */
class MechDuelWeaponsTest {

    @Test
    void aRackBetweenSalvosKeepsItsReach() {
        MechLoadoutComponent warden = emptiedRacks(MissileReplenisherComponent.STANDARD);

        assertEquals(reach(warden, true), MechDuelBand.reach(MechDuelRange.ownWeapons(warden)),
                "a rack its loader is recharging is between salvos, not out");
    }

    @Test
    void aRackNothingRefillsLeavesTheReading() {
        MechLoadoutComponent warden = emptiedRacks(MissileReplenisherComponent.NONE);

        assertEquals(reach(warden, false), MechDuelBand.reach(MechDuelRange.ownWeapons(warden)),
                "with no loader an empty rack is spent, and the gun is all it has");
    }

    /** A stock Warden whose racks have each fired their salvo, one tick of {@code loader} after. */
    private static MechLoadoutComponent emptiedRacks(MissileReplenisherComponent loader) {
        MechLoadoutComponent warden = MechVariant.WARDEN.createLoadout(MechRole.LR_SUPPORT);
        assertTrue(reach(warden, true) > reach(warden, false), "the Warden's racks out-reach its gun");
        for (MechWeaponMount mount : warden.mounts()) {
            if (mount == null || mount.component.ammoCapacity < 0) continue;
            mount.commitTrigger(0L, null);
            mount.clearBurst();
            mount.advanceReplenishment(BattleSimulation.TICK_DT, loader);
            assertEquals(0, mount.ammo, mount.weaponId());
        }
        return warden;
    }

    /** The longest range among the loadout's mounts, its racks included or not. */
    private static float reach(MechLoadoutComponent loadout, boolean racks) {
        float reach = 0f;
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null || !racks && mount.component.ammoCapacity >= 0) continue;
            reach = Math.max(reach, mount.weaponDef().range);
        }
        return reach;
    }
}
