package com.dillon.starsectormarines.battle.air.engine;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A door gun goes on a turret before a fixed rail, whatever order the spec lists them in. */
class TurretSlotResolverOrderTest {

    @AfterEach
    void forgetTheSpec() {
        TurretSlotResolver.useHullSpecs(null);
    }

    @Test
    void turretsFillBeforeFixedHardpointsAndNonWeaponSlotsAreSkipped() throws Exception {
        // The Kite's shape: two wing rails listed ahead of the one turret, then a system slot.
        JSONObject spec = new JSONObject("{\"center\":[41,28],\"weaponSlots\":["
                + "{\"type\":\"MISSILE\",\"mount\":\"HARDPOINT\",\"angle\":0,\"arc\":6,\"locations\":[6,22]},"
                + "{\"type\":\"MISSILE\",\"mount\":\"HARDPOINT\",\"angle\":0,\"arc\":6,\"locations\":[6,-22]},"
                + "{\"type\":\"BALLISTIC\",\"mount\":\"TURRET\",\"angle\":0,\"arc\":200,\"locations\":[-7,0]},"
                + "{\"type\":\"SYSTEM\",\"mount\":\"HIDDEN\",\"angle\":0,\"arc\":5,\"locations\":[0,0]}]}");
        TurretSlotResolver.useHullSpecs(hullId -> spec);

        List<WeaponSlot> mounts = TurretSlotResolver.mounts("order-test-hull");

        assertEquals(3, mounts.size());
        assertTrue(mounts.get(0).turret(), "the turret is the first hardpoint filled");
        assertEquals(200f, mounts.get(0).arcDeg());
        assertTrue(mounts.get(0).localY() < 0f, "it sits aft of the hull centre, as the spec puts it");
        assertEquals("HARDPOINT", mounts.get(1).mount());
        assertEquals(mounts.get(0).localX(), TurretSlotResolver.resolve("order-test-hull")[0][0]);
    }
}
