package com.dillon.starsectormarines.battle.sim;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadTrafficSolverTest {
    private static final float DT = 1f / 30f;

    @Test
    void coincidentParallelSquadsSpreadWithoutSlowing() {
        Frame frame = new Frame(2);
        frame.solve();
        assertTrue(frame.offset[0] < 0f);
        assertTrue(frame.offset[1] > 0f);
        assertEquals(1f, frame.speed[0]);
        assertEquals(1f, frame.speed[1]);
        assertEquals(2, frame.solver.activeHints());
    }

    @Test
    void sideRemainsStableAfterTinyCenterCrossing() {
        Frame frame = new Frame(2);
        for (int n = 0; n < 15; n++) frame.solve();
        float previous = frame.offset[0];
        frame.y[0] = 0.1f;
        frame.solve();
        assertTrue(frame.offset[0] < previous);
    }

    @Test
    void constrainedTerrainCompressesAndAssignsOneYieldingSquad() {
        Frame frame = new Frame(2);
        for (int n = 0; n < 15; n++) frame.solve();
        Arrays.fill(frame.open, 0f);
        frame.solve();
        assertEquals(0f, frame.offset[0]);
        assertEquals(0f, frame.offset[1]);
        assertEquals(1f, frame.speed[0]);
        assertEquals(SquadTrafficSolver.MIN_SPEED_SCALE, frame.speed[1]);
    }

    @Test
    void squadAlreadyAheadInitiallyKeepsItsProgress() {
        Frame frame = new Frame(2);
        Arrays.fill(frame.open, 0f);
        frame.x[1] = 1f;
        frame.solve();
        assertEquals(SquadTrafficSolver.MIN_SPEED_SCALE, frame.speed[0]);
        assertEquals(1f, frame.speed[1]);
    }

    @Test
    void sustainedYieldingEventuallyReceivesPriorityWithoutStoppingEitherSquad() {
        Frame frame = new Frame(2);
        Arrays.fill(frame.open, 0f);
        int[] fullSpeed = new int[2];
        int longestRun = 0, currentRun = 0;
        boolean previousYield = false;
        for (int n = 0; n < 240; n++) {
            frame.solve();
            for (int i = 0; i < 2; i++) {
                assertTrue(frame.speed[i] >= SquadTrafficSolver.MIN_SPEED_SCALE);
                if (frame.speed[i] == 1f) fullSpeed[i]++;
            }
            boolean yielding = frame.speed[1] < 1f;
            currentRun = yielding == previousYield ? currentRun + 1 : 1;
            longestRun = Math.max(longestRun, currentRun);
            previousYield = yielding;
        }
        assertTrue(fullSpeed[0] >= 45);
        assertTrue(fullSpeed[1] >= 45);
        assertTrue(longestRun >= 40, "Priority persists instead of alternating every tick");
    }

    @Test
    void corridorExitRestoresSpreadGradually() {
        Frame frame = new Frame(2);
        Arrays.fill(frame.open, 0f);
        frame.solve();
        Arrays.fill(frame.open, 1f);
        frame.solve();
        assertTrue(frame.offset[1] > 0f && frame.offset[1] < 0.1f);
        assertEquals(1f, frame.speed[1]);
    }

    @Test
    void crossingTrafficYieldsEvenInOpenGroundWithoutInventingASidewaysRoute() {
        Frame frame = new Frame(2);
        frame.hx[1] = 0f;
        frame.hy[1] = 1f;
        frame.solve();
        assertEquals(1f, frame.speed[0]);
        assertEquals(SquadTrafficSolver.MIN_SPEED_SCALE, frame.speed[1]);
        assertEquals(0f, frame.offset[0]);
        assertEquals(0f, frame.offset[1]);
    }

    @Test
    void terrainOcclusionAndDifferentCoordinationGroupsHaveNoEffect() {
        Frame frame = new Frame(2);
        frame.visibility = (a, b) -> false;
        frame.solve();
        assertEquals(0, frame.solver.activeHints());
        frame.visibility = null;
        frame.groups[1] = 1;
        frame.solve();
        assertEquals(0, frame.solver.activeHints());
    }

    @Test
    void removingNeighborReleasesItsOffsetInsteadOfCruisingForever() {
        Frame frame = new Frame(2);
        for (int n = 0; n < 30; n++) frame.solve();
        assertTrue(frame.offset[1] > 0.5f);
        frame.x[1] = 100f;
        for (int n = 0; n < 240; n++) frame.solve();
        assertEquals(0f, frame.offset[0]);
        assertEquals(0f, frame.offset[1]);
    }

    @Test
    void zeroHeadingSuppressesOldHints() {
        Frame frame = new Frame(2);
        frame.offset[0] = 2f;
        frame.age[0] = 5f;
        frame.hx[0] = 0f;
        frame.solve();
        assertEquals(0f, frame.offset[0]);
        assertEquals(0f, frame.age[0]);
        assertEquals(1f, frame.speed[0]);
    }

    @Test
    void denseStackHasStrictCandidateAndOutputBounds() {
        Frame frame = new Frame(500);
        for (int n = 0; n < 20; n++) {
            frame.solve();
            assertTrue(frame.solver.candidateVisits() <= 500 * SquadTrafficSolver.MAX_CANDIDATE_VISITS);
            assertTrue(frame.solver.pairChecks() <= frame.solver.candidateVisits());
            for (int i = 0; i < 500; i++) {
                assertTrue(Float.isFinite(frame.offset[i]));
                assertTrue(Math.abs(frame.offset[i]) <= SquadTrafficSolver.MAX_LATERAL_OFFSET);
                assertTrue(frame.speed[i] >= SquadTrafficSolver.MIN_SPEED_SCALE);
            }
        }
    }

    @Test
    void sparseInputOrderDoesNotChooseTheSideOrPriority() {
        Frame forward = new Frame(3);
        Frame reverse = new Frame(3);
        forward.x[1] = 1f;
        forward.y[2] = 2f;
        for (int i = 0; i < 3; i++) {
            reverse.ids[2 - i] = forward.ids[i];
            reverse.x[2 - i] = forward.x[i];
            reverse.y[2 - i] = forward.y[i];
        }
        for (int n = 0; n < 20; n++) {
            forward.solve();
            reverse.solve();
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(forward.offset[i], reverse.offset[2 - i], 0.00001f);
            assertEquals(forward.speed[i], reverse.speed[2 - i]);
        }
    }

    @Test
    void fourSquadsInColumnDevelopDistinctLateralBandsWithoutSlowing() {
        Frame frame = new Frame(4);
        Arrays.fill(frame.radius, 3f);
        for (int i = 0; i < 4; i++) frame.x[i] = 5f * i;
        for (int n = 0; n < 180; n++) {
            frame.solve();
            for (int i = 0; i < 4; i++) {
                // Ideal tracking isolates the preference generator from the
                // adapter's terrain and locomotion; played scenes cover those.
                frame.y[i] = frame.offset[i];
                frame.x[i] += DT;
                assertEquals(1f, frame.speed[i]);
            }
        }
        assertTrue(frame.offset[3] - frame.offset[0] > 2f);
        for (int i = 1; i < 4; i++) {
            assertTrue(frame.offset[i] - frame.offset[i - 1] > 0.1f,
                    "Center squads must not collapse into one common lane");
        }
    }

    @Test
    void negativeCoordinatesAndBucketEdgesStillFindNearbySquads() {
        Frame frame = new Frame(2);
        frame.x[0] = -0.1f;
        frame.x[1] = 0.1f;
        frame.y[0] = frame.y[1] = -16.1f;
        frame.solve();
        assertTrue(frame.offset[0] < 0f);
        assertTrue(frame.offset[1] > 0f);
    }

    private static final class Frame {
        final SquadTrafficSolver solver = new SquadTrafficSolver();
        final int count;
        final int[] ids, groups;
        final float[] x, y, hx, hy, radius, open, offset, age, speed;
        SquadTrafficSolver.PairVisibility visibility;

        Frame(int count) {
            this.count = count;
            ids = new int[count];
            groups = new int[count];
            x = new float[count];
            y = new float[count];
            hx = new float[count];
            hy = new float[count];
            radius = new float[count];
            open = new float[count];
            offset = new float[count];
            age = new float[count];
            speed = new float[count];
            for (int i = 0; i < count; i++) ids[i] = i + 1;
            Arrays.fill(hx, 1f);
            Arrays.fill(radius, 2f);
            Arrays.fill(open, 1f);
        }

        void solve() {
            solver.solve(count, ids, groups, x, y, hx, hy, radius, open, offset, age, speed, DT, visibility);
        }
    }
}
