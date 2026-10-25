package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.ArmorClass;
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
        sim.applyDamage(body, runner, 1000f, 1000f, 0f);
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
    void anIdleRunnerRecyclesADeadRunnerIntoTheBoreAndAFightingOneLeavesIt() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            long dead = SwarmStrains.spawn(sim, "dead", UnitType.SWARM_RUNNER, 5, 5);
            long fighter = SwarmStrains.spawn(sim, "fighter", UnitType.SWARM_RUNNER, 4, 5);
            long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 2, 8));
            sim.combat().setTargetId(fighter, marine);
            sim.applyDamage(dead, 1000f, 1000f);
            harvest.onDeath(new DeathEvent(dead, sim.world().x(dead), sim.world().y(dead), 0));
            sim.getUnitIndex().rebuild(sim.getRoster());
            harvest.tick(sim);
            assertTrue(harvest.isLoose(dead), "a dead Runner lies loose for the swarm");
            assertFalse(harvest.isCarrier(fighter), "a Runner still fighting leaves it");

            sim.combat().setTargetId(fighter, 0L);
            harvest.tick(sim);
            assertEquals(dead, harvest.loadOf(fighter), "a Runner with nothing to fight takes it up");
            assertEquals(0, harvest.seized(), "recycling the swarm's own is not harvest");

            sim.world().setPos(fighter, 19.5f, 5.5f);
            harvest.tick(sim);
            assertEquals(1, harvest.recycled());
            assertEquals(0, harvest.delivered());
            assertEquals(1, bore.feeds());
        }
    }

    @Test
    void aScavengerFetchesTheDeadBeforeItFightsAndAnAttackerGoesForTheMarine() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            sim.swarmBores().open(sim, 1, false, 22, 5);
            harvest.tick(sim);
            long dead = SwarmStrains.spawn(sim, "dead", UnitType.SWARM_RUNNER, 16, 2);
            sim.applyDamage(dead, 1000f, 1000f);
            harvest.onDeath(new DeathEvent(dead, sim.world().x(dead), sim.world().y(dead), 0));
            harvest.tick(sim);
            long scavenger = SwarmStrains.spawn(sim, "scavenger", UnitType.SWARM_RUNNER, 3, 5);
            long attacker = SwarmStrains.spawn(sim, "attacker", UnitType.SWARM_RUNNER, 3, 7);
            sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 6, 6));
            harvest.markScavenger(scavenger);
            sim.getUnitIndex().rebuild(sim.getRoster());

            SwarmPressureBehavior.INSTANCE.update(scavenger, sim);
            SwarmPressureBehavior.INSTANCE.update(attacker, sim);
            assertEquals(0L, sim.combat().targetId(scavenger), "the scavenger leaves the Marine");
            int[] path = sim.movement().path(scavenger);
            assertEquals(16, Paths.destX(path), "it heads for the dead");
            assertEquals(2, Paths.destY(path));
            assertTrue(sim.combat().targetId(attacker) != 0L, "an attacker goes for the Marine");
        }
    }

    @Test
    void theImpaleDrawIsTheStrikesOwnAndComesUpAboutOneBlowInTwenty() {
        assertEquals(SwarmHarvest.impaleDraw(120, 7L, 9L), SwarmHarvest.impaleDraw(120, 7L, 9L));
        int hits = 0, draws = 0;
        for (int tick = 0; tick < 200; tick++) {
            for (long runner = 1; runner <= 100; runner++) {
                draws++;
                if (SwarmHarvest.impaleDraw(tick, runner, 5000L + tick) < SwarmHarvest.IMPALE_CHANCE) hits++;
            }
        }
        assertEquals(SwarmHarvest.IMPALE_CHANCE, hits / (float) draws, 0.01f);
    }

    @Test
    void anImpaledBodyIsHeldAliveAndFreedWithBothLegsWoundedWhenItsCarrierDies() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, 4, 5);
            long body = sim.spawn(new EntitySpec("militia", Faction.ALLY, UnitType.MILITIA, 5, 5));
            float hp = sim.world().hp(body);

            harvest.impale(body, runner);
            harvest.tick(sim);
            assertTrue(harvest.isCarried(body));
            assertTrue(harvest.holdsStill(body), "a held body does nothing");
            assertEquals(hp, sim.world().hp(body), "the impaling blow does no damage of its own");
            assertEquals(SwarmHarvest.EventKind.IMPALED, harvest.events().get(0).kind());

            sim.applyDamage(runner, 1000f, 1000f);
            harvest.tick(sim);
            assertFalse(harvest.isCarried(body));
            assertTrue(sim.world().isAlive(body), "killing the carrier frees a living body");
            assertEquals(2, sim.wounds().wounds(body, BodyPart.LEGS), "the blades went through both legs");
            assertTrue(harvest.holdsStill(body), "it lies recovering for a moment");
            assertFalse(harvest.isLoose(body), "a living body is freed, not left for another Runner");
            assertEquals(1, harvest.freed());
        }
    }

    @Test
    void aMarineCarriedToTheBoreIsTakenAndIsBiomass() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            long runner = SwarmStrains.spawn(sim, "runner", UnitType.SWARM_RUNNER, 16, 5);
            long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 17, 5));
            harvest.impale(marine, runner);
            harvest.tick(sim);
            assertTrue(harvest.takenMarines(sim).contains(marine), "still held: lost with the carrier if the battle ends");

            sim.world().setPos(runner, 19.5f, 5.5f);
            harvest.tick(sim);
            assertFalse(sim.world().isAlive(marine), "taken at the mouth");
            assertEquals(1, harvest.taken());
            assertEquals(1, bore.feeds());
            assertTrue(harvest.takenMarines(sim).contains(marine));
        }
    }

    @Test
    void aPowerSuitIsNeverTaken() {
        try (BattleSimulation sim = simulation()) {
            SwarmHarvest harvest = sim.swarmHarvest();
            sim.swarmBores().open(sim, 1, false, 20, 5);
            harvest.tick(sim);
            long suit = sim.spawn(new EntitySpec("suit", Faction.MARINE, UnitType.MARINE, 5, 5)
                    .armorClass(ArmorClass.POWER_SUIT));
            assertFalse(SwarmHarvest.harvestable(sim, suit));
            long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 8, 5));
            assertTrue(SwarmHarvest.harvestable(sim, marine));
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

    @Test
    void aFedBodySlidesIntoTheMouthThenSinksOutOfSight() {
        SwarmHarvest.Feed feed = new SwarmHarvest.Feed(1L, 10f, 4f, 12f, 4f, 0);
        int slide = Math.round(SwarmHarvest.FEED_SLIDE_SECONDS / BattleSimulation.TICK_DT);
        int gone = Math.round(SwarmHarvest.FEED_SECONDS / BattleSimulation.TICK_DT);
        assertEquals(10f, SwarmHarvest.fedAt(feed, 0)[0], 1e-4f, "let go where it was");
        float mid = SwarmHarvest.fedAt(feed, slide / 2)[0];
        assertTrue(mid > 11f && mid < 12f, "eased: past halfway at half the slide, " + mid);
        assertEquals(12f, SwarmHarvest.fedAt(feed, slide)[0], 1e-4f, "at the mouth");
        assertEquals(12f, SwarmHarvest.fedAt(feed, gone)[0], 1e-4f, "and stays there");
        assertEquals(0f, SwarmHarvest.sunk(feed, slide), 1e-4f, "it sinks only once at the mouth");
        float half = SwarmHarvest.sunk(feed, (slide + gone) / 2);
        assertTrue(half > 0.3f && half < 0.7f, "sinking, " + half);
        assertEquals(1f, SwarmHarvest.sunk(feed, gone), 1e-4f);
    }
}
