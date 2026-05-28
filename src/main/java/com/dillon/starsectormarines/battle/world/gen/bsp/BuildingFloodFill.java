package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Single-pass flood-fill that discovers buildings from the post-stamp
 * {@link CellTopology} and writes the resulting registry. Runs once at the
 * end of generation, after walls have been tagged via
 * {@link CellTopology#tagDefaultWalls} so the WALL tag is authoritative.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Walk every cell. Seed = "has a {@link BuildingKind} hint and is not
 *       tagged WALL." Keying off the hint rather than ground kind sidesteps
 *       outdoor STRIPED surfaces like landing-zone aprons (no roof) and lets
 *       industrial warehouses (STRIPED floor + stamped hint) participate.</li>
 *   <li>BFS through 4-neighbors that match the seed predicate. Each connected
 *       component becomes one candidate building. Interior partition walls
 *       split rooms; partition doorways (non-wall hint cells) join them, so
 *       a multi-room residential reads as one building.</li>
 *   <li>Components with fewer than {@link #MIN_CELLS} cells are discarded
 *       (left at id=0) — too small to be a meaningful room, would just be
 *       visual noise.</li>
 *   <li>Surviving components get a sequential id (1..N), a bounding rect,
 *       a per-cell list, a kind voted from the stamped hints, and a
 *       deterministic tint derived from the id + seed.</li>
 * </ol>
 */
public final class BuildingFloodFill {

    /** Minimum interior-cell count for a flood-fill component to become a Building. */
    private static final int MIN_CELLS = 4;

    private BuildingFloodFill() {}

    public static Buildings populate(CellTopology topology, long seed) {
        Buildings buildings = new Buildings();
        int w = topology.getWidth();
        int h = topology.getHeight();
        if (w <= 0 || h <= 0) return buildings;

        int nextId = 1;

        // Scratch buffers reused across components.
        int[] queueX = new int[w * h];
        int[] queueY = new int[w * h];
        List<int[]> componentCells = new ArrayList<>(); // packed (x<<16)|y per cell

        for (int y0 = 0; y0 < h; y0++) {
            for (int x0 = 0; x0 < w; x0++) {
                if (!isInteriorSeed(topology, x0, y0)) continue;
                if (topology.getBuildingId(x0, y0) != 0) continue;

                // BFS this component. Use the cell's own building-id field as
                // the "visited" mark by writing the candidate id eagerly,
                // then rolling it back if the component fails the size gate.
                int head = 0;
                int tail = 0;
                queueX[tail] = x0;
                queueY[tail] = y0;
                tail++;
                topology.setBuildingId(x0, y0, nextId);

                int minX = x0, maxX = x0, minY = y0, maxY = y0;
                componentCells.clear();
                EnumMap<BuildingKind, Integer> votes = new EnumMap<>(BuildingKind.class);

                while (head < tail) {
                    int cx = queueX[head];
                    int cy = queueY[head];
                    head++;

                    componentCells.add(new int[]{cx, cy});
                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;

                    BuildingKind hint = topology.getBuildingKindHint(cx, cy);
                    if (hint != null) {
                        votes.merge(hint, 1, Integer::sum);
                    }

                    // 4-neighbor expansion. Only walk into INDOOR/TILE,
                    // non-WALL, unvisited cells.
                    if (cx > 0 && isInteriorSeed(topology, cx - 1, cy)
                            && topology.getBuildingId(cx - 1, cy) == 0) {
                        topology.setBuildingId(cx - 1, cy, nextId);
                        queueX[tail] = cx - 1; queueY[tail] = cy; tail++;
                    }
                    if (cx + 1 < w && isInteriorSeed(topology, cx + 1, cy)
                            && topology.getBuildingId(cx + 1, cy) == 0) {
                        topology.setBuildingId(cx + 1, cy, nextId);
                        queueX[tail] = cx + 1; queueY[tail] = cy; tail++;
                    }
                    if (cy > 0 && isInteriorSeed(topology, cx, cy - 1)
                            && topology.getBuildingId(cx, cy - 1) == 0) {
                        topology.setBuildingId(cx, cy - 1, nextId);
                        queueX[tail] = cx; queueY[tail] = cy - 1; tail++;
                    }
                    if (cy + 1 < h && isInteriorSeed(topology, cx, cy + 1)
                            && topology.getBuildingId(cx, cy + 1) == 0) {
                        topology.setBuildingId(cx, cy + 1, nextId);
                        queueX[tail] = cx; queueY[tail] = cy + 1; tail++;
                    }
                }

                if (componentCells.size() < MIN_CELLS) {
                    // Roll back — component too small to be a real room.
                    for (int[] cell : componentCells) {
                        topology.setBuildingId(cell[0], cell[1], 0);
                    }
                    continue;
                }

                // Pack cells into parallel int[] arrays for cache-friendly iteration.
                int n = componentCells.size();
                int[] cellsX = new int[n];
                int[] cellsY = new int[n];
                for (int i = 0; i < n; i++) {
                    int[] cell = componentCells.get(i);
                    cellsX[i] = cell[0];
                    cellsY[i] = cell[1];
                }

                BuildingKind kind = voteKind(votes);
                int tint = deterministicTint(nextId, seed);
                float tr = ((tint >> 16) & 0xFF) / 255f;
                float tg = ((tint >>  8) & 0xFF) / 255f;
                float tb = ( tint        & 0xFF) / 255f;

                buildings.add(new Building(
                        nextId, kind,
                        minX, maxX, minY, maxY,
                        cellsX, cellsY,
                        tr, tg, tb));
                nextId++;
            }
        }

        return buildings;
    }

    /**
     * Seed predicate — a cell is part of a building's interior iff a stamper
     * tagged it with a {@link BuildingKind} hint and it isn't a wall. Walls
     * separating two stamped rooms naturally split components; interior
     * partition doorways (non-wall hint cells inside the same shell) join them.
     */
    private static boolean isInteriorSeed(CellTopology topology, int x, int y) {
        if (topology.hasTag(x, y, Tag.WALL)) return false;
        return topology.getBuildingKindHint(x, y) != null;
    }

    private static BuildingKind voteKind(Map<BuildingKind, Integer> votes) {
        if (votes.isEmpty()) return BuildingKind.OTHER;
        BuildingKind best = BuildingKind.OTHER;
        int bestCount = -1;
        for (Map.Entry<BuildingKind, Integer> e : votes.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * Deterministic tint per building. Uses a small palette of brown/tan
     * brick variations rather than free-form RGB so the city reads as one
     * material with block-to-block flavor, not a clown carnival. Mixed off
     * id + seed.
     */
    private static int deterministicTint(int id, long seed) {
        // Simple splittable hash — id and seed both spread into all bits.
        long h = id * 0x9E3779B97F4A7C15L ^ seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        int idx = (int) ((h & 0x7FFFFFFF) % TINT_PALETTE.length);
        return TINT_PALETTE[idx];
    }

    /**
     * Palette of subtle warm-toned tints to multiply against the BRICK base.
     * Keeps roofs visually distinct from the underlying paver-plaza ground
     * (which uses the same BRICK tile at 1.0 tint) while staying inside a
     * coherent "weathered urban roof" range.
     */
    private static final int[] TINT_PALETTE = {
            0xC8B49C, // warm tan
            0xB29782, // dusky brick
            0xA8907A, // muted clay
            0xBFA589, // pale ochre
            0x9E8470, // dark sienna
            0xC4A98D, // sandstone
            0xA89177, // gray-brown
            0xB8987C, // adobe
    };
}
