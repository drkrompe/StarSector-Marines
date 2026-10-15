package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.CompanyYardReview.Stage;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CompanyYardReviewTest {
    @Test void readingAndBackingOutSpendNothingAndOnlySuccessfulInstallShowsReceipt() {
        var review = new CompanyYardReview();
        var orders = new AtomicInteger();
        var exits = new AtomicInteger();
        review.next(() -> { orders.incrementAndGet(); return null; }, exits::incrementAndGet);
        assertEquals(Stage.ORDER, review.stage());
        assertEquals(0, orders.get());
        review.back();
        assertEquals(Stage.SURVEY, review.stage());
        review.next(() -> fail("Reading cannot install"), exits::incrementAndGet);
        review.next(() -> { orders.incrementAndGet(); return "Ship left the fleet"; }, exits::incrementAndGet);
        assertEquals(Stage.ORDER, review.stage());
        assertEquals("Ship left the fleet", review.failure());
        review.next(() -> { orders.incrementAndGet(); return null; }, exits::incrementAndGet);
        assertEquals(Stage.COMPLETE, review.stage());
        assertNull(review.failure());
        review.back();
        assertEquals(Stage.COMPLETE, review.stage());
        review.next(() -> fail("Receipt cannot install twice"), exits::incrementAndGet);
        assertEquals(2, orders.get());
        assertEquals(1, exits.get());
    }

    @Test void personalContactAndDamageClaimsBelongToTheFoundingVisit() {
        var jangala = model(Stage.SURVEY, true, null);
        assertTrue(jangala.get("dialogue").toString().contains("Nessa"));
        assertFalse(model(Stage.SURVEY, false, null).get("dialogue").toString().contains("Nessa"));
        var order = model(Stage.ORDER, true, null);
        assertTrue(order.get("dialogue").toString().contains("Wayline road"));
        assertTrue(order.get("reserve").toString().contains("Due from fleet: 0"));
        assertFalse((boolean) order.get("nextDisabled"));
        assertTrue((boolean) model(Stage.ORDER, true, "Unavailable").get("nextDisabled"));
        var complete = model(Stage.COMPLETE, true, null);
        assertTrue(complete.get("dialogue").toString().contains("Engineering"));
        assertTrue(complete.get("dialogue").toString().contains("still shut"));
        assertNotEquals(jangala.get("image"), complete.get("image"));
        assertEquals("Return to port", complete.get("nextLabel"));
        assertTrue((boolean) complete.get("backDisabled"));
    }
    private static Map<String, Object> model(Stage stage, boolean jangala, String refusal) {
        return CompanyYardScreen.model(stage, jangala, "Second Wind", 0, 5000, refusal, null, true,
                () -> {}, () -> {}, () -> {});
    }
}
