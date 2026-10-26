package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;

/**
 * The ground of a fort or an objective, whose walls stand up to the swarm longer than a house's
 * ({@code combat-durability-nouns.md}, Fortified works).
 *
 * <p>A redoubt's wall, a colony compound's curtain and an objective's set piece are built to be held:
 * a Brute that tore through one as fast as through a house wall was inside before the company could get
 * there. So every cell of such a place carries {@link CellTag#WORKS}, and a swarm strain's structural
 * hit on it (a blow, a charge's ram, the bowl through a barricade) counts for
 * {@link #SWARM_TOUGHNESS} times less. The wall keeps its hit points, so it is still light enough for
 * a Brute to choose to break, and everything else's damage (a demolition charge, a shell, fire) is
 * unchanged; ordinary buildings carry no mark and fall as fast as ever.
 */
public final class FortifiedWorks {

    /**
     * How many of a swarm strain's structural hits a works cell takes for one on a house wall. Four:
     * a Brute alone on a redoubt's 120 hit point wall takes about twenty-three seconds of blows rather
     * than five, time for a squad to reach a wall it is called to. The owner's pick over two and six to eight.
     */
    public static final int SWARM_TOUGHNESS = 4;

    private FortifiedWorks() {}

    /** Marks every cell of the rectangle, corners inclusive and clipped to the map, as works ground. */
    public static void mark(NavigationGrid grid, int left, int top, int right, int bottom) {
        for (int y = Math.min(top, bottom); y <= Math.max(top, bottom); y++) {
            for (int x = Math.min(left, right); x <= Math.max(left, right); x++) {
                grid.markWorks(x, y);
            }
        }
    }

    /** Whether a swarm strain's hit on this cell, or on its {@code edge} when set, lands on works. */
    public static boolean isWorks(NavigationGrid grid, int x, int y, Direction edge) {
        if (grid.hasTag(x, y, CellTag.WORKS)) return true;
        return edge != null && grid.hasTag(x + edge.dx, y + edge.dy, CellTag.WORKS);
    }

    /** What a swarm strain's structural hit of {@code amount} on this cell or edge does: at least one point. */
    public static int swarmDamage(NavigationGrid grid, int x, int y, Direction edge, int amount) {
        if (amount <= 0 || !isWorks(grid, x, y, edge)) return amount;
        return Math.max(1, Math.round(amount / (float) SWARM_TOUGHNESS));
    }
}
