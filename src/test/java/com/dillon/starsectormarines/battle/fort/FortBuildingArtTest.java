package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Placement;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                TurretCatalogRegistry.FORT_CONTROL_ROOM_STRUCTURE_ID, TurretCatalogRegistry.FORT_AIR_CONTROL_STRUCTURE_ID}) {
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

    private static final String ANNEXED_ART = """
            { "roof": "a.png", "damaged": "b.png", "wreck": "c.png",
              "annexes": [ { "room": "loading-bay", "roof": "r.png", "damaged": "d.png", "wreck": "w.png",
                             "lights": { "sprite": "l2.png" } } ],
              "blastDoor": { "sprite": "door.png", "damaged": "door-hurt.png" } }
            """;

    @Test
    void parsesAnnexRoomsAndABlastDoor() throws Exception {
        FortBuildingArt art = FortBuildingArt.parse("s", new JSONObject(ANNEXED_ART));
        FortBuildingArt.Annex bay = art.annex("loading-bay");
        assertNotNull(bay);
        assertEquals("r.png", bay.roofFor(FortBuildingArt.State.INTACT));
        assertEquals("d.png", bay.roofFor(FortBuildingArt.State.DAMAGED));
        assertNull(bay.roofFor(FortBuildingArt.State.WRECKED));
        assertNull(art.annex("operations-hall"));
        assertEquals("door-hurt.png", art.blastDoor().damaged());
        assertTrue(art.spritePaths().containsAll(List.of("r.png", "d.png", "w.png", "l2.png", "door.png", "door-hurt.png")));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ANNEXED_ART.replace("\"annexes\": [", "\"annexes\": [ { \"room\": \"loading-bay\", \"roof\": \"x\", \"damaged\": \"y\", \"wreck\": \"z\" },"))));
        assertThrows(JSONException.class, () -> FortBuildingArt.parse("s", new JSONObject(
                ANNEXED_ART.replace("\"damaged\": \"d.png\", ", ""))));
    }

    @Test
    void everyArtKeyedAnteRoomIsDrawnByItsAspectsArt() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            int keyed = 0;
            for (FortRoom room : plan.rooms()) {
                if (room.art() == null) continue;
                keyed++;
                assertFalse(room.main(), "a main room is drawn by its aspect's own roof");
                StructureDef def = TurretCatalogRegistry.requireStructure(plan.coreStructureId(room.aspect()));
                assertNotNull(def.buildingArt.annex(room.art()), room.aspect() + " names no art for " + room.art());
                assertTrue(def.buildingArt.annexRooms(plan, room.aspect()).contains(room));
            }
            assertTrue(keyed > 0);
        }
    }

    @Test
    void shippedRoofArtIsTheSizeOfTheFloorItCovers() throws IOException {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (FortAspect aspect : FortAspect.values()) {
                String id;
                try {
                    id = plan.coreStructureId(aspect);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                FortBuildingArt art = TurretCatalogRegistry.requireStructure(id).buildingArt;
                if (art == null) continue;
                int[] floor = plan.floor(aspect);
                assertSize(id, art.roofFor(FortBuildingArt.State.INTACT), floor);
                assertSize(id, art.roofFor(FortBuildingArt.State.DAMAGED), floor);
                assertSize(id, art.wreckPath(), floor);
                if (art.lights() != null) assertSize(id, art.lights().spritePath(), floor);
                for (FortRoom room : art.annexRooms(plan, aspect)) {
                    FortBuildingArt.Annex annex = art.annex(room.art());
                    assertSize(id + "/" + room.art(), annex.roof(), room.floor());
                    assertSize(id + "/" + room.art(), annex.damaged(), room.floor());
                    assertSize(id + "/" + room.art(), annex.wreck(), room.floor());
                    if (annex.lights() != null) assertSize(id + "/" + room.art(), annex.lights().spritePath(), room.floor());
                }
                if (art.blastDoor() != null) {
                    assertSize(id, art.blastDoor().sprite(), new int[]{0, 0, 3, 1});
                    assertSize(id, art.blastDoor().damaged(), new int[]{0, 0, 3, 1});
                }
            }
        }
    }

    /** Art is exported at 64 px per cell of the floor it covers, so a sprite that is not that size has been re-cropped. */
    private static void assertSize(String id, String path, int[] floor) throws IOException {
        BufferedImage image = ImageIO.read(Path.of("mod", path).toFile());
        assertEquals(floor[2] * 64, image.getWidth(), id + " " + path + " width");
        assertEquals(floor[3] * 64, image.getHeight(), id + " " + path + " height");
    }

    private static void assertShipped(String id, String path) {
        assertTrue(Files.isRegularFile(Path.of("mod", path)), id + " names " + path + ", which is not shipped");
    }
}
