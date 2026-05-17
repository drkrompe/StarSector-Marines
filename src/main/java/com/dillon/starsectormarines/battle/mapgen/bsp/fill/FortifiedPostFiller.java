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
 * Filler for {@link BlockKind#FORTIFIED_POST} leaves. Carves a small hardened
 * emplacement: a hollow shell with a striped-safety-floor interior, a single
 * perimeter doorway, and a sparse scatter of skyport-style doodads (crates +
 * marker panels) that reads as "turret nest interior, with a way in".
 *
 * <p>Footprint policy: clamps the painted rect to at most 5x5 cells centered
 * inside the leaf (minimum 3x3 — the smallest size that still has an interior
 * cell). Anything past the 5x5 inner is left as STREET so the surrounding road
 * frame keeps reading as such even if the BSP gave us an oversized leaf for
 * this kind. The leaf itself is small in practice (labeling biases this kind
 * low-weight), but the clamp is a safety net.
 *
 * <p>Doorway decision: we keep ONE doorway rather than going fully sealed.
 * A truly sealed hardpoint would break the {@code MapResult} invariant that
 * any interior walkable cell is reachable from outside — fillers don't get
 * to opt out of that. One doorway preserves traversal and AI access (you can
 * push the post), and matches how the future #3 turret apron will want a
 * crew-side entry.
 *
 * <p>POI: emits a {@link PointOfInterest.Kind#COMMS} POI to mark this as the
 * "turret nest" anchor for future heavy-turret placement (#3 work).
 *
 * <p>TODO (visual): swap the perimeter walls to the urban-2 {@code turret-wall-*}
 * 3x3 (cols 3..5 rows 0..2) once the wall-pass renderer learns about the
 * urban-2 turret-wall block. For now perimeter cells fall through to the
 * default urban-1 wall art via {@code CellTopology.tagDefaultWalls}.
 */
public final class FortifiedPostFiller implements BlockFiller {

    /** Maximum painted-rect side length — caps the hardpoint footprint. */
    private static final int MAX_SIDE = 5;
    /** Minimum painted-rect side length — anything smaller has no interior. */
    private static final int MIN_SIDE = 3;
    /** Probability each interior cell receives a doodad (sparse — the nest is a working emplacement, not a storeroom). */
    private static final float DOODAD_PER_CELL_CHANCE = 0.45f;

    @Override
    public BlockKind kind() { return BlockKind.FORTIFIED_POST; }

    @Override
    public void fill(BlockLeaf leaf, NavigationGrid grid, CellTopology topology,
                     List<PointOfInterest> pois, List<Doodad> doodads, Random rng) {
        int w = Math.min(MAX_SIDE, leaf.width());
        int h = Math.min(MAX_SIDE, leaf.height());
        if (w < MIN_SIDE || h < MIN_SIDE) {
            // Too small to enclose anything readable — leave as STREET so the
            // road frame absorbs the leaf cleanly. (Labeling shouldn't hand us
            // a sub-3 leaf for this kind, but be defensive.)
            return;
        }

        // Center the painted rect inside the leaf so the surrounding leaf
        // cells stay STREET / sidewalk.
        int left   = leaf.left + (leaf.width()  - w) / 2;
        int top    = leaf.top  + (leaf.height() - h) / 2;
        int right  = left + w - 1;
        int bottom = top  + h - 1;

        // Perimeter: non-walkable wall cells. Interior: walkable + STRIPED.
        for (int x = left; x <= right; x++) {
            grid.setWalkable(x, top,    false);
            grid.setWalkable(x, bottom, false);
        }
        for (int y = top + 1; y <= bottom - 1; y++) {
            grid.setWalkable(left,  y, false);
            grid.setWalkable(right, y, false);
        }
        for (int y = top + 1; y <= bottom - 1; y++) {
            for (int x = left + 1; x <= right - 1; x++) {
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }

        // Single doorway on a random side — preserves the interior-reachable
        // invariant while keeping the post feeling sealed (one breach point,
        // not a throughway like residential two-door buildings).
        int side = rng.nextInt(4);
        int doorX;
        int doorY;
        switch (side) {
            case 0:  // top
                doorX = left + 1 + rng.nextInt(Math.max(1, w - 2));
                doorY = top;
                break;
            case 1:  // bottom
                doorX = left + 1 + rng.nextInt(Math.max(1, w - 2));
                doorY = bottom;
                break;
            case 2:  // left
                doorX = left;
                doorY = top + 1 + rng.nextInt(Math.max(1, h - 2));
                break;
            default: // right
                doorX = right;
                doorY = top + 1 + rng.nextInt(Math.max(1, h - 2));
                break;
        }
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        topology.setGroundKind(doorX, doorY, GroundKind.STRIPED);

        // Anchor cell: nearest walkable cell outside the rect. Conservatively
        // pick the cell immediately outside the doorway along the door's
        // facing direction; if that's out of bounds or non-walkable, fall back
        // to the door cell itself (it's walkable by construction).
        int anchorX = doorX;
        int anchorY = doorY;
        switch (side) {
            case 0: anchorY = doorY - 1; break;
            case 1: anchorY = doorY + 1; break;
            case 2: anchorX = doorX - 1; break;
            default: anchorX = doorX + 1; break;
        }
        if (!grid.inBounds(anchorX, anchorY) || !grid.isWalkable(anchorX, anchorY)) {
            anchorX = doorX;
            anchorY = doorY;
        }

        // Interior anchor: the post's geometric center is always a walkable
        // STRIPED cell (the carve guarantees a single open interior region),
        // so the center cell is the right pick. If the rect was clamped to
        // 3x3 the interior is a single 1x1 cell — also the center.
        int interiorX = (left + right) / 2;
        int interiorY = (top + bottom) / 2;

        pois.add(new PointOfInterest(PointOfInterest.Kind.COMMS,
                left, top, right, bottom,
                anchorX, anchorY, interiorX, interiorY));

        // Sparse interior doodads — skyport pool (crates + marker panels) reads
        // as "this emplacement is operational" without crowding the floor.
        // Skips the doorway cell so the door overlay stays clean.
        List<int[]> interior = new ArrayList<>();
        for (int y = top + 1; y <= bottom - 1; y++) {
            for (int x = left + 1; x <= right - 1; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (grid.isDoorway(x, y))   continue;
                interior.add(new int[]{x, y});
            }
        }
        TileManifest.TileFrame[] pool = TileManifest.SKYPORT_DOODADS;
        for (int[] cell : interior) {
            if (rng.nextFloat() >= DOODAD_PER_CELL_CHANCE) continue;
            TileManifest.TileFrame tile = pool[rng.nextInt(pool.length)];
            doodads.add(new Doodad(cell[0], cell[1], tile));
        }
    }
}
