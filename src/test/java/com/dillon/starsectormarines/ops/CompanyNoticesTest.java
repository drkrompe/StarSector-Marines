package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.marine.MarineRoster;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class CompanyNoticesTest {
    @Test void unlocksAreIndependentAndReviewDoesNotCompleteOrPurchase() {
        var roster = new MarineRoster();
        assertTrue(CompanyNotices.unread(roster, BoatBayState.DAMAGED).isEmpty());
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertTrue(CompanyNotices.unread(roster, BoatBayState.DAMAGED).contains(CompanyNotices.Notice.SECOND_BAY));
        CompanyNotices.Notice.SECOND_BAY.review(roster);
        assertFalse(CompanyNotices.unread(roster, BoatBayState.DAMAGED).contains(CompanyNotices.Notice.SECOND_BAY));
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertTrue(CompanyNotices.unread(roster, BoatBayState.DAMAGED).contains(CompanyNotices.Notice.SECONDHAND_WINGS));
        CompanyNotices.Notice.SECONDHAND_WINGS.review(roster);
        assertFalse(roster.boatDeck().secondhandWingsPurchased());
        assertTrue(CompanyNotices.unread(roster, BoatBayState.DAMAGED).contains(CompanyNotices.Notice.FIRST_REFIT));
    }

    @Test void receiptsSurviveSaveAndCompletedOpportunitiesNeedNoReceipt() throws Exception {
        var roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        CompanyNotices.Notice.FIRST_REFIT.review(roster);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(roster); }
        MarineRoster loaded;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            loaded = (MarineRoster) in.readObject();
        }
        assertTrue(loaded.hasReviewedCompanyNotice(CompanyNotices.Notice.FIRST_REFIT.id));
        assertTrue(CompanyNotices.unread(loaded, BoatBayState.RESTORED).isEmpty());
    }
}
