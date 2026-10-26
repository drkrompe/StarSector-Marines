package com.dillon.starsectormarines.campaign.refit;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipRefitsRefusalTest {
    private static final ShipCondition START = ShipCondition.battered();
    /** Enough of every material that only the rule under test can refuse. */
    private static final Map<String, Integer> PLENTY = Map.of(Commodities.METALS, 999, Commodities.HEAVY_MACHINERY, 999,
            Commodities.RARE_METALS, 999, Commodities.GAMMA_CORE, 9, Commodities.BETA_CORE, 9, Commodities.ALPHA_CORE, 9);

    private static String refuse(RefitOrder order, ShipCondition condition, boolean home, boolean active,
                                 boolean relief, boolean docked, int credits) {
        return ShipRefits.refusal(order, condition, home, active, relief, docked, credits, PLENTY, id -> id);
    }

    @Test void anEligibleOrderIsRefusedForEachMissingPrecondition() {
        var order = RefitOrder.STRUCTURE_RESTORED;
        assertNull(refuse(order, START, true, true, true, true, order.price));
        assertNotNull(refuse(order, START, false, true, true, true, order.price));
        assertNotNull(refuse(order, START, true, false, true, true, order.price));
        assertNotNull(refuse(order, START, true, true, false, true, order.price));
        assertNotNull(refuse(order, START, true, true, true, false, order.price));
        assertNotNull(refuse(order, START, true, true, true, true, order.price - 1));
        assertNotNull(refuse(order, ShipCondition.NONE, true, true, true, true, order.price));
    }

    @Test void aTierThatTakesACoreIsRefusedWithoutOneInCargo() {
        var order = RefitOrder.COMPUTE_CLUSTER;
        var ready = START.with(RefitTrack.COMPUTE, RefitBand.RESTORED).with(RefitTrack.POWER, RefitBand.IMPROVED_I)
                .with(RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED);
        assertNotNull(ShipRefits.refusal(order, ready, true, true, true, true, order.price, Map.of(), id -> id));
        assertNull(ShipRefits.refusal(order, ready, true, true, true, true, order.price,
                Map.of(Commodities.GAMMA_CORE, 1), id -> id));
        assertNull(ShipRefits.refusal(RefitOrder.STRUCTURE_RESTORED, START, true, true, true, true,
                RefitOrder.STRUCTURE_RESTORED.price, Map.of(), id -> id), "a tier with no core needs only credits");
    }

    @Test void repeatsSkippedTiersAndLockedCoresAreRefused() {
        var done = START.with(RefitTrack.STRUCTURE, RefitBand.RESTORED);
        assertNotNull(refuse(RefitOrder.STRUCTURE_RESTORED, done, true, true, true, true, 99_999));
        assertNotNull(refuse(RefitOrder.MESS_GALLEY, START, true, true, true, true, 99_999));
        assertNotNull(refuse(RefitOrder.LIFE_SUPPORT_RESTORED, START, true, true, true, true, 99_999));
        assertNull(refuse(RefitOrder.LIFE_SUPPORT_RESTORED,
                START.with(RefitTrack.POWER, RefitBand.RESTORED).with(RefitTrack.STRUCTURE, RefitBand.RESTORED), true, true, true, true, 99_999));
    }
}
