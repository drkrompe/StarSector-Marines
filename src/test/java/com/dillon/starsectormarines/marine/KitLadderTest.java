package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Kit Ladder places every card and pattern once, and the store sells nothing before its station. */
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
            if (row.getValue() == UnlockStation.START) start.add(row.getKey());
        }
        Set<String> known = new HashSet<>(new MechBay().knownPatternIds());
        known.addAll(new MarineArmory().knownLoadoutCardIds());
        assertEquals(known, start);
    }

    @Test
    void theStoreSellsAPatternOnlyOnceItsStationIsPassedAndNeverOneAtNone() {
        Set<UnlockStation> early = EnumSet.of(UnlockStation.START, UnlockStation.RELIEF);
        var progress = new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(), early);
        for (FactionEquipmentSource source : List.of(FactionEquipmentSource.MARKET, FactionEquipmentSource.LICENSE)) {
            List<String> drawn = MechPatternPicker.pick("independent", source, 500, 3L, Set.of(), progress);
            assertFalse(drawn.isEmpty(), source.name());
            for (String id : drawn) assertEquals(UnlockStation.RELIEF, KitLadder.station(id), id);
        }
        List<String> late = MechPatternPicker.pick("independent", FactionEquipmentSource.MARKET, 500, 3L, Set.of(),
                new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(), UnlockStation.all()));
        for (String id : late) assertNotEquals(UnlockStation.NONE, KitLadder.station(id), id);
        String neverSold = KitLadder.stations().entrySet().stream()
                .filter(row -> row.getValue() == UnlockStation.NONE && MechPattern.parse(row.getKey()) != null)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        assertTrue(MechPatternPicker.pick("independent", FactionEquipmentSource.RECOVERY, 500, 3L, Set.of(),
                new EquipmentAcquisitionEligibility.Progress(99, 99)).contains(neverSold),
                "recovery is not the store: it still reaches what the ladder never sells");
    }

    @Test
    void aNewCampaignHasPassedOnlyTheStartAndTheOpeningAddsItsMissions() {
        assertEquals(EnumSet.of(UnlockStation.START), UnlockStation.reached(id -> false, new CampaignState()));
        Set<UnlockStation> reached = UnlockStation.reached(
                OpeningOperationKind.RELIEF.missionId::equals, new CampaignState());
        assertEquals(EnumSet.of(UnlockStation.START, UnlockStation.RELIEF), reached);
        assertNotNull(EquipmentAcquisitionEligibility.Progress.OPENING.stations());
        assertEquals(Set.of(UnlockStation.START), EquipmentAcquisitionEligibility.Progress.OPENING.stations());
    }
}
