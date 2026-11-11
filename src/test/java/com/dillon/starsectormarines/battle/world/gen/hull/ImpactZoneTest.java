package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImpactZoneTest {

    private static int thirds(int cells) {
        return cells * 3;
    }

    @Test
    void softGroundUnderTheHullIsPloughedBare() {
        for (int x = 0; x < 40; x++) {
            GroundKind to = ImpactZone.ploughed(GroundKind.GRASS, thirds(ImpactZone.FLATTEN), 7L, x, 3);
            assertNotNull(to);
            assertEquals(true, to == GroundKind.DIRT || to == GroundKind.RUBBLE, "ploughed to " + to);
        }
    }

    @Test
    void groundBeyondTheBandIsUntouched() {
        assertNull(ImpactZone.ploughed(GroundKind.GRASS, thirds(ImpactZone.BATTER + 1), 7L, 5, 5));
        assertNull(ImpactZone.ploughed(GroundKind.STREET, thirds(ImpactZone.ROAD_RAGGED + 1), 7L, 5, 5));
    }

    @Test
    void aRoadEndsInRubbleShortOfTheHull() {
        assertEquals(GroundKind.RUBBLE, ImpactZone.ploughed(GroundKind.STREET, thirds(ImpactZone.ROAD_BREAK), 7L, 5, 5));
        assertEquals(GroundKind.RUBBLE, ImpactZone.ploughed(GroundKind.SIDEWALK, 0, 7L, 5, 5));
    }

    @Test
    void floorsAreNotPloughed() {
        assertNull(ImpactZone.ploughed(GroundKind.INDOOR, 0, 7L, 5, 5));
    }

    @Test
    void openGroundAndPondsAreBuriedAcrossTheBand() {
        int inside = ImpactZone.BATTER - ImpactZone.OPEN_RAGGED;
        for (int x = 0; x < 40; x++) {
            assertNotNull(ImpactZone.ploughed(GroundKind.SNOW, thirds(inside), 7L, x, 3));
            assertNotNull(ImpactZone.ploughed(GroundKind.WATER, thirds(inside), 7L, x, 3));
        }
    }
}
