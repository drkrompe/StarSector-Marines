package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AuxiliaryWarrantProgress;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The letter shows the office's reply only to an answer the campaign actually recorded. */
class AuxiliaryWarrantDeskTest {

    @Test
    void aRecordedAnswerTurnsTheLetterAndARefusedOneDoesNot() {
        AtomicInteger answered = new AtomicInteger();
        AuxiliaryWarrantDesk accepted = new AuxiliaryWarrantDesk(new Reactor(),
                () -> AuxiliaryWarrantProgress.Stage.OFFERED, () -> true, () -> false, answered::incrementAndGet);
        accepted.open();
        assertEquals("aux-backdrop", accepted.row().get().markupProperty("classes"));
        assertEquals("hq-wings console-action", accepted.row().get().markupProperty("resumeClasses"));
        ((Runnable) accepted.row().get().markupProperty("accept")).run();
        assertEquals(AuxiliaryWarrantDesk.Page.ACCEPTED, accepted.row().get().page());
        assertEquals(1, answered.get());

        AuxiliaryWarrantDesk refused = new AuxiliaryWarrantDesk(new Reactor(),
                () -> AuxiliaryWarrantProgress.Stage.OFFERED, () -> false, () -> false, answered::incrementAndGet);
        refused.open();
        ((Runnable) refused.row().get().markupProperty("decline")).run();
        assertEquals(AuxiliaryWarrantDesk.Page.TERMS, refused.row().get().page());
        assertEquals(1, answered.get());
    }

    @Test
    void theBannerShowsOnlyWhileTheTermsAwaitAnAnswer() {
        AuxiliaryWarrantDesk desk = new AuxiliaryWarrantDesk(new Reactor(),
                () -> AuxiliaryWarrantProgress.Stage.ACCEPTED, () -> false, () -> false, () -> { });
        assertEquals("aux-hidden-part", desk.row().get().markupProperty("resumeClasses"));
        assertEquals("aux-hidden", desk.row().get().markupProperty("classes"));
    }
}
