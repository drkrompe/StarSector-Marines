package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Place;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Settlement;
import com.dillon.starsectormarines.tools.mapauthoring.SketchRaster.CrossingCells;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the Maps page proves before it saves, each failure a sentence an author can act on:
 * <ul>
 * <li>every named place is reachable on foot from the landing zone, through edges as the pathfinder honours them;</li>
 * <li>every crossing the map draws really crosses: walkable from one bank over its cells to the other;</li>
 * <li>nothing is built on a way: no settlement stands on a road, a crossing, a drop or a rise.</li>
 * </ul>
 * Reads only the grid and the sketch's cells, so it is proved on a small grid with no generator behind it.
 */
public final class MapChecks {

    /** How far from a place's own cell its standing ground may be, for a place marked on a wall or a mount. */
    static final int STAND_REACH = 3;

    private MapChecks() { }

    public static List<String> run(NavigationGrid grid, SketchRaster.Cut cut, RoadRasterizer.Raster roads, MapSketch sketch) {
        List<String> out = new ArrayList<>();
        reachability(grid, sketch, out);
        for (CrossingCells c : cut.crossings()) crossing(grid, cut, c, out);
        builtOnWays(cut, roads, sketch, out);
        return out;
    }

    static void reachability(NavigationGrid grid, MapSketch sketch, List<String> out) {
        Place landing = sketch.landing();
        if (landing == null) {
            out.add("The map has no landing zone: mark one place as LANDING.");
            return;
        }
        int start = stand(grid, landing.x(), landing.y());
        if (start < 0) {
            out.add(String.format(Locale.ROOT, "The landing zone %s (%d,%d) has no ground to stand on.", landing.label(), landing.x(), landing.y()));
            return;
        }
        boolean[] reached = flood(grid, start, null);
        for (Place p : sketch.places()) {
            if (p == landing) continue;
            if (!reachable(grid, reached, p.x(), p.y())) {
                out.add(String.format(Locale.ROOT, "%s (%d,%d) cannot be reached on foot from the landing zone.", p.label(), p.x(), p.y()));
            }
        }
    }

    static void crossing(NavigationGrid grid, SketchRaster.Cut cut, CrossingCells c, List<String> out) {
        String name = String.format(Locale.ROOT, "The crossing at %.0f%% along %s", c.crossing().at() * 100f, c.drop());
        int w = cut.w();
        boolean any = false;
        for (int i = 0; i < c.cells().length; i++) {
            if (!c.cells()[i]) continue;
            any = true;
            if (!grid.isWalkable(i % w, i / w)) {
                out.add(String.format(Locale.ROOT, "%s is not walkable at %d,%d.", name, i % w, i / w));
                return;
            }
        }
        if (!any) {
            out.add(name + " lies off its drop.");
            return;
        }
        int a = bank(grid, cut, c, 1f), b = bank(grid, cut, c, -1f);
        if (a < 0 || b < 0) {
            out.add(name + " does not reach ground on both banks.");
            return;
        }
        // Over the crossing and the bank ground beside it only: a way round by another crossing does not count.
        boolean[] allowed = near(c.cells(), cut.w(), cut.h(), 2);
        allowed[a] = allowed[b] = true;
        if (!flood(grid, a, allowed)[b]) out.add(name + " does not join its banks.");
    }

    static void builtOnWays(SketchRaster.Cut cut, RoadRasterizer.Raster roads, MapSketch sketch, List<String> out) {
        int w = cut.w(), h = cut.h();
        for (Settlement s : sketch.settlements()) {
            String on = null;
            for (int y = Math.max(0, s.y()); y < Math.min(h, s.y() + s.h()) && on == null; y++) {
                for (int x = Math.max(0, s.x()); x < Math.min(w, s.x() + s.w()) && on == null; x++) {
                    int i = y * w + x;
                    if (roads != null && roads.road(x, y)) on = "a road";
                    else if (cut.bridged(i)) on = "a crossing";
                    else if (cut.drop()[i]) on = "a drop";
                    else if (cut.rise()[i]) on = "a rise";
                    if (on != null) out.add(String.format(Locale.ROOT, "Settlement %s stands on %s at %d,%d.", s.id(), on, x, y));
                }
            }
            if (s.x() < 0 || s.y() < 0 || s.x() + s.w() > w || s.y() + s.h() > h) {
                out.add("Settlement " + s.id() + " runs off the map.");
            }
        }
    }

    /** The first walkable cell beyond the drop from a crossing's centre, going {@code sign} across it. */
    private static int bank(NavigationGrid grid, SketchRaster.Cut cut, CrossingCells c, float sign) {
        Pt o = c.centre(), n = c.across();
        for (float t = 0f; t < 60f; t += .5f) {
            int x = (int) Math.floor(o.x() + n.x() * t * sign), y = (int) Math.floor(o.y() + n.y() * t * sign);
            if (x < 0 || y < 0 || x >= cut.w() || y >= cut.h()) return -1;
            int i = y * cut.w() + x;
            if (cut.drop()[i] || c.cells()[i]) continue;
            return grid.isWalkable(x, y) ? i : -1;
        }
        return -1;
    }

    private static boolean[] near(boolean[] cells, int w, int h, int r) {
        boolean[] out = new boolean[cells.length];
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i]) continue;
            int x = i % w, y = i / w;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) out[ny * w + nx] = true;
            }
        }
        return out;
    }

    private static int stand(NavigationGrid grid, int x, int y) {
        int w = grid.getWidth(), h = grid.getHeight();
        for (int r = 0; r <= STAND_REACH; r++) for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && grid.isWalkable(nx, ny)) return ny * w + nx;
        }
        return -1;
    }

    private static boolean reachable(NavigationGrid grid, boolean[] reached, int x, int y) {
        int w = grid.getWidth(), h = grid.getHeight();
        for (int dy = -STAND_REACH; dy <= STAND_REACH; dy++) for (int dx = -STAND_REACH; dx <= STAND_REACH; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && reached[ny * w + nx]) return true;
        }
        return false;
    }

    private static final Direction[] CARDINAL = {Direction.N, Direction.E, Direction.S, Direction.W};

    /** Cells reachable on foot from {@code start} by cardinal steps over open edges, within {@code allowed} when given. */
    static boolean[] flood(NavigationGrid grid, int start, boolean[] allowed) {
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int i = queue.poll(), x = i % w, y = i / w;
            for (Direction d : CARDINAL) {
                int nx = x + d.dx, ny = y + d.dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (seen[n] || !grid.isWalkable(nx, ny) || allowed != null && !allowed[n]) continue;
                if (!grid.isSharedEdgePassable(x, y, d)) continue;
                seen[n] = true;
                queue.add(n);
            }
        }
        return seen;
    }
}
