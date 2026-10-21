package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Harvest's own rules, asked of {@link SwarmHarvest} directly: nothing here steps a battle. */
class SwarmHarvestTest {

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(24, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 10));
    }

    private static long colonist(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("colonist", Faction.CIVILIAN, UnitType.CIVILIAN, x, y).role(UnitRole.FLEE));
    }

    /** A Runner's blow on {@code body} that killed it this tick, as the death drain reports it. */
    private static void killedBy(SwarmHarvest harvest, long body, long runner, BattleSimulation sim) {
        harvest.struck(body, runner);
        harvest.onDeath(new DeathEvent(body, sim.world().x(body), sim.world().y(body), 0));
    }

    @Test
    void aRunnersKillIsSeizedDraggedAndFedIntoTheBore() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            assertTrue(harvest.isOpen(), "a living bore opens the harvest");
            long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, 4, 5);
            long body = colonist(sim, 5, 5);

            killedBy(harvest, body, runner, sim);
            harvest.tick(sim);
            assertEquals(body, harvest.loadOf(runner));
            assertEquals(1, harvest.seized());
            assertEquals(SwarmHarvest.CARRIER_SPEED * UnitType.SWARM_RUNNER.moveSpeed,
                    sim.movement().moveSpeed(runner), 1e-4f, "a carrier drags at a fraction of its pace");

            sim.world().setPos(runner, 12.5f, 5.5f);
            harvest.tick(sim);
            float dx = sim.world().x(body) - 12.5f, dy = sim.world().y(body) - 5.5f;
            assertEquals(SwarmHarvest.TRAIL_CELLS, Math.sqrt(dx * dx + dy * dy), 1e-3, "the load trails its carrier");

            sim.world().setPos(runner, 19.5f, 5.5f);
            harvest.tick(sim);
            assertEquals(1, harvest.delivered());
            assertEquals(1, bore.feeds());
            assertFalse(harvest.isCarrier(runner), "a delivery frees the carrier");
            assertEquals(UnitType.SWARM_RUNNER.moveSpeed, sim.movement().moveSpeed(runner), 1e-4f);
        }
    }

    @Test
    void aDeadCarrierDropsItsLoadAndAnotherRunnerTakesItUp() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            long carrier = SwarmStrains.spawn(sim, "carrier", UnitType.SWARM_RUNNER, 4, 5);
            long body = colonist(sim, 5, 5);
            killedBy(harvest, body, carrier, sim);
            harvest.tick(sim);

            sim.applyDamage(carrier, 1000f, 1000f);
            harvest.tick(sim);
            assertFalse(harvest.isCarried(body));
            assertTrue(harvest.isLoose(body), "the body lies where its carrier fell");
            assertEquals(1, harvest.dropped());
            assertEquals(0, harvest.delivered());

            long second = SwarmStrains.spawn(sim, "second", UnitType.SWARM_RUNNER,
                    sim.world().cellX(body), sim.world().cellY(body));
            sim.getUnitIndex().rebuild(sim.getRoster());
            harvest.tick(sim);
            assertEquals(body, harvest.loadOf(second));
            assertEquals(2, harvest.seized());
        }
    }

    @Test
    void onlyARunnersKillWithABoreOpenIsHarvested() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, 4, 5);
            long body = colonist(sim, 5, 5);
            harvest.tick(sim);
            killedBy(harvest, body, runner, sim);
            harvest.tick(sim);
            assertFalse(harvest.isCarried(body), "no bore, nowhere to take it");

            sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            long brute = SwarmStrains.spawn(sim, "brute", UnitType.SWARM_BRUTE, 8, 5);
            long other = colonist(sim, 9, 5);
            killedBy(harvest, other, brute, sim);
            harvest.tick(sim);
            assertFalse(harvest.isCarried(other), "only a Runner carries");

            long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 12, 5));
            assertTrue(SwarmHarvest.harvestable(sim, marine), "a Marine on foot is taken");
            long mech = sim.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 14, 5));
            assertFalse(SwarmHarvest.harvestable(sim, mech), "a machine is not");
        }
    }

    @Test
    void theControlLeavesTheDeadWhereTheyFall() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = new SwarmHarvest(false);
            sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            assertFalse(harvest.isOpen());
            long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, 4, 5);
            long body = colonist(sim, 5, 5);
            killedBy(harvest, body, runner, sim);
            harvest.tick(sim);
            assertFalse(harvest.isCarried(body));
        }
    }
}
