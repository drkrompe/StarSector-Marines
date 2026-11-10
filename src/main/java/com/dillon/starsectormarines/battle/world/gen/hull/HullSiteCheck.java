package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.ruins.DelveCheck;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Apron;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The fallen hull's laws, read off the finished grid one cardinal step at a time ({@code fallen-hull-nouns.md}, as
 * {@link DelveCheck} reads a ruins site's): every way in is open and its apron's berths clear; from the first apron the
 * forge, every hall and every other apron is reached; a hall frame walks from hall to hall through the hull's cargo
 * docks ({@code derelict-watch.md}, section 3).
 */
public final class HullSiteCheck {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private HullSiteCheck() { }

    /** What the hull gets wrong on this grid; empty when it keeps every law. */
    public static List<String> problems(NavigationGrid grid, CrashSiteStage.Laid laid) {
        List<String> out = new ArrayList<>();
        List<Apron> aprons = laid.interior().aprons();
        if (aprons.isEmpty()) {
            out.add("the hull has no way in");
            return out;
        }
        for (Area door : laid.interior().entrances()) {
            for (int y = door.bottom(); y <= door.top(); y++) {
                for (int x = door.left(); x <= door.right(); x++) {
                    if (!grid.isWalkable(x, y)) {
                        out.add("the hull's way in at " + door + " is not open");
                        y = door.top() + 1;
                        break;
                    }
                }
            }
        }
        LandingPad start = aprons.get(0).lift().berth(0);
        boolean[] reach = reach(grid, start.centerX, start.centerY);
        for (Apron apron : aprons) {
            for (LandingPad berth : apron.lift().berths()) {
                if (!berth.isClear(grid, null)) out.add(apron.name() + " has a berth that is not clear ground");
                else if (!reached(grid, reach, new Cell(berth.centerX, berth.centerY))) out.add(apron.name() + " is not reachable");
            }
        }
        if (!reached(grid, reach, laid.forge())) out.add("the autoforge at " + laid.forge() + " is not reachable");
        List<Area> chambers = laid.interior().chambers();
        for (int k = 0; k < chambers.size(); k++) {
            Area c = chambers.get(k);
            if (!reached(grid, reach, new Cell(c.centreX(), c.centreY()))) out.add("hall " + (k + 1) + " at " + c + " is not reachable");
        }
        out.addAll(DelveCheck.frameProblems(grid, laid.interior().halls(), laid.interior().doors(),
                DelveCheck.HALL_FRAME_RADIUS));
        return out;
    }

    private static boolean reached(NavigationGrid grid, boolean[] reach, Cell c) {
        return grid.inBounds(c.x(), c.y()) && reach[c.y() * grid.getWidth() + c.x()];
    }

    private static boolean[] reach(NavigationGrid grid, int x0, int y0) {
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] seen = new boolean[w * h];
        if (!grid.isWalkable(x0, y0)) return seen;
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        frontier.add(new int[]{x0, y0});
        seen[y0 * w + x0] = true;
        while (!frontier.isEmpty()) {
            int[] c = frontier.poll();
            for (int[] s : STEPS) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx] || !grid.isWalkable(nx, ny)) continue;
                seen[ny * w + nx] = true;
                frontier.add(new int[]{nx, ny});
            }
        }
        return seen;
    }
}
