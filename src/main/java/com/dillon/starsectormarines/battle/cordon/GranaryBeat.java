package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * L6, Burn the granary ({@code cordon-nouns.md}, Optional objectives): the colony's granary on its side
 * toward the line is biomass the bores will eat. Burnt before the second tide, that tide comes at three
 * quarters of its pressure. Burnt when half its walls are down, by any hand: charges, fire, a blade, or a
 * squad that holds it for twenty seconds and torches it (the simple form of setting it alight: its walls
 * are brought down through the ordinary wall-damage path). Lapses if no Marine ever reached it; fails if
 * one did and it still stands when the second tide comes.
 */
final class GranaryBeat extends CordonBeat {

    /** Seconds a Marine must stand at the granary to torch it. */
    static final int TORCH_SECONDS = 20;
    /** Cells round the granary a Marine counts as at it. */
    static final int AT = 3;
    /** What the next tide's pressure is scaled by once it burns. */
    static final float TIDE_SCALE = 0.75f;
    private static final int BURN = 10_000;

    private int[] box;
    private final List<int[]> walls = new ArrayList<>();
    private boolean attempted, burnt;
    private int heldSince = -1;

    GranaryBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: burnt succeeds; the second tide with it standing fails if anyone tried, lapses if nobody did. */
    static Status rule(boolean burnt, boolean deadline, boolean attempted) {
        if (burnt) return Status.SUCCEEDED;
        if (deadline) return attempted ? Status.FAILED : Status.LAPSED;
        return Status.ACTIVE;
    }

    /** Whether half or more of the walls it was built with are down. */
    static boolean burnt(int built, int standing) {
        return built > 0 && standing * 2 <= built;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (box == null) {
            box = beats.bounds(CordonSetPieces.GRANARY);
            if (box == null) return Status.LAPSED;
            for (int y = box[1]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[2]; x++) {
                    if (sim.getTopology().isWall(x, y) && !sim.getGrid().isWalkable(x, y)) walls.add(new int[]{x, y});
                }
            }
            if (walls.isEmpty()) return Status.LAPSED;
        }
        boolean marineAt = marineAt(sim);
        attempted |= marineAt;
        heldSince = marineAt ? (heldSince < 0 ? tick : heldSince) : -1;
        if (heldSince >= 0 && tick - heldSince >= CordonSequence.ticks(TORCH_SECONDS)) {
            for (int[] w : walls) sim.mapEditor().damageWall(w[0], w[1], BURN);
        }
        int standing = 0;
        for (int[] w : walls) if (sim.getTopology().isWall(w[0], w[1]) && !sim.getGrid().isWalkable(w[0], w[1])) standing++;
        burnt = burnt(walls.size(), standing);
        Status status = rule(burnt, beats.sequence().tides() >= 2, attempted);
        if (status == Status.SUCCEEDED) beats.sequence().weakenNextTide(TIDE_SCALE);
        return status;
    }

    private boolean marineAt(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)
                    || sim.identity().faction(unit) != Faction.MARINE) continue;
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            if (x >= box[0] - AT && x <= box[2] + AT && y >= box[1] - AT && y <= box[3] + AT) return true;
        }
        return false;
    }

    @Override
    int[] call(BattleSimulation sim) {
        if (box == null) return null;
        return new int[]{(box[0] + box[2]) / 2, (box[1] + box[3]) / 2, 2};
    }
}
