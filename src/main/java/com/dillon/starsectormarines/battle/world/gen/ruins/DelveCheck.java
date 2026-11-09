package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Layer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Way;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The laws a ruins delve keeps on its finished map ({@code ruins-ground.md}): the vault is reachable from the
 * landing, the layer count is the one declared, every layer has its open ways, and no layer closes the route; at
 * the larger grades no single blocked way closes it either. Every published place a squad stands on is reachable
 * too. A ruins map that breaks one is a generation error, never a fallback.
 *
 * <p>Reach is walked one cardinal step at a time under the grid's own step rule, which is stricter than the
 * pathfinder's diagonal steps, so a pass here is a pass for any route the battle plans.
 */
public final class DelveCheck {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private DelveCheck() {}

    /** What the delve gets wrong on this grid; empty when it keeps every law. */
    public static List<String> problems(NavigationGrid grid, RuinsDelve delve, RuinsGroundPlan plan) {
        List<String> out = new ArrayList<>();
        if (delve.layers().size() != plan.layers()) {
            out.add("declared " + plan.layers() + " layers, laid " + delve.layers().size());
        }
        if (delve.landing().isEmpty()) {
            out.add("no arrival area on the landing ground");
            return out;
        }
        LandingPad start = delve.landing().get(0).berth(0);
        boolean[] reach = reach(grid, start.centerX, start.centerY, null);
        Cell source = delve.vault().source();
        if (!reached(grid, reach, source)) out.add("vault source " + source + " is not reachable from the landing");
        if (!reached(grid, reach, delve.vault().approach())) out.add("vault approach is not reachable");
        for (LandingArea area : delve.liftPoints()) {
            for (LandingPad berth : area.berths()) {
                if (!berth.isClear(grid, null)) out.add(area.id + " has a berth that is not clear ground");
                else if (!reached(grid, reach, new Cell(berth.centerX, berth.centerY))) {
                    out.add(area.id + " is not reachable");
                }
            }
        }
        for (Layer layer : delve.layers()) {
            List<Way> open = layer.openWays();
            if (open.size() < plan.minOpenWays()) {
                out.add(layer.name() + " has " + open.size() + " open ways; needs " + plan.minOpenWays());
            }
            for (Way way : open) {
                if (!reached(grid, reach, way.near()) || !reached(grid, reach, way.far()) || !walkable(grid, way.span())) {
                    out.add(way.name() + " is not crossable");
                }
            }
            if (open.size() >= 2) {
                for (Way way : open) {
                    boolean[] without = reach(grid, start.centerX, start.centerY, way.span());
                    if (!reached(grid, without, source)) out.add("blocking " + way.name() + " closes the route");
                }
            }
            for (RuinsDelve.SideRoom room : layer.rooms()) {
                if (!reached(grid, reach, room.interior())) out.add(room.name() + " is not reachable");
            }
        }
        if (!reached(grid, reach, delve.powerHall().console())) out.add("power hall console is not reachable");
        if (!reached(grid, reach, delve.records().console())) out.add("records console is not reachable");
        for (RuinsDelve.Junction junction : delve.junctions()) {
            if (!reached(grid, reach, junction.stand())) out.add(junction.name() + " is not reachable");
        }
        RuinsDelve.Interior interior = delve.interior();
        if (interior == null) {
            out.add("no interior");
        } else {
            if (delve.layers().size() - interior.firstLayer() != plan.interiorLayers()) {
                out.add("declared " + plan.interiorLayers() + " interior layers, laid "
                        + (delve.layers().size() - interior.firstLayer()));
            }
            if (interior.entrances().isEmpty()) out.add("the interior has no entrance");
            for (Area entrance : interior.entrances()) {
                if (!walkable(grid, entrance)) out.add("an interior entrance at " + entrance + " is not open");
            }
            for (int k = 0; k < interior.chambers().size(); k++) {
                Area chamber = interior.chambers().get(k);
                if (!reached(grid, reach, new Cell(chamber.centreX(), chamber.centreY()))) {
                    out.add(RuinsDelve.Interior.chamberName(k) + " is not reachable");
                }
            }
        }
        return out;
    }

    /** Cells reachable from {@code (x, y)} by cardinal steps, never entering {@code blocked}. */
    static boolean[] reach(NavigationGrid grid, int x, int y, Area blocked) {
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] seen = new boolean[w * h];
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return seen;
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        seen[y * w + x] = true;
        queue[tail++] = y * w + x;
        while (head < tail) {
            int at = queue[head++], cx = at % w, cy = at / w;
            for (int[] step : STEPS) {
                int nx = cx + step[0], ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || seen[ny * w + nx]) continue;
                if (blocked != null && blocked.contains(nx, ny)) continue;
                if (!grid.isWalkable(nx, ny) || !grid.canTraverseCellStep(cx, cy, nx, ny)) continue;
                seen[ny * w + nx] = true;
                queue[tail++] = ny * w + nx;
            }
        }
        return seen;
    }

    /** Cardinal steps from {@code (x, y)} to {@code goal}, or -1 when it cannot be reached. */
    public static int routeLength(NavigationGrid grid, int x, int y, Cell goal) {
        int w = grid.getWidth(), h = grid.getHeight();
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return -1;
        int[] dist = new int[w * h];
        Arrays.fill(dist, -1);
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        dist[y * w + x] = 0;
        queue[tail++] = y * w + x;
        while (head < tail) {
            int at = queue[head++], cx = at % w, cy = at / w;
            if (cx == goal.x() && cy == goal.y()) return dist[at];
            for (int[] step : STEPS) {
                int nx = cx + step[0], ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || dist[ny * w + nx] >= 0) continue;
                if (!grid.isWalkable(nx, ny) || !grid.canTraverseCellStep(cx, cy, nx, ny)) continue;
                dist[ny * w + nx] = dist[at] + 1;
                queue[tail++] = ny * w + nx;
            }
        }
        return -1;
    }

    private static boolean walkable(NavigationGrid grid, Area area) {
        for (int y = area.bottom(); y <= area.top(); y++) {
            for (int x = area.left(); x <= area.right(); x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
            }
        }
        return true;
    }

    private static boolean reached(NavigationGrid grid, boolean[] reach, Cell cell) {
        return grid.inBounds(cell.x(), cell.y()) && reach[cell.y() * grid.getWidth() + cell.x()];
    }
}
