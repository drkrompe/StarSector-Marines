package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where an airfield stands against the ways round it, and where its hamlet may not. */
class AirfieldSitingTest {

    private static final int W = 240, H = 90;
    /** The road along x the fields are sited beside, and a track across it. */
    private static final int ROAD_Y = 50, TRACK_X = 120;

    private static LayoutGround ground() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(1), W, H, 1L);
        boolean[][] road = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = ROAD_Y; y < ROAD_Y + 3; y++) road[x][y] = true;
        for (int y = 0; y < ROAD_Y; y++) for (int x = TRACK_X; x < TRACK_X + 3; x++) road[x][y] = true;
        ctx.put(BspKeys.ROAD_CELLS, road);
        return new LayoutGround(ctx);
    }

    private static boolean overlaps(Structure s, int x0, int y0, int x1, int y1) {
        return s.x() <= x1 && s.x() + s.width() - 1 >= x0 && s.y() <= y1 && s.y() + s.height() - 1 >= y0;
    }

    /** A field takes no way, faces its apron and gate to the road it stands beside, and reaches it by a short track. */
    @Test
    void aFieldStandsBesideTheRoadWithItsApronTowardIt() {
        LayoutGround ground = ground();
        Structure field = AirfieldSiting.site(ground, "airbase-1", new int[]{1, 1, TRACK_X - 1, ROAD_Y - 1},
                40, 10, (x, y) -> ground.buildable(x, y));
        assertNotNull(field);
        assertFalse(overlaps(field, 0, ROAD_Y, W - 1, ROAD_Y + 2), "the field stands on the road");
        assertFalse(overlaps(field, TRACK_X, 0, TRACK_X + 2, ROAD_Y), "the field cuts the track");
        assertEquals(2, field.turns(), "the road is north, so the strip goes south and the apron faces the road");
        AirfieldSiting.Access access = AirfieldSiting.access(ground, field);
        assertTrue(access.track().size() <= 3 * (AirfieldSiting.ACCESS_REACH + 2), "the track is short");
        assertTrue(ROAD_Y - (field.y() + field.height()) <= AirfieldSiting.ACCESS_REACH, "the field is beside the road");
    }

    /** The hamlet keeps off the field, the ways, and the strip's rows past either end. */
    @Test
    void aHamletLeavesTheStripTheFieldAndTheWaysClear() {
        LayoutGround ground = ground();
        Structure field = new Structure("airbase-1", StructureKind.AIRFIELD, 40, ROAD_Y - 3 - StructureKind.AIRFIELD.deep,
                2, false);
        List<Structure> hamlet = AirfieldSiting.hamlet(ground, field, (x, y) -> ground.buildable(x, y));
        assertFalse(hamlet.isEmpty(), "open ground either side and nothing built");
        int stripY1 = field.y() + AirfieldSiting.STRIP_BAND - 1;
        for (Structure s : hamlet) {
            assertFalse(overlaps(s, field.x() - 1, field.y() - 1, field.x() + field.width(), field.y() + field.height()),
                    s.name() + " stands on the field");
            assertFalse(overlaps(s, field.x() - AirfieldSiting.RUN_UP, field.y(),
                    field.x() + field.width() - 1 + AirfieldSiting.RUN_UP, stripY1), s.name() + " stands in the run-up");
            assertFalse(overlaps(s, 0, ROAD_Y, W - 1, ROAD_Y + 2), s.name() + " stands on the road");
            assertFalse(overlaps(s, TRACK_X, 0, TRACK_X + 2, ROAD_Y), s.name() + " stands on the track");
            assertTrue(s.y() + s.height() - 1 <= field.y() + field.height() - 1, s.name() + " is past the apron edge");
        }
        for (int i = 0; i < hamlet.size(); i++) {
            for (int j = i + 1; j < hamlet.size(); j++) {
                Structure a = hamlet.get(i);
                assertFalse(overlaps(hamlet.get(j), a.x(), a.y(), a.x() + a.width() - 1, a.y() + a.height() - 1),
                        "two of the hamlet's buildings overlap");
            }
        }
    }
}
