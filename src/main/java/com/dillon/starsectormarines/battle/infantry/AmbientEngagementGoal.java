package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.AmbientAdvance;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/**
 * The floor of the goal ladder: what a squad does when nothing else it wants
 * can be acted on.
 *
 * <p>{@code ai-nouns.md} has always said a squad without workable orders "falls
 * through to ambient engagement rather than inventing a mission", and the
 * priority ladder has always ended in {@link Priority#IDLE}. Nothing occupied
 * that bucket, so the fall-through fell through to nothing: a squad whose
 * mission goal declined and whose engagement goals could not chain held no
 * plan, and a planless member has its path cleared by design. The result was a
 * composed, unbroken squad standing still under fire until its commander's next
 * pulse — with no way to recover on its own, because the individual tier owns
 * no movement and the squad tier had produced nothing to execute.
 *
 * <p><b>A cue, never an objective.</b> The destination is the squad's own
 * evidence and only ever that: the freshest hostile it still believes in, or
 * failing that the bearing of something it recently heard. Both are sanctioned
 * squad-local knowledge — an anonymous audible bearing is explicitly "a valid
 * investigation cue without inventing a hostile identity". What this goal must
 * not do is pick a zone or a compound and go take it; that is the squad
 * inventing a mission, which is the half of the law that was already honoured.
 *
 * <p>With no cue at all the goal declines. A squad that believes in nobody and
 * has heard nothing has genuinely nothing to advance on, and marching it
 * somewhere for the sake of motion would be worse than holding: it would be
 * this goal inventing the mission it exists not to invent.
 */
public final class AmbientEngagementGoal implements Goal {

    public static final AmbientEngagementGoal INSTANCE = new AmbientEngagementGoal();

    /**
     * How stale a heard bearing may be and still be worth walking to.
     *
     * <p>Well inside the belief lifetime, because this is a weaker claim than a
     * belief: a bearing says only that something happened over there, and the
     * older it is the more likely the squad walks to where a fight was rather
     * than where one is. Long enough to survive the seconds of quiet between
     * bursts that would otherwise make a squad give up on a firefight it can
     * hear.
     */
    static final float BEARING_INVESTIGATION_SECONDS = 6f;

    private static final int BEARING_INVESTIGATION_TICKS = Math.round(
            BEARING_INVESTIGATION_SECONDS / BattleSimulation.TICK_DT);

    private AmbientEngagementGoal() {}

    @Override public String name() { return "AmbientEngagement"; }

    @Override public Priority priority() { return Priority.IDLE; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        // Survival owns a broken squad; a floor goal must never argue with it.
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        return cue(squad, sim) != null ? 1f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        int[] cue = cue(squad, sim);
        if (cue == null) return null;
        // Plan stickiness, for the reason every push goal needs it: the squad
        // replans on contact edges, doctrine flips and casualties, and
        // re-synthesizing the same step would discard the advance's commit
        // state and its bounding progress along with it.
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof AmbientAdvance advance
                    && advance.destX() == cue[0] && advance.destY() == cue[1]) {
                return current;
            }
        }
        return new SquadPlan(List.of(
                new SquadPlan.Step(new AmbientAdvance(cue[0], cue[1]))));
    }

    /** The best local evidence to close on, or {@code null} when there is none. */
    private static int[] cue(Squad squad, BattleView sim) {
        BelievedContact contact = WorldStateBuilder.freshestActionableContact(squad, sim);
        if (contact != null) {
            return unreached(squad, contact.lastSeenCellX(), contact.lastSeenCellY());
        }
        AudibleBearing heard = squad.audibleBearing();
        if (heard != null && sim.getSimTickIndex() - heard.heardTick()
                <= BEARING_INVESTIGATION_TICKS) {
            return unreached(squad, heard.cellX(), heard.cellY());
        }
        return null;
    }

    /**
     * The cue cell, unless the squad is already standing on it.
     *
     * <p>A cue the squad has reached is not a cue. Without this the goal
     * re-synthesizes the same advance the instant the last one succeeds, the
     * fresh step reports arrival again on its first tick, and the squad replans
     * every tick until the evidence expires — the ladder's own version of the
     * churn plan stickiness exists to prevent, and just as invisible from
     * outside, since each individual plan looks correct.
     */
    private static int[] unreached(Squad squad, int cellX, int cellY) {
        float dx = cellX + 0.5f - squad.centroidX;
        float dy = cellY + 0.5f - squad.centroidY;
        float arrival = AmbientAdvance.ARRIVAL_RADIUS;
        return dx * dx + dy * dy <= arrival * arrival ? null : new int[]{cellX, cellY};
    }
}
