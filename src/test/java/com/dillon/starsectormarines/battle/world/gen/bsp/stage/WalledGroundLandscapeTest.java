package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.NaturalChasm;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No landscape feature stands inside a wall ({@code OutdoorLandscapeStage.reserveWalled}). */
class WalledGroundLandscapeTest {

    private static final int W = 160;
    private static final int H = 100;
    /** The walled place's square: across the middle of the map, where a drop running edge to edge would cross. */
    private static final int X0 = 60, X1 = 100, Y0 = 30, Y1 = 70;

    private static List<Precinct> places() {
        GrownTrunkPlan.Profile town = GrownTrunkPlan.Profile.town();
        return List.of(Precinct.settlement("town", 20, 20, town),
                Precinct.garrison("garrison", 80, 50, town, FortressProgram.cantonment(4, 1, 0)));
    }

    /** The town claims everything but the garrison's square. */
    private static int[][] claim() {
        int[][] claim = new int[W][H];
        for (int x = 0; x < W; x++) {
            Arrays.fill(claim[x], 0);
            for (int y = Y0; y <= Y1; y++) if (x >= X0 && x <= X1) claim[x][y] = 1;
        }
        return claim;
    }

    @Test
    void aWalledPlacesWholeClaimIsReservedAndNoOpenPlacesIs() {
        boolean[] reserved = new boolean[W * H];
        OutdoorLandscapeStage.reserveWalled(reserved, places(), claim(), W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            boolean walled = x >= X0 && x <= X1 && y >= Y0 && y <= Y1;
            assertEquals(walled, reserved[y * W + x], "cell " + x + "," + y);
        }
    }

    @Test
    void aNaturalDropRunsRoundAWalledPlaceNeverThroughIt() {
        boolean[] reserved = new boolean[W * H];
        OutdoorLandscapeStage.reserveWalled(reserved, places(), claim(), W, H);
        int cut = 0;
        for (int seed = 1; seed <= 8; seed++) {
            NaturalChasm.Cut drop = NaturalChasm.plan(W, H, seed % 2 == 0, .5f, i -> !reserved[i], i -> true, seed);
            if (drop == null) continue;
            cut++;
            for (int i = 0; i < drop.drop().length; i++) {
                if (drop.drop()[i]) assertFalse(reserved[i], "seed " + seed + ": the drop is inside the wall at "
                        + i % W + "," + i / W);
            }
        }
        assertTrue(cut > 0, "a drop is cut on some seed");
    }
}
