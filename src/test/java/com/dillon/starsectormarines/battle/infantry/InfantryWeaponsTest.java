package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

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
import com.dillon.starsectormarines.marine.BraceSpec;
import com.dillon.starsectormarines.marine.HoldingFiringPositionSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
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

    @Test
    void shredderReleasesOneSimultaneousSixProjectileCloud() {
        Fixture f = fixture(ExperienceTier.REGULAR, false, WeaponRegistry.require(WeaponRegistry.SMG_ID));

        f.weapons.fireShot(f.shooter, f.target, FireStance.STANCED,
                new ConstantRandom(0.5f));

        assertEquals(6, f.shots.getActiveShots().size());
        assertEquals(6, f.sim.telemetry().roundsFired(f.shooter));
    }


    /**
     * A braced marine measurably shoots better, measured at the seam where it
     * would have to be true: the highest aim roll a round still lands on target
     * with. The stance is read through at fire time rather than written onto the
     * shooter, so this is the only place the improvement exists at all
     * ({@code integral-system-slate.md}).
     */
    @Test
    void aBracedMarineLandsRoundsAnUnbracedOneWouldHaveMissedWith() {
        float unbraced = highestAimRollThatLands(false);
        float braced = highestAimRollThatLands(true);

        assertTrue(braced > unbraced,
                "planting has to buy something at the trigger: unbraced landed up to "
                        + unbraced + ", braced up to " + braced);
    }

    /** And gives it all back the moment the stance expires. */
    @Test
    void anExpiredBraceLeavesTheShooterExactlyAsItWasIssued() {
        float issued = highestAimRollThatLands(false);
        Fixture f = bracedFixture();
        BattleSimulation sim = f.sim();
        sim.integralSystems().activate(f.shooter());
        for (int tick = 0; tick < 400; tick++) {
            sim.integralSystems().tick(f.shooter(), BattleSimulation.TICK_DT);
        }
        assertFalse(sim.integralSystems().isActive(f.shooter()));

        assertEquals(issued, highestAimRollThatLands(f, false), 1e-6f,
                "an expired stance leaves no remainder behind");
    }

    /**
     * The largest aim roll this shooter still puts on the target with, to a
     * hundredth. Everything downstream of the aim roll is held centred, so the
     * only thing that moves the answer is the accuracy the fire seam computed.
     */
    private static float highestAimRollThatLands(boolean braced) {
        Fixture f = bracedFixture();
        if (braced) f.sim().integralSystems().activate(f.shooter());
        return highestAimRollThatLands(f, braced);
    }

    private static float highestAimRollThatLands(Fixture f, boolean expectBraced) {
        assertEquals(expectBraced, f.sim().integralSystems().isActive(f.shooter()));
        float best = -1f;
        for (int step = 0; step <= 100; step++) {
            float roll = step / 100f;
            f.weapons().fireShot(f.shooter(), f.target(), FireStance.STANCED,
                    new FirstThenCentered(roll));
            for (ShotService.PendingImpact impact : drainImpacts(f.shots())) {
                if (impact.victimId == f.target()) best = roll;
            }
        }
        return best;
    }

    private static Fixture bracedFixture() {
        return fixture(ExperienceTier.REGULAR, false,
                WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), brace());
    }

    private static IntegralSystemDef brace() {
        return new IntegralSystemDef("system.test-brace", "Test brace",
                EquipmentGrade.SERVICE, "Planted.", IntegralSystemEffect.BRACE,
                SpecialResourceMode.COOLDOWN, 5f, 18f, 0,
                null, null, null, new BraceSpec(0.4f, 1.6f),
                new HoldingFiringPositionSpec(0.5f, 4f));
    }

    /**
     * The aim roll under test, then a centred everything-else. Keeps one shot's
     * outcome a function of the accuracy alone rather than of how many samples
     * the trajectory happened to draw.
     */
    private static final class FirstThenCentered extends Random {
        private final float first;
        private boolean used;

        FirstThenCentered(float first) {
            this.first = first;
        }

        @Override
        public float nextFloat() {
            if (used) return 0.5f;
            used = true;
            return first;
        }
    }

    private static Fixture fixture(ExperienceTier experience, boolean friendlyInLane) {
        return fixture(experience, friendlyInLane, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
    }

    private static Fixture fixture(ExperienceTier experience, boolean friendlyInLane,
                                   WeaponDef weapon) {
        return fixture(experience, friendlyInLane, weapon, null);
    }

    private static Fixture fixture(ExperienceTier experience, boolean friendlyInLane,
                                   WeaponDef weapon, IntegralSystemDef system) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
        SoldierProfile profile = new SoldierProfile(
                SoldierAptitude.STEADY, experience.minimumXp);
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE,
                UnitType.MARINE, 2, ROW)
                .primaryWeapon(weapon, EquipmentGrade.SERVICE, profile)
                .integralSystem(system));
        long friendly = sim.spawn(new EntitySpec("friendly", Faction.MARINE,
                UnitType.MARINE, 11, friendlyInLane ? ROW : ROW + 3));
        long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER,
                UnitType.MARINE, 15, ROW));
        ShotService shots = new ShotService();
        BallisticResolver resolver = new BallisticResolver(grid,
                new DoodadService(grid), sim.getUnitIndex(), sim.getRoster());
        InfantryWeapons weapons = new InfantryWeapons(
                sim.getRoster(), resolver, shots, grid, new Random(0L));
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

    private static final class ConstantRandom extends Random {
        private final float value;

        ConstantRandom(float value) {
            this.value = value;
        }

        @Override
        public float nextFloat() {
            return value;
        }
    }
}
