package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One room on a surface map, as somewhere that holds work.
 *
 * <p>The ground family's answer to what a compartment is aboard ship. A
 * {@link JobSite} wants an extent, a purpose and an id, and a map already knows
 * all three per cell: generation stamps a {@link RoomPurpose} on every cell of
 * every room it furnishes, and a room is the contiguous run of cells carrying
 * one. So a site is <b>recovered</b> rather than published — no generator has to
 * remember to hand its rooms on, and a family that starts stamping purposes
 * gets its job sites the same day.
 *
 * <p>Recovered from the purpose rather than from the building registry, which
 * is the nearer-looking answer and the wrong one. A building is a closed region
 * of interior found by flood fill, and a fortress shed comes out as one region
 * of five hundred and seventeen cells of which four hundred and ninety-six are
 * the bay — the rest being the threshold and the stub of yard its doors were cut
 * to. Work standing in that remainder would be the bay's, which it is not; and a
 * building holding two rooms would be one site holding both their trades.
 */
public final class RoomSite implements JobSite {

    /**
     * Purposes that are how you get somewhere rather than somewhere to be.
     *
     * <p>About circulation, not about whether a room has work in it. A room with
     * nothing published simply offers nothing and is never anybody's posting,
     * which is the correct answer and needs no list. A corridor is different in
     * kind: it is the space between rooms, it runs the length of a building, and
     * taken as a site it would join everything either end of it into one place
     * with one purpose.
     */
    private static final Set<RoomPurpose> CIRCULATION = EnumSet.of(
            RoomPurpose.GENERIC, RoomPurpose.CORRIDOR, RoomPurpose.OFFICE_CORRIDOR,
            RoomPurpose.MEDICAL_CORRIDOR, RoomPurpose.INDUSTRIAL_SPINE);

    private final int id;
    private final RoomPurpose purpose;
    private final int centreX;
    private final int centreY;
    /** This room's cells, packed and sorted so membership is a binary search. */
    private final long[] cells;

    private RoomSite(int id, RoomPurpose purpose, long[] cells, int centreX, int centreY) {
        this.id = id;
        this.purpose = purpose;
        this.cells = cells;
        this.centreX = centreX;
        this.centreY = centreY;
    }

    /**
     * Every room on this map, in a stable order.
     *
     * <p>Scanned column by column so two runs over one map produce the same
     * sites with the same ids, which is what makes a claim group stable across
     * a replay.
     */
    public static List<RoomSite> findAll(CellTopology topology, int width, int height) {
        boolean[][] seen = new boolean[width][height];
        List<RoomSite> sites = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (seen[x][y]) continue;
                RoomPurpose purpose = topology.getRoomPurpose(x, y);
                if (purpose == null || CIRCULATION.contains(purpose)) continue;
                RoomSite site = flood(topology, seen, width, height, x, y, purpose, sites.size());
                sites.add(site);
            }
        }
        return List.copyOf(sites);
    }

    /** The one room of this purpose that covers the given cell, or null. */
    public static RoomSite covering(List<RoomSite> sites, int cellX, int cellY) {
        for (RoomSite site : sites) {
            if (site.contains(cellX, cellY)) return site;
        }
        return null;
    }

    private static RoomSite flood(CellTopology topology, boolean[][] seen,
                                  int width, int height, int startX, int startY,
                                  RoomPurpose purpose, int id) {
        Deque<int[]> frontier = new ArrayDeque<>();
        List<long[]> found = new ArrayList<>();
        long sumX = 0;
        long sumY = 0;
        seen[startX][startY] = true;
        frontier.add(new int[]{ startX, startY });
        while (!frontier.isEmpty()) {
            int[] cell = frontier.poll();
            found.add(new long[]{ key(cell[0], cell[1]) });
            sumX += cell[0];
            sumY += cell[1];
            for (int[] step : STEPS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (seen[nx][ny]) continue;
                if (topology.getRoomPurpose(nx, ny) != purpose) continue;
                seen[nx][ny] = true;
                frontier.add(new int[]{ nx, ny });
            }
        }
        long[] cells = new long[found.size()];
        for (int index = 0; index < cells.length; index++) cells[index] = found.get(index)[0];
        Arrays.sort(cells);
        return new RoomSite(id, purpose, cells,
                (int) (sumX / cells.length), (int) (sumY / cells.length));
    }

    private static final int[][] STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    @Override
    public int id() {
        return id;
    }

    @Override
    public RoomPurpose purpose() {
        return purpose;
    }

    @Override
    public boolean contains(int cellX, int cellY) {
        return Arrays.binarySearch(cells, key(cellX, cellY)) >= 0;
    }

    @Override
    public int centreX() {
        return centreX;
    }

    @Override
    public int centreY() {
        return centreY;
    }

    /** How much floor this room covers. */
    public int cellCount() {
        return cells.length;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
