package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a front is, asked of {@link FrontDepth} directly.
 *
 * <p>Two things have to hold for the reinforcement layer to be able to stop
 * reading biomes. The stock recipe's front must come out of the biome map
 * unchanged — the same ground in the same band, cell for cell — or a Conquest
 * battle that already worked starts dispatching somewhere else. And a precinct
 * map's front must actually be a depth: band 0 exactly the objective's claim,
 * and the bands beyond it rising with distance rather than merely differing.
 */
public class FrontDepthTest {

    private static final int W = 280;
    private static final int H = 168;

    private static FrontDepth stockFront() {
        return FrontDepth.fromBiomes(new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42)));
    }

    @Test
    public void stockFrontReproducesTheBiomeBandingCellForCell() {
        BiomeMap biomes = new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42));
        FrontDepth front = FrontDepth.fromBiomes(biomes);

        assertEquals(4, front.bands());
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                assertEquals(expectedBand(biomes.biomeAt(x, y)), front.bandAt(x, y),
                        "band at (" + x + "," + y + ")");
            }
        }
    }

    private static int expectedBand(BiomeKind biome) {
        switch (biome) {
            case FORTRESS_DISTRICT: return 0;
            case CITY:              return 1;
            case PORT:              return 2;
            case BEACH:             return 3;
            case OUTSKIRTS:         return 3;
        }
        throw new IllegalStateException("Unhandled biome " + biome);
    }

    @Test
    public void stockFrontNamesTheDistrictsTheCommsOfficerAlreadySaid() {
        FrontDepth front = stockFront();
        assertEquals("fortress district", front.bandName(0));
        assertEquals("city district", front.bandName(1));
        assertEquals("port district", front.bandName(2));
        assertEquals("beachhead", front.bandName(3));
    }

    @Test
    public void stockFrontsObjectiveCentreSitsInTheFortressDistrict() {
        FrontDepth front = stockFront();
        int[] centre = front.objectiveCentre();
        assertEquals(0, front.bandAt(centre[0], centre[1]),
                "the centroid of band 0 is itself band 0 on a contiguous strip");
        assertTrue(centre[1] > H * 3 / 4,
                "the fortress end of a SOUTH_TO_NORTH axis is the high-y end");
    }

    /**
     * The step is on the vector's dominant axis, so a target on the objective's
     * own column moves purely along the axis — which is the old rally shift
     * exactly, and the reason the stock recipe keeps its behaviour.
     */
    @Test
    public void rearwardFromBandTwoLandsOnTheOldAxisShift() {
        FrontDepth front = stockFront();
        int[] centre = front.objectiveCentre();
        int y = H * 25 / 100;
        assertEquals(2, front.bandAt(centre[0], y), "precondition: that cell is the port band");

        int[] rear = front.rearward(centre[0], y, 8);
        assertEquals(centre[0], rear[0]);
        assertEquals(y + 8, rear[1], "eight cells toward the fortress end");
        assertTrue(front.bandAt(rear[0], rear[1]) <= 2, "and no further from the objective than it started");
    }

    @Test
    public void rearwardTurnsRoundOnTheFarSideOfTheObjective() {
        FrontDepth front = stockFront();
        int[] centre = front.objectiveCentre();

        int[] rear = front.rearward(centre[0], H - 1, 8);
        assertEquals(H - 9, rear[1], "a target deeper than the objective is pulled back toward it");
    }

    // ---- fromObjective ----

    private static final int PW = 60;
    private static final int PH = 40;
    private static final int BLOCK = 10;
    private static final int WHO = 3;

    /** A 60x40 map whose objective precinct claimed a 10x10 block in the south-west corner. */
    private static int[][] cornerClaim() {
        int[][] claim = new int[PW][PH];
        for (int x = 0; x < PW; x++) {
            for (int y = 0; y < PH; y++) {
                claim[x][y] = GrownTrunkPlan.UNOWNED;
            }
        }
        for (int x = 0; x < BLOCK; x++) {
            for (int y = 0; y < BLOCK; y++) {
                claim[x][y] = WHO;
            }
        }
        return claim;
    }

    @Test
    public void objectiveFrontIsTheClaimThenThreeRings() {
        FrontDepth front = FrontDepth.fromObjective(cornerClaim(), WHO, PW, PH);
        assertNotNull(front);
        assertEquals(4, front.bands());

        int[] population = new int[4];
        for (int x = 0; x < PW; x++) {
            for (int y = 0; y < PH; y++) {
                int band = front.bandAt(x, y);
                population[band]++;
                boolean inBlock = x < BLOCK && y < BLOCK;
                assertEquals(inBlock, band == 0,
                        "band 0 is exactly the claim, at (" + x + "," + y + ")");
            }
        }
        assertEquals(BLOCK * BLOCK, population[0]);
        for (int band = 1; band < 4; band++) {
            assertTrue(population[band] > 0, "band " + band + " holds ground");
        }
    }

    @Test
    public void objectiveFrontRisesMonotonicallyAlongEveryRayFromTheClaim() {
        FrontDepth front = FrontDepth.fromObjective(cornerClaim(), WHO, PW, PH);
        int[][] rays = {{1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}, {3, 1}};
        for (int[] ray : rays) {
            int previous = 0;
            for (int step = 0; ; step++) {
                int x = BLOCK / 2 + ray[0] * step;
                int y = BLOCK / 2 + ray[1] * step;
                if (x >= PW || y >= PH) break;
                int band = front.bandAt(x, y);
                assertTrue(band >= previous,
                        "band fell from " + previous + " to " + band
                                + " at (" + x + "," + y + ") along ray "
                                + ray[0] + "," + ray[1]);
                previous = band;
            }
        }
    }

    @Test
    public void objectiveFrontNamesItsBands() {
        FrontDepth front = FrontDepth.fromObjective(cornerClaim(), WHO, PW, PH);
        assertEquals("citadel", front.bandName(0));
        assertEquals("inner districts", front.bandName(1));
        assertEquals("outer districts", front.bandName(2));
        assertEquals("approaches", front.bandName(3));
    }

    @Test
    public void objectiveCentreIsTheClaimsOwnMiddle() {
        FrontDepth front = FrontDepth.fromObjective(cornerClaim(), WHO, PW, PH);
        int[] centre = front.objectiveCentre();
        assertEquals(BLOCK / 2 - 1, centre[0], "mean of 0..9 truncates to 4");
        assertEquals(BLOCK / 2 - 1, centre[1]);
        assertEquals(0, front.bandAt(centre[0], centre[1]));
    }

    @Test
    public void aClaimNobodyTookIsNoFrontAtAll() {
        assertNull(FrontDepth.fromObjective(cornerClaim(), WHO + 1, PW, PH),
                "a map whose objective claimed nothing has no front, not a front of depth zero");
    }

    @Test
    public void bandAtClampsOutsideTheMapTheWayBiomeAtDoes() {
        FrontDepth front = FrontDepth.fromObjective(cornerClaim(), WHO, PW, PH);
        assertEquals(front.bandAt(0, 0), front.bandAt(-5, -5));
        assertEquals(front.bandAt(PW - 1, PH - 1), front.bandAt(PW + 40, PH + 40));
    }
}
