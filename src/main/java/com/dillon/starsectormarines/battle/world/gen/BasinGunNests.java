package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;

import java.util.List;

/**
 * The guns the occupiers dug in on each A Working Ship visit: ship guns cut from the wreckfall and set in sandbag
 * nests. On Salvage Rights' night heavy guns cover the cradle and the yard before it, autocannon the open yard, a
 * rocket nest stands behind the cradle and heavy machine guns watch the haul road and the north buildings; none faces
 * the receiving apron, where the company lands. On Cold Start the crew still holds the pump with a heavy gun and an
 * autocannon inside the work site, so the company clears them to start the pump. On Working Passage a gun stands
 * beside each watch on Morrow's route, off her hover lane, with a rocket nest behind the bend. UNTUNED first pass.
 */
final class BasinGunNests {
    private BasinGunNests() {}

    /** One nest: the cell aimed at and the emplacement it carries. */
    record Nest(int x, int y, String gun) {}

    static final String HEAVY = "structure.turret-hephaestus";
    static final String AUTOCANNON = "structure.turret-arbalest";
    static final String ROCKETS = "structure.turret-locust";
    static final String MACHINE_GUN = "structure.turret-heavy-mg";

    /** {@code visit}'s nests. */
    static List<Nest> nests(ShepherdBasin.Visit visit) {
        return switch (visit) {
            case SALVAGE_RIGHTS -> List.of(
                    new Nest(98, 116, HEAVY),        // the yard between the workshop and the cradle
                    new Nest(113, 122, HEAVY),       // cradle, north-west
                    new Nest(150, 100, HEAVY),       // cradle, south-east
                    new Nest(92, 104, AUTOCANNON),   // open yard west of the cradle
                    new Nest(118, 82, AUTOCANNON),   // below the long shed
                    new Nest(160, 112, ROCKETS),     // behind the cradle
                    new Nest(95, 130, MACHINE_GUN),  // north buildings
                    new Nest(172, 40, MACHINE_GUN)); // south haul picket
            case COLD_START -> List.of(
                    new Nest(163, 113, HEAVY),       // pump work site, west
                    new Nest(180, 113, AUTOCANNON)); // pump work site, east
            case WORKING_PASSAGE -> List.of(
                    new Nest(219, 130, AUTOCANNON),  // bend watch
                    new Nest(240, 130, ROCKETS),     // behind the bend
                    new Nest(263, 116, HEAVY),       // narrows watch
                    new Nest(276, 95, MACHINE_GUN)); // climb-out watch
        };
    }

    /**
     * Stamps every one of {@code visit}'s nests on the first ground near its aim a nest fits. Runs after finalize:
     * nests are stamped onto the finished grid and draw no random numbers.
     */
    static void arm(GenContext c, ShepherdBasin.Visit visit) {
        boolean[][] roads = c.get(BspKeys.ROAD_RESERVATION);
        for (Nest nest : nests(visit)) {
            int[] at = fit(c, roads, nest.x(), nest.y());
            if (at == null) throw new IllegalStateException("No ground for the basin nest at " + nest.x() + "," + nest.y());
            c.defensePosts.add(DefensePostStamper.stampNest(c.grid, c.topology, c.doodads, at[0], at[1], nest.gun()));
        }
    }

    private static int[] fit(GenContext c, boolean[][] roads, int x, int y) {
        for (int r = 0; r <= 6; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == r
                            && DefensePostStamper.nestFits(c.grid, c.topology, roads, x + dx, y + dy))
                        return new int[]{x + dx, y + dy};
        return null;
    }
}
