package com.dillon.starsectormarines.battle.world.gen.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawn roads are joined into one network along the road under them, and nowhere else. */
class RoadJoinsTest {

    private static final int W = 40, H = 30;

    /** An L of road five cells wide: along y 3..7 from x 3 to 33, then up x 29..33 to y 26. */
    private static boolean[][] ell() {
        boolean[][] road = new boolean[W][H];
        for (int x = 3; x <= 33; x++) for (int y = 3; y <= 7; y++) road[x][y] = true;
        for (int x = 29; x <= 33; x++) for (int y = 3; y <= 26; y++) road[x][y] = true;
        return road;
    }

    private static void paint(boolean[][] cells, int x0, int y0, int x1, int y1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) cells[x][y] = true;
    }

    @Test
    void twoPiecesOnOneRoadAreJoinedAlongIt() {
        boolean[][] road = ell();
        boolean[][] drawn = new boolean[W][H];
        paint(drawn, 3, 4, 10, 6);      // the west end of the road
        paint(drawn, 30, 20, 32, 26);   // the north end
        List<List<int[]>> paths = RoadJoins.paths(passable(road, drawn), drawn);
        assertEquals(1, paths.size(), "two pieces take one join");
        List<int[]> path = paths.get(0);
        assertTrue(drawn[path.get(0)[0]][path.get(0)[1]], "the join starts on a piece");
        int[] end = path.get(path.size() - 1);
        assertTrue(drawn[end[0]][end[1]], "the join ends on a piece");
        for (int i = 0; i < path.size(); i++) {
            int[] c = path.get(i);
            assertTrue(road[c[0]][c[1]] || drawn[c[0]][c[1]], "the join left the road at " + c[0] + "," + c[1]);
            if (i > 0) {
                int[] p = path.get(i - 1);
                assertEquals(1, Math.abs(p[0] - c[0]) + Math.abs(p[1] - c[1]), "the join skips a cell");
            }
        }
        assertEquals(1, RoadJoins.joins(road, drawn, 1L).stream().map(RoadPlan.Route::width).distinct().count());
    }

    @Test
    void theJoinKeepsToTheMiddleOfTheRoad() {
        boolean[][] road = ell();
        boolean[][] drawn = new boolean[W][H];
        paint(drawn, 3, 4, 6, 6);
        paint(drawn, 24, 4, 27, 6);
        for (int[] c : RoadJoins.paths(passable(road, drawn), drawn).get(0)) {
            assertEquals(5, c[1], "the join ran along the road's edge at " + c[0] + "," + c[1]);
        }
    }

    @Test
    void piecesNoRoadJoinsStayApart() {
        boolean[][] road = new boolean[W][H];
        paint(road, 2, 2, 12, 6);
        paint(road, 25, 20, 35, 24);
        boolean[][] drawn = new boolean[W][H];
        paint(drawn, 3, 3, 11, 5);
        paint(drawn, 26, 21, 34, 23);
        assertTrue(RoadJoins.joins(road, drawn, 1L).isEmpty(), "a join was drawn across ground with no road on it");
    }

    @Test
    void oneDrawnNetworkTakesNoJoin() {
        boolean[][] road = ell();
        boolean[][] drawn = new boolean[W][H];
        paint(drawn, 3, 4, 32, 6);
        paint(drawn, 30, 4, 32, 26);
        assertTrue(RoadJoins.joins(road, drawn, 1L).isEmpty());
    }

    @Test
    void aJoinRunsAlongTheAxesAndTurnsCleanly() {
        List<int[]> ell = new ArrayList<>();
        for (int x = 0; x <= 10; x++) ell.add(new int[]{x, 0});
        for (int y = 1; y <= 6; y++) ell.add(new int[]{10, y});
        List<List<int[]>> runs = RoadJoins.runs(ell);
        assertEquals(2, runs.size(), "an L is two runs");
        for (List<int[]> run : runs) {
            int[] a = run.get(0), b = run.get(run.size() - 1);
            assertTrue(a[0] == b[0] || a[1] == b[1], "a run left the grid's axes");
        }
    }

    @Test
    void aJoinAcrossOpenGroundHasAPlannersBendsAndOneWidth() {
        // A broad paved square: the two pieces lie off both axes of each other.
        boolean[][] road = new boolean[W][H];
        paint(road, 1, 1, W - 2, H - 2);
        boolean[][] drawn = new boolean[W][H];
        paint(drawn, 3, 3, 6, 6);
        paint(drawn, 30, 20, 34, 24);
        List<List<int[]>> paths = RoadJoins.paths(passable(road, drawn), drawn);
        assertEquals(1, paths.size());
        assertTrue(BendFloodTest.bends(paths.get(0)) <= 2,
                "the join stepped across the square: " + BendFloodTest.bends(paths.get(0)) + " bends");
        for (RoadPlan.Route r : RoadJoins.joins(road, drawn, 1L)) {
            assertEquals(RoadJoins.WIDTH, r.width(), "a join swelled or narrowed with its ground");
            RoadPlan.Point a = r.points().get(0), b = r.points().get(r.points().size() - 1);
            assertTrue(a.x() == b.x() || a.y() == b.y(), "a join was drawn as a diagonal stroke");
        }
    }

    private static boolean[][] passable(boolean[][] road, boolean[][] drawn) {
        boolean[][] out = new boolean[W][H];
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) out[x][y] = road[x][y] || drawn[x][y];
        return out;
    }
}
