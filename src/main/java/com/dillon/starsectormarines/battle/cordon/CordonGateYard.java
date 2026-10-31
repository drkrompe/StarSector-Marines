package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.GateYardLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Road's truck yard ({@code cordon-nouns.md}, Cut-off town): paved open ground inside the town compound's wide
 * rear gate, where a convoy's truck drives in, turns and stands to load, the staging area beside it. Laid by the map
 * ({@code Fortification.GateYard}), read here from the map's laid layout.
 *
 * <p>Its cells are the yard alone: the staging area is beside it, never on it ({@link CordonStagingArea}). Reached
 * from the sequence as {@link CordonSequence#yard()}.
 *
 * <p>A truck drives in on the gate's axis, runs to the yard's far side, swings across toward the staging area and
 * comes back toward the gate to its stop ({@link #loop}), so it stands to load already facing out; loaded, it drives
 * forward out of the gate ({@link #wayOut}).
 */
public final class CordonGateYard {

    /** Cells out from the gate, across the glacis, where a truck lines up on the gate's axis to drive in. */
    static final int APPROACH = 4;
    /** How near a road's cell must come to that line-up point for the road to be cut there rather than searched on. */
    static final int ON_ROAD = 2;

    private final List<int[]> cells;
    private final List<int[]> beside;
    private final Set<Long> keys = new HashSet<>();
    private final int[] gate, entry, turn, stop;

    /**
     * @param cells  the yard's cells
     * @param beside the open ground the map kept beside it for the staging area
     * @param gate   the gate's middle on the wall's line
     * @param entry  just inside the gate, where a truck drives in
     * @param turn   the yard's middle, where a truck turns
     * @param stop   the yard's cell beside the staging ground, where a truck stops to load
     */
    public CordonGateYard(List<int[]> cells, List<int[]> beside, int[] gate, int[] entry, int[] turn, int[] stop) {
        this.cells = List.copyOf(cells);
        this.beside = List.copyOf(beside);
        for (int[] c : cells) keys.add(key(c[0], c[1]));
        this.gate = gate.clone();
        this.entry = entry.clone();
        this.turn = turn.clone();
        this.stop = stop.clone();
    }

    /** The town compound's yard on a map's laid layout, or {@code null} where the map laid none. */
    public static CordonGateYard of(LaidLayout layout) {
        if (layout == null) return null;
        Laid laid = layout.find(GateYardLayout.pieceName(CivilDefenceCompound.PLACE));
        if (laid == null || laid.kind() != Laid.Kind.YARD || !laid.isLaid() || laid.gaps().size() < 4
                || laid.cells().isEmpty()) {
            return null;
        }
        return new CordonGateYard(laid.cells(), laid.nests(), laid.gaps().get(0), laid.gaps().get(1),
                laid.gaps().get(2), laid.gaps().get(3));
    }

    /** The yard's cells, row by row from the gate inward. */
    public List<int[]> cells() { return cells; }

    public boolean contains(int x, int y) { return keys.contains(key(x, y)); }

    /** The open ground the map kept beside the yard, where the staging area stands. */
    public List<int[]> beside() { return beside; }

    /** The gate's middle cell, on the wall's line. */
    public int gateX() { return gate[0]; }

    public int gateY() { return gate[1]; }

    /** The yard's cell just inside the gate, where a truck pulls in. */
    public int entryX() { return entry[0]; }

    public int entryY() { return entry[1]; }

    /** The yard's middle, where a truck turns to face the gate again. */
    public int turnX() { return turn[0]; }

    public int turnY() { return turn[1]; }

    /** The yard's cell beside the staging area, where a truck stops to load. */
    public int stopX() { return stop[0]; }

    public int stopY() { return stop[1]; }

    /** One step from the gate into the yard along x: the way a truck drives in ({@code -1}, {@code 0} or {@code 1}). */
    public int inwardX() { return Integer.signum(entry[0] - gate[0]); }

    /** One step from the gate into the yard along y. */
    public int inwardY() { return Integer.signum(entry[1] - gate[1]); }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** The yard's cell {@code k} cells in from the gate and {@code t} along the wall (outside it for negative k). */
    public int[] at(int k, int t) {
        int ix = inwardX(), iy = inwardY(), lx = Math.abs(iy), ly = Math.abs(ix);
        return new int[]{gate[0] + ix * k + lx * t, gate[1] + iy * k + ly * t};
    }

    /** How many cells in from the gate a cell lies. */
    private int depthOf(int[] c) {
        return (c[0] - gate[0]) * inwardX() + (c[1] - gate[1]) * inwardY();
    }

    /** How far along the wall from the gate's axis a cell lies. */
    private int alongOf(int[] c) {
        return (c[0] - gate[0]) * Math.abs(inwardY()) + (c[1] - gate[1]) * Math.abs(inwardX());
    }

    /** The yard's depth: its furthest row in from the gate. */
    int depth() {
        int deepest = 1;
        for (int[] c : cells) deepest = Math.max(deepest, depthOf(c));
        return deepest;
    }

    /** The hand the staging area lies on along the wall: +1 or -1. */
    int side() {
        return alongOf(stop) < 0 ? -1 : 1;
    }

    /**
     * A truck's way in, cell by cell from the gate to its stop: in on the gate's axis to the yard's far side, a cell
     * past the axis on the far hand to widen the turn, across toward the staging area, and back toward the gate to the
     * stop, so it stands there facing out.
     */
    public List<int[]> loop() {
        int s = side(), far = Math.max(1, depth() - 1), across = Math.abs(alongOf(stop));
        List<int[]> out = new ArrayList<>();
        out.add(gate.clone());
        for (int[] point : List.of(entry, at(far, -s), at(far, s * across), stop)) line(out, point);
        return out;
    }

    /** A loaded truck's way out from its stop, forward through the gate to the line-up point outside, cell by cell. */
    public List<int[]> wayOut() {
        int s = side(), across = Math.abs(alongOf(stop));
        List<int[]> out = new ArrayList<>();
        out.add(stop.clone());
        for (int[] point : List.of(at(1, s * Math.min(across, 3)), at(-1, s), at(-APPROACH, 0))) line(out, point);
        return out;
    }

    /** Appends the cells from {@code out}'s last cell to {@code to}, a step at a time along the longer way first. */
    private static void line(List<int[]> out, int[] to) {
        int[] at = out.get(out.size() - 1).clone();
        while (at[0] != to[0] || at[1] != to[1]) {
            int dx = to[0] - at[0], dy = to[1] - at[1];
            if (Math.abs(dx) >= Math.abs(dy)) at[0] += Integer.signum(dx);
            else at[1] += Integer.signum(dy);
            out.add(at.clone());
        }
    }

    /**
     * A convoy's road carried on into the yard: the road cut where it passes the line-up point outside the gate (or,
     * where it ends short of it, searched on to it over drivable ground), in on the gate's axis, and round the yard's
     * {@link #loop} to the stop. {@code null} where no truck can reach the line-up point, and the convoys then stop
     * at the road's end as before.
     *
     * @param gateIndex the road's index of the gate's cell, where the yard's loop begins
     */
    public record Extended(List<int[]> cells, int gateIndex) { }

    Extended extend(List<int[]> road, CordonCorridor.CellTest drivable) {
        if (road == null || road.isEmpty()) return null;
        int[] outside = at(-APPROACH, 0);
        int nearest = 0;
        for (int i = 0; i < road.size(); i++) {
            if (chebyshev(road.get(i), outside) < chebyshev(road.get(nearest), outside)) nearest = i;
        }
        List<int[]> out = new ArrayList<>();
        if (chebyshev(road.get(nearest), outside) <= ON_ROAD) {
            for (int i = 0; i <= nearest; i++) out.add(road.get(i).clone());
            line(out, outside);
        } else {
            List<int[]> on = search(road.get(road.size() - 1), outside, drivable);
            if (on == null) return null;
            for (int[] c : road) out.add(c.clone());
            out.addAll(on.subList(1, on.size()));
        }
        line(out, gate);
        int gateIndex = out.size() - 1;
        List<int[]> loop = loop();
        out.addAll(loop.subList(1, loop.size()));
        return new Extended(out, gateIndex);
    }

    /** The shortest four-way path over drivable cells from one cell to another, both ends included, or {@code null}. */
    private static List<int[]> search(int[] from, int[] to, CordonCorridor.CellTest drivable) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(from);
        parent.put(key(from[0], from[1]), Long.MIN_VALUE);
        int limit = 4 * (Math.abs(from[0] - to[0]) + Math.abs(from[1] - to[1]) + 20);
        while (!open.isEmpty()) {
            int[] c = open.poll();
            if (c[0] == to[0] && c[1] == to[1]) {
                List<int[]> path = new ArrayList<>();
                for (long k = key(c[0], c[1]); k != Long.MIN_VALUE; k = parent.get(k)) {
                    path.add(0, new int[]{(int) (k >> 32), (int) k});
                }
                return path;
            }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (Math.abs(nx - from[0]) + Math.abs(ny - from[1]) > limit) continue;
                if (parent.containsKey(key(nx, ny)) || !drivable.test(nx, ny)) continue;
                parent.put(key(nx, ny), key(c[0], c[1]));
                open.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static int chebyshev(int[] a, int[] b) {
        return Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
    }
}
