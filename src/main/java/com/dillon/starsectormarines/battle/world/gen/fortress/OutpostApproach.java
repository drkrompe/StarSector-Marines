package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.StoryServiceYard;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

import java.util.ArrayList;

/** Staggered roadside pickets; their occupants come from the installation's finite roster. */
final class OutpostApproach {
    private OutpostApproach() {}

    static void stamp(GenContext c) {
        position(c, 194, 74);
        position(c, 158, 86);
    }

    private static void position(GenContext c, int x, int y) {
        var yard = new StoryServiceYard(c);
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        for (int yy = y - 3; yy <= y + 3; yy++) for (int xx = x - 3; xx <= x + 3; xx++)
            if (roads[xx][yy]) throw new IllegalStateException("Approach position overlaps road clearance");
        yard.gravel(x - 3, y - 3, x + 3, y + 3);
        // East-facing cover overlooks the inbound march; both flanks and the road remain open.
        for (int dy = -2; dy <= 2; dy++) yard.prop(x + 2, y + dy, "sandbag-straight-e", 0);
        yard.prop(x - 2, y + 2, "industrial-crate-stack", 0);
        var stands = new ArrayList<TacticalNode.StandPosition>();
        for (int dy = -1; dy <= 1; dy++)
            stands.add(new TacticalNode.StandPosition(x + 1, y + dy));
        c.tactical.add(new TacticalNode(TacticalNode.Kind.FORWARD_BUNKER, x, y,
                x - 3, y - 3, x + 3, y + 3, Faction.DEFENDER, 100, 3, stands));
    }
}
