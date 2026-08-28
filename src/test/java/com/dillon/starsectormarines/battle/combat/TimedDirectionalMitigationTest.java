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
 * Mitigation as a durability noun: a bounded <em>pool</em> of post-cover damage
 * an actor absorbs, for an explicit duration, across a bounded arc measured from
 * the target's facing at the moment of the hit
 * ({@code combat-durability-nouns.md}).
 *
 * <p>Every assertion here is about behaviour or an invariant. The authored soak
 * amounts and arcs in the armour catalog are balance output and are deliberately
 * not pinned by any test in this file.
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
    /** Comfortably more than one hit, so a test about the arc is not also a test about running dry. */
    private static final float SOAK = 30f;

    @Test
    void aScreenedHitResolvesLessAndTheDifferenceIsMitigationRatherThanArmor() {
        Arena arena = new Arena();
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 5f);

        Resolved plain = arena.shoot(control);
        Resolved shielded = arena.shoot(screened);

        assertTrue(shielded.total() < plain.total(),
                "a screened hit should cost the target less than the same hit unscreened");
        assertTrue(shielded.mitigated > 0f, "the screen should have absorbed something");
        assertEquals(0f, plain.mitigated, 1e-4f, "an unscreened target absorbs nothing");
        // The screen is not extra armour wearing a costume: it took damage off
        // the hit, so LESS armour was spent, not more.
        assertTrue(shielded.armorLost < plain.armorLost,
                "absorbed damage must not be booked as armour absorption");
        assertEquals(DAMAGE, shielded.mitigated, 1e-3f,
                "a pool that covers the whole hit takes the whole hit");
    }

    /**
     * The half of the change that makes concentrated fire an answer: the pool is
     * a quantity, it is spent as it absorbs, and the screen ends when it is gone.
     */
    @Test
    void thePoolIsSpentAsItAbsorbsAndTheScreenEndsWhenItIsEmpty() {
        Arena arena = new Arena();
        // Two hits' worth and a little, so the third is what breaks it.
        float pool = DAMAGE * 2.5f;
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, pool, ARC, 300f);
        MitigationService screens = arena.roster().mitigations();

        assertEquals(pool, screens.soakRemaining(screened), 1e-4f);
        arena.shoot(screened);
        assertEquals(pool - DAMAGE, screens.soakRemaining(screened), 1e-3f,
                "a hit spends exactly what it was absorbed by");
        arena.shoot(screened);
        assertTrue(screens.isActive(screened), "and the screen holds while anything is left");

        Resolved breaking = arena.shoot(screened);

        assertFalse(screens.isActive(screened), "the pool ran out, so the screen is gone");
        // No time is ticked anywhere in this test and the window is five
        // minutes long, so the pool is the only thing that could have ended it.
        assertTrue(screens.breakFlashRemaining(screened) > 0f,
                "and it ended by breaking rather than by timing out");
        assertEquals(pool - 2 * DAMAGE, breaking.mitigated, 1e-3f,
                "the breaking hit absorbs only what was left");
        assertTrue(breaking.total() > 0f, "and the rest of it lands");
    }

    /**
     * A hit larger than what is left neither wastes the overflow nor absorbs it.
     * That is the difference between a pool and a fraction, stated as an
     * arithmetic identity against an unscreened control.
     */
    @Test
    void overflowBeyondThePoolPassesToArmorUnchanged() {
        Arena arena = new Arena();
        float pool = DAMAGE * 0.25f;
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, pool, ARC, 300f);

        Resolved overflowing = arena.shoot(screened);
        Resolved plain = arena.shoot(control);

        assertEquals(pool, overflowing.mitigated, 1e-3f, "the pool absorbs all of itself");
        // Armour here is deep enough never to break, so what it removes is
        // linear in what reached it — which makes the overflow's fate checkable
        // as an identity against the unscreened control rather than a threshold.
        assertEquals(plain.total() * (DAMAGE - pool) / DAMAGE, overflowing.total(), 1e-2f,
                "and the remainder resolves at the ordinary efficiency");
    }

    /** Fire from outside the arc never touches the pool. Flanking bypasses the screen entirely. */
    @Test
    void fireFromOutsideTheArcNeverTouchesThePool() {
        Arena arena = new Arena();
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(AWAY_FROM_SHOOTER, SOAK, ARC, 300f);
        MitigationService screens = arena.roster().mitigations();

        for (int i = 0; i < 10; i++) {
            Resolved flanked = arena.shoot(screened);
            assertEquals(0f, flanked.mitigated, 1e-4f);
        }

        assertEquals(SOAK, screens.soakRemaining(screened), 1e-4f,
                "a screen pointed the wrong way is not worn down by fire it never met");
        assertTrue(screens.isActive(screened));
        assertEquals(arena.shoot(control).total(), arena.shoot(screened).total(), 1e-3f,
                "and the flanked target resolves exactly as an unscreened one would");
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
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 30f);

        assertTrue(arena.shoot(screened).mitigated > 0f);

        arena.roster().mitigations().face(screened, AWAY_FROM_SHOOTER);
        Resolved turnedAway = arena.shoot(screened);
        Resolved plain = arena.shoot(control);

        assertEquals(0f, turnedAway.mitigated, 1e-4f,
                "a shot arriving outside the arc is not absorbed at all");
        assertEquals(plain.total(), turnedAway.total(), 1e-3f,
                "and the target resolves it exactly as an unscreened one would");
        assertTrue(arena.roster().mitigations().isActive(screened),
                "the screen is still up — it simply does not face this shot");
    }

    @Test
    void expiryLeavesNoResidue() {
        Arena arena = new Arena();
        long control = arena.spawnTarget();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 0.4f);
        MitigationService screens = arena.roster().mitigations();

        screens.tick(screened, 0.4f);

        assertFalse(screens.isActive(screened));
        assertEquals(0f, screens.soakRemaining(screened), 1e-6f,
                "an unspent pool does not survive its own window");
        Resolved after = arena.shoot(screened);
        Resolved plain = arena.shoot(control);
        assertEquals(0f, after.mitigated, 1e-4f);
        assertEquals(plain.total(), after.total(), 1e-3f,
                "the target resolves damage exactly as it did before the system was spent");
    }

    /**
     * Summation is how two individually reasonable authored numbers reach an
     * unbeatable pool without anyone noticing, and the arc rule cannot rescue a
     * design whose pool no fight can spend.
     */
    @Test
    void twoSimultaneousMitigationsResolveAsTheLargerNotTheSum() {
        Arena arena = new Arena();
        MitigationService screens = arena.roster().mitigations();

        long smallerFirst = arena.spawnScreenedTarget(TOWARD_SHOOTER, 10f, ARC, 30f);
        screens.grant(smallerFirst, 25f, ARC, 30f);
        assertEquals(25f, screens.soakRemaining(smallerFirst), 1e-6f);

        long largerFirst = arena.spawnScreenedTarget(TOWARD_SHOOTER, 25f, ARC, 30f);
        screens.grant(largerFirst, 10f, ARC, 30f);
        assertEquals(25f, screens.soakRemaining(largerFirst), 1e-6f,
                "a smaller screen must not displace a larger live one");

        long sole = arena.spawnScreenedTarget(TOWARD_SHOOTER, 25f, ARC, 30f);
        assertEquals(arena.shoot(sole).mitigated, arena.shoot(smallerFirst).mitigated, 1e-3f,
                "two screens must resolve as the larger, never as the sum");
    }

    /**
     * The arc law is enforced by the service rather than left to authoring, and
     * finiteness needs no separate rule: a pool that can be spent is a pool
     * massed fire can beat.
     */
    @Test
    void aScreenIsNeverAllRoundAndAlwaysRunsOut() {
        Arena arena = new Arena();
        long absurd = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 30f);
        MitigationService screens = arena.roster().mitigations();

        screens.grant(absurd, 1_000f, 900f, 30f);

        assertTrue(screens.arcDegrees(absurd) < 360f, "a screen has to leave a flank");
        screens.face(absurd, AWAY_FROM_SHOOTER);
        assertEquals(0f, arena.shoot(absurd).mitigated, 1e-4f,
                "even the widest legal arc leaves a bearing it does not cover");

        screens.face(absurd, TOWARD_SHOOTER);
        float pool = screens.soakRemaining(absurd);
        while (screens.isActive(absurd)) arena.shoot(absurd);
        assertEquals(0f, screens.soakRemaining(absurd), 1e-4f,
                "however large the pool, enough fire spends it");
        assertTrue(pool > 0f, "fixture assumption: there was a pool to spend");
    }

    @Test
    void aScreenNeedsAClockAndSomethingToSpend() {
        Arena arena = new Arena();
        long target = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 30f);
        MitigationService screens = arena.roster().mitigations();
        screens.clear(target);

        assertFalse(screens.grant(target, SOAK, ARC, 0f),
                "there is no mitigation without a duration");
        assertFalse(screens.grant(target, 0f, ARC, 5f),
                "and none without a pool to absorb with");
        assertFalse(screens.isActive(target));
    }

    /** Most actors carry nothing that can raise a screen, and asking says so. */
    @Test
    void anOrdinaryActorCarriesNoScreenAndCannotBeGrantedOne() {
        Arena arena = new Arena();
        long plain = arena.spawnTarget();
        MitigationService screens = arena.roster().mitigations();

        assertFalse(screens.has(plain));
        assertFalse(screens.grant(plain, SOAK, ARC, 5f));
        assertEquals(0f, screens.soakAgainst(plain, 0f, 0f, 1f, 0f), 1e-6f);
        screens.tick(plain, 1f);
        screens.absorb(plain, 5f);
        screens.clear(plain);
    }

    /**
     * Cover is a property of the world the shot crossed; the screen is a
     * property of the target at that instant. A marine behind a wall and behind
     * a screen is simply both, in that order — so the screen spends its pool on
     * what cover left, not on the raw hit.
     */
    @Test
    void coverResolvesBeforeTheScreenSoNeitherIsBypassedNorDoubleCounted() {
        Arena arena = new Arena();
        long openGround = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 30f);
        arena.coverFacingShooter(NavigationGrid.MAX_COVER);
        long behindCover = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 30f);

        float spentInCover = arena.shoot(behindCover).mitigated;
        arena.clearCover();
        float spentInTheOpen = arena.shoot(openGround).mitigated;

        assertTrue(spentInCover > 0f && spentInCover < spentInTheOpen,
                "a covered target hands its screen a smaller hit to absorb, so the pool"
                        + " lasts longer behind a wall");
    }

    /**
     * An unattributed hit — scripted damage, a strafing run — has no locatable
     * source, so there is no bearing to measure the arc against. Inventing one
     * would make a screen work against fire it was never facing.
     */
    @Test
    void aHitWithNoLocatableSourceIsNeverMitigated() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 30f);

        float before = arena.telemetry().damageMitigated(screened);
        arena.sim().applyDamage(screened, CombatTelemetryService.NO_ATTACKER,
                DAMAGE, PENETRATION, 0f);

        assertEquals(before, arena.telemetry().damageMitigated(screened), 1e-4f);
        assertEquals(SOAK, arena.roster().mitigations().soakRemaining(screened), 1e-4f,
                "and it costs the pool nothing");
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
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, DAMAGE * 0.5f, ARC, 30f);
        World world = arena.roster().world();

        float soak = arena.roster().mitigations().soakAgainst(
                screened, world.x(screened), world.y(screened),
                world.x(arena.shooter()), world.y(arena.shooter()));
        DurabilityModel.Resolution predicted = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(DAMAGE, PENETRATION, soak, world.armor(screened),
                world.armorRating(screened), world.hp(screened), predicted);

        Resolved applied = arena.shoot(screened);

        assertNotEquals(0f, predicted.mitigatedDamage(), "fixture assumption: the screen faces the shot");
        assertEquals(predicted.mitigatedDamage(), applied.mitigated, 1e-3f);
        assertEquals(predicted.armorDamage(), applied.armorLost, 1e-3f);
        assertEquals(predicted.structureDamage(), applied.hpLost, 1e-3f);
    }

    @Test
    void theModelRefusesANonsensePool() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        assertThrows(IllegalArgumentException.class,
                () -> DurabilityModel.resolveInto(10f, 1f, -0.5f, 0f, 0f, 10f, out));
        assertThrows(IllegalArgumentException.class,
                () -> DurabilityModel.resolveInto(10f, 1f, Float.NaN, 0f, 0f, 10f, out));
    }

    /** A pool larger than the hit takes the hit, and never more than the hit. */
    @Test
    void theModelNeverAbsorbsMoreThanTheHit() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(100f, 10f, 1_000f, 5f, 10f, 25f, out);

        assertEquals(100f, out.mitigatedDamage(), 1e-4f);
        assertEquals(0f, out.armorDamage(), 1e-4f);
        assertEquals(0f, out.structureDamage(), 1e-4f);
        assertFalse(out.armorBroken());
    }

    /** Mitigation removes damage; it never puts anything back into a capacity. */
    @Test
    void theModelNeverRestoresACapacity() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(100f, 10f, 50f, 5f, 10f, 25f, out);

        assertEquals(50f, out.mitigatedDamage(), 1e-4f);
        assertEquals(5f, out.armorDamage(), 1e-4f, "armour loss is still clamped to the capacity");
        assertTrue(out.armorBroken(), "an absorbed hit that still exceeds the armour breaks it");
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
        long screened = arena.spawnScreenedTarget(0f, SOAK, ARC, 30f);
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
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 0.25f);
        MitigationSystem sweep = new MitigationSystem(arena.roster());
        MitigationService screens = arena.roster().mitigations();

        sweep.tick(0.1f);
        assertTrue(screens.isActive(screened));
        sweep.tick(0.2f);

        assertFalse(screens.isActive(screened));
        assertEquals(0f, arena.shoot(screened).mitigated, 1e-4f);
    }

    /** A screen that broke leaves a mark for presentation, and the sweep drains it. */
    @Test
    void breakingLeavesAMarkThatDrainsWithTheSweep() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, DAMAGE * 0.5f, ARC, 300f);
        MitigationService screens = arena.roster().mitigations();
        MitigationSystem sweep = new MitigationSystem(arena.roster());

        assertEquals(0f, screens.breakFlashRemaining(screened), 1e-6f);
        arena.shoot(screened);

        assertFalse(screens.isActive(screened));
        assertTrue(screens.breakFlashRemaining(screened) > 0f,
                "a screen beaten down is distinguishable from one that timed out");

        sweep.tick(MitigationService.BREAK_FLASH_SECONDS);
        assertEquals(0f, screens.breakFlashRemaining(screened), 1e-6f);
    }

    /** A window that simply ran out is not a break, and must not be marked as one. */
    @Test
    void aWindowThatRanOutLeavesNoBreakMark() {
        Arena arena = new Arena();
        long screened = arena.spawnScreenedTarget(TOWARD_SHOOTER, SOAK, ARC, 0.25f);
        MitigationService screens = arena.roster().mitigations();

        new MitigationSystem(arena.roster()).tick(0.3f);

        assertFalse(screens.isActive(screened));
        assertEquals(0f, screens.breakFlashRemaining(screened), 1e-6f);
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

        long spawnScreenedTarget(float facingDegrees, float soakAmount, float arcDegrees,
                                 float durationSeconds) {
            long id = sim.spawn(targetSpec().integralSystem(screenSource(soakAmount, arcDegrees)));
            roster().mitigations().face(id, facingDegrees);
            roster().mitigations().grant(id, soakAmount, arcDegrees, durationSeconds);
            return id;
        }

        private EntitySpec targetSpec() {
            return new EntitySpec("target-" + spawned++, Faction.DEFENDER, UnitType.MARINE,
                    TARGET_X, ROW)
                    // Hit points and armour well past what these hits can spend,
                    // so a measurement never turns into an armour break or a
                    // death cascade mid-assertion.
                    .health(10_000f)
                    .armor(4_000f, 8f, 1f, 1f);
        }

        /** Fires one attributed hit and reports what it cost. */
        Resolved shoot(long target) {
            return shoot(target, DAMAGE);
        }

        private Resolved shoot(long target, float damage) {
            World world = roster().world();
            float armorBefore = world.armor(target);
            float hpBefore = world.hp(target);
            float mitigatedBefore = telemetry().damageMitigated(target);
            sim.applyDamage(target, shooter, damage, PENETRATION, 0f);
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
    private static IntegralSystemDef screenSource(float soakAmount, float arcDegrees) {
        return new IntegralSystemDef("system.test-screen", "Test screen", EquipmentGrade.SERVICE, "A screen.",
                IntegralSystemEffect.BREACHER_ASSIST, SpecialResourceMode.COOLDOWN,
                3f, 9f, 0, new BreacherAssistSpec(1.1f, soakAmount, arcDegrees), null, null,
                new CrossingUnderFireSpec(12f));
    }
}
