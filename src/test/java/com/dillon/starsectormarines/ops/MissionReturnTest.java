package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionReturnTest {

    @Test
    void noMissionLeadsToTheBoard() {
        assertEquals(ScreenId.MISSION_SELECT, MissionReturn.of(false, false).screen);
    }

    @Test
    void aMissionNotYetFoughtLeadsToItsBriefing() {
        assertEquals(ScreenId.BRIEFING, MissionReturn.of(true, false).screen);
    }

    @Test
    void aFoughtMissionLeadsBackToItsDebriefNotItsBriefing() {
        assertEquals(ScreenId.RESULTS, MissionReturn.of(true, true).screen);
    }
}
