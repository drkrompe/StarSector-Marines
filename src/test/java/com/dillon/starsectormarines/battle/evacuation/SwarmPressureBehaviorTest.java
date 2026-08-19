package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.SeparationSystem;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmPressureBehaviorTest {

    @Test
    void sealedShelterRedirectsVisibleSwarmPressureToMarines() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(new PointOfInterest(
                        PointOfInterest.Kind.RESIDENTIAL,
                        6, 4, 10, 8, 8, 6, 8, 6)), 11L);
        assertNotNull(payload);
        long runner = runner(sim, 2, 2);
        long marine = marine(sim, 4, 2);

        assertTrue(sim.isCivilianShelterProtected());
        assertEquals(marine,
                SwarmPressureBehavior.selectTarget(runner, sim));
    }

    @Test
    void activeRegisteredEvacueeOutranksCloserMarineAndAmbientCivilian() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long ambient = civilian(sim, "ambient", 3, 2);
        marine(sim, 4, 2);
        long evacuee = civilian(sim, "evacuee", 10, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);

        long target = SwarmPressureBehavior.selectTarget(runner, sim);

        assertEquals(evacuee, target);
        assertFalse(target == ambient);
    }

    @Test
    void fallsBackToNearestMarineWhenNoActiveEvacueeRemains() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long farther = marine(sim, 9, 2);
        long nearer = marine(sim, 5, 2);
        long evacuee = civilian(sim, "evacuee", 3, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        sim.getCivilianEvacuationTracker().markEvacuated(evacuee);

        assertEquals(nearer,
                SwarmPressureBehavior.selectTarget(runner, sim));
        assertFalse(farther == nearer);
    }

    @Test
    void hiddenEvacueeIsNotKnownBeforeRunnerSensesIt() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long marine = marine(sim, 4, 2);
        long evacuee = civilian(sim, "hidden evacuee", 10, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        sim.getGrid().setWalkable(6, 2, false);

        assertEquals(marine,
                SwarmPressureBehavior.selectTarget(runner, sim));
    }

    @Test
    void runnerRemembersEvacueeAfterInitialDiscovery() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        marine(sim, 4, 2);
        long evacuee = civilian(sim, "discovered evacuee", 10, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        assertEquals(evacuee,
                SwarmPressureBehavior.selectTarget(runner, sim));

        sim.combat().setTargetId(runner, evacuee);
        sim.getGrid().setWalkable(6, 2, false);

        assertEquals(evacuee,
                SwarmPressureBehavior.selectTarget(runner, sim));
    }

    @Test
    void pathsTowardDistantTargetAndAppliesOnlyContactDamage() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long evacuee = civilian(sim, "evacuee", 8, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        float initialHp = sim.world().hp(evacuee);

        SwarmPressureBehavior.INSTANCE.update(runner, sim);

        int[] path = sim.movement().path(runner);
        assertFalse(Paths.isEmpty(path));
        assertEquals(8, Paths.destX(path));
        assertEquals(2, Paths.destY(path));
        assertEquals(initialHp, sim.world().hp(evacuee));

        sim.world().setCellPos(runner, 7, 2);
        sim.clearPath(runner);
        SwarmPressureBehavior.INSTANCE.update(runner, sim);

        assertTrue(sim.world().hp(evacuee) < initialHp);
        assertTrue(sim.combat().cooldownTimer(runner) > 0f);
    }

    @Test
    void repeatedBehaviorTicksCloseAllTheWayToMeleeContact() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long evacuee = civilian(sim, "evacuee", 10, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        float initialHp = sim.world().hp(evacuee);
        boolean damaged = false;

        for (int tick = 0; tick < 160; tick++) {
            SwarmPressureBehavior.INSTANCE.update(runner, sim);
            if (sim.resolveUnit(evacuee) == 0L
                    || sim.world().hp(evacuee) < initialHp) {
                damaged = true;
                break;
            }
        }

        assertTrue(damaged,
                "an unobstructed runner must close the full distance and land contact damage");
    }

    /**
     * S3 swarm-spread balance guard: separation may fan a converged pack out,
     * but it must not strand outer runners just beyond contact range. Drive a
     * full eight-runner pack through behavior, movement and separation for 12
     * seconds and count actual cooldown resets. Every runner must establish
     * contact and sustain several attacks rather than merely touch the target
     * once before being excluded by the crowd.
     */
    @Test
    void separatedSwarmPackSustainsMeleePressureFromEveryRunner() {
        BattleSimulation sim = simulation();
        long target = sim.spawn(new EntitySpec("durable target", Faction.MARINE,
                UnitType.MARINE, 12, 6).health(100_000f));
        long[] runners = new long[8];
        int[] attacks = new int[runners.length];
        for (int i = 0; i < runners.length; i++) {
            runners[i] = runner(sim, 2, 6);
        }
        SeparationSystem separation = new SeparationSystem(
                sim.getRoster(), sim.getUnitIndex(), sim.getGrid());

        int ticks = Math.round(12f / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            sim.movement().beginTick(BattleSimulation.TICK_DT);
            sim.getUnitIndex().rebuild(sim.getRoster());
            for (int i = 0; i < runners.length; i++) {
                float cooldownBefore = sim.combat().cooldownTimer(runners[i]);
                SwarmPressureBehavior.INSTANCE.update(runners[i], sim);
                float expectedWithoutAttack = Math.max(0f,
                        cooldownBefore - BattleSimulation.TICK_DT);
                if (sim.combat().cooldownTimer(runners[i])
                        > expectedWithoutAttack + 1e-5f) {
                    attacks[i]++;
                }
            }
            separation.tick(BattleSimulation.TICK_DT);
        }

        for (int i = 0; i < runners.length; i++) {
            assertTrue(attacks[i] >= 6,
                    "runner " + i + " landed only " + attacks[i]
                            + " attacks after the pack spread around its target");
            assertEquals(target, sim.combat().targetId(runners[i]),
                    "runner " + i + " lost the shared pressure target");
        }
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(16, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(16, 12));
    }

    private static long runner(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, x, y).role(UnitRole.SWARM_PRESSURE));
    }

    private static long civilian(BattleSimulation sim, String name,
                                  int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.CIVILIAN,
                UnitType.CIVILIAN, x, y).role(UnitRole.VIP));
    }

    private static long marine(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, x, y));
    }
}
