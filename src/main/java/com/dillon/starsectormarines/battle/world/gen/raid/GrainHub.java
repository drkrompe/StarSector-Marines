package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.OreLine;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralGround;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Use;
import com.dillon.starsectormarines.battle.world.gen.rural.TownFields;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

/**
 * A farm world's Raid hub ({@code raid-site-templates.md}, The planet's chokepoint; Farming): the central grain
 * elevator and processing hub every farm on the planet hauls its harvest to, laid as the process runs.
 *
 * <p><b>In.</b> The farm roads are the site's feeders: they come in off the map's edges, from the planet's other
 * fields, and the fields nearest the hub line the roads and the places on the map's open ground ({@link #crops}), a few
 * of them dug as fish ponds ({@link #pond}). Harvest haulers queue at the intake shed ({@link #haulerQueue}), the farm
 * roads' side first, and grain spilt in the tipping lies round it and round the loadout ({@link #spill}).
 *
 * <p><b>Through.</b> The intake shed with its weighbridge office stands in the rear, where the farm roads enter;
 * the dryer, the silo battery the elevator legs fill (one bin per machine the raid wrecks) and the mill stand in the
 * yard; the cold store and the loadout stand on the frontage, by the gate the haul road leaves from. The grain
 * gallery links them overhead in the order the grain passes ({@link #CHAIN}), along the bins rather than out to each,
 * so the hub reads as one process.
 */
public final class GrainHub {

    /** Where the harvest lands: the intake shed's pits, where haulers tip. */
    public static final RoomPurpose INTAKE = RoomPurpose.LOADING_BAY;
    /** The weighbridge and grading office beside the intake, where every load is weighed in. */
    public static final RoomPurpose WEIGHBRIDGE = RoomPurpose.CIVIC_OFFICE;
    /** The dryer tower that takes the field moisture out before the grain can be stored. */
    public static final RoomPurpose DRYER = RoomPurpose.PRODUCTION_FLOOR;
    /** A silo bin on the elevator's legs: the machine a raid wrecks. */
    public static final RoomPurpose SILO = RoomPurpose.PARTS_CAGE;
    /** The mill and packing hall. */
    public static final RoomPurpose MILL = RoomPurpose.SHOP_FLOOR;
    /** The cold store the packed food waits in. */
    public static final RoomPurpose COLD_STORE = RoomPurpose.STOCKROOM;
    /** The loadout, where the food leaves for the planet by the haul road. */
    public static final RoomPurpose LOADOUT = RoomPurpose.VEHICLE_BAY;

    /** The process in the order the grain passes through it; the gallery links the halls in this order. */
    public static final List<RoomPurpose> CHAIN = List.of(INTAKE, DRYER, SILO, MILL, COLD_STORE, LOADOUT);

    private static final RoomShape INTAKE_SHED = RoomShape.rectangle(16, 8);
    private static final RoomShape WEIGHBRIDGE_OFFICE = RoomShape.rectangle(5, 4);
    private static final RoomShape DRYER_TOWER = RoomShape.rectangle(8, 10);
    /** A silo bin: square, so neighbouring bins never touch at a cut corner. */
    private static final RoomShape SILO_BIN = RoomShape.rectangle(8, 8);
    private static final RoomShape MILL_HALL = RoomShape.rectangle(14, 9);
    private static final RoomShape COLD_STORE_HALL = RoomShape.rectangle(14, 8);
    private static final RoomShape LOADOUT_BAY = RoomShape.rectangle(10, 6);

    /**
     * Yard the hub keeps per machine on top of its packing slack: the hauler lanes, the turning ground at the
     * intake and the gallery's runs. A planet's harvest, not a town's. UNTUNED.
     */
    static final int YARD_PER_MACHINE = 300;

    /** Harvest haulers queued at the intake. UNTUNED. */
    static final int QUEUE = 4;
    /** Cells of yard between the intake's wall and the queue, left for the crews on foot. */
    static final int QUEUE_GAP = 1;
    /** The stand-in hauler's footprint, long side first (art: the shared note {@code raid-farm.md}). */
    static final int HAULER_LONG = 3, HAULER_SHORT = 2;
    static final String HAULER = "doodad.parked-flatbed-truck";
    static final String HAULER_LOADED = "doodad.parked-cargo-truck";

    /** Cells round the intake and loadout over which spilt grain lies. UNTUNED. */
    static final int SPILL_REACH = 3;
    /** Share of those cells the spill covers, thinner the farther from the wall. UNTUNED. */
    static final float SPILL_SHARE = 0.55f;

    /** Dry bund kept round a pond inside its plot: a walkable ring, so a pond never cuts ground off. */
    static final int BUND = 2;
    /** Share of a farm world's plots dug as fish ponds, in per cent. UNTUNED. */
    static final int POND_SHARE = 12;
    /** Shares of a farm world's fields by what they stand in at harvest: grain, row crops, cut stubble. UNTUNED. */
    static final int GRAIN_SHARE = 50, ROW_SHARE = 35;

    /** The gallery's art, by shape; the ore conveyor's stands in until a grain gallery is drawn. */
    static final String GALLERY = "ore-conveyor.";
    /** Cells a gallery link may run beyond twice its straight distance before it is left out. UNTUNED. */
    static final int LINK_SLACK = 12;

    private static final String SEED = "grain-hub";

    private GrainHub() { }

    /**
     * The hub's program: the intake shed and weighbridge office in the rear where the farm roads come in, the dryer,
     * a silo bin per machine and the mill in the yard, the cold store, the loadout and the posts on the frontage.
     *
     * @param machines silo bins, one per machine the raid wrecks
     */
    public static FortressProgram program(int machines) {
        if (machines < 1) throw new IllegalArgumentException("a grain hub has at least one silo bin");
        return new FortressProgram(List.of(
                new FortressBuilding(INTAKE, INTAKE_SHED, Ward.REAR, 1),
                new FortressBuilding(WEIGHBRIDGE, WEIGHBRIDGE_OFFICE, Ward.REAR, 1),
                new FortressBuilding(DRYER, DRYER_TOWER, Ward.YARD, 1),
                new FortressBuilding(SILO, SILO_BIN, Ward.YARD, machines),
                new FortressBuilding(MILL, MILL_HALL, Ward.YARD, 1),
                new FortressBuilding(COLD_STORE, COLD_STORE_HALL, Ward.FRONTAGE, 1),
                new FortressBuilding(LOADOUT, LOADOUT_BAY, Ward.FRONTAGE, 1),
                RaidWorksParts.guardPosts(2)),
                0, machines * YARD_PER_MACHINE, true);
    }

    /**
     * Dresses the built hub ({@link RaidIndustry#dressWorks}): the fields and ponds on the open country, the grain
     * gallery from hall to hall, the spill, and the haulers at the intake. The farm roads come in on the works'
     * {@code outer} flank and its {@code deep} end.
     */
    public static void dress(GenContext c, PrecinctPlan plan, Precinct works, Side outer, Side deep) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        Buildings standing = c.get(BspKeys.BUILDINGS);
        int index = plan.precincts().indexOf(works);
        if (claim == null || standing == null || index < 0) return;
        int w = c.width, h = c.height;
        Random rng = new Random(c.seed ^ SEED.hashCode());
        fields(c, plan, claim, rng);
        List<List<int[]>> halls = steps(standing.all(), c.topology,
                (x, y) -> x >= 0 && y >= 0 && x < w && y < h && claim[x][y] == index);
        gallery(c, halls);
        // The hub's own yard: haulers and spill stay inside its wall.
        BiPredicate<Integer, Integer> yard = (x, y) -> x >= 0 && y >= 0 && x < w && y < h && claim[x][y] == index
                && WorksProps.open(c, x, y, 1, 1) && c.topology.getBuildingId(x, y) == 0
                && c.topology.getGroundKind(x, y) != GroundKind.INDOOR
                && c.topology.getGroundKind(x, y) != GroundKind.WATER;
        BiPredicate<Integer, Integer> walk = (x, y) -> x >= 0 && y >= 0 && x < w && y < h && c.grid.isWalkable(x, y);
        int[] intake = largest(halls.get(CHAIN.indexOf(INTAKE)));
        int[] loadout = largest(halls.get(CHAIN.indexOf(LOADOUT)));
        int sand = RuralGround.surface(c, GroundKind.SAND);
        for (int[] box : new int[][]{intake, loadout}) {
            if (box == null) continue;
            for (int[] cell : spill(box, yard, rng)) {
                c.topology.setGroundKind(cell[0], cell[1], GroundKind.SAND);
                c.topology.setSurface(cell[0], cell[1], sand);
                c.topology.setNatureOverlayIndex(cell[0], cell[1], -1);
            }
        }
        if (intake != null) {
            // The haulers queue on whichever side of the intake has the room, the farm roads' side first.
            List<Props.Placement> queue = List.of();
            for (Side side : List.of(outer, deep, outer.opposite(), deep.opposite())) {
                List<Props.Placement> q = haulerQueue(intake, side, yard, walk, QUEUE);
                if (q.size() > queue.size()) queue = q;
            }
            for (Props.Placement p : queue) WorksProps.place(c, p.x(), p.y(), p.doodad(), p.quarterTurns());
        }
    }

    /** Ground a field may be laid on: the planet's own surface, not anything made or paved. */
    private static final Set<GroundKind> NATURAL = EnumSet.of(GroundKind.GRASS, GroundKind.DIRT,
            GroundKind.STONE, GroundKind.SAND, GroundKind.SNOW, GroundKind.HARDPAN, GroundKind.RUBBLE);

    /**
     * The fields the nearest farms work round the hub: plots on open, unclaimed natural ground fronting the roads and
     * the places' ground ({@link TownFields}), at harvest, a few dug as ponds, painted as the colony's fields are.
     */
    private static void fields(GenContext c, PrecinctPlan plan, int[][] claim, Random rng) {
        int w = c.width, h = c.height;
        int[][] roadOwner = c.get(BspKeys.PRECINCT_ROAD);
        boolean[][] reserved = c.get(BspKeys.ROAD_RESERVATION);
        boolean[][] held = c.get(BspKeys.HELD_GROUND);
        boolean[][] sites = plan.layout().keepClear(w, h);
        boolean[][] open = new boolean[w][h], town = new boolean[w][h], road = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                road[x][y] = reserved != null && reserved[x][y]
                        || roadOwner != null && roadOwner[x][y] != GrownTrunkPlan.UNOWNED;
                boolean claimed = claim[x][y] != GrownTrunkPlan.UNOWNED;
                // A farm world works the open ground round every place it built, the hub's above all.
                town[x][y] = claimed && !road[x][y];
                open[x][y] = !road[x][y] && !claimed && PreparedGround.open(c, x, y) && !c.isMadeGround(x, y)
                        && c.topology.getBuildingId(x, y) == 0 && (held == null || !held[x][y])
                        && (sites == null || !sites[x][y]) && NATURAL.contains(c.topology.getGroundKind(x, y));
            }
        }
        List<Plot> laid = new ArrayList<>();
        for (Plot plot : crops(TownFields.plan(w, h, open, town, road, c.seed ^ SEED.hashCode()), rng.nextLong())) {
            if (plot.use() == Use.WILD) continue;
            List<int[]> water = rng.nextInt(100) < POND_SHARE ? pond(plot) : List.of();
            Plot painted = water.isEmpty() ? plot
                    : new Plot(plot.x0(), plot.y0(), plot.x1(), plot.y1(), Use.PASTURE, plot.rowsAlongX(), false);
            if (RuralGround.paint(c, painted, (x, y) -> open[x][y]) == 0) continue;
            laid.add(painted);
            for (int[] cell : water) water(c, cell[0], cell[1]);
        }
        // A passable prop the scatter left wholly inside a field is cleared off it, as the colony's fields are.
        c.doodads.removeIf(d -> d.cover == 0 && laid.stream().anyMatch(p -> p.contains(d.cellX, d.cellY)
                && p.contains(d.cellX + d.footprintCellsX - 1, d.cellY + d.footprintCellsY - 1)));
    }

    private static void water(GenContext c, int x, int y) {
        if (!PreparedGround.open(c, x, y)) return;
        c.topology.setSurface(x, y, 0);
        c.topology.setGroundKind(x, y, GroundKind.WATER);
        c.topology.setNatureOverlayIndex(x, y, -1);
        c.topology.setTag(x, y, CellTopology.Tag.SHALLOW_WATER, false);
        c.grid.setTag(x, y, NavigationGrid.CellTag.SHALLOW_WATER, false);
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, true);
        c.grid.setEdgeCoverSuppressed(x, y, true);
        c.markMadeGround(x, y);
    }

    /**
     * The grain gallery: each step of the chain linked to the next overhead, and a step of several halls (the silo
     * battery) run along hall by hall, nearest first, rather than out to each and back. A link that would wander round
     * the works is left out. Its legs stand where a works prop may and strand nothing.
     */
    private static void gallery(GenContext c, List<List<int[]>> halls) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad(GALLERY + "straight") == null) return;
        int w = c.width, h = c.height;
        boolean[] taken = new boolean[w * h];
        for (List<int[]> step : halls) {
            for (int[] b : step) {
                for (int y = Math.max(0, b[1]); y <= Math.min(h - 1, b[3]); y++) {
                    for (int x = Math.max(0, b[0]); x <= Math.min(w - 1, b[2]); x++) taken[y * w + x] = true;
                }
            }
        }
        IntPredicate passable = i -> !taken[i] && i % w > 0 && i / w > 0 && i % w < w - 1 && i / w < h - 1
                && c.topology.getBuildingId(i % w, i / w) == 0 && !c.topology.isWall(i % w, i / w);
        IntPredicate canStand = i -> WorksProps.open(c, i % w, i / w, 1, 1)
                && !PlacementGuards.wouldStrandGround(c.grid, i % w, i / w);
        List<int[]> run = run(halls);
        for (int k = 0; k + 1 < run.size(); k++) {
            List<Props.Placement> pieces = new ArrayList<>(), legs = new ArrayList<>();
            if (!link(run.get(k), run.get(k + 1), w, h, passable, canStand, pieces, legs)) continue;
            for (Props.Placement p : pieces) {
                PreparedGround.prop(c, p.x(), p.y(), registry.doodad(p.doodad()), p.quarterTurns());
                taken[p.y() * w + p.x()] = true;
            }
            DoodadDef leg = registry.doodad(GALLERY + "leg");
            for (Props.Placement p : legs) {
                if (canStand.test(p.y() * w + p.x())) PreparedGround.prop(c, p.x(), p.y(), leg, p.quarterTurns());
            }
        }
    }

    /**
     * The halls in the order the gallery visits them: each step in turn, a step of several halls taken nearest-first
     * from the hall before it. Steps with no hall standing are passed over.
     */
    static List<int[]> run(List<List<int[]>> halls) {
        List<int[]> out = new ArrayList<>();
        for (List<int[]> step : halls) {
            List<int[]> left = new ArrayList<>(step);
            while (!left.isEmpty()) {
                int[] best = left.get(0);
                if (!out.isEmpty()) {
                    int[] at = out.get(out.size() - 1);
                    float bestD = Float.MAX_VALUE;
                    for (int[] b : left) {
                        float d = Math.abs(centre(b, 0) - centre(at, 0)) + Math.abs(centre(b, 1) - centre(at, 1));
                        if (d < bestD) { bestD = d; best = b; }
                    }
                }
                left.remove(best);
                out.add(best);
            }
        }
        return out;
    }

    private static float centre(int[] box, int axis) { return (box[axis] + box[axis + 2]) / 2f; }

    /** One link of the gallery from hall {@code from} to hall {@code to} (boxes round their walls). */
    private static boolean link(int[] from, int[] to, int w, int h, IntPredicate passable, IntPredicate canStand,
                                List<Props.Placement> pieces, List<Props.Placement> legs) {
        int[] start = beside(from, to, passable, w, h);
        if (start == null) return false;
        IntPredicate inTo = i -> i % w >= to[0] && i % w <= to[2] && i / w >= to[1] && i / w <= to[3];
        IntPredicate goal = i -> passable.test(i) && ((i % w > 0 && inTo.test(i - 1))
                || (i % w < w - 1 && inTo.test(i + 1)) || (i >= w && inTo.test(i - w))
                || (i + w < w * h && inTo.test(i + w)));
        List<int[]> path = OreLine.route(w, h, start[0], start[1], passable, goal);
        if (path.isEmpty()) return false;
        int gx = Math.max(to[0] - start[0], Math.max(0, start[0] - to[2]));
        int gy = Math.max(to[1] - start[1], Math.max(0, start[1] - to[3]));
        if (path.size() > 2 * (gx + gy) + LINK_SLACK) return false;
        int[] last = path.get(path.size() - 1);
        int intoX = -1, intoY = -1;
        for (int d = 0; d < 4 && intoX < 0; d++) {
            int nx = last[0] + (d == 0 ? 1 : d == 2 ? -1 : 0), ny = last[1] + (d == 1 ? 1 : d == 3 ? -1 : 0);
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && inTo.test(ny * w + nx)) {
                intoX = nx;
                intoY = ny;
            }
        }
        // A single cell fed and emptied on the same side is no run: two halls whose walls nearly touch.
        if (intoX < 0 || path.size() == 1 && intoX == start[2] && intoY == start[3]) return false;
        List<OreLine.Piece> course;
        try {
            course = OreLine.pieces(path, start[2], start[3], intoX, intoY, true);
        } catch (IllegalArgumentException noTurn) {
            return false;
        }
        for (OreLine.Piece p : course) {
            pieces.add(new Props.Placement(GALLERY + p.shape().name().toLowerCase(Locale.ROOT), p.x(), p.y(),
                    p.quarterTurns()));
        }
        for (int[] at : OreLine.legs(course, canStand, w)) {
            int turns = 0;
            for (OreLine.Piece p : course) if (p.x() == at[0] && p.y() == at[1]) turns = p.quarterTurns() % 2;
            legs.add(new Props.Placement(GALLERY + "leg", at[0], at[1], turns));
        }
        return true;
    }

    /**
     * Where a link from {@code from} to {@code to} starts: the open cell against {@code from}'s wall, not at a corner,
     * nearest {@code to}, with the wall cell behind it, as {@code {x, y, wallX, wallY}}.
     */
    static int[] beside(int[] from, int[] to, IntPredicate passable, int w, int h) {
        float tx = centre(to, 0), ty = centre(to, 1);
        int[] best = null;
        float bestD = Float.MAX_VALUE;
        for (int y = from[1] - 1; y <= from[3] + 1; y++) {
            for (int x = from[0] - 1; x <= from[2] + 1; x++) {
                boolean side = x == from[0] - 1 || x == from[2] + 1, end = y == from[1] - 1 || y == from[3] + 1;
                if (side == end || x < 0 || y < 0 || x >= w || y >= h || !passable.test(y * w + x)) continue;
                float d = Math.abs(x - tx) + Math.abs(y - ty);
                if (d >= bestD) continue;
                bestD = d;
                int wx = x < from[0] ? x + 1 : x > from[2] ? x - 1 : x;
                int wy = y < from[1] ? y + 1 : y > from[3] ? y - 1 : y;
                best = new int[]{x, y, wx, wy};
            }
        }
        return best;
    }

    /**
     * The hub's halls standing, by step of {@link #CHAIN} (each list possibly empty), as boxes {x0, y0, x1, y1} round
     * their outer walls: every building {@code inWorks} accepts, taken as the step its cells' room purpose mostly is.
     */
    public static List<List<int[]>> steps(Collection<Building> buildings, CellTopology topology,
                                          BiPredicate<Integer, Integer> inWorks) {
        List<List<int[]>> out = new ArrayList<>();
        for (int i = 0; i < CHAIN.size(); i++) out.add(new ArrayList<>());
        for (Building b : buildings) {
            if (b.cellCount() == 0 || !inWorks.test((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2)) continue;
            Map<RoomPurpose, Integer> votes = new EnumMap<>(RoomPurpose.class);
            for (int i = 0; i < b.cellCount(); i++) {
                RoomPurpose p = topology.getRoomPurpose(b.cellsX[i], b.cellsY[i]);
                if (p != null) votes.merge(p, 1, Integer::sum);
            }
            RoomPurpose most = null;
            int best = 0;
            for (Map.Entry<RoomPurpose, Integer> e : votes.entrySet()) {
                if (e.getValue() > best) {
                    best = e.getValue();
                    most = e.getKey();
                }
            }
            int step = most == null ? -1 : CHAIN.indexOf(most);
            // A building's cells are its floor; its outer walls stand one cell beyond.
            if (step >= 0) out.get(step).add(new int[]{b.minX - 1, b.minY - 1, b.maxX + 1, b.maxY + 1});
        }
        return out;
    }

    private static int[] largest(List<int[]> boxes) {
        int[] best = null;
        int area = 0;
        for (int[] b : boxes) {
            int a = (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
            if (a > area) {
                area = a;
                best = b;
            }
        }
        return best;
    }

    /**
     * A farm world's fields at harvest: each surveyed plot standing in grain, in row crops, or cut to stubble, drawn
     * from {@code seed}. Wild ground stays wild.
     */
    static List<Plot> crops(List<Plot> surveyed, long seed) {
        Random rng = new Random(seed ^ 0x4752414eL);
        List<Plot> out = new ArrayList<>();
        for (Plot p : surveyed) {
            if (p.use() == Use.WILD) {
                out.add(p);
                continue;
            }
            int roll = rng.nextInt(100);
            Use use = roll < GRAIN_SHARE ? Use.GRAIN : roll < GRAIN_SHARE + ROW_SHARE ? Use.ROW_CROP : Use.FALLOW;
            out.add(new Plot(p.x0(), p.y0(), p.x1(), p.y1(), use, p.rowsAlongX(), p.hedged()));
        }
        return out;
    }

    /**
     * A pond dug in a plot: its water inside a dry bund {@link #BUND} cells wide, so the ground round it stays one
     * piece. Empty when the plot is too small to hold water inside its bund.
     */
    static List<int[]> pond(Plot plot) {
        List<int[]> out = new ArrayList<>();
        int x0 = plot.x0() + BUND, x1 = plot.x1() - BUND, y0 = plot.y0() + BUND, y1 = plot.y1() - BUND;
        if (x1 - x0 < 2 || y1 - y0 < 2) return out;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) out.add(new int[]{x, y});
        return out;
    }

    /**
     * Grain spilt in the handling round a building ({x0, y0, x1, y1} round its walls): yard cells within
     * {@link #SPILL_REACH} of its walls, thicker against them and thinning outward.
     */
    static List<int[]> spill(int[] box, BiPredicate<Integer, Integer> yard, Random rng) {
        List<int[]> out = new ArrayList<>();
        for (int x = box[0] - SPILL_REACH; x <= box[2] + SPILL_REACH; x++) {
            for (int y = box[1] - SPILL_REACH; y <= box[3] + SPILL_REACH; y++) {
                int dx = Math.max(box[0] - x, Math.max(0, x - box[2]));
                int dy = Math.max(box[1] - y, Math.max(0, y - box[3]));
                int d = Math.max(dx, dy);
                if (d == 0 || !yard.test(x, y)) continue;
                float share = SPILL_SHARE * (SPILL_REACH + 1 - d) / SPILL_REACH;
                if (rng.nextFloat() < share) out.add(new int[]{x, y});
            }
        }
        return out;
    }

    /**
     * Harvest haulers queued along the intake's wall that faces {@code side}: a line parallel to that wall,
     * {@link #QUEUE_GAP} cells off it, each a hauler's footprint of {@code yard} ground with a ring of walkable
     * ground round it, so the queue never closes a way. The first hauler stands loaded at the head of the queue.
     * Empty when the wall has no room.
     *
     * @param box the intake {x0, y0, x1, y1} round its walls
     */
    static List<Props.Placement> haulerQueue(int[] box, Side side, BiPredicate<Integer, Integer> yard,
                                             BiPredicate<Integer, Integer> walk, int count) {
        List<Props.Placement> out = new ArrayList<>();
        boolean alongX = side.dy != 0;
        int fw = alongX ? HAULER_LONG : HAULER_SHORT, fh = alongX ? HAULER_SHORT : HAULER_LONG;
        int turns = alongX ? 0 : 1;
        int x, y;
        if (side.dy > 0) { x = box[0]; y = box[3] + 1 + QUEUE_GAP; }
        else if (side.dy < 0) { x = box[0]; y = box[1] - QUEUE_GAP - fh; }
        else if (side.dx > 0) { x = box[2] + 1 + QUEUE_GAP; y = box[1]; }
        else { x = box[0] - QUEUE_GAP - fw; y = box[1]; }
        int step = alongX ? fw + 1 : fh + 1;
        int span = alongX ? box[2] - box[0] + 1 : box[3] - box[1] + 1;
        for (int along = 0; along + (alongX ? fw : fh) <= span && out.size() < count; along += step) {
            int px = x + (alongX ? along : 0), py = y + (alongX ? 0 : along);
            if (!fits(px, py, fw, fh, yard, walk)) continue;
            out.add(new Props.Placement(out.isEmpty() ? HAULER_LOADED : HAULER, px, py, turns));
        }
        return out;
    }

    private static boolean fits(int x, int y, int fw, int fh, BiPredicate<Integer, Integer> yard,
                                BiPredicate<Integer, Integer> walk) {
        for (int dx = -1; dx <= fw; dx++) {
            for (int dy = -1; dy <= fh; dy++) {
                boolean inside = dx >= 0 && dy >= 0 && dx < fw && dy < fh;
                if (inside ? !yard.test(x + dx, y + dy) : !walk.test(x + dx, y + dy)) return false;
            }
        }
        return true;
    }
}
