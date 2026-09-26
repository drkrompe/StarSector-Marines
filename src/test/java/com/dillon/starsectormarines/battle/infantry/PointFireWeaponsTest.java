package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Primary release, burst and arrival contracts on a bare roster, without a battle. */
class PointFireWeaponsTest {
    @Test
    void damageArrivesWithTheVisibleRoundAndTelemetryCountsTheRelease() {
        Fixture f = new Fixture(new QueueRandom(0f, 0f, 0f));
        long victim = f.body(Faction.DEFENDER, 12, 5);
        f.weapons.firePointShot(f.shooter, new PointFireAim(12.5f, 5.5f), FireStance.STANCED);

        ShotEvent shot = f.shots.getActiveShots().get(0);
        assertTrue(shot.struckUnit);
        assertFalse(shot.hit, "there is no locked intended target");
        assertEquals(BallisticResolver.StopKind.UNIT_HIT, shot.stopKind);
        assertEquals(1, f.roster.telemetry().roundsFired(f.shooter));
        List<ShotService.PendingImpact> arrivals = new ArrayList<>();
        f.shots.tickImpacts(shot.lifetimeMax * 0.5f, arrivals::add);
        assertTrue(arrivals.isEmpty());
        f.shots.tickImpacts(shot.lifetimeMax * 0.5f + 1e-5f, arrivals::add);
        assertEquals(1, arrivals.size());
        assertEquals(victim, arrivals.get(0).victimId);
        assertEquals(f.roster.combat().attackDamage(f.shooter), arrivals.get(0).damage);
    }

    @Test
    void trainingCanHoldAFriendlyCatchBeforeEmission() {
        Fixture f = new Fixture(new QueueRandom(0f, 0f, 0f, 0.49f));
        f.body(Faction.MARINE, 12, 5);
        f.weapons.firePointShot(f.shooter, new PointFireAim(20.5f, 5.5f), FireStance.STANCED);
        assertTrue(f.shots.getActiveShots().isEmpty());
        assertEquals(0, f.roster.telemetry().roundsFired(f.shooter));
        f.shots.tickImpacts(100f, impact -> fail("withheld round cannot deal damage"));
    }

    @Test
    void failedFriendlyHoldKeepsTheSharedDamageReduction() {
        Fixture f = new Fixture(new QueueRandom(0f, 0f, 0f, 0.5f));
        long friendly = f.body(Faction.MARINE, 12, 5);
        f.weapons.firePointShot(f.shooter, new PointFireAim(20.5f, 5.5f), FireStance.STANCED);
        List<ShotService.PendingImpact> impacts = new ArrayList<>();
        f.shots.tickImpacts(100f, impacts::add);
        assertEquals(1, impacts.size());
        assertEquals(friendly, impacts.get(0).victimId);
        assertTrue(impacts.get(0).friendly);
        assertEquals(f.roster.combat().attackDamage(f.shooter)
                * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT, impacts.get(0).damage);
    }

    @Test
    void burstKeepsItsPointUntilCompletionAndClearCancelsPendingWork() {
        Fixture f = new Fixture(new Random(19));
        PointFireAim aim = new PointFireAim(20.5f, 5.5f);
        f.roster.combat().beginPointBurst(f.shooter, aim);
        int rounds = f.roster.combat().burstRemaining(f.shooter);
        assertTrue(rounds > 0);
        f.weapons.tick();
        assertTrue(f.shots.getActiveShots().isEmpty(), "burst spacing holds the next round");
        f.roster.combat().setPointFireIntent(f.shooter, new PointFireAim(2.5f, 12.5f), FireStance.MOVING);
        for (int tick = 0; tick < 100; tick++) f.weapons.tick();
        assertEquals(rounds, f.shots.getActiveShots().size());
        assertTrue(f.shots.getActiveShots().stream().allMatch(shot -> shot.toX > shot.fromX));
        assertEquals(0L, f.roster.combat().burstTargetId(f.shooter));
        assertNull(f.roster.combat().burstPointAim(f.shooter));
        f.roster.combat().beginPointBurst(f.shooter, aim);
        f.roster.combat().clearPrimaryFire(f.shooter);
        for (int tick = 0; tick < 100; tick++) f.weapons.tick();
        assertEquals(rounds, f.shots.getActiveShots().size());
        assertNull(f.roster.combat().pointFireAim(f.shooter));
        assertEquals(0, f.roster.combat().burstRemaining(f.shooter));
    }

    @Test
    void burstUsesAppliedMotionForItsMovingFirePenalty() {
        Fixture planted = new Fixture(new Random(83));
        Fixture moving = new Fixture(new Random(83));
        PointFireAim aim = new PointFireAim(20.5f, 5.5f);
        for (Fixture f : List.of(planted, moving)) {
            f.roster.combat().beginPointBurst(f.shooter, aim);
            f.roster.combat().setBurstTimer(f.shooter, 0f);
        }
        BattleComponents c = moving.roster.components();
        moving.roster.entityWorld().setFloat(moving.shooter, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_X, 1f);
        planted.weapons.tick();
        moving.weapons.tick();
        ShotEvent stationaryShot = planted.shots.getActiveShots().get(0);
        ShotEvent movingShot = moving.shots.getActiveShots().get(0);
        assertTrue(Math.abs(movingShot.toY - movingShot.fromY)
                > Math.abs(stationaryShot.toY - stationaryShot.fromY));
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(40, 16);
        final UnitSpatialIndex index = new UnitSpatialIndex(40, 16);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final ShotService shots = new ShotService();
        final InfantryWeapons weapons;
        final long shooter;

        Fixture(Random random) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
            }
            shooter = roster.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, 5)
                    .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID),
                            EquipmentGrade.SERVICE, new SoldierProfile(SoldierAptitude.STEADY,
                                    ExperienceTier.REGULAR.minimumXp)));
            weapons = new InfantryWeapons(roster,
                    new BallisticResolver(grid, new DoodadService(grid), index, roster), shots, grid, random);
        }

        long body(Faction faction, int x, int y) {
            return roster.spawn(new EntitySpec("body", faction, UnitType.MARINE, x, y));
        }
    }

    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> values = new ArrayDeque<>();
        QueueRandom(float... values) {
            for (float value : values) this.values.add(value);
        }
        @Override public float nextFloat() {
            if (values.isEmpty()) throw new AssertionError("Unexpected random draw");
            return values.remove();
        }
    }
}
