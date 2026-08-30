package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechLanceOrder;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the soft-separation laws in
 * {@code continuous-positions-nouns.md}, plus a drone-hub immovability
 * regression and a production-tick-loop
 * wiring check (see {@link #droneHubNeverMovesWhileOverlappingMarineResolvesFully}
 * and {@link #separationRunsInsideTheProductionTickLoop}).
 *
 * <p>Most tests construct a {@link SeparationSystem} directly and drive it
 * with explicit {@code tick(dt)} calls rather than going through
 * {@code BattleSimulation.advance} — units spawn without a squad (idle,
 * per {@code GoapInfantryBehavior}'s "solo units idle" contract) so a full
 * tick loop would add nothing but win-check/GOAP bookkeeping noise. {@code
 * UnitRosterService.spawn} mirrors new units into the spatial index inline
 * ({@code UnitSpatialIndex.add}), so the index is live-correct without a
 * tick-loop rebuild. {@link #separationRunsInsideTheProductionTickLoop} is
 * the deliberate exception — it exists specifically to exercise the real
 * tick loop's wiring.
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
     * Test 2 — wall slide: 8 marines stack one cell short of a wall run.
     * After relaxing, every unit is on a walkable cell and none crossed the
     * wall line — the guard's X-only/Y-only slide fallback keeps the push
     * from clipping through. 8 units (not 4) is load-bearing: a 4-marine
     * stack's unconstrained packing radius never reaches the wall line, so
     * the walkability guard is never actually consulted with a failing
     * candidate cell and the test would stay green with the guard deleted.
     * The added "pressed against the wall" assertion checks the guard did
     * something, not just that units never got close enough to need it.
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
        long[] marines = spawnStack(sim, 8, wallColumn - 1, 10);

        int ticks = Math.round(2f / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) separation.tick(BattleSimulation.TICK_DT);

        boolean pressedAgainstWall = false;
        for (long m : marines) {
            float x = sim.world().x(m);
            int cx = (int) Math.floor(x);
            int cy = (int) Math.floor(sim.world().y(m));
            assertTrue(grid.isWalkable(cx, cy),
                    "unit " + m + " ended on a non-walkable cell (" + cx + "," + cy + ")");
            assertTrue(cx < wallColumn,
                    "unit " + m + " crossed the wall line: cellX=" + cx);
            if (x > wallColumn - UnitType.MARINE.radius) pressedAgainstWall = true;
        }
        assertTrue(pressedAgainstWall,
                "no unit pressed close enough to the wall to actually exercise the walkability guard");
    }

    @Test
    public void overlapResolutionCannotPushAUnitThroughAWindowEdge() {
        NavigationGrid grid = new NavigationGrid(2, 1);
        grid.setWalkableFloor(0, 0);
        grid.setWalkableFloor(1, 0);
        grid.placeEdgeBarrier(0, 0, Direction.E,
                SharedEdgeBarrier.Kind.WINDOW);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(2, 1));
        SeparationSystem separation = separationFor(sim);
        long left = sim.spawn(new EntitySpec(
                "left", Faction.MARINE, UnitType.MARINE, 0, 0));
        long againstPane = sim.spawn(new EntitySpec(
                "pane", Faction.MARINE, UnitType.MARINE, 0, 0));
        sim.world().setPos(left, 0.80f, 0.5f);
        sim.world().setPos(againstPane, 0.98f, 0.5f);
        sim.getUnitIndex().rebuild(sim.getRoster());

        separation.tick(BattleSimulation.TICK_DT);

        assertEquals(0.98f, sim.world().x(againstPane), 0f,
                "separation must not bypass a closed shared edge");
        assertTrue(sim.world().x(left) < 0.80f,
                "the unconstrained unit should still absorb its half of the push");
    }

    /**
     * S3 chokepoint regression: a twelve-marine, three-fire-team squad
     * converges from a room into a one-cell-wide passage, traverses it, and
     * fans back out into the room on the far side. This drives movement and
     * separation in their production order while rebuilding the unit-index
     * snapshot once per tick. Every
     * mover must make forward progress through both mouths and finish its
     * route on walkable ground; a separation/path tug that oscillates at a
     * doorway leaves at least one path unexhausted within the generous
     * 20-second budget.
     */
    @Test
    public void fireteamFlowsThroughOneCellChokepointWithoutOscillation() {
        int w = 24, h = 9;
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x <= 7; x++) grid.setWalkableFloor(x, y);
            for (int x = 16; x < w - 1; x++) grid.setWalkableFloor(x, y);
        }
        for (int x = 8; x <= 15; x++) grid.setWalkableFloor(x, 4);

        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(w, h));
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] marines = new long[12];
        for (int i = 0; i < marines.length; i++) {
            int startX = 2 + i % 4;
            int startY = 2 + i / 4;
            int destX = 18 + i % 4;
            int destY = 2 + i / 4;
            marines[i] = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, startX, startY)
                    .squad(squadId).fireTeam(i / Squad.FIRE_TEAM_SIZE));
            sim.setPath(marines[i], new int[]{
                    startX, startY,
                    7, 4,
                    8, 4,
                    15, 4,
                    16, 4,
                    destX, destY
            });
        }
        grid.setDoorway(8, 4, true);
        grid.setDoorway(15, 4, true);

        boolean[] enteredChoke = new boolean[marines.length];
        boolean[] exitedChoke = new boolean[marines.length];
        int[] entryRetreats = new int[marines.length];
        int[] exitRetreats = new int[marines.length];
        float[] previousX = new float[marines.length];
        for (int i = 0; i < marines.length; i++) {
            previousX[i] = sim.world().x(marines[i]);
        }
        int ticks = Math.round(20f / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) {
            sim.movement().beginTick(BattleSimulation.TICK_DT);
            sim.getUnitIndex().rebuild(sim.getRoster());
            for (long marine : marines) sim.advanceMovement(marine);
            separation.tick(BattleSimulation.TICK_DT);

            for (int i = 0; i < marines.length; i++) {
                int cx = sim.world().cellX(marines[i]);
                int cy = sim.world().cellY(marines[i]);
                assertTrue(grid.isWalkable(cx, cy),
                        "tick " + t + " unit " + i + " entered blocked cell (" + cx + "," + cy + ")");
                float x = sim.world().x(marines[i]);
                if (x >= 8.5f) enteredChoke[i] = true;
                if (x >= 16.5f) exitedChoke[i] = true;
                if (previousX[i] >= 8.5f && x < 8.5f) entryRetreats[i]++;
                if (previousX[i] >= 16.5f && x < 16.5f) exitRetreats[i]++;
                previousX[i] = x;
            }
        }

        for (int i = 0; i < marines.length; i++) {
            assertTrue(enteredChoke[i], "unit " + i + " never entered the chokepoint");
            assertTrue(exitedChoke[i], "unit " + i + " never exited the chokepoint");
            assertTrue(sim.movement().settled(marines[i]),
                    "unit " + i + " remained in a movement/separation tug after 20 seconds");
            assertTrue(sim.world().x(marines[i]) >= 17f,
                    "unit " + i + " was pushed back through the exit after finishing");
            assertTrue(entryRetreats[i] <= 1,
                    "unit " + i + " oscillated across the choke entrance " + entryRetreats[i] + " times");
            assertTrue(exitRetreats[i] <= 1,
                    "unit " + i + " oscillated across the choke exit " + exitRetreats[i] + " times");
        }
    }

    @Test
    public void threeFireTeamsReleaseFormationBeforeDoorway() {
        int width = 28;
        int height = 15;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (x != 13) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(13, 7);
        grid.setDoorway(13, 7, true);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, height));
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] marines = new long[12];
        float[] beforeX = new float[marines.length];
        float[] beforeY = new float[marines.length];
        for (int i = 0; i < marines.length; i++) {
            int team = i / Squad.FIRE_TEAM_SIZE;
            int startX = 5 + team * 2;
            int startY = 5 + i % Squad.FIRE_TEAM_SIZE;
            marines[i] = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, startX, startY)
                    .squad(squadId).fireTeam(team));
            sim.setPath(marines[i], new int[]{startX, startY, 13, 7, 23, 7});
            beforeX[i] = sim.world().x(marines[i]);
            beforeY[i] = sim.world().y(marines[i]);
        }
        sim.getUnitIndex().rebuild(sim.getRoster());

        separation.tick(BattleSimulation.TICK_DT);

        for (int i = 0; i < marines.length; i++) {
            assertEquals(beforeX[i], sim.world().x(marines[i]), 0f,
                    "formation should yield X authority before the portal for marine " + i);
            assertEquals(beforeY[i], sim.world().y(marines[i]), 0f,
                    "formation should yield Y authority before the portal for marine " + i);
        }
    }

    @Test
    public void threeFireTeamsReformSquadArcAfterConstraintClears() {
        BattleSimulation sim = openArena(36, 24);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] marines = new long[12];
        for (int i = 0; i < marines.length; i++) {
            int team = i / Squad.FIRE_TEAM_SIZE;
            int x = 9 + team * 2;
            int y = 9 + i % Squad.FIRE_TEAM_SIZE;
            marines[i] = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, x, y).squad(squadId).fireTeam(team));
            sim.setPath(marines[i], new int[]{x, y, 31, y});
        }
        sim.getUnitIndex().rebuild(sim.getRoster());

        int ticks = Math.round(6f / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            separation.tick(BattleSimulation.TICK_DT);
        }

        float firstTeamY = teamCentroidY(sim, marines, 0);
        float thirdTeamY = teamCentroidY(sim, marines, 2);
        assertTrue(thirdTeamY - firstTeamY >= 5f,
                "squad arc should reform in open ground; team-centroid gap="
                        + (thirdTeamY - firstTeamY));
    }

    @Test
    public void sharedDestinationRetainsArrivalHeadingLongEnoughToFinishFootprint() {
        BattleSimulation sim = openArena(32, 20);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] marines = new long[8];
        float[] offsetsX = {1f, 0f, -1f, 0f,
                0.7071f, -0.7071f, -0.7071f, 0.7071f};
        float[] offsetsY = {0f, 1f, 0f, -1f,
                0.7071f, 0.7071f, -0.7071f, -0.7071f};
        for (int i = 0; i < marines.length; i++) {
            marines[i] = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 20, 10).squad(squadId)
                    .fireTeam(i / Squad.FIRE_TEAM_SIZE));
            // Both teams share one centroid but every body begins outside
            // physical-overlap range, isolating arrival-formation authority
            // from the collision resolver's deterministic coincident push.
            sim.world().setPos(marines[i], 20.5f + offsetsX[i],
                    10.5f + offsetsY[i]);
            sim.setPath(marines[i], new int[]{5, 10, 20, 10});
            sim.movement().setPathIdx(marines[i], 2);
            sim.movement().setFormationMemoryTimer(marines[i],
                    MovementService.FORMATION_MEMORY_SECONDS);
        }
        sim.getUnitIndex().rebuild(sim.getRoster());

        int ticks = Math.round(1.5f / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            sim.movement().beginTick(BattleSimulation.TICK_DT);
            separation.tick(BattleSimulation.TICK_DT);
        }

        float gap = Math.abs(teamCentroidY(sim, marines, 1)
                - teamCentroidY(sim, marines, 0));
        assertTrue(gap >= 1f,
                "settled teams should finish the open-ground footprint; gap=" + gap);
    }

    @Test
    public void distinctAuthoredDestinationsDoNotReceiveArrivalFormationDrift() {
        BattleSimulation sim = openArena(32, 20);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] marines = new long[8];
        float[] x = new float[8];
        float[] y = new float[8];
        for (int i = 0; i < marines.length; i++) {
            int cellX = 8 + i;
            int cellY = 6 + i % 2 * 4;
            marines[i] = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, cellX, cellY).squad(squadId)
                    .fireTeam(i / Squad.FIRE_TEAM_SIZE));
            sim.setPath(marines[i], new int[]{2, cellY, cellX, cellY});
            sim.movement().setPathIdx(marines[i], 2);
            sim.movement().setFormationMemoryTimer(marines[i],
                    MovementService.FORMATION_MEMORY_SECONDS);
            x[i] = sim.world().x(marines[i]);
            y[i] = sim.world().y(marines[i]);
        }
        sim.getUnitIndex().rebuild(sim.getRoster());
        separation.tick(BattleSimulation.TICK_DT);

        for (int i = 0; i < marines.length; i++) {
            assertEquals(x[i], sim.world().x(marines[i]), 0f,
                    "authored post X must remain authoritative");
            assertEquals(y[i], sim.world().y(marines[i]), 0f,
                    "authored post Y must remain authoritative");
        }
    }

    /**
     * Test 3 — mass asymmetry: a mech (radius 0.6, mass 0.36) and a marine
     * (radius 0.3, mass 0.09) overlap by 0.1 cells along the x-axis. The
     * inverse-mass weighting ({@code w = m(other) / (m(self) + m(other))})
     * gives the marine a yield weight of 0.8 and the mech 0.2 — a clean 4×
     * ratio, matching {@code (0.6/0.3)² == 4}. Overlap is kept small enough
     * that neither impulse hits the {@code MAX_PUSH_SPEED} clamp, so one
     * tick's raw displacement reflects the mass split exactly.
     *
     * <p>Also covers the S2 VEL fold-in: {@code beginTick} zeroes
     * {@code MOVEMENT_VEL_X/Y} first (matching the production per-tick
     * contract), then after the single-tick shove both units' velocity
     * columns must equal their measured displacement over {@code TICK_DT} —
     * catching swapped axes, a missing {@code /dt} scale, or an
     * overwrite-instead-of-add bug that the pre-existing position-only
     * assertions couldn't see.
     */
    @Test
    public void mechOverlapDisplacesMarineFourTimesFartherAndFoldsIntoVelocity() {
        BattleSimulation sim = openArena(20, 20);
        SeparationSystem separation = separationFor(sim);
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        long mech = sim.spawn(new EntitySpec("h", Faction.MARINE, UnitType.HEAVY_MECH, 10, 10));

        // sumR = 0.6 + 0.3 = 0.9; place 0.8 apart along x so overlap = 0.1.
        sim.world().setPos(marine, 10.1f, 10.5f);
        sim.world().setPos(mech, 10.9f, 10.5f);
        sim.getUnitIndex().rebuild(sim.getRoster());

        float marineX0 = sim.world().x(marine);
        float mechX0 = sim.world().x(mech);
        sim.movement().beginTick(BattleSimulation.TICK_DT);
        separation.tick(BattleSimulation.TICK_DT);
        float marineDispX = sim.world().x(marine) - marineX0;
        float mechDispX = sim.world().x(mech) - mechX0;

        assertTrue(Math.abs(mechDispX) > 0f, "mech should have yielded some displacement");
        assertEquals(4f * Math.abs(mechDispX), Math.abs(marineDispX), 1e-4f,
                "marine displacement (" + marineDispX + ") should be ~4x the mech's (" + mechDispX + ")");

        EntityWorld entityWorld = sim.getEntityWorld();
        BattleComponents comps = sim.getBattleComponents();
        float marineVelX = entityWorld.getFloat(marine, comps.MOVEMENT, BattleComponents.MOVEMENT_VEL_X);
        float marineVelY = entityWorld.getFloat(marine, comps.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y);
        float mechVelX = entityWorld.getFloat(mech, comps.MOVEMENT, BattleComponents.MOVEMENT_VEL_X);
        float mechVelY = entityWorld.getFloat(mech, comps.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y);
        assertEquals(marineDispX / BattleSimulation.TICK_DT, marineVelX, 1e-3f, "marine VEL_X should track applied displacement / dt");
        assertEquals(0f, marineVelY, 1e-3f, "marine VEL_Y should be ~0 for a purely x-axis shove");
        assertEquals(mechDispX / BattleSimulation.TICK_DT, mechVelX, 1e-3f, "mech VEL_X should track applied displacement / dt");
        assertEquals(0f, mechVelY, 1e-3f, "mech VEL_Y should be ~0 for a purely x-axis shove");
    }

    @Test
    public void mixedRoleMechLanceTakesAnOpenGroundDiamond() {
        BattleSimulation sim = openArena(44, 36);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long hound = spawnMech(sim, MechVariant.HOUND, MechRole.ASSAULT,
                Faction.MARINE, squadId, 20, 18);
        long highBulwark = spawnMech(sim, MechVariant.BULWARK,
                MechRole.ARMORED_SUPPORT, Faction.MARINE, squadId, 18, 20);
        long lowBulwark = spawnMech(sim, MechVariant.BULWARK,
                MechRole.ARMORED_SUPPORT, Faction.MARINE, squadId, 18, 16);
        long sirocco = spawnMech(sim, MechVariant.SIROCCO, MechRole.LR_SUPPORT,
                Faction.MARINE, squadId, 16, 18);
        long[] mechs = {hound, highBulwark, lowBulwark, sirocco};
        for (long mech : mechs) {
            sim.setPath(mech, new int[]{sim.world().cellX(mech),
                    sim.world().cellY(mech), 38, 18});
        }
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(SeparationSystem.MECH_FORMATION_OPEN_DISTANCE,
                separation.preferredMechFormationSpacing(mechs, mechs.length),
                1e-6f);
        float houndX = sim.world().x(hound);
        separation.tick(BattleSimulation.TICK_DT);
        float firstStep = Math.abs(sim.world().x(hound) - houndX);
        assertTrue(firstStep > 0f);
        assertTrue(firstStep <= SeparationSystem.FORMATION_MAX_SPEED
                        * BattleSimulation.TICK_DT + 1e-6f,
                "formation spacing should steer gently instead of hitting the collision cap");

        int ticks = Math.round(8f / BattleSimulation.TICK_DT);
        for (int tick = 1; tick < ticks; tick++) {
            separation.tick(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.world().x(hound) > sim.world().x(highBulwark));
        assertTrue(sim.world().x(hound) > sim.world().x(lowBulwark));
        assertTrue(sim.world().x(sirocco) < sim.world().x(highBulwark));
        assertTrue(sim.world().x(sirocco) < sim.world().x(lowBulwark));
        assertTrue(sim.world().y(highBulwark) > sim.world().y(lowBulwark));
        assertTrue(distance(sim, hound, highBulwark) >= 5.75f);
        assertTrue(distance(sim, hound, lowBulwark) >= 5.75f);
        assertTrue(distance(sim, sirocco, highBulwark) >= 5.75f);
        assertTrue(distance(sim, sirocco, lowBulwark) >= 5.75f);
    }

    @Test
    public void freeReignDisablesFormationSteeringButNotPhysicalSeparation() {
        BattleSimulation sim = openArena(32, 20);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long brawler = spawnMech(sim, MechVariant.HOUND, MechRole.ASSAULT,
                Faction.MARINE, squadId, 14, 10);
        long support = spawnMech(sim, MechVariant.SIROCCO, MechRole.LR_SUPPORT,
                Faction.MARINE, squadId, 18, 10);
        Squad squad = sim.getSquad(squadId);
        squad.applyLanceOrder(MechLanceOrder.FREE_REIGN);
        sim.setPath(brawler, new int[]{14, 10, 28, 10});
        sim.setPath(support, new int[]{18, 10, 28, 10});
        sim.getUnitIndex().rebuild(sim.getRoster());
        float brawlerX = sim.world().x(brawler);
        float supportX = sim.world().x(support);

        separation.tick(BattleSimulation.TICK_DT);

        assertEquals(brawlerX, sim.world().x(brawler), 0f);
        assertEquals(supportX, sim.world().x(support), 0f,
                "free-reign members receive no role-slot formation correction");

        sim.world().setPos(brawler, 14.5f, 10.5f);
        sim.world().setPos(support, 15.4f, 10.5f);
        sim.getUnitIndex().rebuild(sim.getRoster());
        separation.tick(BattleSimulation.TICK_DT);

        assertTrue(distance(sim, brawler, support) > 0.9f,
                "free reign must retain ordinary mech collision separation");
    }

    @Test
    public void constrainedMechLanceUsesTheCompressionFloor() {
        int width = 24;
        int height = 7;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int x = 1; x < width - 1; x++) grid.setWalkableFloor(x, 3);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, height));
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long first = spawnMech(sim, MechVariant.BULWARK,
                MechRole.ARMORED_SUPPORT, Faction.MARINE, squadId, 7, 3);
        long second = spawnMech(sim, MechVariant.BULWARK,
                MechRole.ARMORED_SUPPORT, Faction.MARINE, squadId, 8, 3);

        assertEquals(SeparationSystem.MECH_FORMATION_MIN_DISTANCE,
                separation.preferredMechFormationSpacing(
                        new long[]{first, second}, 2), 1e-6f);
        sim.setPath(first, new int[]{7, 3, 20, 3});
        sim.setPath(second, new int[]{8, 3, 20, 3});
        sim.getUnitIndex().rebuild(sim.getRoster());
        int ticks = Math.round(5f / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            separation.tick(BattleSimulation.TICK_DT);
        }

        float compressedDistance = distance(sim, first, second);
        assertTrue(compressedDistance >= 2.35f,
                "compressed lance should retain nearly the 2.5-cell floor; distance="
                        + compressedDistance + ", first=(" + sim.world().x(first)
                        + "," + sim.world().y(first) + "), second=("
                        + sim.world().x(second) + "," + sim.world().y(second) + ")");
        assertEquals(3, sim.world().cellY(first));
        assertEquals(3, sim.world().cellY(second));
    }

    @Test
    public void infantryFormationUsesWeaponRangeToSetDepth() {
        BattleSimulation sim = openArena(30, 24);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long shortRange = sim.spawn(new EntitySpec("short", Faction.MARINE,
                UnitType.MARINE, 14, 11).squad(squadId).attackRange(8f));
        long longRange = sim.spawn(new EntitySpec("long", Faction.MARINE,
                UnitType.MARINE, 14, 12).squad(squadId).attackRange(30f));
        sim.setPath(shortRange, new int[]{14, 11, 26, 11});
        sim.setPath(longRange, new int[]{14, 12, 26, 12});
        sim.getUnitIndex().rebuild(sim.getRoster());

        assertEquals(SeparationSystem.INFANTRY_FORMATION_OPEN_DISTANCE,
                separation.preferredInfantryFormationSpacing(
                        new long[]{shortRange, longRange}, 2), 1e-6f);
        int ticks = Math.round(4f / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            separation.tick(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.world().x(shortRange) > sim.world().x(longRange),
                "short-range marine should take the forward slot");
        assertTrue(distance(sim, shortRange, longRange) >= 1.65f,
                "fireteam should open to its infantry-scale interval");
    }

    @Test
    public void divergentSquadOrdersDoNotCreateAFormationTug() {
        BattleSimulation sim = openArena(24, 20);
        SeparationSystem separation = separationFor(sim);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long eastbound = sim.spawn(new EntitySpec("east", Faction.MARINE,
                UnitType.MARINE, 10, 9).squad(squadId));
        long westbound = sim.spawn(new EntitySpec("west", Faction.MARINE,
                UnitType.MARINE, 10, 11).squad(squadId));
        sim.setPath(eastbound, new int[]{10, 9, 20, 9});
        sim.setPath(westbound, new int[]{10, 11, 2, 11});
        sim.getUnitIndex().rebuild(sim.getRoster());
        float eastX = sim.world().x(eastbound);
        float westX = sim.world().x(westbound);

        separation.tick(BattleSimulation.TICK_DT);

        assertEquals(eastX, sim.world().x(eastbound), 0f);
        assertEquals(westX, sim.world().x(westbound), 0f);
    }

    @Test
    public void unrelatedMechSquadsUseOnlyTheCompressionFloor() {
        BattleSimulation sim = openArena(30, 20);
        SeparationSystem separation = separationFor(sim);
        int firstSquad = sim.mintSquad(
                Faction.MARINE, UnitType.HEAVY_MECH);
        int secondSquad = sim.mintSquad(
                Faction.MARINE, UnitType.HEAVY_MECH);
        long first = spawnMech(sim, MechVariant.HOUND, MechRole.ASSAULT,
                Faction.MARINE, firstSquad, 10, 10);
        long second = spawnMech(sim, MechVariant.SIROCCO, MechRole.LR_SUPPORT,
                Faction.MARINE, secondSquad, 12, 10);
        sim.setPath(first, new int[]{10, 10, 25, 10});
        sim.setPath(second, new int[]{12, 10, 25, 10});
        sim.getUnitIndex().rebuild(sim.getRoster());

        int ticks = Math.round(5f / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            separation.tick(BattleSimulation.TICK_DT);
        }

        float distance = distance(sim, first, second);
        assertTrue(distance >= 2.35f,
                "separate lances still need the mech compression floor");
        assertTrue(distance < 2.75f,
                "separate lances must not be pulled into one six-cell formation");
    }

    @Test
    public void formationForceDoesNotPushIdleOrEnemyMechsApart() {
        BattleSimulation sim = openArena(24, 20);
        SeparationSystem separation = separationFor(sim);
        long idleAllyA = spawnMech(sim, MechVariant.HOUND, Faction.MARINE, 5, 5);
        long idleAllyB = spawnMech(sim, MechVariant.BULWARK, Faction.MARINE, 7, 5);
        long movingMarine = spawnMech(sim, MechVariant.SIROCCO, Faction.MARINE, 12, 12);
        long nearbyEnemy = spawnMech(sim, MechVariant.HOUND, Faction.DEFENDER, 14, 12);
        sim.setPath(movingMarine, GridPathfinder.findPath(
                sim.getGrid(), 12, 12, 20, 12));
        sim.getUnitIndex().rebuild(sim.getRoster());

        separation.tick(BattleSimulation.TICK_DT);

        assertEquals(2f, distance(sim, idleAllyA, idleAllyB), 1e-6f,
                "settled authored positions should not drift merely for formation spacing");
        assertEquals(2f, distance(sim, movingMarine, nearbyEnemy), 1e-6f,
                "enemy mechs should receive only physical overlap separation");
    }

    private static long spawnMech(BattleSimulation sim, MechVariant variant,
                                  Faction faction, int x, int y) {
        return spawnMech(sim, variant, MechRole.ARMORED_SUPPORT, faction,
                Squad.NO_SQUAD, x, y);
    }

    private static long spawnMech(BattleSimulation sim, MechVariant variant,
                                  MechRole role, Faction faction, int squadId,
                                  int x, int y) {
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                variant.id, faction, UnitType.HEAVY_MECH, x, y)
                .squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(role));
        return mech;
    }

    private static float distance(BattleSimulation sim, long a, long b) {
        float dx = sim.world().x(a) - sim.world().x(b);
        float dy = sim.world().y(a) - sim.world().y(b);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float teamCentroidY(BattleSimulation sim, long[] members,
                                       int teamIndex) {
        float total = 0f;
        int first = teamIndex * Squad.FIRE_TEAM_SIZE;
        for (int i = first; i < first + Squad.FIRE_TEAM_SIZE; i++) {
            total += sim.world().y(members[i]);
        }
        return total / Squad.FIRE_TEAM_SIZE;
    }

    /**
     * Test 4 — immovables: a marine spawns overlapping a turret. Over 2
     * sim-seconds the turret's position never moves at all (infinite mass —
     * {@link SeparationSystem#weightOf} returns 1 against it, and it never
     * accumulates its own impulse as the outer participant), while the
     * marine resolves the full overlap and ends outside the turret's radius.
     *
     * <p>The first-tick displacement is also checked against the
     * {@code w = 1} prediction (full clamped step): without the {@code
     * isImmovable(b) -> w = 1} branch in {@code weightOf}, the marine would
     * instead yield by the ordinary inverse-mass split (~0.69 of the
     * overlap, not the full clamped push), which the "eventually resolves"
     * assertion alone can't distinguish from the correct behavior.
     */
    @Test
    public void turretNeverMovesWhileOverlappingMarineResolvesFully() {
        BattleSimulation sim = openArena(20, 20);
        SeparationSystem separation = separationFor(sim);
        // UnitType.TURRET's base maxHp is a 0f placeholder (MapTurret#create overwrites
        // it per-kind in production); isAliveById requires hp > 0, so this spec needs an
        // explicit health seed or the turret spawns dead-on-arrival and never participates.
        long turret = sim.spawn(new EntitySpec("t", Faction.DEFENDER, UnitType.TURRET, 10, 10).health(50f));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));

        float turretX0 = sim.world().x(turret);
        float turretY0 = sim.world().y(turret);
        float marineX0 = sim.world().x(marine);
        float marineY0 = sim.world().y(marine);

        // First-tick check: a full-overlap marine-on-immovable-turret shove is
        // clamped by MAX_PUSH_SPEED * dt, i.e. w=1 means the marine takes the
        // entire clamped step on tick 1 (overlap is large enough here to clamp).
        separation.tick(BattleSimulation.TICK_DT);
        assertEquals(turretX0, sim.world().x(turret), 0f, "tick 0 turret x moved");
        assertEquals(turretY0, sim.world().y(turret), 0f, "tick 0 turret y moved");
        float firstDx = sim.world().x(marine) - marineX0;
        float firstDy = sim.world().y(marine) - marineY0;
        float firstStep = (float) Math.sqrt(firstDx * firstDx + firstDy * firstDy);
        float expectedClampedStep = SeparationSystem.MAX_PUSH_SPEED * BattleSimulation.TICK_DT;
        assertEquals(expectedClampedStep, firstStep, 1e-4f,
                "marine's first-tick step (" + firstStep + ") should be the full clamped push (w=1), not a partial inverse-mass split");

        int ticks = Math.round(2f / BattleSimulation.TICK_DT);
        for (int t = 1; t < ticks; t++) {
            separation.tick(BattleSimulation.TICK_DT);
            assertEquals(turretX0, sim.world().x(turret), 0f, "tick " + t + " turret x moved");
            assertEquals(turretY0, sim.world().y(turret), 0f, "tick " + t + " turret y moved");
        }

        float dx = sim.world().x(marine) - sim.world().x(turret);
        float dy = sim.world().y(marine) - sim.world().y(turret);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float minSeparation = UnitType.MARINE.radius + UnitType.TURRET.radius;
        assertTrue(dist >= minSeparation - 1e-3f,
                "marine still overlapping stationary turret: dist=" + dist + " < " + minSeparation);
    }

    /**
     * Regression for the drone-hub immovability gap: {@code isImmovable} used
     * to check only {@code UnitRole.STRUCTURE} (never actually stamped by any
     * spawn path) and {@code UnitType.isTurret()} — a live drone hub (role
     * {@code DRONE_HUB}, type {@code DRONE_HUB_STRUCTURE}) fell through both
     * clauses and was treated as an ordinary movable participant. Because
     * hubs are a static type, they're spawned without a {@code MOVEMENT}
     * component, so once a hub accumulated an impulse, {@code apply} would
     * move it off its anchor and then crash the tick trying to fold the
     * displacement into a {@code MOVEMENT} column the entity doesn't carry.
     * A marine overlapping a live, walkable-cell hub reproduces the
     * accumulate path that used to reach that crash; asserting the hub never
     * moves (and the tick doesn't throw) is the regression check now that
     * {@code isImmovable} covers {@code UnitType.isStatic()}.
     */
    @Test
    public void droneHubNeverMovesWhileOverlappingMarineResolvesFully() {
        BattleSimulation sim = openArena(20, 20);
        SeparationSystem separation = separationFor(sim);
        long hub = sim.spawn(DroneHub.create("hub", Faction.DEFENDER, 10, 10));
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));

        float hubX0 = sim.world().x(hub);
        float hubY0 = sim.world().y(hub);

        int ticks = Math.round(2f / BattleSimulation.TICK_DT);
        for (int t = 0; t < ticks; t++) {
            separation.tick(BattleSimulation.TICK_DT);
            assertEquals(hubX0, sim.world().x(hub), 0f, "tick " + t + " hub x moved");
            assertEquals(hubY0, sim.world().y(hub), 0f, "tick " + t + " hub y moved");
        }

        float dx = sim.world().x(marine) - sim.world().x(hub);
        float dy = sim.world().y(marine) - sim.world().y(hub);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float minSeparation = UnitType.MARINE.radius + UnitType.DRONE_HUB_STRUCTURE.radius;
        assertTrue(dist >= minSeparation - 1e-3f,
                "marine still overlapping stationary hub: dist=" + dist + " < " + minSeparation);
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

    @Test
    public void denseCollisionCacheSurvivesGrowthAndSwapPop() {
        BattleSimulation sim = openArena(80, 80);
        long[] fillers = new long[65];
        for (int i = 0; i < fillers.length; i++) {
            fillers[i] = sim.spawn(new EntitySpec("filler-" + i,
                    Faction.CIVILIAN, UnitType.CIVILIAN,
                    2 + (i % 16) * 4, 2 + (i / 16) * 4));
        }
        long first = sim.spawn(new EntitySpec("first", Faction.MARINE,
                UnitType.MARINE, 70, 70));
        long swappedTail = sim.spawn(new EntitySpec("swapped tail",
                Faction.MARINE, UnitType.MARINE, 70, 70));
        sim.world().setPos(first, 70.4f, 70.5f);
        sim.world().setPos(swappedTail, 70.6f, 70.5f);

        sim.getRoster().releaseFromRegistry(fillers[1]);
        float before = Math.abs(sim.world().x(first)
                - sim.world().x(swappedTail));

        separationFor(sim).tick(BattleSimulation.TICK_DT);

        float dx = sim.world().x(first) - sim.world().x(swappedTail);
        float dy = sim.world().y(first) - sim.world().y(swappedTail);
        float after = (float) Math.sqrt(dx * dx + dy * dy);
        assertTrue(after > before,
                "the tail unit moved into a released dense slot must still resolve overlap");
    }

    @Test
    public void pendingDeathDoesNotParticipateBeforeCorpseTransmute() {
        BattleSimulation sim = openArena(20, 20);
        long alive = sim.spawn(new EntitySpec("alive", Faction.MARINE,
                UnitType.MARINE, 10, 10));
        long pendingDeath = sim.spawn(new EntitySpec("pending death",
                Faction.DEFENDER, UnitType.MARINE, 10, 10));
        sim.world().setHp(pendingDeath, 0f);

        float aliveX = sim.world().x(alive);
        float aliveY = sim.world().y(alive);
        float deadX = sim.world().x(pendingDeath);
        float deadY = sim.world().y(pendingDeath);

        separationFor(sim).tick(BattleSimulation.TICK_DT);

        assertEquals(aliveX, sim.world().x(alive), 0f);
        assertEquals(aliveY, sim.world().y(alive), 0f);
        assertEquals(deadX, sim.world().x(pendingDeath), 0f);
        assertEquals(deadY, sim.world().y(pendingDeath), 0f);
    }

    @Test
    public void collisionCacheRejectsDenseUnitMissingFromGridQuery() {
        BattleSimulation sim = openArena(20, 20);
        long malformed = sim.spawn(new EntitySpec("malformed", Faction.MARINE,
                UnitType.MARINE, 10, 10));
        sim.getEntityWorld().removeComponent(malformed,
                sim.getBattleComponents().POSITION);

        assertThrows(IllegalStateException.class,
                () -> separationFor(sim).tick(BattleSimulation.TICK_DT));
    }

    /**
     * Wiring check: every test above constructs a {@link SeparationSystem}
     * directly and calls {@code tick(dt)} by hand, so none of them would
     * notice if {@code BattleSimulation.tick()}'s own {@code separation.tick(
     * TICK_DT)} call were deleted, mis-ordered, or passed the wrong dt. This
     * one drives the real {@link BattleSimulation#advance} loop instead — a
     * stack of overlapping marines separates purely from ticking the full
     * simulation, proving the production slot actually runs. A live unit of
     * each faction is parked far away (mirrors {@code DeadBodySystemTest}'s
     * pattern) so the win-check doesn't end the battle mid-test.
     */
    @Test
    public void separationRunsInsideTheProductionTickLoop() {
        BattleSimulation sim = openArena(20, 20);
        sim.spawn(new EntitySpec("d-keepalive", Faction.DEFENDER, UnitType.MARINE, 18, 18));
        long[] marines = spawnStack(sim, 8, 10, 10);

        sim.advance(2f);

        float minSeparation = 2f * UnitType.MARINE.radius;
        for (int i = 0; i < marines.length; i++) {
            for (int j = i + 1; j < marines.length; j++) {
                float dx = sim.world().x(marines[i]) - sim.world().x(marines[j]);
                float dy = sim.world().y(marines[i]) - sim.world().y(marines[j]);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                assertTrue(dist >= minSeparation - 1e-3f,
                        "pair (" + i + "," + j + ") still overlapping after driving BattleSimulation.advance: dist=" + dist + " < " + minSeparation);
            }
        }
    }
}
