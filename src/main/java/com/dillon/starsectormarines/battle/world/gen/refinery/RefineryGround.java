package com.dillon.starsectormarines.battle.world.gen.refinery;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.gen.precinct.RefinerySite;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Dresses a refinery's Raid site once its places have grown ({@link RefinerySite}): the tank farm in its bunds with the
 * loading rack and its tankers, the pipe racks (crude in from the map's edges along the feeders to the receiving
 * manifold, between the units, and the product out over the haul road to the farm and along it to the rack), each
 * unit's columns on the plot beside its house, the flare stack off the works' deep corner, and the spills a working
 * plant leaves. Run by the landscape pass before anything wild grows, on a map whose plan's layout holds the
 * {@link RefinerySite#TANK_FARM} site; every other map is left alone.
 *
 * <p>Art the plant has none of yet is stood in by the nearest prop drawn larger (its sheet read at a finer cell), so a
 * tank or column reads at its real size: a feed tank's roof for a storage tank, a product vessel for a column, a
 * lattice tank for the flare. Each is listed in the ruins theme art brief's refinery entry.
 */
public final class RefineryGround {

    /** Stand-in art: a storage tank's roof, a column's head, the flare's tip, and the spills. */
    static final String TANK_ART = "doodad.ruins-feed-tank";
    static final String SMALL_TANK_ART = "doodad.ruins-nutrient-tank";
    static final String COLUMN_ART = "doodad.ruins-product-tank";
    static final String FLARE_ART = "doodad.ruins-lattice-tank";
    static final String STAIN_ART = "doodad.ruins-oil-stain";
    static final String TRAIL_ART = "doodad.ruins-oil-trail";
    static final String TANKER_ART = "doodad.parked-tanker-truck";

    /**
     * A unit's columns, the main column and its stripper side by side on the plot: the biggest pair the yard beside its
     * house takes. Each a divisor of the column art's 384 px. UNTUNED.
     */
    static final int[][] PLOT_SIZES = {{8, 6}, {6, 4}};
    /** How far along the house's side a plot may slide to find its ground. */
    static final int[] PLOT_SLIDES = {0, -2, 2, -4, 4, -6, 6, -8, 8, -10, 10};
    /** Cells between a unit's house and its plot. */
    static final int PLOT_GAP = 2;
    /** The flare stack's footprint, and the clear ground it keeps round it. UNTUNED. */
    static final int FLARE = 4;
    static final int FLARE_CLEAR = 4;
    /** Cells beyond the works' claim the flare stands. UNTUNED. */
    static final int FLARE_OFF = 8;
    /** Extra cost of laying a rack over a road cell: a rack runs beside a road and bridges it square. */
    static final int ROAD_COST = 6;
    /** Extra cost of carrying a rack across another, on a higher tier. */
    static final int CROSS_COST = 12;
    /** Extra cost of carrying a rack over a wall. */
    static final int WALL_COST = 4;
    /** A tanker at the rack, drawn at twice its parked sheet so it stands to the tanks' scale; its footprint. */
    static final int TRUCK_SCALE = 2;
    static final int TRUCK_X = 6;
    static final int TRUCK_Y = 4;
    /** The farm's loading apron, and the sealed pad under a bund or a unit's columns. */
    static final GroundKind APRON = GroundKind.STRIPED;
    static final GroundKind PAD = GroundKind.COURTYARD;
    /** Cells between the stains along a pipeline. UNTUNED. */
    static final int DRIP_EVERY = 23;
    /** Cells a link inside the works may run beyond twice its straight distance before it is left out. */
    static final int LINK_SLACK = 16;

    private RefineryGround() { }

    /**
     * Dresses the refinery the plan lays, if it lays one.
     *
     * @param reserved cells the landscape keeps; every cell dressed here is added to it
     * @param free     open natural ground, whatever the landscape keeps there: the tank farm takes it on the ground its
     *                 site held (its walkways and lane keep the farm passable), the flare only where nothing is kept
     */
    public static void dress(GenContext c, PrecinctPlan plan, boolean[] reserved, IntPredicate free) {
        if (plan == null || plan.layout() == null) return;
        if (!(plan.layout().find(RefinerySite.TANK_FARM) instanceof Site farm)
                || farm.purpose() != SitePurpose.GROUND) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad(StreetDressing.PIPE_STRAIGHT) == null) return;
        new Dresser(c, plan, reserved, free, registry).dress(farm);
    }

    /** A building of the works: its cells' bounding box (inclusive, y as the map's rows) and its middle. */
    record House(int left, int top, int right, int bottom) {
        House {
            if (right < left || bottom < top) throw new IllegalArgumentException("an empty house");
        }
    }

    /** One refinery's dressing, holding the cells already laid so no two pieces contend for one. */
    private static final class Dresser {
        final GenContext c;
        final PrecinctPlan plan;
        final boolean[] reserved;
        final IntPredicate free;
        final TileRegistry registry;
        final int w, h;
        final boolean[][] roads;
        /** Cells the racks have taken: no rack runs over another. */
        final boolean[] racked;
        final Precinct works;
        final int[] claimBox;

        Dresser(GenContext c, PrecinctPlan plan, boolean[] reserved, IntPredicate free, TileRegistry registry) {
            this.c = c;
            this.plan = plan;
            this.reserved = reserved;
            this.free = free;
            this.registry = registry;
            this.w = c.width;
            this.h = c.height;
            this.roads = c.get(BspKeys.ROAD_RESERVATION);
            this.racked = new boolean[w * h];
            this.works = plan.objective();
            this.claimBox = claimBox();
        }

        void dress(Site site) {
            List<House> units = rooms(RefinerySite.UNIT_ROOM);
            List<House> receiving = rooms(RefinerySite.RECEIVING_ROOM);
            List<House> pump = rooms(RefinerySite.PUMP_ROOM);
            TankFarm.Plan farm = tankFarm(site);
            for (House unit : units) plot(unit);
            if (!receiving.isEmpty()) {
                House manifold = receiving.get(0);
                for (Piece piece : plan.layout().pieces()) {
                    if (piece instanceof Road && piece.name().startsWith(RaidSiteTemplate.FEEDER)) {
                        feeder(piece.name().substring(RaidSiteTemplate.FEEDER.length()), manifold);
                    }
                }
                // Receiving, then each unit nearest the last, then the pump house: the order the stock passes.
                List<House> chain = new ArrayList<>();
                chain.add(manifold);
                List<House> left = new ArrayList<>(units);
                while (!left.isEmpty()) {
                    House at = chain.get(chain.size() - 1);
                    left.sort(Comparator.comparingInt(p -> gap(at, p)));
                    chain.add(left.remove(0));
                }
                if (!pump.isEmpty()) chain.add(pump.get(0));
                for (int k = 0; k + 1 < chain.size(); k++) link(chain.get(k), chain.get(k + 1));
            }
            House out = !pump.isEmpty() ? pump.get(0) : units.isEmpty() ? null : units.get(units.size() - 1);
            if (farm != null && out != null) product(out, farm);
            flare(units);
        }

        // ---- the tank farm ----

        TankFarm.Plan tankFarm(Site site) {
            Precinct industry = null;
            for (Precinct p : plan.precincts()) if (RaidSiteTemplate.INDUSTRY.equals(p.name())) industry = p;
            int roadY = works == null ? (site.y0() + site.y1()) / 2
                    : industry == null ? works.seedY() : (works.seedY() + industry.seedY()) / 2;
            int nearX = industry != null ? industry.seedX() : plan.attackerFrom().x0() == 0f ? 0 : w - 1;
            TankFarm.Plan farm = TankFarm.plan(w, site.x0(), site.y0(), site.x1(), site.y1(), free, roadY, nearX);
            if (farm == null) return null;
            // Made ground: the loading lane an apron the tankers stand on, each bund's floor a sealed pad. The
            // walkways between the bunds stay the planet's gravel.
            int[] all = farm.paved(), lane0 = farm.lane();
            for (int y = lane0[1]; y <= lane0[3]; y++) for (int x = all[0]; x <= all[2]; x++) pave(x, y, APRON);
            for (int y = all[1]; y <= all[3]; y++) for (int x = all[0]; x <= all[2]; x++) keep(x, y);
            for (TankFarm.Bund bund : farm.bunds()) {
                for (int y = bund.y0(); y <= bund.y1(); y++) for (int x = bund.x0(); x <= bund.x1(); x++) pave(x, y, PAD);
            }
            DoodadDef stain = registry.doodad(STAIN_ART);
            boolean leaked = false;
            for (TankFarm.Bund bund : farm.bunds()) {
                for (int y = bund.y0(); y <= bund.y1(); y++) for (int x = bund.x0(); x <= bund.x1(); x++) keep(x, y);
                // One tank weeps into its bund: the stain on the bund floor beside it.
                if (!leaked && stain != null && !bund.tanks().isEmpty()) {
                    TankFarm.Tank t = bund.tanks().get(0);
                    Doodad spill = scaled(stain, 4, t.x() + t.size() - 3, t.y() - 2);
                    if (spill != null) c.doodads.add(spill);
                    leaked = true;
                }
                for (TankFarm.Tank tank : bund.tanks()) tank(tank);
                for (TankFarm.Wall wall : bund.walls()) {
                    StreetDressing.jerseyBarrier(c.grid, c.topology, c.doodads, wall.x(), wall.y(), wall.alongX());
                }
            }
            loadout(farm);
            int[] lane = farm.lane();
            for (int y = lane[1]; y <= lane[3]; y++) for (int x = lane[0]; x <= lane[2]; x++) keep(x, y);
            return farm;
        }

        /** A tank: its disc solid and blind, its roof drawn over the square it stands in. */
        void tank(TankFarm.Tank tank) {
            DoodadDef art = registry.doodad(tank.size() >= TankFarm.BIG ? TANK_ART : SMALL_TANK_ART);
            stampDisc(tank.x(), tank.y(), tank.size());
            if (art == null) return;
            Doodad d = scaled(art, tank.size(), tank.x(), tank.y());
            if (d != null) c.doodads.add(d);
        }

        /**
         * The loading rack in the lane's bay: a gantry run along the bay over the loading arms, the tankers drawn up
         * under it, and the spills where the arms drip.
         */
        void loadout(TankFarm.Plan farm) {
            int[] bay = farm.bay();
            int rackY = farm.laneDy() < 0 ? bay[3] - 1 : bay[1] + 1;
            int truckY = farm.laneDy() < 0 ? rackY - TRUCK_Y : rackY + 1;
            DoodadDef stain = registry.doodad(STAIN_ART), tanker = registry.doodad(TANKER_ART);
            if (stain != null) {
                for (int x = bay[0] + 2; x + 2 <= bay[2]; x += 5) {
                    Doodad d = new Doodad(x, farm.laneDy() < 0 ? rackY - 1 : rackY, stain);
                    c.doodads.add(d);
                }
            }
            StreetDressing.pipeRun(c.grid, c.topology, c.doodads, bay[0], rackY, bay[2], rackY, 4);
            for (int x = bay[0]; x <= bay[2]; x++) racked[rackY * w + x] = true;
            if (tanker == null) return;
            for (int x = bay[0] + 1; x + TRUCK_X <= bay[2] + 1; x += TRUCK_X + 2) {
                if (truckY < 0 || truckY + TRUCK_Y > h) break;
                boolean clear = true;
                for (int y = truckY; y < truckY + TRUCK_Y; y++) for (int dx = 0; dx < TRUCK_X; dx++) {
                    if (!c.grid.isWalkable(x + dx, y) || c.topology.isFixture(x + dx, y)) clear = false;
                }
                if (!clear) continue;
                Doodad truck = scaledBy(tanker, TRUCK_SCALE, x, truckY);
                if (truck == null) continue;
                for (int y = truckY; y < truckY + TRUCK_Y; y++) for (int dx = 0; dx < TRUCK_X; dx++) fixture(x + dx, y, true);
                c.doodads.add(truck);
            }
        }

        // ---- the units' plots ----

        /** A unit's columns on open yard beside its house: the first side and slide where the plot fits. */
        void plot(House unit) {
            for (int[] sizes : PLOT_SIZES) if (plot(unit, sizes[0], sizes[1])) return;
        }

        /** The plot of a column and its stripper of these sizes beside the house, if one fits; true when laid. */
        boolean plot(House unit, int column, int stripper) {
            DoodadDef art = registry.doodad(COLUMN_ART);
            int len = column + PLOT_GAP + stripper;
            for (int side = 0; side < 4; side++) {
                boolean alongX = side < 2;
                int pw = alongX ? len : column, ph = alongX ? column : len;
                for (int slide : PLOT_SLIDES) {
                    int x, y;
                    switch (side) {
                        case 0 -> { x = unit.left() + slide; y = unit.bottom() + 1 + PLOT_GAP; }
                        case 1 -> { x = unit.left() + slide; y = unit.top() - PLOT_GAP - ph; }
                        case 2 -> { x = unit.right() + 1 + PLOT_GAP; y = unit.top() + slide; }
                        default -> { x = unit.left() - PLOT_GAP - pw; y = unit.top() + slide; }
                    }
                    if (!plotFits(x, y, pw, ph)) continue;
                    int sx = alongX ? x + column + PLOT_GAP : x + (column - stripper) / 2;
                    int sy = alongX ? y + (column - stripper) / 2 : y + column + PLOT_GAP;
                    if (strands(x, y, column) || strands(sx, sy, stripper)) continue;
                    for (int py = y - 1; py <= y + ph; py++) for (int px = x - 1; px <= x + pw; px++) pave(px, py, PAD);
                    stampDisc(x, y, column);
                    stampDisc(sx, sy, stripper);
                    if (art != null) {
                        Doodad a = scaled(art, column, x, y), b = scaled(art, stripper, sx, sy);
                        if (a != null) c.doodads.add(a);
                        if (b != null) c.doodads.add(b);
                    }
                    return true;
                }
            }
            return false;
        }

        /** The plot and a ring round it are open yard inside the works: walkable, off the roads and doors. */
        boolean plotFits(int x0, int y0, int pw, int ph) {
            for (int y = y0 - 1; y <= y0 + ph; y++) {
                for (int x = x0 - 1; x <= x0 + pw; x++) {
                    if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) return false;
                    if (!yard(x, y) || !inWorks(x, y)) return false;
                }
            }
            return true;
        }

        boolean strands(int x0, int y0, int size) {
            List<int[]> cells = disc(x0, y0, size);
            return PlacementGuards.wouldStrandGround(c.grid, cells.toArray(new int[0][]));
        }

        // ---- the racks ----

        /** A feeder's pipeline: from the map's edge on its side, beside the feeder's road, to the receiving manifold. */
        void feeder(String sideName, House manifold) {
            int[] start = edgeStart(sideName.toUpperCase(Locale.ROOT));
            if (start == null) return;
            List<int[]> course = PipeRack.route(w, h, start[0], start[1], cost(), next(manifold));
            lay(course, Integer.MAX_VALUE);
            trunk(course);
            drips(course);
        }

        /**
         * A pipeline weeps at its flanges: a stain under every {@link #DRIP_EVERY}th cell of a trunk line out in the
         * country, on open ground beside it.
         */
        void drips(List<int[]> course) {
            DoodadDef trail = registry.doodad(TRAIL_ART);
            if (trail == null) return;
            for (int i = DRIP_EVERY / 2; i < course.size(); i += DRIP_EVERY) {
                int[] at = course.get(i);
                if (inWorks(at[0], at[1]) || (roads != null && roads[at[0]][at[1]])
                        || !c.grid.isWalkable(at[0], at[1]) || c.topology.isFixture(at[0], at[1])) continue;
                boolean alongX = i + 1 < course.size() && course.get(i + 1)[1] == at[1];
                // The trail lies along the line: its frame runs along y, so a quarter turn lays it along x.
                c.doodads.add(new Doodad(at[0], alongX ? at[1] : at[1] - 1, trail, alongX ? 1 : 0));
            }
        }

        /** The second rack beside a trunk line's, where every cell of it may be laid. */
        void trunk(List<int[]> course) {
            List<int[]> twin = PipeRack.twin(course);
            IntUnaryOperator cost = cost();
            for (int[] cell : twin) if (cost.applyAsInt(cell[1] * w + cell[0]) < 0) return;
            lay(twin, Integer.MAX_VALUE);
        }

        /** The start of a pipeline at the map's edge: beside the feeder road where it leaves, else level with the works. */
        int[] edgeStart(String side) {
            int along = switch (side) { case "EAST", "WEST" -> h; default -> w; };
            int fixed = switch (side) { case "EAST" -> w - 2; case "WEST" -> 1; case "NORTH" -> h - 2; default -> 1; };
            boolean onX = side.equals("EAST") || side.equals("WEST");
            int aim = works == null ? along / 2 : onX ? works.seedY() : works.seedX();
            int road = -1;
            for (int d = 0; d < along / 2 && road < 0; d++) {
                for (int s : new int[]{aim - d, aim + d}) {
                    if (s < 1 || s >= along - 1) continue;
                    int x = onX ? fixed : s, y = onX ? s : fixed;
                    if (roads != null && roads[x][y]) { road = s; break; }
                }
            }
            int base = road < 0 ? aim : road;
            // Beside the road: past its far kerb toward the works' outer side, or whichever side is open.
            for (int off : new int[]{4, -4, 5, -5, 6, -6, 0}) {
                int s = base + off;
                if (s < 1 || s >= along - 1) continue;
                int x = onX ? fixed : s, y = onX ? s : fixed;
                int i = y * w + x;
                if (roads != null && roads[x][y] && off != 0) continue;
                if (cost().applyAsInt(i) >= 0) return new int[]{x, y};
            }
            return null;
        }

        /** A link of the plant's racks from one building's wall to the next's, left out when it would wander. */
        void link(House from, House to) {
            int[] start = beside(from, (to.left() + to.right()) / 2f, (to.top() + to.bottom()) / 2f);
            if (start == null) return;
            int gx = Math.max(to.left() - start[0], Math.max(0, start[0] - to.right()));
            int gy = Math.max(to.top() - start[1], Math.max(0, start[1] - to.bottom()));
            lay(PipeRack.route(w, h, start[0], start[1], cost(), next(to)), 2 * (gx + gy) + LINK_SLACK);
        }

        /**
         * The product line: from the pump house out over the glacis and the haul road to the tank farm's lane, then
         * along the lane to the loading rack.
         */
        void product(House pump, TankFarm.Plan farm) {
            int[] lane = farm.lane(), bay = farm.bay();
            int row = farm.laneDy() < 0 ? lane[3] : lane[1];
            int[] paved = farm.paved();
            int farX = bay[0] == lane[0] ? paved[2] : paved[0];
            int[] start = beside(pump, (lane[0] + lane[2]) / 2f, row);
            if (start == null) return;
            IntPredicate laneEnd = i -> i / w == row && Math.abs(i % w - farX) <= 3;
            List<int[]> out = PipeRack.route(w, h, start[0], start[1], cost(), laneEnd);
            if (out.isEmpty()) return;
            lay(out, Integer.MAX_VALUE);
            trunk(out);
            int[] end = out.get(out.size() - 1);
            int bayX = bay[0] == lane[0] ? bay[2] + 1 : bay[0] - 1;
            List<int[]> along = new ArrayList<>();
            int dx = Integer.signum(bayX - end[0]);
            for (int x = end[0] + dx; dx != 0 && x != bayX + dx; x += dx) along.add(new int[]{x, row});
            lay(along, Integer.MAX_VALUE);
        }

        /** Lays a course's pieces, unless it is empty or longer than {@code most}. */
        void lay(List<int[]> path, int most) {
            if (path.isEmpty() || path.size() > most) return;
            DoodadDef beam = registry.doodad(StreetDressing.PIPE_BEAM), column = registry.doodad(StreetDressing.GANTRY_COLUMN);
            DoodadDef[] straight = {registry.doodad(StreetDressing.PIPE_STRAIGHT),
                    registry.doodad(StreetDressing.PIPE_STRAIGHT_B)};
            List<PipeRack.Piece> pieces = PipeRack.pieces(path, this::canStand, w);
            int k = 0;
            for (PipeRack.Piece piece : pieces) {
                int x = piece.x(), y = piece.y();
                int turns = piece.alongX() ? 0 : 1;
                if (piece.kind() == PipeRack.Kind.GANTRY) {
                    if (column != null && canStand(y * w + x)) {
                        fixture(x, y, true);
                        c.doodads.add(new Doodad(x, y, column));
                    }
                    if (beam != null) c.doodads.add(new Doodad(x, y, beam, turns));
                } else if (straight[k % 2] != null) {
                    c.doodads.add(new Doodad(x, y, straight[k % 2], turns));
                }
                k++;
                racked[y * w + x] = true;
                reserved[y * w + x] = true;
            }
        }

        /** Where a column may stand: open ground off the roads and doorways, whose loss strands nothing. */
        boolean canStand(int i) {
            int x = i % w, y = i / w;
            if (roads != null && roads[x][y]) return false;
            return c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y) && !c.topology.isFixture(x, y)
                    && !c.topology.isWall(x, y) && !PlacementGuards.touchesDoorway(c.grid, x, y)
                    && !PlacementGuards.wouldStrandGround(c.grid, x, y);
        }

        /**
         * What a rack pays to cross a cell: never a building, tank, fixture or the edge rows; a wall, a road and another
         * rack dearly, so it runs beside roads and bridges them square, and crosses another rack only where it must.
         */
        IntUnaryOperator cost() {
            return i -> {
                int x = i % w, y = i / w;
                if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) return -1;
                if (racked[i]) return CROSS_COST;
                if (c.topology.getBuildingId(x, y) != 0) return -1;
                if (c.topology.isWall(x, y)) return WALL_COST;
                if (!c.grid.isWalkable(x, y) || c.topology.isFixture(x, y)) return -1;
                if (roads != null && roads[x][y]) return ROAD_COST;
                return 0;
            };
        }

        /** The open cells against a building's wall: where a rack running to it ends. */
        IntPredicate next(House to) {
            return i -> {
                int x = i % w, y = i / w;
                boolean inX = x >= to.left() && x <= to.right(), inY = y >= to.top() && y <= to.bottom();
                return (inX && (y == to.top() - 1 || y == to.bottom() + 1)) || (inY && (x == to.left() - 1 || x == to.right() + 1));
            };
        }

        /** The open cell against {@code from}'s wall nearest {@code (tx, ty)} that a rack may start on. */
        int[] beside(House from, float tx, float ty) {
            IntUnaryOperator cost = cost();
            int[] best = null;
            float bestD = Float.MAX_VALUE;
            for (int y = from.top() - 1; y <= from.bottom() + 1; y++) {
                for (int x = from.left() - 1; x <= from.right() + 1; x++) {
                    boolean side = x == from.left() - 1 || x == from.right() + 1, end = y == from.top() - 1 || y == from.bottom() + 1;
                    if (side == end || x < 0 || y < 0 || x >= w || y >= h) continue;
                    if (cost.applyAsInt(y * w + x) != 0) continue;
                    float d = Math.abs(x - tx) + Math.abs(y - ty);
                    if (d < bestD) { bestD = d; best = new int[]{x, y}; }
                }
            }
            return best;
        }

        // ---- the flare ----

        /**
         * The flare stack: alone on open ground {@link #FLARE_OFF} past the works' claim at its deep, outer corner,
         * its scorched ground round it, and its header from the nearest unit.
         */
        void flare(List<House> units) {
            if (claimBox == null) return;
            boolean deepEast = plan.attackerFrom().x0() == 0f;
            boolean outerNorth = works != null && works.seedY() > h / 2;
            int ax = deepEast ? claimBox[2] + FLARE_OFF : claimBox[0] - FLARE_OFF - FLARE;
            int ay = outerNorth ? claimBox[3] - FLARE : claimBox[1];
            int[] at = null;
            for (int r = 0; r <= 24 && at == null; r++) {
                for (int dy = -r; dy <= r && at == null; dy++) for (int dx = -r; dx <= r && at == null; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (flareFits(ax + dx, ay + dy)) at = new int[]{ax + dx, ay + dy};
                }
            }
            if (at == null) return;
            DoodadDef stain = registry.doodad(STAIN_ART), tip = registry.doodad(FLARE_ART);
            int scorch = 2 * FLARE;
            if (stain != null) {
                Doodad d = scaled(stain, scorch, at[0] - FLARE / 2, at[1] - FLARE / 2);
                if (d != null) c.doodads.add(d);
            }
            stampDisc(at[0], at[1], FLARE);
            if (tip != null) {
                Doodad d = scaled(tip, FLARE, at[0], at[1]);
                if (d != null) c.doodads.add(d);
            }
            for (int y = at[1] - FLARE_CLEAR; y < at[1] + FLARE + FLARE_CLEAR; y++) {
                for (int x = at[0] - FLARE_CLEAR; x < at[0] + FLARE + FLARE_CLEAR; x++) keep(x, y);
            }
            House nearest = null;
            for (House u : units) {
                if (nearest == null || dist(u, at) < dist(nearest, at)) nearest = u;
            }
            if (nearest == null) return;
            int[] start = beside(nearest, at[0] + FLARE / 2f, at[1] + FLARE / 2f);
            if (start == null) return;
            int fx = at[0], fy = at[1];
            IntPredicate goal = i -> {
                int x = i % w, y = i / w;
                return x >= fx - 1 && x <= fx + FLARE && y >= fy - 1 && y <= fy + FLARE;
            };
            int manhattan = Math.abs(start[0] - fx) + Math.abs(start[1] - fy);
            lay(PipeRack.route(w, h, start[0], start[1], cost(), goal), 2 * manhattan + LINK_SLACK);
        }

        boolean flareFits(int x0, int y0) {
            for (int y = y0 - FLARE_CLEAR; y < y0 + FLARE + FLARE_CLEAR; y++) {
                for (int x = x0 - FLARE_CLEAR; x < x0 + FLARE + FLARE_CLEAR; x++) {
                    if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) return false;
                    if (!free.test(y * w + x) || reserved[y * w + x]) return false;
                }
            }
            return true;
        }

        // ---- helpers ----

        /** The cells of the disc in the {@code size} square at {@code (x0, y0)}. */
        List<int[]> disc(int x0, int y0, int size) {
            List<int[]> out = new ArrayList<>();
            float r = size / 2f, cx = x0 + r, cy = y0 + r;
            for (int y = y0; y < y0 + size; y++) for (int x = x0; x < x0 + size; x++) {
                float dx = x + .5f - cx, dy = y + .5f - cy;
                if (dx * dx + dy * dy <= r * r + .25f) out.add(new int[]{x, y});
            }
            return out;
        }

        /** A vessel's disc: not walkable, not seen or shot through, and cover to whoever stands against it. */
        void stampDisc(int x0, int y0, int size) {
            for (int[] cell : disc(x0, y0, size)) fixture(cell[0], cell[1], false);
        }

        void fixture(int x, int y, boolean seeThrough) {
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            c.grid.setWalkable(x, y, false);
            c.grid.setSeeThrough(x, y, seeThrough);
            c.topology.setFixture(x, y, true);
            reserved[y * w + x] = true;
        }

        void keep(int x, int y) {
            if (x >= 0 && y >= 0 && x < w && y < h) reserved[y * w + x] = true;
        }

        /**
         * {@code def} drawn {@code size} cells square from {@code (x, y)}: its sheet read at a cell so much finer that
         * its frame spans the size. Null when the frame does not divide into it.
         */
        /** {@code def} drawn {@code k} times its sheet size from {@code (x, y)}, or null when its frame does not divide. */
        Doodad scaledBy(DoodadDef def, int k, int x, int y) {
            int cell0 = def.sourceCellPx > 0 ? def.sourceCellPx : TileManifest.TILE_SIZE;
            if (k <= 0 || cell0 % k != 0) return null;
            int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
            return new Doodad(x, y, new TileManifest.TileFrame(def.col * k, def.row * k), def.sheetPath,
                    def.cover.level(), def.ballisticHalfHeight, fx * k, fy * k, null, cell0 / k);
        }

        /** Paves a cell as made ground of {@code kind}: nothing wild dresses it. */
        void pave(int x, int y, GroundKind kind) {
            if (x < 0 || y < 0 || x >= w || y >= h || c.topology.getBuildingId(x, y) != 0 || c.topology.isWall(x, y)) return;
            c.topology.setGroundKind(x, y, kind);
            c.topology.setSurface(x, y, 0);
            c.topology.setNatureOverlayIndex(x, y, -1);
            keep(x, y);
        }

        Doodad scaled(DoodadDef def, int size, int x, int y) {
            int cell0 = def.sourceCellPx > 0 ? def.sourceCellPx : TileManifest.TILE_SIZE;
            int px = cell0 * def.footprintCellsX;
            if (size <= 0 || px % size != 0) return null;
            int cell = px / size;
            if ((def.col * cell0) % cell != 0 || (def.row * cell0) % cell != 0) return null;
            return new Doodad(x, y, new TileManifest.TileFrame(def.col * cell0 / cell, def.row * cell0 / cell),
                    def.sheetPath, def.cover.level(), def.ballisticHalfHeight, size, size, null, cell);
        }

        /**
         * The works' buildings whose rooms are {@code purpose}, nearest the works' seed first: found on the ground, by
         * the cells of each building in the works' claim, since not every room is a point of interest.
         */
        List<House> rooms(RoomPurpose purpose) {
            Set<Integer> ids = new HashSet<>();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int id = c.topology.getBuildingId(x, y);
                if (id != 0 && inWorks(x, y) && c.topology.getRoomPurpose(x, y) == purpose) ids.add(id);
            }
            // The whole building, its walls with its rooms, so a rack ends against its outside.
            Map<Integer, int[]> boxes = new LinkedHashMap<>();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int id = c.topology.getBuildingId(x, y);
                if (!ids.contains(id)) continue;
                int[] b = boxes.get(id);
                if (b == null) boxes.put(id, b = new int[]{x, y, x, y});
                b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], y);
            }
            List<House> out = new ArrayList<>();
            for (int[] b : boxes.values()) out.add(new House(b[0], b[1], b[2], b[3]));
            if (works != null) {
                out.sort(Comparator.comparingInt(p -> Math.abs((p.left() + p.right()) / 2 - works.seedX())
                        + Math.abs((p.top() + p.bottom()) / 2 - works.seedY())));
            }
            return out;
        }

        boolean inWorks(int x, int y) {
            int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
            int index = works == null ? -1 : plan.precincts().indexOf(works);
            if (claim == null || index < 0) return false;
            return x >= 0 && y >= 0 && x < claim.length && y < claim[0].length && claim[x][y] == index;
        }

        /** Open yard: walkable ground, not a road, building, wall, fixture or doorway, nor taken by a rack. */
        boolean yard(int x, int y) {
            if (roads != null && roads[x][y]) return false;
            return c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y) && !c.topology.isFixture(x, y)
                    && !c.topology.isWall(x, y) && c.topology.getBuildingId(x, y) == 0 && !racked[y * w + x];
        }

        /** The works' claim's bounding box, or null when the plan has no claim. */
        int[] claimBox() {
            int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
            int index = works == null ? -1 : plan.precincts().indexOf(works);
            if (claim == null || index < 0) return null;
            int x0 = w, y0 = h, x1 = -1, y1 = -1;
            for (int x = 0; x < Math.min(w, claim.length); x++) for (int y = 0; y < Math.min(h, claim[x].length); y++) {
                if (claim[x][y] != index) continue;
                x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
            }
            return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
        }

        static int gap(House a, House b) {
            return Math.abs((a.left() + a.right()) / 2 - (b.left() + b.right()) / 2) + Math.abs((a.top() + a.bottom()) / 2 - (b.top() + b.bottom()) / 2);
        }

        static int dist(House a, int[] at) {
            return Math.abs((a.left() + a.right()) / 2 - at[0]) + Math.abs((a.top() + a.bottom()) / 2 - at[1]);
        }
    }
}
