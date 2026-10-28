package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.marine.BoatGun;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Two guns on one hardpoint mount as one pod: one slot, one arc, a barrel either side. */
class AirArmamentPodTest {

    private static final String HULL = "pod-test-hull";

    @AfterEach
    void forgetTheSpec() {
        TurretSlotResolver.useHullSpecs(null);
    }

    @Test
    void aPodIsTwoGunsOnOneSlotAndArcWithABarrelEitherSide() throws Exception {
        JSONObject spec = new JSONObject("{\"center\":[20,20],\"weaponSlots\":["
                + "{\"type\":\"BALLISTIC\",\"mount\":\"TURRET\",\"angle\":90,\"arc\":150,\"locations\":[4,6]},"
                + "{\"type\":\"BALLISTIC\",\"mount\":\"TURRET\",\"angle\":0,\"arc\":360,\"locations\":[-4,0]}]}");
        TurretSlotResolver.useHullSpecs(hullId -> spec);
        String laser = BoatGun.PULSE_LASER.structureId();
        String srm = BoatGun.SRM_POD.structureId();

        MountedTurret[] mounts = AirArmament.mounts(new String[]{laser, null}, new String[]{laser, srm},
                HULL, 0f, true);

        assertEquals(3, mounts.length, "a pod's two guns, and a lone second gun on a bare top");
        MountedTurret lead = mounts[0], second = mounts[1];
        assertSame(second, lead.podPartner);
        assertSame(lead, second.podLead);
        assertEquals(lead.mount.localOffsetX, second.mount.localOffsetX);
        assertEquals(lead.mount.localOffsetY, second.mount.localOffsetY);
        assertEquals(lead.mount.arcDeg, second.mount.arcDeg, "the pod keeps the hardpoint's one arc");
        assertEquals(150f, lead.mount.arcDeg);
        float[] left = lead.barrelOffset(1f), right = second.barrelOffset(1f);
        assertEquals(-left[0], right[0], 1e-5f);
        assertEquals(-left[1], right[1], 1e-5f);
        assertEquals(MountedTurret.POD_BARREL_OFFSET_CELLS, Math.hypot(right[0], right[1]), 1e-5f);
        assertNull(mounts[2].podLead, "a bottom gun alone mounts as a plain turret");
        assertEquals(srm, mounts[2].mount.structureId);
        assertEquals(0f, mounts[2].barrelOffset(1f)[0]);
    }
}
