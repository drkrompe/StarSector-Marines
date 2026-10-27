package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.battle.story.SmallWarProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import java.util.ArrayList;
import java.util.List;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Chapter 5 offer, settlement routing and debrief, read only from frozen outcomes. */
class SmallWarSettlementTest {
    private static HeadlessStrings copy;
    @BeforeAll static void shippedCopy() throws Exception { copy = new HeadlessStrings(Path.of("mod")); }
    @AfterAll static void closeCopy() { copy.close(); }
    @Test void theOfferNamesTheRatifiedRouteAndIsAbsentBeforeRatification() {
        var signed = afterYard();
        assertTrue(signed.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertTrue(AgreusArcProgress.termsOfProtection(AgreusArcCampaign.offer(signed, "Agreus").id), "Chapter 4 comes first");
        assertEquals("LATCHKEY HANDOVER", AgreusArcCampaign.chapterPlace(signed));
        assertTrue(signed.ratifyAgreement(AgreusYardAgreement.YARD_COMPACT));
        var m = AgreusArcCampaign.offer(signed, "Agreus");
        assertEquals("BLACK SLUICE", AgreusArcCampaign.chapterPlace(signed), "the board names the chapter's own ground");
        assertEquals("NORTH WEIR", AgreusArcCampaign.chapterPlace(new AgreusArcProgress()));
        assertEquals("A Small War", m.name);
        assertEquals(44000, m.payout);
        assertEquals(OperationTier.ESTABLISHED, m.tier);
        assertTrue(m.flavor.contains("Yard Compact"));
        assertTrue(m.flavor.contains("Mika Drent"), "Castle cites the attributed source of the target");
        assertFalse(m.requirements.contains("12,000"));
        assertEquals(AgreusArcProgress.SMALL_WAR_PREFIX + 1, m.id);
    }

    @Test void custodyVictoryPaysThePremiumThroughItsOwnSinkOnce() {
        var p = ratified(AgreusYardAgreement.HOUSE_CUSTODY);
        var m = AgreusArcCampaign.offer(p, "Agreus");
        assertTrue(m.requirements.contains("12,000-credit House Custody premium"));
        assertTrue(p.begin(m.id, "agreus", "Agreus"));
        var premiums = new ArrayList<Integer>();
        assertEquals(-1, AgreusArcCampaign.settleSmallWar(p, MissionOutcome.builder().mission(m).victory(true).payoutEarned(44000).build(), premiums::add),
                "an outcome without the frozen Black Sluice report cannot settle");
        assertEquals(-1, AgreusArcCampaign.settleSmallWar(p, outcome(Mission.builder(m).source(MissionSource.RECURRING).build(), true, report(true, true, 0)), premiums::add));
        var victory = outcome(m, true, report(true, true, 0));
        assertEquals(56000, AgreusArcCampaign.settleSmallWar(p, victory, premiums::add));
        assertEquals(List.of(12000), premiums);
        assertTrue(p.smallWarSettled());
        assertEquals(-1, AgreusArcCampaign.settleSmallWar(p, victory, premiums::add));
        assertEquals(List.of(12000), premiums);
    }

    @Test void debriefNamesTheRouteLossesAndVossWithoutVoice() {
        var p = ratified(AgreusYardAgreement.HOUSE_CUSTODY);
        var m = AgreusArcCampaign.offer(p, "Agreus");
        var won = MissionOutcome.builder().mission(m).victory(true).payoutEarned(44000).marinesLost(3).smallWar(report(true, true, 6)).build();
        var lines = MissionDebriefConversation.smallWarLines(won);
        assertTrue(lines.stream().noneMatch(WaylineNarration.Line::voiced));
        String text = String.join(" ", lines.stream().map(WaylineNarration.Line::text).toList());
        assertTrue(text.contains("House Custody"));
        assertTrue(text.contains("56,000"));
        assertTrue(text.contains("Keep the pier. I've lost worse berths."));
        assertTrue(text.contains("6 of Greyhook's people walked off"));
        int cost = text.indexOf("3 of ours"), invitation = text.indexOf("Dormer is asking what it would take to keep us here.");
        assertTrue(cost >= 0 && invitation > cost, "the company's cost is acknowledged before the invitation");

        var lost = MissionOutcome.builder().mission(m).victory(false).payoutEarned(0)
                .smallWar(new SmallWarProgress.Report(true, true, false, false, true, true, true, 0, 4, 0, false)).build();
        String failed = String.join(" ", MissionDebriefConversation.smallWarLines(lost).stream().map(WaylineNarration.Line::text).toList());
        assertTrue(failed.contains("marked fuel storage was destroyed"));
        assertFalse(failed.contains("Keep the pier"));
    }

    @Test void theDuelOverheadGetsOneLineWinOrLoseAndNothingWithoutIt() {
        var m = AgreusArcCampaign.offer(ratified(AgreusYardAgreement.HOUSE_CUSTODY), "Agreus");
        String overhead = "Overhead: the Greyhook rig got away over the sea.";
        for (boolean victory : new boolean[]{true, false}) {
            var plain = outcome(m, victory, report(victory, true, 6));
            var dueled = MissionOutcome.builder().mission(m).victory(victory).payoutEarned(victory ? 44000 : 0)
                    .smallWar(report(victory, true, 6)).smallWarOverhead(overhead).build();
            var without = MissionDebriefConversation.smallWarLines(plain);
            var with = MissionDebriefConversation.smallWarLines(dueled);
            assertEquals(without.size() + 1, with.size());
            assertEquals(overhead, with.get(1).text(), "Castle says what happened overhead right after the result");
            assertTrue(without.stream().noneMatch(line -> line.text().startsWith("Overhead")));
        }
    }

    static MissionOutcome outcome(Mission m, boolean victory, SmallWarProgress.Report report) {
        return MissionOutcome.builder().mission(m).victory(victory).payoutEarned(victory ? 44000 : 0).smallWar(report).build();
    }
    static SmallWarProgress.Report report(boolean complete, boolean custody, int withdrawn) {
        return new SmallWarProgress.Report(true, true, true, complete, true, false, true, 0, 0, withdrawn, custody);
    }
    static AgreusArcProgress ratified(AgreusYardAgreement route) {
        var p = afterYard();
        assertTrue(p.signAgreement(route));
        assertTrue(p.ratifyAgreement(route));
        return p;
    }
    static AgreusArcProgress afterYard() {
        var p = YardOwnersRewardTest.afterWarrant(); String id = p.nextMissionId();
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(36000, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0));
        return p;
    }
}
