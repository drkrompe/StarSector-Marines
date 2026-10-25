package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.objective.optional.ObjectivePressure;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;

import java.util.List;

/**
 * A muster in the market square ({@code set-piece-blocks.md}, The market square): the colony's people have
 * gathered between the stalls, waiting out the swarm's first push, and the company holds the square until it
 * has passed. The square's mouths decide which ways the swarm comes in.
 *
 * <p>Held when {@link #HOLD_SECONDS} have gone with at least half the muster alive; lost the moment fewer are.
 * Once held the muster breaks up and its people go their own ways.
 */
public final class SquareMuster implements PressuredObjective.Task {

    /** People in the muster. */
    static final int PEOPLE = 8;
    /** Seconds the square is held. */
    static final float HOLD_SECONDS = 100f;
    /** Seconds before the end the pressure's final rush comes. */
    static final float FINAL_SECONDS = 30f;

    private final OpenSquare square;
    private final Gathering muster;
    private int startTick = -1;
    private float elapsed;
    private Status status = Status.ACTIVE;

    private SquareMuster(OpenSquare square, Gathering muster) {
        this.square = square;
        this.muster = muster;
    }

    /** The muster gathered round the centre of {@code square}; null where it has no room for the people. */
    public static SquareMuster install(BattleSimulation sim, OpenSquare square) {
        if (square == null) return null;
        List<int[]> places = Gathering.placesAround(sim.getGrid(), sim.getTopology(), square.centreX(),
                square.centreY(), PEOPLE);
        if (places.size() < PEOPLE) return null;
        return new SquareMuster(square, Gathering.spawn(sim, "Muster", places, square.centreX() + .5f,
                square.centreY() + .5f));
    }

    @Override
    public Status advance(BattleSimulation sim, int tick) {
        if (status.isFinal()) return status;
        if (startTick < 0) startTick = tick;
        elapsed = (tick - startTick) * BattleSimulation.TICK_DT;
        if (muster.alive(sim) * 2 < muster.size()) return status = Status.FAILED;
        if (elapsed >= HOLD_SECONDS) {
            muster.disperse(sim);
            return status = Status.SUCCEEDED;
        }
        return status;
    }

    /** Half the hold is half way; its last stretch sends everything the pressure owns into the square. */
    @Override
    public ObjectivePressure.Beat beat() {
        float progress = elapsed / HOLD_SECONDS;
        return elapsed >= HOLD_SECONDS - FINAL_SECONDS
                ? new ObjectivePressure.Beat(progress, true, new int[]{square.centreX(), square.centreY()})
                : ObjectivePressure.Beat.underWay(progress);
    }

    /** Seconds held so far. */
    public float elapsed() { return elapsed; }

    /** People of the muster left alive. */
    public int alive(BattleSimulation sim) { return muster.alive(sim); }

    /** People in the muster. */
    public int size() { return muster.size(); }

    /** The square held. */
    public OpenSquare square() { return square; }
}
