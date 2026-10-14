package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.marine.CommsKitSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Who on a side can direct its air right now, and onto what ground.
 *
 * <p>An operator is a living unit carrying a {@link CommsKit}. It can direct a strike at a point when it is alive, not
 * suppressed, not already on the net, and has <em>fair knowledge</em> of the point: either an unobstructed line to it
 * inside its spotting range, or a belief the caller vouches for (the player's fog says the ground is seen; an AI's
 * memory says it was sighted) inside radio range. Nothing here reads an enemy's position the operator has no way of
 * knowing; the caller decides what is believed.
 */
public final class CommsOperators {

    private CommsOperators() {}

    /** One operator and its radio. */
    public record Operator(long unit, CommsKitSpec spec) {}

    /** How a point is known to an operator. */
    public enum Knowledge {
        /** Not known: the operator has no sight of it and no belief. */
        NONE,
        /** Sighted: an unobstructed line inside spotting range. */
        SIGHT,
        /** Believed: not in sight, but the caller vouches for it. */
        BELIEF
    }

    /** Every living, unsuppressed, free operator on {@code side}, in roster order. */
    public static List<Operator> available(BattleSimulation sim, Faction side) {
        List<Operator> out = new ArrayList<>();
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] units = roster.factionDenseArray(side);
        int count = roster.factionLiveCount(side);
        for (int i = 0; i < count; i++) {
            long u = units[i];
            if (!world.isAlive(u)) continue;
            CommsKitSpec spec = CommsKit.specOf(world, u);
            if (spec == null) continue;
            if (world.secondaryCooldownTimer(u) > 0f) continue;
            if (suppressed(sim, u, spec)) continue;
            out.add(new Operator(u, spec));
        }
        return out;
    }

    /** Whether the fire on {@code unit} is heavy enough that it cannot work the radio. */
    public static boolean suppressed(BattleSimulation sim, long unit, CommsKitSpec spec) {
        return sim.getRoster().combat().incomingPressure(unit, sim.getSimTickIndex()) >= spec.suppressionPressure();
    }

    /** What {@code operator} knows of {@code (targetX, targetY)} given whether the caller {@code believes} it. */
    public static Knowledge knowledge(BattleSimulation sim, Operator operator, float targetX, float targetY,
                                      boolean believes) {
        World world = sim.world();
        float dx = targetX - world.x(operator.unit());
        float dy = targetY - world.y(operator.unit());
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= operator.spec().spotRangeCells()
                && lineClear(sim.getGrid(), world.cellX(operator.unit()), world.cellY(operator.unit()),
                (int) Math.floor(targetX), (int) Math.floor(targetY), operator.spec().spotRangeCells())) {
            return Knowledge.SIGHT;
        }
        if (believes && distance <= operator.spec().radioRangeCells()) return Knowledge.BELIEF;
        return Knowledge.NONE;
    }

    /**
     * The operator best placed to direct a strike at the point: sight before belief, then the nearest. {@code null}
     * when nobody can.
     */
    public static Operator best(BattleSimulation sim, Faction side, float targetX, float targetY, boolean believes) {
        Operator best = null;
        int bestRank = Integer.MAX_VALUE;
        float bestDistSq = Float.MAX_VALUE;
        World world = sim.world();
        for (Operator op : available(sim, side)) {
            Knowledge k = knowledge(sim, op, targetX, targetY, believes);
            if (k == Knowledge.NONE) continue;
            float dx = targetX - world.x(op.unit());
            float dy = targetY - world.y(op.unit());
            float distSq = dx * dx + dy * dy;
            int rank = k == Knowledge.SIGHT ? 0 : 1;
            if (rank < bestRank || rank == bestRank && distSq < bestDistSq) {
                best = op;
                bestRank = rank;
                bestDistSq = distSq;
            }
        }
        return best;
    }

    /**
     * Whether the line from the operator's cell to the target is open. A structure stands on a blocked cell, so the
     * target cell itself and its four neighbours are all tried: seeing a nest is seeing the ground it stands in.
     */
    static boolean lineClear(NavigationGrid grid, int ox, int oy, int tx, int ty, float range) {
        if (grid.hasLineOfSightWithin(ox, oy, tx, ty, range)) return true;
        return grid.hasLineOfSightWithin(ox, oy, tx + 1, ty, range)
                || grid.hasLineOfSightWithin(ox, oy, tx - 1, ty, range)
                || grid.hasLineOfSightWithin(ox, oy, tx, ty + 1, range)
                || grid.hasLineOfSightWithin(ox, oy, tx, ty - 1, range);
    }
}
