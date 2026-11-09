package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
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

    @Test void theRuinsCardStartsAtTheVaultAndShowsEveryLiftPoint() {
        var vault = new RuinsVaultObjective("R", "vault haul", "The vault", 0, 30f, 10f, List.of(
                new RuinsVaultObjective.LiftPoint("Landing area", true, new int[]{10, 10, 2, 2}),
                new RuinsVaultObjective.LiftPoint("East court", false, new int[]{10, 10, 14, 10})));
        var item = MissionObjectiveProjection.payload(vault, IDLE);
        assertEquals("R-source", item.focusMarkerId());
        assertTrue(item.instruction().contains("30.0s"), "the breach states the grade's time");
        assertEquals(3, item.markers().size(), "the vault, the landing area and the court");
        var landing = item.markers().stream().filter(m -> m.name().equals("Landing area")).findFirst().orElseThrow();
        var court = item.markers().stream().filter(m -> m.name().equals("East court")).findFirst().orElseThrow();
        assertNotEquals(landing.status(), court.status(), "an unheld court is not shown as an open lift point");
    }

    @Test void theRuinsCardTakesTheEntranceFirstAndMovesThePullOutMarkerToTheEntry() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(20, 20))) {
            var vault = new RuinsVaultObjective("R", "vault haul", "The vault", sim.getZoneGraph().zoneIdAt(10, 10),
                    100f, 10f, List.of(
                    new RuinsVaultObjective.LiftPoint("Landing area", true, new int[]{10, 10, 2, 2}),
                    new RuinsVaultObjective.LiftPoint("Entrance", RuinsVaultObjective.LiftPoint.Kind.ENTRANCE,
                            new int[]{10, 10, 16, 16})));
            var approach = MissionObjectiveProjection.payload(vault, IDLE);
            assertTrue(approach.steps().startsWith("1 Entrance"), approach.steps());
            assertEquals("TAKE THE ENTRANCE", approach.phase());
            assertTrue(marker(approach, "Landing area").radius() > 0, "the approach pulls out at the landing");
            assertEquals(0f, marker(approach, "Entrance").radius());

            sim.spawn(new EntitySpec("pointman", Faction.MARINE, UnitType.MARINE, 16, 16));
            vault.tick(sim);
            assertTrue(MissionObjectiveProjection.payload(vault, IDLE).progressText().startsWith("Entrance"),
                    "an apron being held shows its timer");
            for (int i = 0; i < 200 && vault.entry() < 0; i++) vault.tick(sim);

            var delve = MissionObjectiveProjection.payload(vault, IDLE);
            assertEquals("ENTRY HELD", delve.phase());
            assertTrue(delve.steps().startsWith("Entrance: done"), delve.steps());
            assertEquals(0f, marker(delve, "Landing area").radius());
            assertTrue(marker(delve, "Entrance").radius() > 0, "the delve pulls out at the entry");
        }
    }

    private static MissionObjectiveSnapshot.Marker marker(MissionObjectiveSnapshot.Item item, String name) {
        return item.markers().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    @Test void cohortReportsSimultaneousHoldsAndUsesTheMovingPayloadAsFocus() {
        var item = MissionObjectiveProjection.payload(new Cohort(false, false, 2, 1),
                new CivilianEvacuationSystem.Progress(false, 0, true, false, 2, 1, 3, 1));
        assertEquals("2 aboard / 5 awaiting rescue / 1 lost", item.progressText());
        assertTrue(item.blocker().contains("2 need close escort"));
        assertTrue(item.blocker().contains("1 has no route"));
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
