package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.List;

/**
 * R2, A truck goes down ({@code cordon-nouns.md}, Optional objectives): on its way back loaded, the first
 * convoy's second truck breaks down in the suburb, halfway between the town and the gap (scripted). Its
 * people climb down and walk back through the gap while the company covers them; they are out once they
 * are behind the line. Succeeds when four of the six are out; fails once fewer than four can be.
 *
 * <p>The broken-down truck is taken off the road rather than left on it, so the convoys after it can still
 * drive the corridor; its people are the convoy's townspeople, counted out or lost as they walk.
 */
final class TruckDownBeat extends CordonBeat {

    static final int CONVOY = 1, TRUCK = 1;
    /** Out of six, how many must reach safety. */
    static final int NEEDED = 4;
    /** Rows behind the line the walkers are safe; corridor cells short of the gap they are walked to. */
    static final int SAFE_ROWS = 6, WALK_SHORT = 20;

    private CordonWalkers walkers;
    private int[] dest;
    private int countedOut, countedLost;

    TruckDownBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: enough out succeeds, too few left to make it fails. */
    static Status rule(int needed, int out, int onField) {
        if (out >= needed) return Status.SUCCEEDED;
        if (out + onField < needed) return Status.FAILED;
        return Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null) return Status.LAPSED;
        if (walkers == null) {
            String phase = convoys.phase(CONVOY);
            if ("out".equals(phase) || "lost".equals(phase)) return Status.LAPSED;
            if (!"outbound".equals(phase)) return Status.ACTIVE;
            int[] truck = convoys.truck(sim, CONVOY, TRUCK);
            if (truck == null) return Status.LAPSED;
            List<int[]> corridor = convoys.corridor();
            int down = convoys.gapIndex() + (corridor.size() - 1 - convoys.gapIndex()) / 2;
            if (truck[0] > down || truck[1] <= 0) return Status.ACTIVE;
            int people = convoys.strand(sim, CONVOY, TRUCK);
            if (people <= 0) return Status.LAPSED;
            walkers = CordonWalkers.stand(sim, "Passenger", truck[2], truck[3], people);
            // They walk to the camp where there is one, else back through the gap.
            CordonCamp camp = convoys.camp();
            int[] to = camp != null ? new int[]{camp.centreX(), camp.centreY()}
                    : corridor.get(Math.max(0, convoys.gapIndex() - WALK_SHORT));
            dest = CordonGround.nearestWalkable(sim.getGrid(), to[0], to[1]);
            convoys.walkedLost(people - walkers.spawned());
            if (dest == null) dest = to;
        }
        walkers.count(sim);
        return rule(Math.min(NEEDED, walkers.spawned()), walkers.out(), walkers.onField());
    }

    /** The walkers keep walking, and are counted, after the beat has gone final. */
    @Override
    void watch(BattleSimulation sim, int tick) {
        if (walkers == null) return;
        CordonConvoys convoys = beats.sequence().convoys();
        CordonCamp camp = convoys.camp();
        if (camp != null) {
            // Out once they reach the camp, where they wait with the others.
            walkers.walk(sim, dest[0], dest[1], camp::contains, id -> convoys.arrived(sim, id));
        } else {
            walkers.walk(sim, dest[0], dest[1], (x, y) -> beats.behindLine(y) >= SAFE_ROWS);
            convoys.walkedOut(walkers.out() - countedOut);
        }
        convoys.walkedLost(walkers.lost() - countedLost);
        countedOut = walkers.out();
        countedLost = walkers.lost();
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (walkers == null) return null;
        int[] at = walkers.where(sim);
        return at == null ? null : new int[]{at[0], at[1], 2};
    }
}
