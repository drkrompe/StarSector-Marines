package com.dillon.starsectormarines.battle.world.gen.precinct;

/**
 * Where one lane lies across the map, laterally.
 *
 * <p>A lane is the map's side of a command track, so the two have to agree about
 * which strip of ground they are talking about — a place seeded in the middle of
 * track 1 that the commanders classify into track 0 is resistance in the wrong
 * lane. The arithmetic is therefore the same arithmetic:
 * {@code ConquestTrackLayout} splits the lateral extent into {@code count}
 * contiguous runs by rounding each boundary up, and so does this.
 *
 * <p><b>Copied rather than shared, deliberately.</b> {@code ConquestTrackLayout}
 * lives in {@code battle.command}, which reads {@code battle.world.gen}; a
 * generator asking a commander where its places go would invert that. Two copies
 * of arithmetic drift, so {@code LaneTrackAgreementTest} asks both for every
 * cell of a map and fails when they disagree — the dependency stays one-way and
 * the agreement is pinned rather than hoped for.
 */
public final class LaneGeometry {

    private LaneGeometry() {
    }

    /** First lateral cell classified into {@code lane}. */
    public static int startInclusive(int lane, int count, int lateralExtent) {
        require(lane, count, lateralExtent);
        return ceilDiv(lane * lateralExtent, count);
    }

    /** Last lateral cell classified into {@code lane}. */
    public static int endInclusive(int lane, int count, int lateralExtent) {
        require(lane, count, lateralExtent);
        return ceilDiv((lane + 1) * lateralExtent, count) - 1;
    }

    /** The middle of {@code lane}'s own strip — where its ladder stands. */
    public static int centre(int lane, int count, int lateralExtent) {
        return (startInclusive(lane, count, lateralExtent)
                + endInclusive(lane, count, lateralExtent)) / 2;
    }

    /** Which lane a lateral coordinate falls in; {@code -1} for one off the map. */
    public static int laneForLateral(float lateral, int count, int lateralExtent) {
        if (!Float.isFinite(lateral) || lateral < 0f) return -1;
        if (lateral >= lateralExtent) return count - 1;
        return Math.min((int) (lateral * count / lateralExtent), count - 1);
    }

    private static void require(int lane, int count, int lateralExtent) {
        if (count <= 0) throw new IllegalArgumentException("lane count must be positive");
        if (lateralExtent <= 0) {
            throw new IllegalArgumentException("lateral extent must be positive");
        }
        if (lane < 0 || lane >= count) {
            throw new IllegalArgumentException("lane out of range: " + lane);
        }
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
