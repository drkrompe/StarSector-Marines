package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.AgreusArcProgress.HandoverStage;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reading, comparing and leaving commit nothing; only a confirmed signature reaches the campaign. */
class AgreusHandoverDeskTest {
    private static HeadlessStrings copy;
    @BeforeAll static void shippedCopy() throws Exception { copy = new HeadlessStrings(Path.of("mod")); }
    @AfterAll static void closeCopy() { copy.close(); }
    @Test void readingComparingAndLeavingCommitNothing() {
        var signatures = new ArrayList<AgreusYardAgreement>();
        var desk = desk(() -> HandoverStage.OPEN, a -> { signatures.add(a); return true; }, () -> true, new AtomicInteger());
        desk.open();
        assertEquals("aux-backdrop", prop(desk, "classes"));
        assertEquals("hq-wings console-action", prop(desk, "resumeClasses"));
        run(desk, "compact");
        assertTrue(((String) prop(desk, "cost")).contains("forgoes the 12,000-credit premium"));
        run(desk, "compare");
        assertEquals(Strings.get("agreusArcCastleComparison"), prop(desk, "record"));
        run(desk, "custody");
        assertTrue(((String) prop(desk, "benefit")).contains("payable only with Chapter 5 victory"));
        run(desk, "primary");
        assertEquals(AgreusHandoverDesk.Page.CONFIRM_CUSTODY, desk.row().get().page());
        run(desk, "secondary");
        assertEquals(AgreusHandoverDesk.Page.CUSTODY, desk.row().get().page());
        run(desk, "close");
        assertEquals("aux-hidden", prop(desk, "classes"));
        assertTrue(signatures.isEmpty());
    }

    @Test void aConfirmedSignatureTurnsTheLetterOnlyWhenRecorded() {
        var answered = new AtomicInteger();
        var signatures = new ArrayList<AgreusYardAgreement>();
        var desk = desk(() -> HandoverStage.OPEN, a -> { signatures.add(a); return true; }, () -> false, answered);
        desk.open();
        run(desk, "compact");
        run(desk, "primary");
        run(desk, "primary");
        assertEquals(List.of(AgreusYardAgreement.YARD_COMPACT), signatures);
        assertEquals(AgreusHandoverDesk.Page.SIGNED, desk.row().get().page());
        assertEquals(1, answered.get());

        var refused = desk(() -> HandoverStage.OPEN, a -> false, () -> false, answered);
        refused.open();
        run(refused, "primary");
        run(refused, "primary");
        assertEquals(AgreusHandoverDesk.Page.CONFIRM_CUSTODY, refused.row().get().page());
        run(refused, "secondary");
        run(refused, "secondary");
        run(refused, "primary");
        assertEquals(AgreusHandoverDesk.Page.CONFIRM_DECLINE, refused.row().get().page(), "a refused decline does not pretend");
        assertEquals(1, answered.get());
    }

    @Test void decliningNeedsItsOwnConfirmation() {
        var answered = new AtomicInteger();
        var declines = new AtomicInteger();
        var desk = desk(() -> HandoverStage.OPEN, a -> fail("no signature"), () -> declines.incrementAndGet() > 0, answered);
        desk.open();
        run(desk, "secondary");
        assertEquals(0, declines.get());
        run(desk, "primary");
        assertEquals(1, declines.get());
        assertEquals(AgreusHandoverDesk.Page.DECLINED, desk.row().get().page());
        assertEquals("Done", prop(desk, "closeLabel"));
    }

    @Test void theBannerShowsOnlyWhileTheDecisionIsOpen() {
        for (var stage : List.of(HandoverStage.NOT_REACHED, HandoverStage.SIGNED, HandoverStage.RATIFIED, HandoverStage.DECLINED))
            assertEquals("aux-hidden-part", prop(desk(() -> stage, a -> true, () -> true, new AtomicInteger()), "resumeClasses"));
    }

    @Test void correspondenceIsTextOnlyAndFollowsTheDecision() {
        var p = new AgreusArcProgress();
        assertTrue(AgreusArcCampaign.correspondence(p).isEmpty());
        for (WaylineNarration.Line line : List.of(AgreusArcCampaign.LENNA_GATE, AgreusArcCampaign.LENNA_LIEN, AgreusArcCampaign.VOSS_WARNING))
            assertFalse(line.voiced(), line.id());
        assertTrue(AgreusArcCampaign.VOSS_WARNING.speaker().startsWith("SERA VOSS"));
    }

    @Test void theGuideTileShowsChapterFourAsInPreparationOnceSigned() {
        var p = afterYard();
        var guide = new AgreusWorkGuide(p, null, () -> { });
        assertEquals("DECISION / Latchkey custody agreements", guide.markupProperty("mission"));
        assertEquals(List.of(AgreusArcCampaign.LENNA_GATE, AgreusArcCampaign.LENNA_LIEN), AgreusArcCampaign.correspondence(p));
        assertTrue(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertEquals("NEW / Terms of Protection", guide.markupProperty("mission"));
        assertTrue(((String) guide.markupProperty("preparation")).contains(AgreusArcCampaign.VOSS_WARNING.text()));
        assertTrue(((String) guide.markupProperty("payment")).contains("12,000 cr premium on Chapter 5 victory"));
        assertTrue(AgreusArcCampaign.correspondence(p).contains(AgreusArcCampaign.VOSS_WARNING));
    }

    private static AgreusArcProgress afterYard() {
        var p = new AgreusArcProgress(); String id = p.missionId();
        assertTrue(p.begin(id, "agreus"));
        p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, false);
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus"));
        p.settleWarrant(id, "agreus", true, 30000, true, true, true, true, false, 0, 0);
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(36000, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0));
        return p;
    }

    private interface Signer { boolean sign(AgreusYardAgreement a); }

    private static AgreusHandoverDesk desk(Supplier<HandoverStage> stage, Signer sign,
                                           BooleanSupplier decline, AtomicInteger answered) {
        var signed = new AtomicReference<AgreusYardAgreement>();
        return new AgreusHandoverDesk(new Reactor(), stage, signed::get, sign::sign, decline, answered::incrementAndGet);
    }

    private static Object prop(AgreusHandoverDesk desk, String key) { return desk.row().get().markupProperty(key); }
    private static void run(AgreusHandoverDesk desk, String key) { ((Runnable) prop(desk, key)).run(); }
}
