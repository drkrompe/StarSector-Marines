package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1 coverage for {@link SeparationSystem} — tests 1, 2, 5, 6 of the story
 * doc's test plan ({@code roadmap/continuous-positions/stories/separation-steering.md}).
 * Tests 3/4 (mass asymmetry, immovables) are S2.
 *
 * <p>Every test constructs a {@link SeparationSystem} directly and drives it
 * with explicit {@code tick(dt)} calls rather than going through
 * {@code BattleSimulation.advance} — units spawn without a squad (idle,
 * per {@code GoapInfantryBehavior}'s "solo units idle" contract) so a full
 * tick loop would add nothing but win-check/GOAP bookkeeping noise. {@code
 * UnitRosterService.spawn} mirrors new units into the spatial index inline
 * ({@code UnitSpatialIndex.add}), so the index is live-correct without a
 * tick-loop rebuild.
 */
public class SeparationSystemTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(w, h));
    }

    private static SeparationSystem separationFor(BattleSimulation sim) {
        return new SeparationSystem(sim.getRoster(), sim.getUnitIndex(), sim.getGrid());
    }

    private static long[] spawnStack(BattleSimulation sim, int count, int cellX, int cellY) {
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, cellX, cellY));
        }
        return ids;
    }

    /**
     * Test 1 — stack relax: 8 marines spawn on one point in open ground.
     * Over 2 sim-seconds every pair separates to at least the sum of their
     * radii, and no single tick moves a unit farther than the
     * {@code MAX_PUSH_SPEED * dt} clamp allows (relaxation, not a teleport pop).
     */
    @Test
    public void stackOfMarinesRelaxesGraduallyToNonOverlap() {
        BattleSimulation sim = openArena(20, 20);
        SeparationSystem separation = separationFor(sim);
        long[] marines = spawnStack(sim, 8, 10, 10);

        float maxStepPerTick = SeparationSystem.MAX_PUSH_SPEED * BattleSimulation.TICK_DT + 1e-4f;
        float[] prevX = new float[marines.length];
        float[] prevY = new float[marines.length];
        for (int i = 0; i < marines.length; i++) {
            prevX[i] = sim.world().x(marines[i]);
            prevY[i] = sim.world().y(marines[i]);
        }

        int ticks = Math.round(2f / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) {
            separation.tick(BattleSimulation.TICK_DT);
            for (int i = 0; i < marines.length; i++) {
                float x = sim.world().x(marines[i]);
                float y = sim.world().y(marines[i]);
                float dx = x - prevX[i];
                float dy = y - prevY[i];
                float step = (float) Math.sqrt(dx * dx + dy * dy);
                assertTrue(step <= maxStepPerTick,
                        "tick " + t + " unit " + i + " jumped " + step + " cells (cap " + maxStepPerTick + ")");
                prevX[i] = x;
                prevY[i] = y;
            }
        }

        float minSeparation = 2f * UnitType.MARINE.radius;
        for (int i = 0; i < marines.length; i++) {
            for (int j = i + 1; j < marines.length; j++) {
                float dx = sim.world().x(marines[i]) - sim.world().x(marines[j]);
                float dy = sim.world().y(marines[i]) - sim.world().y(marines[j]);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                assertTrue(dist >= minSeparation - 1e-3f,
                        "pair (" + i + "," + j + ") still overlapping: dist=" + dist + " < " + minSeparation);
            }
        }
    }

    /**
     * Test 2 — wall slide: 4 marines stack one cell short of a wall run.
     * After relaxing, every unit is on a walkable cell and none crossed the
     * wall line — the guard's X-only/Y-only slide fallback keeps the push
     * from clipping through.
     */
    @Test
    public void wallAdjacentStackSlidesAndStaysWalkable() {
        int w = 20, h = 20;
        int wallColumn = 15;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x == wallColumn) continue; // leave this column at its default (non-walkable)
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(w, h));
        SeparationSystem separation = separationFor(sim);
        long[] marines = spawnStack(sim, 4, wallColumn - 1, 10);

        int ticks = Math.round(2f / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) separation.tick(BattleSimulation.TICK_DT);

        for (long m : marines) {
            int cx = (int) Math.floor(sim.world().x(m));
            int cy = (int) Math.floor(sim.world().y(m));
            assertTrue(grid.isWalkable(cx, cy),
                    "unit " + m + " ended on a non-walkable cell (" + cx + "," + cy + ")");
            assertTrue(cx < wallColumn,
                    "unit " + m + " crossed the wall line: cellX=" + cx);
        }
    }

    /**
     * Test 5 — determinism, including the coincident-point tiebreak (every
     * marine here starts on the exact same point): two identically-built
     * runs land on bit-identical final positions. No RNG anywhere in the
     * accumulate/apply pass.
     */
    @Test
    public void identicalSetupIncludingCoincidentPointsIsDeterministic() {
        float[][] runA = runStackAndCapture();
        float[][] runB = runStackAndCapture();
        assertEquals(runA.length, runB.length);
        for (int i = 0; i < runA.length; i++) {
            assertEquals(runA[i][0], runB[i][0], 0f, "unit " + i + " x diverged between runs");
            assertEquals(runA[i][1], runB[i][1], 0f, "unit " + i + " y diverged between runs");
        }
    }

    private static float[][] runStackAndCapture() {
        BattleSimulation sim = openArena(20, 20);
        SeparationSystem separation = separationFor(sim);
        long[] marines = spawnStack(sim, 8, 10, 10);
        int ticks = Math.round(2f / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) separation.tick(BattleSimulation.TICK_DT);
        float[][] out = new float[marines.length][2];
        for (int i = 0; i < marines.length; i++) {
            out[i][0] = sim.world().x(marines[i]);
            out[i][1] = sim.world().y(marines[i]);
        }
        return out;
    }

    /**
     * Test 6 — non-interference: a lone mover crossing an empty map has a
     * bit-identical trajectory whether {@link SeparationSystem} is ticked
     * alongside it or not. With no other participant in range, every
     * accumulate pass finds zero overlap, so the apply pass never writes —
     * the same "system present" state should never diverge from the plain
     * {@code MovementService}-only path.
     */
    @Test
    public void loneMoverTrajectoryUnaffectedBySeparationSystem() {
        BattleSimulation withoutSeparation = openArena(20, 20);
        long baseline = withoutSeparation.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 2, 2));
        withoutSeparation.setPath(baseline, new int[]{2, 2, 15, 2});

        BattleSimulation withSeparation = openArena(20, 20);
        SeparationSystem separation = separationFor(withSeparation);
        long shadowed = withSeparation.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 2, 2));
        withSeparation.setPath(shadowed, new int[]{2, 2, 15, 2});

        int ticks = 200;
        for (int t = 0; t < ticks; t++) {
            withoutSeparation.movement().beginTick(BattleSimulation.TICK_DT);
            withoutSeparation.advanceMovement(baseline);

            withSeparation.movement().beginTick(BattleSimulation.TICK_DT);
            withSeparation.advanceMovement(shadowed);
            separation.tick(BattleSimulation.TICK_DT);

            assertEquals(withoutSeparation.world().x(baseline), withSeparation.world().x(shadowed), 0f,
                    "tick " + t + " x diverged");
            assertEquals(withoutSeparation.world().y(baseline), withSeparation.world().y(shadowed), 0f,
                    "tick " + t + " y diverged");
        }
        assertTrue(withoutSeparation.movement().settled(baseline), "baseline should have finished its path");
    }
}
