package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.StoryServiceYard;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/** Three working pockets behind the stands, across the unobstructed service lane. */
final class OutpostSupplyYard {
    private OutpostSupplyYard() {}

    static void stamp(GenContext c) {
        int x = c.width - 71, y = c.height / 2;
        var yard = new StoryServiceYard(c);
        pocket(c, yard, x, y - 27, x + 10, y - 17);
        pocket(c, yard, x, y + 6, x + 10, y + 15);
        pocket(c, yard, x, y + 19, x + 10, y + 28);

        // Outbound cargo with room for the loader to turn toward the service lane.
        yard.cargo(x + 1, y - 25);
        yard.cargo(x + 1, y - 20);
        yard.prop(x + 7, y - 23, "stores-cargo-loader", 0);
        yard.prop(x + 7, y - 20, "parked-flatbed-truck", 0);

        // Incoming freight is sorted on a short roller table, beside empty pallets.
        yard.cargo(x + 1, y + 11);
        yard.prop(x + 1, y + 7, "stores-roller-conveyor", 0);
        yard.prop(x + 7, y + 8, "stores-cargo-loader", 1);
        yard.prop(x + 5, y + 7, "industrial-pallet-stack", 0);

        // A separate fuel pocket, set back from touchdown and the freight crossing.
        for (int dx : new int[]{2, 5, 8}) for (int dy : new int[]{22, 26})
            yard.prop(x + dx, y + dy, "industrial-fluid-tank", 0);
        yard.prop(x + 1, y + 20, "industrial-drum-cluster", 0);
        yard.prop(x + 5, y + 20, "industrial-cable-reel", 0);
        yard.prop(x + 6, y + 23, "parked-tanker-truck", 0);
    }

    private static void pocket(GenContext c, StoryServiceYard yard, int l, int b, int r, int t) {
        // Check the entire working surface before furnishing, including road shoulders
        // and the full eastbound air corridor, not just the individual prop anchors.
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        for (int yy = b; yy <= t; yy++) for (int xx = l; xx <= r; xx++) {
            if (roads[xx][yy]) throw new IllegalStateException("Supply yard overlaps road clearance");
            for (var pad : c.landingPads)
                if (xx >= pad.left() - 4 && yy >= pad.bottom() - 4 && yy <= pad.top() + 4)
                    throw new IllegalStateException("Supply yard overlaps eastern flight approach");
        }
        yard.gravel(l, b, r, t);
        yard.bay(l, b, r, t);
    }
}
