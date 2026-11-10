package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFacesTest {

    static final int GRID_W = 10;

    /** A 2x2 roof at (3,3) whose outer edges stop short by an eave: 0.7 of a cell in from its building's side. */
    private static float[][] rects(int at) {
        int x = at % GRID_W, y = at / GRID_W;
        float x0 = x == 3 ? .3f : 0f, x1 = x == 4 ? .7f : 1f, y0 = y == 3 ? .3f : 0f, y1 = y == 4 ? .7f : 1f;
        return new float[][]{{x0, y0, x1, y1}};
    }

    private static final int[] CELLS = {33, 34, 43, 44};

    @Test
    void theOutlineIsTheRoofAsDrawnNotItsCells() {
        float[] edges = BuildingFaces.outline(CELLS, GRID_W, BuildingFacesTest::rects);
        assertEquals(4 * 6, edges.length, "one straight run a side");
        for (int e = 0; e < edges.length; e += 6) {
            for (int k = 0; k < 4; k += 2) {
                float px = edges[e + k], py = edges[e + k + 1];
                assertTrue(near(px, 3.3f) || near(px, 4.7f), "x on the eave line: " + px);
                assertTrue(near(py, 3.3f) || near(py, 4.7f), "y on the eave line: " + py);
            }
            float mx = (edges[e] + edges[e + 2]) / 2f, my = (edges[e + 1] + edges[e + 3]) / 2f;
            assertTrue(edges[e + 4] * (mx - 4f) + edges[e + 5] * (my - 4f) > 0f, "normals point out");
        }
    }

    @Test
    void aWallsTopIsTheRoofsEdgeAtAnyView() {
        float[] edges = BuildingFaces.outline(CELLS, GRID_W, BuildingFacesTest::rects);
        float lift = BuildingFaces.lift(BuildingFaces.HEIGHTS[2]);
        for (float c : new float[]{-40f, 0f, 3.7f, 4f, 12.5f, 90f}) {
            for (int e = 0; e < edges.length; e += 6) {
                for (int k = 0; k < 4; k += 2) {
                    float p = edges[e + k];
                    // The roof's piece is drawn as its centre lifted and its size scaled by the lift, about the
                    // view's centre; its corner on this edge must be the wall's top.
                    float centre = 4f, half = .7f;
                    float roofCorner = BuildingFaces.lifted(centre, c, lift) + Math.signum(p - centre) * half * lift;
                    assertEquals(roofCorner, BuildingFaces.lifted(p, c, lift), 1e-4f, "view at " + c);
                }
            }
        }
    }

    @Test
    void onlyTheWallsTurnedToTheEyeAreFacesAndTallerRoofsLiftMore() {
        assertTrue(BuildingFaces.facing(1f, 0f, 5f, 0f, 10f, 0f));
        assertFalse(BuildingFaces.facing(-1f, 0f, 2f, 0f, 10f, 0f));
        assertEquals(1f, BuildingFaces.lift(0f), 1e-6f);
    }

    private static BuildingFaces.Built built(BuildingKind kind, int cells, float variation) {
        return new BuildingFaces.Built(kind, cells, false, false, variation);
    }

    private static float liftOf(BuildingFaces.Built b) {
        return BuildingFaces.lift(BuildingFaces.HEIGHTS[BuildingFaces.heightClass(b)]);
    }

    @Test
    void aBuildingsLiftFollowsWhatItWasBuiltAs() {
        float shed = liftOf(built(BuildingKind.RESIDENTIAL, 10, .5f));
        float home = liftOf(built(BuildingKind.RESIDENTIAL, 50, .5f));
        float garage = liftOf(built(BuildingKind.INDUSTRIAL, 30, .1f));
        float tallWorks = liftOf(built(BuildingKind.INDUSTRIAL, 300, .1f));
        assertTrue(shed < home, "a hut stands lower than a home");
        assertTrue(garage < home, "a garage stands lower than a two-storey home");
        assertTrue(tallWorks > home, "the biggest works stand over the homes");
        assertTrue(liftOf(new BuildingFaces.Built(BuildingKind.RESIDENTIAL, 50, true, false, .5f)) > home,
                "a sealed town's block stacks higher");
        assertEquals(BuildingFaces.TALL, BuildingFaces.heightClass(new BuildingFaces.Built(BuildingKind.CIVIC, 9, false,
                true, .5f)), "a tower's room makes a tower");
    }

    @Test
    void aStreetsHomesStepUpAndDownByTheirOwnNumber() {
        assertEquals(BuildingFaces.ONE, BuildingFaces.heightClass(built(BuildingKind.RESIDENTIAL, 80, .1f)));
        assertEquals(BuildingFaces.TWO, BuildingFaces.heightClass(built(BuildingKind.RESIDENTIAL, 80, .5f)));
        assertEquals(BuildingFaces.THREE, BuildingFaces.heightClass(built(BuildingKind.RESIDENTIAL, 80, .9f)));
    }

    @Test
    void aDestroyedBuildingDropsItsLiftToRubble() {
        BuildingFaces.Built works = built(BuildingKind.INDUSTRIAL, 400, .5f);
        assertEquals(BuildingFaces.heightClass(works), BuildingFaces.heightClass(works, .2f),
                "a damaged building still stands");
        assertEquals(BuildingFaces.RAZED, BuildingFaces.heightClass(works, .6f));
        assertEquals(1f, BuildingFaces.lift(BuildingFaces.HEIGHTS[BuildingFaces.RAZED]), 1e-6f, "rubble lies flat");
    }

    @Test
    void aBreachOpensTheFaceAndADoorOrWindowIsDrawnWhereTheBuildingHasOne() {
        assertEquals(BuildingFaces.Segment.BREACH, BuildingFaces.segment(false, false, false, false, false));
        assertEquals(BuildingFaces.Segment.DOOR, BuildingFaces.segment(true, true, false, false, false));
        assertEquals(BuildingFaces.Segment.LOADING, BuildingFaces.segment(true, true, false, false, true));
        assertEquals(BuildingFaces.Segment.PANE, BuildingFaces.segment(true, false, true, true, false));
        assertEquals(BuildingFaces.Segment.BLIND, BuildingFaces.segment(true, false, false, false, false));
        assertEquals(BuildingFaces.Look.SEALED, BuildingFaces.look("colony.ceramic-wall", false));
        assertEquals(BuildingFaces.Look.INSULATED, BuildingFaces.look("colony.masonry-wall", true));
    }

    @Test
    void aRunIsCutAtEachCellAndEachPieceReadsTheCellUnderIt() {
        // The roof's south edge, from 3.3 to 4.7 along y = 3.3, facing south: two pieces over cells (3,3) and (4,3).
        float[] segs = BuildingFaces.segments(new float[]{3.3f, 3.3f, 4.7f, 3.3f, 0f, -1f}, GRID_W);
        assertEquals(2 * BuildingFaces.SEG, segs.length);
        assertEquals(4f, segs[2], 1e-4f);
        assertEquals(33, (int) segs[6]);
        assertEquals(34, (int) segs[BuildingFaces.SEG + 6]);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}
