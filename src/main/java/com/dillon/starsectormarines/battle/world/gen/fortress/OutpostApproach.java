package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.StoryServiceYard;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;

import java.util.ArrayList;

/** Staggered roadside pickets; their occupants come from the installation's finite roster. */
final class OutpostApproach {
    private OutpostApproach() {}

    /** Picket centres, east (nearest the landing pads) first. */
    private static final int[][] PICKETS = {{194, 74}, {158, 86}};
    /** Each picket's gun: a heavy nest whose contact round goes through an APC's plate. */
    static final String NEST_GUN = "structure.turret-hephaestus";

    static void stamp(GenContext c) {
        for (int[] p : PICKETS) position(c, p[0], p[1]);
    }

    /**
     * A gun nest behind each picket, on the first ground west of its sandbags a nest fits. Runs after
     * finalize: nests are stamped onto the finished grid and draw no random numbers.
     */
    static void arm(GenContext c) {
        var roads = c.get(BspKeys.ROAD_RESERVATION);
        for (int[] p : PICKETS) {
            int[] at = null;
            search:
            for (int dx = 4; dx <= 10; dx++) for (int dy : new int[]{0, -1, 1, -2, 2, -3, 3}) {
                if (DefensePostStamper.nestFits(c.grid, c.topology, roads, p[0] - dx, p[1] + dy)) {
                    at = new int[]{p[0] - dx, p[1] + dy};
                    break search;
                }
            }
            if (at == null) throw new IllegalStateException("No ground for the picket nest at " + p[0] + "," + p[1]);
            c.defensePosts.add(DefensePostStamper.stampNest(c.grid, c.topology, c.doodads, at[0], at[1], NEST_GUN));
        }
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
        for (int dy = -2; dy <= 2; dy++)
            stands.add(new TacticalNode.StandPosition(x + 1, y + dy));
        stands.add(new TacticalNode.StandPosition(x - 1, y));
        c.tactical.add(new TacticalNode(TacticalNode.Kind.FORWARD_BUNKER, x, y,
                x - 3, y - 3, x + 3, y + 3, Faction.DEFENDER, 100, stands.size(), stands));
    }
}
