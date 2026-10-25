package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;

/**
 * The town plan's zoning ({@code town-plan.md}) for a map that is one settlement: laid over the rolled
 * {@link DistrictMap} so the town has a centre, a high street round it and an industrial edge, rather than
 * themes scattered block by block. A map of precincts is planned per place instead ({@code PrecinctZoning}).
 *
 * <ul>
 *   <li><b>Core</b>: the block holding the town's centre and its neighbours toward the centre's nearer block
 *       corners, two by two where the map has room, are the civic core.</li>
 *   <li><b>High street ring</b>: every block beside the core is mixed town, where a main street and a market
 *       square find their pocket.</li>
 *   <li><b>Industrial edge</b>: the map edge farthest from the core is the industrial belt, two blocks deep
 *       where the world's economy is industry.</li>
 *   <li>Elsewhere the roll stands, except that a civic block away from the core is homes (a town has one
 *       centre) and an industrial block off the belt is homes too unless it touches the belt, which it then
 *       extends.</li>
 * </ul>
 *
 * The coast keeps its waterfront everywhere ({@link DistrictMap#forceThemeAt} declines it). Draws no random
 * number.
 */
public final class TownZoning {

    private TownZoning() { }

    /** What each block of a plan is. */
    enum Part { CORE, RING, BELT, REST }

    /** Lays the plan over {@code map}, its centre at nav cell {@code (centreX, centreY)}. */
    public static void apply(DistrictMap map, int centreX, int centreY) {
        Part[][] plan = plan(map.districtsX(), map.districtsY(), map.districtCellWidth(), map.districtCellHeight(),
                centreX, centreY, map.economyTheme() == MapDistrictTheme.INDUSTRIAL);
        int nx = map.districtsX(), ny = map.districtsY();
        for (int dx = 0; dx < nx; dx++) {
            for (int dy = 0; dy < ny; dy++) {
                MapDistrictTheme rolled = map.themeAtDistrict(dx, dy);
                MapDistrictTheme theme = switch (plan[dx][dy]) {
                    case CORE -> MapDistrictTheme.CIVIC;
                    case RING -> MapDistrictTheme.MIXED;
                    case BELT -> MapDistrictTheme.INDUSTRIAL;
                    case REST -> rest(rolled, touches(plan, dx, dy, Part.BELT));
                };
                if (theme != rolled) {
                    map.forceThemeAt(dx * map.districtCellWidth() + map.districtCellWidth() / 2,
                            dy * map.districtCellHeight() + map.districtCellHeight() / 2, theme);
                }
            }
        }
    }

    /** A block outside the plan keeps its roll, but the centre and the industry are the plan's. */
    static MapDistrictTheme rest(MapDistrictTheme rolled, boolean besideBelt) {
        if (rolled == MapDistrictTheme.CIVIC) return MapDistrictTheme.RESIDENTIAL;
        if (rolled == MapDistrictTheme.INDUSTRIAL && !besideBelt) return MapDistrictTheme.RESIDENTIAL;
        return rolled;
    }

    /**
     * The plan on a grid of {@code nx} by {@code ny} blocks of {@code cellW} by {@code cellH} cells, centred on
     * nav cell {@code (centreX, centreY)}; {@code deepBelt} makes the industrial edge two blocks deep.
     */
    static Part[][] plan(int nx, int ny, int cellW, int cellH, int centreX, int centreY, boolean deepBelt) {
        Part[][] plan = new Part[nx][ny];
        for (int dx = 0; dx < nx; dx++) for (int dy = 0; dy < ny; dy++) plan[dx][dy] = Part.REST;
        int cx = clamp(centreX / cellW, nx), cy = clamp(centreY / cellH, ny);
        int cx2 = clamp(cx + (centreX - cx * cellW < cellW / 2 ? -1 : 1), nx);
        int cy2 = clamp(cy + (centreY - cy * cellH < cellH / 2 ? -1 : 1), ny);
        int x0 = Math.min(cx, cx2), x1 = Math.max(cx, cx2), y0 = Math.min(cy, cy2), y1 = Math.max(cy, cy2);
        for (int dx = x0; dx <= x1; dx++) for (int dy = y0; dy <= y1; dy++) plan[dx][dy] = Part.CORE;
        for (int dx = 0; dx < nx; dx++) {
            for (int dy = 0; dy < ny; dy++) {
                if (plan[dx][dy] == Part.REST && touches(plan, dx, dy, Part.CORE)) plan[dx][dy] = Part.RING;
            }
        }
        // The edge farthest from the core; ties go west, east, north, south, so the choice is stable.
        int[] room = {x0, nx - 1 - x1, y0, ny - 1 - y1};
        int side = 0;
        for (int i = 1; i < room.length; i++) if (room[i] > room[side]) side = i;
        int depth = deepBelt ? 2 : 1;
        for (int dx = 0; dx < nx; dx++) {
            for (int dy = 0; dy < ny; dy++) {
                int in = switch (side) {
                    case 0 -> dx;
                    case 1 -> nx - 1 - dx;
                    case 2 -> dy;
                    default -> ny - 1 - dy;
                };
                if (in < depth && plan[dx][dy] == Part.REST) plan[dx][dy] = Part.BELT;
            }
        }
        return plan;
    }

    private static boolean touches(Part[][] plan, int dx, int dy, Part part) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int x = dx + d[0], y = dy + d[1];
            if (x >= 0 && y >= 0 && x < plan.length && y < plan[0].length && plan[x][y] == part) return true;
        }
        return false;
    }

    private static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }
}
