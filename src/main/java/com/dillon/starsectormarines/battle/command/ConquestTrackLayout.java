package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

/** Immutable, faction-neutral geometry for Conquest's lateral command tracks. */
public final class ConquestTrackLayout {

    public static final int DEFAULT_TRACK_COUNT = 3;

    private final TraversalAxis axis;
    private final int trackCount;
    private final int width;
    private final int height;
    private final int lateralExtent;
    private final int forwardExtent;

    public ConquestTrackLayout(TraversalAxis axis, int width, int height) {
        this(axis, width, height, DEFAULT_TRACK_COUNT);
    }

    public ConquestTrackLayout(TraversalAxis axis, int width, int height,
                               int trackCount) {
        if (axis == null) throw new IllegalArgumentException("axis is required");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("map dimensions must be positive");
        }
        if (trackCount <= 0) {
            throw new IllegalArgumentException("trackCount must be positive");
        }
        this.axis = axis;
        this.trackCount = trackCount;
        this.width = width;
        this.height = height;
        this.lateralExtent = axis == TraversalAxis.SOUTH_TO_NORTH ? width : height;
        this.forwardExtent = axis == TraversalAxis.SOUTH_TO_NORTH ? height : width;
    }

    public TraversalAxis axis() { return axis; }
    public int trackCount() { return trackCount; }
    public int width() { return width; }
    public int height() { return height; }
    public int lateralExtent() { return lateralExtent; }
    public int forwardExtent() { return forwardExtent; }

    public float lateralCoordinate(float x, float y) {
        return axis == TraversalAxis.SOUTH_TO_NORTH ? x : y;
    }

    public float forwardCoordinate(float x, float y) {
        return axis == TraversalAxis.SOUTH_TO_NORTH ? y : x;
    }

    public int trackForCell(int x, int y) {
        return trackForLateral(axis == TraversalAxis.SOUTH_TO_NORTH ? x : y);
    }

    public int trackForLateral(float lateral) {
        if (!Float.isFinite(lateral) || lateral < 0f) return -1;
        if (lateral >= lateralExtent) return trackCount - 1;
        return Math.min((int) (lateral * trackCount / lateralExtent),
                trackCount - 1);
    }

    /** Canonical marine advance progress: zero at the LZ edge, one at the keep edge. */
    public float assaultProgress(float x, float y) {
        float forward = forwardCoordinate(x, y);
        if (forwardExtent <= 1) return 0f;
        return Math.max(0f, Math.min(1f, forward / (forwardExtent - 1f)));
    }

    /** First integer cell classified into {@code track}. */
    public int lateralStartInclusive(int track) {
        requireTrack(track);
        return ceilDiv(track * lateralExtent, trackCount);
    }

    /** Last integer cell classified into {@code track}. */
    public int lateralEndInclusive(int track) {
        requireTrack(track);
        return ceilDiv((track + 1) * lateralExtent, trackCount) - 1;
    }

    public int lateralCenterCell(int track) {
        return (lateralStartInclusive(track) + lateralEndInclusive(track)) / 2;
    }

    public int cellX(int lateral, int forward) {
        return axis == TraversalAxis.SOUTH_TO_NORTH ? lateral : forward;
    }

    public int cellY(int lateral, int forward) {
        return axis == TraversalAxis.SOUTH_TO_NORTH ? forward : lateral;
    }

    private void requireTrack(int track) {
        if (track < 0 || track >= trackCount) {
            throw new IllegalArgumentException("track out of range: " + track);
        }
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
