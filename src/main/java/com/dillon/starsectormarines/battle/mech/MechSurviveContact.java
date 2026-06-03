package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.unit.Entity;

import java.util.List;

/**
 * Mech sibling of {@link SurviveContact} — a mech squad whose per-chassis
 * morale aggregation flips {@link Squad#moraleBroken} pulls back to cover,
 * keeps firing all three weapon tracks, and re-enters the fight if morale
 * recovers. Lives in the {@link Goal.Priority#SURVIVAL} bucket so it
 * outranks {@link MechEliminateEnemiesGoal} (ENGAGEMENT) but loses to the
 * MISSION-tier role goals — except those goals carve themselves out when
 * MORALE_BROKEN trips, so in practice SurviveContact wins whenever it's
 * relevant. See the MORALE_BROKEN gate in
 * {@link OverwatchKillZoneGoal#relevance(WorldState, Squad, BattleSimulation)}
 * and {@link BackstopAssignedSquadGoal#relevance(WorldState, Squad, BattleSimulation)}.
 *
 * <p>Custom-plan: single-step {@link MechBreakContact}. The action runs
 * perpetually; the 2s replan window is what re-evaluates morale state.
 * Per-mech HP-threshold drain (see
 * {@link BattleSimulation#applyDamage(Entity, float, float, float)})
 * is monotonic, so a heavily damaged mech stays broken; combined with the
 * armor-gone cap dropping the morale ceiling to
 * {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_MORALE_ARMOR_GONE_CAP}, the squad is locked
 * in MechSurviveContact for the rest of the fight once a majority of its
 * members cross the bottom HP threshold — the intended "wounded mech
 * withdraws" moment from roadmap/ai/14-mech-stage1.md.
 */
public final class MechSurviveContact implements Goal {

    public static final MechSurviveContact INSTANCE = new MechSurviveContact();

    private MechSurviveContact() {}

    @Override public String name() { return "MechSurviveContact"; }
    @Override public Priority priority() { return Priority.SURVIVAL; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        return state.get(Predicate.MORALE_BROKEN) ? 1.0f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY.with(Predicate.MORALE_BROKEN, false);
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(MechBreakContact.INSTANCE)));
    }
}
