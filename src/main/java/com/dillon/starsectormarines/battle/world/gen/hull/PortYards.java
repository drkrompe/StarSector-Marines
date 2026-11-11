package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.raid.RefineryGround;
import com.dillon.starsectormarines.battle.world.gen.raid.TankFarm;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A port's yards on the ground a colony would farm ({@code long-survey-maps.md}): on a story map whose patch asks for
 * them ({@link HullSitePlan#yards}), each worked field wholly south of that row is no field but a yard of the port,
 * two tank farms in their bunds with their loading racks and pipe runs to each apron of landing pads
 * with their fuel line and the stores and bowsers stood between them. Laid when the hull is finished, before the shower
 * falls and the claim lattice seats its points, so the lattice and the haul roads see the yards as they stand. Every
 * other field keeps the planet skin's rule.
 *
 * <p>A yard stays inside its field, which the layout already keeps off the columns' ways and their band; nothing that
 * stands is laid in the landing's field of fire, and nothing on the hull, its trench or its aprons.
 */
public final class PortYards {

    /** Cells kept bare inside a field's edge: the yard's own headland, where its pipe runs. */
    static final int HEADLAND = 1;
    /** A landing pad's side, its painted ring included, and the apron left between two pads. UNTUNED. */
    static final int PAD = 11, PAD_GAP = 6;
    /** A yard's loading lane: narrower than a refinery's, a port's farm loads a bowser at a time. UNTUNED. */
    static final int LANE = 5;
    /** Cells between a pipe run's columns. */
    static final int PIPE_BAY = 6;
    /** The landing apron's ground and a pad's painted ring. */
    static final GroundKind APRON = GroundKind.COURTYARD, MARKED = GroundKind.STRIPED;
    static final String PAD_EDGE = "doodad.fl-striped-yellow";
    /** What stands between two pads, cycled down each gap: stores, a bowser's tank, a pump set. */
    static final String[] PAD_KIT = {"doodad.industrial-crate-stack", "doodad.fuel-tank-vertical",
            "doodad.industrial-pump-set", "doodad.industrial-fluid-tank"};
    /** Cells between two pieces of kit down a gap. */
    static final int KIT_STEP = 7;

    /** The narrowest clear piece of a field a yard is laid on, and the most pieces one field takes. */
    static final int SMALLEST = 16, MOST_PIECES = 4;

    private PortYards() { }

    /**
     * Lays the yards on {@code ctx}'s fields wholly south of {@code southOf} (their top row below it) and takes those
     * fields off the map's fields, so no skin grows a farm on them. Fields the hull, trench or aprons touch are already
     * gone ({@link CrashSiteStage#dropFields}).
     */
    static void lay(GenContext ctx, int southOf, BitSet hull, BitSet trench, BitSet aprons, FieldOfFire fire) {
        List<RuralLayout.Plot> fields = ctx.get(BspKeys.WORKED_FIELDS);
        TileRegistry registry = TileRegistry.installed();
        if (southOf <= 0 || fields == null || fields.isEmpty() || registry == null) return;
        int w = ctx.width, h = ctx.height;
        boolean[][] roads = ctx.get(BspKeys.ROAD_RESERVATION);
        List<RuralLayout.Plot> yards = new ArrayList<>(), kept = new ArrayList<>();
        for (RuralLayout.Plot plot : fields) (plot.y1() < southOf ? yards : kept).add(plot);
        if (yards.isEmpty()) return;
        yards.sort(Comparator.comparingInt(RuralLayout.Plot::y0).thenComparingInt(RuralLayout.Plot::x0));
        RefineryGround.Stamp stamp = new RefineryGround.Stamp(ctx, registry);
        BitSet taken = new BitSet(w * h);
        IntPredicate open = i -> {
            int x = i % w, y = i / w;
            if (taken.get(i) || hull.get(i) || trench.get(i) || aprons.get(i) || stamp.laid(x, y)) return false;
            // A field's tracks still carry the farm's traffic: a yard keeps a cell off them.
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (roads != null && nx >= 0 && ny >= 0 && nx < w && ny < h && roads[nx][ny]) return false;
            }
            if (fire != null && fire.contains(x, y)) return false;
            return ctx.grid.isWalkable(x, y) && !ctx.grid.isDoorway(x, y) && !ctx.topology.isWall(x, y)
                    && !ctx.topology.isFixture(x, y) && ctx.topology.getBuildingId(x, y) == 0;
        };
        int laid = 0;
        for (RuralLayout.Plot plot : yards) {
            clear(ctx, plot, hull, trench, aprons, roads);
            int x0 = Math.max(1, plot.x0() + HEADLAND), y0 = Math.max(1, plot.y0() + HEADLAND);
            int x1 = Math.min(w - 2, plot.x1() - HEADLAND), y1 = Math.min(h - 2, plot.y1() - HEADLAND);
            // The yard faces the town: its lane, or its pads' fuel line behind them, on the side toward the middle.
            boolean townNorth = (y0 + y1) / 2 < h / 2;
            // A field's tracks and the landing's field of fire part its ground: each clear piece big enough takes a
            // yard, by turns two tank farms to an apron of pads, so a row of fields reads as a port's yards.
            for (int piece = 0; piece < MOST_PIECES && x1 > x0 && y1 > y0; piece++) {
                int[] r = TankFarm.largestClear(w, x0, y0, x1, y1, open);
                if (r == null || r[2] - r[0] + 1 < SMALLEST || r[3] - r[1] + 1 < SMALLEST) break;
                boolean placed = laid % 3 != 2 && tanks(ctx, stamp, w, r[0], r[1], r[2], r[3], open, townNorth);
                if (!placed) placed = pads(ctx, registry, r[0], r[1], r[2], r[3], open, townNorth);
                if (placed) laid++;
                for (int y = r[1] - 1; y <= r[3] + 1; y++) {
                    for (int x = r[0] - 1; x <= r[2] + 1; x++) if (x >= 0 && y >= 0 && x < w && y < h) taken.set(y * w + x);
                }
            }
        }
        ctx.put(BspKeys.WORKED_FIELDS, kept);
        if (fire != null) bareFire(ctx, southOf, fire, hull, trench, aprons);
    }

    /**
     * The landing's field of fire south of {@code southOf}, where nothing stands, read as bare ground rather than an
     * abandoned farm: the farm tracks across it, the yards' worn tracks among them, are the planet's own ground again.
     * The road graph keeps them; only their drawing goes. Streets, paving and the hull's ground keep their own.
     */
    private static void bareFire(GenContext ctx, int southOf, FieldOfFire fire, BitSet hull, BitSet trench,
                                 BitSet aprons) {
        int w = ctx.width;
        CellTopology topology = ctx.topology;
        SurfacePalette palette = topology.groundPalette();
        GroundKind wild = palette == null ? GroundKind.DIRT : palette.inlandGround();
        if (wild == GroundKind.DIRT) return;
        String block = CrashSiteStage.block(wild);
        for (int y = 0; y < Math.min(ctx.height, southOf); y++) {
            for (int x = 0; x < w; x++) {
                int k = y * w + x;
                if (!fire.contains(x, y) || hull.get(k) || trench.get(k) || aprons.get(k) || topology.isWall(x, y)
                        || topology.getBuildingId(x, y) != 0 || topology.getGroundKind(x, y) != GroundKind.DIRT) continue;
                topology.setGroundKind(x, y, wild);
                topology.setSurface(x, y, block == null ? 0 : topology.surfaceIndex(block));
                topology.setNatureOverlayIndex(x, y, -1);
            }
        }
    }

    /**
     * The field cleared to the planet's own ground, as a field the hull came down on is, and the tracks the road graph
     * runs across it worn to trodden earth, so the yard's service tracks read as the tracks between the fields do.
     */
    private static void clear(GenContext ctx, RuralLayout.Plot plot, BitSet hull, BitSet trench, BitSet aprons,
                              boolean[][] roads) {
        int w = ctx.width;
        SurfacePalette palette = ctx.topology.groundPalette();
        GroundKind wild = palette == null ? GroundKind.DIRT : palette.inlandGround();
        String block = CrashSiteStage.block(wild);
        CellTopology topology = ctx.topology;
        for (int y = Math.max(0, plot.y0()); y <= Math.min(ctx.height - 1, plot.y1()); y++) {
            for (int x = Math.max(0, plot.x0()); x <= Math.min(w - 1, plot.x1()); x++) {
                int k = y * w + x;
                if (hull.get(k) || trench.get(k) || aprons.get(k) || topology.isWall(x, y)
                        || topology.getBuildingId(x, y) != 0) continue;
                GroundKind kind = topology.getGroundKind(x, y);
                if (kind != GroundKind.GRASS && kind != GroundKind.DIRT && kind != GroundKind.SAND
                        && kind != GroundKind.SNOW && kind != GroundKind.STONE) continue;
                topology.setNatureOverlayIndex(x, y, -1);
                if (roads != null && roads[x][y]) {
                    topology.setGroundKind(x, y, GroundKind.DIRT);
                    topology.setSurface(x, y, topology.surfaceIndex(CrashSiteStage.TRODDEN));
                    continue;
                }
                topology.setGroundKind(x, y, wild);
                if (block != null) topology.setSurface(x, y, topology.surfaceIndex(block));
            }
        }
    }

    /**
     * A tank farm on the field: its bunds, its loading lane toward the town with the rack at the end nearest the map's
     * middle under its rack, and the product main along its back headland and down its far end. False when not even one bund fits.
     */
    private static boolean tanks(GenContext ctx, RefineryGround.Stamp stamp, int w, int x0, int y0, int x1, int y1,
                                 IntPredicate open, boolean townNorth) {
        TankFarm.Plan farm = TankFarm.plan(w, x0, y0, x1, y1, open, townNorth ? ctx.height : -1, w / 2, LANE);
        if (farm == null) return false;
        stamp.farm(farm, false);
        // The loading rack along the lane's bund side over its bay; the lane stays open apron for the bowsers.
        int[] bay = farm.bay();
        int rackY = farm.laneDy() > 0 ? bay[1] : bay[3];
        StreetDressing.pipeRun(ctx.grid, ctx.topology, ctx.doodads, bay[0], rackY, bay[2], rackY, PIPE_BAY);
        int[] e = farm.paved();
        // The main runs along the headland behind the bunds, the side away from the lane, and down the end away from
        // the rack: two runs, each on one axis, so the farm reads as fed from the port.
        int backY = farm.laneDy() > 0 ? e[1] - 1 : e[3] + 1;
        int farX = farm.bay()[0] <= e[0] ? e[2] + 1 : e[0] - 1;
        pipe(ctx, open, e[0], backY, e[2], backY);
        pipe(ctx, open, farX, Math.min(backY, e[1]), farX, Math.max(backY, e[3]));
        return true;
    }

    /**
     * Landing pads on the field: an apron paved across it, the pads in a grid with a painted ring each, a store, a
     * bowser's tank or a pump set down each gap between them, and the fuel line along the apron's back.
     */
    static boolean pads(GenContext ctx, TileRegistry registry, int x0, int y0, int x1, int y1,
                        IntPredicate open, boolean townNorth) {
        return pads(ctx, registry, x0, y0, x1, y1, open, townNorth, 2);
    }

    /** As above, laid only where at least {@code least} pads fit: a town's pad court may be one pad. */
    static boolean pads(GenContext ctx, TileRegistry registry, int x0, int y0, int x1, int y1,
                        IntPredicate open, boolean townNorth, int least) {
        int w = ctx.width;
        int[] r = TankFarm.largestClear(w, x0, y0, x1, y1, open);
        if (r == null) return false;
        int across = (r[2] - r[0] + 1 + PAD_GAP) / (PAD + PAD_GAP), deep = (r[3] - r[1] + 1 + PAD_GAP) / (PAD + PAD_GAP);
        // One pad alone is no landing field, but in a town's lot it is a pad court.
        if (across * deep < Math.max(1, least)) return false;
        int spanX = across * PAD + (across - 1) * PAD_GAP, spanY = deep * PAD + (deep - 1) * PAD_GAP;
        int ox = r[0] + (r[2] - r[0] + 1 - spanX) / 2, oy = r[1] + (r[3] - r[1] + 1 - spanY) / 2;
        for (int y = r[1]; y <= r[3]; y++) for (int x = r[0]; x <= r[2]; x++) pave(ctx, x, y, APRON);
        DoodadDef edge = registry.doodad(PAD_EDGE);
        for (int j = 0; j < deep; j++) {
            for (int i = 0; i < across; i++) {
                int px = ox + i * (PAD + PAD_GAP), py = oy + j * (PAD + PAD_GAP);
                padCells(ctx, px, py);
                for (int y = py; y < py + PAD; y++) {
                    for (int x = px; x < px + PAD; x++) {
                        boolean ring = x == px || y == py || x == px + PAD - 1 || y == py + PAD - 1;
                        boolean cross = (x == px + PAD / 2 || y == py + PAD / 2)
                                && Math.abs(x - px - PAD / 2) <= 2 && Math.abs(y - py - PAD / 2) <= 2;
                        if (!ring && !cross) continue;
                        ctx.topology.setGroundKind(x, y, MARKED);
                        if (edge != null) ctx.doodads.add(new Doodad(x, y, edge));
                    }
                }
            }
        }
        // Kit down each gap between two pads, cycled so no gap is all one thing; on the apron, never on a pad.
        int n = 0;
        for (int i = 0; i + 1 < across; i++) {
            int gx = ox + i * (PAD + PAD_GAP) + PAD + (PAD_GAP - 3) / 2;
            for (int y = r[1] + 1; y + 3 <= r[3]; y += KIT_STEP) kit(ctx, registry, PAD_KIT[n++ % PAD_KIT.length], gx, y);
        }
        for (int j = 0; j + 1 < deep; j++) {
            int gy = oy + j * (PAD + PAD_GAP) + PAD + (PAD_GAP - 3) / 2;
            for (int x = r[0] + 1; x + 3 <= r[2]; x += KIT_STEP) kit(ctx, registry, PAD_KIT[n++ % PAD_KIT.length], x, gy);
        }
        int backY = townNorth ? r[1] - 1 : r[3] + 1;
        pipe(ctx, open, r[0], backY, r[2], backY);
        return true;
    }

    /** Marks a pad's cells, from its corner, in the map's pads ({@link HullKeys#PADS}). */
    private static void padCells(GenContext ctx, int px, int py) {
        BitSet pads = ctx.get(HullKeys.PADS);
        if (pads == null) {
            pads = new BitSet(ctx.width * ctx.height);
            ctx.put(HullKeys.PADS, pads);
        }
        for (int y = py; y < py + PAD; y++) for (int x = px; x < px + PAD; x++) pads.set(y * ctx.width + x);
    }

    /** One piece of kit with its corner at {@code (x, y)}, where every cell under it is open apron. */
    private static void kit(GenContext ctx, TileRegistry registry, String id, int x, int y) {
        DoodadDef def = registry.doodad(id);
        if (def == null) return;
        int fx = Math.max(1, def.footprintCellsX), fy = Math.max(1, def.footprintCellsY);
        for (int yy = y; yy < y + fy; yy++) {
            for (int xx = x; xx < x + fx; xx++) {
                if (xx < 0 || yy < 0 || xx >= ctx.width || yy >= ctx.height) return;
                if (ctx.topology.getGroundKind(xx, yy) != APRON || ctx.topology.isFixture(xx, yy)) return;
            }
        }
        ctx.doodads.add(new Doodad(x, y, def));
    }

    /** A pipe run from one cell to the other along one axis, laid only where every cell of it is open. */
    private static void pipe(GenContext ctx, IntPredicate open, int x0, int y0, int x1, int y1) {
        int w = ctx.width, h = ctx.height;
        if (x0 < 0 || y0 < 0 || x1 >= w || y1 >= h) return;
        if (Math.max(x1 - x0, y1 - y0) < PIPE_BAY) return;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (!open.test(y * w + x)) return;
        StreetDressing.pipeRun(ctx.grid, ctx.topology, ctx.doodads, x0, y0, x1, y1, PIPE_BAY);
    }

    /** Paves an open cell as made ground of {@code kind}. */
    private static void pave(GenContext ctx, int x, int y, GroundKind kind) {
        ctx.topology.setGroundKind(x, y, kind);
        ctx.topology.setSurface(x, y, 0);
        ctx.topology.setNatureOverlayIndex(x, y, -1);
        ctx.markMadeGround(x, y);
    }
}
