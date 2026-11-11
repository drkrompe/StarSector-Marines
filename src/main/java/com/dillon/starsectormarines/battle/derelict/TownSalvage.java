package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The town's own metal as salvage on the claim (`fallen-hull-nouns.md`, Strippers): each building near enough the hull
 * that its zone keeps it gives one pile, laid on open ground beside it where a stripper can stand and cut, its metal
 * by the building's size. The buildings nearest the hull come first, as the strippers work outward. The hull's sections
 * are its own and give none; a town building between sections spread through the town is the town's.
 */
public final class TownSalvage {

    /** Metal a building gives for each cell it covers. UNTUNED. */
    public static final float METAL_PER_CELL = 4f;
    /** The least and most metal one building gives. UNTUNED. */
    public static final float MIN_METAL = 120f, MAX_METAL = 600f;
    /** How far a pile's cell may lie from its building, cells (Chebyshev). */
    public static final int BESIDE_CELLS = 2;

    /** The ground as the salvage reads it. */
    public interface Ground {
        int width();

        int height();

        /** The building covering a cell, 0 for none. */
        int buildingAt(int x, int y);

        /** Whether a stripper can stand on a cell: walkable, no wall, no building. */
        boolean open(int x, int y);
    }

    private TownSalvage() { }

    /**
     * Up to {@code max} piles, one per building, nearest the hull first.
     *
     * @param ground the battle's ground
     * @param site   the hull's site: its laid box, its sections and the reach its zone keeps off them
     * @param max    the most piles laid
     */
    public static List<FallenHullSite.Pile> piles(Ground ground, FallenHullSite site, int max) {
        Map<Integer, int[]> cells = new HashMap<>();   // building -> {cells, nearest reach, pile x, pile y}
        int reach = site.perimeterCells();
        for (int y = 0; y < ground.height(); y++) {
            for (int x = 0; x < ground.width(); x++) {
                int b = ground.buildingAt(x, y);
                if (b == 0 || onHull(site, x, y)) continue;
                int[] c = cells.computeIfAbsent(b, k -> new int[] {0, Integer.MAX_VALUE, -1, -1});
                c[0]++;
            }
        }
        // Each building's pile: the open cell beside it nearest the hull, on the ground the hull zone keeps.
        for (int y = 0; y < ground.height(); y++) {
            for (int x = 0; x < ground.width(); x++) {
                if (onHull(site, x, y) || !ground.open(x, y)) continue;
                int r = FallenHullSite.Rect.nearestOf(site.sections(), x, y).chebyshev(x, y);
                if (r > reach) continue;
                for (int b : besideBuildings(ground, x, y)) {
                    int[] c = cells.get(b);
                    if (c == null || r >= c[1]) continue;
                    c[1] = r;
                    c[2] = x;
                    c[3] = y;
                }
            }
        }
        List<int[]> ranked = new ArrayList<>();   // {building, cells, reach, x, y}
        for (Map.Entry<Integer, int[]> e : cells.entrySet()) {
            int[] c = e.getValue();
            if (c[2] >= 0) ranked.add(new int[] {e.getKey(), c[0], c[1], c[2], c[3]});
        }
        ranked.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[0], b[0]));
        List<FallenHullSite.Pile> out = new ArrayList<>();
        for (int[] r : ranked) {
            if (out.size() >= max) break;
            float metal = Math.max(MIN_METAL, Math.min(MAX_METAL, r[1] * METAL_PER_CELL));
            out.add(new FallenHullSite.Pile(r[3], r[4], metal));
        }
        return out;
    }

    /**
     * The town as places on the claim: an open cell beside each building the hull zone keeps, at most one in each
     * {@code spacing}-cell square, nearest the hull first. Where the hull's flocks range the night it came down.
     */
    public static List<int[]> places(Ground ground, FallenHullSite site, int spacing) {
        List<FallenHullSite.Pile> all = piles(ground, site, Integer.MAX_VALUE);
        List<int[]> out = new ArrayList<>();
        Set<Long> squares = new HashSet<>();
        for (FallenHullSite.Pile p : all) {
            long key = ((long) Math.floorDiv(p.y(), spacing) << 32) ^ (Math.floorDiv(p.x(), spacing) & 0xffffffffL);
            if (squares.add(key)) out.add(new int[] {p.x(), p.y()});
        }
        return out;
    }

    /**
     * Whether a cell lies on one of the hull's sections. Its laid box spans the furrow between sections spread through
     * a town, so the town's buildings between them are the town's, not the hull's.
     */
    private static boolean onHull(FallenHullSite site, int x, int y) {
        for (FallenHullSite.Rect r : site.sections()) if (r.contains(x, y)) return true;
        return false;
    }

    /** The buildings within {@link #BESIDE_CELLS} of a cell, each once. */
    private static List<Integer> besideBuildings(Ground ground, int x, int y) {
        List<Integer> out = new ArrayList<>(2);
        for (int dy = -BESIDE_CELLS; dy <= BESIDE_CELLS; dy++) {
            for (int dx = -BESIDE_CELLS; dx <= BESIDE_CELLS; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= ground.width() || ny >= ground.height()) continue;
                int b = ground.buildingAt(nx, ny);
                if (b != 0 && !out.contains(b)) out.add(b);
            }
        }
        return out;
    }
}
