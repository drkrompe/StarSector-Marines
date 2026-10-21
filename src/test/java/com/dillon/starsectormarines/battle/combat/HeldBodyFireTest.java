package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.evacuation.SwarmHarvest;
import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner's rule for a body on a swarm carrier's blades
 * (`colony-breach-nouns.md`, Harvest): direct fire never touches it, aimed,
 * stray or through another body, and a blast reaches it only while
 * {@code areaHitsHeld}. Asked of the resolver and the detonation walk.
 */
class HeldBodyFireTest {

    private static final int W = 24;
    private static final int H = 12;
    private static final int ROW = 5;
    private static final float EPS = 1e-4f;
    private static final String AREA_SWITCH = "battle.colonyBreach.impale.areaHitsHeld";

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static long body(BattleSimulation sim, int cellX) {
        return sim.spawn(new EntitySpec("body", Faction.DEFENDER, UnitType.MARINE, cellX, ROW)
                .health(100f).armor(100f, 1f));
    }

    /** Puts {@code body} on the blades of a Runner one cell behind it, with a bore open to take it to. */
    private static void hold(BattleSimulation sim, long body) {
        SwarmHarvest harvest = sim.swarmHarvest();
        sim.swarmBores().open(sim, 1, false, W - 2, 1);
        harvest.tick(sim);
        long runner = SwarmStrains.spawn(sim, "carrier", UnitType.SWARM_RUNNER,
                sim.world().cellX(body) + 1, ROW);
        harvest.impale(body, runner);
        harvest.tick(sim);
        assertTrue(harvest.isCarried(body), "the body is held");
    }

    private static BallisticResolver.Resolution fireAt(BattleSimulation sim, long shooter, long target) {
        BallisticResolver resolver = new BallisticResolver(
                sim.getGrid(), new DoodadService(sim.getGrid()), sim.getUnitIndex(), sim.getRoster());
        resolver.spareHeld(sim.swarmHarvest()::isCarried);
        return resolver.resolve(shooter, target, 1f, 0f, 10f, 14f, new QueueRandom(0f, 0.5f, 0.5f, 0.5f, 0.5f));
    }

    @Test
    void anAimedRoundPassesTheBodyOnTheBlades() {
        try (BattleSimulation control = openArena()) {
            long shooter = control.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, ROW));
            long target = body(control, 6);
            assertEquals(target, fireAt(control, shooter, target).victimId(), "control: a free body is hit");
        }
        try (BattleSimulation sim = openArena()) {
            long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, ROW));
            long target = body(sim, 6);
            hold(sim, target);
            BallisticResolver.Resolution shot = fireAt(sim, shooter, target);
            assertNotEquals(target, shot.victimId(), "the round flies past the held body");
            for (BallisticResolver.BodyHit hit : shot.bodyHits()) {
                assertNotEquals(target, hit.victimId(), "nor does it pass through it");
            }
        }
    }

    @Test
    void aRoundAtSomethingBehindTheHeldBodyIsNotStoppedByIt() {
        try (BattleSimulation sim = openArena()) {
            long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, ROW));
            long held = body(sim, 6);
            long beyond = body(sim, 12);
            hold(sim, held);
            BallisticResolver.Resolution shot = fireAt(sim, shooter, beyond);
            assertNotEquals(held, shot.victimId());
            for (BallisticResolver.BodyHit hit : shot.bodyHits()) assertNotEquals(held, hit.victimId());
        }
    }

    @Test
    void aBlastReachesTheHeldBodyButADirectPayloadNever() {
        try (BattleSimulation sim = openArena()) {
            long held = body(sim, 6);
            hold(sim, held);
            float x = sim.world().x(held), y = sim.world().y(held);
            sim.detonateNow(blast(x, y, held));
            assertEquals(90f, sim.world().armor(held), EPS, "the splash lands; the direct payload does not");
        }
    }

    @Test
    void theSwitchSparesTheHeldBodyFromBlastsToo() {
        String was = System.getProperty(AREA_SWITCH);
        System.setProperty(AREA_SWITCH, "false");
        try (BattleSimulation sim = openArena()) {
            long held = body(sim, 6);
            hold(sim, held);
            sim.detonateNow(blast(sim.world().x(held), sim.world().y(held), held));
            assertEquals(100f, sim.world().armor(held), EPS);
            assertEquals(100f, sim.world().hp(held), EPS);
        } finally {
            if (was == null) System.clearProperty(AREA_SWITCH);
            else System.setProperty(AREA_SWITCH, was);
        }
    }

    /** A grenade on the held body: 10 splash at full penetration, and 40 direct on the body itself. */
    private static PendingDetonation blast(float x, float y, long direct) {
        return new PendingDetonation(CombatTelemetryService.NO_ATTACKER, x, y, 0f,
                1.5f, /*splashDamage*/ 10f, /*splashPenetration*/ 20f,
                /*wallDamage*/ 0, Faction.MARINE, /*aerialDelivery*/ false,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false,
                /*friendlyFireImmune*/ false,
                direct, /*directDamage*/ 40f, /*directPenetration*/ 20f,
                /*authoredAftermath*/ false);
    }

    /** Hands back a fixed sequence of rolls, then mid-range ones. */
    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> queue = new ArrayDeque<>();

        QueueRandom(float... values) {
            for (float v : values) queue.add(v);
        }

        @Override
        public float nextFloat() {
            return queue.isEmpty() ? 0.5f : queue.poll();
        }
    }
}
