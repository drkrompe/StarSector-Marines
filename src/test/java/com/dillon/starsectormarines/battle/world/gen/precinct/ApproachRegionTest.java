package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stated approach length becomes a region, to the cell.
 *
 * <p>The band a mission states is a fraction of the map and the objective's
 * claim is grown, so the only place the two can be turned into cells is after
 * generation. What is asked here is that the arithmetic is the arithmetic: the
 * band slides exactly far enough, stops where it is told to stop, and keeps the
 * approach it started with even once it is nowhere near an edge.
 */
class ApproachRegionTest {

    private static final int W = 560;
    private static final int H = 336;

    /** A claim in the far east, its attacker-facing boundary at x = 350. */
    private static final int[] EAST_CLAIM = {350, 100, 520, 260};

    /** A claim in the far north, its attacker-facing boundary at y = 220. */
    private static final int[] NORTH_CLAIM = {200, 220, 400, 320};

    /**
     * The band slides to exactly the stated distance from the claim.
     *
     * <p>{@link MapPlacement#WEST} spans x 0..186 at this size, so its
     * objective-facing side is 164 cells short of a claim beginning at 350. A
     * standard standoff wants 80, so it slides 84 and comes out 84..270 — the
     * same 187 cells of depth it started with, in a different place.
     */
    @Test
    void aStatedStandoffSlidesTheBandToTheCell() {
        int[] stated = MapPlacement.WEST.bounds(W, H);
        assertEquals(186, stated[2], "the western third of a 560-wide map");

        ApproachRegion standard = ApproachRegion.resolve(
                MapPlacement.WEST, Standoff.STANDARD, EAST_CLAIM, W, H);
        assertArrayEquals(new int[]{84, stated[1], 270, stated[3]}, standard.bounds());
        assertEquals(80, standard.standoffCells());
        assertEquals(EAST_CLAIM[0] - standard.x1(), standard.standoffCells(),
                "the reading is the gap between the region and the claim");

        ApproachRegion close = ApproachRegion.resolve(
                MapPlacement.WEST, Standoff.CLOSE, EAST_CLAIM, W, H);
        assertArrayEquals(new int[]{124, stated[1], 310, stated[3]}, close.bounds());
        assertEquals(40, close.standoffCells());
    }

    /**
     * {@link Standoff#FAR} is the band the mission stated, untouched.
     *
     * <p>Every map that is not a Conquest takes this, so it has to be identity
     * rather than a very large number that happens to clamp to identity.
     */
    @Test
    void farIsTheStatedBandUnchanged() {
        for (MapPlacement from : new MapPlacement[]{
                MapPlacement.WEST, MapPlacement.SOUTH, MapPlacement.SOUTH_WEST,
                MapPlacement.NORTH_EAST, MapPlacement.CENTRE}) {
            ApproachRegion region = ApproachRegion.resolve(
                    from, Standoff.FAR, EAST_CLAIM, W, H);
            assertArrayEquals(from.bounds(W, H), region.bounds(),
                    from + " under FAR is the band it stated");
        }
    }

    /**
     * A map that cannot afford the standoff keeps the band it has.
     *
     * <p>The band already sits against the edge it arrived across, so sliding
     * backward would take it off the map. What comes back is the stated band and
     * a standoff smaller than the one asked for, which is the honest answer and
     * the one {@code BspKeys.APPROACH_STANDOFF} carries.
     */
    @Test
    void aMapThatCannotAffordTheStandoffSaysSo() {
        // A claim reaching back to x = 200 leaves only 14 cells beyond the
        // western third — less than either standoff asks for.
        int[] near = {200, 100, 520, 260};
        ApproachRegion region = ApproachRegion.resolve(
                MapPlacement.WEST, Standoff.STANDARD, near, W, H);
        assertArrayEquals(MapPlacement.WEST.bounds(W, H), region.bounds(),
                "a band with no room to slide stays where it was stated");
        assertEquals(14, region.standoffCells());
    }

    /**
     * A band with nothing to stand off from is the band, whatever was stated.
     *
     * <p>What a map with no claim yet, or none of it programmed, resolves to.
     */
    @Test
    void noClaimIsNoSlide() {
        ApproachRegion region = ApproachRegion.resolve(
                MapPlacement.SOUTH, Standoff.CLOSE, null, W, H);
        assertArrayEquals(MapPlacement.SOUTH.bounds(W, H), region.bounds());
        assertEquals(Integer.MAX_VALUE, region.standoffCells());
    }

    /**
     * The approach is the edge the <em>stated</em> band sat against, and it
     * survives the slide.
     *
     * <p>This is the whole reason the approach is carried rather than derived
     * from the resolved rect: a slid region touches no map edge at all, and the
     * nearest-edge rule would hand a southern approach a side it never had.
     */
    @Test
    void theApproachSurvivesTheSlide() {
        ApproachRegion region = ApproachRegion.resolve(
                MapPlacement.SOUTH, Standoff.STANDARD, NORTH_CLAIM, W, H);
        assertEquals(LandingPad.Approach.SOUTH, region.approach());
        assertEquals(80, region.standoffCells());
        assertEquals(NORTH_CLAIM[1] - region.y1(), region.standoffCells());
        // Off the southern edge entirely, which is exactly the case that used to
        // resolve to a side approach.
        assertEquals(28, region.y0());
        assertEquals(140, region.y1());
    }

    /**
     * A corner band takes the edge with the longer frontage, and slides along
     * that edge's own axis.
     *
     * <p>{@link ApproachRegion#awayFrom} hands out corners, so this is the
     * unstated-attacker case rather than a curiosity.
     */
    @Test
    void aCornerBandSlidesAlongTheEdgeItFaces() {
        ApproachRegion region = ApproachRegion.resolve(
                MapPlacement.SOUTH_WEST, Standoff.STANDARD, NORTH_CLAIM, W, H);
        assertEquals(LandingPad.Approach.SOUTH, region.approach(),
                "a south-west corner is wider than it is deep at 560x336");
        int[] stated = MapPlacement.SOUTH_WEST.bounds(W, H);
        assertEquals(stated[0], region.x0(), "a slide along y leaves x alone");
        assertEquals(stated[2], region.x1());
        assertEquals(80, region.standoffCells());
    }
}
