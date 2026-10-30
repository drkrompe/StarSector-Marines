package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** One weapon, drawn by the mount it sits in: the nose gun on the centreline, the arm gun anywhere else. */
class MechWeaponPresentationTest {

    @Test
    void aChaingunIsTheNoseGunOnTheCentrelineAndTheArmGunOnASideArmOrATurret() {
        for (MechWeaponComponent gun : new MechWeaponComponent[]{MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.CHAINGUN_HF}) {
            assertEquals(LayeredMechAppearance.ARMS_NOSE_CHAINGUN, gun.appearanceSelector(MechMountSlot.ARMS));
            assertEquals(LayeredMechAppearance.ARMS_CHAINGUN, gun.appearanceSelector(MechMountSlot.LEFT_ARM));
            assertEquals(LayeredMechAppearance.ARMS_CHAINGUN, gun.appearanceSelector(MechMountSlot.RIGHT_ARM));
            assertEquals(LayeredMechAppearance.ARMS_CHAINGUN, gun.appearanceSelector(null), "a turret or stores");
            assertTrue(gun.noseDrawingOn(MechMountSlot.ARMS));
            assertFalse(gun.noseDrawingOn(null));
        }
        assertEquals(LayeredMechAppearance.ARMS_NOSE_MUSTER_AUTOGUN,
                MechWeaponComponent.MUSTER_AUTOGUN.appearanceSelector(MechMountSlot.ARMS));
        assertEquals(LayeredMechAppearance.ARMS_MUSTER_AUTOGUN,
                MechWeaponComponent.MUSTER_AUTOGUN.appearanceSelector(MechMountSlot.LEFT_ARM));
    }

    @Test
    void aWeaponWithoutANoseDrawingLooksTheSameOnEveryMount() {
        for (MechMountSlot slot : new MechMountSlot[]{MechMountSlot.ARMS, MechMountSlot.LEFT_ARM, null}) {
            assertEquals(MechWeaponComponent.LIGHT_MACHINE_GUN.appearanceSelector,
                    MechWeaponComponent.LIGHT_MACHINE_GUN.appearanceSelector(slot));
            assertFalse(MechWeaponComponent.BASTION_AUTOCANNON.noseDrawingOn(slot));
        }
        assertEquals(MechWeaponComponent.SRM_5.appearanceSelector,
                MechWeaponComponent.SRM_5.appearanceSelector(MechMountSlot.LEFT_SHOULDER));
    }

    @Test
    void theOneChaingunFitsTheCentrelineAndEitherArm() {
        for (MechWeaponComponent gun : new MechWeaponComponent[]{MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.CHAINGUN_HF, MechWeaponComponent.MUSTER_AUTOGUN}) {
            assertTrue(gun.accepts(MechMountSlot.ARMS), gun + " on the centreline");
            assertTrue(gun.accepts(MechMountSlot.LEFT_ARM), gun + " on an arm");
            assertTrue(gun.accepts(MechMountSlot.RIGHT_ARM));
            assertFalse(gun.accepts(MechMountSlot.LEFT_SHOULDER));
        }
    }

    @Test
    void theMuzzleFollowsTheDrawingTheMountResolves() {
        MechWeaponComponent gun = MechWeaponComponent.CHAINGUN;
        var nose = MechHardpointGeometry.armsMuzzle(gun.appearanceSelector(MechMountSlot.ARMS), MechMountSlot.ARMS);
        var arm = MechHardpointGeometry.armsMuzzle(gun.appearanceSelector(MechMountSlot.LEFT_ARM), MechMountSlot.LEFT_ARM);
        assertEquals(0f, nose.xHullWidths(), "on the centreline");
        assertNotEquals(0f, arm.xHullWidths(), "out on the arm");
        assertTrue(nose.yHullWidths() > arm.yHullWidths(), "the nose barrel starts further forward");
    }
}
