package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissionObjectiveProjectionTest {
    static final CivilianEvacuationSystem.Progress IDLE = new CivilianEvacuationSystem.Progress(false, 0, false, false, 0, 0, 0, 0);

    @Test void archiveHasRecoveryProgressAndNoInventedPickup() {
        var archive = new ColonyArchiveObjective(4, 5, 2);
        var item = MissionObjectiveProjection.payload(archive, IDLE);
        assertEquals(1, item.markers().size());
        assertEquals("Colony archive", item.focus().name());
        assertTrue(item.instruction().contains("5.0s"));
        assertFalse(item.instruction().contains("uncontested"), "archive law only requires Marine presence");
        assertTrue(item.blocker().contains("archive room"));
    }

    @Test void extractionStartsAtTheNamedSourceWithAnExplicitHoldCondition() {
        var payload = new ExtractionObjective("P", "Navigation recorder", 0, new int[]{8, 9, 2, 3});
        var item = MissionObjectiveProjection.payload(payload, IDLE);
        assertEquals(2, item.markers().size());
        assertEquals(8, item.focus().x());
        assertTrue(item.instruction().contains("4.0s"));
        assertTrue(item.blocker().contains("reach and hold"));
        assertEquals(0f, item.progress());
    }

    @Test void cohortReportsSimultaneousHoldsAndUsesTheMovingPayloadAsFocus() {
        var item = MissionObjectiveProjection.payload(new Cohort(false, false, 2, 1),
                new CivilianEvacuationSystem.Progress(false, 0, true, false, 2, 1, 3, 1));
        assertEquals("2 aboard / 5 awaiting rescue / 1 lost", item.progressText());
        assertTrue(item.blocker().contains("2 need close escort"));
        assertTrue(item.blocker().contains("1 have no route"));
        assertTrue(item.blocker().contains("3 waiting for pickup"));
        assertEquals("cohort-payload", item.focusMarkerId());
        assertEquals(15, item.focus().x());
    }

    @Test void partialSurvivalCompletionAndFailureOverrideStaleMovementHolds() {
        var holds = new CivilianEvacuationSystem.Progress(true, 60, true, false, 4, 2, 1, 0);
        var completed = MissionObjectiveProjection.payload(new Cohort(true, false, 3, 5), holds);
        assertEquals(MissionObjectiveSnapshot.State.COMPLETE, completed.state());
        assertEquals("", completed.blocker());
        assertEquals(1f, completed.progress());
        assertEquals("3 aboard / 0 awaiting rescue / 5 lost", completed.progressText());
        assertFalse(completed.markers().stream().anyMatch(m -> m.id().endsWith("-payload")));
        var failed = MissionObjectiveProjection.payload(new Cohort(false, true, 0, 8), holds);
        assertEquals("No survivors evacuated.", failed.blocker());
    }

    @Test void enemyCommandPicturesCannotAppearAsFriendlySquadActivity() {
        var enemy = new CommanderSnapshot<>(Faction.DEFENDER, "extraction", "guard", 0, 0, 0, 0, List.of(), List.of(), null);
        assertTrue(MissionObjectiveProjection.activities(enemy, id -> "Private").isEmpty());
        var details = new ExtractionCommandSnapshot(0, Faction.MARINE, "IN_TRANSIT", "P", "Package",
                1, 1, 2, 2, 3, 3, 4, 4, .3f, true, 7, false, false, ExtractionPayloadObjective.Failure.NONE,
                List.of(new ExtractionCommandSnapshot.SquadIntent(7, ExtractionCommandSnapshot.Role.LEFT_SCREEN,
                        "screen", null, 3, 3, true)));
        var own = new CommanderSnapshot<>(Faction.MARINE, "extraction", "escort", 0, 0, 1, 0, List.of(), List.of(), details);
        var row = MissionObjectiveProjection.activities(own, id -> "Kestrel").get(0);
        assertEquals("Kestrel", row.name());
        assertEquals("Protecting the left flank", row.task());
        assertEquals("In contact", row.status());
    }

    /** Small published-fact fixture: no world generation or simulation needed to test presentation. */
    record Cohort(boolean isComplete, boolean isFailed, int boardedElements, int lostElements) implements ExtractionPayloadObjective {
        public String payloadId() { return "cohort"; }
        public String payloadName() { return "Workers"; }
        public Kind payloadKind() { return Kind.COHORT; }
        public int sourceCellX() { return 2; }
        public int sourceCellY() { return 3; }
        public int egressCellX() { return 30; }
        public int egressCellY() { return 30; }
        public int payloadCellX() { return 15; }
        public int payloadCellY() { return 15; }
        public int initialElements() { return 8; }
        public int activeElements() { return 8 - boardedElements - lostElements; }
        public float normalizedProgress() { return boardedElements / 8f; }
        public Phase extractionPhase() { return isFailed ? Phase.FAILED : isComplete ? Phase.COMPLETE : Phase.IN_TRANSIT; }
        public Failure failureReason() { return isFailed ? Failure.LOST : Failure.NONE; }
        public boolean alarmActive() { return true; }
        public int alarmRaisedTick() { return 1; }
        public int controllingSquadId() { return 7; }
        public boolean escortPresent() { return true; }
        public Faction owningFaction() { return Faction.MARINE; }
        public void tick(BattleView sim) { }
        public String displayName() { return "Evacuate workers"; }
    }
}
