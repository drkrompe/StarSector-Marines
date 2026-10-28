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
        assertEquals(6500, EquipmentStore.price(EquipmentAccessTier.COMMON, 0.3f));
        assertEquals(13_000, EquipmentStore.price(EquipmentAccessTier.ADVANCED, 0.3f));
        assertEquals(26_000, EquipmentStore.price(EquipmentAccessTier.PRESTIGE, 0.3f));
        assertEquals(39_000, EquipmentStore.price(EquipmentAccessTier.RESTRICTED, 0.3f));
        assertEquals(EquipmentStore.BASE_PRICE, EquipmentStore.price(EquipmentAccessTier.COMMON, -1f),
                "a negative tariff is no discount");
        long previous = 0;
        for (EquipmentAccessTier tier : EquipmentAccessTier.values()) {
            assertTrue(EquipmentStore.price(tier, 0.3f) > previous, "a scarcer tier costs more: " + tier);
            previous = EquipmentStore.price(tier, 0.3f);
        }
    }

    @Test
    void shelfIsListedThenMarketThenLicensedAndEveryOfferIsPricedAtTheTariff() {
        EquipmentStore.Place place = new EquipmentStore.Place("hegemony", "chicomoztoc", 7, true, 0.3f, true);
        String listedId = LoadoutCards.all().get(0).id();

        List<EquipmentStore.Offer> shelf = EquipmentStore.shelf(place, 30L, Set.of(), Set.of(),
                FULL_ACCESS, List.of(listedId));

        assertEquals(listedId, shelf.get(0).id());
        assertEquals(EquipmentStore.Shelf.LISTED, shelf.get(0).shelf());
        assertTrue(shelf.stream().anyMatch(o -> o.shelf() == EquipmentStore.Shelf.MARKET));
        assertTrue(shelf.stream().anyMatch(o -> o.shelf() == EquipmentStore.Shelf.LICENSE));
        assertTrue(shelf.stream().anyMatch(o -> o.kind() == EquipmentStore.Kind.MECH_PATTERN),
                "a military market carries a mech pattern");
        for (EquipmentStore.Offer o : shelf) {
            EquipmentAccessTier tier = o.kind() == EquipmentStore.Kind.LOADOUT
                    ? LoadoutCard.parse(o.id()).accessTier() : MechPattern.parse(o.id()).accessTier();
            assertEquals(EquipmentStore.price(tier, 0.3f), o.price(), o.id());
        }
        assertEquals(shelf, EquipmentStore.shelf(place, 30L, Set.of(), Set.of(), FULL_ACCESS,
                List.of(listedId)), "the shelf is stable within a month");
    }

    @Test
    void anUnlicensedSmallMarketHasNoLicensedShelfAndNoMechPattern() {
        EquipmentStore.Place place = new EquipmentStore.Place("independent", "jangala", 3, false, 0.3f, false);

        List<EquipmentStore.Offer> shelf = EquipmentStore.shelf(place, 12L, Set.of(), Set.of(),
                FULL_ACCESS, List.of());

        assertFalse(shelf.isEmpty());
        assertTrue(shelf.stream().noneMatch(o -> o.shelf() == EquipmentStore.Shelf.LICENSE));
        assertTrue(shelf.stream().noneMatch(o -> o.kind() == EquipmentStore.Kind.MECH_PATTERN));
    }

    @Test
    void aLoadoutSaleChargesOnceAndTeachesTheArmory() {
        MarineArmory armory = new MarineArmory();
        Purse purse = new Purse(10_000, true);
        EquipmentStore.Offer offer = loadoutOffer(armory, 6500);

        assertEquals(EquipmentStore.Sale.SOLD, EquipmentStore.buy(offer, armory, new MechBay(), purse));

        assertEquals(3500, purse.credits);
        assertTrue(armory.knowsLoadoutCard(offer.id()));
        assertTrue(EquipmentStore.known(offer, armory, new MechBay()));
        assertEquals(EquipmentStore.Sale.ALREADY_KNOWN,
                EquipmentStore.buy(offer, armory, new MechBay(), purse));
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

        assertEquals(EquipmentStore.Sale.SOLD, EquipmentStore.buy(offer, new MarineArmory(), bay, purse));

        assertTrue(bay.knowsPattern(pattern.id()));
        assertEquals(5000, purse.credits);
    }

    @Test
    void aSaleTheCompanyCannotPayForOrCannotReachTeachesNothingAndChargesNothing() {
        MarineArmory armory = new MarineArmory();
        EquipmentStore.Offer offer = loadoutOffer(armory, 6500);

        Purse poor = new Purse(6499, true);
        assertEquals(EquipmentStore.Sale.CANNOT_AFFORD, EquipmentStore.buy(offer, armory, new MechBay(), poor));
        assertEquals(6499, poor.credits);

        Purse away = new Purse(99_999, false);
        assertEquals(EquipmentStore.Sale.NOT_AT_MARKET, EquipmentStore.buy(offer, armory, new MechBay(), away));
        assertEquals(99_999, away.credits);
        assertFalse(armory.knowsLoadoutCard(offer.id()));
    }

    @Test
    void aSaleWithNowhereToTeachIsRefusedBeforeItCharges() {
        Purse purse = new Purse(10_000, true);
        EquipmentStore.Offer offer = loadoutOffer(new MarineArmory(), 6500);

        assertEquals(EquipmentStore.Sale.UNAVAILABLE, EquipmentStore.buy(offer, null, new MechBay(), purse));
        assertEquals(10_000, purse.credits);
        EquipmentStore.Offer unknown = new EquipmentStore.Offer(
                EquipmentStore.Kind.LOADOUT, "not-a-card", EquipmentStore.Shelf.MARKET, 6500);
        assertEquals(EquipmentStore.Sale.UNAVAILABLE,
                EquipmentStore.buy(unknown, new MarineArmory(), new MechBay(), purse));
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
