package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONObject;
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

    /** A beam with a splash burns where a player points it, as a rocket bursts there; a plain beam runs on its bearing. */
    @Test
    void aSplashBeamBurstsWhereItIsPointedAndAPlainBeamDoesNot() throws Exception {
        assertTrue(ManualPointFire.bursts(beam(0.5f)));
        assertFalse(ManualPointFire.bursts(beam(0f)));
    }

    private static WeaponDef beam(float aoeRadius) throws Exception {
        JSONObject json = new JSONObject("""
                {
                  "id": "weapon.point-beam-fixture", "mount": "mech-mount",
                  "catalog": { "displayName": "Point Beam" },
                  "sim": { "range": 30.0, "damage": 8.0, "accuracy": 1.0, "cooldown": 1.0, "penetration": 2.0,
                           "roundVelocity": 320.0, "flightSec": 0.05 },
                  "render": { "beam": { "coreWidthPx": 2.0, "glowColor": "E01A12", "glowWidthPx": 8.0,
                    "pulseCycles": 1.0, "lifetimeSec": 0.1 } },
                  "fx": { "impact": [ {"kind": "glow", "radius": 0.2, "lifetime": 0.1} ] }
                }
                """);
        if (aoeRadius > 0f) json.getJSONObject("sim").put("aoeRadius", aoeRadius);
        return WeaponDef.parse(json);
    }
}
