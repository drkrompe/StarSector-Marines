package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * A town on a world nobody lives on in the open ({@code mapgen-nouns.md}, Habitat): the whole town sealed inside one
 * continuous wall, with a couple of airlocks in it, rather than a lock on every building's door. Inside the wall it is
 * an ordinary town of streets, blocks and courts; outside it is bare ground, with no outlying places.
 *
 * <p>The wall is the place's {@link Precinct.Boundary#WALLED boundary}, drawn on the outline of what grew like a
 * garrison's, so the town is a zoned precinct that is walled; that pairing exists for nothing else.
 */
public final class SealedTown {

    /**
     * The sealed wall: two locks, where its two widest roads meet it, and no emplacements of its own, since it is where
     * people live rather than a garrison. As hard to cut through as a garrison's wall.
     */
    public static final Fortification LOCKS = new Fortification(2, 240);

    /** Cells in from the wall a lock's inner door stands: the length of its chamber. */
    public static final int LOCK_DEPTH = 3;

    private SealedTown() { }

    /** Whether the town on this world is sealed: wherever its habitat is enclosed (toxic, airless, irradiated). */
    public static boolean on(TargetProfile profile) {
        return HabitatZoning.forProfile(profile).enclosed();
    }

    /** The world's town, sealed. Its character is decided with every other place's, after placement. */
    static Precinct settlement(int x, int y, GrownTrunkPlan.Profile growth) {
        return new Precinct("settlement", x, y, growth, null, Precinct.Boundary.WALLED, LOCKS);
    }

    /**
     * The door lines of each lock, to be marked: the gate itself, the outer door in the wall, and the inner door
     * {@link #LOCK_DEPTH} cells straight in from each of its cells, where the road still runs. The chamber between
     * them is the road's.
     *
     * @param gates the wall's open gates
     * @param claim the place claim, {@code who} where it is this place's
     * @param road  which place's road each cell is, {@link GrownTrunkPlan#UNOWNED} where it is none
     */
    public static List<int[]> lockFloors(List<PrecinctBoundary.Gate> gates, int[][] claim, int[][] road, int who) {
        int width = claim.length, height = claim[0].length;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<int[]> out = new ArrayList<>();
        for (PrecinctBoundary.Gate gate : gates) {
            for (int[] c : gate.cells()) {
                out.add(c);
                for (int[] s : sides) {
                    // The way in: the opposite of the side the place's ground ends on.
                    int nx = c[0] + s[0], ny = c[1] + s[1];
                    boolean outside = nx < 0 || ny < 0 || nx >= width || ny >= height || claim[nx][ny] != who;
                    if (!outside) continue;
                    int x = c[0] - LOCK_DEPTH * s[0], y = c[1] - LOCK_DEPTH * s[1];
                    if (x >= 0 && y >= 0 && x < width && y < height && claim[x][y] == who
                            && road[x][y] != GrownTrunkPlan.UNOWNED) {
                        out.add(new int[]{x, y});
                    }
                    break;
                }
            }
        }
        return out;
    }
}
