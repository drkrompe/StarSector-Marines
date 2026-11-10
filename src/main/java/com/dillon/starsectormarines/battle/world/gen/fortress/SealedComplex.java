package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.Habitat;
import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayDeque;

/**
 * A place whose people live inside it because the world outside will not have them ({@code mapgen-nouns.md},
 * Habitat): on a shielded world every programmed place is one sealed complex rather than buildings in a yard. Its
 * rooms are packed as a hull ({@link FortressInterior#packSealed}); whatever ground inside its wall no room took is
 * hall under the same roof; its wall is the shielding, poured as brutalist concrete; and where a road meets the wall
 * the opening is an airlock, its floor marked. Outside the wall there is only bare ground.
 *
 * <p>The halls, rooms and locks are one building, so the roof runs unbroken over the whole complex and lifts as one
 * when a squad is inside it.
 */
public final class SealedComplex {

    /** The complex's outer shielding. */
    public static final String SKIN = "installation.dark-concrete-wall@brutalist";

    /** What the complex's halls are floored with. */
    static final String HALL_FLOOR = "installation.concrete-floor";

    private SealedComplex() { }

    /**
     * Whether the places on this world are sealed complexes: where hard radiation keeps everyone inside. The plan marks
     * their programs {@link FortressProgram#sealedComplex() sealed}; everything after reads the program.
     */
    public static boolean on(TargetProfile profile) {
        return HabitatZoning.forProfile(profile) == Habitat.SHIELDED;
    }

    /** How far in from the place's edge its roads stay roads: the stub each airlock opens onto. */
    static final int GATE_STUB = 4;

    /**
     * Leaves only the stubs of the place's roads where they reach its edge as circulation, and gives the rest of its
     * road over to building. Inside a sealed complex nothing is a street: the packer wedges the rooms together and cuts
     * the halls between them from the gates in, as a deck's passages are cut, rather than standing rooms along roads.
     *
     * @param buildable   the place's buildable mask, widened in place
     * @param circulation the place's circulation mask, narrowed in place
     */
    public static void gateStubsOnly(boolean[][] buildable, boolean[][] circulation, int[][] own, int who) {
        int width = own.length, height = own[0].length;
        int[][] depth = depthIn(own, who, width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (circulation[x][y] && depth[x][y] > GATE_STUB) {
                    circulation[x][y] = false;
                    buildable[x][y] = true;
                }
            }
        }
    }

    /** Each of the place's cells' step count in from its edge; the edge itself is 1. */
    static int[][] depthIn(int[][] own, int who, int width, int height) {
        int[][] depth = new int[width][height];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (own[x][y] == who && edge(own, who, x, y, width, height)) {
                    depth[x][y] = 1;
                    queue.add(new int[]{x, y});
                }
            }
        }
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] s : sides) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (own[nx][ny] != who || depth[nx][ny] != 0) continue;
                depth[nx][ny] = depth[c[0]][c[1]] + 1;
                queue.add(new int[]{nx, ny});
            }
        }
        return depth;
    }

    private static boolean edge(int[][] own, int who, int x, int y, int width, int height) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] s : sides) {
            int nx = x + s[0], ny = y + s[1];
            if (nx < 0 || ny < 0 || nx >= width || ny >= height || own[nx][ny] != who) return true;
        }
        return false;
    }

    /**
     * Roofs the place's own ground: every cell of it a body can stand on that no room claimed becomes indoor hall,
     * part of the complex's one building. Run after packing and before the wall, so the ring the wall is stamped on is
     * already the complex's ground.
     *
     * @param own the place claim, {@code who} where it is this place's
     */
    public static void roofHalls(GenContext ctx, int[][] own, int who) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        int floor = topology.surfaceIndex(HALL_FLOOR);
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (own[x][y] != who || !grid.isWalkable(x, y) || topology.isWater(x, y)) continue;
                if (topology.getBuildingKindHint(x, y) != null) continue;
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setSurface(x, y, floor);
                topology.setBuildingKindHint(x, y, BuildingKind.FORTIFIED);
            }
        }
    }

    /**
     * Skins the walled complex: its wall and every room wall inside it in shielding concrete, and each opening in its
     * wall an airlock with a marked floor. Run after the wall is stamped.
     */
    public static void skin(GenContext ctx, int[][] own, int who) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        int skin = topology.surfaceIndex(SKIN);
        int shield = topology.surfaceIndex(Habitat.SHIELD_WALL);
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (own[x][y] != who) continue;
                String id = topology.getSurfaceId(x, y);
                if (InstallationWalls.PERIMETER.equals(id)) topology.setSurface(x, y, skin);
                else if (InstallationWalls.BUILDING.equals(id)) topology.setSurface(x, y, shield);
                if (grid.isWalkable(x, y) && !topology.isWall(x, y) && edge(own, who, x, y, ctx.width, ctx.height)) {
                    topology.setGroundKind(x, y, GroundKind.STRIPED);
                    topology.setBuildingKindHint(x, y, BuildingKind.FORTIFIED);
                }
            }
        }
    }

}
