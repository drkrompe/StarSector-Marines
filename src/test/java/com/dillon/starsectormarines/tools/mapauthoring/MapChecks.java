package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapRecipe;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the Maps page proves of a built owned map beyond the recipe's own proof ({@code OwnedMapRecipe.result}: every
 * pad, site, building and route reachable from the origin), each failure a sentence an author can act on:
 * <ul>
 * <li>every crossing joins its banks: walkable from one side of its drop over its deck to the other;</li>
 * <li>nothing is built on a way: no road, deck or gate cell is closed by a wall, a building or a prop.</li>
 * </ul>
 * Reads only the built grid and the document, so it is proved on a small grid with no map behind it.
 */
public final class MapChecks {

    private MapChecks() { }

    public static List<String> run(NavigationGrid grid, OwnedMap map, RoadPlan roads) {
        List<String> out = new ArrayList<>();
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] dropCells = new boolean[w * h];
        List<OwnedMapRecipe.Cut> cuts = new ArrayList<>();
        for (OwnedMap.Drop d : map.drops()) {
            OwnedMapRecipe.Cut cut = OwnedMapRecipe.cut(d, w, h);
            cuts.add(cut);
            for (int i = 0; i < dropCells.length; i++) dropCells[i] |= cut.drop()[i] && !cut.decks()[i];
        }
        for (int k = 0; k < map.drops().size(); k++) {
            OwnedMap.Drop d = map.drops().get(k);
            for (Cell c : d.crossings()) crossing(grid, d, c, cuts.get(k).decks(), dropCells, out);
        }
        builtOnWays(grid, map, roads, cuts, out);
        return out;
    }

    static void crossing(NavigationGrid grid, OwnedMap.Drop drop, Cell at, boolean[] decks, boolean[] dropCells, List<String> out) {
        int w = grid.getWidth(), h = grid.getHeight();
        String name = String.format(Locale.ROOT, "The crossing of %s at %d,%d", drop.name(), at.x(), at.y());
        boolean[] mine = near(decks, w, h, at, drop.crossingWidth() + drop.width());
        boolean any = false;
        for (int i = 0; i < mine.length; i++) {
            if (!mine[i]) continue;
            any = true;
            if (!grid.isWalkable(i % w, i / w)) {
                out.add(String.format(Locale.ROOT, "%s is closed at %d,%d.", name, i % w, i / w));
                return;
            }
        }
        if (!any) { out.add(name + " lies off its drop."); return; }
        float[] across = across(drop.path(), at.x() + .5f, at.y() + .5f);
        int a = bank(grid, dropCells, mine, at, across, 1f), b = bank(grid, dropCells, mine, at, across, -1f);
        if (a < 0 || b < 0) { out.add(name + " does not reach open ground on both banks."); return; }
        boolean[] allowed = grow(mine, w, h, 2);
        for (int i = 0; i < allowed.length; i++) if (dropCells[i]) allowed[i] = false;
        allowed[a] = allowed[b] = true;
        if (!flood(grid, a, allowed)[b]) out.add(name + " does not join its banks.");
    }

    static void builtOnWays(NavigationGrid grid, OwnedMap map, RoadPlan roads, List<OwnedMapRecipe.Cut> cuts, List<String> out) {
        int w = grid.getWidth(), h = grid.getHeight();
        if (roads != null && roads.width() == w && roads.height() == h) {
            RoadRasterizer.Raster raster = RoadRasterizer.rasterize(roads);
            first(grid, i -> raster.owner()[i] >= 0, "a road", out);
        }
        for (OwnedMapRecipe.Cut cut : cuts) first(grid, i -> cut.decks()[i], "a crossing", out);
        for (OwnedMap.Settlement s : map.settlements()) {
            for (Cell g : s.gates()) {
                if (g.x() >= 0 && g.y() >= 0 && g.x() < w && g.y() < h && !grid.isWalkable(g.x(), g.y())) {
                    out.add(String.format(Locale.ROOT, "%s's gate at %d,%d is closed.", s.name(), g.x(), g.y()));
                }
            }
        }
    }

    private interface Cells { boolean has(int i); }

    private static void first(NavigationGrid grid, Cells cells, String what, List<String> out) {
        int w = grid.getWidth(), count = 0, first = -1;
        for (int i = 0; i < w * grid.getHeight(); i++) {
            if (cells.has(i) && !grid.isWalkable(i % w, i / w)) { if (count++ == 0) first = i; }
        }
        if (count > 0) {
            out.add(String.format(Locale.ROOT, "Something is built on %s at %d,%d (%d cells closed).", what, first % w, first / w, count));
        }
    }

    /** The unit direction across a path at its point nearest {@code (x, y)}. */
    static float[] across(List<Cell> path, float x, float y) {
        float best = Float.MAX_VALUE, nx = 0f, ny = 1f;
        for (int k = 1; k < path.size(); k++) {
            float ax = path.get(k - 1).x() + .5f, ay = path.get(k - 1).y() + .5f;
            float dx = path.get(k).x() + .5f - ax, dy = path.get(k).y() + .5f - ay, len2 = dx * dx + dy * dy;
            if (len2 == 0f) continue;
            float t = Math.max(0f, Math.min(1f, ((x - ax) * dx + (y - ay) * dy) / len2));
            float d = (float) Math.hypot(x - ax - dx * t, y - ay - dy * t);
            if (d < best) {
                best = d;
                float len = (float) Math.sqrt(len2);
                nx = -dy / len;
                ny = dx / len;
            }
        }
        return new float[]{nx, ny};
    }

    /** The first open cell beyond the drop from a crossing, going {@code sign} across it. */
    private static int bank(NavigationGrid grid, boolean[] dropCells, boolean[] deck, Cell at, float[] n, float sign) {
        int w = grid.getWidth(), h = grid.getHeight();
        for (float t = 0f; t < 80f; t += .5f) {
            int x = (int) Math.floor(at.x() + .5f + n[0] * t * sign), y = (int) Math.floor(at.y() + .5f + n[1] * t * sign);
            if (x < 0 || y < 0 || x >= w || y >= h) return -1;
            int i = y * w + x;
            if (dropCells[i] || deck[i]) continue;
            return grid.isWalkable(x, y) ? i : -1;
        }
        return -1;
    }

    /** The deck cells joined to the one nearest {@code at}, within {@code reach} of it. */
    private static boolean[] near(boolean[] decks, int w, int h, Cell at, int reach) {
        boolean[] out = new boolean[decks.length];
        int start = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < decks.length; i++) {
            if (!decks[i]) continue;
            float d = (float) Math.hypot(i % w - at.x(), i / w - at.y());
            if (d < best && d <= reach) { best = d; start = i; }
        }
        if (start < 0) return out;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        out[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int i = queue.poll(), x = i % w, y = i / w;
            for (Direction d : CARDINAL) {
                int nx = x + d.dx, ny = y + d.dy, n = ny * w + nx;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || out[n] || !decks[n]) continue;
                out[n] = true;
                queue.add(n);
            }
        }
        return out;
    }

    private static boolean[] grow(boolean[] cells, int w, int h, int r) {
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

    private static final Direction[] CARDINAL = {Direction.N, Direction.E, Direction.S, Direction.W};

    /** Cells reachable on foot from {@code start} by cardinal steps over open edges, within {@code allowed}. */
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
