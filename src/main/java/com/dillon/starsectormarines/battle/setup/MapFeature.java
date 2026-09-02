package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Something a generated map either has or does not, that a mission may need in
 * order to be the battle it claims to be.
 *
 * <p>Generation is a long chain of passes that each decline politely. A ward
 * that cannot fit an airfield builds none; a claim that comes up short takes a
 * smaller lot; a stamper with nowhere to stand emits nothing. Every one of
 * those is the right local decision, and none of them knows what the mission
 * was promised. The garrison airfield was missing from a quarter of live
 * conquest battles for exactly that reason: the ward skipped it, and the only
 * symptom was an enemy that flew its reinforcements in from off map.
 *
 * <p>So the requirement is stated where it is owned — by the mission, not by
 * the pass that happens to satisfy it — and checked against the finished map.
 * A feature is a question about the <em>output</em>, deliberately: it does not
 * care which pass produced it, and it keeps working when one is replaced.
 *
 * @see MissionMapRequirements
 */
public enum MapFeature {

    /** Somewhere for the defence to live — the nodes garrison squads deploy to. */
    DEFENDER_GARRISON("a defender garrison") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.tacticalMap != null
                    && map.tacticalMap.all().stream()
                        .anyMatch(node -> node.defaultGuard == Faction.DEFENDER);
        }
    },

    /** The compound an assault is won by taking. */
    CENTRAL_KEEP("a defender command post") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.tacticalMap != null
                    && map.tacticalMap.all().stream()
                        .anyMatch(node -> node.kind == TacticalNode.Kind.COMMAND_POST
                                && node.defaultGuard == Faction.DEFENDER);
        }
    },

    /**
     * Berths the defender's air arm flies from, and that an attacker can burn
     * to stop it.
     */
    GARRISON_AIRFIELD("a garrison airfield") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.landingPads.stream()
                    .anyMatch(pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD);
        }
    },

    /** Somewhere for the assault to arrive. */
    MARINE_LANDING_ZONE("a marine landing zone") {
        @Override
        public boolean presentIn(MapResult map) {
            return map != null && map.marineSpawnX >= 0 && map.marineSpawnY >= 0;
        }
    },

    /**
     * A way to walk to every compound from where the marines come ashore.
     *
     * <p>Conquest is won by flipping every compound, so one nobody can walk
     * into is an unwinnable mission rather than a cosmetic defect. The law was
     * stated in {@code precincts.md} and asserted in
     * {@code ConquestOnPrecinctsTest} from the day lanes put nine more walled
     * places on the map, and nothing enforced it at generation time: a seed
     * whose gates all closed the wrong way shipped, and the only symptom was a
     * battle that could not be finished.
     *
     * <p><b>Reached means some walkable cell of the footprint, not the
     * anchor.</b> A tactical-node anchor carries no promise of standing on open
     * floor — the trap {@code mapgen-nouns.md} already records — so an anchor
     * test would fail maps that are perfectly playable.
     *
     * <p>One flood of the walkable graph per generated map, which is a few
     * milliseconds against the seconds generation already costs, and it buys a
     * re-roll instead of a battle nobody can win.
     */
    WALKABLE_COMPOUNDS("a way to walk to every compound") {
        @Override
        public boolean presentIn(MapResult map) {
            if (map == null || map.grid == null || map.tacticalMap == null) return false;
            NavigationGrid grid = map.grid;
            if (map.marineSpawnX < 0 || map.marineSpawnY < 0) return false;
            boolean[][] seen = floodFrom(grid, map.marineSpawnX, map.marineSpawnY);
            for (TacticalNode node : map.tacticalMap.all()) {
                if (!CompoundService.isCompound(node.kind)) continue;
                if (!anyCellSeen(seen, grid, node)) return false;
            }
            return true;
        }
    };

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Every walkable cell a marine can reach from where the force lands. */
    private static boolean[][] floodFrom(NavigationGrid grid, int fromX, int fromY) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        boolean[][] seen = new boolean[width][height];
        if (fromX >= width || fromY >= height) return seen;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{fromX, fromY});
        seen[fromX][fromY] = true;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : STEPS) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    /** Whether the flood touched any cell of this node's footprint. */
    private static boolean anyCellSeen(boolean[][] seen, NavigationGrid grid,
                                       TacticalNode node) {
        int left = Math.max(0, Math.min(node.left, node.right));
        int right = Math.min(grid.getWidth() - 1, Math.max(node.left, node.right));
        int bottom = Math.max(0, Math.min(node.top, node.bottom));
        int top = Math.min(grid.getHeight() - 1, Math.max(node.top, node.bottom));
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                if (seen[x][y]) return true;
            }
        }
        return false;
    }

    /** How this reads in the message a failed requirement produces. */
    public final String description;

    MapFeature(String description) {
        this.description = description;
    }

    /** Whether the finished map has it. */
    public abstract boolean presentIn(MapResult map);
}
