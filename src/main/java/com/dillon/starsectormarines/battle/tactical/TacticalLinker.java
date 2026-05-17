package com.dillon.starsectormarines.battle.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Build the directed-link graph between {@link TacticalNode}s using simple
 * geometric proximity rules. Runs once after the generator has finished
 * emitting all nodes; mutates each node's links list in place.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li><b>OVERWATCHES</b>: each {@link TacticalNode.Kind#HEAVY_TOWER} links to up to
 *       {@link #MAX_OVERWATCH_TARGETS} nearest {@link TacticalNode.Kind#GATE}s within
 *       {@link #OVERWATCH_RADIUS_CELLS}. Models the tower's firing arc covering the
 *       gate approach.</li>
 *   <li><b>SUPPLIES</b>: each {@link TacticalNode.Kind#BARRACKS} links to up to
 *       {@link #MAX_SUPPLY_TARGETS} nearest towers / MGs / bunkers within
 *       {@link #SUPPLY_RADIUS_CELLS}. Models the reinforcement supply line.</li>
 *   <li><b>FALLBACK_TO</b>: each {@link TacticalNode.Kind#GATE} links to its
 *       nearest forward bunker first, then nearest heavy tower. Models the
 *       defender retreat ordering: breach → forward defense → wall hold.</li>
 *   <li><b>GUARDS</b>: each {@link TacticalNode.Kind#FORWARD_BUNKER} links to the
 *       single nearest gate. Models the bunker's "forward defense for this
 *       gate" role; the gate appears in only one bunker's GUARDS list since
 *       it's the bunker that's pegged to a gate, not the other way round.</li>
 * </ul>
 *
 * <p>All rules use Manhattan distance for selection — cheap, matches AI's
 * pathing assumptions. The radius bounds keep the graph local: a tower
 * doesn't end up overwatching a gate on the opposite end of the wall.
 */
public final class TacticalLinker {

    /** Max range for tower → gate overwatch links. ~1.5× heavy-tower spacing so adjacent towers can both watch the same gate but distant ones cannot. */
    private static final int OVERWATCH_RADIUS_CELLS = 55;
    /** Max links per tower. Most towers will hit 1-2 gates. */
    private static final int MAX_OVERWATCH_TARGETS = 2;

    /** Max range for barracks → tower/MG/bunker supply links. Generous — most barracks supply the entire wall section nearest to them. */
    private static final int SUPPLY_RADIUS_CELLS = 80;
    /** Max links per barracks. Keeps the graph readable; the AI can still walk SUPPLIES outward via additional barracks. */
    private static final int MAX_SUPPLY_TARGETS = 6;

    /** Max range for fallback links — a gate's fallback target should be reachable in one bounded movement order. */
    private static final int FALLBACK_RADIUS_CELLS = 60;

    private TacticalLinker() {}

    public static void link(TacticalMap map) {
        List<TacticalNode> all = map.all();
        linkOverwatches(all);
        linkSupplies(all);
        linkFallbacks(all);
        linkGuards(all);
    }

    private static void linkOverwatches(List<TacticalNode> all) {
        List<TacticalNode> gates = filter(all, TacticalNode.Kind.GATE);
        for (TacticalNode tower : filter(all, TacticalNode.Kind.HEAVY_TOWER)) {
            List<TacticalNode> targets = nearestWithin(tower, gates, OVERWATCH_RADIUS_CELLS, MAX_OVERWATCH_TARGETS);
            for (TacticalNode g : targets) {
                tower.addLink(TacticalNode.LinkKind.OVERWATCHES, g);
            }
        }
    }

    private static void linkSupplies(List<TacticalNode> all) {
        EnumSet<TacticalNode.Kind> supplyTargets = EnumSet.of(
                TacticalNode.Kind.HEAVY_TOWER,
                TacticalNode.Kind.MG_NEST,
                TacticalNode.Kind.FORWARD_BUNKER);
        List<TacticalNode> candidates = filterAny(all, supplyTargets);
        for (TacticalNode barracks : filter(all, TacticalNode.Kind.BARRACKS)) {
            List<TacticalNode> targets = nearestWithin(barracks, candidates, SUPPLY_RADIUS_CELLS, MAX_SUPPLY_TARGETS);
            for (TacticalNode t : targets) {
                barracks.addLink(TacticalNode.LinkKind.SUPPLIES, t);
            }
        }
    }

    private static void linkFallbacks(List<TacticalNode> all) {
        List<TacticalNode> bunkers = filter(all, TacticalNode.Kind.FORWARD_BUNKER);
        List<TacticalNode> towers  = filter(all, TacticalNode.Kind.HEAVY_TOWER);
        for (TacticalNode gate : filter(all, TacticalNode.Kind.GATE)) {
            // Forward bunker first — it's the immediate retreat behind the gate (well, behind from defender POV: bunker is in kill zone forward of the wall).
            // Actually, FALLBACK_TO from a gate means "where defenders go if the gate is breached" — that's INWARD (into the fortress).
            // Forward bunkers sit in the kill zone OUTSIDE the wall, so they're not a fallback for the gate. Skip bunkers, fall back to nearest tower.
            // (We keep bunkers in the FALLBACK signature for symmetry but for v1 only tower links emit.)
            TacticalNode nearestTower = nearestSingle(gate, towers, FALLBACK_RADIUS_CELLS);
            if (nearestTower != null) gate.addLink(TacticalNode.LinkKind.FALLBACK_TO, nearestTower);
        }
        // Forward bunker also has a fallback target — the gate it guards.
        // Sets up the "bunker falls → defenders retreat to gate" flow.
        List<TacticalNode> gates = filter(all, TacticalNode.Kind.GATE);
        for (TacticalNode bunker : bunkers) {
            TacticalNode nearestGate = nearestSingle(bunker, gates, FALLBACK_RADIUS_CELLS);
            if (nearestGate != null) bunker.addLink(TacticalNode.LinkKind.FALLBACK_TO, nearestGate);
        }
    }

    private static void linkGuards(List<TacticalNode> all) {
        List<TacticalNode> gates = filter(all, TacticalNode.Kind.GATE);
        for (TacticalNode bunker : filter(all, TacticalNode.Kind.FORWARD_BUNKER)) {
            TacticalNode nearest = nearestSingle(bunker, gates, FALLBACK_RADIUS_CELLS);
            if (nearest != null) bunker.addLink(TacticalNode.LinkKind.GUARDS, nearest);
        }
    }

    private static List<TacticalNode> filter(List<TacticalNode> all, TacticalNode.Kind kind) {
        List<TacticalNode> out = new ArrayList<>();
        for (TacticalNode n : all) if (n.kind == kind) out.add(n);
        return out;
    }

    private static List<TacticalNode> filterAny(List<TacticalNode> all, EnumSet<TacticalNode.Kind> kinds) {
        List<TacticalNode> out = new ArrayList<>();
        for (TacticalNode n : all) if (kinds.contains(n.kind)) out.add(n);
        return out;
    }

    private static List<TacticalNode> nearestWithin(TacticalNode origin, List<TacticalNode> pool,
                                                     int radius, int max) {
        List<TacticalNode> filtered = new ArrayList<>();
        for (TacticalNode candidate : pool) {
            int d = Math.abs(candidate.anchorX - origin.anchorX) + Math.abs(candidate.anchorY - origin.anchorY);
            if (d <= radius) filtered.add(candidate);
        }
        filtered.sort(Comparator.comparingInt(n ->
                Math.abs(n.anchorX - origin.anchorX) + Math.abs(n.anchorY - origin.anchorY)));
        if (filtered.size() > max) return filtered.subList(0, max);
        return filtered;
    }

    private static TacticalNode nearestSingle(TacticalNode origin, List<TacticalNode> pool, int radius) {
        TacticalNode best = null;
        int bestDist = Integer.MAX_VALUE;
        for (TacticalNode candidate : pool) {
            int d = Math.abs(candidate.anchorX - origin.anchorX) + Math.abs(candidate.anchorY - origin.anchorY);
            if (d > radius) continue;
            if (d < bestDist) { bestDist = d; best = candidate; }
        }
        return best;
    }
}
