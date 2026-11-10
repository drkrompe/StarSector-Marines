package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

import java.util.ArrayList;
import java.util.List;

/**
 * A landform a map's design places ({@code voids.md}, placed by design; owner: "make fun maps, then skin them to match
 * the biomes"): a natural drop across the map or a sheltering cleft round its town. Nothing rolls for one; a map
 * has one because its design states it, and the world's palette only picks its skin (a gorge, a canyon, a
 * crevasse, a lava rift). Stated with the plan, before anything is laid: the plan's landscape holds the landform's
 * ground, so the places, roads and fields grow round it, and the landscape pass cuts or raises it on what was held.
 */
public sealed interface Landform permits Landform.Drop, Landform.Shelter {

    /** The plan with this landform's ground held. */
    PrecinctPlan onto(PrecinctPlan plan, int width, int height);

    /** The plan with {@code landform}'s ground held, or the plan unchanged when there is none. */
    static PrecinctPlan apply(Landform landform, PrecinctPlan plan, int width, int height) {
        return landform == null || plan == null ? plan : landform.onto(plan, width, height);
    }

    /**
     * A natural drop edge to edge across the map, its roads and routes crossing it on bridges and timber footbridges
     * between ({@code NaturalChasm}).
     *
     * @param alongX whether it runs west to east, else south to north
     * @param across where it runs, as a share of the map across it (0 to 1)
     */
    record Drop(boolean alongX, float across) implements Landform {

        /** The held band's thickness: the drop's width with its wander and the held margins. */
        static final int BAND_THICKNESS = 28;

        public Drop {
            if (!(across > 0f && across < 1f)) throw new IllegalArgumentException("a drop runs inside the map");
        }

        @Override public PrecinctPlan onto(PrecinctPlan plan, int width, int height) {
            List<RidgePlanner.Band> held = new ArrayList<>(plan.landscape().held());
            held.add(band(width, height));
            return plan.withLandscape(plan.landscape().withHeld(held));
        }

        RidgePlanner.Band band(int width, int height) {
            List<ElevationPlan.Point> path = alongX
                    ? List.of(new ElevationPlan.Point(0, at(height)), new ElevationPlan.Point(width - 1, at(height)))
                    : List.of(new ElevationPlan.Point(at(width), 0), new ElevationPlan.Point(at(width), height - 1));
            return new RidgePlanner.Band(path, BAND_THICKNESS, 1, 1, RidgePlanner.Relief.DROP);
        }

        private int at(int span) {
            return Math.round(across * (span - 1));
        }

        /** The drop a landscape holds, or {@code null}. */
        public static Drop of(Landscape landscape, int width, int height) {
            for (RidgePlanner.Band band : landscape.held()) {
                if (band.relief() != RidgePlanner.Relief.DROP) continue;
                ElevationPlan.Point a = band.path().get(0), b = band.path().get(band.path().size() - 1);
                boolean alongX = Math.abs(b.x() - a.x()) >= Math.abs(b.y() - a.y());
                float across = (float) (alongX ? a.y() / Math.max(1, height - 1) : a.x() / Math.max(1, width - 1));
                return new Drop(alongX, Math.max(.01f, Math.min(.99f, across)));
            }
            return null;
        }
    }

    /**
     * The town sheltered in a cleft, its walls rising along a pair of opposite edges as terraced banks and the
     * battle fought on its floor ({@link CrevasseTown}): on a frozen world a great crevasse.
     *
     * @param alongX whether the walls run along the south and north edges, else the west and east: along the battle's
     *               axis, so the attack runs down the floor rather than over a wall
     */
    record Shelter(boolean alongX) implements Landform {
        @Override public PrecinctPlan onto(PrecinctPlan plan, int width, int height) {
            return CrevasseTown.shelter(plan, width, height, alongX);
        }
    }
}
