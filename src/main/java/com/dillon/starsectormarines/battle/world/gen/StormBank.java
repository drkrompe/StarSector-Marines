package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.elevation.PlateauPlanner;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import java.util.ArrayList;
import java.util.List;

/**
 * The earth bank a desert-like storm world's town throws up against its weather ({@code mapgen-nouns.md}, Climate):
 * a graded berm along the map's windward edge, ramped from the town's side, standing between the town and the wind.
 *
 * <p>It is planned before anything grows, as held ground on the plan's landscape ({@code Landscape.withHeld}): the
 * town's places, claims, arms and fields grow round it, so it always has room, and the outdoor landscape pass raises
 * it later through {@link RidgePlanner}, which breaks it into a pass wherever a road, a building, a landing or an
 * access route crosses and proves the ground on both sides stays one. So it is cover and a height to hold, never a
 * seal. The town makes room for it: a plan built against it sets its places {@link #leeward leeward} of the ground it
 * holds, so no place is seeded or claimed on the bank.
 *
 * <p>A desert or a dust-blown rock under {@link Climate.Hazard#STORMS} banks ({@link #raises}). A frozen world's
 * blizzards would want snow fences, not earth; a living world has no dust to keep out.
 */
public final class StormBank {

    /**
     * Cells kept clear along every map edge by the landscape pass's reservations (the outer four cells, and two
     * more round each): no ridge, face or apron may stand on them.
     */
    static final int EDGE_RESERVED = 7;
    /**
     * Cells from the windward map edge to the bank's outer face: past the edge reservation, with room outside the
     * face for the planner's apron ({@code PlateauPlanner.FACE_WIDTH + SOUTH_FACE + 1}), which must be free ground,
     * and a cell for the planner's half-cell steps from the band's centre, which round outward on the high edges.
     */
    public static final int OFFSET = EDGE_RESERVED + PlateauPlanner.FACE_WIDTH + PlateauPlanner.SOUTH_FACE + 2;
    /** The bank's width through, cells: the narrowest berm the ridge planner grades a walkable top on (8 is refused). */
    public static final int THICKNESS = 10;
    /** Ramp width, cells. */
    public static final int RAMP = 5;
    /** Cells kept between the bank's ends and the map's corners. */
    static final int END = OFFSET;
    /** Margin round the band the plan holds from growth, as the precinct reservations hold any held ridge. */
    static final int HELD_MARGIN = 3;
    /**
     * Half a planned pass, cells: the gap left in the bank, before anything grows, round a road the plan already
     * knows will leave the map through the bank's ground. Growth lays that road (a place's arm out to the edge, which
     * the works' feeder then runs along), so the gap is wide enough for a primary arm once the held margin and the
     * ramps either side are taken off it, and for the slack between the place's seed and its claim's middle, where
     * the feeder leaves.
     */
    public static final int PASS_HALF = 20;
    /**
     * Half the ground a planned pass leaves open to the edge: the gap less what the bank holds past each end of a
     * stretch (its graded end, ramp and held margin reach some nine cells beyond it). A primary arm and its shoulders.
     */
    public static final int OPEN_HALF = 8;
    /**
     * Cells past the held ground that the kept strips run on the lee: the planner's apron reaches a cell past the
     * held margin, and a road the town lays beside held ground reserves two cells round it, so without these the
     * apron's last row is reserved and the whole stretch is refused.
     */
    static final int LEE_KEPT = 3;
    /** Open ground between everything the bank holds and the nearest place's region, cells. */
    public static final int STANDOFF = 6;

    private StormBank() { }

    /** Whether a settlement on this world banks against its storms: a desert or a rock, with storms. */
    public static boolean raises(TargetProfile profile) {
        if (profile == null || !profile.climate().has(Climate.Hazard.STORMS)) return false;
        return profile.surface() == SurfacePalette.ARID || profile.surface() == SurfacePalette.ROCK;
    }

    /** The bank on a {@code width} x {@code height} map with the wind from {@code wind}: one straight graded band. */
    public static RidgePlanner.Band band(StormWind.Side wind, int width, int height) {
        int length = wind.alongX() ? width : height;
        return piece(wind, width, height, END, length - 1 - END);
    }

    /**
     * The bank with a planned pass round each of {@code passes}: positions along the windward edge (x for a north
     * or south wind, y for an east or west one) where a road the plan already knows leaves the map. Each pass is
     * {@link #PASS_HALF} either side of its road; a stretch left too short to raise is left out.
     */
    public static List<RidgePlanner.Band> bands(StormWind.Side wind, int width, int height, List<Integer> passes) {
        int length = wind.alongX() ? width : height;
        boolean[] gap = new boolean[length];
        for (int at : passes) {
            for (int a = Math.max(0, at - PASS_HALF); a <= Math.min(length - 1, at + PASS_HALF); a++) gap[a] = true;
        }
        List<RidgePlanner.Band> out = new ArrayList<>();
        int start = -1;
        for (int a = END; a <= length - END; a++) {
            boolean open = a < length - END && !gap[a];
            if (open && start < 0) start = a;
            if (!open && start >= 0) {
                if (a - start >= MIN_LENGTH) out.add(piece(wind, width, height, start, a - 1));
                start = -1;
            }
        }
        return out;
    }

    /**
     * The ground windward of the bank, kept open before anything grows ({@code Landscape.withKept}): the strip from
     * the edge past the bank's lee apron the whole length of the edge, {@link #LEE_KEPT} beyond its held ground
     * (kept ground is never raised, and the bank is held besides), and past each end of the bank the corner out
     * as deep as the bank holds, so no place grows round the bank onto its windward side. The planned passes are
     * left open, so the roads through them still reach the edge.
     */
    public static List<Landscape.Track> windward(StormWind.Side wind, int width, int height, List<Integer> passes) {
        int length = wind.alongX() ? width : height;
        int corner = depth(wind, width, height), lee = corner + LEE_KEPT;
        boolean[] gap = new boolean[length];
        for (int at : passes) {
            for (int a = Math.max(0, at - OPEN_HALF); a <= Math.min(length - 1, at + OPEN_HALF); a++) gap[a] = true;
        }
        List<Landscape.Track> out = new ArrayList<>();
        int start = -1;
        for (int a = 0; a <= length; a++) {
            boolean open = a < length && !gap[a];
            if (open && start < 0) start = a;
            if (!open && start >= 0) {
                out.add(strip(wind, width, height, start, a - 1, lee));
                start = -1;
            }
        }
        out.add(strip(wind, width, height, 0, END - 1, corner));
        out.add(strip(wind, width, height, length - END, length - 1, corner));
        return out;
    }

    /** The box from the windward edge {@code depth} cells in, from {@code a0} to {@code a1} along it. */
    private static Landscape.Track strip(StormWind.Side wind, int width, int height, int a0, int a1, int depth) {
        if (wind.alongX()) {
            int y0 = wind.low() ? 0 : height - depth, y1 = wind.low() ? depth - 1 : height - 1;
            return new Landscape.Track(a0, y0, a1, y1);
        }
        int x0 = wind.low() ? 0 : width - depth, x1 = wind.low() ? depth - 1 : width - 1;
        return new Landscape.Track(x0, a0, x1, a1);
    }

    /** The shortest stretch of bank worth planning, cells: what the ridge planner raises at the bank's width. */
    static final int MIN_LENGTH = 2 * THICKNESS + 2 * 2;

    private static RidgePlanner.Band piece(StormWind.Side wind, int width, int height, int a0, int a1) {
        int toward = wind.low() ? 1 : -1;
        int near = OFFSET, far = OFFSET + THICKNESS - 1;
        RidgePlanner.Band band;
        if (wind.alongX()) {
            int y0 = wind.low() ? near : height - 1 - far, y1 = wind.low() ? far : height - 1 - near;
            band = RidgePlanner.Band.rect(a0, y0, a1, y1, true, toward, RAMP);
        } else {
            int x0 = wind.low() ? near : width - 1 - far, x1 = wind.low() ? far : width - 1 - near;
            band = RidgePlanner.Band.rect(x0, a0, x1, a1, false, toward, RAMP);
        }
        return band.berm();
    }

    /**
     * The bank's ground on a {@code width} x {@code height} map, indexed {@code [x][y]}: every cell from the windward
     * edge to {@link #depth} in, its passes included. What reaches the edge across it (a landing pad's approach) stops
     * here rather than reserving the bank away.
     */
    public static boolean[][] ground(StormWind.Side wind, int width, int height) {
        int depth = depth(wind, width, height);
        boolean[][] out = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int in = wind.alongX() ? (wind.low() ? y : height - 1 - y) : (wind.low() ? x : width - 1 - x);
                out[x][y] = in < depth;
            }
        }
        return out;
    }

    /** How far in from the windward edge the bank holds ground, cells: {@link #reach} without its open standoff. */
    public static int depth(StormWind.Side wind, int width, int height) {
        return reach(wind, width, height) - STANDOFF;
    }

    /**
     * How far in from the windward edge the bank's ground runs, cells: everything {@link RidgePlanner} may raise or
     * ramp for it, held with its margin, and {@link #STANDOFF} more of open ground before the town.
     */
    public static int reach(StormWind.Side wind, int width, int height) {
        boolean[][] held = RidgePlanner.heldGround(List.of(band(wind, width, height)), width, height,
                HELD_MARGIN);
        int deepest = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!held[x][y]) continue;
                int in = wind.alongX() ? (wind.low() ? y : height - 1 - y) : (wind.low() ? x : width - 1 - x);
                deepest = Math.max(deepest, in + 1);
            }
        }
        return deepest + STANDOFF;
    }

    /**
     * A place's region with the bank's ground taken off the windward side: the placement squeezed toward the lee by
     * {@link #reach}, so the regions keep their order and proportions and none reaches onto the bank.
     */
    public static MapPlacement leeward(MapPlacement at, StormWind.Side wind, int width, int height) {
        float share = (float) reach(wind, width, height) / (wind.alongX() ? height : width);
        float keep = 1f - share;
        if (wind.alongX()) {
            float y0 = wind.low() ? share + at.y0() * keep : at.y0() * keep;
            float y1 = wind.low() ? share + at.y1() * keep : at.y1() * keep;
            return new MapPlacement(at.x0(), y0, at.x1(), y1);
        }
        float x0 = wind.low() ? share + at.x0() * keep : at.x0() * keep;
        float x1 = wind.low() ? share + at.x1() * keep : at.x1() * keep;
        return new MapPlacement(x0, at.y0(), x1, at.y1());
    }

    /**
     * Whether a site whose company lands on the west when unmirrored should mirror across x, so its landing is never
     * on the windward side: a town turns its back to the wind, and the bank stands behind it or on a flank, never
     * across the company's way in. A wind along the landing's axis decides; one across it leaves the draw as it was.
     */
    public static boolean mirrorX(boolean drawn, StormWind.Side wind) {
        return switch (wind) {
            case WEST -> true;
            case EAST -> false;
            default -> drawn;
        };
    }
}
