package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.turret.DefensePost;

import java.util.List;

/**
 * Default goal for GARRISON-routed defender squads. Always relevant when the
 * squad carries the {@link Squad#holdsFireUntilKillZone} flag (set at squad
 * mint by {@code BattleSetup} for nodes whose default role is GARRISON).
 *
 * <p>{@link Priority#MISSION} — outranks {@link EliminateEnemiesGoal} so the
 * squad doesn't abandon its post to chase a visible enemy. Loses to
 * {@link GarrisonAmbush} (same bucket, registered earlier so it wins the
 * tie) for chokepoint-shaped zones, and to {@link SurviveContact} (higher
 * SURVIVAL priority) when morale breaks.
 *
 * <p>Custom-plan: for a squad still linked to a live turret emplacement
 * ({@link Squad#defensePost} set — turrets standing), a {@link GuardPostPatrol}
 * that wanders the post's bounding box ({@link Squad#patrolRadius} around the
 * post anchor). Otherwise — a released turret squad (post demolished) or a
 * non-turret garrison post — a single-step plan of {@link HoldPost}, the tight
 * static hold. Either action runs perpetually (always returns RUNNING); the
 * squad-level periodic replan is what swaps goals when conditions change
 * (morale broken → SurviveContact; chokepoint geometry exposed →
 * GarrisonAmbush).
 */
public final class GuardPost implements Goal {

    public static final GuardPost INSTANCE = new GuardPost();

    private GuardPost() {}

    @Override public String name() { return "GuardPost"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (!squad.holdsFireUntilKillZone) return 0f;
        // Yield to GarrisonCompound for the primary node of a multi-building
        // compound — that squad patrols the whole base instead of leashing to
        // one post. Non-primary squads keep holding their own building here.
        if (GarrisonCompound.defenderAreaPatrol(squad, sim)) return 0f;
        return 1.0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        DefensePost post = squad.defensePost;
        if (post != null) {
            return new SquadPlan(List.of(new SquadPlan.Step(
                    new GuardPostPatrol(post.anchorX, post.anchorY, squad.patrolRadius))));
        }
        return new SquadPlan(List.of(new SquadPlan.Step(HoldPost.INSTANCE)));
    }
}
