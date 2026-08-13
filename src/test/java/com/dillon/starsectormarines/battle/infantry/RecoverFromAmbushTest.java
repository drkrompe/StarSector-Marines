package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link RecoverFromAmbush} — the Story A re-trigger that wires
 * {@link com.dillon.starsectormarines.battle.infantry.BreakLOS} into
 * goal selection. Verifies relevance reads {@link Predicate#UNDER_FIRE_AT_LOS},
 * priority is SURVIVAL, desiredState wants {@code UNDER_FIRE_AT_LOS=false}
 * (so backward-chaining picks BreakLOS), and the bucket-priority math gives
 * SurviveContact the win when morale is also broken.
 */
public class RecoverFromAmbushTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(40, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(40, 20));
    }

    @Test
    public void priorityIsSurvival() {
        assertEquals(Goal.Priority.SURVIVAL, RecoverFromAmbush.INSTANCE.priority(),
                "RecoverFromAmbush lives in SURVIVAL — beats ENGAGEMENT, can tie SurviveContact");
    }

    @Test
    public void relevanceZeroWhenNotUnderFire() {
        assertEquals(0f, RecoverFromAmbush.INSTANCE.relevance(
                WorldState.EMPTY.with(Predicate.UNDER_FIRE_AT_LOS, false),
                null, null),
                "no incoming fire at LoS — goal inactive");
    }

    @Test
    public void relevancePositiveWhenUnderFire() {
        assertTrue(RecoverFromAmbush.INSTANCE.relevance(
                WorldState.EMPTY.with(Predicate.UNDER_FIRE_AT_LOS, true),
                null, null) > 0f,
                "incoming fire at LoS triggers the goal");
    }

    @Test
    public void desiredStateWantsUnderFireFalse() {
        WorldState d = RecoverFromAmbush.INSTANCE.desiredState(null, null);
        assertTrue(d.isSpecified(Predicate.UNDER_FIRE_AT_LOS),
                "desiredState must constrain UNDER_FIRE_AT_LOS for the planner to chain back");
        assertFalse(d.get(Predicate.UNDER_FIRE_AT_LOS),
                "want UNDER_FIRE_AT_LOS flipped to false — that's what BreakLOS achieves");
    }

    @Test
    public void noCustomPlanUsesBackwardChaining() {
        assertNull(RecoverFromAmbush.INSTANCE.customPlan(null, null),
                "RecoverFromAmbush relies on the standard planner — BreakLOS is the only producer");
    }

    @Test
    public void surviveContactWinsTieWhenMoraleAlsoBroken() {
        // Both goals in SURVIVAL; SurviveContact = 1.0, RecoverFromAmbush = 0.5
        // → SurviveContact wins on raw relevance. Verified through the actual
        // picker so the rule isn't a private comment buried in the goal class.
        WorldState ws = WorldState.EMPTY
                .with(Predicate.MORALE_BROKEN, true)
                .with(Predicate.UNDER_FIRE_AT_LOS, true);
        Goal picked = Goal.pickMostRelevant(
                List.of(SurviveContact.INSTANCE, RecoverFromAmbush.INSTANCE),
                ws, null, null);
        assertSame(SurviveContact.INSTANCE, picked,
                "broken morale + ambush → full retreat (SurviveContact), not duck-and-re-engage");
    }

    @Test
    public void recoverFromAmbushWinsWhenOnlyUnderFire() {
        WorldState ws = WorldState.EMPTY
                .with(Predicate.MORALE_BROKEN, false)
                .with(Predicate.UNDER_FIRE_AT_LOS, true);
        Goal picked = Goal.pickMostRelevant(
                List.of(SurviveContact.INSTANCE, RecoverFromAmbush.INSTANCE),
                ws, null, null);
        assertSame(RecoverFromAmbush.INSTANCE, picked,
                "intact morale but in a fire lane → break LoS");
    }

    @Test
    public void recoverFromAmbushPreemptsPatrolReinforcement() {
        WorldState ws = WorldState.EMPTY
                .with(Predicate.MORALE_BROKEN, false)
                .with(Predicate.UNDER_FIRE_AT_LOS, true);
        Squad patrol = new Squad(1, Faction.DEFENDER);
        patrol.alertLevel = SquadAlertLevel.ENGAGED;
        patrol.lastSeenEnemyX = 20;
        patrol.lastSeenEnemyY = 20;
        patrol.centroidX = 40f;
        patrol.centroidY = 40f;
        Goal picked = Goal.pickMostRelevant(
                List.of(ReinforceContact.INSTANCE, RecoverFromAmbush.INSTANCE),
                ws, patrol, null);
        assertSame(RecoverFromAmbush.INSTANCE, picked,
                "incoming fire must interrupt a patrol flank instead of being outranked as a mission");
    }

    @Test
    public void firstIncomingShotInterruptsFreshFlankPlanImmediately() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad patrol = sim.getSquad(squadId);
        long defender = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 5, 5)
                .squad(squadId));
        sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 20, 5));
        patrol.originalSize = 1;
        patrol.aliveMembers = 1;
        patrol.aliveMembersAtLastPlan = 1;
        patrol.timeSinceReplan = 0f;
        SquadPlan.Step flank = new SquadPlan.Step(new FlankApproach(25, 10));
        flank.assignments.put("any", List.of(defender));
        patrol.currentPlan = new SquadPlan(List.of(flank));
        patrol.currentGoal = ReinforceContact.INSTANCE;

        sim.postShot(new ShotEvent(20.5f, 5.5f, 5.5f, 5.5f,
                true, Faction.MARINE, 1.0f));
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(patrol._underFireAtLosThisTick,
                "the alert pass must expose incoming fire for an ordinary patrol, not only a garrison");
        assertSame(RecoverFromAmbush.INSTANCE, patrol.currentGoal,
                "a fresh flank plan must be replaced on the first incoming shot, before the two-second cadence");
        assertSame(BreakLOS.INSTANCE, patrol.currentPlan.steps().get(0).action,
                "the immediate survival replan should execute BreakLOS");
    }

    @Test
    public void plannerComposesSingleBreakLOSStep() {
        // End-to-end: feed the desiredState into the real backward-chaining
        // planner with the action library and verify it composes [BreakLOS].
        // This is what guards against a future change making BreakLOS
        // un-pickable (e.g. someone adding a precondition the current
        // world doesn't satisfy).
        WorldState current = WorldState.EMPTY.with(Predicate.UNDER_FIRE_AT_LOS, true);
        WorldState desired = RecoverFromAmbush.INSTANCE.desiredState(null, null);
        List<Action> library = List.of(EngagePosture.INSTANCE, BreakLOS.INSTANCE);

        SquadPlan plan = Planner.plan(current, desired, library, null, null, 64);
        assertNotNull(plan, "planner must find a plan when BreakLOS satisfies the goal");
        assertEquals(1, plan.stepCount(), "single-step plan: BreakLOS produces UNDER_FIRE_AT_LOS=false");
        assertSame(BreakLOS.INSTANCE, plan.steps().get(0).action,
                "the step must be BreakLOS — no other action in the library produces that effect");
    }
}
