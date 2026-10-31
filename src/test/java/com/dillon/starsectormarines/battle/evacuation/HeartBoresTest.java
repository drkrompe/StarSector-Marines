package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartBoresTest {

    @Test
    void theRoadSatelliteStandsBesideTheRoadNotOnIt() {
        List<int[]> road = new ArrayList<>();
        for (int y = 80; y <= 130; y++) road.add(new int[]{200, y});
        assertTrue(HeartBores.besideRoad(road, 215, 130));
        assertFalse(HeartBores.besideRoad(road, 202, 120), "a mouth on the road would block it");
        assertFalse(HeartBores.besideRoad(road, 200, 130 + HeartBores.ROAD_REACH + 1), "out of the road's reach");
        // Broad Front advances along y: the road's far end is its deepest cell, the settlement's near gate.
        assertArrayEquals(new int[]{200, 130}, HeartBores.farEnd(Form.BROAD_FRONT, road));
    }

    @Test
    void broadFrontAddsAnOptionalInnerRowOnTheLanes() {
        Form form = Form.BROAD_FRONT;
        int w = form.scale.width, h = form.scale.height;
        int[] workings = form.cellAt(0.95f, 0.5f), start = form.cellAt(0.02f, 0.5f);
        HeartBores open = HeartBores.plan(country(form, false), new CellTopology(w, h), form, workings, start, List.of(), 7L);
        assertEquals(5 + HeartBores.innerBands(form).length, open.satellites().size());
        for (HeartBores.Site site : open.satellites()) {
            if (site.number() <= 5) continue;
            float line = new float[]{1f / 6f, 1f / 3f, 2f / 3f, 5f / 6f}[site.number() - 6];
            assertTrue(Math.abs(form.across(site.x(), site.y()) - line) <= HeartBores.INNER_HALF_ACROSS + 0.01f,
                    "inner satellite " + site.number() + " stands on its lane's line");
        }
        // Ground the inner row cannot seat on leaves it out rather than failing the map.
        HeartBores shut = HeartBores.plan(country(form, true), new CellTopology(w, h), form, workings, start, List.of(), 7L);
        assertEquals(5, shut.satellites().size());
        assertEquals(0, HeartBores.innerBands(Form.THE_BURN).length);
    }

    /** Open walkable country; with {@code shutInner}, nothing walkable in the inner row's depth band but a lane down the middle. */
    private static NavigationGrid country(Form form, boolean shutInner) {
        NavigationGrid grid = new NavigationGrid(form.scale.width, form.scale.height);
        for (int y = 0; y < form.scale.height; y++) {
            for (int x = 0; x < form.scale.width; x++) {
                float depth = form.depth(x, y);
                boolean shut = shutInner && depth >= HeartBores.INNER_DEPTH_FROM - 0.01f
                        && depth <= HeartBores.INNER_DEPTH_TO + 0.01f && Math.abs(form.across(x, y) - 0.5f) > 0.03f;
                if (!shut) grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }
}
