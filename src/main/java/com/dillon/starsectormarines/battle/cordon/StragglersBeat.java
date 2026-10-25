package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * L1, Stragglers on the wrong side ({@code cordon-nouns.md}, Optional objectives): a farm family radios
 * in from the farmstead in front of the line before the first tide. They wait until a Marine reaches
 * them, then walk in through the line to the ground behind the nearest redoubt and are safe once they
 * are well behind it. Succeeds when the last of them is in with at least one alive; fails when all of
 * them die; left waiting when the battle ends, they are failed (nobody came).
 */
final class StragglersBeat extends CordonBeat {

    /** When the family radios in: a minute and a half, well before the first tide. */
    static final int RADIO_SECONDS = 90;
    static final int PEOPLE = 6;
    /** Cells from a straggler a Marine has reached them. */
    static final int REACH = 6;
    /** Rows behind the line a straggler is safe, and how far behind the redoubt they are walked. */
    static final int SAFE_ROWS = 10, REAR_ROWS = 22;

    private CordonWalkers family;
    private boolean reached;
    private int[] dest;

    StragglersBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: in when nobody is left out there and someone made it, failed when nobody did. */
    static Status rule(int spawned, int onField, int out) {
        if (spawned == 0) return Status.LAPSED;
        if (onField > 0) return Status.ACTIVE;
        return out > 0 ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (family == null) {
            if (tick < CordonSequence.ticks(RADIO_SECONDS)) return Status.ACTIVE;
            int[] farm = home();
            if (farm == null) return Status.LAPSED;
            family = CordonWalkers.stand(sim, "Straggler", farm[0], farm[1], PEOPLE);
            dest = destination(sim, farm);
            if (dest == null) return Status.LAPSED;
        }
        if (!reached) {
            family.count(sim);
            reached = family.reachedByMarines(sim, REACH);
            return rule(family.spawned(), family.onField(), family.out());
        }
        family.walk(sim, dest[0], dest[1], (x, y) -> beats.behindLine(y) >= SAFE_ROWS);
        return rule(family.spawned(), family.onField(), family.out());
    }

    /** Behind the manned redoubt nearest the farmstead, through its gates. */
    private int[] destination(BattleSimulation sim, int[] farm) {
        CordonRedoubt best = null;
        long bestD = Long.MAX_VALUE;
        for (CordonRedoubt r : beats.sequence().redoubts()) {
            if (!r.manned()) continue;
            long dx = r.centreX() - farm[0], dy = r.centreY() - farm[1];
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = r;
            }
        }
        if (best == null) return null;
        // On ground already safe: snapped to the nearest open cell, a destination on the line's side of that row
        // left the family standing short of it all battle.
        int y = best.centreY() - CordonFront.frontward(beats.plan()) * REAR_ROWS;
        int[] at = CordonGround.nearestWalkable(sim.getGrid(), best.centreX(), y, (cx, cy) -> beats.behindLine(cy) >= SAFE_ROWS);
        return at != null ? at : beats.rearOf(sim, best.centreX(), best.centreY(), REAR_ROWS);
    }

    @Override
    protected Status ended(BattleSimulation sim) {
        return family != null && !reached && family.spawned() > 0 ? Status.FAILED : Status.LAPSED;
    }

    /** The middle of the stragglers' home: the farmstead, or a city's tenement; {@code null} where neither stands. */
    private int[] home() {
        for (String name : OptionalObjective.alternatives(CordonSetPieces.STRAGGLERS_HOME)) {
            int[] at = beats.centre(name);
            if (at != null) return at;
        }
        return null;
    }

    @Override
    String describe(BattleSimulation sim) {
        int[] place = home();
        String at = place == null ? "no place" : "at " + place[0] + "," + place[1];
        if (family == null) return at;
        return at + ", " + family.onField() + " on the field, " + family.out() + " out, " + family.lost() + " lost"
                + (reached ? ", reached" : "");
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (family == null) return null;
        int[] at = family.where(sim);
        return at == null ? null : new int[]{at[0], at[1], 1};
    }
}
