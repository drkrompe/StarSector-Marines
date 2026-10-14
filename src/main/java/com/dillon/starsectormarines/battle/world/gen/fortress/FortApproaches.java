package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives a kind of fort's {@link AssaultApproach}es from the geometry that kind already authors: its gates, its nests
 * and its wall. A kind with something more particular to say overrides {@link FortPlan#approaches()}; the derivation is
 * what keeps a new stamp from needing a plan before it can be assaulted at all.
 */
public final class FortApproaches {

    /**
     * A nest covers an approach when its mouth is within this many cells: the heavy machine gun the nests mount reaches
     * 24 cells, and the margin is the cell the gun stands in and the width of a gate. UNTUNED.
     */
    public static final int NEST_COVER_CELLS = 26;
    /** How far inside the wall the entry cell lies from the mouth of a gate: past the gate leaves and the towers. */
    public static final int GATE_ENTRY_DEPTH = 4;
    /** How far inside the outer wall layer a breach's entry cell lies. */
    public static final int BREACH_ENTRY_DEPTH = 3;
    /** A breach is not cut this close to a gate (cells): that is a gate with extra steps. */
    public static final int BREACH_GATE_CLEARANCE = 10;

    private FortApproaches() {}

    /** The gate approaches in plan order, then the breach if the wall offers one. */
    public static List<AssaultApproach> derive(FortPlan plan) {
        List<AssaultApproach> out = new ArrayList<>();
        List<FortPlan.Gate> gates = plan.gates();
        for (int i = 0; i < gates.size(); i++) {
            FortPlan.Gate gate = gates.get(i);
            out.add(new AssaultApproach(i == 0 ? Variant.MAIN_GATE : Variant.POSTERN, i, gate.mouthX(), gate.mouthY(),
                    gate.outX(), gate.outY(), gate.mouthX() - gate.outX() * GATE_ENTRY_DEPTH,
                    gate.mouthY() - gate.outY() * GATE_ENTRY_DEPTH,
                    covering(plan, gate.mouthX(), gate.mouthY()), lane(plan, gate.mouthX(), gate.mouthY(), gate.outX(), gate.outY())));
        }
        AssaultApproach breach = breach(plan);
        if (breach != null) out.add(breach);
        return out;
    }

    /** Indices of the nests with the cell within {@link #NEST_COVER_CELLS}. */
    static List<Integer> covering(FortPlan plan, int x, int y) {
        List<Integer> covering = new ArrayList<>();
        List<int[]> nests = plan.nests();
        for (int i = 0; i < nests.size(); i++) {
            if (Math.hypot(nests.get(i)[0] - x, nests.get(i)[1] - y) <= NEST_COVER_CELLS) covering.add(i);
        }
        return covering;
    }

    private static List<int[]> lane(FortPlan plan, int x, int y, int dx, int dy) {
        List<int[]> cells = new ArrayList<>();
        for (int k = 0; ; k++) {
            int cx = x + dx * k;
            int cy = y + dy * k;
            if (cx < 0 || cy < 0 || cx >= plan.width() || cy >= plan.height()) break;
            cells.add(new int[] {cx, cy});
        }
        return cells;
    }

    /**
     * The wall cell farthest from every nest whose outside is open ground and whose inside, two layers in, is the
     * works' yard: the thinnest honest place to cut. Scanned in a fixed order so the answer is the same on every run.
     */
    private static AssaultApproach breach(FortPlan plan) {
        List<int[]> nests = plan.nests();
        List<FortPlan.Gate> gates = plan.gates();
        int[] centre = plan.centrepiece();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        double bestScore = -1;
        double bestCentre = Double.MAX_VALUE;
        int[] best = null;
        for (int x = 0; x < plan.width(); x++) {
            for (int y = 0; y < plan.height(); y++) {
                if (!plan.isWall(x, y)) continue;
                for (int[] s : steps) {
                    if (plan.isWorks(x + s[0], y + s[1])) continue;
                    int ix = x - 2 * s[0];
                    int iy = y - 2 * s[1];
                    if (!plan.isWorks(ix, iy) || plan.isWall(ix, iy)) continue;
                    boolean nearGate = false;
                    for (FortPlan.Gate gate : gates) {
                        if (Math.hypot(gate.mouthX() - x, gate.mouthY() - y) < BREACH_GATE_CLEARANCE) nearGate = true;
                    }
                    if (nearGate) continue;
                    double score = Double.MAX_VALUE;
                    for (int[] nest : nests) score = Math.min(score, Math.hypot(nest[0] - x, nest[1] - y));
                    double toCentre = Math.hypot(centre[0] - x, centre[1] - y);
                    if (score > bestScore || (score == bestScore && toCentre < bestCentre)) {
                        bestScore = score;
                        bestCentre = toCentre;
                        best = new int[] {x, y, s[0], s[1]};
                    }
                }
            }
        }
        if (best == null) return null;
        int mouthX = best[0] + best[2];
        int mouthY = best[1] + best[3];
        return new AssaultApproach(Variant.BREACH, -1, mouthX, mouthY, best[2], best[3],
                best[0] - best[2] * BREACH_ENTRY_DEPTH, best[1] - best[3] * BREACH_ENTRY_DEPTH,
                covering(plan, mouthX, mouthY), lane(plan, mouthX, mouthY, best[2], best[3]));
    }
}
