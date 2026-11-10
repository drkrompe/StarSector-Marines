package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechBodyHardpoints;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictWalkerGunsTest {

    /** Every weapon a derelict mount could hold leaves the front of its turret head: barrel tip and flash show. */
    @Test
    void everyWeaponsMuzzleClearsItsHead() {
        for (MechWeaponComponent weapon : MechWeaponComponent.values()) {
            boolean arm = weapon.mountFamily == MechWeaponComponent.MountFamily.ARMS;
            MechMountSlot slot = arm ? MechMountSlot.LEFT_ARM : MechMountSlot.LEFT_SHOULDER;
            float length = MechHardpointGeometry.barrelHullWidths(slot, weapon.appearanceSelector(slot));
            if (length <= 0f) continue;
            float muzzle = (1f - MechBodyHardpoints.SEAT) * length;
            float front = DerelictWalkerGuns.headFront(!arm, length);
            assertTrue(muzzle > front, weapon + ": muzzle " + muzzle + " inside head front " + front);
        }
    }
}
