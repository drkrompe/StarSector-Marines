package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two programs a lane place is built from.
 *
 * <p>What matters about them is not their contents but their <em>exclusions</em>
 * and their scale. A lane place that packed a keep would give the map two
 * canonical command posts and disable the keep phase in silence; one that owed
 * an airfield would be an installation rather than a post on the way to one.
 * And both have to be small enough that nine of them fit on a map beside the
 * fortress they lead to.
 */
class LanePostProgramTest {

    /**
     * The one-keep law, asked of the program rather than of a generated map.
     * {@code BspCityGenerator.requireNoCompetingKeep} throws on two, so this is
     * the cheap place to find out.
     */
    @Test
    void neitherLanePostPacksAKeep() {
        assertEquals(0, FortressProgram.outpost().countOf(RoomPurpose.KEEP_THRONE),
                "an outpost with a keep is a second canonical command post");
        assertEquals(0, FortressProgram.strongpoint().countOf(RoomPurpose.KEEP_THRONE),
                "a strongpoint with a keep is a second canonical command post");
    }

    /** An air arm belongs to an installation, and a lot is the largest thing a program orders. */
    @Test
    void neitherLanePostOwesAnAirfield() {
        assertEquals(0, FortressProgram.outpost().airfields());
        assertEquals(0, FortressProgram.strongpoint().airfields());
    }

    /**
     * A lane place is a compound because it berths somebody, which is what makes
     * the capture rule and the victory law see it at all. A post that emitted no
     * {@code BARRACKS} node would be scenery the marines walk past.
     */
    @Test
    void bothLanePostsBerthSomebody() {
        assertTrue(FortressProgram.outpost().countOf(RoomPurpose.BARRACKS) >= 1,
                "an outpost nobody sleeps in is not a compound");
        assertTrue(FortressProgram.strongpoint().countOf(RoomPurpose.BARRACKS) >= 2,
                "a strongpoint is meant to be a sequence of compounds, not one");
    }

    /** A strongpoint is a position that has to be reduced; an outpost is a position. */
    @Test
    void aStrongpointIsDeeperThanAnOutpost() {
        FortressProgram outpost = FortressProgram.outpost();
        FortressProgram strongpoint = FortressProgram.strongpoint();
        assertTrue(strongpoint.floorArea() > outpost.floorArea(),
                "a strongpoint that is not larger than an outpost is the same place twice");
        assertEquals(1, strongpoint.countOf(RoomPurpose.ARMORY),
                "the armoury is what makes a strongpoint worth taking rather than clearing");
        assertEquals(0, outpost.countOf(RoomPurpose.ARMORY));
    }

    /**
     * Nine of these stand on a Conquest map beside a garrison, so their ground
     * has to be a rounding error against it rather than a competitor for it.
     */
    @Test
    void bothArePostsRatherThanInstallations() {
        int garrison = FortressProgram.garrison().envelopeArea();
        assertTrue(FortressProgram.strongpoint().envelopeArea() * 4 < garrison,
                "a strongpoint at " + FortressProgram.strongpoint().envelopeArea()
                        + " cells against a garrison's " + garrison
                        + " is not a post on the way to it");
        assertTrue(FortressProgram.outpost().envelopeArea()
                        < FortressProgram.strongpoint().envelopeArea());
    }
}
