package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * Stand-in hull sites on open ground, until the crash-site ground fills {@link FallenHullSite} from its hull place.
 * The hull lies at the map's west end, its mouth facing east; the lattice runs east from it in a row with a branch,
 * the points {@link #SPACING} apart, and a haul road runs out along the row.
 */
final class FallenHullFixtures {

    static final int WIDTH = 120;
    static final int HEIGHT = 64;
    static final int ROW_Y = 32;
    static final int SPACING = 20;
    static final FallenHullSite.Rect HULL = new FallenHullSite.Rect(4, 24, 16, 40);
    static final int FORGE_X = 10;
    static final int MOUTH_X = 20;
    /** The hull zone's reach off the hull's plates: the ground east to x 28, short of point 0's. */
    static final int REACH = 12;

    /** Points 0, 1, 2 east along the row; point 3 north of point 0. */
    static final List<ClaimLattice.Point> LATTICE = List.of(
            new ClaimLattice.Point(0, 36, ROW_Y), new ClaimLattice.Point(1, 36 + SPACING, ROW_Y),
            new ClaimLattice.Point(2, 36 + SPACING * 2, ROW_Y), new ClaimLattice.Point(3, 36, ROW_Y + SPACING));

    private FallenHullFixtures() {
    }

    static int[] road() {
        int[] road = new int[2 * 8];
        for (int i = 0; i < 8; i++) {
            road[i * 2] = MOUTH_X + i * 10;
            road[i * 2 + 1] = ROW_Y;
        }
        return road;
    }

    static FallenHullSite site(List<FallenHullSite.StartingStake> stakes, List<FallenHullSite.Pile> salvage,
                               List<FallenHullSite.Posting> walkers, float feedstock) {
        return new FallenHullSite(HULL, FORGE_X, ROW_Y, MOUTH_X, ROW_Y, REACH, LATTICE, SPACING, List.of(road()),
                stakes, salvage, walkers, List.of(new int[] {MOUTH_X, ROW_Y}), feedstock, List.of(HULL));
    }
}
