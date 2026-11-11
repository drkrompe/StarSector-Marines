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
    void aCutAwayBuildingsNearWallsDropToKneeHeightWithItsRoofsFade() {
        float h = BuildingFaces.HEIGHTS[BuildingFaces.TWO];
        assertEquals(1f, BuildingFaces.standing(h, 0f), 1e-6f, "roof on: the wall stands whole");
        assertEquals(BuildingFaces.KNEE_METERS, BuildingFaces.standing(h, 1f) * h, 1e-4f, "roof gone: knee high");
        float last = 1f;
        for (int k = 1; k <= 20; k++) {
            float now = BuildingFaces.standing(h, k / 20f);
            assertTrue(now < last && last - now < .06f, "it falls step by step with the fade, never a pop");
            last = now;
        }
    }

    @Test
    void theStreetJustPastATallBuildingIsHiddenUnderItButTheNearStreetIsNot() {
        // A tall building from x = 20 to 30, the view's centre at x = 0.
        float lift = BuildingFaces.lift(BuildingFaces.HEIGHTS[BuildingFaces.TALL]);
        float pastFarSide = BuildingFaces.unlifted(31f, 0f, lift);
        float nearSide = BuildingFaces.unlifted(19f, 0f, lift);
        assertTrue(pastFarSide >= 20f && pastFarSide < 30f, "a cell past the far wall shows the roof: " + pastFarSide);
        assertTrue(nearSide < 20f, "the street before the near wall stays in sight");
        assertEquals(31f, BuildingFaces.lifted(pastFarSide, 0f, lift), 1e-4f, "unlifting undoes the lift");
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
                "a building with a few walls breached still stands");
        assertEquals(BuildingFaces.heightClass(works), BuildingFaces.heightClass(works, 0f),
                "one whose roof fell in with its walls standing stands as a shell");
        assertEquals(BuildingFaces.RAZED, BuildingFaces.heightClass(works, .6f), "most walls down razes it");
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
    void anUpperStoreysWindowStandsOnlyOverARealOne() {
        BuildingFaces.Segment w = BuildingFaces.Segment.WINDOW, p = BuildingFaces.Segment.PANE;
        BuildingFaces.Segment blind = BuildingFaces.Segment.BLIND;
        assertEquals(w, BuildingFaces.storeyFeature(w, 0, false), "the ground storey is what stands there");
        assertEquals(BuildingFaces.Segment.DOOR, BuildingFaces.storeyFeature(BuildingFaces.Segment.DOOR, 0, true));
        assertEquals(w, BuildingFaces.storeyFeature(w, 2, true), "stacked over a window");
        assertEquals(p, BuildingFaces.storeyFeature(p, 1, true), "a slit over a slit");
        assertEquals(blind, BuildingFaces.storeyFeature(BuildingFaces.Segment.DOOR, 1, true), "nothing over a door");
        assertEquals(blind, BuildingFaces.storeyFeature(blind, 1, true), "nothing over a blind wall");
        assertEquals(blind, BuildingFaces.storeyFeature(w, 1, false), "works show no upper windows");
    }

    @Test
    void aWindowsHurtFollowsItsFrameAndAGoneOneIsShotOut() {
        assertEquals(0f, BuildingFaces.windowHurt(40, 40), 1e-6f);
        assertEquals(.5f, BuildingFaces.windowHurt(20, 40), 1e-6f);
        assertEquals(1f, BuildingFaces.windowHurt(0, 40), 1e-6f);
        assertEquals(1f, BuildingFaces.windowHurt(0, 0), 1e-6f, "no frame stands");
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

    @Test
    void aFaceStandsOnItsWallsOuterSideAndItsCornersReachTheWallsCorners() {
        // The 2x2 block's roof stops 0.3 inside its walls' outer sides (3.3 to 4.7); the walls stand from 3 to 5.
        float[] segs = BuildingFaces.segments(BuildingFaces.outline(CELLS, GRID_W, BuildingFacesTest::rects), GRID_W);
        assertEquals(8 * BuildingFaces.SEG, segs.length, "two pieces a side");
        int corners = 0;
        for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
            for (int k = 8; k < 12; k++) {
                float v = segs[e + k];
                assertTrue(near(v, 3f) || near(v, 4f) || near(v, 5f), "the foot on a cell boundary: " + v);
            }
            float nx = segs[e + 4], ny = segs[e + 5];
            float across = nx != 0f ? segs[e + 8] : segs[e + 9];
            assertEquals(nx + ny > 0f ? 5f : 3f, across, 1e-4f, "the foot on the wall's outer side");
            corners += Integer.bitCount((int) segs[e + 7]);
        }
        assertEquals(0, corners, "a run under 1.5 cells marks no corner post");
    }

    @Test
    void anInsideCornersFootDrawsBackAndAnOutsideOnesReachesOut() {
        // An L: a north run whose west end turns down (outside corner) and east end turns up (inside corner).
        float[] outline = {3.3f, 5.7f, 7f, 5.7f, 0f, 1f,
                3.3f, 2f, 3.3f, 5.7f, -1f, 0f,
                7f, 5.7f, 7f, 9f, -1f, 0f};
        float[] segs = BuildingFaces.segments(outline, GRID_W);
        assertEquals(3f, segs[8], 1e-4f, "the outside corner reaches the wall's corner");
        assertEquals(6f, segs[9], 1e-4f);
        assertEquals(BuildingFaces.CORNER_START, (int) segs[7] & BuildingFaces.CORNER_START);
        int last = 3 * BuildingFaces.SEG;
        assertEquals(7f, segs[last + 10], 1e-4f, "the inside corner's end stays on the boundary it already meets");
        assertEquals(0, (int) segs[last + 7] & BuildingFaces.CORNER_END, "no post at an inside corner");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /**
     * Zoomed out, a face far from the view's centre stands tall on narrow cells: it is dressed by its storeys' height
     * on screen, so it is never a blank slab; a face both narrow and short stays its shaded material.
     */
    @Test
    void aFaceTallOnScreenIsDressedHoweverNarrowItsCells() {
        assertTrue(BuildingFaces.detailed(BuildingFaces.DETAIL_CELL_PX, 0f), "wide cells");
        assertTrue(BuildingFaces.detailed(5f, BuildingFaces.DETAIL_STOREY_PX), "narrow cells, tall storeys");
        assertFalse(BuildingFaces.detailed(5f, 3f), "narrow and short");
    }

    /**
     * The eye stands at its own height at play zoom and closer, and rises with the view past it, so a building at the
     * view's edge leans about as many cells zoomed all the way out as at play zoom, where a camera that stayed put
     * would lean it four times as far: zoomed out, buildings no longer grow taller against their own footprints.
     */
    @Test
    void theEyeRisesWithTheViewSoABuildingLeansAlikeAtEveryZoom() {
        assertEquals(1f, BuildingFaces.rise(BuildingFaces.PLAY_CELLS * .5f), 1e-6f, "closer than play zoom");
        assertEquals(1f, BuildingFaces.rise(BuildingFaces.PLAY_CELLS), 1e-6f, "play zoom");
        assertEquals(4f, BuildingFaces.rise(BuildingFaces.PLAY_CELLS * 4f), 1e-6f, "the whole map");
        float meters = BuildingFaces.HEIGHTS[BuildingFaces.TALL];
        float edge = BuildingFaces.PLAY_CELLS / 2f, wide = BuildingFaces.PLAY_CELLS * 4f;
        // Cells a roof at the view's edge stands off its footprint.
        float atPlay = edge * (BuildingFaces.lift(meters, BuildingFaces.rise(BuildingFaces.PLAY_CELLS)) - 1f);
        float risen = 4f * edge * (BuildingFaces.lift(meters, BuildingFaces.rise(wide)) - 1f);
        float stayed = 4f * edge * (BuildingFaces.lift(meters) - 1f);
        assertTrue(risen <= atPlay && risen > .85f * atPlay, "about as far as at play zoom: " + risen + " " + atPlay);
        assertEquals(4f * atPlay, stayed, 1e-3f, "a camera that stayed put leans it four times as far");
    }

    /** At night about a third of the windows are lit, each by its place, so the same window stays lit frame to frame. */
    @Test
    void aboutAThirdOfTheWindowsAreLitAtNight() {
        int lit = 0;
        for (int seed = 0; seed < 2000; seed++) {
            if (FacePainter.lit(seed)) lit++;
            assertEquals(FacePainter.lit(seed), FacePainter.lit(seed));
        }
        assertTrue(lit > 500 && lit < 700, "lit " + lit);
    }
}
