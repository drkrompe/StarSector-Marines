package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A town sheltered in a great crevasse on a frozen world ({@code voids.md}, the crevasse town; owner: "a settlement
 * might have built itself into a massive crevasse to shelter from the planet's winds"). Decided with the plan, before
 * anything is laid: the plan's landscape holds two bands of {@link RidgePlanner.Relief#ICE_WALL} along a pair of
 * opposite edges, so the town, its roads and its fields grow on the floor between them, and the landscape pass raises
 * the crevasse's walls on what they held ({@code CrevasseShelter}) as terraced ice banks, never as hills.
 */
public final class CrevasseTown {

    /** The share of frozen worlds' derived maps that are crevasse towns, short of the switch. UNTUNED. */
    public static final float CHANCE = .5f;
    /**
     * Each band's thickness, centred on its edge: what it holds reaches half this plus the held margins (about 22
     * cells) in from the edge, the wall's depth with its wander.
     */
    static final int BAND_THICKNESS = 30;

    private CrevasseTown() { }

    /**
     * The plan as a crevasse town when the world is frozen and the map's own draw says so, else the plan unchanged.
     *
     * @param alongX whether the walls run along the north and south edges (else along the west and east): along the
     *               battle's axis, so the attack runs down the crevasse's floor rather than over a wall
     */
    public static PrecinctPlan shelter(PrecinctPlan plan, TargetProfile profile, int width, int height,
                                       boolean alongX, long seed) {
        if (plan == null || profile == null || profile.surface() != SurfacePalette.FROZEN || !wanted(seed)) {
            return plan;
        }
        List<RidgePlanner.Band> held = new ArrayList<>(plan.landscape().held());
        held.addAll(walls(width, height, alongX));
        return plan.withLandscape(plan.landscape().withHeld(held));
    }

    /**
     * Whether this map is a crevasse town: {@code battle.mapgen.crevasseTowns} (a share from 0 to 1) when set, else
     * {@link #CHANCE}, on the map's own substream.
     */
    public static boolean wanted(long seed) {
        String asked = System.getProperty("battle.mapgen.crevasseTowns");
        float chance = asked == null || asked.isBlank() ? CHANCE : Float.parseFloat(asked.trim());
        return new Random(seed ^ 0x43524556_5457L).nextFloat() < chance;
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
                BAND_THICKNESS, 1, 1, RidgePlanner.Relief.ICE_WALL);
    }

    /** The crevasse's wall bands a landscape holds; empty for any other map. */
    public static List<RidgePlanner.Band> wallsOf(Landscape landscape) {
        List<RidgePlanner.Band> out = new ArrayList<>();
        for (RidgePlanner.Band band : landscape.held()) if (band.relief() == RidgePlanner.Relief.ICE_WALL) out.add(band);
        return out;
    }

    /** Whether a crevasse's walls run along the north and south edges. */
    public static boolean alongX(List<RidgePlanner.Band> walls) {
        List<ElevationPlan.Point> path = walls.get(0).path();
        return Math.abs(path.get(1).x() - path.get(0).x()) >= Math.abs(path.get(1).y() - path.get(0).y());
    }
}
