package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;

import java.util.List;

/**
 * LR Support mech goal: hold an LR-band overwatch position and lob LRMs
 * at the squad's known threat axis. {@link Goal.Priority#MISSION} so it
 * outranks the ambient {@link MechEliminateEnemiesGoal} when both are
 * relevant.
 *
 * <p>Relevance is gated on two conditions:
 * <ol>
 *   <li>At least one alive squad member has {@link MechRole#LR_SUPPORT}.
 *       In a mixed-role squad this is "some member can do overwatch"; the
 *       action's per-member role branching handles the rest.</li>
 *   <li>The squad has a known contact ({@code lastSeenEnemyX/Y} set by
 *       the alert-update pass) — a "kill corridor" needs an anchor.</li>
 * </ol>
 *
 * <p>Without a known contact, relevance drops to zero and the ambient
 * {@link MechEliminateEnemiesGoal} takes over with parity engagement —
 * which itself handles target acquisition. The two goals are designed to
 * trade off cleanly as contact state changes.
 *
 * <p>Custom-plans a single-step {@link OverwatchKillZone} action. The
 * planner's backward-chaining search would produce the same one-step
 * plan; bypassing it via {@code customPlan} keeps the dispatch cheap.
 */
public final class OverwatchKillZoneGoal implements Goal {

    public static final OverwatchKillZoneGoal INSTANCE = new OverwatchKillZoneGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.KILL_ZONE_COVERED, true);

    private OverwatchKillZoneGoal() {}

    @Override public String name() { return "OverwatchKillZone"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        // Yield to SurviveContact when the squad is morale-broken. Same
        // carve-out shape as {@link ClearAssignedZoneGoal} — a role-driven
        // hold is a tactical hint, not a unit-level objective, and a broken
        // squad needs the SURVIVAL bucket to win so BreakContact runs. Playtest
        // dump squad_0 caught LR_SUPPORT mechs sitting on overwatch at 8/90 HP
        // because MISSION outranks SURVIVAL unconditionally.
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        if (squad.lastSeenEnemyX < 0 || squad.lastSeenEnemyY < 0) return 0f;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.mech != null && u.mech.role == MechRole.LR_SUPPORT) return 1f;
        }
        return 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(OverwatchKillZone.INSTANCE)));
    }
}
