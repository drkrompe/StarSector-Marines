package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.dillon.starsectormarines.campaign.refit.FleetFacilities;
import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitOrder;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetEngineeringModelTest {
    private final List<RefitOrder> paid = new ArrayList<>();
    private final ShipCondition fresh = FleetFacilities.fresh();
    private final ShipConditionModel model = ShipConditionModel.fleet(new Reactor(), () -> fresh,
            order -> new ShipRefits.Offer(fresh, 99_999, Map.of(), null),
            order -> { paid.add(order); return null; }, CommodityPresentation.NONE, () -> { });

    @Test void theTreeIsAllThereIsAndCannotBeClosedOntoADeck() {
        assertTrue(model.deckless());
        assertTrue(model.systemsOpen());
        model.closeSystems();
        assertTrue(model.systemsOpen());
        assertEquals("systems-view", model.row().get().get("systemsClasses"));
    }

    @Test void aReviewedTierIsPaidOnAuthorizationWithNoDeckToStageIt() {
        model.selectTrack(RefitTrack.MESS);
        assertEquals("refit:" + RefitOrder.MESS_GALLEY.name(), model.row().get().get("order"), "the fleet starts past the deficit");
        Runnable act = (Runnable) model.row().get().get("action");
        act.run();
        assertTrue(paid.isEmpty(), "the first press only reviews");
        act.run();
        assertEquals(List.of(RefitOrder.MESS_GALLEY), paid);
    }

    @Test void progressCountsOnlyWhatLiesPastTheFleetStart() {
        assertEquals(0, ShipConditionModel.tiersDone(fresh, true));
        var gantry = fresh.with(RefitTrack.MECH_GANTRIES, RefitBand.RESTORED);
        assertEquals(1, ShipConditionModel.tiersDone(gantry, true));
        assertEquals(ShipConditionModel.tiersDone(fresh, false), ShipConditionModel.tiersDone(gantry, false),
                "the Second Wind's count never includes the fleet's rooms");
    }
}
