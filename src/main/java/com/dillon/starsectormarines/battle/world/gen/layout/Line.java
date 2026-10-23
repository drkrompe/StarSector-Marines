package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * A line of works along a path of world cells ({x, y} vertices), broken where
 * a road crosses it and {@code roadGap} cells either side, so roads always
 * pass. {@code frontward} is the world direction the line faces; a wire course
 * stands in front of it, opened every {@code funnelEvery} cells (none at 0).
 *
 * <p>{@code depth} is how many rows deep a trench is dug where the ground
 * allows (the extra rows behind the course); a trench takes a sandbag parapet
 * on its front lip. A run long enough takes a nest at each end when
 * {@code nestsAtEnds}, and one every {@code nestEvery} cells between (none at
 * 0). {@code barricadeGaps} throws a barricade across each road gap, leaving
 * its middle open.
 */
public record Line(String name, List<int[]> path, LineStyle style, int[] frontward, int roadGap,
                   int funnelEvery, boolean nestsAtEnds, boolean required, int depth, int nestEvery,
                   boolean barricadeGaps) implements Piece {

    public Line {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a line needs a name");
        if (path == null || path.size() < 2) throw new IllegalArgumentException("a line needs two vertices: " + name);
        path = List.copyOf(path);
        if (frontward == null || frontward.length != 2) throw new IllegalArgumentException("a line faces somewhere: " + name);
        if (roadGap < 0) throw new IllegalArgumentException("negative road gap: " + name);
        if (depth < 1) throw new IllegalArgumentException("a line is at least one row deep: " + name);
        if (nestEvery < 0) throw new IllegalArgumentException("negative nest pitch: " + name);
    }

    /** A one-row line with nests at its run ends only and its gaps left bare. */
    public Line(String name, List<int[]> path, LineStyle style, int[] frontward, int roadGap,
                int funnelEvery, boolean nestsAtEnds, boolean required) {
        this(name, path, style, frontward, roadGap, funnelEvery, nestsAtEnds, required, 1, 0, false);
    }
}
