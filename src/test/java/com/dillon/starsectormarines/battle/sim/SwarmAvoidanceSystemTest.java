package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmAvoidanceSystemTest {

    @Test
    void idleMarineBacksAwayFromNearbyAlien() {
        BattleSimulation sim = openArena(24, 20);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 10, 10);
        spawn(sim, "runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 12, 10);
        float before = sim.world().x(marine);

        avoidanceFor(sim).tick(BattleSimulation.TICK_DT);

        assertTrue(sim.world().x(marine) < before);
        assertEquals(10.5f, sim.world().y(marine), 1e-6f);
        assertTrue(sim.movement().velX(marine) < 0f);
    }

    @Test
    void loneAlienSlowsButDoesNotStopAnAdvanceAtModerateRange() {
        BattleSimulation sim = openArena(24, 20);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 5, 10);
        spawn(sim, "runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 8, 10);
        sim.setPath(marine, new int[]{5, 10, 15, 10});
        float before = sim.world().x(marine);
        sim.movement().beginTick(BattleSimulation.TICK_DT);
        sim.advanceMovement(marine);
        float authoredAdvance = sim.world().x(marine);

        avoidanceFor(sim).tick(BattleSimulation.TICK_DT);

        assertTrue(sim.world().x(marine) > before,
                "one runner at moderate range should make the marine cautious, not stationary");
        assertTrue(sim.world().x(marine) < authoredAdvance,
                "avoidance should reduce movement directly into the runner");
    }

    @Test
    void severalAliensTurnAnAdvanceIntoAControlledRetreat() {
        BattleSimulation sim = openArena(24, 20);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 5, 10);
        spawn(sim, "runner-a", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 8, 9);
        spawn(sim, "runner-b", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 8, 10);
        spawn(sim, "runner-c", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 8, 11);
        sim.setPath(marine, new int[]{5, 10, 15, 10});
        float before = sim.world().x(marine);
        sim.movement().beginTick(BattleSimulation.TICK_DT);
        sim.advanceMovement(marine);

        avoidanceFor(sim).tick(BattleSimulation.TICK_DT);

        assertTrue(sim.world().x(marine) < before,
                "a runner cluster ahead should outweigh the authored forward step");
        float retreat = before - sim.world().x(marine);
        assertTrue(retreat <= (SwarmAvoidanceSystem.MAX_AVOID_SPEED
                - UnitType.MARINE.moveSpeed) * BattleSimulation.TICK_DT + 1e-4f);
    }

    @Test
    void avoidanceCannotExceedTheMarinesAuthoredMoveSpeed() {
        BattleSimulation sim = openArena(24, 20);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 5, 10);
        spawn(sim, "runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 5, 12);
        sim.setPath(marine, new int[]{5, 10, 15, 10});
        float startX = sim.world().x(marine);
        float startY = sim.world().y(marine);
        sim.movement().beginTick(BattleSimulation.TICK_DT);
        sim.advanceMovement(marine);

        avoidanceFor(sim).tick(BattleSimulation.TICK_DT);

        float dx = sim.world().x(marine) - startX;
        float dy = sim.world().y(marine) - startY;
        float displacement = (float) Math.sqrt(dx * dx + dy * dy);
        float vx = sim.movement().velX(marine);
        float vy = sim.movement().velY(marine);
        float appliedSpeed = (float) Math.sqrt(vx * vx + vy * vy);
        assertTrue(displacement <= UnitType.MARINE.moveSpeed
                * BattleSimulation.TICK_DT + 1e-5f);
        assertTrue(appliedSpeed <= UnitType.MARINE.moveSpeed + 1e-5f);
    }

    @Test
    void conventionalEnemiesAndDistantAliensDoNotAlterMovement() {
        BattleSimulation sim = openArena(24, 20);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 5, 10);
        spawn(sim, "soldier", Faction.DEFENDER,
                UnitType.MARINE_RED, 7, 10);
        spawn(sim, "distant-runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 11, 10);
        float x = sim.world().x(marine);
        float y = sim.world().y(marine);

        avoidanceFor(sim).tick(BattleSimulation.TICK_DT);

        assertEquals(x, sim.world().x(marine), 0f);
        assertEquals(y, sim.world().y(marine), 0f);
    }

    @Test
    void avoidanceCannotPushAMarineThroughTheMapBoundary() {
        BattleSimulation sim = openArena(12, 12);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 0, 5);
        spawn(sim, "runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 1, 5);

        avoidanceFor(sim).tick(1f);

        assertEquals(0, sim.world().cellX(marine));
        assertTrue(sim.getGrid().isWalkable(
                sim.world().cellX(marine), sim.world().cellY(marine)));
    }

    @Test
    void avoidanceRunsInsideTheProductionTickLoop() {
        BattleSimulation sim = openArena(24, 20);
        long marine = spawn(sim, "marine", Faction.MARINE,
                UnitType.MARINE, 10, 10);
        spawn(sim, "alien", Faction.DEFENDER,
                UnitType.ALIEN, 12, 10);
        float before = sim.world().x(marine);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.world().x(marine) < before);
    }

    private static SwarmAvoidanceSystem avoidanceFor(BattleSimulation sim) {
        return new SwarmAvoidanceSystem(
                sim.getRoster(), sim.getUnitIndex(), sim.getGrid());
    }

    private static long spawn(BattleSimulation sim, String name,
                              Faction faction, UnitType type,
                              int x, int y) {
        return sim.spawn(new EntitySpec(name, faction, type, x, y));
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
