package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.derelict.StakeFootprint.Part;
import com.dillon.starsectormarines.battle.derelict.SurveyStake.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StakeConstructionTest {

    @Test
    void theComingStagesPartsBuildOverItsProgressAndAreWholeOnceItArrives() {
        // Planted: the cradle and pad build toward raised; the store waits for the stage after.
        assertEquals(-1f, StakeConstruction.share(Part.CRADLE, Stage.PLANTED, 0f));
        float early = StakeConstruction.share(Part.CRADLE, Stage.PLANTED, 0.3f);
        float later = StakeConstruction.share(Part.CRADLE, Stage.PLANTED, 0.5f);
        assertTrue(early > 0f && later > early && later < 1f, early + " then " + later);
        assertEquals(1f, StakeConstruction.share(Part.CRADLE, Stage.PLANTED, 0.99f));
        assertEquals(-1f, StakeConstruction.share(Part.STORE, Stage.PLANTED, 0.9f));
        // Raised: the cradle and pad whole; the store and posts build toward established.
        assertEquals(1f, StakeConstruction.share(Part.DRONE, Stage.RAISED, 0f));
        assertTrue(StakeConstruction.share(Part.STORE, Stage.RAISED, 0.5f) > 0f);
        assertEquals(1f, StakeConstruction.share(Part.POST_EAST, Stage.ESTABLISHED, 1f));
        assertEquals(-1f, StakeConstruction.share(Part.CRADLE, Stage.SCRAP, 1f));
    }

    @Test
    void theSpireMovesOnlyAsItsStageArrives() {
        float[] driving = StakeConstruction.spireMotion(Stage.PLANTED, 0f);
        assertNotNull(driving);
        assertTrue(driving[0] > 1f, "drawn nearer the camera as it comes down");
        assertNull(StakeConstruction.spireMotion(Stage.PLANTED, StakeConstruction.DRIVE_SHARE));
        float[] opening = StakeConstruction.spireMotion(Stage.RAISED, 0f);
        assertNotNull(opening);
        assertTrue(opening[0] < 1f && opening[1] > 0f, "smaller and turned as it starts to open");
        assertNull(StakeConstruction.spireMotion(Stage.ESTABLISHED, 0f));
    }
}
