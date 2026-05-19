package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * {@link BlockFiller} for {@link BlockKind#DENSE_BLOCK} leaves — dense urban
 * infill that subdivides the leaf into a 2×2 grid of small hollow shells
 * separated by a 1-cell cross-shaped alley. The leaf still has the BSP road
 * frame around it; this filler just packs the interior tighter than a single
 * building would.
 *
 * <p>Visual + gameplay shape:
 * <ul>
 *   <li>Cross-shaped alley at the leaf's midpoint, 1 cell wide each axis.
 *       Alley cells are walkable {@link GroundKind#STREET} so they read as
 *       narrow back-alleys connected to the surrounding road network.</li>
 *   <li>Four sub-buildings in the leaf's quadrants. Each has its own
 *       perimeter wall and 1 doorway picked randomly from its four sides
 *       (so some doors face the alley, some face the outer road frame).</li>
 *   <li>1-2 doodads per sub-building from {@link TileManifest#RESIDENTIAL_DOODADS}.
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
            TileManifest.RESIDENTIAL_DOODADS,
            PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.HOME);

    @Override
    public BlockKind kind() { return BlockKind.DENSE_BLOCK; }

    @Override
    public void fill(BlockLeaf leaf,
                     NavigationGrid grid,
                     CellTopology topology,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     Random rng) {
        int w = leaf.width();
        int h = leaf.height();
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
                TileManifest.TileFrame tile = TileManifest.RESIDENTIAL_DOODADS[
                        rng.nextInt(TileManifest.RESIDENTIAL_DOODADS.length)];
                doodads.add(new Doodad(dx, dy, tile));
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
