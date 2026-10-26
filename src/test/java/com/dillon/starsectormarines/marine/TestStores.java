package com.dillon.starsectormarines.marine;

import java.util.HashMap;
import java.util.Map;

/** Squad-founding stores for tests: cargo counts and a credit balance. A spend takes the whole bill or nothing; it counts the bills it paid. */
public final class TestStores implements SquadFoundingResources {
    public final Map<String, Integer> stock = new HashMap<>();
    public long credits;
    public int spends;

    /** Enough marines and credits for {@code squads} founding bills. */
    public static TestStores forSquads(int squads) {
        TestStores stores = new TestStores();
        for (SquadFoundingCost.Line line : SquadFoundingCost.STANDARD.lines())
            stores.stock.put(line.commodityId(), squads * line.quantity());
        stores.credits = (long) squads * SquadFoundingCost.STANDARD.credits();
        return stores;
    }

    @Override public int available(String commodityId) { return stock.getOrDefault(commodityId, 0); }
    @Override public long credits() { return credits; }
    @Override public String commodityName(String commodityId) { return commodityId; }
    @Override public String commodityIcon(String commodityId) { return ""; }

    @Override public boolean spend(SquadFoundingCost cost) {
        if (!canAfford(cost)) return false;
        for (SquadFoundingCost.Line line : cost.lines()) stock.merge(line.commodityId(), -line.quantity(), Integer::sum);
        credits -= cost.credits();
        spends++;
        return true;
    }
}
