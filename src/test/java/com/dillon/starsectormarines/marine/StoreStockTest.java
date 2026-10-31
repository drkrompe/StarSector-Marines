package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store is paced by the Kit Ladder alone: what its stations open is on every shelf, whatever the market's faction
 * or the company's standing, and nothing else is.
 */
class StoreStockTest {

    private static final String XIV_SHEET = "loadout-card:sheet:armor:hegemony-shock";

    /** A campaign that has won the Broken Supply Route junction, and every station before it. */
    private static Set<UnlockStation> throughJunction() {
        Set<UnlockStation> reached = new HashSet<>();
        UnlockStation junction = UnlockStation.byId("junction");
        for (UnlockStation station : UnlockStation.all()) {
            if (station.compareTo(junction) <= 0) reached.add(station);
        }
        return reached;
    }

    private static EquipmentAcquisitionEligibility.Progress progress(Set<UnlockStation> reached) {
        return new EquipmentAcquisitionEligibility.Progress(0, reached);
    }

    @Test
    void everyCardAndPatternAtAReachedStationIsOfferedAtEveryMarket() {
        Set<UnlockStation> reached = throughJunction();
        Set<String> cards = new HashSet<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (reached.contains(KitLadder.station(card.id()))) cards.add(card.id());
        }
        Set<String> patterns = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) {
            if (reached.contains(KitLadder.station(pattern.id()))) patterns.add(pattern.id());
        }
        assertFalse(cards.isEmpty());
        assertFalse(patterns.isEmpty());
        assertEquals(cards, Set.copyOf(StoreStock.cards(Set.of(), progress(reached)).marketCardIds()));
        assertEquals(patterns, Set.copyOf(StoreStock.patterns(Set.of(), progress(reached))));
        assertTrue(patterns.contains("mech-pattern:chassis:warden"), "the junction opens the Warden");
    }

    @Test
    void nothingAtAnUnreachedStationOrAtNoneIsOffered() {
        Set<UnlockStation> reached = throughJunction();
        StoreStock.StockPlan plan = StoreStock.cards(Set.of(), progress(reached));
        for (String id : plan.allCardIds()) assertTrue(reached.contains(KitLadder.station(id)), id);
        for (String id : StoreStock.patterns(Set.of(), progress(reached))) {
            assertTrue(reached.contains(KitLadder.station(id)), id);
        }
        EquipmentAcquisitionEligibility.Progress everywhere = progress(UnlockStation.all());
        for (String id : StoreStock.cards(Set.of(), everywhere).allCardIds()) {
            assertFalse(KitLadder.station(id).equals(UnlockStation.NONE), id);
        }
        for (String id : StoreStock.patterns(Set.of(), everywhere)) {
            assertFalse(KitLadder.station(id).equals(UnlockStation.NONE), id);
        }
    }

    @Test
    void aBoatPartIsOfferedOnlyOnceItsStationIsPassed() {
        String early = "lift_cradle";
        String junctionPart = "uprated_drive";
        assertEquals(UnlockStation.START, KitLadder.station(BoatParts.ladderId(early)));
        assertEquals(UnlockStation.byId("junction"), KitLadder.station(BoatParts.ladderId(junctionPart)));
        List<String> founding = StoreStock.boatParts(Set.of(), progress(Set.of(UnlockStation.START)));
        assertTrue(founding.contains(early));
        assertFalse(founding.contains(junctionPart), "the junction's part waits on the junction");
        List<String> later = StoreStock.boatParts(Set.of(), progress(throughJunction()));
        assertTrue(later.contains(junctionPart));
        assertFalse(StoreStock.boatParts(Set.of(junctionPart), progress(throughJunction())).contains(junctionPart),
                "a part the deck knows is not offered again");
        for (String starter : BoatParts.starterIds()) assertFalse(later.contains(starter), starter);
    }

    @Test
    void whatTheCompanyKnowsOrCarriesIsNotOffered() {
        EquipmentAcquisitionEligibility.Progress everywhere = progress(UnlockStation.all());
        Set<String> cards = Set.copyOf(StoreStock.cards(Set.of(), everywhere).marketCardIds());
        String known = cards.iterator().next();
        assertFalse(StoreStock.cards(Set.of(known), everywhere).allCardIds().contains(known));
        Set<String> patterns = Set.copyOf(StoreStock.patterns(Set.of(), everywhere));
        assertTrue(StoreStock.patterns(patterns, everywhere).isEmpty());
    }

    @Test
    void aRestrictedCardIsSoldAtItsStationLikeAnyOther() {
        StoreStock.StockPlan plan = StoreStock.cards(Set.of(), progress(UnlockStation.all()));
        assertTrue(plan.marketCardIds().contains(XIV_SHEET), "no warrant, no standing, no faction asked");
        assertFalse(StoreStock.cards(Set.of(), progress(throughJunction())).allCardIds().contains(XIV_SHEET),
                "but not before its station");
    }
}
