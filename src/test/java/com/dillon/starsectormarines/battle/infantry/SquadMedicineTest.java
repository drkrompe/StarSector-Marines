package com.dillon.starsectormarines.battle.infantry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The one billet rule the battle and the company card both read. */
public class SquadMedicineTest {

    @Test
    public void theMiddleTeamComesFirstThenRosterOrder() {
        int[] team = {0, 0, 1, 1, 2};
        assertEquals(2, SquadMedicine.medicBillet(5, i -> true, i -> team[i]));
        assertEquals(3, SquadMedicine.medicBillet(5, i -> i != 2, i -> team[i]));
        assertEquals(0, SquadMedicine.medicBillet(5, i -> team[i] != 1, i -> team[i]),
                "with nobody eligible in the middle team, the first eligible anywhere");
        assertEquals(-1, SquadMedicine.medicBillet(5, i -> false, i -> team[i]));
    }
}
