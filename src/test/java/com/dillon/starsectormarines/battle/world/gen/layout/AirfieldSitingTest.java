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
    /** The column band the clearance tests keep. */
    private static final int BAND = 6;

    private static LayoutGround ground() {
        return ground(null, false);
    }

    /** The same ground keeping {@code ways}, with a road along the map's south edge when {@code edgeRoad}. */
    private static LayoutGround ground(MissionLayout.Ways ways, boolean edgeRoad) {
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
        if (edgeRoad) for (int x = 0; x < W; x++) for (int y = 0; y < LayoutGround.EDGE; y++) road[x][y] = true;
        ctx.put(BspKeys.ROAD_CELLS, road);
        return new LayoutGround(ctx, ways);
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

    /** The clearance runs the band either side of a column's way, the way included, and not along the map's edge. */
    @Test
    void theColumnClearanceIsTheBandEitherSideOfAColumnsWay() {
        LayoutGround ground = ground(new MissionLayout.Ways(BAND, false), true);
        assertTrue(ground.inColumnClearance(TRACK_X + 1, 20), "the way itself");
        assertTrue(ground.inColumnClearance(TRACK_X - BAND, 20));
        assertFalse(ground.inColumnClearance(TRACK_X - BAND - 1, 20));
        assertTrue(ground.inColumnClearance(TRACK_X + 2 + BAND, 20));
        assertFalse(ground.inColumnClearance(TRACK_X + 3 + BAND, 20));
        assertTrue(ground.inColumnClearance(40, ROAD_Y - BAND));
        assertFalse(ground.inColumnClearance(40, ROAD_Y - BAND - 1));
        assertFalse(ground.inColumnClearance(40, LayoutGround.EDGE + 1), "the edge's road is no column's");
        assertFalse(ground(MissionLayout.Ways.NONE, true).inColumnClearance(TRACK_X + 1, 20), "no band, no clearance");
    }

    /**
     * With a band kept, the field, its hamlet and its access track keep out of the clearance: the field turns its
     * apron to the edge's road rather than cross the band to the column's.
     */
    @Test
    void aFieldAndItsHamletKeepOutOfTheColumnClearance() {
        LayoutGround ground = ground(new MissionLayout.Ways(BAND, false), true);
        Structure field = AirfieldSiting.site(ground, "airbase-1", new int[]{1, 1, TRACK_X - 1, ROAD_Y - 1},
                TRACK_X, ROAD_Y, (x, y) -> ground.buildable(x, y));
        assertNotNull(field);
        assertEquals(0, field.turns(), "the apron faces the edge's road, away from the clearance");
        assertTrue(field.x() + field.width() - 1 < TRACK_X - BAND, "the field stands off the track's band");
        assertTrue(field.y() + field.height() - 1 < ROAD_Y - BAND, "the field stands off the road's band");
        AirfieldSiting.Access access = AirfieldSiting.access(ground, field);
        assertTrue(access.reaches(), "the gate reaches the edge's road");
        for (int[] c : access.track()) assertFalse(ground.inColumnClearance(c[0], c[1]), "the track crosses the band");
        HamletGrowth.Hamlet grown = AirfieldSiting.hamlet(ground, field, (x, y) -> ground.buildable(x, y));
        for (int[] c : grown.lane()) assertFalse(ground.inColumnClearance(c[0], c[1]), "the lane runs in the clearance");
        for (Structure s : grown.buildings()) {
            for (int x = s.x(); x < s.x() + s.width(); x++) {
                for (int y = s.y(); y < s.y() + s.height(); y++) {
                    assertFalse(ground.inColumnClearance(x, y), s.name() + " stands in the clearance");
                }
            }
        }
    }

    /**
     * The hamlet grows round a lane of its own joined to a way, four or more buildings where there is room, and keeps
     * off the field, the ways, and the strip's rows past either end.
     */
    @Test
    void aHamletLeavesTheStripTheFieldAndTheWaysClear() {
        LayoutGround ground = ground();
        Structure field = new Structure("airbase-1", StructureKind.AIRFIELD, 40, ROAD_Y - 3 - StructureKind.AIRFIELD.deep,
                2, false);
        HamletGrowth.Hamlet grown = AirfieldSiting.hamlet(ground, field, (x, y) -> ground.buildable(x, y));
        List<Structure> hamlet = grown.buildings();
        assertTrue(hamlet.size() >= 4, "open ground either side and nothing built: " + hamlet.size());
        assertFalse(grown.lane().isEmpty(), "the hamlet has its lane");
        for (int[] c : grown.lane()) {
            assertFalse(ground.isWay(c[0], c[1]), "the lane runs on a way");
            for (Structure s : hamlet) assertFalse(overlaps(s, c[0], c[1], c[0], c[1]), s.name() + " stands on the lane");
        }
        int stripY1 = field.y() + AirfieldSiting.STRIP_BAND - 1;
        for (Structure s : hamlet) {
            assertFalse(overlaps(s, field.x() - 1, field.y() - 1, field.x() + field.width(), field.y() + field.height()),
                    s.name() + " stands on the field");
            assertFalse(overlaps(s, field.x() - AirfieldSiting.RUN_UP, field.y(),
                    field.x() + field.width() - 1 + AirfieldSiting.RUN_UP, stripY1), s.name() + " stands in the run-up");
            assertFalse(overlaps(s, 0, ROAD_Y, W - 1, ROAD_Y + 2), s.name() + " stands on the road");
            assertFalse(overlaps(s, TRACK_X, 0, TRACK_X + 2, ROAD_Y), s.name() + " stands on the track");
        }
        for (int i = 0; i < hamlet.size(); i++) {
            for (int j = i + 1; j < hamlet.size(); j++) {
                Structure a = hamlet.get(i);
                assertFalse(overlaps(hamlet.get(j), a.x(), a.y(), a.x() + a.width() - 1, a.y() + a.height() - 1),
                        "two of the hamlet's buildings overlap");
            }
        }
    }

    @Test
    void aFieldKeepsItsGapAndItsStripsRunUpClear() {
        Structure field = new Structure("airbase-1", StructureKind.AIRFIELD, 100, 20, 0, false);
        int stripY0 = AirfieldSiting.apronStep(field) > 0 ? field.y() : field.y() + field.height() - AirfieldSiting.STRIP_BAND;
        int apronY = AirfieldSiting.apronStep(field) > 0 ? field.y() + field.height() - 1 : field.y();
        int pastEnd = field.x() + field.width() + AirfieldSiting.RUN_UP - 1;
        assertTrue(AirfieldSiting.keepsClear(field, field.x() + 3, field.y() + 3), "the field itself");
        assertTrue(AirfieldSiting.keepsClear(field, pastEnd, stripY0), "the strip's rows past its end: the run-up");
        assertFalse(AirfieldSiting.keepsClear(field, pastEnd + 1, stripY0), "past the run-up");
        assertFalse(AirfieldSiting.keepsClear(field, field.x() + field.width() + AirfieldSiting.RUN_UP / 3, apronY),
                "off the field's end on its apron half: where a pit may stand");
    }
}
