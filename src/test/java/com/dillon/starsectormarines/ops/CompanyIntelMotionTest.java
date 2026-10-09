package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.CompanyHqViewModel.NewsPost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyIntelMotionTest {
    @Test void initialReportsAndReintroducedIdentitiesNeverClaimNewArrivals() {
        CompanyIntelMotion motion = new CompanyIntelMotion();
        NewsPost old = post("old", false, false);
        motion.observe(List.of(old), old);
        assertEquals(0f, motion.arrivalStrength(CompanyIntelMotion.CONFIRMED));
        motion.observe(List.of(), null);
        motion.observe(List.of(old), old);
        assertEquals(0f, motion.arrivalStrength(CompanyIntelMotion.CONFIRMED));
        assertEquals(1, motion.count(CompanyIntelMotion.CONFIRMED));
    }

    @Test void onlyAnUnseenReceivedIdentityPulsesItsActualChannel() {
        CompanyIntelMotion motion = new CompanyIntelMotion();
        motion.observe(List.of(), null);
        NewsPost rumor = post("rumor", true, false);
        NewsPost company = post("dispatch", false, true);
        motion.observe(List.of(rumor, company), rumor);
        assertEquals(1f, motion.arrivalStrength(CompanyIntelMotion.RUMOR));
        assertEquals(1f, motion.arrivalStrength(CompanyIntelMotion.COMPANY));
        assertEquals(0f, motion.arrivalStrength(CompanyIntelMotion.CONFIRMED));
        motion.advance(0.8f);
        float faded = motion.arrivalStrength(CompanyIntelMotion.RUMOR);
        assertTrue(faded > 0 && faded < 1);
        motion.observe(List.of(rumor, company), company);
        assertEquals(faded, motion.arrivalStrength(CompanyIntelMotion.RUMOR));
        motion.advance(4);
        assertEquals(0f, motion.arrivalStrength(CompanyIntelMotion.RUMOR));
    }

    @Test void selectionUsesStableIdentityAcrossRefreshAndOnlyChangesRestartReveal() {
        CompanyIntelMotion motion = new CompanyIntelMotion();
        NewsPost first = post("a", false, false);
        motion.observe(List.of(first), first);
        assertEquals(0f, motion.selectionReveal());
        motion.advance(0.4f);
        float reveal = motion.selectionReveal();
        motion.observe(List.of(post("a", false, false)), post("a", false, false));
        assertEquals(reveal, motion.selectionReveal());
        NewsPost second = post("b", true, false);
        motion.observe(List.of(first, second), second);
        assertEquals(0f, motion.selectionReveal());
        assertEquals(CompanyIntelMotion.RUMOR, motion.selectedChannel());
        motion.advance(1);
        assertEquals(1f, motion.selectionReveal());
        motion.observe(List.of(), null);
        assertEquals(-1, motion.selectedChannel());
    }

    @Test void invalidFrameDurationsDoNotPoisonTimingAndLongSessionsStayBounded() {
        CompanyIntelMotion motion = new CompanyIntelMotion();
        motion.advance(2);
        motion.advance(Float.NaN);
        motion.advance(Float.POSITIVE_INFINITY);
        motion.advance(-10);
        assertEquals(2f, motion.timeSeconds());
        motion.advance(1440);
        assertEquals(2f, motion.timeSeconds());
        motion.advance(Float.MAX_VALUE);
        assertTrue(motion.timeSeconds() >= 0 && motion.timeSeconds() < 1440);
        assertTrue(motion.phase(6, 0.2f) >= 0 && motion.phase(6, 0.2f) < 1);
        assertEquals(1f, motion.selectionReveal());
    }

    private static NewsPost post(String id, boolean rumor, boolean company) {
        return new NewsPost(id, "", "", "", "", rumor, company);
    }
}
