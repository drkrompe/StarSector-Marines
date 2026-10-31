package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The store is paced by the Kit Ladder alone: what its stations open is on every shelf, and nothing else is. */
class StoreStockTest {

    private static final String XIV_SHEET = "loadout-card:sheet:armor:hegemony-shock";
    private static final List<String> FACTIONS = List.of("independent", "hegemony", "pirates", "tritachyon", "remnant");

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
        return new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(), reached);
    }

    @Test
    void everyCardAndPatternAtAReachedStationIsOfferedAtEveryMarket() {
        Set<UnlockStation> reached = throughJunction();
        Set<String> cards = new HashSet<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (card.accessTier() != EquipmentAccessTier.RESTRICTED && reached.contains(KitLadder.station(card.id()))) {
                cards.add(card.id());
            }
        }
        Set<String> patterns = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) {
            if (reached.contains(KitLadder.station(pattern.id()))) patterns.add(pattern.id());
        }
        assertFalse(cards.isEmpty());
        assertFalse(patterns.isEmpty());
        for (String faction : FACTIONS) {
            for (boolean licensed : new boolean[] {false, true}) {
                StoreStock.StockPlan plan = StoreStock.cards(faction, licensed, Set.of(), progress(reached));
                assertEquals(cards, Set.copyOf(plan.marketCardIds()), faction + " licensed=" + licensed);
            }
        }
        assertEquals(patterns, Set.copyOf(StoreStock.patterns(Set.of(), progress(reached))));
        assertTrue(patterns.contains("mech-pattern:chassis:warden"), "the junction opens the Warden");
    }

    @Test
    void nothingAtAnUnreachedStationOrAtNoneIsOffered() {
        Set<UnlockStation> reached = throughJunction();
        StoreStock.StockPlan plan = StoreStock.cards("independent", true, Set.of(), progress(reached));
        for (String id : plan.allCardIds()) assertTrue(reached.contains(KitLadder.station(id)), id);
        for (String id : StoreStock.patterns(Set.of(), progress(reached))) {
            assertTrue(reached.contains(KitLadder.station(id)), id);
        }
        EquipmentAcquisitionEligibility.Progress everywhere = progress(UnlockStation.all());
        for (String id : StoreStock.cards("independent", true, Set.of(), everywhere).allCardIds()) {
            assertFalse(KitLadder.station(id).equals(UnlockStation.NONE), id);
        }
        for (String id : StoreStock.patterns(Set.of(), everywhere)) {
            assertFalse(KitLadder.station(id).equals(UnlockStation.NONE), id);
        }
    }

    @Test
    void whatTheCompanyKnowsOrCarriesIsNotOffered() {
        EquipmentAcquisitionEligibility.Progress everywhere = progress(UnlockStation.all());
        Set<String> cards = Set.copyOf(StoreStock.cards("independent", false, Set.of(), everywhere).marketCardIds());
        String known = cards.iterator().next();
        assertFalse(StoreStock.cards("independent", false, Set.of(known), everywhere).allCardIds().contains(known));
        Set<String> patterns = Set.copyOf(StoreStock.patterns(Set.of(), everywhere));
        assertTrue(StoreStock.patterns(patterns, everywhere).isEmpty());
    }

    @Test
    void aRestrictedCardNeedsItsWarrantAndItsTraditionsLicenceShelf() {
        EquipmentAcquisitionEligibility.Progress warranted =
                new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(XIV_SHEET), UnlockStation.all());
        StoreStock.StockPlan hegemony = StoreStock.cards("hegemony", true, Set.of(), warranted);
        assertTrue(hegemony.licensedCardIds().contains(XIV_SHEET));
        assertFalse(hegemony.marketCardIds().contains(XIV_SHEET));
        assertFalse(StoreStock.cards("hegemony", false, Set.of(), warranted).allCardIds().contains(XIV_SHEET),
                "no standing, no licence shelf");
        assertFalse(StoreStock.cards("independent", true, Set.of(), warranted).allCardIds().contains(XIV_SHEET),
                "only the tradition that fields it issues it");
        assertFalse(StoreStock.cards("hegemony", true, Set.of(), progress(UnlockStation.all()))
                .allCardIds().contains(XIV_SHEET), "no warrant, no sale");
        for (String id : StoreStock.cards("hegemony", true, Set.of(), progress(UnlockStation.all())).allCardIds()) {
            assertFalse(LoadoutCard.parse(id).accessTier() == EquipmentAccessTier.RESTRICTED, id);
        }
    }

    @Test
    void theLicenceShelfOpensAtFavourableStanding() {
        assertFalse(EquipmentStore.hasLicenseAccess(RepLevel.NEUTRAL));
        assertTrue(EquipmentStore.hasLicenseAccess(RepLevel.FAVORABLE));
        assertTrue(EquipmentStore.hasLicenseAccess(RepLevel.COOPERATIVE));
    }
}
