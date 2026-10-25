package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who a trained shooter shoots when a friend stands in the lane to the one he
 * was told to shoot.
 *
 * <p>The lane and the clock are faked — the firing gate's geometry is
 * {@code FiringLaneTest}'s — so each case names exactly which enemy is blocked
 * and when, and asks only the rule: wait out a hold shorter than a switch
 * costs, then shoot somebody with a clear lane and finish him, and hold when
 * there is nobody else or a step-aside is under way.
 */
public class LaneRetargetTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final int ROW = 20;
    private static final int SHOOTER_X = 10;

    /** Longer than any experience tier's registration delay, in ticks. */
    private static final int PAST_THE_SWITCH_COST = 60;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long marine(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, x, y));
    }

    private static long enemy(BattleSimulation sim, int x, int y) {
        EntitySpec spec = new EntitySpec("e", Faction.DEFENDER, UnitType.MARINE, x, y);
        spec.moveSpeed = 0f;
        return sim.spawn(spec);
    }

    /** A rule whose lane is blocked on {@code blocked[0]} and whose clock reads {@code now[0]}. */
    private static LaneRetarget rule(BattleSimulation sim, long[] blocked, int[] now) {
        return new LaneRetarget((shooter, target) -> target == blocked[0],
                sim.getTacticalScoring(), sim.combat(), sim.world(), () -> now[0]);
    }

    @Test
    public void aBlockedTargetGivesWayOnceTheHoldHasCostAsMuchAsASwitch() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        long clear = enemy(sim, SHOOTER_X + 8, ROW + 6);
        sim.advance(BattleSimulation.TICK_DT);
        long[] blocked = {target};
        int[] now = {0};
        LaneRetarget rule = rule(sim, blocked, now);

        assertEquals(target, rule.redirect(shooter, target),
                "a lane only just blocked may clear sooner than a new target registers");
        now[0] = PAST_THE_SWITCH_COST;
        assertEquals(clear, rule.redirect(shooter, target));
    }

    /** A hold shorter than a switch costs is waited out: the target never changes. */
    @Test
    public void aLaneThatClearsBeforeASwitchPaysIsWaitedOut() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        enemy(sim, SHOOTER_X + 8, ROW + 6);
        sim.advance(BattleSimulation.TICK_DT);
        long[] blocked = {target};
        int[] now = {0};
        LaneRetarget rule = rule(sim, blocked, now);

        rule.redirect(shooter, target);
        blocked[0] = 0L;
        now[0] = 1;
        assertEquals(target, rule.redirect(shooter, target));
        blocked[0] = target;
        now[0] = PAST_THE_SWITCH_COST;
        assertEquals(target, rule.redirect(shooter, target),
                "a new block starts its own wait rather than inheriting the old one");
    }

    /**
     * Once switched he finishes the alternative: going back the moment the
     * first lane clears would cost a second registration, and a lane that
     * flickers would cost one every tick.
     */
    @Test
    public void theAlternativeIsFinishedWhenTheFirstLaneClears() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        long clear = enemy(sim, SHOOTER_X + 8, ROW + 6);
        sim.advance(BattleSimulation.TICK_DT);
        long[] blocked = {target};
        int[] now = {0};
        LaneRetarget rule = rule(sim, blocked, now);
        rule.redirect(shooter, target);
        now[0] = PAST_THE_SWITCH_COST;
        rule.redirect(shooter, target);

        blocked[0] = 0L;
        assertEquals(clear, rule.redirect(shooter, target));
        blocked[0] = clear;
        assertEquals(target, rule.redirect(shooter, target),
                "and leaves it when its own lane fills, for the first target's clear one");
    }

    @Test
    public void aClearTargetIsKept() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        enemy(sim, SHOOTER_X + 4, ROW + 2);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(target, rule(sim, new long[]{0L}, new int[]{0}).redirect(shooter, target),
                "a nearer enemy is no reason to leave a target he can shoot");
    }

    /** With nobody else to shoot the target stands and the gate holds, as it did before the rule. */
    @Test
    public void withNobodyElseInRangeTheTargetStands() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        enemy(sim, W - 2, H - 2);
        sim.combat().setAttackRange(shooter, 20f);
        sim.advance(BattleSimulation.TICK_DT);
        int[] now = {0};
        LaneRetarget rule = rule(sim, new long[]{target}, now);

        rule.redirect(shooter, target);
        now[0] = PAST_THE_SWITCH_COST;
        assertEquals(target, rule.redirect(shooter, target));
    }

    /** Registration restarts on every switch, so an enemy he is already registered on is chosen over a nearer one. */
    @Test
    public void anEnemyAlreadyRegisteredIsChosenOverANearerOne() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        long registered = enemy(sim, SHOOTER_X + 10, ROW + 8);
        enemy(sim, SHOOTER_X + 4, ROW - 3);
        sim.advance(BattleSimulation.TICK_DT);
        sim.combat().setReflexTargetId(shooter, registered);
        int[] now = {0};
        LaneRetarget rule = rule(sim, new long[]{target}, now);

        rule.redirect(shooter, target);
        now[0] = PAST_THE_SWITCH_COST;
        assertEquals(registered, rule.redirect(shooter, target));
    }

    /** A step-aside under way will clear this lane, so the squad's target is not traded for another. */
    @Test
    public void aMarineSteppingAsideKeepsHisTarget() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long target = enemy(sim, SHOOTER_X + 12, ROW);
        enemy(sim, SHOOTER_X + 8, ROW + 6);
        sim.advance(BattleSimulation.TICK_DT);
        int[] now = {0};
        LaneRetarget rule = rule(sim, new long[]{target}, now);
        rule.redirect(shooter, target);
        sim.setPath(shooter, new int[]{SHOOTER_X, ROW + 1});
        sim.world().setSidestepTimer(shooter, 1f);
        now[0] = PAST_THE_SWITCH_COST;

        assertEquals(target, rule.redirect(shooter, target));
    }

    /** The rule sits where fire is queued, so the registered threat is the one the round goes at. */
    @Test
    public void queuedFireGoesToTheRedirectedTargetAndRegistersIt() {
        BattleSimulation sim = arena();
        long shooter = marine(sim, SHOOTER_X, ROW);
        long asked = enemy(sim, SHOOTER_X + 12, ROW);
        long redirected = enemy(sim, SHOOTER_X + 8, ROW + 6);
        sim.advance(BattleSimulation.TICK_DT);
        sim.combat().setFireRedirect((s, t) -> t == asked ? redirected : t);

        sim.combat().setFireIntent(shooter, asked, FireStance.STANCED, false);

        assertEquals(redirected, sim.combat().fireTargetId(shooter));
        assertEquals(redirected, sim.combat().reflexTargetId(shooter));
    }
}
