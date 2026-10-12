package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration.Offer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatBayRestorationModelTest {
    @Test void inspectionSpendsNothingAndConfirmationRechecksTheLiveOrder() {
        Offer[] offer = {new Offer(true, 12000, null)};
        int[] inspections = {0}, commits = {0}, changes = {0};
        var model = new BoatBayRestorationModel(new Reactor(), () -> offer[0],
                () -> { commits[0]++; return offer[0].refusal(); },
                () -> inspections[0]++, () -> changes[0]++);
        model.row().get().action().run();
        assertEquals(1, inspections[0]);
        assertEquals(0, commits[0]);
        offer[0] = new Offer(true, 100, "Insufficient credits");
        model.row().get().action().run();
        assertEquals(1, commits[0]);
        assertEquals(0, changes[0]);
        assertTrue((boolean) model.row().get().markupProperty("disabled"));
        offer[0] = new Offer(true, 12000, null);
        model.refresh();
        model.row().get().action().run();
        assertEquals(1, changes[0]);
    }
}
