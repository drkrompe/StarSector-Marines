package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * A line of works along a path of world cells ({x, y} vertices), broken where
 * a road crosses it and {@code roadGap} cells either side, so roads always
 * pass. {@code frontward} is the world direction the line faces; a wire course
 * stands in front of it, opened every {@code funnelEvery} cells (none at 0).
 */
public record Line(String name, List<int[]> path, LineStyle style, int[] frontward, int roadGap,
                   int funnelEvery, boolean nestsAtEnds, boolean required) implements Piece {

    public Line {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a line needs a name");
        if (path == null || path.size() < 2) throw new IllegalArgumentException("a line needs two vertices: " + name);
        path = List.copyOf(path);
        if (frontward == null || frontward.length != 2) throw new IllegalArgumentException("a line faces somewhere: " + name);
        if (roadGap < 0) throw new IllegalArgumentException("negative road gap: " + name);
    }
}
