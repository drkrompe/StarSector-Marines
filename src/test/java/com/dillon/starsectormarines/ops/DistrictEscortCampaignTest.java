package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.DistrictEscortProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusDistrictEscortProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DistrictEscortCampaignTest {
    private HeadlessStrings strings;
    /** Reads the shipped copy, so what these tests pin is the text the player sees. */
    @BeforeEach void shippedCopy() throws Exception { strings = new HeadlessStrings(Path.of("mod")); }
    @AfterEach void restoreSettings() { strings.close(); }

    @Test void bothOfficesPostTheSameJobUnderTheirOwnLetterhead() {
        var custody = DistrictEscortCampaign.offer(new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY), "Agreus");
        var compact = DistrictEscortCampaign.offer(new AgreusDistrictEscortProgress(AgreusYardAgreement.YARD_COMPACT), "Agreus");
        for (var m : new Mission[]{custody, compact}) {
            assertTrue(DistrictEscortCampaign.isBattle(m)); assertFalse(AgreusArcCampaign.isBattle(m));
            assertEquals(MissionSource.RECURRING, m.source);
            assertEquals(24_000, m.payout);
            assertTrue(m.flavor.contains(DistrictEscortCampaign.terms()));
            assertTrue(m.flavor.contains("DE-1-1"));
        }
        assertEquals(custody.requirements, compact.requirements, "no hidden buff behind either letterhead");
        assertEquals(custody.requiredDrops, compact.requiredDrops);
        assertEquals(custody.tier, compact.tier); assertEquals(custody.risk, compact.risk);
        assertNotEquals(custody.flavor, compact.flavor);
        assertTrue(custody.flavor.startsWith("Dormer's office:")); assertTrue(compact.flavor.startsWith("Lenna Saye:"));
        assertNotEquals(DistrictEscortCampaign.Letterhead.RUSK_OFFICE.partyName(), DistrictEscortCampaign.Letterhead.LENNA_ASSOCIATION.partyName());
    }

    @Test void onlyTheRatifiedRoutesContactIsOfferedTheWork() {
        var custody = new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY);
        var compact = new AgreusDistrictEscortProgress(AgreusYardAgreement.YARD_COMPACT);
        var rusk = client(AgreusYardAgreement.RUSK_OFFICE); var lenna = client(AgreusYardAgreement.LENNA_ASSOCIATION);
        assertTrue(DistrictEscortCampaign.isContact(custody, rusk)); assertFalse(DistrictEscortCampaign.isContact(custody, lenna));
        assertTrue(DistrictEscortCampaign.isContact(compact, lenna)); assertFalse(DistrictEscortCampaign.isContact(compact, rusk));
        assertFalse(DistrictEscortCampaign.isContact(compact, null));
        assertNull(DistrictEscortCampaign.resolve(new AgreusArcProgress(), compact), "unsettled arc offers nothing");
    }

    @Test void settlementTakesOnlyItsOwnRecurringReceiptOnce() {
        var p = new AgreusDistrictEscortProgress(AgreusYardAgreement.YARD_COMPACT);
        var m = DistrictEscortCampaign.offer(p, "Agreus");
        var receipt = new DistrictEscortProgress.Report(true, true, true, true, false, 0, 1);
        var won = MissionOutcome.builder().mission(m).victory(true).payoutEarned(24_000).districtEscort(receipt).build();
        assertEquals(-1, DistrictEscortCampaign.settle(p, won), "not launched");
        assertTrue(p.begin(m.id, "agreus", "Agreus"));
        assertThrows(IllegalArgumentException.class, () -> DistrictEscortCampaign.offer(p, "Agreus"), "no second posting while one is out");
        assertEquals(-1, DistrictEscortCampaign.settle(p, MissionOutcome.builder().mission(m).victory(true).payoutEarned(24_000).build()));
        assertEquals(-1, DistrictEscortCampaign.settle(p, MissionOutcome.builder().mission(Mission.builder(m).source(MissionSource.STORY).build())
                .victory(true).payoutEarned(24_000).districtEscort(receipt).build()));
        assertEquals(-1, DistrictEscortCampaign.settle(p, MissionOutcome.builder().mission(Mission.builder(m).targetPlanetName("Jangala").build())
                .victory(true).payoutEarned(24_000).districtEscort(receipt).build()));
        assertEquals(24_000, DistrictEscortCampaign.settle(p, won));
        assertEquals(-1, DistrictEscortCampaign.settle(p, won));
        assertEquals(1, p.jobsCompleted());
        var next = DistrictEscortCampaign.offer(p, "Agreus");
        assertNotEquals(m.id, next.id); assertTrue(next.flavor.contains("DE-2-2"));
    }

    private static Client client(String contact) {
        return new Client("independent", "contact", OpeningBroker.CREST, null, false, null, -1L, contact);
    }
}
