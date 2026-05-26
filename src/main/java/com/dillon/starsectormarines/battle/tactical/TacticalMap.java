package com.dillon.starsectormarines.battle.tactical;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Read-only catalogue of {@link TacticalNode}s for one generated map. Built
 * by the map generator after all emissions and link-building have completed;
 * exposed to the battle AI for squad allocation, hold-point selection, and
 * fallback routing.
 *
 * <h2>Queries</h2>
 * Filter methods return new lists — callers can sort/mutate freely. The
 * underlying node list is not exposed mutably. {@link #within} and
 * {@link #nearest} use anchor-cell Manhattan distance, not bbox overlap;
 * that's fast and matches how the AI thinks ("which squad slots are within
 * sprint range").
 */
public final class TacticalMap {

    private final List<TacticalNode> nodes;

    public TacticalMap(List<TacticalNode> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    /** Every node on the map, in emission order. */
    public List<TacticalNode> all() {
        return Collections.unmodifiableList(nodes);
    }

    /** Every node of exactly {@code kind}. */
    public List<TacticalNode> ofKind(TacticalNode.Kind kind) {
        List<TacticalNode> out = new ArrayList<>();
        for (TacticalNode n : nodes) {
            if (n.kind == kind) out.add(n);
        }
        return out;
    }

    /** Every node whose {@link TacticalNode#defaultGuard} matches {@code faction}. */
    public List<TacticalNode> forFaction(Faction faction) {
        List<TacticalNode> out = new ArrayList<>();
        for (TacticalNode n : nodes) {
            if (n.defaultGuard == faction) out.add(n);
        }
        return out;
    }

    /** Every node whose anchor cell is within Manhattan {@code radius} of {@code (x,y)}. */
    public List<TacticalNode> within(int x, int y, int radius) {
        List<TacticalNode> out = new ArrayList<>();
        for (TacticalNode n : nodes) {
            int dx = Math.abs(n.anchorX - x);
            int dy = Math.abs(n.anchorY - y);
            if (dx + dy <= radius) out.add(n);
        }
        return out;
    }

    /**
     * Up to {@code count} nodes nearest to {@code (x,y)}, filtered by
     * {@code kinds}. Results sorted by ascending Manhattan distance.
     * Pass {@code null} or empty {@code kinds} to consider all kinds.
     */
    public List<TacticalNode> nearest(int x, int y, int count, EnumSet<TacticalNode.Kind> kinds) {
        List<TacticalNode> candidates = new ArrayList<>();
        for (TacticalNode n : nodes) {
            if (kinds != null && !kinds.isEmpty() && !kinds.contains(n.kind)) continue;
            candidates.add(n);
        }
        candidates.sort(Comparator.comparingInt(n -> Math.abs(n.anchorX - x) + Math.abs(n.anchorY - y)));
        if (candidates.size() > count) return candidates.subList(0, count);
        return candidates;
    }

    public int size() { return nodes.size(); }
}
