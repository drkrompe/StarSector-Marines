package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.FoundingReserve;
import com.dillon.starsectormarines.ops.CompanyYardReview.Stage;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CompanyYardReviewTest {
    @Test void onlyTheBillInstallsAndBackNeverReturnsToIt() {
        var review = new CompanyYardReview(true);
        var orders = new AtomicInteger();
        var exits = new AtomicInteger();
        review.back();
        assertEquals(Stage.COST, review.stage());
        review.next(() -> { orders.incrementAndGet(); return "Ship left the fleet"; }, exits::incrementAndGet);
        assertEquals(Stage.COST, review.stage());
        assertEquals("Ship left the fleet", review.failure());
        review.next(() -> { orders.incrementAndGet(); return null; }, exits::incrementAndGet);
        assertEquals(Stage.RUNWAY, review.stage());
        assertNull(review.failure());
        review.back();
        assertEquals(Stage.RUNWAY, review.stage(), "Back is off once the conversion is paid");
        review.next(() -> fail("Reading cannot install"), exits::incrementAndGet);
        assertEquals(Stage.ROAD, review.stage());
        review.next(() -> fail("Reading cannot install"), exits::incrementAndGet);
        assertEquals(Stage.PAPERS, review.stage());
        review.back();
        assertEquals(Stage.ROAD, review.stage());
        review.next(() -> fail("Reading cannot install"), exits::incrementAndGet);
        review.next(() -> fail("The last page leaves"), exits::incrementAndGet);
        assertEquals(2, orders.get());
        assertEquals(1, exits.get());
    }

    @Test void aFallbackYardEndsAtTheRunway() {
        var review = new CompanyYardReview(false);
        var exits = new AtomicInteger();
        review.next(() -> null, exits::incrementAndGet);
        assertTrue(review.last());
        review.next(() -> fail("Paid once"), exits::incrementAndGet);
        assertEquals(1, exits.get());
        assertEquals(Stage.RUNWAY, review.stage());
    }

    @Test void pagesCarryTheStoryTheBillAndTheCommandersReplies() {
        var cost = model(Stage.COST, true, null);
        assertEquals("1 / 4", cost.get("page"));
        assertTrue(cost.get("reserve").toString().contains("Due from the ship's account: 0"));
        assertTrue(cost.get("reserve").toString().contains("Pressure test waived: 124"));
        assertTrue(cost.get("narrative").toString().contains("chits"));
        assertEquals("Pay her. Install the conversion.", cost.get("nextLabel"));
        assertTrue((boolean) cost.get("backDisabled"));
        assertFalse((boolean) cost.get("nextDisabled"));
        assertTrue((boolean) model(Stage.COST, true, "Unavailable").get("nextDisabled"));
        var runway = model(Stage.RUNWAY, true, null);
        assertTrue(runway.get("reserve").toString().contains("Runway: 22 days"));
        assertTrue((boolean) runway.get("backDisabled"));
        assertTrue(model(Stage.ROAD, true, null).get("dialogue").toString().contains("Mara"));
        assertFalse((boolean) model(Stage.ROAD, true, null).get("backDisabled"));
        var papers = model(Stage.PAPERS, true, null);
        assertEquals("4 / 4", papers.get("page"));
        assertTrue(papers.get("dialogue").toString().contains("ore dust"));
        assertTrue(papers.get("destination").toString().contains("Marine operations"));
        for (var stage : Stage.values()) {
            var props = model(stage, true, null);
            assertEquals(CompanyPrologueScreen.SKIP_OFF_CLASSES, props.get("skipClasses"));
            assertNotEquals(props.get("image"), model(stage == Stage.COST ? Stage.RUNWAY : Stage.COST, true, null).get("image"));
        }
        var fallback = model(Stage.COST, false, null);
        assertEquals("1 / 2", fallback.get("page"));
        assertFalse(fallback.get("dialogue").toString().contains("Nessa"));
        assertFalse(model(Stage.RUNWAY, false, null).get("dialogue").toString().contains("Nessa"));
        var cash = CompanyYardScreen.model(Stage.COST, true, "Second Wind", 1000, 0, null, null, true,
                () -> {}, () -> {}, () -> {});
        assertTrue(cash.get("reserve").toString().contains("Short by: 3,986"));
    }

    @Test void runwayIsTheStoryBurnAgainstTheAccount() {
        assertEquals(22, CompanyYardScreen.runwayDays(473));
        assertEquals(0, CompanyYardScreen.runwayDays(-50));
    }

    private static Map<String, Object> model(Stage stage, boolean jangala, String refusal) {
        return CompanyYardScreen.model(stage, jangala, "Second Wind", 473, stage == Stage.COST ? FoundingReserve.CREDITS : 0,
                refusal, null, true, () -> {}, () -> {}, () -> {});
    }
}
