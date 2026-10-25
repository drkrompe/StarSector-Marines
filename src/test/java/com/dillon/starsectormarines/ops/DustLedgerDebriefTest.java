package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.DustLedgerBattleFixture;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Broker;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Grade;
import com.dillon.starsectormarines.i18n.Strings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DustLedgerDebriefTest {

    @Test
    void gradeOpensAndBrokerCloses() {
        var lines = MissionDebriefConversation.dustLedgerLines(new DustLedgerSequence.Report(Grade.PARTIAL, Broker.DELIVERED));
        assertEquals(List.of(Strings.get("corvusDustLedgerDebriefOpenPartial"),
                Strings.get("corvusDustLedgerDebriefFindPartialCaught"),
                Strings.get("corvusDustLedgerDebriefCloseCaught")), lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void onlyABrokerDeliveredAliveIsCaught() {
        for (Broker broker : Broker.values()) {
            var finding = MissionDebriefConversation.dustLedgerLines(new DustLedgerSequence.Report(Grade.ASHES, broker)).get(1);
            assertEquals(Strings.get(broker == Broker.DELIVERED ? "corvusDustLedgerDebriefFindStrongboxCaught"
                    : "corvusDustLedgerDebriefFindStrongboxEscaped"), finding.text(), broker.name());
        }
    }

    @Test
    void noLedgerDebriefWithoutASeizedLedger() {
        var unseized = MissionOutcome.builder().victory(true)
                .dustLedger(new DustLedgerSequence.Report(null, Broker.ESCAPED)).build();
        assertFalse(MissionDebriefConversation.dustLedger(unseized));
        var defeat = MissionOutcome.builder().victory(false)
                .dustLedger(new DustLedgerSequence.Report(Grade.WHOLE, Broker.DELIVERED)).build();
        assertFalse(MissionDebriefConversation.dustLedger(defeat));
    }

    @Test
    void chapterFixtureSurvivesRoundTrip() throws Exception {
        var chapter = CorvusChapter.DUST_LEDGER;
        var fixture = chapter.fixture(12, BattleSetup.defaultManifest(), chapter.profile(chapter.surface));
        assertInstanceOf(DustLedgerBattleFixture.class, fixture);
        assertEquals(fixture, BattleFixtureJson.fromJson(BattleFixtureJson.toJson(fixture)));
    }
}
