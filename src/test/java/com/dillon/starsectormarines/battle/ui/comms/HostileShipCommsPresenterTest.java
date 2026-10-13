package com.dillon.starsectormarines.battle.ui.comms;

import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostileShipCommsPresenterTest {

    @Test
    void theInboundBeatBecomesADangerNoticeThatOutlastsTheWarning() {
        BattleCommsFeed.Notice notice = HostileShipCommsPresenter.notice(HostileShipSupport.BEAT_INBOUND);
        assertNotNull(notice);
        assertEquals("HOSTILE CAPITAL INBOUND", notice.heading());
        assertEquals(BattleCommsFeed.Tone.DANGER, notice.tone());
        assertTrue(notice.durationSec() > HostileShipSupport.WARNING_SECONDS);
    }

    @Test
    void otherBeatsAreNotItsBusiness() {
        assertNull(HostileShipCommsPresenter.notice("opening.relief.started"));
    }
}
