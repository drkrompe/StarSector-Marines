package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a Ruins battle's vault and lift points stand on its ground ({@code mapgen-nouns.md}, Ruins ground): the vault's source
 * cell as the ground published it, and a lift point at the landing area and at every landing court, each with its
 * route from the vault. Read off {@link MapResult#ruinsDelve}, never searched for.
 *
 * @param sourceX    the vault's source cell, where the breach is held
 * @param liftPoints the landing area first, then each court nearest the landing first
 */
record RuinsGroundLayout(int sourceX, int sourceY, List<RuinsVaultObjective.LiftPoint> liftPoints) {

    /** The layout of a ruins map; a map with no delve, or a lift point with no route from the vault, is an error. */
    static RuinsGroundLayout of(MapResult map) {
        var delve = map.ruinsDelve;
        if (delve == null) throw new IllegalStateException("a Ruins battle needs ruins ground");
        var source = delve.vault().source();
        List<RuinsVaultObjective.LiftPoint> lifts = new ArrayList<>();
        lifts.add(new RuinsVaultObjective.LiftPoint(BattleSetup.RUINS_LANDING_NAME, true,
                route(map, source.x(), source.y(), delve.landing().get(0).berth(0))));
        for (var layer : delve.layers()) {
            if (layer.court() == null) continue;
            LandingArea area = layer.court().area();
            lifts.add(new RuinsVaultObjective.LiftPoint("Landing court " + (layer.index() + 1), false,
                    route(map, source.x(), source.y(), area.berth(0))));
        }
        return new RuinsGroundLayout(source.x(), source.y(), List.copyOf(lifts));
    }

    private static int[] route(MapResult map, int x, int y, LandingPad to) {
        int[] route = GridPathfinder.findPath(map.grid, x, y, to.centerX, to.centerY);
        if (route.length == 0) throw new IllegalStateException("no route from the vault to the berth at "
                + to.centerX + "," + to.centerY);
        return route;
    }
}
