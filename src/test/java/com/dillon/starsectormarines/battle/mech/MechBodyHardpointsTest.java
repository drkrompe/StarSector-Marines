package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechBodyHardpointsTest {

    private static final MechVariant[] WALKERS = {MechVariant.SECURITY_ROUNDS, MechVariant.SECURITY_WALKER,
            MechVariant.SECURITY_HALL};
    private static final float EPS = 1e-4f;
    /** Half the torso drawing's width, hull widths: 0.85 cells per render scale against a hull width of 1.148. */
    private static final float TORSO_HALF = 0.85f / (0.82f * 1.40f) / 2f;

    /** A test hardpoint: a port right of centre, its arc centred 30 degrees to the right, 60 either side. */
    private static final MechBodyHardpoints.Hardpoint PORT = new MechBodyHardpoints.Hardpoint(MechMountSlot.RIGHT_ARM,
            MechFittingLayout.SocketType.BALLISTIC, 2, 0.2f, 0.1f, -30f, 60f, 0.5f);

    @Test
    void everyWalkerMountIsAHardpointThatTakesItsStockFit() {
        for (MechVariant variant : WALKERS) {
            assertTrue(MechBodyHardpoints.has(variant), variant.id);
            for (MechMountSlot slot : MechMountSlot.values()) {
                MechBodyHardpoints.Hardpoint point = MechBodyHardpoints.of(variant, slot);
                if (!variant.hasMount(slot)) {
                    assertNull(point, variant.id + " " + slot);
                    continue;
                }
                MechWeaponComponent stock = variant.stockWeapon(slot);
                if (stock == null) continue;
                assertNotNull(point, variant.id + " " + slot);
                assertTrue(point.takes(stock), variant.id + " " + slot + " takes " + stock);
            }
        }
    }

    @Test
    void everyWalkerFrameFitsItsHardpoints() {
        for (DerelictWalkerFrame frame : DerelictWalkerFrame.values()) {
            MechLoadoutComponent loadout = frame.createLoadout();
            for (MechWeaponMount mount : loadout.mounts()) {
                if (mount == null) continue;
                MechBodyHardpoints.Hardpoint point = MechBodyHardpoints.of(frame.chassis, mount.slot);
                assertNotNull(point, frame + " " + mount.slot);
                assertTrue(point.takes(mount.component), frame + " " + mount.slot + " takes " + mount.component);
            }
        }
    }

    @Test
    void companyChassisCarryNoBodyHardpoints() {
        for (MechVariant variant : MechVariant.companyLines()) assertFalse(MechBodyHardpoints.has(variant));
    }

    @Test
    void fourGunWalkerMountsStandMirroredOnTheTorso() {
        for (MechVariant variant : new MechVariant[] {MechVariant.SECURITY_WALKER, MechVariant.SECURITY_HALL}) {
            for (MechMountSlot[] pair : new MechMountSlot[][] {{MechMountSlot.LEFT_ARM, MechMountSlot.RIGHT_ARM},
                    {MechMountSlot.LEFT_SHOULDER, MechMountSlot.RIGHT_SHOULDER}}) {
                MechBodyHardpoints.Hardpoint left = MechBodyHardpoints.of(variant, pair[0]);
                MechBodyHardpoints.Hardpoint right = MechBodyHardpoints.of(variant, pair[1]);
                assertTrue(left.right() < 0f && right.right() > 0f, variant.id + " left is left");
                assertEquals(-left.right(), right.right(), EPS);
                assertEquals(left.forward(), right.forward(), EPS);
                assertEquals(-left.arcCentre(), right.arcCentre(), EPS);
                assertTrue(Math.abs(left.right()) < TORSO_HALF && Math.abs(left.forward()) < TORSO_HALF,
                        variant.id + " " + pair[0] + " on the torso");
            }
        }
    }

    @Test
    void roundsWalkerPairIsSymmetricOnTheTorsoAndBearsBothGunsAhead() {
        MechBodyHardpoints.Hardpoint gun = MechBodyHardpoints.of(MechVariant.SECURITY_ROUNDS, MechMountSlot.LEFT_ARM);
        MechBodyHardpoints.Hardpoint rack = MechBodyHardpoints.of(MechVariant.SECURITY_ROUNDS,
                MechMountSlot.LEFT_SHOULDER);
        assertNull(MechBodyHardpoints.of(MechVariant.SECURITY_ROUNDS, MechMountSlot.RIGHT_ARM));
        assertNull(MechBodyHardpoints.of(MechVariant.SECURITY_ROUNDS, MechMountSlot.RIGHT_SHOULDER));
        // One each side, mirrored, both on the torso.
        assertTrue(gun.right() < 0f);
        assertEquals(-gun.right(), rack.right(), EPS);
        assertEquals(gun.forward(), rack.forward(), EPS);
        assertEquals(-gun.arcCentre(), rack.arcCentre(), EPS);
        assertTrue(Math.abs(gun.right()) < TORSO_HALF && Math.abs(gun.forward()) < TORSO_HALF);
        // Both bear on one target ahead and a good way to either side.
        for (float bearing : new float[] {-90f, -45f, 0f, 45f, 90f}) {
            assertTrue(gun.covers(0f, bearing) && rack.covers(0f, bearing), "at " + bearing);
        }
    }

    @Test
    void pivotTurnsWithTheBody() {
        // Heading up (0): right is +x. Heading left (90): right is +y.
        float[] up = MechBodyHardpoints.pivot(10f, 20f, 0f, 2f, PORT);
        assertEquals(10.4f, up[0], EPS);
        assertEquals(20.2f, up[1], EPS);
        float[] left = MechBodyHardpoints.pivot(10f, 20f, 90f, 2f, PORT);
        assertEquals(9.8f, left[0], EPS);
        assertEquals(20.4f, left[1], EPS);
    }

    @Test
    void arcCoversItsOwnSpanOnly() {
        assertTrue(PORT.covers(0f, -30f));
        assertTrue(PORT.covers(0f, 30f));
        assertTrue(PORT.covers(0f, -90f));
        assertFalse(PORT.covers(0f, 31f));
        assertFalse(PORT.covers(0f, -91f));
        // The arc turns with the body.
        assertTrue(PORT.covers(90f, 100f));
        assertFalse(PORT.covers(90f, 125f));
    }

    @Test
    void gunTracksTheAimAndStopsAtTheArcEdge() {
        assertEquals(-10f, PORT.gunFacing(0f, -10f), EPS);
        assertEquals(30f, PORT.gunFacing(0f, 75f), EPS);
        assertEquals(-90f, PORT.gunFacing(0f, -120f), EPS);
        assertEquals(120f, PORT.gunFacing(90f, 170f), EPS);
    }

    @Test
    void roundLeavesTheTipOfTheGunOnItsMount() {
        int gun = LayeredMechAppearance.ARMS_HC_10;
        float hull = 2f;
        float[] pivot = MechBodyHardpoints.pivot(5f, 5f, 0f, hull, PORT);
        float reach = (1f - MechBodyHardpoints.SEAT) * MechBodyHardpoints.gunLengthCells(PORT, gun, hull);
        assertTrue(reach > 0f);
        // Aimed straight ahead: the muzzle is straight ahead of the pivot.
        MechHardpointGeometry.Point ahead = MechBodyHardpoints.muzzle(5f, 5f, 0f, 0f, hull, PORT, gun);
        assertEquals(pivot[0], ahead.x(), EPS);
        assertEquals(pivot[1] + reach, ahead.y(), EPS);
        // Aimed past the arc: the gun stops at -90 and the muzzle stands out to the right of the pivot.
        MechHardpointGeometry.Point stopped = MechBodyHardpoints.muzzle(5f, 5f, 0f, -150f, hull, PORT, gun);
        assertEquals(pivot[0] + reach, stopped.x(), EPS);
        assertEquals(pivot[1], stopped.y(), EPS);
    }

    @Test
    void walkerFiresFromItsHardpointMuzzle() {
        MechLoadoutComponent loadout = MechVariant.SECURITY_WALKER.createLoadout(null);
        float hull = LayeredMechAppearance.hullWidthCells(MechVariant.SECURITY_WALKER.renderScale);
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null) continue;
            MechBodyHardpoints.Hardpoint point = MechBodyHardpoints.of(loadout.variant, mount.slot);
            MechHardpointGeometry.Point fired = MechHardpointGeometry.muzzle(3f, 4f, 0.1f, -0.2f, 40f, 75f, loadout,
                    mount, 0);
            MechHardpointGeometry.Point tip = MechBodyHardpoints.muzzle(3.1f, 3.8f, 40f, 75f, hull, point,
                    mount.component.appearanceSelector(mount.slot));
            assertEquals(tip.x(), fired.x(), EPS, mount.slot.name());
            assertEquals(tip.y(), fired.y(), EPS, mount.slot.name());
            // The round leaves out on the gun, never the body's centre.
            float[] pivot = MechBodyHardpoints.pivot(3.1f, 3.8f, 40f, hull, point);
            float reach = (float) Math.hypot(fired.x() - pivot[0], fired.y() - pivot[1]);
            assertTrue(reach > 0.1f, mount.slot.name());
        }
    }
}
