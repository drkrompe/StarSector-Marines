package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
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
 * The laws a ruins delve keeps on its finished map ({@code mapgen-nouns.md}, Ruins ground): the vault is reachable from the
 * landing, the layer count is the one declared, every layer has its open ways, and no layer closes the route; at
 * the larger grades no single blocked way closes it either. Every published place a squad stands on is reachable
 * too. A derelict hall frame in any plant hall can travel to every other one, through hall doors it passes or tears
 * ({@link #frameProblems}). A ruins map that breaks one is a generation error, never a fallback.
 *
 * <p>Reach is walked one cardinal step at a time under the grid's own step rule, which is stricter than the
 * pathfinder's diagonal steps, so a pass here is a pass for any route the battle plans.
 */
public final class DelveCheck {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The body a hall frame stands in: the MG-70's radius, cells. */
    public static final float HALL_FRAME_RADIUS = MechVariant.SECURITY_HALL.radius;

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
            if (interior.aprons().size() != interior.entrances().size()) {
                out.add(interior.entrances().size() + " entrances but " + interior.aprons().size() + " aprons");
            }
            for (RuinsDelve.Apron apron : interior.aprons()) {
                for (LandingPad berth : apron.lift().berths()) {
                    if (!berth.isClear(grid, null)) out.add(apron.name() + " has a berth that is not clear ground");
                    else if (!reached(grid, reach, new Cell(berth.centerX, berth.centerY))) {
                        out.add(apron.name() + " is not reachable");
                    }
                }
            }
            for (Area entrance : interior.entrances()) {
                if (!walkable(grid, entrance)) out.add("an interior entrance at " + entrance + " is not open");
            }
            for (int k = 0; k < interior.chambers().size(); k++) {
                Area chamber = interior.chambers().get(k);
                if (!reached(grid, reach, new Cell(chamber.centreX(), chamber.centreY()))) {
                    out.add(RuinsDelve.Interior.chamberName(k) + " is not reachable");
                }
            }
            out.addAll(frameProblems(grid, interior.halls(), interior.doors(), HALL_FRAME_RADIUS));
        }
        return out;
    }

    /**
     * Whether a hall frame of {@code radius} standing at any plant hall's post (its middle) can travel to every other
     * hall's post ({@code ruins-expedition.md}, Ways sized for machines): over ground its body fits, through every hall
     * door, a dead one forced and one narrower than the frame torn a jamb wider at each end. Narrow service ways it
     * cannot follow are allowed; a hall it cannot reach is not.
     */
    public static List<String> frameProblems(NavigationGrid grid, List<RuinsDelve.PlantHall> halls,
                                             List<RuinsDelve.LeafDoor> doors, float radius) {
        List<String> out = new ArrayList<>();
        if (halls.size() < 2) return out;
        NavigationGrid forced = forced(grid, doors, radius);
        Area first = halls.get(0).footprint();
        boolean[] reach = bodyReach(forced, radius, first.centreX(), first.centreY());
        if (reach == null) {
            out.add("a hall frame cannot stand in the " + halls.get(0).stage().name().toLowerCase() + " hall at "
                    + first);
            return out;
        }
        for (int k = 1; k < halls.size(); k++) {
            Area hall = halls.get(k).footprint();
            if (!postReached(forced, reach, hall)) {
                out.add("a hall frame cannot travel from the hall at " + first + " to the post in the hall at " + hall);
            }
        }
        return out;
    }

    /**
     * A copy of {@code grid} with every hall door as a hall frame leaves it: forced open across its span, and one
     * narrower than the frame torn a jamb wider at each end until the frame fits.
     */
    static NavigationGrid forced(NavigationGrid grid, List<RuinsDelve.LeafDoor> doors, float radius) {
        NavigationGrid forced = grid.copyNavigationTopology();
        for (RuinsDelve.LeafDoor door : doors) {
            Area s = door.span();
            for (int y = s.bottom(); y <= s.top(); y++) {
                for (int x = s.left(); x <= s.right(); x++) open(forced, x, y);
            }
            if (bodyFits(radius, door.length())) continue;
            int widen = 1;
            while (!bodyFits(radius, door.length() + 2 * widen)) widen++;
            for (int k = 1; k <= widen; k++) {
                if (door.alongX()) {
                    open(forced, s.left() - k, s.bottom());
                    open(forced, s.right() + k, s.bottom());
                } else {
                    open(forced, s.left(), s.bottom() - k);
                    open(forced, s.left(), s.top() + k);
                }
            }
        }
        return forced;
    }

    /** Whether a body of {@code radius} passes a way {@code cells} wide: its body across is less than the way. */
    static boolean bodyFits(float radius, int cells) {
        return radius * 2f < cells;
    }

    /**
     * The cells a body of {@code radius} reaches from the standable cell nearest {@code (x, y)}, over the cells it
     * stands at the middle or a corner of (a body as wide as a way stands on the seam between its rows); null when it
     * cannot stand within three cells of the start.
     */
    static boolean[] bodyReach(NavigationGrid grid, float radius, int x0, int y0) {
        int w = grid.getWidth(), h = grid.getHeight();
        int start = -1;
        for (int r = 0; r <= 3 && start < 0; r++) {
            for (int dy = -r; dy <= r && start < 0; dy++) {
                for (int dx = -r; dx <= r && start < 0; dx++) {
                    int x = x0 + dx, y = y0 + dy;
                    if (grid.inBounds(x, y) && stands(grid, x, y, radius)) start = y * w + x;
                }
            }
        }
        if (start < 0) return null;
        boolean[] reach = new boolean[w * h];
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        reach[start] = seen[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int at = queue[head++], cx = at % w, cy = at / w;
            for (int[] step : STEPS) {
                int nx = cx + step[0], ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || seen[ny * w + nx]) continue;
                seen[ny * w + nx] = true;
                if (!stands(grid, nx, ny, radius)) continue;
                reach[ny * w + nx] = true;
                queue[tail++] = ny * w + nx;
            }
        }
        return reach;
    }

    /** Whether a body of {@code radius} stands at the middle or at a corner of cell {@code (x, y)}. */
    private static boolean stands(NavigationGrid grid, int x, int y, float radius) {
        if (ManualTerrainMotion.canStand(grid, x + 0.5f, y + 0.5f, radius)) return true;
        for (int cx = 0; cx <= 1; cx++) {
            for (int cy = 0; cy <= 1; cy++) {
                if (ManualTerrainMotion.canStand(grid, x + cx, y + cy, radius)) return true;
            }
        }
        return false;
    }

    private static void open(NavigationGrid grid, int x, int y) {
        if (grid.inBounds(x, y)) grid.setWalkableFloor(x, y);
    }

    /** Whether the reach takes in a hall's post: a cell within three of its middle that the body stands on. */
    private static boolean postReached(NavigationGrid grid, boolean[] reach, Area hall) {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int x = hall.centreX() + dx, y = hall.centreY() + dy;
                if (grid.inBounds(x, y) && reach[y * grid.getWidth() + x]) return true;
            }
        }
        return false;
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
