package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a laid layout keeps its promises on a finished map: every required
 * piece stands (whole or in part), every way left through a line is open
 * ground, and a line that closes any ground leaves at least one way through.
 * The one check a mission's acceptance and the story-map pools both read.
 */
public final class MissionLayoutCheck {

    private MissionLayoutCheck() {}

    /** What is wrong, one line per fault; empty when the layout holds. */
    public static List<String> problems(NavigationGrid grid, LaidLayout laid) {
        List<String> out = new ArrayList<>();
        for (Laid piece : laid.pieces()) {
            if (!piece.isLaid()) {
                if (piece.required()) out.add(piece.name() + " was not laid: " + piece.reason());
                continue;
            }
            if (piece.kind() != Laid.Kind.LINE) continue;
            boolean closes = false;
            for (int[] c : piece.cells()) closes |= !walkable(grid, c);
            boolean through = false;
            for (int[] gap : piece.gaps()) {
                if (walkable(grid, gap)) through = true;
                else out.add(piece.name() + " has a closed gap at " + gap[0] + "," + gap[1]);
            }
            if (closes && !through) out.add(piece.name() + " closes ground and leaves no way through");
        }
        return out;
    }

    private static boolean walkable(NavigationGrid grid, int[] c) {
        return grid.inBounds(c[0], c[1]) && grid.isWalkable(c[0], c[1]);
    }
}
