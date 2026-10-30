package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped Kit Ladder data holds together with the code that reads it: every card and pattern is placed once,
 * the start is what a new company knows, every sheet can be issued, and the store sells nothing before its station.
 */
class KitLadderTest {

    @Test
    void everyCardAndPatternHasOneStationAndEveryRowNamesOne() {
        Set<String> items = new HashSet<>();
        for (LoadoutCard card : LoadoutCards.all()) items.add(card.id());
        for (MechPattern pattern : MechPattern.all()) items.add(pattern.id());
        for (String id : items) assertTrue(KitLadder.stations().containsKey(id), id + " has no Kit Ladder station");
        for (String id : KitLadder.stations().keySet()) {
            assertTrue(LoadoutCard.parse(id) != null || MechPattern.parse(id) != null, id + " names nothing");
        }
    }

    @Test
    void theStartIsWhatANewCompanyKnows() {
        Set<String> start = new HashSet<>();
        for (Map.Entry<String, UnlockStation> row : KitLadder.stations().entrySet()) {
            if (row.getValue().equals(UnlockStation.START)) start.add(row.getKey());
        }
        Set<String> known = new HashSet<>(new MechBay().knownPatternIds());
        known.addAll(new MarineArmory().knownLoadoutCardIds());
        assertEquals(known, start);
    }

    @Test
    void everySheetIssuesFromItsOwnSuitsAndReliefsFreePickSitsAtRelief() {
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            assertDoesNotThrow(() -> ArmorIssueResolver.issueSheet(plan), plan.id());
        }
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            assertEquals("relief", KitLadder.station(new LoadoutCard(LoadoutCard.Kind.WEAPON, path.weapons).id()).id);
            assertEquals("relief", KitLadder.station(new LoadoutCard(LoadoutCard.Kind.SHEET, path.armor).id()).id);
        }
    }

    @Test
    void theStoreSellsAPatternOnlyOnceItsStationIsPassedAndNeverOneAtNone() {
        UnlockStation relief = UnlockStation.byId("relief");
        Set<UnlockStation> early = Set.of(UnlockStation.START, relief);
        var progress = new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(), early);
        for (FactionEquipmentSource source : List.of(FactionEquipmentSource.MARKET, FactionEquipmentSource.LICENSE)) {
            List<String> drawn = MechPatternPicker.pick("independent", source, 500, 3L, Set.of(), progress);
            assertFalse(drawn.isEmpty(), source.name());
            for (String id : drawn) assertEquals(relief, KitLadder.station(id), id);
        }
        List<String> late = MechPatternPicker.pick("independent", FactionEquipmentSource.MARKET, 500, 3L, Set.of(),
                new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(), UnlockStation.all()));
        for (String id : late) assertNotEquals(UnlockStation.NONE, KitLadder.station(id), id);
        String neverSold = KitLadder.stations().entrySet().stream()
                .filter(row -> row.getValue().equals(UnlockStation.NONE) && MechPattern.parse(row.getKey()) != null)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        assertTrue(MechPatternPicker.pick("independent", FactionEquipmentSource.RECOVERY, 500, 3L, Set.of(),
                new EquipmentAcquisitionEligibility.Progress(99, 99)).contains(neverSold),
                "recovery is not the store: it still reaches what the ladder never sells");
        assertEquals(UnlockStation.NONE, KitLadder.station("loadout-card:weapon:weapons:retired"),
                "an id an old save still carries reads as never sold");
    }

    @Test
    void aNewCampaignHasPassedOnlyTheStartAndTheOpeningAddsItsMissions() {
        assertEquals(Set.of(UnlockStation.START), UnlockStation.reached(id -> false, new CampaignState()));
        Set<UnlockStation> reached = UnlockStation.reached(
                OpeningOperationKind.RELIEF.missionId::equals, new CampaignState());
        assertEquals(Set.of(UnlockStation.START, UnlockStation.byId("relief")), reached);
        assertEquals(Set.of(UnlockStation.START), EquipmentAcquisitionEligibility.Progress.OPENING.stations());
    }
}
