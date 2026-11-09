package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective.LiftPoint;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a Ruins battle's vault and lift points stand on its ground ({@code mapgen-nouns.md}, Ruins ground): the vault's
 * source cell as the ground published it, and a lift point at the landing area, at every landing court and at every
 * entrance of the interior (its apron), each with its route from the vault. Read off {@link MapResult#ruinsDelve},
 * never searched for.
 *
 * @param sourceX    the vault's source cell, where the breach is held
 * @param liftPoints the landing area first, then each court nearest the landing first, then each entrance
 */
record RuinsGroundLayout(int sourceX, int sourceY, List<LiftPoint> liftPoints) {

    /** What the rail and map call an entrance; numbered when the interior has more than one. */
    static final String ENTRANCE_NAME = "Entrance";

    /** The layout of a ruins map; a map with no delve, or a lift point with no route from the vault, is an error. */
    static RuinsGroundLayout of(MapResult map) {
        var delve = map.ruinsDelve;
        if (delve == null) throw new IllegalStateException("a Ruins battle needs ruins ground");
        var source = delve.vault().source();
        List<LiftPoint> lifts = new ArrayList<>();
        lifts.add(new LiftPoint(BattleSetup.RUINS_LANDING_NAME, true,
                route(map, source.x(), source.y(), delve.landing().get(0).berth(0))));
        for (var layer : delve.layers()) {
            if (layer.court() == null) continue;
            LandingArea area = layer.court().area();
            lifts.add(new LiftPoint("Landing court " + (layer.index() + 1), false,
                    route(map, source.x(), source.y(), area.berth(0))));
        }
        RuinsDelve.Interior interior = delve.interior();
        if (interior != null) {
            List<RuinsDelve.Area> entrances = interior.entrances();
            for (int i = 0; i < entrances.size(); i++) {
                RuinsDelve.Cell apron = apronCell(map.grid, entrances.get(i), interior.footprint());
                String name = entrances.size() == 1 ? ENTRANCE_NAME : ENTRANCE_NAME + " " + (i + 1);
                lifts.add(new LiftPoint(name, LiftPoint.Kind.ENTRANCE,
                        route(map, source.x(), source.y(), apron.x(), apron.y())));
            }
        }
        return new RuinsGroundLayout(source.x(), source.y(), List.copyOf(lifts));
    }

    /**
     * Where an entrance's apron is held: the walkable cell outside the interior's footprint nearest the entrance, by
     * walking out from its middle. Provisional until the ground publishes each entrance's apron itself.
     */
    static RuinsDelve.Cell apronCell(NavigationGrid grid, RuinsDelve.Area entrance, RuinsDelve.Area footprint) {
        int startX = entrance.centreX();
        int startY = entrance.centreY();
        boolean[] seen = new boolean[grid.getWidth() * grid.getHeight()];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{startX, startY});
        seen[startY * grid.getWidth() + startX] = true;
        int[][] steps = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}};
        while (!open.isEmpty()) {
            int[] cell = open.poll();
            if (!footprint.contains(cell[0], cell[1]) && grid.isWalkable(cell[0], cell[1])) {
                return new RuinsDelve.Cell(cell[0], cell[1]);
            }
            for (int[] step : steps) {
                int x = cell[0] + step[0];
                int y = cell[1] + step[1];
                if (!grid.inBounds(x, y) || seen[y * grid.getWidth() + x]) continue;
                seen[y * grid.getWidth() + x] = true;
                if (grid.isWalkable(x, y) || (x == startX && y == startY)) open.add(new int[]{x, y});
            }
        }
        throw new IllegalStateException("entrance " + entrance + " opens on no open ground");
    }

    private static int[] route(MapResult map, int x, int y, LandingPad to) {
        return route(map, x, y, to.centerX, to.centerY);
    }

    private static int[] route(MapResult map, int x, int y, int toX, int toY) {
        int[] route = GridPathfinder.findPath(map.grid, x, y, toX, toY);
        if (route.length == 0) throw new IllegalStateException("no route from the vault to " + toX + "," + toY);
        return route;
    }
}
