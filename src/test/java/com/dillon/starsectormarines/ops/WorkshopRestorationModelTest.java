package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration.Offer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopRestorationModelTest {
    @Test void everyStageNeedsItsOwnReview() {
        Offer[] live = {new Offer(WorkshopState.BURIED, 20000, null)};
        int[] inspected = {0}, paid = {0}, changed = {0};
        var model = new WorkshopRestorationModel(new Reactor(), () -> live[0], state -> {
            paid[0]++; live[0] = new Offer(state.next(), 19000, null); return null;
        }, () -> inspected[0]++, () -> changed[0]++);
        model.row().get().action().run();
        assertEquals(1, inspected[0]); assertEquals(0, paid[0]);
        model.row().get().action().run();
        assertEquals(1, paid[0]); assertEquals(1, changed[0]);
        model.row().get().action().run();
        assertEquals(2, inspected[0]); assertEquals(1, paid[0]);
    }
}
