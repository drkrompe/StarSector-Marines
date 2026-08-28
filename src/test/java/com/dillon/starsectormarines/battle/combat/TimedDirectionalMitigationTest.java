package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.CrossingUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mitigation as a durability noun: a bounded fraction of post-cover damage
 * refused, for an explicit duration, across a bounded arc measured from the
 * target's facing at the moment of the hit ({@code combat-durability-nouns.md}).
 *
 * <p>Every assertion here is about behaviour or an invariant. The authored
 * fractions and arcs in the armour catalog are balance output and are
 * deliberately not pinned by any test in this file.
 */
class TimedDirectionalMitigationTest {

    private static final int ARENA = 16;
    private static final int ROW = 5;
    private static final int SHOOTER_X = 2;
    private static final int TARGET_X = 8;

    /**
     * The shooter stands due west of the target in this arena, so this is the
     * bearing a screen must point along to cover the incoming shot. Its
     * opposite is the same screen pointed at nothing.
     */
    private static final float TOWARD_SHOOTER = 90f;
    private static final float AWAY_FROM_SHOOTER = -90f;

    private static final float ARC = 160f;
    private static final float DAMAGE = 12f;
    private static final float PENETRATION = 4f;

    @Test
    void aScreenedHitResolvesLessAndTheDifferenceIsMitigationRatherThanArmor() {
        Arena arena = new Arena();
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 5f);

        Resolved plain = arena.shoot(control);
        Resolved shielded = arena.shoot(screened);

        assertTrue(shielded.total() < plain.total(),
                "a screened hit should cost the target less than the same hit unscreened");
        assertTrue(shielded.mitigated > 0f, "the screen should have refused something");
        assertEquals(0f, plain.mitigated, 1e-4f, "an unscreened target refuses nothing");
        // The screen is not extra armour wearing a costume: it took damage off
        // the hit, so LESS armour was spent, not more.
        assertTrue(shielded.armorLost < plain.armorLost,
                "mitigated damage must not be booked as armour absorption");
        assertEquals(0.5f * DAMAGE, shielded.mitigated, 1e-3f,
                "the screen refuses its fraction of the hit, and the rest goes on to armour");
    }

    /**
     * The rule that keeps an entry a squad problem rather than a solo trick: a
     * breacher who turns to deal with something behind them loses the front they
     * were covering, with no grace period.
     */
    @Test
    void turningOutOfTheArcRemovesTheEffectInTheSameTick()
    {
        Arena arena = new Arena();
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);

        assertTrue(arena.shoot(screened).mitigated > 0f);

        arena.roster().mitigations().face(screened, AWAY_FROM_SHOOTER);
        Resolved turnedAway = arena.shoot(screened);
        Resolved plain = arena.shoot(control);

        assertEquals(0f, turnedAway.mitigated, 1e-4f,
                "a shot arriving outside the arc is not mitigated at all");
        assertEquals(plain.total(), turnedAway.total(), 1e-3f,
                "and the target resolves it exactly as an unscreened one would");
        assertTrue(arena.roster().mitigations().isActive(screened),
                "the screen is still up — it simply does not face this shot");
    }

    @Test
    void expiryLeavesNoResidue() {
        Arena arena = new Arena();
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 0.4f);
        MitigationService screens = arena.roster().mitigations();

        screens.tick(screened, 0.4f);

        assertFalse(screens.isActive(screened));
        assertEquals(0f, screens.fraction(screened), 1e-6f);
        Resolved after = arena.shoot(screened);
        Resolved plain = arena.shoot(control);
        assertEquals(0f, after.mitigated, 1e-4f);
        assertEquals(plain.total(), after.total(), 1e-3f,
                "the target resolves damage exactly as it did before the system was spent");
    }

    /**
     * Summation is how two individually reasonable authored numbers reach
     * immunity without anyone noticing, and the arc rule cannot rescue a design
     * that has already reached 1.
     */
    @Test
    void twoSimultaneousMitigationsResolveAsTheStrongerNotTheSum() {
        Arena arena = new Arena();
        MitigationService screens = arena.roster().mitigations();

        long weakerFirst = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.3f, ARC, 30f);
        screens.grant(weakerFirst, 0.6f, ARC, 30f);
        assertEquals(0.6f, screens.fraction(weakerFirst), 1e-6f);

        long strongerFirst = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.6f, ARC, 30f);
        screens.grant(strongerFirst, 0.3f, ARC, 30f);
        assertEquals(0.6f, screens.fraction(strongerFirst), 1e-6f,
                "a weaker screen must not displace a stronger live one");

        long sole = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.6f, ARC, 30f);
        assertEquals(arena.shoot(sole).mitigated, arena.shoot(weakerFirst).mitigated, 1e-3f,
                "two screens must resolve as the stronger, never as the sum");
    }

    @Test
    void aScreenIsNeverTotalAndNeverAllRound() {
        Arena arena = new Arena();
        long absurd = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);
        MitigationService screens = arena.roster().mitigations();

        screens.grant(absurd, 4f, 900f, 30f);

        assertTrue(screens.fraction(absurd) < 1f, "a hit that cannot land is an off-switch");
        assertTrue(screens.arcDegrees(absurd) < 360f, "a screen has to leave a flank");
        screens.face(absurd, AWAY_FROM_SHOOTER);
        assertEquals(0f, arena.shoot(absurd).mitigated, 1e-4f,
                "even the widest legal arc leaves a bearing it does not cover");
        screens.face(absurd, TOWARD_SHOOTER);
        assertTrue(arena.shoot(absurd).mitigated < DAMAGE,
                "and inside the arc it still refuses less than the whole hit");
    }

    @Test
    void aScreenNeedsAClock() {
        Arena arena = new Arena();
        long target = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);
        MitigationService screens = arena.roster().mitigations();
        screens.clear(target);

        assertFalse(screens.grant(target, 0.5f, ARC, 0f), "there is no mitigation without a duration");
        assertFalse(screens.isActive(target));
    }

    /** Most actors carry nothing that can raise a screen, and asking says so. */
    @Test
    void anOrdinaryActorCarriesNoScreenAndCannotBeGrantedOne() {
        Arena arena = new Arena();
        long plain = arena.spawnTarget();
        MitigationService screens = arena.roster().mitigations();

        assertFalse(screens.has(plain));
        assertFalse(screens.grant(plain, 0.5f, ARC, 5f));
        assertEquals(0f, screens.fractionAgainst(plain, 0f, 0f, 1f, 0f), 1e-6f);
        screens.tick(plain, 1f);
        screens.clear(plain);
    }

    /**
     * Cover is a property of the world the shot crossed; the screen is a
     * property of the target at that instant. A marine behind a wall and behind
     * a screen is simply both, in that order.
     */
    @Test
    void coverResolvesBeforeTheScreenSoNeitherIsBypassedNorDoubleCounted() {
        Arena arena = new Arena();
        long openGround = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);
        arena.coverFacingShooter(NavigationGrid.MAX_COVER);
        long behindCover = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);

        float inCover = arena.shoot(behindCover).mitigated;
        arena.clearCover();
        float inTheOpen = arena.shoot(openGround).mitigated;

        assertTrue(inCover > 0f && inCover < inTheOpen,
                "the screen refuses a fraction of what cover left, not of the raw hit");
    }

    /**
     * An unattributed hit — scripted damage, a strafing run — has no locatable
     * source, so there is no bearing to measure the arc against. Inventing one
     * would make a screen work against fire it was never facing.
     */
    @Test
    void aHitWithNoLocatableSourceIsNeverMitigated() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);

        float before = arena.telemetry().damageMitigated(screened);
        arena.sim().applyDamage(screened, CombatTelemetryService.NO_ATTACKER,
                DAMAGE, PENETRATION, 0f);

        assertEquals(before, arena.telemetry().damageMitigated(screened), 1e-4f);
    }

    /**
     * One damage path. What the sim applied has to be reproducible from the
     * shared model plus the shared arc question — if it were not, prediction and
     * balance evidence would be describing a different fight than the one being
     * fought.
     */
    @Test
    void theSharedCalculationReproducesWhatTheSimulationApplied() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 30f);
        World world = arena.roster().world();

        float fraction = arena.roster().mitigations().fractionAgainst(
                screened, world.x(screened), world.y(screened),
                world.x(arena.shooter()), world.y(arena.shooter()));
        DurabilityModel.Resolution predicted = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(DAMAGE, PENETRATION, fraction, world.armor(screened),
                world.armorRating(screened), world.hp(screened), predicted);

        Resolved applied = arena.shoot(screened);

        assertNotEquals(0f, predicted.mitigatedDamage(), "fixture assumption: the screen faces the shot");
        assertEquals(predicted.mitigatedDamage(), applied.mitigated, 1e-3f);
        assertEquals(predicted.armorDamage(), applied.armorLost, 1e-3f);
        assertEquals(predicted.structureDamage(), applied.hpLost, 1e-3f);
    }

    @Test
    void theModelRefusesATotalScreen() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        assertThrows(IllegalArgumentException.class,
                () -> DurabilityModel.resolveInto(10f, 1f, 1f, 0f, 0f, 10f, out));
        assertThrows(IllegalArgumentException.class,
                () -> DurabilityModel.resolveInto(10f, 1f, -0.5f, 0f, 0f, 10f, out));
    }

    /** Mitigation removes damage; it never puts anything back into a capacity. */
    @Test
    void theModelNeverRestoresACapacity() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(100f, 10f, 0.5f, 5f, 10f, 25f, out);

        assertEquals(50f, out.mitigatedDamage(), 1e-4f);
        assertEquals(5f, out.armorDamage(), 1e-4f, "armour loss is still clamped to the capacity");
        assertTrue(out.armorBroken(), "a mitigated hit that still exceeds the armour breaks it");
        assertTrue(out.structureDamage() > 0f && out.structureDamage() <= 25f);
    }

    /**
     * The screen is aimed from simulation state, so it turns with the wearer
     * rather than with a smoothed render angle. A stationary defender points it
     * at what it is shooting at, which means re-targeting behind itself opens
     * the front it was covering.
     */
    @Test
    void theSweepPointsTheScreenAtWhatTheWearerIsDealingWith() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(0f, 0.5f, ARC, 30f);
        long behind = arena.sim().spawn(new EntitySpec("behind", Faction.MARINE,
                UnitType.MARINE, TARGET_X + 5, ROW));
        MitigationSystem sweep = new MitigationSystem(arena.roster());
        MitigationService screens = arena.roster().mitigations();

        arena.roster().combat().setTargetId(screened, arena.shooter());
        sweep.tick(0f);
        assertEquals(TOWARD_SHOOTER, screens.facingDegrees(screened), 1e-3f);
        assertTrue(arena.shoot(screened).mitigated > 0f);

        arena.roster().combat().setTargetId(screened, behind);
        sweep.tick(0f);
        assertEquals(AWAY_FROM_SHOOTER, screens.facingDegrees(screened), 1e-3f);
        assertEquals(0f, arena.shoot(screened).mitigated, 1e-4f,
                "turning to deal with what is behind costs the front, in the same tick");
    }

    @Test
    void theSweepDrainsTheClockAndDropsTheScreenOnExpiry() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, 0.5f, ARC, 0.25f);
        MitigationSystem sweep = new MitigationSystem(arena.roster());
        MitigationService screens = arena.roster().mitigations();

        sweep.tick(0.1f);
        assertTrue(screens.isActive(screened));
        sweep.tick(0.2f);

        assertFalse(screens.isActive(screened));
        assertEquals(0f, arena.shoot(screened).mitigated, 1e-4f);
    }

    // ---- fixture ----

    /** What one hit actually cost the target, read off armor, structure, and telemetry. */
    private record Resolved(float armorLost, float hpLost, float mitigated) {
        float total() {
            return armorLost + hpLost;
        }
    }

    private static final class Arena {
        private final NavigationGrid grid = new NavigationGrid(ARENA, ARENA);
        private final BattleSimulation sim;
        private final long shooter;
        private int spawned;

        Arena() {
            for (int y = 0; y < ARENA; y++) {
                for (int x = 0; x < ARENA; x++) grid.setWalkableFloor(x, y);
            }
            sim = new BattleSimulation(grid, new CellTopology(ARENA, ARENA));
            shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE,
                    SHOOTER_X, ROW));
        }

        BattleSimulation sim() {
            return sim;
        }

        UnitRosterService roster() {
            return sim.getRoster();
        }

        CombatTelemetryService telemetry() {
            return sim.getRoster().telemetry();
        }

        long shooter() {
            return shooter;
        }

        void coverFacingShooter(int level) {
            grid.setCoverAtFacing(TARGET_X, ROW, NavigationGrid.FACING_W, level);
        }

        void clearCover() {
            grid.setCoverAtFacing(TARGET_X, ROW, NavigationGrid.FACING_W, 0);
        }

        /** A plain armoured defender: no suit capability, so no screen is possible. */
        long spawnTarget() {
            return sim.spawn(targetSpec());
        }

        long spawnScreenedTarget(float facingDegrees, float fraction, float arcDegrees,
                                 float durationSeconds) {
            long id = sim.spawn(targetSpec().integralSystem(screenSource(fraction, arcDegrees)));
            roster().mitigations().face(id, facingDegrees);
            roster().mitigations().grant(id, fraction, arcDegrees, durationSeconds);
            return id;
        }

        private EntitySpec targetSpec() {
            return new EntitySpec("target-" + spawned++, Faction.DEFENDER, UnitType.MARINE,
                    TARGET_X, ROW)
                    // Hit points well past what any one shot here can spend, so a
                    // measurement never turns into a death cascade mid-assertion.
                    .health(10_000f)
                    .armor(40f, 8f, 1f, 1f);
        }

        /** Fires one attributed hit and reports what it cost. */
        Resolved shoot(long target) {
            World world = roster().world();
            float armorBefore = world.armor(target);
            float hpBefore = world.hp(target);
            float mitigatedBefore = telemetry().damageMitigated(target);
            sim.applyDamage(target, shooter, DAMAGE, PENETRATION, 0f);
            return new Resolved(armorBefore - world.armor(target), hpBefore - world.hp(target),
                    telemetry().damageMitigated(target) - mitigatedBefore);
        }
    }

    /**
     * The suit-side source a screen needs to exist at all — spawn attaches the
     * mitigation capability only to an actor carrying something that can raise
     * one. Its own clocks are never spent here; these tests grant directly so
     * they measure mitigation rather than an activation policy.
     */
    private static IntegralSystemDef screenSource(float fraction, float arcDegrees) {
        return new IntegralSystemDef("system.test-screen", "Test screen", EquipmentGrade.SERVICE, "A screen.",
                IntegralSystemEffect.BREACHER_ASSIST, SpecialResourceMode.COOLDOWN,
                3f, 9f, 0, new BreacherAssistSpec(1.1f, fraction, arcDegrees), null,
                new CrossingUnderFireSpec(12f));
    }
}
