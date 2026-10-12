package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipEngineTest {

    private static final float K = WarshipScale.cells(1f);

    @Test
    void anEngineSlotIsPlacedByTheSameRuleAsAWeaponSlot() throws Exception {
        JSONObject hull = new JSONObject("""
                {"weaponSlots":[{"id":"WS 001","type":"BALLISTIC","size":"LARGE","mount":"TURRET",
                                 "locations":[-82,70],"angle":0,"arc":90}],
                 "engineSlots":[{"angle":160,"contrailSize":64,"length":64,"location":[-82,70],
                                 "style":"LOW_TECH","width":20}]}""");
        WarshipSlot gun = WarshipSlot.parse(hull).get(0);
        WarshipEngine engine = WarshipEngine.parse(hull).get(0);

        assertEquals(gun.localX(), engine.localX(), 1e-5f);
        assertEquals(gun.localY(), engine.localY(), 1e-5f);
        // Aft of the centre of gravity: behind the nose, so negative along it.
        assertTrue(engine.localY() < 0f);
    }

    @Test
    void sizesAreConvertedFromPixelsToCellsAndTheStyleIsKept() throws Exception {
        JSONObject hull = new JSONObject("""
                {"engineSlots":[{"angle":200,"length":64,"location":[-103,-40],"style":"HIGH_TECH","width":20}]}""");
        WarshipEngine engine = WarshipEngine.parse(hull).get(0);

        assertEquals(200f, engine.angleDegrees(), 1e-5f);
        assertEquals(64f * K, engine.lengthCells(), 1e-5f);
        assertEquals(20f * K, engine.widthCells(), 1e-5f);
        assertEquals("HIGH_TECH", engine.style());
        // Starboard of the nose line: vanilla +y is port, so a negative y is positive x here.
        assertEquals(40f * K, engine.localX(), 1e-5f);
        assertEquals(-103f * K, engine.localY(), 1e-5f);
    }

    @Test
    void missingFieldsTakeTheAircraftParsersDefaults() throws Exception {
        JSONObject hull = new JSONObject("""
                {"engineSlots":[{"location":[0,0]},{"angle":180}]}""");
        List<WarshipEngine> engines = WarshipEngine.parse(hull);

        // The second has no position and is skipped; the first is a midline nozzle burning aft.
        assertEquals(1, engines.size());
        assertEquals(180f, engines.get(0).angleDegrees(), 1e-5f);
        assertEquals("MIDLINE", engines.get(0).style());
    }

    @Test
    void aHullWithoutEngineSlotsHasNoNozzles() throws Exception {
        assertTrue(WarshipEngine.parse(new JSONObject("{}")).isEmpty());
    }
}
