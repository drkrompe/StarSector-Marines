package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Gives each squad its own way into a zone, so a force ordered onto one
 * target does not file through one door.
 *
 * <p>Every squad sent to a zone used to plan the identical hop sequence: one
 * breadth-first path, with the doorway zones on it skipped, and each hop aimed
 * at the middle cell of its room. So the route in was whichever door the
 * search met first, and twenty-nine squads sharing a target shared a door.
 * Two things make it per squad. The search breaks ties between equally short
 * zone paths by a squad-specific rotation of each zone's portals, so squads
 * choose different doors where the geometry offers several. And a hop that
 * enters through a skipped doorway aims at the cell just inside <em>that</em>
 * door, because aiming at the room's middle lets the cell-level pathfinder
 * walk the squad back to whichever door is nearest.
 *
 * <p>Costs nothing per tick: both choices are made once, when the plan is
 * synthesized, and the existing plan stickiness holds them across replans.
 * {@code -Dbattle.squad.apertureSpread=true} turns it on; it is off while the
 * effect is being measured apart from everything else.
 */
public final class ApertureSpread {

    public static final String PROPERTY = "battle.squad.apertureSpread";

    /** Read once, like the other measured levers. */
    static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty(PROPERTY, "false"));

    /** The legacy search order. */
    public static final int NO_VARIANT = 0;

    private ApertureSpread() { }

    /** This squad's tie-break variant; {@link #NO_VARIANT} when disabled. */
    public static int variant(Squad squad) {
        return ENABLED && squad != null ? squad.id + 1 : NO_VARIANT;
    }

    /** Where in a list of {@code size} portals the search starts. */
    static int startIndex(int variant, int size) {
        return size <= 0 || variant == NO_VARIANT
                ? 0 : Math.floorMod(variant - 1, size);
    }

    /**
     * The cell just inside {@code zone} behind the doorway the path came
     * through, or {@code null} when the path did not arrive by a doorway zone
     * (or the variant is {@link #NO_VARIANT}) and the room's own middle
     * applies.
     */
    public static int[] cellBehindDoorway(NavigationZone zone,
                                          NavigationZone previous,
                                          int variant, ZoneGraph graph,
                                          NavigationGrid grid) {
        if (variant == NO_VARIANT || zone == null || previous == null
                || previous.getCellCount() != 1) return null;
        int doorway = previous.getCellIndices()[0];
        if (!grid.isDoorwayAt(doorway)) return null;
        int width = grid.getWidth();
        int x = doorway % width;
        int y = doorway / width;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] step : steps) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (nx < 0 || ny < 0 || nx >= width || ny >= grid.getHeight()) continue;
            if (graph.zoneIdAt(nx, ny) == zone.getZoneId()) {
                return new int[] {nx, ny};
            }
        }
        return null;
    }
}
