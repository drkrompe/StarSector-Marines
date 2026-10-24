package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A chassis' hardpoints as trigger mounts; the sorting itself is {@link FireGroupsTest}'s. */
class MechFireGroupsTest {
    private static MechWeaponMount[] of(MechVariant v) {
        MechWeaponMount[] out = new MechWeaponMount[MechMountSlot.values().length];
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent component = v.stockWeapon(slot);
            if (component != null) out[slot.ordinal()] = new MechWeaponMount(slot, component);
        }
        return out;
    }

    private static int bit(MechMountSlot slot) {
        return 1 << slot.ordinal();
    }

    @Test
    void missileHardpointsAreOrdnanceIndexedByOrdinal() {
        MechWeaponMount[] warden = of(MechVariant.WARDEN);
        assertEquals(bit(MechMountSlot.ARMS), MechFireGroups.primaryMask(warden));
        assertEquals(bit(MechMountSlot.LEFT_SHOULDER) | bit(MechMountSlot.RIGHT_SHOULDER),
                MechFireGroups.secondaryMask(warden), "arcing LRMs drop on the cursor point from the right button");
        assertEquals("R", MechFireGroups.badge(warden, TriggerPicks.LINKED, warden[MechMountSlot.LEFT_SHOULDER.ordinal()]));
    }

    @Test
    void aDestroyedHardpointIsNeverAMemberButAnEmptyRackStays() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        bulwark[MechMountSlot.LEFT_ARM.ordinal()] = null;
        bulwark[MechMountSlot.LEFT_SHOULDER.ordinal()].ammo = 0;
        assertArrayEquals(new int[]{MechFireGroups.LINKED}, MechFireGroups.members(bulwark, FireTrigger.LEFT));
        assertTrue(MechFireGroups.isMember(bulwark, FireTrigger.RIGHT, MechMountSlot.LEFT_SHOULDER.ordinal() + 1));
    }
}
