package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;

/**
 * What every arrival area needs of the ground under it, whatever authored it.
 *
 * <p>A berth is a piece of open map: in bounds, walkable, and not inside a
 * building. That much is the same question on the conquest shoreline and inside
 * a precinct's attacker region, and the two stages differ only in what they add
 * to it — a biome on one side, a claimed region and the map's own hazards on the
 * other. Kept here so the shared half cannot drift into two answers.
 */
final class LandingGround {

    private LandingGround() {}

    /** Whether a single cell is open map a shuttle could stand a berth on. */
    static boolean isOpen(GenContext ctx, int x, int y) {
        if (!ctx.grid.inBounds(x, y) || !ctx.grid.isWalkable(x, y)) return false;
        return ctx.topology.getBuildingId(x, y) == 0;
    }

    /** Whether every cell of an area — both berths and the ground between — is open. */
    static boolean isOpen(GenContext ctx, LandingArea area) {
        for (int y = area.bottom; y <= area.top; y++) {
            for (int x = area.left; x <= area.right; x++) {
                if (!isOpen(ctx, x, y)) return false;
            }
        }
        return true;
    }
}
