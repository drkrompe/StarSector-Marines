package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

import java.util.ArrayList;
import java.util.List;

/**
 * A town sheltered in a cleft, on a frozen world a great crevasse ({@code voids.md}, the crevasse town; owner: "a
 * settlement might have built itself into a massive crevasse to shelter from the planet's winds"). A map's design
 * places it ({@link Landform.Shelter}); nothing rolls for one. Stated with the plan, before anything is laid: the
 * plan's landscape holds two bands of {@link RidgePlanner.Relief#SHELTER_WALL} along a pair of opposite edges, so the
 * town, its roads and its fields grow on the floor between them, and the landscape pass raises the walls on what they
 * held ({@code CrevasseShelter}) as terraced banks in the world's skin, never as hills.
 */
public final class CrevasseTown {

    /**
     * Each band's thickness, centred on its edge: what it holds reaches half this plus the held margins (about 25
     * cells) in from the edge, the wall's depth with its wander.
     */
    static final int BAND_THICKNESS = 36;

    private CrevasseTown() { }

    /**
     * The plan as a sheltered town, its walls held along a pair of opposite edges ({@link Landform.Shelter}).
     *
     * @param alongX whether the walls run along the south and north edges, else the west and east
     */
    public static PrecinctPlan shelter(PrecinctPlan plan, int width, int height, boolean alongX) {
        if (plan == null) return null;
        List<RidgePlanner.Band> held = new ArrayList<>(plan.landscape().held());
        held.addAll(walls(width, height, alongX));
        return plan.withLandscape(plan.landscape().withHeld(held));
    }

    /** The two wall bands, each centred on its edge and running its whole length. */
    static List<RidgePlanner.Band> walls(int width, int height, boolean alongX) {
        List<RidgePlanner.Band> out = new ArrayList<>();
        if (alongX) {
            out.add(band(0, 0, width - 1, 0));
            out.add(band(0, height - 1, width - 1, height - 1));
        } else {
            out.add(band(0, 0, 0, height - 1));
            out.add(band(width - 1, 0, width - 1, height - 1));
        }
        return out;
    }

    private static RidgePlanner.Band band(int x0, int y0, int x1, int y1) {
        return new RidgePlanner.Band(List.of(new ElevationPlan.Point(x0, y0), new ElevationPlan.Point(x1, y1)),
                BAND_THICKNESS, 1, 1, RidgePlanner.Relief.SHELTER_WALL);
    }

    /** The crevasse's wall bands a landscape holds; empty for any other map. */
    public static List<RidgePlanner.Band> wallsOf(Landscape landscape) {
        List<RidgePlanner.Band> out = new ArrayList<>();
        for (RidgePlanner.Band band : landscape.held()) if (band.relief() == RidgePlanner.Relief.SHELTER_WALL) out.add(band);
        return out;
    }

    /** Whether a crevasse's walls run along the north and south edges. */
    public static boolean alongX(List<RidgePlanner.Band> walls) {
        List<ElevationPlan.Point> path = walls.get(0).path();
        return Math.abs(path.get(1).x() - path.get(0).x()) >= Math.abs(path.get(1).y() - path.get(0).y());
    }
}
