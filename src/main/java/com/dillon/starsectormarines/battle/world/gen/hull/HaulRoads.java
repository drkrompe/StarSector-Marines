package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.HaulRoad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The haul roads ({@code fallen-hull-nouns.md}, Hauler): the way a hauler walks from each lattice point back to the
 * hull. One shortest-path tree grown outward from the hull's aprons by cardinal steps, so the roads from neighbouring
 * points merge into trunks toward the hull rather than each running its own line. A point with no way back has no
 * road.
 */
public final class HaulRoads {

    private HaulRoads() { }

    /** Whether a hauler may step onto the cell. */
    @FunctionalInterface
    public interface Walk {
        boolean open(int x, int y);
    }

    private static final int[][] STEPS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /** The roads from every point of {@code lattice} that has a way to one of {@code sources}, in lattice order. */
    public static List<HaulRoad> lay(int width, int height, Walk walk, List<Cell> sources, List<LatticePoint> lattice) {
        int[] parent = new int[width * height];
        Arrays.fill(parent, -2);
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        for (Cell s : sources) {
            if (s.x() < 0 || s.y() < 0 || s.x() >= width || s.y() >= height || !walk.open(s.x(), s.y())) continue;
            int i = s.y() * width + s.x();
            if (parent[i] != -2) continue;
            parent[i] = -1;
            frontier.add(i);
        }
        while (!frontier.isEmpty()) {
            int i = frontier.poll();
            int x = i % width, y = i / width;
            for (int[] s : STEPS) {
                int nx = x + s[0], ny = y + s[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int n = ny * width + nx;
                if (parent[n] != -2 || !walk.open(nx, ny)) continue;
                parent[n] = i;
                frontier.add(n);
            }
        }
        List<HaulRoad> out = new ArrayList<>();
        for (LatticePoint p : lattice) {
            int i = p.cell().y() * width + p.cell().x();
            if (parent[i] == -2) continue;
            List<Cell> cells = new ArrayList<>();
            for (int at = i; at >= 0; at = parent[at]) cells.add(new Cell(at % width, at / width));
            out.add(new HaulRoad(p.index(), cells));
        }
        return out;
    }
}
