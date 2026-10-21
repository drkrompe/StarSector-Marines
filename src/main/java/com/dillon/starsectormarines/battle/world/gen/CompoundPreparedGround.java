package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A compound's <b>prepared ground</b>: a sandbag apron in front of the entrance a place is attacked through, laid so that
 * it is a fighting position in its own right (`mapgen-nouns.md`, prepared ground).
 *
 * <p>An apron is one straight course of bags parallel to the wall, {@link #STANDOFFS standing off} the doorway, with a
 * gap exactly as wide as the door directly in front of it: the way in stays straight and open, and a squad behind the
 * bags covers the ground the door is approached across. It is laid whole or not at all, and only where the ground it
 * leaves standing is a full run ({@link FightingPositionIndex#MIN_RUN_CELLS}), so every apron on the map is a place a
 * squad can be sent to and none is a stub of cover that reads like one and is not.
 *
 * <p>The authored laws hold by refusal rather than repair: nothing is laid on a doorway, a wall, a fixture, ground the
 * caller keeps clear (roads, vehicle corridors), or beside another door; the door's own lane and the cells behind the
 * bags must be open floor. A door wider than {@link #MAX_DOOR_WIDTH} is a vehicle entrance and gets none.
 *
 * <p>Nothing about the position is recorded here: the props are what the fighting-position index reads, so the run is a
 * garrisonable for every consumer as soon as the battle derives it.
 */
public final class CompoundPreparedGround {

    /** {@code -Dbattle.mapgen.compoundPreparedGround=false} lays no aprons: the control for every reading of them. */
    public static final String PROPERTY = "battle.mapgen.compoundPreparedGround";
    /** Read once from the property above; settable for a control in the same JVM. */
    public static boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Bags each side of the door's gap. Six bags leave a run of about twenty cells: a squad's worth. UNTUNED. */
    public static final int WING = 3;
    /** Distances out from the doorway the course is tried at, best first. Three leaves two clear cells at the door. */
    public static final int[] STANDOFFS = {3, 2, 4};
    /**
     * The widest door an apron is laid in front of. Anything wider is a vehicle entrance, and a gap of three cells
     * would part the course into two halves too short to be a run each.
     */
    public static final int MAX_DOOR_WIDTH = 2;
    /** How many cells of wall an approach looks past for the ground in front of a face. */
    public static final int APPROACH_WALL_DEPTH = 3;

    /** Which cells the caller needs left exactly as they are (roads, reservations, corridors). */
    @FunctionalInterface
    public interface KeepClear {
        boolean test(int x, int y);
    }

    /**
     * A walkable opening in a place's envelope: {@code width} doorway cells from {@code (x, y)} along the wall, opening
     * outward toward {@code (fx, fy)}.
     */
    public record Entrance(int x, int y, int width, int fx, int fy) {
        /** The along-the-wall step. */
        int px() { return fy != 0 ? 1 : 0; }
        int py() { return fx != 0 ? 1 : 0; }
        int midX() { return x + px() * (width - 1) / 2; }
        int midY() { return y + py() * (width - 1) / 2; }
    }

    /**
     * One sandbag piece: its cell, the way its bags face, and for a corner the second way it faces.
     */
    public record Bag(int x, int y, int dx, int dy, int cornerDx, int cornerDy) {
        boolean corner() { return cornerDx != 0 || cornerDy != 0; }
    }

    /**
     * A planned apron: the bags, and the walkable cells behind them where a defender stands covered toward the door's
     * approach, nearest the door first.
     */
    public record Apron(Entrance entrance, List<Bag> bags, List<int[]> behind) { }

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    private CompoundPreparedGround() {}

    /** The compound kinds whose places are given prepared ground: the leaves of a base the defender holds. */
    public static boolean isCompoundKind(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY;
    }

    /**
     * Lays one apron at the entrance of {@code node}'s building that best faces {@code (towardX, towardY)}, the side its
     * attack comes from, or none when no entrance facing that side can take one.
     *
     * @return the apron laid, or {@code null}
     */
    public static Apron layFacing(GenContext ctx, TacticalNode node, int towardX, int towardY, KeepClear keepClear) {
        if (!ENABLED || !artInstalled()) return null;
        for (Entrance door : candidates(ctx.grid, ctx.topology, node, towardX, towardY)) {
            Apron apron = plan(ctx.grid, ctx.topology, door, keepClear);
            if (apron == null) continue;
            lay(ctx, apron);
            return apron;
        }
        return null;
    }

    /**
     * A story map's compounds given their prepared ground: for each compound site, one apron facing the ground its
     * attack lands on ({@code fromX, fromY}, the attacker's landing), kept off streets and landing berths. Laid by the
     * map before it is finished, so a story map's held compounds offer the same positions a generated base does.
     *
     * @return how many aprons were laid
     */
    public static int layForSites(GenContext ctx, List<TacticalNode> sites, int fromX, int fromY) {
        KeepClear keepClear = (x, y) -> ctx.topology.getGroundKind(x, y) == CellTopology.GroundKind.STREET
                || onPad(ctx, x, y);
        int laid = 0;
        for (TacticalNode site : sites) {
            if (!isCompoundKind(site.kind)) continue;
            int dx = fromX - (site.left + site.right) / 2;
            int dy = fromY - (site.top + site.bottom) / 2;
            int towardX = Math.abs(dx) >= Math.abs(dy) ? Integer.signum(dx) : 0;
            int towardY = Math.abs(dx) >= Math.abs(dy) ? 0 : Integer.signum(dy);
            if (towardX == 0 && towardY == 0) continue;
            if (layFacing(ctx, site, towardX, towardY, keepClear) != null) laid++;
        }
        return laid;
    }

    private static boolean onPad(GenContext ctx, int x, int y) {
        for (LandingPad pad : ctx.landingPads) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) if (pad.contains(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    /**
     * Where an apron may go, best first: a door that faces the attack, then the attack-facing face of the place itself
     * (an approach with no door in it, the gallery or yard edge an assault crosses), then a door on a flank. Never a door
     * facing away from the attack: a course there covers ground nobody comes across.
     */
    public static List<Entrance> candidates(NavigationGrid grid, CellTopology topology, TacticalNode node,
                                            int towardX, int towardY) {
        List<Entrance> doors = entrances(grid, node.left, node.top, node.right, node.bottom);
        doors.sort(Comparator.comparingInt((Entrance e) -> -(e.fx * towardX + e.fy * towardY))
                .thenComparingInt(e -> -edgeDepth(node, e, towardX, towardY))
                .thenComparingInt(Entrance::y).thenComparingInt(Entrance::x));
        List<Entrance> out = new ArrayList<>();
        for (Entrance door : doors) if (door.fx * towardX + door.fy * towardY > 0) out.add(door);
        out.addAll(approaches(grid, topology, node, towardX, towardY));
        for (Entrance door : doors) if (door.fx * towardX + door.fy * towardY == 0) out.add(door);
        return out;
    }

    /**
     * The attack-facing face of the place as one-cell openings onto the ground in front of it: for each point along the
     * face, the first open floor stepping outward past the envelope's wall, its midpoint first and then stepping out
     * along the face either way, so a face with a wall stub or a lamp post at its middle still finds its course.
     */
    private static List<Entrance> approaches(NavigationGrid grid, CellTopology topology, TacticalNode node,
                                             int towardX, int towardY) {
        List<Entrance> out = new ArrayList<>();
        if (Math.abs(towardX) + Math.abs(towardY) != 1) return out;
        boolean vertical = towardY != 0;
        int lo = vertical ? node.left : node.top;
        int hi = vertical ? node.right : node.bottom;
        int mid = (lo + hi) / 2;
        int edge = vertical ? (towardY < 0 ? node.top - 1 : node.bottom + 1) : (towardX < 0 ? node.left - 1 : node.right + 1);
        for (int step = 0; step <= (hi - lo) / 2; step += WING) {
            for (int sign : step == 0 ? new int[]{1} : new int[]{-1, 1}) {
                int at = mid + sign * step;
                if (at < lo || at > hi) continue;
                for (int out1 = 0; out1 <= APPROACH_WALL_DEPTH; out1++) {
                    int x = vertical ? at : edge + towardX * out1;
                    int y = vertical ? edge + towardY * out1 : at;
                    if (!floor(grid, topology, x, y)) continue;
                    out.add(new Entrance(x, y, 1, towardX, towardY));
                    break;
                }
            }
        }
        return out;
    }

    /** How far toward the attack the entrance lies: the attack-facing door of a deep building first. */
    private static int edgeDepth(TacticalNode node, Entrance e, int towardX, int towardY) {
        return (e.midX() - (node.left + node.right) / 2) * towardX + (e.midY() - (node.top + node.bottom) / 2) * towardY;
    }

    /**
     * The entrances of the envelope round the box: runs of doorway cells on or just outside it, each with a walkable
     * interior cell inside the box on one side and walkable ground outside it on the other. In row-major order of the
     * first cell.
     */
    public static List<Entrance> entrances(NavigationGrid grid, int left, int top, int right, int bottom) {
        List<Entrance> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int y = top - 1; y <= bottom + 1; y++) {
            for (int x = left - 1; x <= right + 1; x++) {
                if (!grid.inBounds(x, y) || !grid.isDoorway(x, y) || seen.contains(key(x, y))) continue;
                int[] f = outward(grid, x, y, left, top, right, bottom);
                if (f == null) continue;
                int px = f[1] != 0 ? 1 : 0;
                int py = f[0] != 0 ? 1 : 0;
                int width = 0;
                while (grid.inBounds(x + px * width, y + py * width) && grid.isDoorway(x + px * width, y + py * width)
                        && sameFacing(outward(grid, x + px * width, y + py * width, left, top, right, bottom), f)) {
                    seen.add(key(x + px * width, y + py * width));
                    width++;
                }
                out.add(new Entrance(x, y, width, f[0], f[1]));
            }
        }
        return out;
    }

    private static boolean sameFacing(int[] a, int[] b) {
        return a != null && a[0] == b[0] && a[1] == b[1];
    }

    /** The one outward direction from this doorway cell: interior in the box behind it, open ground outside ahead. */
    private static int[] outward(NavigationGrid grid, int x, int y, int left, int top, int right, int bottom) {
        int[] found = null;
        for (int k = 0; k < 4; k++) {
            int ix = x - DX[k];
            int iy = y - DY[k];
            int ox = x + DX[k];
            int oy = y + DY[k];
            if (!inside(ix, iy, left, top, right, bottom) || inside(ox, oy, left, top, right, bottom)) continue;
            if (!grid.inBounds(ix, iy) || !grid.inBounds(ox, oy)) continue;
            if (!grid.isWalkable(ix, iy) || !grid.isWalkable(ox, oy) || grid.isDoorway(ox, oy)) continue;
            if (found != null) return null;
            found = new int[]{DX[k], DY[k]};
        }
        return found;
    }

    private static boolean inside(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    /**
     * Plans the apron for one entrance, at the first {@link #STANDOFFS standoff} where the whole course fits and leaves
     * a full run, or {@code null} when none does. Reads the grid only.
     */
    public static Apron plan(NavigationGrid grid, CellTopology topology, Entrance door, KeepClear keepClear) {
        if (door.width < 1 || door.width > MAX_DOOR_WIDTH) return null;
        for (int standoff : STANDOFFS) {
            Apron apron = planAt(grid, topology, door, standoff, keepClear);
            if (apron != null) return apron;
        }
        return null;
    }

    private static Apron planAt(NavigationGrid grid, CellTopology topology, Entrance door, int standoff,
                                KeepClear keepClear) {
        int fx = door.fx, fy = door.fy, px = door.px(), py = door.py();
        // The door's lane: every cell from the doorway out through the gap and one beyond is open floor. An approach
        // with no door in it starts on open ground, which must be floor too.
        int from = grid.inBounds(door.x, door.y) && grid.isDoorway(door.x, door.y) ? 1 : 0;
        for (int w = 0; w < door.width; w++) {
            for (int d = from; d <= standoff + 1; d++) {
                int x = door.x + px * w + fx * d;
                int y = door.y + py * w + fy * d;
                if (!floor(grid, topology, x, y)) return null;
            }
        }
        List<Bag> bags = new ArrayList<>();
        List<int[]> behind = new ArrayList<>();
        Set<Long> course = new HashSet<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 1; k <= WING; k++) {
                int along = side < 0 ? -k : door.width - 1 + k;
                int x = door.x + px * along + fx * standoff;
                int y = door.y + py * along + fy * standoff;
                if (!floor(grid, topology, x, y) || keepClear.test(x, y) || besideDoor(grid, x, y)) return null;
                int bx = x - fx, by = y - fy;
                if (!floor(grid, topology, bx, by) || keepClear.test(bx, by)) return null;
                bags.add(new Bag(x, y, fx, fy, 0, 0));
                behind.add(new int[]{bx, by});
                course.add(key(x, y));
            }
            // The flank: the end of each wing turns back toward the wall by one bag where the ground allows, so the cells
            // behind the course are covered from the side as well as from the front. Optional; a wing without it stands.
            int end = side < 0 ? -WING : door.width - 1 + WING;
            int ex = door.x + px * end + fx * standoff, ey = door.y + py * end + fy * standoff;
            int sx = px * side, sy = py * side;
            int rx = ex - fx, ry = ey - fy;
            if (standoff >= 3 && floor(grid, topology, rx, ry) && !keepClear.test(rx, ry) && !besideDoor(grid, rx, ry)) {
                bags.set(bags.size() - 1, new Bag(ex, ey, fx, fy, sx, sy));
                bags.add(new Bag(rx, ry, sx, sy, 0, 0));
                behind.removeIf(c -> c[0] == rx && c[1] == ry);
                course.add(key(rx, ry));
            }
        }
        if (members(grid, course) < FightingPositionIndex.MIN_RUN_CELLS) return null;
        // The ones nearest the door first: a picket stands at the gap.
        int mx = door.midX(), my = door.midY();
        behind.sort(Comparator.comparingInt((int[] c) -> Math.abs(c[0] - mx) + Math.abs(c[1] - my))
                .thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        return new Apron(door, List.copyOf(bags), List.copyOf(behind));
    }

    /** Open floor a prop may stand on and a body may cross: walkable, and no door, wall or fixture. */
    private static boolean floor(NavigationGrid grid, CellTopology topology, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y)
                && !topology.isFixture(x, y) && !topology.isWall(x, y);
    }

    /** Whether any of the eight cells round this one is a doorway: a bag there would crowd another door. */
    private static boolean besideDoor(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(x + dx, y + dy) && grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    /**
     * The cells the fighting-position index would give this course: the bags and their walkable cardinal neighbours, by
     * the index's own rule, counted as one connected group from the first bag.
     */
    static int members(NavigationGrid grid, Set<Long> course) {
        Set<Long> member = new HashSet<>();
        for (long c : course) {
            int x = (int) (c >> 32), y = (int) c;
            member.add(c);
            for (int k = 0; k < 4; k++) {
                int nx = x + DX[k], ny = y + DY[k];
                if (grid.inBounds(nx, ny) && grid.isWalkable(nx, ny)) member.add(key(nx, ny));
            }
        }
        if (course.isEmpty()) return 0;
        // 4-connected group containing the course: the course joins through the door's gap, so it is one group.
        List<Long> queue = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        long start = course.iterator().next();
        queue.add(start);
        seen.add(start);
        for (int head = 0; head < queue.size(); head++) {
            long c = queue.get(head);
            int x = (int) (c >> 32), y = (int) c;
            for (int k = 0; k < 4; k++) {
                long n = key(x + DX[k], y + DY[k]);
                if (member.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        return seen.containsAll(course) ? seen.size() : 0;
    }

    /**
     * Lays the planned bags: the thick two-course parapet a fort's earthworks use, so a sandbagged door reads the same
     * as a sandbagged belt. Knee-high cover anybody steps over: each cell stays walkable and the prop supplies it.
     */
    public static void lay(GenContext ctx, Apron apron) {
        // Whatever loose dressing lay there (it was no fixture, or the course would have been refused) gives way.
        Set<Long> course = new HashSet<>();
        for (Bag bag : apron.bags) course.add(key(bag.x, bag.y));
        ctx.doodads.removeIf(d -> covers(d, course));
        for (Bag bag : apron.bags) {
            DoodadDef def = TileRegistry.installed().doodad("doodad.fort-sandbag-" + artName(bag));
            if (def == null) continue;
            ctx.grid.setSeeThrough(bag.x, bag.y, true);
            ctx.topology.setFixture(bag.x, bag.y, true);
            ctx.doodads.add(new Doodad(bag.x, bag.y, def));
            ctx.markMadeGround(bag.x, bag.y);
        }
    }

    /**
     * Whether the parapet art is installed. Without it nothing can be laid, and an apron with no props is no position:
     * the place is left exactly as it was.
     */
    private static boolean artInstalled() {
        TileRegistry registry = TileRegistry.installed();
        return registry != null && registry.doodad("doodad.fort-sandbag-s") != null;
    }

    /** The piece's art name: a straight face's letter, or a corner's north-south then east-west letters. */
    public static String artName(Bag bag) {
        if (!bag.corner()) return String.valueOf(letter(bag.dx, bag.dy));
        boolean firstNs = bag.dy != 0;
        char ns = firstNs ? letter(bag.dx, bag.dy) : letter(bag.cornerDx, bag.cornerDy);
        char ew = firstNs ? letter(bag.cornerDx, bag.cornerDy) : letter(bag.dx, bag.dy);
        return "corner-" + ns + ew;
    }

    private static boolean covers(Doodad d, Set<Long> cells) {
        for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++) {
            for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) {
                if (cells.contains(key(x, y))) return true;
            }
        }
        return false;
    }

    /** The sandbag art's facing letter for a world direction (y grows north). */
    static char letter(int dx, int dy) {
        if (dy > 0) return 'n';
        if (dy < 0) return 's';
        return dx > 0 ? 'e' : 'w';
    }

    private static long key(int x, int y) {
        return PreparedGround.key(x, y);
    }
}
