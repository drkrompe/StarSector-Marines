package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmAudioTest {

    @Test void aBruteStepsEveryHalfCellAndARunnerEveryCell() {
        assertTrue(SwarmAudio.stepped(UnitType.SWARM_BRUTE, 0.4f, 0.6f));
        assertTrue(SwarmAudio.stepped(UnitType.SWARM_BRUTE, 0.9f, 0.1f));
        assertFalse(SwarmAudio.stepped(UnitType.SWARM_RUNNER, 0.4f, 0.6f));
        assertTrue(SwarmAudio.stepped(UnitType.SWARM_RUNNER, 0.9f, 0.1f));
    }

    @Test void aStandingBodyMakesNoFootfall() {
        assertFalse(SwarmAudio.stepped(UnitType.SWARM_BRUTE, 0.3f, 0.3f));
        assertFalse(SwarmAudio.stepped(UnitType.SWARM_RUNNER, 0.7f, 0.7f));
    }
}
