package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctDefence;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
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
 * Dresses a refinery's Raid site once its places have grown ({@link RefiningWorks}): the tank farm in its bunds with the
 * loading rack and its tankers, the pipe racks (crude in from the map's edges along the feeders to the receiving
 * manifold, between the units, and the product out over the haul road to the farm and along it to the rack), each
 * unit's columns on the plot beside its house, the flare stack off the works' deep corner, and the spills a working
 * plant leaves. Run by the landscape pass before anything wild grows, on a map whose plan's layout holds the
 * {@link RefiningWorks#TANK_FARM} site; every other map is left alone.
 *
 * <p>The vessels, the flare and the spills are the refinery's own sheet ({@code refinery-plant}), each frame drawn over
 * the disc its fixture stands on ({@link #over}): a column's frame carries its platform ring, its vapour line and the
 * long shadow that makes it read tall, so it is drawn larger than its disc and offset up and left. The bund walls,
 * the loading rack and the tankers are still stood in by existing art drawn larger.
 */
public final class RefineryGround {

    /** The refinery's own art: a floating-roof tank, a volatiles sphere, a column and its stripper, the flare, a spill. */
    static final String TANK_ART = "refinery.tank";
    static final String SPHERE_ART = "refinery.sphere";
    static final String COLUMN_ART = "refinery.column";
    static final String STRIPPER_ART = "refinery.stripper";
    /** The column and stripper mirrored, their vapour lines leaving west. */
    static final String COLUMN_WEST_ART = "refinery.column-west", STRIPPER_WEST_ART = "refinery.stripper-west";
    static final String FLARE_ART = "refinery.flare";
    static final String SPILL_ART = "refinery.spill";
    /**
     * How each frame lies over its disc ({@code refinery-plant-export.py}): the frame's span in discs, and where the
     * disc's middle falls across it.
     */
    static final float TANK_SPAN = 1f, TANK_AT = .5f;
    static final float COLUMN_SPAN = 2.5f, COLUMN_AT = .4f;
    static final float SPHERE_SPAN = 2f, SPHERE_AT = .375f;
    static final float FLARE_SPAN = 4f, FLARE_AT = .5f;
    /**
     * The props that say what each house does ({@code refinery-utilities}): a fired heater's or the boiler's furnace
     * stack, the boiler's cooling tower, the pump skids at the receiving and pump houses, the control room's comms mast,
     * the lab's gas rack, and the loading islands between the tankers.
     */
    static final String STACK_ART = "refinery.stack";
    static final String TOWER_ART = "refinery.cooling-tower";
    static final String SKID_ART = "refinery.pump-skid";
    static final String MAST_ART = "refinery.mast";
    static final String ISLAND_ART = "refinery.loading-island";
    static final String GAS_ART = "refinery.gas-rack";
    /** A stack's chimney, and its frame: four chimneys across, the chimney 0.375 along (it leans its smoke away). */
    static final int STACK = 4;
    static final float STACK_SPAN = 4f, STACK_AT = .375f;
    /** The cooling tower's round footprint, the biggest that fits; its frame is its rim. */
    static final int[] TOWERS = {8, 6};
    /** A pump skid stands on 6 by 2 cells, drawn in a 6-cell frame from {@link #SKID_ROW} rows above them. */
    static final int SKID_X = 6, SKID_Y = 2, SKID_FRAME = 6, SKID_ROW = 2;
    /** The comms mast's base, and its frame: five bases across, centred. */
    static final int MAST = 2;
    static final float MAST_SPAN = 5f, MAST_AT = .5f;
    /** The lab's gas rack on 4 by 2 cells, drawn in a 4-cell frame from a row above them. */
    static final int GAS_X = 4, GAS_Y = 2, GAS_FRAME = 4, GAS_ROW = 1;
    /** A loading island between two tankers: the gap it stands in, and its 8-cell frame, from a cell before it. */
    static final int ISLAND_GAP = 4, ISLAND_FRAME = 8;
    /** Cells between a house's wall and a prop beside it. */
    static final int PROP_GAP = 1;
    /** A big spill where a tank weeps. */
    static final int SPILL = 6;
    /** Small stains: the loading arms' drips and the flare's soot. */
    static final String STAIN_ART = "doodad.ruins-oil-stain";
    static final String TRAIL_ART = "doodad.ruins-oil-trail";
    static final String TANKER_ART = "doodad.parked-tanker-truck";

    /** How far along a house's side a prop beside it may slide to find its ground. */
    static final int[] PLOT_SLIDES = {0, -2, 2, -4, 4, -6, 6, -8, 8, -10, 10};
    /** The process area's lanes and rack corridor: the roadway ground a fortress lays. */
    static final GroundKind LANE = GroundKind.STONE;
    /** Most units the process area stands; the raid takes as many as its tier asks of them. */
    static final int MOST_UNITS = 5;
    /** A unit's fired heater: a firebox with its stack, drawn in an 8-cell frame from a row above its box. */
    static final String HEATER_ART = "refinery.heater";
    static final int HEATER_FRAME = 8, HEATER_ROW = 1;
    /** The flare stack's footprint, and the clear ground it keeps round it. UNTUNED. */
    static final int FLARE = 8;
    /** The soot round the flare's foot. */
    static final int SCORCH = 16;
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
     * Dresses the refinery the plan lays, if it lays one: after the buildings, roads and doorways are final and before
     * the landscape pass. Every cell it dresses is marked made ground, so the landscape grows nothing on the farm, the
     * plots, the racks or the flare's ground.
     */
    public static void dress(GenContext c, PrecinctPlan plan, Precinct works) {
        if (plan == null || plan.layout() == null || works == null) return;
        if (!(plan.layout().find(RefiningWorks.TANK_FARM) instanceof Site farm)
                || farm.purpose() != SitePurpose.GROUND) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || registry.doodad(StreetDressing.PIPE_STRAIGHT) == null) return;
        int w = c.width;
        new Dresser(c, plan, new boolean[c.width * c.height], i -> natural(c, i % w, i / w), registry).dress(farm);
    }

    /**
     * A frame drawn over a disc {@code disc} cells across at {@code x}: its drawn size and where it starts, the frame
     * being {@code span} discs across with the disc's middle {@code at} of the way along it. Null when either is not a
     * whole number of cells.
     */
    static int[] over(int disc, float span, float at, int x) {
        float size = disc * span, from = x + disc / 2f - at * size;
        if (size != Math.round(size) || from != Math.round(from)) return null;
        return new int[]{Math.round(size), Math.round(from)};
    }

    /** Open natural ground: walkable country outside any building, wall, door, fixture, made ground or road. */
    static boolean natural(GenContext c, int x, int y) {
        boolean wild = switch (c.topology.getGroundKind(x, y)) {
            case GRASS, DIRT, STONE, SAND, SNOW, HARDPAN -> true;
            default -> false;
        };
        boolean[][] roads = c.get(BspKeys.ROAD_RESERVATION);
        return wild && c.grid.isWalkable(x, y) && !c.grid.isDoorway(x, y) && !c.topology.isWall(x, y)
                && !c.topology.isFixture(x, y) && c.topology.getBuildingId(x, y) == 0 && !c.isMadeGround(x, y)
                && (roads == null || !roads[x][y]);
    }

    /** A building of the works: its cells' bounding box (inclusive, y as the map's rows) and its middle. */
    record House(int left, int top, int right, int bottom) {
        House {
            if (right < left || bottom < top) throw new IllegalArgumentException("an empty house");
        }
    }

    /**
     * Lays plant on the ground: discs that stand solid and blind, frames drawn over them, made ground paved and kept
     * from the landscape, and a tank farm's bunds. The refinery's dressing is one; a story map's yards are another
     * ({@code PortYards}).
     */
    public static class Stamp {
        final GenContext c;
        /** Cells laid here, all marked made ground for the landscape. */
        final boolean[] reserved;
        final TileRegistry registry;
        final int w, h;

        public Stamp(GenContext c, TileRegistry registry) {
            this(c, new boolean[c.width * c.height], registry);
        }

        Stamp(GenContext c, boolean[] reserved, TileRegistry registry) {
            this.c = c;
            this.reserved = reserved;
            this.registry = registry;
            this.w = c.width;
            this.h = c.height;
        }

        /** Whether the cell has been laid here. */
        public boolean laid(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h && reserved[y * w + x];
        }

        /** The farm's made ground, its bunds, their tanks and walls; its loading rack is the caller's. */
        public void farm(TankFarm.Plan farm) {
            // Made ground: the loading lane an apron the tankers stand on, each bund's floor a sealed pad. The
            // walkways between the bunds stay the planet's gravel.
            int[] all = farm.paved(), lane0 = farm.lane();
            for (int y = lane0[1]; y <= lane0[3]; y++) for (int x = all[0]; x <= all[2]; x++) pave(x, y, APRON);
            for (int y = all[1]; y <= all[3]; y++) for (int x = all[0]; x <= all[2]; x++) keep(x, y);
            for (TankFarm.Bund bund : farm.bunds()) {
                for (int y = bund.y0(); y <= bund.y1(); y++) for (int x = bund.x0(); x <= bund.x1(); x++) pave(x, y, PAD);
            }
            DoodadDef stain = registry.doodad(SPILL_ART);
            boolean leaked = false;
            for (TankFarm.Bund bund : farm.bunds()) {
                for (int y = bund.y0(); y <= bund.y1(); y++) for (int x = bund.x0(); x <= bund.x1(); x++) keep(x, y);
                // One tank weeps into its bund: the stain on the bund floor beside it.
                if (!leaked && stain != null && !bund.tanks().isEmpty()) {
                    TankFarm.Tank t = bund.tanks().get(0);
                    Doodad spill = scaled(stain, SPILL, t.x() + t.size() - SPILL / 2, t.y() - 2);
                    if (spill != null) c.doodads.add(spill);
                    leaked = true;
                }
                for (TankFarm.Tank tank : bund.tanks()) tank(tank);
                for (TankFarm.Wall wall : bund.walls()) {
                    StreetDressing.jerseyBarrier(c.grid, c.topology, c.doodads, wall.x(), wall.y(), wall.alongX());
                }
            }
        }

        /**
         * The loading rack in the lane's bay: a gantry run along the bay over the loading arms, the tankers drawn up
         * under it, and the spills where the arms drip.
         */
        public void loadout(TankFarm.Plan farm) {
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
            racked(bay[0], bay[2], rackY);
            if (tanker == null) return;
            DoodadDef island = registry.doodad(ISLAND_ART);
            for (int x = bay[0] + ISLAND_GAP; x + TRUCK_X <= bay[2] + 1; x += TRUCK_X + ISLAND_GAP) {
                if (truckY < 0 || truckY + TRUCK_Y > h) break;
                boolean clear = true;
                for (int y = truckY; y < truckY + TRUCK_Y; y++) for (int dx = -ISLAND_GAP; dx < TRUCK_X; dx++) {
                    if (!c.grid.isWalkable(x + dx, y) || c.topology.isFixture(x + dx, y)) clear = false;
                }
                if (!clear) continue;
                Doodad truck = scaledBy(tanker, TRUCK_SCALE, x, truckY);
                if (truck == null) continue;
                for (int y = truckY; y < truckY + TRUCK_Y; y++) for (int dx = 0; dx < TRUCK_X; dx++) fixture(x + dx, y, true);
                c.doodads.add(truck);
                // The island the truck fills from, in the gap before it, its arms reaching over to it.
                Doodad arms = island == null ? null
                        : scaled(island, ISLAND_FRAME, x - ISLAND_GAP - 1, truckY + TRUCK_Y / 2 - ISLAND_FRAME / 2);
                if (arms == null) continue;
                for (int y = truckY; y < truckY + TRUCK_Y; y++) {
                    for (int dx = 2; dx < ISLAND_GAP; dx++) fixture(x - ISLAND_GAP + dx, y, true);
                }
                c.doodads.add(arms);
            }
        }

        /** The rack's cells along {@code y}, {@code x0} to {@code x1}: a dressing that runs more racks keeps off them. */
        void racked(int x0, int x1, int y) { }

        /**
         * A tank: its disc solid and blind; a big one a floating-roof crude tank drawn over the square it stands in, a
         * small one a volatiles sphere with its shadow.
         */
        void tank(TankFarm.Tank tank) {
            stampDisc(tank.x(), tank.y(), tank.size());
            if (tank.size() >= TankFarm.BIG) drawOver(TANK_ART, tank.size(), TANK_SPAN, TANK_AT, tank.x(), tank.y());
            else drawOver(SPHERE_ART, tank.size(), SPHERE_SPAN, SPHERE_AT, tank.x(), tank.y());
        }

        /** {@code id}'s frame drawn over the disc {@code disc} cells across at {@code (x, y)}, by {@link #over}. */
        void drawOver(String id, int disc, float span, float at, int x, int y) {
            drawOver(id, disc, span, at, at, x, y);
        }

        /** As above, the disc's middle {@code atX} along the frame and {@code atY} down it (a mirrored frame's). */
        void drawOver(String id, int disc, float span, float atX, float atY, int x, int y) {
            DoodadDef art = registry.doodad(id);
            int[] ox = over(disc, span, atX, x), oy = over(disc, span, atY, y);
            if (art == null || ox == null || oy == null) return;
            Doodad d = scaled(art, ox[0], ox[1], oy[1]);
            if (d != null) c.doodads.add(d);
        }

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
            keep(x, y);
        }

        void keep(int x, int y) {
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            reserved[y * w + x] = true;
            c.markMadeGround(x, y);
        }

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

        /**
         * {@code def} drawn {@code size} cells square from {@code (x, y)}: its sheet read at a cell so much finer that
         * its frame spans the size. Null when the frame does not divide into it.
         */
        Doodad scaled(DoodadDef def, int size, int x, int y) {
            int cell0 = def.sourceCellPx > 0 ? def.sourceCellPx : TileManifest.TILE_SIZE;
            int px = cell0 * def.footprintCellsX;
            if (size <= 0 || px % size != 0) return null;
            int cell = px / size;
            if ((def.col * cell0) % cell != 0 || (def.row * cell0) % cell != 0) return null;
            return new Doodad(x, y, new TileManifest.TileFrame(def.col * cell0 / cell, def.row * cell0 / cell),
                    def.sheetPath, def.cover.level(), def.ballisticHalfHeight, size, size, null, cell);
        }

    }

    /** One refinery's dressing, holding the cells already laid so no two pieces contend for one. */
    private static final class Dresser extends Stamp {
        final PrecinctPlan plan;
        final IntPredicate free;
        final boolean[][] roads;
        /** Cells the racks have taken: no rack runs over another. */
        final boolean[] racked;
        final Precinct works;
        final int[] claimBox;
        /** The process area's laid sides, and whether each one's spine is its left edge. */
        final List<ProcessArea.Plan> areas = new ArrayList<>();
        final List<Boolean> spineLefts = new ArrayList<>();

        Dresser(GenContext c, PrecinctPlan plan, boolean[] reserved, IntPredicate free, TileRegistry registry) {
            super(c, reserved, registry);
            this.plan = plan;
            this.free = free;
            this.roads = c.get(BspKeys.ROAD_RESERVATION);
            this.racked = new boolean[w * h];
            this.works = plan.objective();
            this.claimBox = claimBox();
        }

        @Override
        void racked(int x0, int x1, int y) {
            for (int x = x0; x <= x1; x++) racked[y * w + x] = true;
        }

        void dress(Site site) {
            List<House> receiving = rooms(RefiningWorks.RECEIVING_ROOM);
            List<House> pump = rooms(RefiningWorks.PUMP_ROOM);
            List<House> boiler = rooms(RefiningWorks.BOILER_ROOM);
            TankFarm.Plan farm = tankFarm(site);
            List<House> spines = new ArrayList<>();
            List<House> units = process(spines);
            // What each house does, stood beside it: the boiler's cooling tower and stack, the pumps where crude
            // comes in and product goes out, the control room's mast, the lab's gases.
            for (House b : boiler) {
                tower(b);
                stack(b);
            }
            for (House r : receiving) skid(r);
            for (House r : pump) skid(r);
            // The control room is the biggest house of its purpose: the gate posts are guard rooms of the same.
            House control = null;
            for (House r : rooms(RefiningWorks.CONTROL)) if (control == null || area(r) > area(control)) control = r;
            if (control != null) mast(control);
            for (House r : rooms(RefiningWorks.LAB_ROOM)) gases(r);
            if (!receiving.isEmpty()) {
                House manifold = receiving.get(0);
                for (Piece piece : plan.layout().pieces()) {
                    if (piece instanceof Road && piece.name().startsWith(RaidSiteTemplate.FEEDER)) {
                        feeder(piece.name().substring(RaidSiteTemplate.FEEDER.length()), manifold);
                    }
                }
                // Receiving, then the process area's racks (nearest first, across the road between them), then the
                // pump house: the order the stock passes.
                List<House> chain = new ArrayList<>();
                chain.add(manifold);
                List<House> left = new ArrayList<>(spines);
                while (!left.isEmpty()) {
                    House at = chain.get(chain.size() - 1);
                    left.sort(Comparator.comparingInt(p -> gap(at, p)));
                    chain.add(left.remove(0));
                }
                if (!pump.isEmpty()) chain.add(pump.get(0));
                for (int k = 0; k + 1 < chain.size(); k++) link(chain.get(k), chain.get(k + 1));
            }
            // The steam main: from the boiler house to the nearest of the process area's racks.
            if (!boiler.isEmpty() && !spines.isEmpty()) {
                House b = boiler.get(0);
                House near = spines.get(0);
                for (House u : spines) if (gap(b, u) < gap(b, near)) near = u;
                link(b, near);
            }
            // The control room's instrument and power rack to the nearest of the process area's racks: the room
            // that runs the units.
            if (control != null && !spines.isEmpty()) {
                House near = spines.get(0);
                for (House u : spines) if (gap(control, u) < gap(control, near)) near = u;
                link(control, near);
            }
            House out = !pump.isEmpty() ? pump.get(0) : spines.isEmpty() ? null : spines.get(spines.size() - 1);
            if (farm != null && out != null) product(out, farm);
            flare(units);
            defend(farm);
        }

        // ---- the field's guns ----

        /**
         * The guns of the works' open field ({@link FieldDefence}), sited by the walled places' doctrine: the process
         * area's at the works' end of its spine and the mouths of its lanes nearest the works, the tank farm's at
         * both ends of its loading lane. Each emplacement's guard post names the works, which crews it.
         */
        void defend(TankFarm.Plan farm) {
            if (works == null) return;
            DefensePostKind kind = DefensePostKind.LIGHT;
            Fortification fort = works.fortification();
            if (fort != null) {
                for (DefensePostKind k : List.of(DefensePostKind.LARGE, DefensePostKind.MEDIUM)) {
                    if (fort.posts(k) > 0) {
                        kind = k;
                        break;
                    }
                }
            }
            if (!areas.isEmpty()) {
                int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
                for (ProcessArea.Plan a : areas) {
                    x0 = Math.min(x0, a.x0());
                    y0 = Math.min(y0, a.y0());
                    x1 = Math.max(x1, a.x1());
                    y1 = Math.max(y1, a.y1());
                }
                int out = works.seedY() < (y0 + y1) / 2 ? -1 : 1;
                List<DefenceDoctrine.Gate> gates = new ArrayList<>();
                if (plan.layout().find(RefiningWorks.PROCESS_SPINE) instanceof Site spine) {
                    gates.add(FieldDefence.spineEnd(spine.x0(), spine.x1(), out < 0 ? y0 : y1, out));
                }
                for (int k = 0; k < areas.size(); k++) {
                    ProcessArea.Plan a = areas.get(k);
                    boolean left = spineLefts.get(k);
                    ProcessArea.Box lane = a.lanes().get(out < 0 ? 0 : a.blocks().size());
                    gates.add(FieldDefence.laneMouth(lane.y(), lane.y() + lane.h() - 1, left ? x1 : x0,
                            left ? 1 : -1));
                }
                guard(FieldDefence.mask(w, h, x0, y0, x1, y1), gates, kind);
            }
            if (farm != null) {
                int[] f = farm.paved(), lane = farm.lane();
                guard(FieldDefence.mask(w, h, f[0], f[1], f[2], f[3]),
                        List.of(FieldDefence.laneMouth(lane[1], lane[3], f[0], -1),
                                FieldDefence.laneMouth(lane[1], lane[3], f[2], 1)), kind);
            }
        }

        /**
         * Arms a field by the shared doctrine ({@code PrecinctDefence.stampField}): its own posts, on top of the works'
         * wall's, off the roads, crewed by the works' guard.
         */
        void guard(boolean[][] field, List<DefenceDoctrine.Gate> gates, DefensePostKind kind) {
            PrecinctDefence.stampField(c, field, gates, FieldDefence.owed(kind, gates.size()), roads,
                    RaidSiteTemplate.WORKS);
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
            farm(farm);
            loadout(farm);
            int[] lane = farm.lane();
            for (int y = lane[1]; y <= lane[3]; y++) for (int x = lane[0]; x <= lane[2]; x++) keep(x, y);
            return farm;
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

        // ---- the process area ----

        /**
         * The process area on its two sites either side of the outer feeder road ({@link ProcessArea}): each block
         * its column and stripper by the rack and its fired heater behind them, on a sealed pad; the lanes between
         * and round the blocks and the rack's corridor laid as roadway; the rack along the corridor. Each unit's
         * charge cell goes to the raid ({@code GenContext.raidMachines}), nearest the works first. Returns the units'
         * heaters, and adds each laid rack's corridor to {@code spines}.
         */
        List<House> process(List<House> spines) {
            List<ProcessArea.Block> laid = new ArrayList<>();
            List<House> units = new ArrayList<>();
            for (String name : new String[]{RefiningWorks.PROCESS_WEST, RefiningWorks.PROCESS_EAST}) {
                if (!(plan.layout().find(name) instanceof Site site) || site.purpose() != SitePurpose.GROUND) continue;
                // The west site's spine is its east edge, toward the road; the east site's its west edge.
                boolean spineLeft = RefiningWorks.PROCESS_EAST.equals(name);
                int[] clear = TankFarm.largestClear(w, site.x0(), site.y0(), site.x1(), site.y1(), free);
                if (clear == null) continue;
                // The area one process area wide from its spine edge; the site's slack beyond stays open ground.
                if (spineLeft) clear[2] = Math.min(clear[2], clear[0] + ProcessArea.width() - 1);
                else clear[0] = Math.max(clear[0], clear[2] - ProcessArea.width() + 1);
                ProcessArea.Plan area = ProcessArea.plan(clear[0], clear[1], clear[2], clear[3], spineLeft,
                        MOST_UNITS - laid.size());
                if (area == null) continue;
                areas.add(area);
                spineLefts.add(spineLeft);
                for (int y = area.y0(); y <= area.y1(); y++) for (int x = area.x0(); x <= area.x1(); x++) keep(x, y);
                for (ProcessArea.Box lane : area.lanes()) paveBox(lane, LANE);
                paveBox(area.rack(), LANE);
                for (ProcessArea.Block block : area.blocks()) {
                    unit(block, spineLeft);
                    laid.add(block);
                    ProcessArea.Box hb = block.heater();
                    units.add(new House(hb.x(), hb.y(), hb.x() + hb.w() - 1, hb.y() + hb.h() - 1));
                }
                ProcessArea.Box rack = area.rack();
                int rx = spineLeft ? rack.x() + 1 : rack.x() + rack.w() - 2;
                ProcessArea.Block first = area.blocks().get(0), last = area.blocks().get(area.blocks().size() - 1);
                int ry0 = first.pad().y(), ry1 = last.pad().y() + last.pad().h() - 1;
                StreetDressing.pipeRun(c.grid, c.topology, c.doodads, rx, ry0, rx, ry1, 4);
                for (int y = ry0; y <= ry1; y++) racked[y * w + rx] = true;
                spines.add(new House(rx, ry0, rx, ry1));
            }
            // The spine between the two sides: the lane the racks run beside, laid wherever it is open.
            if (!laid.isEmpty() && plan.layout().find(RefiningWorks.PROCESS_SPINE) instanceof Site spine
                    && spine.purpose() == SitePurpose.GROUND) {
                for (int y = spine.y0(); y <= spine.y1(); y++) for (int x = spine.x0(); x <= spine.x1(); x++) {
                    if (x <= 0 || y <= 0 || x >= w - 1 || y >= h - 1 || !free.test(y * w + x)) continue;
                    keep(x, y);
                    pave(x, y, LANE);
                }
            }
            if (works != null) {
                laid.sort(Comparator.comparingInt(b -> Math.abs(b.chargeX() - works.seedX())
                        + Math.abs(b.chargeY() - works.seedY())));
            }
            for (ProcessArea.Block b : laid) c.raidMachines.add(new int[]{b.chargeX(), b.chargeY()});
            return units;
        }

        void paveBox(ProcessArea.Box box, GroundKind kind) {
            for (int y = box.y(); y < box.y() + box.h(); y++) for (int x = box.x(); x < box.x() + box.w(); x++) {
                pave(x, y, kind);
            }
        }

        /**
         * One unit: its pad sealed, its column and stripper solid discs drawn with their vapour lines toward the
         * rack, and its fired heater a solid box drawn with its stack.
         */
        void unit(ProcessArea.Block block, boolean spineLeft) {
            paveBox(block.pad(), PAD);
            ProcessArea.Box col = block.column(), str = block.stripper(), heater = block.heater();
            stampDisc(col.x(), col.y(), col.w());
            stampDisc(str.x(), str.y(), str.w());
            for (int y = heater.y(); y < heater.y() + heater.h(); y++) {
                for (int x = heater.x(); x < heater.x() + heater.w(); x++) fixture(x, y, false);
            }
            // The unit's own pipes, under the vessels' art: the feed spur off the rack to the heater, the transfer line
            // from the heater into the column, the draw from the column to the stripper.
            for (ProcessArea.Run r : block.pipes()) {
                StreetDressing.pipeRun(c.grid, c.topology, c.doodads, r.x0(), r.y0(), r.x1(), r.y1(), 0);
                int dx = Integer.signum(r.x1() - r.x0()), dy = Integer.signum(r.y1() - r.y0());
                for (int x = r.x0(), y = r.y0(); ; x += dx, y += dy) {
                    keep(x, y);
                    if (x >= 0 && y >= 0 && x < w && y < h) racked[y * w + x] = true;
                    if (x == r.x1() && y == r.y1()) break;
                }
            }
            // The column art's vapour line leaves east; where the rack is west of it, its mirror's leaves west.
            // A frame's top row is drawn at its highest cell row, so the disc's place down the frame counts from there.
            float atX = spineLeft ? 1f - COLUMN_AT : COLUMN_AT, atY = 1f - COLUMN_AT;
            drawOver(spineLeft ? COLUMN_WEST_ART : COLUMN_ART, col.w(), COLUMN_SPAN, atX, atY, col.x(), col.y());
            drawOver(spineLeft ? STRIPPER_WEST_ART : STRIPPER_ART, str.w(), COLUMN_SPAN, atX, atY, str.x(), str.y());
            DoodadDef art = registry.doodad(HEATER_ART);
            Doodad d = art == null ? null : scaled(art, HEATER_FRAME, heater.x(), heater.y() - HEATER_ROW);
            if (d != null) c.doodads.add(d);
        }

        // ---- what each house does ----

        /**
         * Open yard of {@code pw} by {@code ph} cells {@link #PROP_GAP} off {@code house}'s wall, its ground not yet
         * dressed, where a prop of that footprint ({@code round} a disc) strands no ground: the first side and slide
         * where it fits, or null.
         */
        int[] standBeside(House house, int pw, int ph, boolean round) {
            for (int side = 0; side < 4; side++) {
                for (int slide : PLOT_SLIDES) {
                    int x, y;
                    switch (side) {
                        case 0 -> { x = house.left() + slide; y = house.bottom() + 1 + PROP_GAP; }
                        case 1 -> { x = house.left() + slide; y = house.top() - PROP_GAP - ph; }
                        case 2 -> { x = house.right() + 1 + PROP_GAP; y = house.top() + slide; }
                        default -> { x = house.left() - PROP_GAP - pw; y = house.top() + slide; }
                    }
                    if (!plotFits(x, y, pw, ph) || dressed(x, y, pw, ph)) continue;
                    List<int[]> cells = round ? disc(x, y, pw) : rect(x, y, pw, ph);
                    if (PlacementGuards.wouldStrandGround(c.grid, cells.toArray(new int[0][]))) continue;
                    return new int[]{x, y};
                }
            }
            return null;
        }

        /** Whether any cell of the box or the ring round it is already dressed. */
        boolean dressed(int x0, int y0, int pw, int ph) {
            for (int y = y0 - 1; y <= y0 + ph; y++) for (int x = x0 - 1; x <= x0 + pw; x++) {
                if (x >= 0 && y >= 0 && x < w && y < h && reserved[y * w + x]) return true;
            }
            return false;
        }

        List<int[]> rect(int x0, int y0, int pw, int ph) {
            List<int[]> out = new ArrayList<>();
            for (int y = y0; y < y0 + ph; y++) for (int x = x0; x < x0 + pw; x++) out.add(new int[]{x, y});
            return out;
        }

        static int area(House house) {
            return (house.right() - house.left() + 1) * (house.bottom() - house.top() + 1);
        }

        /** A furnace stack beside a fired heater or the boiler house: solid, its smoke and shadow drawn away. */
        void stack(House house) {
            if (registry.doodad(STACK_ART) == null) return;
            int[] at = standBeside(house, STACK, STACK, true);
            if (at == null) return;
            stampDisc(at[0], at[1], STACK);
            drawOver(STACK_ART, STACK, STACK_SPAN, STACK_AT, at[0], at[1]);
        }

        /** The boiler house's cooling tower: a big round solid beside it, a smaller one where the big does not fit. */
        void tower(House house) {
            if (registry.doodad(TOWER_ART) == null) return;
            for (int size : TOWERS) {
                int[] at = standBeside(house, size, size, true);
                if (at == null) continue;
                stampDisc(at[0], at[1], size);
                drawOver(TOWER_ART, size, 1f, .5f, at[0], at[1]);
                return;
            }
        }

        /** A pump skid against a house: low, so cover that is seen over. */
        void skid(House house) {
            lowProp(house, SKID_ART, SKID_X, SKID_Y, SKID_FRAME, SKID_ROW);
        }

        /** The lab's gas rack against its wall: low cover. */
        void gases(House house) {
            lowProp(house, GAS_ART, GAS_X, GAS_Y, GAS_FRAME, GAS_ROW);
        }

        void lowProp(House house, String id, int pw, int ph, int frame, int row) {
            DoodadDef art = registry.doodad(id);
            if (art == null) return;
            int[] at = standBeside(house, pw, ph, false);
            if (at == null) return;
            Doodad d = scaled(art, frame, at[0], at[1] - row);
            if (d == null) return;
            for (int[] cell : rect(at[0], at[1], pw, ph)) fixture(cell[0], cell[1], true);
            c.doodads.add(d);
        }

        /** The control room's comms mast: a lattice base, seen through. */
        void mast(House house) {
            if (registry.doodad(MAST_ART) == null) return;
            int[] at = standBeside(house, MAST, MAST, false);
            if (at == null) return;
            for (int[] cell : rect(at[0], at[1], MAST, MAST)) fixture(cell[0], cell[1], true);
            drawOver(MAST_ART, MAST, MAST_SPAN, MAST_AT, at[0], at[1]);
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
                keep(x, y);
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
            DoodadDef stain = registry.doodad(STAIN_ART);
            if (stain != null) {
                Doodad d = scaled(stain, SCORCH, at[0] - (SCORCH - FLARE) / 2, at[1] - (SCORCH - FLARE) / 2);
                if (d != null) c.doodads.add(d);
            }
            stampDisc(at[0], at[1], FLARE);
            drawOver(FLARE_ART, FLARE, FLARE_SPAN, FLARE_AT, at[0], at[1]);
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
