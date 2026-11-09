package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictLegRigTest {

    private static final DerelictLegRig.Solid OPEN = (x, y) -> false;
    private static final float DT = 1f / 30f;
    /** Facing +x (sprite angle -90). */
    private static final float EAST = -90f;

    /** Walks the rig east at {@code speed} for {@code ticks}, calling {@code each} after every tick. */
    private static void walk(DerelictLegRig rig, float speed, int ticks, DerelictLegRig.Solid solid, Runnable each) {
        for (int tick = 0; tick < ticks; tick++) {
            rig.advance(10f + tick * speed * DT, 50.5f, EAST, DT, solid);
            each.run();
        }
    }

    @Test
    void atAWalkTheLegsLiftOneAtATimeInAFourBeatSequence() {
        DerelictLegRig rig = new DerelictLegRig();
        List<Integer> liftoffs = new ArrayList<>();
        boolean[] was = new boolean[4];
        walk(rig, 0.7f, 600, OPEN, () -> {
            int lifted = 0;
            for (int i = 0; i < 4; i++) {
                if (rig.stepping(i)) lifted++;
                if (rig.stepping(i) && !was[i]) liftoffs.add(i);
                was[i] = rig.stepping(i);
            }
            assertTrue(lifted <= 1, "three feet stand at a walk");
        });
        assertEquals(DerelictLegRig.Gait.WALK, rig.gait());
        assertTrue(liftoffs.size() >= 8, "it steps: " + liftoffs);
        // Back, front on one side, then back, front on the other: 1, 0, 2, 3 and round again.
        int[] order = {1, 0, 2, 3};
        int start = 0;
        while (order[start] != liftoffs.get(0)) start++;
        for (int k = 0; k < liftoffs.size(); k++) assertEquals(order[(start + k) % 4], liftoffs.get(k));
    }

    @Test
    void atARunTheDiagonalPairsTrotAndLeaveTheGroundBetweenStrides() {
        DerelictLegRig rig = new DerelictLegRig();
        boolean[] airborne = {false};
        walk(rig, 2.6f, 300, OPEN, () -> {
            if (rig.gait() != DerelictLegRig.Gait.RUN) return;
            int lifted = 0;
            for (int i = 0; i < 4; i++) if (rig.stepping(i)) lifted++;
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (rig.pair(i) == rig.pair(j) && lifted < 4) assertEquals(rig.stepping(i), rig.stepping(j));
                }
            }
            if (lifted == 4) airborne[0] = true;
        });
        assertEquals(DerelictLegRig.Gait.RUN, rig.gait());
        assertTrue(airborne[0], "the run has a moment with every foot off the ground");
    }

    @Test
    void aStandingFootStaysExactlyWhereItLanded() {
        DerelictLegRig rig = new DerelictLegRig();
        float[] lastX = new float[4];
        float[] lastY = new float[4];
        boolean[] was = {true, true, true, true};
        walk(rig, 0.7f, 400, OPEN, () -> {
            for (int i = 0; i < 4; i++) {
                if (!rig.stepping(i) && !was[i]) {
                    assertEquals(lastX[i], rig.footX(i), 1e-6f);
                    assertEquals(lastY[i], rig.footY(i), 1e-6f);
                }
                lastX[i] = rig.footX(i);
                lastY[i] = rig.footY(i);
                was[i] = rig.stepping(i);
            }
        });
    }

    @Test
    void eachLegGripsAWallThatIsCloseAndStandsOnTheFloorWhereNoneIs() {
        // A wall along the north side only (y >= 52); open floor to the south.
        DerelictLegRig.Solid north = (x, y) -> y >= 52;
        DerelictLegRig rig = new DerelictLegRig();
        rig.advance(20f, 50.5f, EAST, DT, north);
        int gripping = 0;
        for (int i = 0; i < 4; i++) {
            boolean northSide = rig.footY(i) > 50.5f;
            assertEquals(northSide, rig.onWall(i), "leg " + i + " grips only on the walled side");
            if (rig.onWall(i)) {
                gripping++;
                assertTrue(rig.footY(i) > 51.8f && rig.footY(i) < 52f, "up against the face");
            } else {
                float dx = rig.footX(i) - 20f;
                float dy = rig.footY(i) - 50.5f;
                assertEquals(DerelictLegRig.REST_CELLS, (float) Math.sqrt(dx * dx + dy * dy), 1e-3f);
            }
        }
        assertEquals(2, gripping);
    }

    @Test
    void inTheOpenNoLegGrips() {
        DerelictLegRig rig = new DerelictLegRig();
        rig.advance(10f, 10f, 0f, DT, OPEN);
        for (int i = 0; i < 4; i++) assertFalse(rig.onWall(i));
    }

    @Test
    void aTorsoThatJumpsStandsFresh() {
        DerelictLegRig rig = new DerelictLegRig();
        rig.advance(10f, 10f, 0f, DT, OPEN);
        rig.advance(40f, 10f, 0f, DT, OPEN);
        for (int i = 0; i < 4; i++) {
            float dx = rig.footX(i) - 40f;
            float dy = rig.footY(i) - 10f;
            assertEquals(DerelictLegRig.REST_CELLS, (float) Math.sqrt(dx * dx + dy * dy), 1e-3f);
            assertFalse(rig.stepping(i));
        }
    }

    @Test
    void theBodySwaysAwayFromALiftedLegAndBouncesHarderAtARun() {
        DerelictLegRig walker = new DerelictLegRig();
        float[] sway = {0f};
        float[] walkHeight = {0f, 0f};
        walk(walker, 0.7f, 600, OPEN, () -> {
            for (int i = 0; i < 4; i++) {
                if (walker.lift(i) < 0.9f) continue;
                // Sideways from the path (the path runs along y = 50.5), against the lifted leg's side.
                float side = Math.signum(walker.footY(i) - 50.5f);
                sway[0] += -(walker.bodyY() - 50.5f) * side;
            }
            walkHeight[0] = Math.min(walkHeight[0], walker.bodyHeight());
            walkHeight[1] = Math.max(walkHeight[1], walker.bodyHeight());
        });
        assertTrue(sway[0] > 0f, "the body leans off a lifted leg: " + sway[0]);
        assertTrue(walkHeight[0] < 0f, "it dips as a leg lifts");
        DerelictLegRig runner = new DerelictLegRig();
        float[] runHeight = {0f, 0f};
        walk(runner, 2.6f, 300, OPEN, () -> {
            runHeight[0] = Math.min(runHeight[0], runner.bodyHeight());
            runHeight[1] = Math.max(runHeight[1], runner.bodyHeight());
        });
        assertTrue(runHeight[1] - runHeight[0] > walkHeight[1] - walkHeight[0], "the run bounces harder");
    }

    @Test
    void aLostLegNeverLiftsItsFootIsDraggedAndTheOthersStillStep() {
        DerelictLegRig rig = new DerelictLegRig();
        rig.advance(10f, 50.5f, EAST, DT, OPEN);
        rig.legsLost(1 << 2, false);
        boolean[] lifted = new boolean[4];
        boolean[] dragged = {false};
        walk(rig, 0.7f, 600, OPEN, () -> {
            for (int i = 0; i < 4; i++) if (rig.stepping(i)) lifted[i] = true;
            if (rig.dragging(2)) dragged[0] = true;
        });
        assertFalse(lifted[2], "the lost leg never lifts");
        assertTrue(lifted[0] && lifted[1] && lifted[3], "the other three still step");
        assertTrue(dragged[0], "its foot is dragged along");
        assertFalse(rig.onWall(2));
    }

    @Test
    void aLostLegGripsNoWallEvenBesideOne() {
        DerelictLegRig rig = new DerelictLegRig();
        DerelictLegRig.Solid north = (x, y) -> y >= 52;
        rig.legsLost(0b1111, false);
        rig.advance(10f, 50.5f, EAST, DT, north);
        for (int i = 0; i < 4; i++) assertFalse(rig.onWall(i), "leg " + i);
    }

    @Test
    void theBodySagsOnThreeLegsAndSinksWhenDowned() {
        DerelictLegRig whole = new DerelictLegRig();
        DerelictLegRig limping = new DerelictLegRig();
        DerelictLegRig downed = new DerelictLegRig();
        limping.legsLost(1, false);
        downed.legsLost(0b11, true);
        for (int t = 0; t < 120; t++) {
            whole.advance(10f, 50.5f, EAST, DT, OPEN);
            limping.advance(10f, 50.5f, EAST, DT, OPEN);
            downed.advance(10f, 50.5f, EAST, DT, OPEN);
        }
        assertTrue(limping.bodyHeight() < whole.bodyHeight() - 0.05f, "it sags");
        assertEquals(-DerelictLegRig.DOWNED_CELLS, downed.bodyHeight(), 0.02f, "downed, the torso is on the floor");
    }
}
