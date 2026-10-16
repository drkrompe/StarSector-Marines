package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurretMountGeometryTest {
    @Test
    void authoredMouthsCycleAndRotateInMountFrame() throws Exception {
        TurretMountDef mount = mount("[[ -0.5, 1 ], [ 0.75, 1.5 ]]");
        assertEquals(new TurretMountGeometry.Point(9.5f, 21f),
                TurretMountGeometry.muzzle(10, 20, 0, mount, 0));
        assertEquals(new TurretMountGeometry.Point(10.75f, 21.5f),
                TurretMountGeometry.muzzle(10, 20, 0, mount, 3));
        TurretMountGeometry.Point rotated = TurretMountGeometry.muzzle(10, 20, 90, mount, 1);
        assertEquals(8.5f, rotated.x(), .0001f);
        assertEquals(20.75f, rotated.y(), .0001f);
    }

    @Test
    void rejectsEmptyMalformedAndNonFiniteMouths() {
        assertThrows(JSONException.class, () -> mount("[]"));
        assertThrows(JSONException.class, () -> mount("[[1]]"));
        assertThrows(JSONException.class, () -> mount("[[1,2,3]]"));
        assertThrows(JSONException.class, () -> mount("[[1e99,1]]"));
    }

    @Test
    void omittedMouthsRetainAlternatingLegacyPair() throws Exception {
        var json = base();
        json.getJSONObject("render").put("muzzleLateralOffsetCells", .5);
        var mount = TurretMountDef.parse(json, WeaponRegistry.installed().require("weapon.ridge-aa"));
        assertEquals(new TurretMountGeometry.Point(-.5f, 1f),
                TurretMountGeometry.muzzle(0, 0, 0, mount, 0));
        assertEquals(new TurretMountGeometry.Point(.5f, 1f),
                TurretMountGeometry.muzzle(0, 0, 0, mount, 1));
    }

    private static TurretMountDef mount(String mouths) throws Exception {
        JSONObject json = base();
        json.getJSONObject("render").put("muzzles", new JSONArray(mouths));
        return TurretMountDef.parse(json, WeaponRegistry.installed().require("weapon.ridge-aa"));
    }

    private static JSONObject base() throws Exception {
        return new JSONObject("""
                {"id":"mount.test","weapon":"weapon.ridge-aa",
                 "sim":{"ammoCapacity":0,"turnRateDegPerSec":90},
                 "render":{"muzzleOffsetCells":1}}
                """);
    }
}
