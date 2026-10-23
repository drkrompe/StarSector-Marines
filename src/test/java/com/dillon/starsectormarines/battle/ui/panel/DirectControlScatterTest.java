package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.MechTriggerPicks;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectControlScatterTest {
    @Test
    void gunsHaveNoRingAndRocketsGrowOneThatTurnsPastRange() {
        var guns = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.CHAINGUN,
                null, null, MechRole.ARMORED_SUPPORT);
        assertNull(DirectControlScatter.mech(guns, MechTriggerPicks.LINKED, 15f));

        var rockets = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_5, null, MechRole.ARMORED_SUPPORT);
        float range = rockets.mount(MechMountSlot.LEFT_SHOULDER).weaponDef().range;
        var near = DirectControlScatter.mech(rockets, MechTriggerPicks.LINKED, range * 0.5f);
        var atRange = DirectControlScatter.mech(rockets, MechTriggerPicks.LINKED, range);
        var far = DirectControlScatter.mech(rockets, MechTriggerPicks.LINKED, range * 1.5f);
        assertFalse(near.beyondRange());
        assertFalse(atRange.beyondRange());
        assertTrue(far.beyondRange());
        assertTrue(near.radiusCells() < atRange.radiusCells());
        assertTrue(far.radiusCells() > atRange.radiusCells() * 1.5f, "past range the ring outgrows distance");
        assertEquals(DirectControlScatter.mech(rockets, MechTriggerPicks.LINKED, range * 2f).radiusCells(),
                DirectControlScatter.mech(rockets, MechTriggerPicks.LINKED, range * 5f).radiusCells(), 1e-5f,
                "a point past the ceiling lands at the ceiling");
    }

    @Test
    void anArcingRackRingsItsCursorToo() {
        var warden = new MechLoadoutComponent(MechVariant.WARDEN, null);
        var ring = DirectControlScatter.mech(warden, MechTriggerPicks.LINKED, 20f);
        assertNotNull(ring);
        assertTrue(ring.radiusCells() > 0f);
        assertFalse(ring.tooClose());
        assertTrue(DirectControlScatter.mech(warden, MechTriggerPicks.LINKED, 3f).tooClose());
        assertTrue(DirectControlScatter.mech(warden, MechTriggerPicks.LINKED, 3f).warning());
    }

    @Test
    void aVehicleLauncherRingsLikeAMechRackAndAVehicleGunHasNone() {
        WeaponDef lrm = VehicleType.LRM_CARRIER.turretStructure().mount.weapon;
        var inRange = DirectControlScatter.vehicle(lrm, lrm.range * 0.6f);
        assertNotNull(inRange);
        assertFalse(inRange.warning());
        assertTrue(DirectControlScatter.vehicle(lrm, lrm.range * 1.2f).beyondRange());
        var close = DirectControlScatter.vehicle(lrm, 4f);
        assertTrue(close.tooClose());
        assertTrue(close.warning());
        assertNull(DirectControlScatter.vehicle(VehicleType.HEAVY_APC.turretStructure().mount.weapon, 15f));
    }

    @Test
    void aWarningRingStaysVisibleWhereAnOrdinaryOneWouldHideUnderTheCrosshair() {
        var tiny = new DirectControlScatter.Ring(0.05f, false, false);
        assertTrue(DirectControlPanel.scatterRingPx(tiny, 20f) < 0f);
        var refused = new DirectControlScatter.Ring(0.05f, false, true);
        assertEquals(DirectControlPanel.MIN_WARNING_RING_PX, DirectControlPanel.scatterRingPx(refused, 20f));
        assertEquals(60f, DirectControlPanel.scatterRingPx(new DirectControlScatter.Ring(3f, true, false), 20f));
    }
}
