package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfantryWeaponsTest {

    private static final int WIDTH = 24;
    private static final int HEIGHT = 12;
    private static final int ROW = 5;

    @Test
    void experienceAuthorsTheFriendlyFireHoldLadder() {
        assertEquals(0.20f, ExperienceTier.GREEN.friendlyFireHoldChance, 1e-6f);
        assertEquals(0.50f, ExperienceTier.REGULAR.friendlyFireHoldChance, 1e-6f);
        assertEquals(0.75f, ExperienceTier.VETERAN.friendlyFireHoldChance, 1e-6f);
        assertEquals(0.90f, ExperienceTier.ELITE.friendlyFireHoldChance, 1e-6f);
    }

    @Test
    void trainedSoldierWithholdsACommittedFriendlyHitBeforeEmission() {
        Fixture f = fixture(ExperienceTier.REGULAR, true);

        // On-target aim, centered lateral/elevation, friendly contact catches,
        // then Regular discipline succeeds at 0.49 < 0.50.
        f.weapons.fireShot(f.shooter, f.target, FireStance.STANCED,
                new QueueRandom(0f, 0.5f, 0.5f, 0f, 0.49f));

        assertEquals(0, f.shots.getActiveShots().size());
        assertEquals(0, f.sim.telemetry().roundsFired(f.shooter));
        assertEquals(List.of(), drainImpacts(f.shots));
    }

    @Test
    void failedDisciplineEmitsOnlyTheFriendlyBoundRound() {
        Fixture f = fixture(ExperienceTier.REGULAR, true);

        // The same committed trajectory, but 0.50 is not below the 0.50 hold
        // chance. The emitted round stops in the ally; it never becomes enemy DPS.
        f.weapons.fireShot(f.shooter, f.target, FireStance.STANCED,
                new QueueRandom(0f, 0.5f, 0.5f, 0f, 0.50f));

        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(1, f.sim.telemetry().roundsFired(f.shooter));
        List<ShotService.PendingImpact> impacts = drainImpacts(f.shots);
        assertEquals(1, impacts.size());
        assertEquals(f.friendly, impacts.get(0).victimId);
        assertTrue(impacts.get(0).friendly);
    }

    @Test
    void safeRoundNeverRollsDisciplineAndStillReachesEnemy() {
        Fixture f = fixture(ExperienceTier.ELITE, false);

        // Queue has only the three target-plane samples. Any accidental
        // discipline roll exhausts it and fails the test.
        f.weapons.fireShot(f.shooter, f.target, FireStance.STANCED,
                new QueueRandom(0f, 0.5f, 0.5f));

        assertEquals(1, f.shots.getActiveShots().size());
        assertEquals(1, f.sim.telemetry().roundsFired(f.shooter));
        List<ShotService.PendingImpact> impacts = drainImpacts(f.shots);
        assertEquals(1, impacts.size());
        assertEquals(f.target, impacts.get(0).victimId);
        assertFalse(impacts.get(0).friendly);
    }

    private static Fixture fixture(ExperienceTier experience, boolean friendlyInLane) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
        SoldierProfile profile = new SoldierProfile(
                SoldierAptitude.STEADY, experience.minimumXp);
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE,
                UnitType.MARINE, 2, ROW)
                .primaryWeapon(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE, profile));
        long friendly = sim.spawn(new EntitySpec("friendly", Faction.MARINE,
                UnitType.MARINE, 11, friendlyInLane ? ROW : ROW + 3));
        long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER,
                UnitType.MARINE, 15, ROW));
        ShotService shots = new ShotService();
        BallisticResolver resolver = new BallisticResolver(grid,
                new DoodadService(grid), sim.getUnitIndex(), sim.getRoster());
        InfantryWeapons weapons = new InfantryWeapons(sim.getRoster(), resolver, shots);
        return new Fixture(sim, weapons, shots, shooter, friendly, target);
    }

    private static List<ShotService.PendingImpact> drainImpacts(ShotService shots) {
        List<ShotService.PendingImpact> impacts = new ArrayList<>();
        shots.tickImpacts(100f, impacts::add);
        return impacts;
    }

    private record Fixture(BattleSimulation sim, InfantryWeapons weapons,
                           ShotService shots, long shooter, long friendly,
                           long target) {}

    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> values = new ArrayDeque<>();

        QueueRandom(float... values) {
            for (float value : values) this.values.add(value);
        }

        @Override
        public float nextFloat() {
            if (values.isEmpty()) throw new IllegalStateException("QueueRandom exhausted");
            return values.remove();
        }
    }
}
