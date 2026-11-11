package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * The ground the hull flattened coming in ({@code fallen-hull-nouns.md}, The ground): a band round the trench and every
 * section that thins outward. Within {@link #FLATTEN} cells of the hull or its trench everything built is wrecked
 * (walls down, roofs fallen in or burnt out), so nothing intact stands against the hull or the plough; out to
 * {@link #BATTER} the blocks are battered, and wrecked in patches that grow rarer with distance; beyond it only the
 * shower's own strikes ({@link DebrisShower}) fall.
 *
 * <p>It is initial damage, applied when the battle is assembled, as runs of cells along each map row, so it never
 * reaches a cell it was not meant to: never the hull, its trench, lips or aprons, a standing piece of debris or a
 * fixture other than a tree or rock, the landing's field of fire, or a piece the mission's layout needs. A named place
 * or a layout's other pieces are spared only clear of {@link #FLATTEN}: nothing intact stands against the hull or
 * the plough. Damage only takes walls and roofs down and fells trees, so every way, stake point and haul road stays
 * as open as it was. The ground under it is ploughed when the map is generated ({@link #plough}): soft ground burnt
 * to bare earth and rubble, and roads ending in rubble short of the hull.
 */
final class ImpactZone {

    /** Wrecked outright within this many cells of the hull or its trench; battered out to the second. UNTUNED. */
    static final int FLATTEN = 20, BATTER = 40;
    /** Ground kept clear round a mission's buildings and structures, and off the landing's field of fire. */
    static final int CLEAR = 5;
    /**
     * Open ground (soft ground and frozen water) is ploughed out to {@link #BATTER}, its outermost cells this deep in
     * patches, so nothing under the band is clean and its edge is ragged. UNTUNED.
     */
    static final int OPEN_RAGGED = 8;
    /** The share of wrecked patches of open ground out in the battered ground left uncratered. UNTUNED. */
    static final float OPEN_SCAR = 0.3f;
    /** A road is rubble this many cells from the hull or its trench, and broken in patches out to the second. */
    static final int ROAD_BREAK = 8, ROAD_RAGGED = 14;
    /** A patch of battered ground wrecked or not together, cells on a side. */
    static final int PATCH = 5;
    /** Strokes in one damage layer, and the most layers the band may take. */
    static final int LAYER_STROKES = 512, MAX_LAYERS = 8;
    /** The band's damage layers' names, numbered from 1. */
    static final String LAYER = "hull-impact-";

    private ImpactZone() { }

    /** A row run of cells to damage, inclusive. */
    record Run(DamagePlan.Mode mode, int y, int x0, int x1) { }

    static List<DamagePlan.Layer> layers(GenContext ctx, BitSet hull, BitSet trench, BitSet lips, BitSet aprons,
                                         FieldOfFire field) {
        int w = ctx.width, h = ctx.height;
        BitSet source = new BitSet(w * h);
        source.or(hull);
        source.or(trench);
        source.or(lips);
        int[] dist = distance(source, w, h);
        boolean[] kept = kept(ctx, dist, hull, trench, lips, aprons, field);
        List<Run> runs = runs(ctx.grid, ctx.topology, dist, kept, w, h, ctx.seed);
        return layers(runs);
    }

    /**
     * The ground itself, on the map as generated: open ground under the band is burnt and ploughed to bare earth and
     * rubble out to {@link #BATTER}, its plants, loose stones and the wild ground's formations gone and a frozen pond
     * buried under it; a road ends in rubble short of the hull. Kept ground, the hull, its trench and the aprons are left
     * as they are.
     */
    static void plough(GenContext ctx, BitSet hull, BitSet trench, BitSet lips, BitSet aprons, FieldOfFire field) {
        plough(ctx, hull, trench, lips, aprons, field, BATTER);
    }

    /** As above, open ground ploughed out to {@code reach} cells ({@link HullSitePlan#plough}), at least the band's. */
    static void plough(GenContext ctx, BitSet hull, BitSet trench, BitSet lips, BitSet aprons, FieldOfFire field,
                       int reach) {
        int w = ctx.width, h = ctx.height, far = Math.max(BATTER, reach);
        BitSet source = new BitSet(w * h);
        source.or(hull);
        source.or(trench);
        source.or(lips);
        int[] dist = distance(source, w, h);
        boolean[] kept = kept(ctx, dist, hull, trench, lips, aprons, field);
        CellTopology topology = ctx.topology;
        BitSet band = new BitSet(w * h);
        for (int k = 0; k < w * h; k++) if (!kept[k] && dist[k] <= far * 3) band.set(k);
        MineCountry.clearFormations(ctx, band);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int k = y * w + x;
                if (!band.get(k)) continue;
                GroundKind kind = topology.getGroundKind(x, y);
                GroundKind to = ploughed(kind, dist[k], ctx.seed, x, y, far);
                if (to == null) continue;
                if (topology.isWall(x, y) || topology.isFixture(x, y) && !ctx.grid.hasTag(x, y, CellTag.NATURAL_SOLID)) {
                    continue;
                }
                if (kind == GroundKind.WATER) {
                    // The pond is filled: ground a figure stands on, no longer water to shoot across.
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.grid.setTag(x, y, CellTag.SEE_THROUGH, false);
                    ctx.grid.setTag(x, y, CellTag.NO_EDGE_COVER, false);
                    topology.setTag(x, y, CellTopology.Tag.SHALLOW_WATER, false);
                }
                topology.setGroundKind(x, y, to);
                String block = CrashSiteStage.block(to);
                if (block != null) topology.setSurface(x, y, topology.surfaceIndex(block));
                topology.setNatureOverlayIndex(x, y, -1);
            }
        }
    }

    /** What the hull's coming in made of a cell's ground, or {@code null} for unchanged. */
    static GroundKind ploughed(GroundKind kind, int distanceThirds, long seed, int x, int y) {
        return ploughed(kind, distanceThirds, seed, x, y, BATTER);
    }

    /** As above, open ground ploughed out to {@code reach} cells. */
    static GroundKind ploughed(GroundKind kind, int distanceThirds, long seed, int x, int y, int reach) {
        float d = distanceThirds / 3f;
        if (kind == GroundKind.STREET || kind == GroundKind.SIDEWALK) {
            if (d <= ROAD_BREAK) return GroundKind.RUBBLE;
            return d <= ROAD_RAGGED && patch(seed ^ 0x40ADL, x * 2, y * 2) < 0.5f ? GroundKind.RUBBLE : null;
        }
        if (!soft(kind) && kind != GroundKind.WATER) return null;
        if (d > reach - OPEN_RAGGED * patch(seed ^ 0x5CA2L, x, y)) return null;
        return CrashSiteStage.trenchGround(seed, x, y);
    }

    /** Chamfer (3, 4) distance from the source cells, in thirds of a cell. */
    static int[] distance(BitSet source, int w, int h) {
        int far = Integer.MAX_VALUE / 2;
        int[] d = new int[w * h];
        Arrays.fill(d, far);
        for (int k = source.nextSetBit(0); k >= 0; k = source.nextSetBit(k + 1)) d[k] = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int k = y * w + x, v = d[k];
                if (x > 0) v = Math.min(v, d[k - 1] + 3);
                if (y > 0) {
                    v = Math.min(v, d[k - w] + 3);
                    if (x > 0) v = Math.min(v, d[k - w - 1] + 4);
                    if (x < w - 1) v = Math.min(v, d[k - w + 1] + 4);
                }
                d[k] = v;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int k = y * w + x, v = d[k];
                if (x < w - 1) v = Math.min(v, d[k + 1] + 3);
                if (y < h - 1) {
                    v = Math.min(v, d[k + w] + 3);
                    if (x < w - 1) v = Math.min(v, d[k + w + 1] + 4);
                    if (x > 0) v = Math.min(v, d[k + w - 1] + 4);
                }
                d[k] = v;
            }
        }
        return d;
    }

    /** The cells the band never touches. */
    private static boolean[] kept(GenContext ctx, int[] dist, BitSet hull, BitSet trench, BitSet lips, BitSet aprons,
                                  FieldOfFire field) {
        int w = ctx.width, h = ctx.height;
        boolean[] kept = new boolean[w * h];
        for (BitSet cells : new BitSet[]{hull, trench, lips, aprons}) {
            for (int k = cells.nextSetBit(0); k >= 0; k = cells.nextSetBit(k + 1)) kept[k] = true;
        }
        if (field != null) {
            long r = field.radius() + CLEAR;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    long dx = x - field.centerX(), dy = y - field.centerY();
                    if (dx * dx + dy * dy <= r * r) kept[y * w + x] = true;
                }
            }
        }
        // A place the town named is kept, but not against the hull: nothing intact stands where it ploughed in.
        boolean[] places = new boolean[w * h];
        for (PointOfInterest poi : ctx.pois) {
            if (poi.kind == PointOfInterest.Kind.RESIDENTIAL || poi.kind == PointOfInterest.Kind.DEPOT) continue;
            box(places, w, h, Math.min(poi.left, poi.right), Math.min(poi.top, poi.bottom),
                    Math.max(poi.left, poi.right), Math.max(poi.top, poi.bottom));
        }
        LaidLayout laid = ctx.get(BspKeys.MISSION_LAYOUT_LAID);
        if (laid != null) {
            for (Laid piece : laid.pieces()) {
                if (piece.cells() == null || piece.cells().isEmpty()) continue;
                int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
                boolean onHull = false;
                for (int[] c : piece.cells()) {
                    x0 = Math.min(x0, c[0]);
                    y0 = Math.min(y0, c[1]);
                    x1 = Math.max(x1, c[0]);
                    y1 = Math.max(y1, c[1]);
                    if (c[0] >= 0 && c[1] >= 0 && c[0] < w && c[1] < h && hull.get(c[1] * w + c[0])) onHull = true;
                }
                // The crash's own piece is the hull; the band is what surrounds it. A piece the mission needs is kept
                // whole; any other (the dressing of a place the hull came down on) only clear of the hull.
                if (onHull) continue;
                box(piece.required() ? kept : places, w, h, x0, y0, x1, y1);
            }
        }
        for (int k = 0; k < w * h; k++) if (places[k] && dist[k] > FLATTEN * 3) kept[k] = true;
        return kept;
    }

    private static void box(boolean[] kept, int w, int h, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0 - CLEAR); y <= Math.min(h - 1, y1 + CLEAR); y++) {
            for (int x = Math.max(0, x0 - CLEAR); x <= Math.min(w - 1, x1 + CLEAR); x++) kept[y * w + x] = true;
        }
    }

    /** What the band does to a cell: wreck it, batter it, or leave it ({@code null}). */
    static DamagePlan.Mode mode(int distanceThirds, long seed, int x, int y) {
        float d = distanceThirds / 3f;
        if (d <= FLATTEN) return DamagePlan.Mode.RUIN;
        if (d > BATTER) return null;
        // Patches are wrecked or battered together; wrecked ones grow rarer outward.
        float wreck = 1f - (d - FLATTEN) / (BATTER - FLATTEN);
        return patch(seed, x, y) < wreck * 0.7f ? DamagePlan.Mode.RUIN : DamagePlan.Mode.BATTERED;
    }

    static boolean built(CellTopology topology, int x, int y) {
        return topology.hasBuildingKindHint(x, y) || topology.getGroundKind(x, y) == GroundKind.INDOOR;
    }

    /** A patch's roll, 0 to 1, the same for every cell in it. */
    static float patch(long seed, int x, int y) {
        long hash = seed ^ ((x / PATCH) * 0x9E3779B97F4A7C15L) ^ ((y / PATCH) * 0xC2B2AE3D27D4EB4FL);
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        return (hash & 0xFFFF) / 65536f;
    }

    /**
     * Whether the band may touch the cell: a building, open ground, a tree or a rock (felled), or a fence or field wall
     * within {@link #FLATTEN}; never a standing piece of hull debris or another fixture.
     */
    static boolean touchable(NavigationGrid grid, CellTopology topology, int x, int y, int distanceThirds) {
        if (topology.isFixture(x, y)) return grid.hasTag(x, y, CellTag.NATURAL_SOLID);
        if (built(topology, x, y) || !topology.isWall(x, y)) return true;
        String surface = topology.getSurfaceId(x, y);
        return distanceThirds <= FLATTEN * 3 && (surface == null || !surface.contains("fallen-hull"));
    }

    /** Whether the cell is soft open ground the hull's coming in burns and ploughs to dirt. */
    static boolean soft(GroundKind kind) {
        return kind == GroundKind.GRASS || kind == GroundKind.DIRT || kind == GroundKind.SAND
                || kind == GroundKind.SNOW || kind == GroundKind.STONE || kind == GroundKind.HARDPAN;
    }

    static List<Run> runs(NavigationGrid grid, CellTopology topology, int[] dist, boolean[] kept, int w, int h,
                          long seed) {
        List<Run> out = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            DamagePlan.Mode open = null;
            int start = 0;
            for (int x = 0; x <= w; x++) {
                DamagePlan.Mode m = null;
                if (x < w) {
                    int k = y * w + x;
                    if (!kept[k] && touchable(grid, topology, x, y, dist[k])) m = mode(dist[k], seed, x, y);
                    // Open ground is burnt and cratered under the hull, and out in the battered ground in patches,
                    // wrecked ones (trees felled) growing rarer outward.
                    if (m != null && !built(topology, x, y) && dist[k] > FLATTEN * 3
                            && (m != DamagePlan.Mode.RUIN || patch(seed ^ 0x5CA2L, x, y) >= OPEN_SCAR)) m = null;
                }
                if (m != open) {
                    if (open != null) out.add(new Run(open, y, start, x - 1));
                    open = m;
                    start = x;
                }
            }
        }
        return out;
    }

    /** The runs as damage layers: one stroke a run, its two ends; battered runs dropped first past the cap. */
    static List<DamagePlan.Layer> layers(List<Run> runs) {
        int cap = LAYER_STROKES * MAX_LAYERS;
        List<Run> keep = new ArrayList<>();
        for (Run r : runs) if (r.mode() == DamagePlan.Mode.RUIN) keep.add(r);
        for (Run r : runs) if (r.mode() != DamagePlan.Mode.RUIN && keep.size() < cap) keep.add(r);
        if (keep.size() > cap) keep = keep.subList(0, cap);
        List<DamagePlan.Layer> out = new ArrayList<>();
        List<DamagePlan.Stroke> strokes = new ArrayList<>();
        for (Run r : keep) {
            strokes.add(new DamagePlan.Stroke(r.mode(), 0.5, List.of(new DamagePlan.Point(r.x0(), r.y()),
                    new DamagePlan.Point(r.x1(), r.y()))));
            if (strokes.size() == LAYER_STROKES) {
                out.add(new DamagePlan.Layer(LAYER + (out.size() + 1), true, strokes));
                strokes = new ArrayList<>();
            }
        }
        if (!strokes.isEmpty()) out.add(new DamagePlan.Layer(LAYER + (out.size() + 1), true, strokes));
        return out;
    }
}
