package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest.Strength;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A Raid's garrison columns: when the next is due, and how large ({@code raid-demolish-the-works.md}, slice 8). */
class RaidPressureTriggerTest {

    private static final RaidPressureTrigger.Clock CLOCK =
            new RaidPressureTrigger.Clock(180, 60, Strength.MEDIUM, Strength.SMALL);

    private static int s(int seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    @Test
    void aQuietMarketSendsColumnsOnItsRoutineClock() {
        assertEquals(s(180), RaidPressureTrigger.due(0, -1, CLOCK));
        assertEquals(s(200) + s(180), RaidPressureTrigger.due(s(200), -1, CLOCK));
    }

    @Test
    void theAlarmBringsTheNextColumnForwardAndQuickensTheClock() {
        assertEquals(s(100) + s(60), RaidPressureTrigger.due(0, s(100), CLOCK),
                "raised at 100 s, the next column comes 60 s later, not at 180 s");
        assertEquals(s(180), RaidPressureTrigger.due(0, s(150), CLOCK),
                "a routine column already nearer than the alarm's still comes first");
        assertEquals(s(300) + s(60), RaidPressureTrigger.due(s(300), s(100), CLOCK));
    }

    @Test
    void eachSecondColumnIsALargerOneUpToTheLargest() {
        assertEquals(Strength.SMALL, RaidPressureTrigger.columnStrength(0, Strength.SMALL));
        assertEquals(Strength.SMALL, RaidPressureTrigger.columnStrength(1, Strength.SMALL));
        assertEquals(Strength.MEDIUM, RaidPressureTrigger.columnStrength(2, Strength.SMALL));
        assertEquals(Strength.LARGE, RaidPressureTrigger.columnStrength(4, Strength.SMALL));
        assertEquals(Strength.LARGE, RaidPressureTrigger.columnStrength(9, Strength.SMALL));
    }
}
