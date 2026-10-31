package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentStoreTest {

    private static final EquipmentAcquisitionEligibility.Progress FULL_ACCESS =
            new EquipmentAcquisitionEligibility.Progress(15, 20);

    @Test
    void priceScalesWithTheAccessTierAndTheMarketTariff() {
        assertEquals(EquipmentStore.BASE_PRICE, EquipmentStore.price(EquipmentAccessTier.COMMON, 0f));
        for (EquipmentAccessTier tier : EquipmentAccessTier.values()) {
            assertEquals(Math.round(EquipmentStore.BASE_PRICE * EquipmentStore.tierMultiplier(tier) * 1.3),
                    EquipmentStore.price(tier, 0.3f), "the tariff is added on top of the tier's price: " + tier);
        }
        assertEquals(EquipmentStore.BASE_PRICE, EquipmentStore.price(EquipmentAccessTier.COMMON, -1f),
                "a negative tariff is no discount");
        long previous = 0;
        for (EquipmentAccessTier tier : EquipmentAccessTier.values()) {
            assertTrue(EquipmentStore.price(tier, 0.3f) > previous, "a scarcer tier costs more: " + tier);
            previous = EquipmentStore.price(tier, 0.3f);
        }
    }

    @Test
    void shelfIsListedFirstThenBySlotAndEveryOfferIsPricedAtTheTariff() {
        EquipmentStore.Place place = new EquipmentStore.Place("hegemony", "chicomoztoc", 7, 0.3f, true);
        String listedId = LoadoutCards.all().get(0).id();

        List<EquipmentStore.Offer> shelf = EquipmentStore.shelf(place, 30L, Set.of(), Set.of(), Set.of(),
                FULL_ACCESS, List.of(listedId));

        assertEquals(listedId, shelf.get(0).id());
        assertEquals(EquipmentStore.Shelf.LISTED, shelf.get(0).shelf());
        assertEquals(1, shelf.stream().filter(o -> o.id().equals(listedId)).count(), "a listing is never offered twice");
        assertTrue(shelf.stream().anyMatch(o -> o.shelf() == EquipmentStore.Shelf.MARKET));
        assertTrue(shelf.stream().anyMatch(o -> o.kind() == EquipmentStore.Kind.MECH_PATTERN));
        assertTrue(shelf.stream().anyMatch(o -> o.kind() == EquipmentStore.Kind.BOAT_PART),
                "a sizeable market carries boat parts");
        assertTrue(shelf.stream().allMatch(o -> o.slot() != null), "every offer is filed in a store slot");
        for (int i = 2; i < shelf.size(); i++) {
            assertTrue(shelf.get(i - 1).slot().ordinal() <= shelf.get(i).slot().ordinal(), "sorted by slot");
        }
        for (EquipmentStore.Offer o : shelf) {
            EquipmentAccessTier tier = o.kind() == EquipmentStore.Kind.LOADOUT
                    ? LoadoutCard.parse(o.id()).accessTier()
                    : o.kind() == EquipmentStore.Kind.BOAT_PART ? BoatParts.accessTier(o.id())
                    : MechPattern.parse(o.id()).accessTier();
            assertEquals(EquipmentStore.price(tier, 0.3f), o.price(), o.id());
        }
        assertEquals(shelf, EquipmentStore.shelf(place, 30L, Set.of(), Set.of(), Set.of(), FULL_ACCESS,
                List.of(listedId)), "the shelf is stable within a month");
    }

    @Test
    void aSmallUnlicensedMarketStillCarriesEveryPatternTheLadderOpens() {
        EquipmentStore.Place place = new EquipmentStore.Place("independent", "jangala", 3, 0.3f, false);

        List<EquipmentStore.Offer> shelf = EquipmentStore.shelf(place, 12L, Set.of(), Set.of(), Set.of(),
                FULL_ACCESS, List.of());

        assertTrue(shelf.stream().noneMatch(o -> o.shelf() == EquipmentStore.Shelf.LICENSE));
        assertEquals(Set.copyOf(StoreStock.patterns(Set.of(), FULL_ACCESS)), Set.copyOf(shelf.stream()
                .filter(o -> o.kind() == EquipmentStore.Kind.MECH_PATTERN).map(EquipmentStore.Offer::id).toList()));
    }

    @Test
    void aLoadoutSaleChargesOnceAndTeachesTheArmory() {
        MarineArmory armory = new MarineArmory();
        Purse purse = new Purse(10_000, true);
        EquipmentStore.Offer offer = loadoutOffer(armory, 6500);

        assertEquals(EquipmentStore.Sale.SOLD, EquipmentStore.buy(offer, new EquipmentStore.Holdings(armory, new MechBay(), null), purse));

        assertEquals(3500, purse.credits);
        assertTrue(armory.knowsLoadoutCard(offer.id()));
        assertTrue(EquipmentStore.known(offer, new EquipmentStore.Holdings(armory, new MechBay(), null)));
        assertEquals(EquipmentStore.Sale.ALREADY_KNOWN,
                EquipmentStore.buy(offer, new EquipmentStore.Holdings(armory, new MechBay(), null), purse));
        assertEquals(3500, purse.credits, "a card already known is not charged again");
    }

    @Test
    void aMechPatternSaleTeachesTheBay() {
        MechBay bay = new MechBay();
        Purse purse = new Purse(10_000, true);
        MechPattern pattern = MechPattern.offerable().stream()
                .filter(p -> !bay.knowsPattern(p.id())).findFirst().orElseThrow();
        EquipmentStore.Offer offer = new EquipmentStore.Offer(
                EquipmentStore.Kind.MECH_PATTERN, pattern.id(), EquipmentStore.Shelf.MARKET, 5000);

        assertEquals(EquipmentStore.Sale.SOLD, EquipmentStore.buy(offer, new EquipmentStore.Holdings(new MarineArmory(), bay, null), purse));

        assertTrue(bay.knowsPattern(pattern.id()));
        assertEquals(5000, purse.credits);
    }

    @Test
    void aSaleTheCompanyCannotPayForOrCannotReachTeachesNothingAndChargesNothing() {
        MarineArmory armory = new MarineArmory();
        EquipmentStore.Offer offer = loadoutOffer(armory, 6500);

        Purse poor = new Purse(6499, true);
        assertEquals(EquipmentStore.Sale.CANNOT_AFFORD, EquipmentStore.buy(offer, new EquipmentStore.Holdings(armory, new MechBay(), null), poor));
        assertEquals(6499, poor.credits);

        Purse away = new Purse(99_999, false);
        assertEquals(EquipmentStore.Sale.NOT_AT_MARKET, EquipmentStore.buy(offer, new EquipmentStore.Holdings(armory, new MechBay(), null), away));
        assertEquals(99_999, away.credits);
        assertFalse(armory.knowsLoadoutCard(offer.id()));
    }

    @Test
    void aBoatPartSaleTeachesTheDeckAndUnlocksTheWorkshop() {
        BoatDeck deck = new BoatDeck();
        String part = BoatParts.offerable().get(0);
        Purse purse = new Purse(10_000, true);
        EquipmentStore.Offer offer = new EquipmentStore.Offer(
                EquipmentStore.Kind.BOAT_PART, part, EquipmentStore.Shelf.MARKET, 4000);
        assertFalse(deck.knowsPart(part));

        assertEquals(EquipmentStore.Sale.SOLD, EquipmentStore.buy(offer,
                new EquipmentStore.Holdings(null, null, deck), purse));

        assertTrue(deck.knowsPart(part));
        assertEquals(6000, purse.credits);
        assertEquals(EquipmentStore.Sale.ALREADY_KNOWN, EquipmentStore.buy(offer,
                new EquipmentStore.Holdings(null, null, deck), purse));
        assertEquals(EquipmentStore.Sale.UNAVAILABLE, EquipmentStore.buy(offer,
                new EquipmentStore.Holdings(null, null, null), purse));
        assertEquals(6000, purse.credits);
    }

    @Test
    void aSaleWithNowhereToTeachIsRefusedBeforeItCharges() {
        Purse purse = new Purse(10_000, true);
        EquipmentStore.Offer offer = loadoutOffer(new MarineArmory(), 6500);

        assertEquals(EquipmentStore.Sale.UNAVAILABLE, EquipmentStore.buy(offer, new EquipmentStore.Holdings(null, new MechBay(), null), purse));
        assertEquals(10_000, purse.credits);
        EquipmentStore.Offer unknown = new EquipmentStore.Offer(
                EquipmentStore.Kind.LOADOUT, "not-a-card", EquipmentStore.Shelf.MARKET, 6500);
        assertEquals(EquipmentStore.Sale.UNAVAILABLE,
                EquipmentStore.buy(unknown, new EquipmentStore.Holdings(new MarineArmory(), new MechBay(), null), purse));
        assertEquals(10_000, purse.credits);
    }

    private static EquipmentStore.Offer loadoutOffer(MarineArmory armory, long price) {
        LoadoutCard card = LoadoutCards.all().stream()
                .filter(c -> !armory.knowsLoadoutCard(c.id())).findFirst().orElseThrow();
        return new EquipmentStore.Offer(EquipmentStore.Kind.LOADOUT, card.id(),
                EquipmentStore.Shelf.MARKET, price);
    }

    private static final class Purse implements FabricationResources {
        long credits;
        final boolean atMarket;

        Purse(long credits, boolean atMarket) {
            this.credits = credits;
            this.atMarket = atMarket;
        }

        @Override public long credits() { return credits; }
        @Override public boolean atMarket() { return atMarket; }

        @Override public boolean spend(FabricationCost cost) {
            if (cost == null || credits < cost.credits()) return false;
            credits -= cost.credits();
            return true;
        }

        @Override public boolean credit(FabricationCost refund) {
            credits += refund.credits();
            return true;
        }
    }
}
