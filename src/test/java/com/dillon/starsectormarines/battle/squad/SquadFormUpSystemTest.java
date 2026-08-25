package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad still arriving by lift holds at its LZ instead of advancing a team at a time. */
class SquadFormUpSystemTest {

    @Test
    void aPartlyLandedSquadIsFormingUp() {
        Squad squad = campaignSquad(12, 4);

        assertTrue(SquadFormUpSystem.formingUp(squad));
    }

    @Test
    void aFullyLandedSquadIsNot() {
        Squad squad = campaignSquad(12, 12);

        assertFalse(SquadFormUpSystem.formingUp(squad));
    }

    @Test
    void aMauledSquadIsNotMistakenForAnArrivingOne() {
        Squad squad = campaignSquad(12, 12);
        squad.aliveMembers = 3;

        assertFalse(SquadFormUpSystem.formingUp(squad),
                "originalSize counts marines landed, so casualties never read as arrivals");
    }

    @Test
    void aGeneratedSquadIsNeverGated() {
        Squad militia = new Squad(1, Faction.DEFENDER);
        militia.originalSize = 2;

        assertFalse(SquadFormUpSystem.formingUp(militia));
    }

    @Test
    void waitingExpiresSoALostLiftCannotDeadlockTheMission() {
        Squad squad = campaignSquad(12, 4);
        squad.formUpElapsed = SquadFormUpSystem.FORM_UP_TIMEOUT;

        assertFalse(SquadFormUpSystem.formingUp(squad));
    }

    @Test
    void theGateMasksExecutionWithoutClearingTheDirective() {
        Squad assembling = campaignSquad(12, 4);
        Squad landed = campaignSquad(12, 12);
        ObjectiveAssignment order = ObjectiveAssignment.escort(
                assembling.id, 5, 5);
        assembling.assignedObjective = order;
        landed.assignedObjective = ObjectiveAssignment.escort(landed.id, 5, 5);

        assertNull(assembling.assignmentForExecution());
        assertSame(order, assembling.assignedObjective);
        assertEquals("FORMING_UP", assembling.assignmentExecutionSuspension());
        assertNotNull(landed.assignmentForExecution());
        assertNull(landed.assignmentExecutionSuspension());
    }

    private static Squad campaignSquad(int expected, int landed) {
        Squad squad = new Squad(1, Faction.MARINE);
        squad.campaignSquadId = "cs-1";
        squad.campaignLabel = "Squad 01";
        squad.expectedSize = expected;
        squad.originalSize = landed;
        return squad;
    }
}
