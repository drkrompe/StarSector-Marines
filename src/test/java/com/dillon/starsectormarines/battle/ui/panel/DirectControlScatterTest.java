package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectControlScatterTest {
    @Test
    void gunsHaveNoRingAndRocketsGrowOneThatTurnsPastRange() {
        var guns = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.CHAINGUN,
                null, null, MechRole.ARMORED_SUPPORT);
        assertNull(DirectControlScatter.mech(guns, 0, 15f));

        var rockets = new MechLoadoutComponent(MechVariant.BULWARK, MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_5, null, MechRole.ARMORED_SUPPORT);
        float range = rockets.mount(MechMountSlot.LEFT_SHOULDER).weaponDef().range;
        var near = DirectControlScatter.mech(rockets, 0, range * 0.5f);
        var atRange = DirectControlScatter.mech(rockets, 0, range);
        var far = DirectControlScatter.mech(rockets, 0, range * 1.5f);
        assertFalse(near.beyondRange());
        assertFalse(atRange.beyondRange());
        assertTrue(far.beyondRange());
        assertTrue(near.radiusCells() < atRange.radiusCells());
        assertTrue(far.radiusCells() > atRange.radiusCells() * 1.5f, "past range the ring outgrows distance");
        assertEquals(DirectControlScatter.mech(rockets, 0, range * 2f).radiusCells(),
                DirectControlScatter.mech(rockets, 0, range * 5f).radiusCells(), 1e-5f,
                "a point past the ceiling lands at the ceiling");
    }

    @Test
    void anArcingRackRingsItsCursorToo() {
        var warden = new MechLoadoutComponent(MechVariant.WARDEN, null);
        var ring = DirectControlScatter.mech(warden, 0, 20f);
        assertNotNull(ring);
        assertTrue(ring.radiusCells() > 0f);
        assertFalse(ring.tooClose());
        assertTrue(DirectControlScatter.mech(warden, 0, 3f).tooClose());
        assertTrue(DirectControlScatter.mech(warden, 0, 3f).warning());
    }
}
