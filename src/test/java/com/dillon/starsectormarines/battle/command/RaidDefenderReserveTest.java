package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The works' yard reserve's order ({@code raid-demolish-the-works.md}, slice 6). */
class RaidDefenderReserveTest {

    @Test
    void theReserveHoldsItsPostUntilTheAlarmThenIsTakenOverAndDefendsTheHall() {
        assertEquals(RaidDefenderCommand.ReserveOrder.HOLD_POST, RaidDefenderCommand.reserveOrder(false, false));
        assertEquals(RaidDefenderCommand.ReserveOrder.TAKE_OVER, RaidDefenderCommand.reserveOrder(true, false),
                "on the alarm a reserve still under its setup hold is handed to the command first");
        assertEquals(RaidDefenderCommand.ReserveOrder.DEFEND_HALL, RaidDefenderCommand.reserveOrder(true, true));
        assertEquals(RaidDefenderCommand.ReserveOrder.DEFEND_HALL, RaidDefenderCommand.reserveOrder(false, true),
                "once roused it keeps to the hall");
    }
}
