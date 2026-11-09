package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A raid target served only in a window: a freighter standing at its berth for so long. */
class RaidServiceWindowTest {

    @Test
    void theTargetIsServedOnlyFromTheWindowOpeningUntilItCloses() {
        RaidObjective.ServiceWindow window = new RaidObjective.ServiceWindow(100, 200);
        assertFalse(window.open(99));
        assertTrue(window.open(100));
        assertTrue(window.open(199));
        assertFalse(window.open(200));
    }

    @Test
    void aWindowClosedWithTheTargetNotSeizedIsMissedAndOneSeizedIsNot() {
        RaidObjective.ServiceWindow window = new RaidObjective.ServiceWindow(100, 200);
        assertFalse(window.missed(199, false));
        assertTrue(window.missed(200, false));
        assertFalse(window.missed(500, true));
        assertThrows(IllegalArgumentException.class, () -> new RaidObjective.ServiceWindow(200, 200));
    }

    @Test
    void beforeTheWindowTheChecklistSaysHowLongUntilItOpens() {
        RaidObjective raid = new RaidObjective("freighter", "Freighter's hold", 12, 13, 1, 3, 4)
                .withServiceWindow(new RaidObjective.ServiceWindow(1950, 3000));
        MissionObjectiveSnapshot.Item item = MissionObjectiveFamilies.raid(raid);
        String expected = MissionObjectiveFamilies.clock(1950);
        assertTrue(item.instruction().contains(expected), item.instruction());
        assertEquals(item.instruction(), item.blocker());
    }

    @Test
    void aClockReadsInMinutesAndSeconds() {
        int perSecond = Math.round(1f / BattleSimulation.TICK_DT);
        assertEquals("2:05", MissionObjectiveFamilies.clock(125 * perSecond));
        assertEquals("0:00", MissionObjectiveFamilies.clock(-5));
    }
}
