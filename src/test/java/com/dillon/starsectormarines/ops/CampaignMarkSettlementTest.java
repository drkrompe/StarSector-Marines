package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveResult;
import com.dillon.starsectormarines.campaign.CampaignMarks;
import com.dillon.starsectormarines.campaign.CampaignState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settlement writes each side objective's mark and pays its credits from the outcome, and the save keeps the mark. */
class CampaignMarkSettlementTest {
    @Test void settlementWritesTheMarksTheOutcomeEarnedAndTheSaveKeepsThem() throws Exception {
        var outcome = MissionOutcome.builder().sideObjectives(List.of(
                new SideObjectiveResult("coupling", "Coupling", State.DONE, "", "corvus.harrow.coupling_delivered"),
                new SideObjectiveResult("warden", "Warden", State.LAPSED, "", ""))).build();
        CampaignState state = new CampaignState();
        assertTrue(state.marks().all().isEmpty(), "an old save starts with no marks");
        MissionResolver.writeCampaignMarks(state.marks(), outcome);
        MissionResolver.writeCampaignMarks(state.marks(), outcome);
        CampaignState loaded = roundTrip(state);
        assertTrue(loaded.marks().has("corvus.harrow.coupling_delivered"));
        assertEquals(List.of("corvus.harrow.coupling_delivered"), loaded.marks().under("corvus.harrow"));
        assertEquals(1, loaded.marks().all().size(), "a lapsed job keeps no mark, and a mark is written once");
    }

    @Test void settlementPaysTheCreditsTheOutcomeEarnedOnce() {
        var outcome = MissionOutcome.builder().sideObjectives(List.of(
                new SideObjectiveResult("coupling", "Coupling", State.DONE, "", "corvus.harrow.coupling_delivered", 1_500),
                new SideObjectiveResult("broker", "Broker", State.DONE, "", "corvus.teague.delivered", 3_000),
                new SideObjectiveResult("feed", "Feed", State.MISSED, "", ""))).build();
        assertEquals(4_500, outcome.sideObjectiveCredits());
        List<Float> paid = new ArrayList<>();
        MissionResolver.paySideObjectives(paid::add, outcome);
        assertEquals(List.of(4_500f), paid);
        paid.clear();
        MissionResolver.paySideObjectives(paid::add, MissionOutcome.builder().build());
        assertTrue(paid.isEmpty(), "nothing earned, nothing paid");
    }

    @Test void aMarkIsNamespacedAndPermanentUntilCleared() {
        var marks = new CampaignMarks();
        assertThrows(IllegalArgumentException.class, () -> marks.write("coupling"));
        assertThrows(IllegalArgumentException.class, () -> marks.write("Corvus.Harrow"));
        assertTrue(marks.write("corvus.teague.delivered"));
        assertFalse(marks.write("corvus.teague.delivered"));
        assertTrue(marks.clear("corvus.teague.delivered"));
        assertFalse(marks.has("corvus.teague.delivered"));
    }

    private static CampaignState roundTrip(CampaignState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (CampaignState) input.readObject();
        }
    }
}
