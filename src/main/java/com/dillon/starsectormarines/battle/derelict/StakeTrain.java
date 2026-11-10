package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * A stake train (`fallen-hull-nouns.md`, Vocabulary): the escort carrying a printed stake out, its walkers round a
 * hauler with the folded stake, bound for a lattice point. The hauler reaching within {@link #PLANT_CELLS} of the point
 * plants the stake; the hauler destroyed before then leaves the stake scrap, and the point is open again.
 */
public final class StakeTrain {

    public enum State { EN_ROUTE, PLANTED, SCRAP }

    /** How near its point, in cells (either axis), the hauler plants the stake. */
    public static final float PLANT_CELLS = 2f;

    /** The point it is bound for, and what the stake's chain will run through first. */
    public final ClaimLattice.Plan plan;
    /** The hauler's lance. */
    public final int haulerSquad;
    /** The escort's lances. */
    public final List<Integer> escort;

    private State state = State.EN_ROUTE;

    public StakeTrain(ClaimLattice.Plan plan, int haulerSquad, List<Integer> escort) {
        this.plan = plan;
        this.haulerSquad = haulerSquad;
        this.escort = List.copyOf(escort);
    }

    public State state() {
        return state;
    }

    /**
     * One look at the train.
     *
     * @param haulerStands whether its hauler still stands
     * @param x            where the hauler stands (read only while it stands)
     * @return the state it came to in this look, or null when unchanged
     */
    public State step(boolean haulerStands, float x, float y) {
        if (state != State.EN_ROUTE) return null;
        if (!haulerStands) {
            state = State.SCRAP;
            return state;
        }
        float px = plan.point().x() + 0.5f;
        float py = plan.point().y() + 0.5f;
        if (Math.max(Math.abs(x - px), Math.abs(y - py)) > PLANT_CELLS) return null;
        state = State.PLANTED;
        return state;
    }
}
