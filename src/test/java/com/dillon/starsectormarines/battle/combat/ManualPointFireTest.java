package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManualPointFireTest {

    private static WeaponDef turretWeapon(String mountId) {
        return TurretCatalogRegistry.requireMount(mountId).weapon;
    }

    @Test
    void anArcingRackIsPointFireableButRefusedInsideItsMinimum() {
        WeaponDef lrm = turretWeapon("mount.vehicle-lrm");
        assertTrue(ManualPointFire.supportsPointFire(lrm));
        assertTrue(ManualPointFire.arcs(lrm));
        float minimum = ManualPointFire.minRangeCells(lrm);
        assertTrue(minimum >= ManualPointFire.MIN_ARC_RANGE_CELLS);
        assertTrue(ManualPointFire.tooClose(lrm, minimum - 0.01f));
        assertFalse(ManualPointFire.tooClose(lrm, minimum));
        assertFalse(ManualPointFire.tooClose(lrm, Float.NaN), "no cursor is not too close");
    }

    @Test
    void aFlatRocketKeepsOnlyItsAuthoredMinimum() {
        WeaponDef srm = turretWeapon("mount.vehicle-srm");
        assertTrue(ManualPointFire.supportsPointFire(srm));
        assertFalse(ManualPointFire.arcs(srm));
        assertEquals(Math.max(0f, srm.minRange), ManualPointFire.minRangeCells(srm));
    }
}
