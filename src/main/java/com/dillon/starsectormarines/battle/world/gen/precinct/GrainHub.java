package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Fields;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.layout.Phase;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Use;
import com.dillon.starsectormarines.battle.world.gen.rural.TownFields;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * A farm world's Raid hub ({@code raid-site-templates.md}, The planet's chokepoint; Farming): the central grain
 * elevator and processing hub every farm on the planet hauls its harvest to, laid as the process runs.
 *
 * <p><b>In.</b> The farm roads are the site's feeders: they come in off the map's edges, from the planet's other
 * fields, and the fields nearest the hub line them on the map's open ground ({@link #crops}). Harvest haulers queue
 * on the yard at the intake shed's door ({@link #haulerQueue}), on the side the farm roads come in by, and grain
 * spilt in the tipping lies round it ({@link #spill}).
 *
 * <p><b>Through.</b> The intake shed with its weighbridge office stands in the rear, where the farm roads enter;
 * the dryer, the silo battery the elevator legs fill (one bin per machine the raid wrecks) and the mill stand in the
 * yard; the cold store and the loadout stand on the frontage, by the gate the haul road leaves from. The grain
 * gallery links them overhead in the order the grain passes ({@link #CHAIN}), so the hub reads as one process.
 *
 * <p><b>Aquaculture.</b> A farm world keeps a few fish ponds among its fields ({@link #POND_SHARE}). A fish-farming
 * world's hub is the same chain with the harvest from pond farms: grow-out tank halls are its machines, it has no
 * dryer, and every plot is a pond ({@link #pond}).
 */
public final class GrainHub {

    /** Where the harvest lands: the intake shed's pits, where haulers tip. */
    public static final RoomPurpose INTAKE = RoomPurpose.LOADING_BAY;
    /** The weighbridge and grading office beside the intake, where every load is weighed in. */
    public static final RoomPurpose WEIGHBRIDGE = RoomPurpose.CIVIC_OFFICE;
    /** The dryer tower that takes the field moisture out before the grain can be stored. */
    public static final RoomPurpose DRYER = RoomPurpose.PRODUCTION_FLOOR;
    /** A silo bin on the elevator's legs: the machine a raid wrecks. An aquaculture hub's grow-out tank hall. */
    public static final RoomPurpose SILO = RoomPurpose.PARTS_CAGE;
    /** The mill and packing hall. An aquaculture hub's processing hall. */
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
    private static final RoomShape TANK_HALL = RoomShape.rectangle(12, 8);
    private static final RoomShape MILL_HALL = RoomShape.rectangle(14, 9);
    private static final RoomShape COLD_STORE_HALL = RoomShape.rectangle(14, 8);
    private static final RoomShape LOADOUT_BAY = RoomShape.rectangle(10, 6);
    private static final RoomShape GUARD_POST = RoomShape.rectangle(4, 4);

    /**
     * Yard the hub keeps per machine on top of its packing slack: the hauler lanes, the turning ground at the
     * intake and the gallery's runs. A planet's harvest, not a town's. UNTUNED.
     */
    static final int YARD_PER_MACHINE = 300;

    /** Harvest haulers queued at the intake. UNTUNED. */
    static final int QUEUE = 4;
    /** Cells of yard between the intake's wall and the queue, left for the crews on foot. */
    static final int QUEUE_GAP = 1;
    /** The stand-in hauler's footprint, long side first (art: {@code raid-farm.md}). */
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

    /** The deferred layout piece the hub's ground is resolved from; a plan that lays it has a grain gallery. */
    public static final String HUB = "farm-hub";
    /** The layout piece the hub's ground is laid under. */
    public static final String FIELDS = "farm-fields";
    public static final String QUEUE_NAME = "intake-queue";

    private GrainHub() { }

    /**
     * The hub's program: the intake shed and weighbridge office in the rear where the farm roads come in, the dryer,
     * a silo bin per machine and the mill in the yard, the cold store, the loadout and the posts on the frontage. An
     * aquaculture hub has a tank hall per machine and a processing hall, and no dryer.
     *
     * @param machines silo bins (or tank halls), one per machine the raid wrecks
     */
    public static FortressProgram program(int machines, boolean aquaculture) {
        if (machines < 1) throw new IllegalArgumentException("a hub has at least one machine");
        List<FortressBuilding> b = new ArrayList<>();
        b.add(new FortressBuilding(INTAKE, INTAKE_SHED, Ward.REAR, 1));
        b.add(new FortressBuilding(WEIGHBRIDGE, WEIGHBRIDGE_OFFICE, Ward.REAR, 1));
        if (!aquaculture) b.add(new FortressBuilding(DRYER, DRYER_TOWER, Ward.YARD, 1));
        b.add(new FortressBuilding(SILO, aquaculture ? TANK_HALL : SILO_BIN, Ward.YARD, machines));
        b.add(new FortressBuilding(MILL, MILL_HALL, Ward.YARD, 1));
        b.add(new FortressBuilding(COLD_STORE, COLD_STORE_HALL, Ward.FRONTAGE, 1));
        b.add(new FortressBuilding(LOADOUT, LOADOUT_BAY, Ward.FRONTAGE, 1));
        b.add(new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE, RoomPacker.EdgeContact.ANY, 2));
        return new FortressProgram(b, 0, machines * YARD_PER_MACHINE, true);
    }

    /** The farm world's program. */
    public static FortressProgram grain(int machines) { return program(machines, false); }

    /** The fish-farming world's program. */
    public static FortressProgram aquaculture(int machines) { return program(machines, true); }

    /**
     * The hub's ground, resolved once the works stands (the works phase, before the landscape grows): the fields or
     * ponds along the farm roads, the spilt grain at the intake and loadout, and the haulers queued at the intake on
     * the side the farm roads come in by ({@code outer}, else {@code deep}).
     */
    public static Deferred ground(Side outer, Side deep, boolean aquaculture) {
        return new Deferred(HUB, Phase.WORKS, g -> resolve(g, outer, deep, aquaculture), false);
    }

    static List<Piece> resolve(LayoutGround g, Side outer, Side deep, boolean aquaculture) {
        int w = g.width(), h = g.height();
        int[] works = g.claimBounds(RaidSiteTemplate.WORKS);
        List<Piece> out = new ArrayList<>();
        if (works == null) return out;
        BiPredicate<Integer, Integer> inWorks = (x, y) -> x >= works[0] && x <= works[2] && y >= works[1]
                && y <= works[3] && g.isClaimed(x, y);
        List<List<int[]>> steps = steps(g.buildings(), g.topology(), inWorks);
        int[] intake = largest(steps.get(CHAIN.indexOf(INTAKE)));
        int[] loadout = largest(steps.get(CHAIN.indexOf(LOADOUT)));
        // A cell a hauler or the spill may take: open ground or the works' hardstand, no building, nothing beside a
        // door. A hauler keeps a walkable ring round it, so standing on an apron never closes a way.
        BiPredicate<Integer, Integer> yard = (x, y) -> x >= 0 && y >= 0 && x < w && y < h && g.open(x, y)
                && g.topology().getBuildingId(x, y) == 0 && g.topology().getGroundKind(x, y) != GroundKind.INDOOR
                && g.topology().getGroundKind(x, y) != GroundKind.WATER && !nearDoor(g, x, y);
        BiPredicate<Integer, Integer> walk = (x, y) -> x >= 0 && y >= 0 && x < w && y < h && g.walkable(x, y);
        List<int[]> spill = new ArrayList<>();
        Random rng = new Random(g.seed(HUB));
        if (intake != null) spill.addAll(spill(intake, yard, rng));
        if (loadout != null) spill.addAll(spill(loadout, yard, rng));

        List<Plot> fields = new ArrayList<>();
        List<int[]> water = new ArrayList<>();
        List<Plot> surveyed = surveyFields(g);
        Random ponds = new Random(g.seed(FIELDS) ^ 0x504f4e44L);
        for (Plot plot : crops(surveyed, g.seed(FIELDS))) {
            // A fish-farming world digs ponds in every plot; a farm world keeps a few among its fields.
            boolean dug = plot.use() != Use.WILD && (aquaculture || ponds.nextInt(100) < POND_SHARE);
            List<int[]> pond = dug ? pond(plot) : List.of();
            if (pond.isEmpty()) {
                fields.add(plot);
                continue;
            }
            water.addAll(pond);
            fields.add(new Plot(plot.x0(), plot.y0(), plot.x1(), plot.y1(), Use.PASTURE, plot.rowsAlongX(), false));
        }
        if (!fields.isEmpty() || !spill.isEmpty()) out.add(new Fields(FIELDS, fields, spill, water, false));

        if (intake != null) {
            // The haulers queue on whichever side of the intake has the room, the farm roads' side first.
            List<Props.Placement> queue = List.of();
            for (Side side : List.of(outer, deep, outer.opposite(), deep.opposite())) {
                List<Props.Placement> q = haulerQueue(intake, side, yard, walk, QUEUE);
                if (q.size() > queue.size()) queue = q;
            }
            if (!queue.isEmpty()) out.add(new Props(QUEUE_NAME, queue, false));
        }
        return out;
    }

    /**
     * The hub's halls standing, by step of {@link #CHAIN} (each list possibly empty), as boxes {x0, y0, x1, y1} round
     * their outer walls: every
     * building {@code inWorks} accepts, taken as the step its cells' room purpose mostly is.
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
                if (e.getValue() > best) { best = e.getValue(); most = e.getKey(); }
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
            if (a > area) { area = a; best = b; }
        }
        return best;
    }

    private static boolean nearDoor(LayoutGround g, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < g.width() && ny < g.height() && g.grid().isDoorway(nx, ny)) return true;
            }
        }
        return false;
    }

    /** Ground a field may be laid on: the planet's own surface, not anything made or paved. */
    private static final Set<GroundKind> NATURAL = EnumSet.of(GroundKind.GRASS, GroundKind.DIRT,
            GroundKind.STONE, GroundKind.SAND, GroundKind.SNOW, GroundKind.HARDPAN, GroundKind.RUBBLE);

    /**
     * The fields the nearest farms work round the hub: plots on open, unclaimed natural ground fronting the farm
     * roads and the places' ground ({@link TownFields}).
     */
    private static List<Plot> surveyFields(LayoutGround g) {
        int w = g.width(), h = g.height();
        boolean[][] open = new boolean[w][h], hub = new boolean[w][h], road = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                road[x][y] = g.isRoad(x, y);
                // A farm world works the open ground round every place it built, the hub's above all.
                hub[x][y] = g.isClaimed(x, y) && !road[x][y];
                open[x][y] = !road[x][y] && !g.isClaimed(x, y) && !g.isHeld(x, y) && g.buildable(x, y)
                        && NATURAL.contains(g.topology().getGroundKind(x, y));
            }
        }
        return TownFields.plan(w, h, open, hub, road, g.seed(FIELDS));
    }

    /**
     * A farm world's fields at harvest: each surveyed plot standing in grain, in row crops, or cut to stubble, drawn
     * from the plot's own seed. Wild ground stays wild.
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
     * Grain spilt in the handling round a building ({x0, y0, x1, y1}): yard cells within {@link #SPILL_REACH} of its
     * walls, thicker against them and thinning outward.
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
     * ground round it, so the queue never closes a way. The first hauler stands loaded at the door end. Empty when
     * the wall has no room.
     *
     * @param box the intake {x0, y0, x1, y1}
     */
    static List<Props.Placement> haulerQueue(int[] box, Side side, BiPredicate<Integer, Integer> yard,
                                             BiPredicate<Integer, Integer> walk, int count) {
        List<Props.Placement> out = new ArrayList<>();
        boolean alongX = side.dy != 0;
        int fw = alongX ? HAULER_LONG : HAULER_SHORT, fh = alongX ? HAULER_SHORT : HAULER_LONG;
        int turns = alongX ? 0 : 1;
        // The low corner of the first hauler, then the step along the wall.
        int x, y;
        if (side.dy > 0) { x = box[0]; y = box[3] + 1 + QUEUE_GAP; }
        else if (side.dy < 0) { x = box[0]; y = box[1] - QUEUE_GAP - fh; }
        else if (side.dx > 0) { x = box[2] + 1 + QUEUE_GAP; y = box[1]; }
        else { x = box[0] - QUEUE_GAP - fw; y = box[1]; }
        int stepX = alongX ? fw + 1 : 0, stepY = alongX ? 0 : fh + 1;
        int span = alongX ? box[2] - box[0] + 1 : box[3] - box[1] + 1;
        for (int along = 0; along + (alongX ? fw : fh) <= span + 2 && out.size() < count;
             along += alongX ? stepX : stepY) {
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
