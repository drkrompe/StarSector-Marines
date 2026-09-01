package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Command scoped per officer — `c13-the-task-force.md`.
 *
 * <p>The change these tests protect is a scale one: Full Strength CONQUEST
 * deploys eighty-four squads / 1,008 marines through six cycling lane shuttles,
 * while {@code Rank.COLONEL} may command twenty-four squads. Bounding
 * a whole operation by one officer's cap made that undeployable. It is now
 * bounded per officer, with a compatibility rule — an unassigned squad falls
 * to the commander — that keeps a roster nobody has reorganized behaving
 * exactly as before.
 */
class TaskForceTest {

    private static MarineRoster rosterWith(int squads) {
        MarineRoster roster = new MarineRoster();
        for (int i = 0; i < squads; i++) {
            MarineSquad squad = roster.createSquad();
            for (int b = 0; b < MarineSquad.CAPACITY; b++) roster.recruitToSquad(squad.id());
        }
        return roster;
    }

    private static MarineCaptain captain(MarineRoster roster, String name, Rank rank) {
        MarineCaptain captain = new MarineCaptain(name, "p.png", rank, 0f);
        roster.add(captain);
        return captain;
    }

    private static Set<String> lineSquadIds(MarineRoster roster) {
        Set<String> ids = new LinkedHashSet<>();
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve()) ids.add(squad.id());
        }
        return ids;
    }

    @Test
    void unassignedSquadsFallToTheCommanderExactlyAsBefore() {
        // The compatibility case: nothing assigned, so the operation is one
        // officer's command and the old cap still binds.
        MarineRoster roster = rosterWith(3);
        MarineCaptain lead = captain(roster, "Vance", Rank.LIEUTENANT);

        TaskForce force = TaskForce.of(roster, lead, lineSquadIds(roster));

        assertEquals(1, force.officerCount());
        assertEquals(3, force.squadCount());
        assertSame(lead, force.elements().get(0).officer);
        assertTrue(force.elements().get(0).inherited, "no home officer — attached to the commander");
        assertTrue(force.isValid(), "a Lieutenant commands three squads");
    }

    @Test
    void theCommandersCapStillBoundsWhatIsUnassigned() {
        MarineRoster roster = rosterWith(5);
        MarineCaptain lead = captain(roster, "Vance", Rank.LIEUTENANT);   // cap 3

        TaskForce force = TaskForce.of(roster, lead, lineSquadIds(roster));

        assertFalse(force.isValid(), "five unassigned squads is past a Lieutenant's three");
        assertFalse(CaptainDeploymentPolicy.isValidCommand(roster, lead, lineSquadIds(roster)));
    }

    @Test
    void assigningSquadsToOfficersIsWhatBuysScale() {
        // The point of the story. Thirty-four squads is past every rank, but
        // spread across three officers each is inside their own cap.
        MarineRoster roster = rosterWith(34);
        MarineCaptain lead = captain(roster, "Vance", Rank.LT_COLONEL);   // 16
        MarineCaptain second = captain(roster, "Okonkwo", Rank.MAJOR);    // 10
        MarineCaptain third = captain(roster, "Reyes", Rank.MAJOR);       // 10

        List<MarineSquad> line = roster.squads().stream()
                .filter(s -> !s.reserve()).toList();
        for (int i = 0; i < line.size(); i++) {
            MarineCaptain owner = i < 16 ? lead : i < 26 ? second : third;
            assertTrue(roster.assignCaptainToSquad(owner.id(), line.get(i).id()),
                    "assignment " + i + " is inside " + owner.name() + "'s cap");
        }

        TaskForce force = TaskForce.of(roster, lead, lineSquadIds(roster));

        assertEquals(3, force.officerCount());
        assertEquals(34, force.squadCount());
        assertEquals(34 * MarineSquad.CAPACITY, force.marines());
        assertTrue(force.isValid(),
                "408 marines deploy because command is per officer, not per operation");
        assertTrue(CaptainDeploymentPolicy.isValidCommand(roster, lead, lineSquadIds(roster)));
    }

    @Test
    void theCommanderHeadsTheList() {
        MarineRoster roster = rosterWith(4);
        MarineCaptain first = captain(roster, "Okonkwo", Rank.CAPTAIN);
        MarineCaptain lead = captain(roster, "Vance", Rank.MAJOR);
        List<MarineSquad> line = roster.squads().stream().filter(s -> !s.reserve()).toList();
        roster.assignCaptainToSquad(first.id(), line.get(0).id());
        roster.assignCaptainToSquad(first.id(), line.get(1).id());
        roster.assignCaptainToSquad(lead.id(), line.get(2).id());
        roster.assignCaptainToSquad(lead.id(), line.get(3).id());

        TaskForce force = TaskForce.of(roster, lead, lineSquadIds(roster));

        assertSame(lead, force.elements().get(0).officer,
                "the operation's commander is listed first regardless of roster order");
        assertEquals(2, force.officerCount());
    }

    @Test
    void anotherOfficersSquadDoesNotSpendTheCommandersCapacity() {
        MarineRoster roster = rosterWith(6);
        MarineCaptain lead = captain(roster, "Vance", Rank.LIEUTENANT);   // cap 3
        MarineCaptain other = captain(roster, "Okonkwo", Rank.CAPTAIN);   // cap 6
        List<MarineSquad> line = roster.squads().stream().filter(s -> !s.reserve()).toList();
        for (int i = 3; i < 6; i++) {
            roster.assignCaptainToSquad(other.id(), line.get(i).id());
        }

        TaskForce force = TaskForce.of(roster, lead, lineSquadIds(roster));

        assertTrue(force.isValid(), "three under the Lieutenant, three under the Captain");
        assertEquals(3, force.remainingCapacity(other), "Captain has used three of six");
        assertEquals(0, force.remainingCapacity(lead), "Lieutenant is full at three");
    }

    @Test
    void anEmptySelectionIsNotACommandFailure() {
        MarineRoster roster = rosterWith(2);
        MarineCaptain lead = captain(roster, "Vance", Rank.CAPTAIN);

        TaskForce force = TaskForce.of(roster, lead, Set.of());

        assertEquals(0, force.squadCount());
        assertTrue(force.isValid());
        assertTrue(CaptainDeploymentPolicy.isValidCommand(roster, lead, Set.of()),
                "deploying nobody is the personnel check's business, not command's");
    }

    @Test
    void canAddIsBoundedByWhoeverWouldLeadTheSquad() {
        MarineRoster roster = rosterWith(5);
        MarineCaptain lead = captain(roster, "Vance", Rank.LIEUTENANT);   // cap 3
        MarineCaptain other = captain(roster, "Okonkwo", Rank.CAPTAIN);
        List<MarineSquad> line = roster.squads().stream().filter(s -> !s.reserve()).toList();
        roster.assignCaptainToSquad(other.id(), line.get(4).id());

        Set<String> selected = new LinkedHashSet<>();
        for (int i = 0; i < 3; i++) selected.add(line.get(i).id());

        assertFalse(CaptainDeploymentPolicy.canAdd(roster, lead, selected, line.get(3).id()),
                "a fourth unassigned squad is past the Lieutenant's cap");
        assertTrue(CaptainDeploymentPolicy.canAdd(roster, lead, selected, line.get(4).id()),
                "but the Captain's own squad rides on the Captain's capacity");
    }
}
