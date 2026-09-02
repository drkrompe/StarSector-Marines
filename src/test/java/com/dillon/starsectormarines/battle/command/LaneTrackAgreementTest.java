package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneGeometry;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The map's lanes and the commanders' tracks are the same strips of ground.
 *
 * <p>They have to be, and they are computed twice: {@code ConquestTrackLayout}
 * lives in {@code battle.command}, which reads {@code battle.world.gen}, so a
 * generator asking it where a place goes would invert the dependency.
 * {@link LaneGeometry} therefore carries its own copy of the arithmetic — and
 * two copies drift, so this asks both about every cell of a Conquest map rather
 * than trusting that they were written the same way.
 *
 * <p>A drift here is not a compile error and not a visible defect: it is a
 * strongpoint seeded in the middle of one lane that the commanders count as
 * belonging to its neighbour, which reads as a capture allocation behaving
 * oddly a long way from the cause.
 */
class LaneTrackAgreementTest {

    @ParameterizedTest
    @EnumSource(TraversalAxis.class)
    void everyCellIsInTheSameLaneAndTheSameTrack(TraversalAxis axis) {
        int width = MapScale.CONQUEST.width;
        int height = MapScale.CONQUEST.height;
        ConquestTrackLayout tracks = new ConquestTrackLayout(axis, width, height);
        int extent = tracks.lateralExtent();
        for (int lateral = 0; lateral < extent; lateral++) {
            assertEquals(tracks.trackForLateral(lateral),
                    LaneGeometry.laneForLateral(lateral, tracks.trackCount(), extent),
                    "lateral " + lateral + " on " + axis);
        }
        for (int track = 0; track < tracks.trackCount(); track++) {
            assertEquals(tracks.lateralStartInclusive(track),
                    LaneGeometry.startInclusive(track, tracks.trackCount(), extent));
            assertEquals(tracks.lateralEndInclusive(track),
                    LaneGeometry.endInclusive(track, tracks.trackCount(), extent));
            assertEquals(tracks.lateralCenterCell(track),
                    LaneGeometry.centre(track, tracks.trackCount(), extent));
        }
    }

    /** One lane per track when nobody states otherwise, which is the whole default. */
    @Test
    void theDefaultLaneCountIsTheDefaultTrackCount() {
        assertEquals(ConquestTrackLayout.DEFAULT_TRACK_COUNT,
                PrecinctPlan.Lanes.DEFAULT_COUNT,
                "a map that lays a different number of lanes than the commanders "
                        + "advance up has resistance in ground nobody is sent to");
    }
}
