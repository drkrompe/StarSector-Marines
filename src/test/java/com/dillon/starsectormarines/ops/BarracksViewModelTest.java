package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarracksViewModelTest {

    @Test
    void additiveArmorPatternsRenderFromTheCatalogRatherThanAnEnumHandle() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        roster.armory().unlockArmor("armor.aegis-composite");
        MarineSquad squad = roster.squads().stream()
                .filter(candidate -> !candidate.reserve()).findFirst().orElseThrow();
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(
                squad.id(), SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                SquadEquipmentDoctrines.CORPORATE_LINE_ARMOR));

        BarracksViewModel viewModel = new BarracksViewModel(
                new Reactor(), roster, () -> 100d);

        assertTrue(viewModel.musterRows().get().stream()
                .allMatch(row -> row.detail().contains("Aegis composite line suit")));
    }

    @Test
    void selectionBrowsesSquadsAndWoundedPersonnelLeaveTheRoomButKeepTheirBillet() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY * 2);
        BarracksViewModel viewModel = new BarracksViewModel(new Reactor(), roster, () -> 100d);

        assertEquals(2, viewModel.squadRows().get().size());
        assertEquals(MarineSquad.CAPACITY, viewModel.sceneMarines().size());

        MarineSquad selected = roster.squads().stream()
                .filter(squad -> !squad.reserve()).findFirst().orElseThrow();
        roster.applySoldierOutcome(Map.of(selected.memberIds().get(0),
                MarineSoldierStatus.WIA), 100f, 1.25f);
        viewModel.refresh();

        assertEquals(MarineSquad.CAPACITY - 1, viewModel.sceneMarines().size());
        assertEquals(MarineSquad.CAPACITY, viewModel.musterRows().get().size());
        assertTrue(viewModel.musterRows().get().get(0).classes().contains("wounded"));
        assertTrue(viewModel.quartersStatus().get().contains("1 in recovery"));

        viewModel.squadRows().get().get(1).select().run();
        assertTrue(viewModel.selectedSquadName().get().contains("02"));
        assertEquals(MarineSquad.CAPACITY, viewModel.sceneMarines().size());
    }

    @Test
    void aFormationWithNoOperationsSaysSoRatherThanShowingZeroes() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        BarracksViewModel viewModel = new BarracksViewModel(new Reactor(), roster, () -> 100d);

        assertTrue(viewModel.squadRows().get().get(0).record()
                .contains("No operations on record"));
        assertEquals("none", cellValue(viewModel, "operations"));
        assertEquals("no data", cellValue(viewModel, "accuracy"));
        assertEquals("none", cellValue(viewModel, "friendly"));
    }

    @Test
    void theServiceRecordReadsTheSquadCareerTheDebriefFolded() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().stream()
                .filter(candidate -> !candidate.reserve()).findFirst().orElseThrow();

        Map<String, MarineSoldierStatus> outcomes = new LinkedHashMap<>();
        Map<String, CombatTelemetryRow> telemetry = new LinkedHashMap<>();
        String survivor = squad.memberIds().get(0);
        String lost = squad.memberIds().get(1);
        outcomes.put(survivor, MarineSoldierStatus.ACTIVE);
        outcomes.put(lost, MarineSoldierStatus.KIA);
        telemetry.put(survivor, row(survivor, squad.id(), 40, 10, 9f, 5));
        telemetry.put(lost, row(lost, squad.id(), 10, 5, 0f, 2));
        roster.applySoldierOutcome(outcomes, 100f, 7f, telemetry, true);

        BarracksViewModel viewModel = new BarracksViewModel(new Reactor(), roster, () -> 100d);

        assertEquals("1  (1 won)", cellValue(viewModel, "operations"));
        assertEquals("7", cellValue(viewModel, "kills"));
        assertEquals("30%", cellValue(viewModel, "accuracy"), "15 of 50 rounds landed");
        assertEquals("1 of 2", cellValue(viewModel, "casualties"));
        assertEquals("9", cellValue(viewModel, "friendly"),
                "friendly fire is reported, not netted away");
        assertTrue(viewModel.squadRows().get().get(0).record().contains("1W / 1 ops"));
        assertTrue(viewModel.squadRows().get().get(0).record().contains("7 confirmed"));
    }

    private static String cellValue(BarracksViewModel viewModel, String key) {
        return viewModel.recordCells().get().stream()
                .filter(cell -> cell.id().endsWith(":" + key))
                .findFirst().orElseThrow().value();
    }

    private static CombatTelemetryRow row(String soldierId, String squadId,
                                          int fired, int hit, float friendly, int kills) {
        return new CombatTelemetryRow(1L, "marine", Faction.MARINE, UnitType.MARINE,
                soldierId, squadId, true, fired, hit, 100f, friendly, 20f, kills, 0);
    }
}
