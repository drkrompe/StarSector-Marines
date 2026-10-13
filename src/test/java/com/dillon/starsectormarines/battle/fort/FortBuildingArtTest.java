package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Placement;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fort building art block: what it parses, the laws that pick a state, and that the shipped art is wired. */
class FortBuildingArtTest {

    private static final String ART = """
            { "roof": "a.png", "damaged": "b.png", "wreck": "c.png",
              "lights": { "sprite": "l.png", "pulseHz": 0.5, "pulseDepth": 0.3 },
              "rotors": [ { "sprite": "r.png", "x": 5.4, "y": 1.35, "cells": 2.4, "spinDegPerSec": 45.0 } ],
              "banner": { "x": 0.7, "y": 3.9, "cells": 1.7 } }
            """;

    @Test
    void parsesRoofStatesLightsRotorsAndBanner() throws Exception {
        FortBuildingArt art = FortBuildingArt.parse("s", new JSONObject(ART));
        assertEquals("a.png", art.roofFor(FortBuildingArt.State.INTACT));
        assertEquals("b.png", art.roofFor(FortBuildingArt.State.DAMAGED));
        assertNull(art.roofFor(FortBuildingArt.State.WRECKED));
        assertEquals("c.png", art.wreckPath());
        assertEquals(1, art.rotors().size());
        assertEquals(0.5f, art.rotors().get(0).damagedSpin());
        assertNotNull(art.banner());
        assertEquals(5, art.spritePaths().size());
    }

    @Test
    void refusesMissingStatesAndOutOfRangeValues() {
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject("{ \"roof\": \"a.png\" }")));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ART.replace("\"pulseDepth\": 0.3", "\"pulseDepth\": 2.0"))));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ART.replace("\"cells\": 2.4", "\"cells\": 0.0"))));
    }

    @Test
    void stateFollowsWreckAndDurability() throws Exception {
        FortBuildingArt art = FortBuildingArt.parse("s", new JSONObject(ART));
        assertEquals(FortBuildingArt.State.INTACT, art.stateOf(false, 1f));
        assertEquals(FortBuildingArt.State.INTACT, art.stateOf(false, 0.6f));
        assertEquals(FortBuildingArt.State.DAMAGED, art.stateOf(false, 0.59f));
        assertEquals(FortBuildingArt.State.WRECKED, art.stateOf(true, 1f));
    }

    @Test
    void machineryRunsOnlyWhilePoweredAndStanding() throws Exception {
        FortBuildingArt art = FortBuildingArt.parse("s", new JSONObject(ART));
        assertEquals(1f, art.activity(FortBuildingArt.State.INTACT, true));
        assertEquals(0.5f, art.activity(FortBuildingArt.State.DAMAGED, true));
        assertEquals(0f, art.activity(FortBuildingArt.State.INTACT, false));
        assertEquals(0f, art.activity(FortBuildingArt.State.WRECKED, true));
    }

    @Test
    void aTakenButUncontestedPlantKeepsRunning() throws Exception {
        FortBuildingArt art = FortBuildingArt.parse("s", new JSONObject(ART));
        assertTrue(FortBuildingArt.plantRuns(false, false));
        assertFalse(FortBuildingArt.plantRuns(true, false));
        assertFalse(FortBuildingArt.plantRuns(false, true));
        assertEquals(1f, art.activity(FortBuildingArt.State.INTACT, FortBuildingArt.plantRuns(false, false)));
    }

    @Test
    void artIsTurnedHalfARevolutionOnlyWhenItsDoorIsOnTheOtherEdge() throws Exception {
        FortBuildingArt bottom = FortBuildingArt.parse("s", new JSONObject(ART));
        FortBuildingArt top = FortBuildingArt.parse("s", new JSONObject(ART.replace("{ \"roof\"", "{ \"door\": \"top\", \"roof\"")));
        assertTrue(top.doorOnTop());
        assertEquals(false, bottom.needsHalfTurn(false));
        assertEquals(true, bottom.needsHalfTurn(true));
        assertEquals(false, top.needsHalfTurn(true));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ART.replace("{ \"roof\"", "{ \"door\": \"left\", \"roof\""))));
    }

    @Test
    void gatehouseTowerGoesDamagedBelowTheThreshold() throws Exception {
        FortGatehouseArt art = FortGatehouseArt.parse("s", new JSONObject(
                "{ \"tower\": \"t.png\", \"towerDamaged\": \"d.png\", \"gate\": \"g.png\" }"));
        assertEquals("t.png", art.towerFor(1f));
        assertEquals("d.png", art.towerFor(0.5f));
    }

    @Test
    void frameMatchesThePlacementForEveryTurn() {
        for (int turns = 0; turns < 4; turns++) {
            Placement at = BatteryFort.layout().at(40, 30, turns);
            FortFrame frame = new FortFrame(BatteryFort.PLAN, 40, 30, turns);
            assertArrayEquals(at.cell(17, 17), frame.cell(17, 17));
            int[] f = BatteryFort.floor(FortAspect.POWER);
            int[] b = frame.bounds(f[0], f[1], f[2], f[3]);
            assertEquals(turns % 2 == 0 ? f[2] : f[3], b[2] - b[0] + 1);
            // The local axes stay a right-handed pair, so art authored in the local frame is turned and never mirrored.
            int[] ex = frame.across();
            int[] ey = frame.downward();
            assertEquals(1, ex[0] * ey[1] - ex[1] * ey[0]);
        }
    }

    @Test
    void everyFortAspectCoreNamesShippedBuildingArt() {
        for (String id : new String[]{TurretCatalogRegistry.FORT_FIRE_CONTROL_STRUCTURE_ID,
                TurretCatalogRegistry.FORT_MAGAZINE_STRUCTURE_ID, TurretCatalogRegistry.FORT_POWER_STRUCTURE_ID,
                TurretCatalogRegistry.FORT_CONTROL_ROOM_STRUCTURE_ID}) {
            StructureDef def = TurretCatalogRegistry.requireStructure(id);
            assertNotNull(def.buildingArt, id + " has no building art");
            for (String path : def.buildingArt.spritePaths()) assertShipped(id, path);
            assertShipped(id, def.art.basePath);
        }
        StructureDef platform = TurretCatalogRegistry.requireStructure(
                TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID);
        assertNotNull(platform.gatehouseArt);
        for (String path : platform.gatehouseArt.spritePaths()) assertShipped(platform.id, path);
        assertShipped(platform.id, "graphics/fort/fort-banner.png");
    }

    private static void assertShipped(String id, String path) {
        assertTrue(Files.isRegularFile(Path.of("mod", path)), id + " names " + path + ", which is not shipped");
    }
}
