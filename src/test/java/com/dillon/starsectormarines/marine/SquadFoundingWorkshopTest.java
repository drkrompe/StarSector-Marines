package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadFoundingWorkshopTest {

    @Test
    void foundingTakesTwelveMarinesAndOnePriceAndMobilizesThem() {
        MarineRoster roster = new MarineRoster();
        TestStores stores = TestStores.forSquads(2);
        stores.stock.put(Commodities.HAND_WEAPONS, 100);

        SquadFoundingWorkshop.Result result = new SquadFoundingWorkshop(roster, stores).foundSquad();

        assertTrue(result.succeeded());
        assertEquals(MarineSquad.CAPACITY, roster.manningCount(result.squad()));
        assertEquals(MarineSquad.CAPACITY, stores.available(Commodities.MARINES));
        assertEquals(SquadFoundingCost.STANDARD.credits(), stores.credits);
        assertEquals(100, stores.available(Commodities.HAND_WEAPONS));
    }

    @Test
    void aShortPurseOrHoldCreatesNoFormationAndConsumesNothing() {
        MarineRoster roster = new MarineRoster();
        TestStores poor = TestStores.forSquads(1);
        poor.credits--;
        TestStores undermanned = TestStores.forSquads(1);
        undermanned.stock.merge(Commodities.MARINES, -1, Integer::sum);

        for (TestStores stores : new TestStores[] {poor, undermanned}) {
            long credits = stores.credits;
            int marines = stores.available(Commodities.MARINES);
            SquadFoundingWorkshop.Result result = new SquadFoundingWorkshop(roster, stores).foundSquad();
            assertEquals(SquadFoundingWorkshop.Status.INSUFFICIENT_RESOURCES, result.status());
            assertEquals(credits, stores.credits);
            assertEquals(marines, stores.available(Commodities.MARINES));
        }
        assertEquals(0, roster.squads().stream().filter(squad -> !squad.reserve()).count());
    }

    @Test
    void awayFromAPortNothingIsFoundedOrSpent() {
        MarineRoster roster = new MarineRoster();
        TestStores stores = TestStores.forSquads(1).away();

        SquadFoundingWorkshop.Result result = new SquadFoundingWorkshop(roster, stores).foundSquad();

        assertEquals(SquadFoundingWorkshop.Status.NO_MARKET, result.status());
        assertEquals(0, stores.spends);
        assertFalse(stores.canBuy(SquadFoundingCost.STANDARD));
        assertTrue(stores.canAfford(SquadFoundingCost.STANDARD));
    }
}
