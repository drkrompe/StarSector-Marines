package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration.Work;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipRestorationOrdersTest {
    @Test void welfareRequiresRestoredMessOnTheActiveOriginalHome() {
        var restored = new ShipRestoration(true, false, false, false, true);
        assertEquals(1.25f, CompanyShipRestoration.moraleRecovery(restored, true));
        assertEquals(1f, CompanyShipRestoration.moraleRecovery(restored, false));
        assertEquals(1f, CompanyShipRestoration.moraleRecovery(ShipRestoration.WRECK, true));
        assertEquals(1f, CompanyShipRestoration.moraleRecovery(ShipRestoration.NONE, true));
        assertNotNull(CompanyShipRestoration.refusal(Work.MESS_HALL, restored, true, true, true, true, 20000));
        assertNull(CompanyShipRestoration.refusal(Work.POWER, restored, true, true, true, true, 20000));
    }

    @Test void ordersRequireOwnedActiveHomeMissionYardAndFullPriceAndRejectRepeats() {
        for (var work : Work.values()) {
            assertNotNull(CompanyShipRestoration.refusal(work, ShipRestoration.WRECK, false, true, true, true, 20000));
            assertNotNull(CompanyShipRestoration.refusal(work, ShipRestoration.WRECK, true, false, true, true, 20000));
            assertNotNull(CompanyShipRestoration.refusal(work, ShipRestoration.WRECK, true, true, false, true, 20000));
            assertNotNull(CompanyShipRestoration.refusal(work, ShipRestoration.WRECK, true, true, true, false, 20000));
            assertNotNull(CompanyShipRestoration.refusal(work, ShipRestoration.WRECK, true, true, true, true, work.price - 1));
            assertNull(CompanyShipRestoration.refusal(work, ShipRestoration.WRECK, true, true, true, true, work.price));
            assertNotNull(CompanyShipRestoration.refusal(work, new ShipRestoration(true, true, true, true), true, true, true, true, 20000));
        }
    }
}
