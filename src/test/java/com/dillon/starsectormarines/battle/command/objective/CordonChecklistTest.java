package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.CordonChecklist.Post;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Hold;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Phase;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Reading;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence.Outcome;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Cordon's rows point the company at the place it is wanted now, and fail only on their own rules. */
class CordonChecklistTest {
    private static final int[] GAP = {50, 100}, GATE = {50, 20};

    @Test void beforeTheFirstConvoyTheCompanyIsSentToTheGap() {
        Item row = CordonChecklist.convoys(Outcome.RUNNING,
                List.of(new Reading(1, Phase.WAITING, Hold.NONE, 1800, -1, -1),
                        new Reading(2, Phase.WAITING, Hold.NONE, 9000, -1, -1)), 2, 0, GAP, GATE, 0);
        assertEquals("cordon-gap", row.focusMarkerId());
        assertEquals(State.ACTIVE, row.state());
        assertTrue(row.blocker().isEmpty());
    }

    @Test void aConvoyHeldForAnEscortOutranksOneDrivingAndSaysWhy() {
        Item row = CordonChecklist.convoys(Outcome.RUNNING, List.of(
                new Reading(1, Phase.OUTBOUND, Hold.NONE, 0, 40, 60),
                new Reading(2, Phase.INBOUND, Hold.ESCORT, 900, 48, 90)), 2, 12, GAP, GATE, 1000);
        assertEquals("cordon-convoy-2", row.focusMarkerId());
        assertFalse(row.blocker().isEmpty());
    }

    @Test void theConvoysRowCompletesOnceEnoughAreOutAndFailsOnceTooFewCanBe() {
        var out = List.of(new Reading(1, Phase.OUT, Hold.NONE, 0, -1, -1),
                new Reading(2, Phase.OUT, Hold.NONE, 0, -1, -1), new Reading(3, Phase.WAITING, Hold.NONE, 9000, -1, -1));
        Item done = CordonChecklist.convoys(Outcome.RUNNING, out, 2, 24, GAP, GATE, 5000);
        assertEquals(State.COMPLETE, done.state());
        assertEquals(1f, done.progress());
        var lost = List.of(new Reading(1, Phase.LOST, Hold.NONE, 0, -1, -1),
                new Reading(2, Phase.LOST, Hold.NONE, 0, -1, -1), new Reading(3, Phase.INBOUND, Hold.NONE, 0, 50, 90));
        assertEquals(State.FAILED, CordonChecklist.convoys(Outcome.RUNNING, lost, 2, 0, GAP, GATE, 5000).state());
    }

    @Test void aCallingRedoubtOutranksTheQuietGapRedoubt() {
        Item row = CordonChecklist.line(CordonVariant.ROAD, Outcome.RUNNING, null, List.of(
                new Post(2, CordonRedoubt.State.HOLDING, 40, 100, true),
                new Post(3, CordonRedoubt.State.HOLDING, 60, 100, true),
                new Post(5, CordonRedoubt.State.CALLING, 120, 100, false)), 0, 1, 600, 27000);
        assertEquals("cordon-redoubt-5", row.focusMarkerId());
        assertEquals(3, row.markers().size(), "both gap redoubts and the calling one");
        assertFalse(row.blocker().isEmpty());
    }

    @Test void aRoadLostOnItsConvoysLeavesTheLineRowStanding() {
        Item row = CordonChecklist.line(CordonVariant.ROAD, Outcome.BROKEN, "convoys_lost",
                List.of(new Post(2, CordonRedoubt.State.HOLDING, 40, 100, true)), 0, 1, 600, 27000);
        assertEquals(State.ACTIVE, row.state());
        assertEquals(State.FAILED, CordonChecklist.line(CordonVariant.ROAD, Outcome.BROKEN, "gap_closed",
                List.of(new Post(2, CordonRedoubt.State.FALLEN, 40, 100, true)), 2, 1, 600, 27000).state());
    }
}
