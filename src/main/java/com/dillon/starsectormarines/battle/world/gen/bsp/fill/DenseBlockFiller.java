package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DistrictTheme;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * {@link BlockFiller} for {@link BlockKind#DENSE_BLOCK} leaves — dense urban
 * infill. Qualifying large lots become a mixed-use pair of elongated row
 * buildings flanking a cover-shaped service alley. Smaller lots retain the
 * established 2×2 compact-shell pattern so the kind remains visible on the
 * common small BSP leaves.
 *
 * <p>Visual + gameplay shape:
 * <ul>
 *   <li>Cross-shaped alley at the leaf's midpoint, 1 cell wide each axis.
 *       Alley cells are walkable {@link GroundKind#STREET} so they read as
 *       narrow back-alleys connected to the surrounding road network.</li>
 *   <li>Four sub-buildings in the leaf's quadrants. Each has its own
 *       perimeter wall and 1 doorway picked randomly from its four sides
 *       (so some doors face the alley, some face the outer road frame).</li>
 *   <li>1-2 doodads per sub-building from the {@code "RESIDENTIAL"} doodad pool.
 *       Tighter density than a single residential building because rooms
 *       are smaller.</li>
 * </ul>
 *
 * <p>Connectivity invariant: the alley reaches the leaf's outer perimeter
 * at four points (top, bottom, left, right midpoints), so it always
 * connects to the surrounding road frame. Each sub-building's doorway
 * opens onto either the alley or the road frame — both walkable — so every
 * interior cell is reachable.
 *
 * <p>Small-leaf fallback: leaves below {@link #MIN_DENSE_DIM} on either axis
 * can't fit a 2×2 with alley + sub-building minima. Those cells delegate to
 * {@link BuildingShellCore#carve} for a single residential-style shell —
 * preserves the BlockKind assignment without breaking on undersized leaves.
 */
public final class DenseBlockFiller implements BlockFiller {

    /** Large-lot threshold for two useful row interiors plus the service alley. */
    static final int TACTICAL_MIN_LONG_DIM = 13;
    static final int TACTICAL_MIN_SHORT_DIM = 10;
    /** Three total alley cells leave two open cells beside each one-cell cover pocket. */
    static final int TACTICAL_ALLEY_WIDTH = 3;
    static final int TACTICAL_CLEAR_WIDTH = 2;

    /**
     * Minimum leaf dim (per axis) to qualify for 2×2 subdivision. Geometric
     * minimum is 3 + 1 (cross alley) + 3 = 7: each sub-building is a 3×3
     * wall ring around a 1×1 interior cell, separated by 1-cell alleys.
     *
     * <p>Was previously 9 — that left bigger interiors but disqualified ~91%
     * of {@code DENSE_BLOCK} labels (instrumented across 6 seeds), which
     * silently fell back to a single residential shell. 7 keeps the dense
     * geometry legal and lifts eligible-leaf share from ~17% to ~46%, hitting
     * the ~2-visible-dense-blocks-per-map target.
     */
    private static final int MIN_DENSE_DIM = 7;

    /** Per-sub-building doodad chance. Higher than single residential because rooms are smaller and need to read as occupied. */
    private static final float DOODAD_CHANCE = 0.75f;
    private static final int DOODAD_MAX_PER_SUB = 2;

    /** Fallback config when the leaf is too small to subdivide — single residential shell. */
    private static final BuildingShellCore.BuildingConfig FALLBACK_CONFIG = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR,
            "RESIDENTIAL",
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME,
            BuildingKind.RESIDENTIAL);

    static final BuildingShellCore.BuildingConfig TENEMENT_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR,
                    "RESIDENTIAL",
                    PointOfInterest.Kind.RESIDENTIAL,
                    BuildingLayouts.LayoutRecipe.DENSE_TENEMENT,
                    BuildingKind.RESIDENTIAL,
                    null,
                    DenseRowPartitionStrategy.TENEMENT);

    static final BuildingShellCore.BuildingConfig MARKET_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.TILE,
                    "COMMERCIAL",
                    PointOfInterest.Kind.RESIDENTIAL,
                    BuildingLayouts.LayoutRecipe.DENSE_MARKET,
                    BuildingKind.COMMERCIAL,
                    null,
                    DenseRowPartitionStrategy.MARKET);

    @Override
    public BlockKind kind() { return BlockKind.DENSE_BLOCK; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        List<PointOfInterest> pois = ctx.pois;
        List<Doodad> doodads = ctx.doodads;
        Random rng = ctx.rng;
        int w = leaf.width();
        int h = leaf.height();
        if (qualifiesForTacticalRows(leaf)) {
            fillTacticalRows(leaf, ctx);
            return;
        }
        if (w < MIN_DENSE_DIM || h < MIN_DENSE_DIM) {
            PointOfInterest poi = BuildingShellCore.carve(leaf, grid, topology, doodads, rng, FALLBACK_CONFIG);
            if (poi != null) pois.add(poi);
            return;
        }

        int midX = leaf.left + w / 2;
        int midY = leaf.top  + h / 2;

        // Carve the cross alley first so the sub-building carves can simply
        // skip the alley row + column. Alley = walkable STREET (narrow road).
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            grid.setWalkableFloor(midX, y);
            topology.setGroundKind(midX, y, GroundKind.STREET);
        }
        for (int x = leaf.left; x <= leaf.right; x++) {
            grid.setWalkableFloor(x, midY);
            topology.setGroundKind(x, midY, GroundKind.STREET);
        }

        // Quadrant rects (inclusive). Each is a sub-building.
        int[][] quads = {
                { leaf.left,  leaf.top,    midX - 1,    midY - 1 }, // top-left   = quad 0
                { midX + 1,   leaf.top,    leaf.right,  midY - 1 }, // top-right  = quad 1
                { leaf.left,  midY + 1,    midX - 1,    leaf.bottom }, // bot-left  = quad 2
                { midX + 1,   midY + 1,    leaf.right,  leaf.bottom }, // bot-right = quad 3
        };

        // Carve sub-buildings. Track the first one's interior cell so we have
        // a real INDOOR anchor — the alley center is STREET, fine for "stand
        // here to interact" but not for "plant a charge inside the building".
        int[] interiorAnchor = null;
        for (int[] q : quads) {
            int[] sub = carveSubBuilding(q[0], q[1], q[2], q[3], grid, topology, doodads, rng);
            if (interiorAnchor == null && sub != null) interiorAnchor = sub;
        }

        // One POI for the whole dense block. Exterior anchor at the alley
        // center (always walkable, always at the geometric middle). Interior
        // anchor inside one of the carved sub-buildings; falls back to the
        // alley center if every sub-building was too small to enclose.
        int interiorX = (interiorAnchor != null) ? interiorAnchor[0] : midX;
        int interiorY = (interiorAnchor != null) ? interiorAnchor[1] : midY;
        pois.add(new PointOfInterest(
                PointOfInterest.Kind.RESIDENTIAL,
                leaf.left, leaf.top, leaf.right, leaf.bottom,
                midX, midY, interiorX, interiorY));
    }

    static boolean qualifiesForTacticalRows(BlockLeaf leaf) {
        return Math.max(leaf.width(), leaf.height()) >= TACTICAL_MIN_LONG_DIM
                && Math.min(leaf.width(), leaf.height()) >= TACTICAL_MIN_SHORT_DIM;
    }

    private static void fillTacticalRows(BlockLeaf leaf, GenContext ctx) {
        boolean verticalAlley = leaf.width() >= leaf.height();
        boolean tenementFirst = ctx.rng.nextBoolean();
        if (verticalAlley) {
            int alleyLeft = leaf.left + (leaf.width() - TACTICAL_ALLEY_WIDTH) / 2;
            int alleyRight = alleyLeft + TACTICAL_ALLEY_WIDTH - 1;
            paintAlley(ctx, alleyLeft, leaf.top, alleyRight, leaf.bottom);
            carveRow(new BlockLeaf(leaf.left, leaf.top, alleyLeft - 1, leaf.bottom, false),
                    BuildingPlacement.Side.RIGHT,
                    tenementFirst ? TENEMENT_CONFIG : MARKET_CONFIG, ctx);
            carveRow(new BlockLeaf(alleyRight + 1, leaf.top, leaf.right, leaf.bottom, false),
                    BuildingPlacement.Side.LEFT,
                    tenementFirst ? MARKET_CONFIG : TENEMENT_CONFIG, ctx);
            furnishAlley(ctx, alleyLeft, leaf.top, alleyRight, leaf.bottom, true);
            return;
        }

        int alleyTop = leaf.top + (leaf.height() - TACTICAL_ALLEY_WIDTH) / 2;
        int alleyBottom = alleyTop + TACTICAL_ALLEY_WIDTH - 1;
        paintAlley(ctx, leaf.left, alleyTop, leaf.right, alleyBottom);
        carveRow(new BlockLeaf(leaf.left, leaf.top, leaf.right, alleyTop - 1, false),
                BuildingPlacement.Side.BOTTOM,
                tenementFirst ? TENEMENT_CONFIG : MARKET_CONFIG, ctx);
        carveRow(new BlockLeaf(leaf.left, alleyBottom + 1, leaf.right, leaf.bottom, false),
                BuildingPlacement.Side.TOP,
                tenementFirst ? MARKET_CONFIG : TENEMENT_CONFIG, ctx);
        furnishAlley(ctx, leaf.left, alleyTop, leaf.right, alleyBottom, false);
    }

    private static void carveRow(BlockLeaf row,
                                 BuildingPlacement.Side frontage,
                                 BuildingShellCore.BuildingConfig config,
                                 GenContext ctx) {
        PointOfInterest poi = BuildingShellCore.carve(
                row, ctx.grid, ctx.topology, ctx.doodads, ctx.rng, config,
                new BuildingPlacement(frontage, true));
        if (poi != null) ctx.pois.add(poi);
    }

    private static void paintAlley(GenContext ctx,
                                   int left, int top, int right, int bottom) {
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setGroundKind(x, y, GroundKind.STREET);
                ctx.topology.setWall(x, y, false);
                ctx.topology.setFixture(x, y, false);
            }
        }
    }

    /**
     * Staggers at most two low cover fixtures against alternating alley edges.
     * Each fixture occupies one of three cross-alley cells, leaving a full
     * two-cell lane at that station; doorway-adjacent candidates are skipped.
     */
    private static void furnishAlley(GenContext ctx,
                                     int left, int top, int right, int bottom,
                                     boolean vertical) {
        DoodadDef[] props = {
                TileRegistry.installed().doodad("doodad.box"),
                TileRegistry.installed().doodad("doodad.industrial-cable-reel")
        };
        int longMin = vertical ? top + 2 : left + 2;
        int longMax = vertical ? bottom - 2 : right - 2;
        if (longMax < longMin) return;

        int[] targets = {
                longMin + (longMax - longMin) / 3,
                longMin + 2 * (longMax - longMin) / 3
        };
        for (int i = 0; i < targets.length; i++) {
            int fixed = i == 0 ? (vertical ? left : top) : (vertical ? right : bottom);
            int[] cell = nearestClearAlleyCell(
                    ctx, left, top, right, bottom, vertical, fixed, targets[i]);
            if (cell == null) continue;
            stampAlleyCover(ctx, cell[0], cell[1], props[i]);
        }
    }

    private static int[] nearestClearAlleyCell(GenContext ctx,
                                                int left, int top, int right, int bottom,
                                                boolean vertical, int fixed, int target) {
        int min = vertical ? top + 2 : left + 2;
        int max = vertical ? bottom - 2 : right - 2;
        for (int distance = 0; distance <= max - min; distance++) {
            for (int sign : new int[]{-1, 1}) {
                if (distance == 0 && sign == 1) continue;
                int along = target + distance * sign;
                if (along < min || along > max) continue;
                int x = vertical ? fixed : along;
                int y = vertical ? along : fixed;
                if (!nearDoorway(ctx.grid, x, y)
                        && !occupied(ctx.doodads, x, y)
                        && !alleyStationOccupied(ctx, left, top, right, bottom,
                        vertical, along)) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean alleyStationOccupied(GenContext ctx,
                                                 int left, int top, int right, int bottom,
                                                 boolean vertical, int along) {
        int crossMin = vertical ? left : top;
        int crossMax = vertical ? right : bottom;
        for (int cross = crossMin; cross <= crossMax; cross++) {
            int x = vertical ? cross : along;
            int y = vertical ? along : cross;
            if (ctx.topology.isFixture(x, y) || occupied(ctx.doodads, x, y)) return true;
        }
        return false;
    }

    private static boolean nearDoorway(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(x + dx, y + dy) && grid.isDoorway(x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean occupied(List<Doodad> doodads, int x, int y) {
        for (Doodad doodad : doodads) {
            if (doodad.occupiesCell(x, y)) return true;
        }
        return false;
    }

    private static void stampAlleyCover(GenContext ctx, int x, int y, DoodadDef prop) {
        ctx.grid.setWalkable(x, y, false);
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setWall(x, y, false);
        ctx.topology.setFixture(x, y, true);
        ctx.doodads.add(new Doodad(x, y, prop));
    }

    /**
     * Carves one sub-building inside the dense block: perimeter walls,
     * INDOOR floor interior, one randomly-placed doorway. The sub-building's
     * perimeter is the rect {@code (l, t)..(r, b)} inclusive — that's the
     * shell. The cell {@code (l, t)} etc. are wall cells, not interior.
     *
     * <p>Skip if the rect is degenerate (need at least 3 in each axis to
     * have a 1×1 walkable interior). That can happen when the leaf is just
     * above {@link #MIN_DENSE_DIM} and a quadrant comes out narrow.
     */
    /**
     * @return a walkable interior cell inside the carved sub-building (the
     *         center if walkable, else a scanned alternative), or {@code null}
     *         if the rect was too small to carve.
     */
    private static int[] carveSubBuilding(int l, int t, int r, int b,
                                          NavigationGrid grid, CellTopology topology,
                                          List<Doodad> doodads, Random rng) {
        if (r - l < 2 || b - t < 2) return null;

        // Perimeter walls — non-walkable. Interior cells stay walkable
        // (they were initialized by the orchestrator's pre-pass).
        for (int x = l; x <= r; x++) {
            grid.setWalkable(x, t, false);
            grid.setWalkable(x, b, false);
            topology.setGroundKind(x, t, GroundKind.INDOOR);
            topology.setGroundKind(x, b, GroundKind.INDOOR);
        }
        for (int y = t + 1; y <= b - 1; y++) {
            grid.setWalkable(l, y, false);
            grid.setWalkable(r, y, false);
            topology.setGroundKind(l, y, GroundKind.INDOOR);
            topology.setGroundKind(r, y, GroundKind.INDOOR);
        }
        // Interior — already walkable, set ground to INDOOR explicitly so the
        // pre-pass STREET doesn't leak through if the orchestrator's defaults
        // change later.
        for (int y = t + 1; y <= b - 1; y++) {
            for (int x = l + 1; x <= r - 1; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
            }
        }

        // Punch one doorway on a random side. Corners are excluded — a corner
        // doorway would face diagonally onto nothing useful.
        int side = rng.nextInt(4); // 0=top, 1=bottom, 2=left, 3=right
        int doorX, doorY;
        switch (side) {
            case 0:  doorX = l + 1 + rng.nextInt(r - l - 1); doorY = t; break;
            case 1:  doorX = l + 1 + rng.nextInt(r - l - 1); doorY = b; break;
            case 2:  doorX = l;                              doorY = t + 1 + rng.nextInt(b - t - 1); break;
            default: doorX = r;                              doorY = t + 1 + rng.nextInt(b - t - 1); break;
        }
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        topology.setGroundKind(doorX, doorY, GroundKind.INDOOR);

        // Doodads — keep them sparse; sub-buildings are tiny and clutter ruins
        // the close-quarters feel.
        if (rng.nextFloat() < DOODAD_CHANCE) {
            int interiorW = (r - 1) - (l + 1) + 1;
            int interiorH = (b - 1) - (t + 1) + 1;
            int interiorCells = interiorW * interiorH;
            int count = Math.min(DOODAD_MAX_PER_SUB, Math.max(0, interiorCells - 1));
            for (int i = 0; i < count; i++) {
                if (i > 0 && rng.nextFloat() < 0.4f) break; // taper
                int dx = l + 1 + rng.nextInt(interiorW);
                int dy = t + 1 + rng.nextInt(interiorH);
                if (dx == doorX && dy == doorY) continue;
                if (!grid.isWalkable(dx, dy)) continue;
                List<DoodadDef> pool = GenMappingRegistry.installed().doodadPool(DistrictTheme.RESIDENTIAL);
                doodads.add(new Doodad(dx, dy, pool.get(rng.nextInt(pool.size()))));
            }
        }

        // Interior anchor: prefer the geometric center, but the partition
        // wall / random doorway may collide with it. Scan walkable non-
        // doorway interior cells and pick the one closest to center.
        int ix = (l + r) / 2;
        int iy = (t + b) / 2;
        if (grid.isWalkable(ix, iy) && !grid.isDoorway(ix, iy)) {
            return new int[]{ix, iy};
        }
        List<int[]> interior = new ArrayList<>();
        for (int y = t + 1; y <= b - 1; y++) {
            for (int x = l + 1; x <= r - 1; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (grid.isDoorway(x, y)) continue;
                interior.add(new int[]{x, y});
            }
        }
        if (interior.isEmpty()) return null;
        int[] best = interior.get(0);
        int bestDist = Math.abs(best[0] - ix) + Math.abs(best[1] - iy);
        for (int[] cell : interior) {
            int d = Math.abs(cell[0] - ix) + Math.abs(cell[1] - iy);
            if (d < bestDist) { best = cell; bestDist = d; }
        }
        return best;
    }
}
