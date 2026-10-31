package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.CordonChecklist.Post;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.cordon.CordonBeats;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Hold;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Phase;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys.Reading;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence.Outcome;
import com.dillon.starsectormarines.battle.cordon.CordonTown;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
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
                        new Reading(2, Phase.WAITING, Hold.NONE, 9000, -1, -1)), people(0, 0), GAP, GATE, 0);
        assertEquals("cordon-gap", row.focusMarkerId());
        assertEquals(State.ACTIVE, row.state());
        assertTrue(row.blocker().isEmpty());
    }

    @Test void aConvoyHeldForAnEscortOutranksOneDrivingAndSaysWhy() {
        Item row = CordonChecklist.convoys(Outcome.RUNNING, List.of(
                new Reading(1, Phase.OUTBOUND, Hold.NONE, 0, 40, 60),
                new Reading(2, Phase.INBOUND, Hold.ESCORT, 900, 48, 90)), people(12, 0), GAP, GATE, 1000);
        assertEquals("cordon-convoy-2", row.focusMarkerId());
        assertFalse(row.blocker().isEmpty());
    }

    /** A town of 48, 24 of whom must come out. */
    private static CordonChecklist.People people(int out, int lost) {
        return new CordonChecklist.People(out, lost, 48, 24);
    }

    @Test void thePeopleRowCompletesOnceEnoughAreOutAndFailsOnceTooFewAreLeftAlive() {
        var road = List.of(new Reading(1, Phase.OUTBOUND, Hold.NONE, 0, 40, 60));
        Item done = CordonChecklist.convoys(Outcome.RUNNING, road, people(24, 0), GAP, GATE, 5000);
        assertEquals(State.COMPLETE, done.state());
        assertEquals(1f, done.progress());
        assertEquals(State.ACTIVE, CordonChecklist.convoys(Outcome.RUNNING, road, people(12, 24), GAP, GATE, 5000).state());
        assertEquals(State.FAILED, CordonChecklist.convoys(Outcome.RUNNING, road, people(12, 25), GAP, GATE, 5000).state());
    }

    private static CordonChecklist.Town town(CordonTown.State state, boolean calling, int trapped, int standing) {
        return new CordonChecklist.Town(state, calling, List.of(new int[]{50, 18}, new int[]{51, 18}), 50, 18, trapped,
                standing, 20, 0, 0);
    }

    @Test void holdAinTafrantIsBeaconedAtTheStagingAreaAndSaysWhenTheCompanyIsWanted() {
        Item quiet = CordonChecklist.town(Outcome.RUNNING, null, town(CordonTown.State.HOLDING, false, 36, 20));
        assertEquals("cordon-1-town", quiet.id());
        assertEquals("cordon-staging", quiet.focusMarkerId());
        assertEquals(2, quiet.focus().footprint().size(), "the staging area's cells are the beacon's footprint");
        assertEquals(State.ACTIVE, quiet.state());
        assertTrue(quiet.blocker().isEmpty());
        assertFalse(CordonChecklist.town(Outcome.RUNNING, null, town(CordonTown.State.HOLDING, true, 36, 9))
                .blocker().isEmpty(), "a calling town asks for squads");
        assertFalse(CordonChecklist.town(Outcome.RUNNING, null, town(CordonTown.State.PRESSED, false, 36, 20))
                .blocker().isEmpty(), "the swarm in the staging area asks for squads");
    }

    @Test void holdAinTafrantCompletesOnceTheTownIsEmptyAndFailsWhenItIsAbandoned() {
        assertEquals(State.COMPLETE,
                CordonChecklist.town(Outcome.RUNNING, "town_empty", town(CordonTown.State.HOLDING, false, 0, 4)).state());
        assertEquals(State.FAILED,
                CordonChecklist.town(Outcome.RUNNING, "town_abandoned", town(CordonTown.State.OVERRUN, false, 12, 0))
                        .state());
        Item boarding = CordonChecklist.town(Outcome.RUNNING, null, new CordonChecklist.Town(CordonTown.State.HOLDING,
                false, List.of(new int[]{50, 18}), 50, 18, 30, 20, 20, 7, 12));
        assertTrue(boarding.progressText().contains("7/12"), boarding.progressText());
    }

    @Test void fallBackIsBeaconedAtTheRallyUntilTheCompanyIsBehindTheLine() {
        Item going = CordonChecklist.fallBack(Outcome.RUNNING, false, new int[]{60, 140});
        assertEquals("cordon-rally", going.focusMarkerId());
        assertEquals(State.ACTIVE, going.state());
        assertFalse(going.blocker().isEmpty());
        assertEquals(State.COMPLETE, CordonChecklist.fallBack(Outcome.RUNNING, true, new int[]{60, 140}).state());
    }

    @Test void aCallingRedoubtIsTheLinesFocus() {
        Item row = CordonChecklist.line(Outcome.RUNNING, null, List.of(
                new Post(2, CordonRedoubt.State.HOLDING, 40, 100),
                new Post(5, CordonRedoubt.State.CALLING, 120, 100)), 0, 1, 600, 27000);
        assertEquals("cordon-redoubt-5", row.focusMarkerId());
        assertEquals(1, row.markers().size(), "only the calling redoubt is beaconed");
        assertFalse(row.blocker().isEmpty());
        assertEquals(State.FAILED, CordonChecklist.line(Outcome.BROKEN, "line_broken",
                List.of(new Post(2, CordonRedoubt.State.FALLEN, 40, 100)), 2, 1, 600, 27000).state());
    }

    @Test void aRunningBeatIsAnOptionalRowBeaconedWhereItIs() {
        Item row = CordonChecklist.beat(new CordonBeats.Reading("last_loading", "The last loading", "Hold the town.",
                Status.ACTIVE, 3_000, true, 50, 20));
        assertTrue(row.optional());
        assertEquals("cordon-beat-last_loading-site", row.focusMarkerId());
        assertEquals(50, row.focus().x());
    }

    @Test void aLapsedBeatIsStruckAndLosesItsBeacon() {
        Item row = CordonChecklist.beat(new CordonBeats.Reading("gap_falls", "The gap falls", "Hold the gap.",
                Status.LAPSED, 3_500, false, -1, -1));
        assertEquals(State.FAILED, row.state());
        assertTrue(row.markers().isEmpty());
    }
}
