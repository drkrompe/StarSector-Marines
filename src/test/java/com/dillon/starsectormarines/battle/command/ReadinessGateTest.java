package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessGateTest {
    private static final int HELD = ReadinessGate.HARDENED_WINDOW_TICKS;

    @Test
    void opportunisticNeedsAHardenedLiftTheEscortsStrengthAndADetachment() {
        assertTrue(gate(HivePosture.OPPORTUNISTIC, false, 2, HELD, 3, 3, 1).open());
        assertFalse(gate(HivePosture.OPPORTUNISTIC, false, 0, HELD, 3, 3, 1).open(), "no built defences");
        assertFalse(gate(HivePosture.OPPORTUNISTIC, false, 2, HELD - 1, 3, 3, 1).open(), "a loss inside the window");
        assertFalse(gate(HivePosture.OPPORTUNISTIC, false, 2, HELD, 2, 3, 1).open(), "the escort left short");
        assertFalse(gate(HivePosture.OPPORTUNISTIC, false, 2, HELD, 3, 3, 0).open(), "nobody who can hurt a bore");
        assertFalse(ReadinessGate.evaluate(HivePosture.OPPORTUNISTIC, false,
                new ReadinessGate.Inputs(2, HELD, 3, 3, 1, HELD - 1)).open(), "an evacuee lost inside the window");
    }

    @Test
    void purgeRelaxesTheLiftAndTheStrengthOnlyAgainstTheMother() {
        ReadinessGate mother = gate(HivePosture.PURGE, true, 0, 0, 0, 4, 1);
        assertTrue(mother.open());
        assertTrue(mother.relaxed());
        assertFalse(gate(HivePosture.PURGE, true, 0, 0, 0, 4, 0).open(), "a detachment is never relaxed");
        assertFalse(gate(HivePosture.PURGE, false, 0, 0, 0, 4, 1).open(), "a first-wave bore keeps the full gate");
    }

    @Test
    void evacuateNeverAsksAboutTheLiftButKeepsTheEscort() {
        assertTrue(gate(HivePosture.EVACUATE, false, 0, 0, 3, 3, 1).open());
        assertFalse(gate(HivePosture.EVACUATE, false, 2, HELD, 2, 3, 1).open());
    }

    @Test
    void escortNeedCountsWalkersTheirScreenPendingDoorsAndTheLiftReserve() {
        CivilianRedoubt walking = redoubt(1, 10, -1, 0, 6);
        CivilianRedoubt pending = redoubt(2, -1, -1, 4, 0);
        CivilianRedoubt fallen = redoubt(3, -1, 50, 0, 0);
        CivilianRedoubt boarded = redoubt(4, 20, -1, 0, 0);
        assertEquals(1 + 1 + 1 + ReadinessGate.LIFT_RESERVE_SQUADS,
                ReadinessGate.escortNeed(List.of(walking, pending, fallen, boarded)));
        assertEquals(1 + ReadinessGate.LIFT_RESERVE_SQUADS, ReadinessGate.escortNeed(List.of()));
    }

    private static ReadinessGate gate(HivePosture posture, boolean mother, int defences, int quiet, int left,
                                      int need, int capable) {
        return ReadinessGate.evaluate(posture, mother, new ReadinessGate.Inputs(defences, quiet, left, need, capable));
    }

    private static CivilianRedoubt redoubt(int number, int releasedTick, int fellTick, int sealed, int walking) {
        return new CivilianRedoubt(number, 0, 0, 0, 0, 1, 1, -1, 4, releasedTick, fellTick, sealed, walking,
                walking > 0 ? 5 : -1, walking > 0 ? 5 : -1, -1);
    }
}
