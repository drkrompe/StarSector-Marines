package com.dillon.starsectormarines.battle.ground;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Math validation for the CSC-subset Reeds-Shepp solver. We can't test
 * "shortest is actually shortest" without enumerating the missing CCC
 * families, but we can validate:
 * <ul>
 *   <li>Identity — start = goal returns a zero-length path.</li>
 *   <li>Pure-straight — a goal directly in front with same heading uses
 *       (close to) all straight segment.</li>
 *   <li>Sample endpoints — sampling at distance 0 returns start, sampling
 *       at total length returns goal (within float tolerance).</li>
 *   <li>Length consistency — total length is monotonic in goal distance
 *       along the straight direction.</li>
 * </ul>
 */
public class ReedsSheppTest {

    private static final float R = 1.5f;   // truck-ish min turn radius
    private static final float EPS = 1e-3f;

    @Test
    public void identityReturnsZeroLengthPath() {
        Pose p = new Pose(3.0f, 5.0f, 45.0f);
        ReedsShepp.Path path = ReedsShepp.shortest(p, p, R);
        assertNotNull(path);
        assertEquals(0.0f, path.lengthUnits, EPS);
    }

    @Test
    public void straightForwardUsesStraightSegment() {
        Pose start = new Pose(0, 0, 0);    // facing +Y in our convention
        Pose goal  = new Pose(0, 10, 0);   // 10 cells north, same heading
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
        assertNotNull(path);
        // Expect close to pure-straight: total path length should be close
        // to 10 cells (Euclidean distance). Any arc bows the path out.
        assertEquals(10.0f, path.lengthCells(R), 0.1f);
    }

    @Test
    public void samplingAtZeroReturnsStart() {
        Pose start = new Pose(2, 3, 30);
        Pose goal = new Pose(8, 6, -10);
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
        assertNotNull(path);
        Pose s0 = ReedsShepp.sample(start, R, path, 0f);
        assertEquals(start.x, s0.x, EPS);
        assertEquals(start.y, s0.y, EPS);
        assertEquals(start.facingDeg, s0.facingDeg, EPS);
    }

    @Test
    public void samplingAtTotalLengthReachesGoal() {
        Pose start = new Pose(0, 0, 0);
        Pose goal = new Pose(5, 4, 90);
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
        assertNotNull(path);
        Pose end = ReedsShepp.sample(start, R, path, path.lengthCells(R));
        assertEquals(goal.x, end.x, 0.01f);
        assertEquals(goal.y, end.y, 0.01f);
        // Facing equality modulo 360°.
        float dAng = ((end.facingDeg - goal.facingDeg + 540) % 360) - 180;
        assertTrue(Math.abs(dAng) < 0.5f, "facing diff " + dAng);
    }

    @Test
    public void lengthIncreasesWithDistance() {
        Pose start = new Pose(0, 0, 0);
        ReedsShepp.Path pNear = ReedsShepp.shortest(start, new Pose(0, 5, 0), R);
        ReedsShepp.Path pFar  = ReedsShepp.shortest(start, new Pose(0, 20, 0), R);
        assertNotNull(pNear);
        assertNotNull(pFar);
        assertTrue(pFar.lengthCells(R) > pNear.lengthCells(R));
    }

    @Test
    public void backToBackHeadingChangeUsesCsc() {
        // 90° heading change with modest displacement — natural LSR.
        Pose start = new Pose(0, 0, 0);    // facing +Y
        Pose goal  = new Pose(5, 5, 90);   // 5 NE of start, facing west
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
        assertNotNull(path);
        // Sanity: total length is at least the straight-line distance.
        float euclid = (float) Math.sqrt(5*5 + 5*5);
        assertTrue(path.lengthCells(R) >= euclid - EPS, "path " + path.lengthCells(R) + " >= " + euclid);
    }

    @Test
    public void cccTightUturn() {
        Pose start = new Pose(0, 0, 0);
        Pose goal  = new Pose(2, 0, 180);
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
        assertNotNull(path, "CCC family should find a path for tight U-turn");
        Pose end = ReedsShepp.sample(start, R, path, path.lengthCells(R));
        assertEquals(goal.x, end.x, 0.05f);
        assertEquals(goal.y, end.y, 0.05f);
    }

    @Test
    public void cccSamplingEndpointMatchesGoal() {
        Pose start = new Pose(5, 5, 0);
        Pose goal  = new Pose(5, 8, 150);
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
        assertNotNull(path);
        Pose end = ReedsShepp.sample(start, R, path, path.lengthCells(R));
        assertEquals(goal.x, end.x, 0.02f);
        assertEquals(goal.y, end.y, 0.02f);
        float dAng = ((end.facingDeg - goal.facingDeg + 540) % 360) - 180;
        assertTrue(Math.abs(dAng) < 0.5f, "facing diff " + dAng);
    }

    @Test
    public void plannerHandlesTightGeometry() {
        Pose start = new Pose(0, 0, 0);
        float[][] goals = {
                {1, 1, 180}, {0, 2, 90}, {-1, 1, 270}, {0.5f, 0.5f, 135}
        };
        for (float[] g : goals) {
            Pose goal = new Pose(g[0], g[1], g[2]);
            ReedsShepp.Path path = ReedsShepp.shortest(start, goal, R);
            assertNotNull(path, "Should find path to (" + g[0] + "," + g[1] + "," + g[2] + ")");
        }
    }
}
