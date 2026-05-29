package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;

import java.util.List;

/**
 * Armored Support mech goal: pace a designated friendly infantry squad and
 * fire heavy weapons from a backstop position. {@link Goal.Priority#MISSION}
 * — outranks the ambient {@link MechEliminateEnemiesGoal} when both score
 * positive relevance, and ties with {@link OverwatchKillZoneGoal} (the
 * tie-break is goal-list order in {@code GoapMechBehavior.MECH_GOALS},
 * which deliberately lists overwatch first).
 *
 * <p>Relevance gate: at least one alive squad member has
 * {@link MechRole#ARMORED_SUPPORT} AND a friendly infantry squad exists
 * to back. The friendly-squad check defers to the action's
 * {@code pickBackedSquad} when it actually runs — we only test for
 * <em>existence</em> here, not viability of any particular candidate.
 *
 * <p>No reading of contact state: an Armored Support mech "marches with
 * the marines" from spawn forward, before contact lands. The action's
 * pacing logic handles the no-contact case (holds within follow distance
 * of centroid without a specific "behind" direction until lastSeenEnemy
 * is set).
 *
 * <p>Custom-plans a single-step {@link BackstopAssignedSquad} action.
 */
public final class BackstopAssignedSquadGoal implements Goal {

    public static final BackstopAssignedSquadGoal INSTANCE = new BackstopAssignedSquadGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.SQUAD_BACKED, true);

    private BackstopAssignedSquadGoal() {}

    @Override public String name() { return "BackstopAssignedSquad"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        // Yield to SurviveContact when morale-broken — see the matching gate
        // in {@link OverwatchKillZoneGoal#relevance}. Backstop is a pacing
        // hint, not a unit-level objective; a mauled armored-support squad
        // pulls back regardless of orders and re-attaches to its infantry
        // squad on the next replan after morale recovers.
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        boolean hasArmored = false;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.mech != null && u.mech.role == MechRole.ARMORED_SUPPORT) {
                hasArmored = true;
                break;
            }
        }
        if (!hasArmored) return 0f;
        // At least one friendly non-mech squad on the same side must exist.
        for (Squad other : sim.getSquads()) {
            if (other.id == squad.id) continue;
            if (other.faction != squad.faction) continue;
            if (other.aliveMembers == 0) continue;
            if (other.isMechSquad()) continue;
            return 1f;
        }
        return 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(BackstopAssignedSquad.INSTANCE)));
    }
}
