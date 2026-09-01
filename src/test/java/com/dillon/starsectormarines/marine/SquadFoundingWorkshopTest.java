package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadFoundingWorkshopTest {

    @Test
    void foundingConsumesOneCompleteBillAndMobilizesTwelveNamedMarines() {
        MarineRoster roster = new MarineRoster();
        TestResources resources = TestResources.stocked(100);

        SquadFoundingWorkshop.Result result = new SquadFoundingWorkshop(
                roster, resources).foundSquad();

        assertTrue(result.succeeded());
        assertEquals(MarineSquad.CAPACITY, roster.manningCount(result.squad()));
        assertEquals(88, resources.available(Commodities.MARINES));
        assertEquals(76, resources.available(Commodities.SUPPLIES));
        assertEquals(88, resources.available(Commodities.HAND_WEAPONS));
        assertEquals(76, resources.available(Commodities.FOOD));
    }

    @Test
    void insufficientBillCreatesNoFormationAndConsumesNothing() {
        MarineRoster roster = new MarineRoster();
        TestResources resources = TestResources.stocked(11);

        SquadFoundingWorkshop.Result result = new SquadFoundingWorkshop(
                roster, resources).foundSquad();

        assertFalse(result.succeeded());
        assertEquals(0, roster.squads().stream().filter(squad -> !squad.reserve()).count());
        assertEquals(11, resources.available(Commodities.MARINES));
        assertEquals(11, resources.available(Commodities.SUPPLIES));
    }

    static final class TestResources implements SquadFoundingResources {
        private final Map<String, Integer> stock = new HashMap<>();

        static TestResources stocked(int quantity) {
            TestResources resources = new TestResources();
            for (SquadFoundingCost.Line line : SquadFoundingCost.STANDARD.lines()) {
                resources.stock.put(line.commodityId(), quantity);
            }
            return resources;
        }

        @Override public int available(String commodityId) {
            return stock.getOrDefault(commodityId, 0);
        }

        @Override public boolean spend(SquadFoundingCost cost) {
            if (!canAfford(cost)) return false;
            for (SquadFoundingCost.Line line : cost.lines()) {
                stock.merge(line.commodityId(), -line.quantity(), Integer::sum);
            }
            return true;
        }
    }
}
