package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SilentColonyMissionResolutionTest {

    @Test
    void explicitDualReportClosesEventAndEmitsImmutableChronicle() {
        Fixture fixture = new Fixture();

        SilentColonyMissionResolution.Result result =
                SilentColonyMissionResolution.apply(
                        fixture.state, fixture.outcome(3,
                                AbandonedColonyArchiveOutcome.RECOVERED), 22);

        assertEquals(SilentColonyMissionResolution.Result.RESOLVED, result);
        assertEquals(CampaignEventState.RESOLVED, fixture.eventState());
        assertEquals(3, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(AbandonedColonyArchiveOutcome.RECOVERED,
                AbandonedColonyArchiveOutcome.fromByte(
                        fixture.state.eventColonyArchiveOutcome[fixture.row]));
        assertEquals(22, fixture.state.eventResolvedTick[fixture.row]);
        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(ChronicleEventType.SILENT_COLONY,
                ChronicleEventType.fromByte(
                        fixture.state.chronicleEventType[0]));
        assertEquals(fixture.eventId,
                fixture.state.chronicleSourceEventId[0]);
        assertEquals(fixture.market, fixture.state.chronicleMarketId[0]);
        assertEquals(8, fixture.state.chronicleSurvivorsAtRisk[0]);
        assertEquals(3, fixture.state.chronicleSurvivorsRescued[0]);
        assertEquals(AbandonedColonyArchiveOutcome.RECOVERED,
                AbandonedColonyArchiveOutcome.fromByte(
                        fixture.state.chronicleColonyArchiveOutcome[0]));
        assertEquals(0, fixture.state.moralChoiceCount);
    }

    @Test
    void measuredZeroAndLostArchiveIsAValidOutcome() {
        Fixture fixture = new Fixture();

        assertEquals(SilentColonyMissionResolution.Result.RESOLVED,
                SilentColonyMissionResolution.apply(
                        fixture.state, fixture.outcome(0,
                                AbandonedColonyArchiveOutcome.LOST), 22));
        assertEquals(0, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(AbandonedColonyArchiveOutcome.LOST,
                AbandonedColonyArchiveOutcome.fromByte(
                        fixture.state.eventColonyArchiveOutcome[fixture.row]));
    }

    @Test
    void replayAfterSaveLoadCannotReplaceFactsOrDispatchTwice()
            throws Exception {
        Fixture fixture = new Fixture();
        MissionOutcome first = fixture.outcome(3,
                AbandonedColonyArchiveOutcome.RECOVERED);
        assertEquals(SilentColonyMissionResolution.Result.RESOLVED,
                SilentColonyMissionResolution.apply(
                        fixture.state, first, 22));

        CampaignState loaded = roundTrip(fixture.state);
        MissionOutcome replay = fixture.outcome(8,
                AbandonedColonyArchiveOutcome.LOST);
        assertEquals(SilentColonyMissionResolution.Result.ALREADY_TERMINAL,
                SilentColonyMissionResolution.apply(loaded, replay, 30));

        int row = loaded.eventIndex(fixture.eventId);
        assertEquals(3, loaded.eventCiviliansRescued[row]);
        assertEquals(AbandonedColonyArchiveOutcome.RECOVERED,
                AbandonedColonyArchiveOutcome.fromByte(
                        loaded.eventColonyArchiveOutcome[row]));
        assertEquals(22, loaded.eventResolvedTick[row]);
        assertEquals(1, loaded.chronicleCount);
        assertEquals(0, loaded.moralChoiceCount);
    }

    @Test
    void committedSaveLoadStillAcceptsItsFirstMatchingReport()
            throws Exception {
        Fixture fixture = new Fixture();
        CampaignState loaded = roundTrip(fixture.state);

        assertEquals(SilentColonyMissionResolution.Result.RESOLVED,
                SilentColonyMissionResolution.apply(loaded,
                        fixture.outcome(5,
                                AbandonedColonyArchiveOutcome.LOST), 24));

        int row = loaded.eventIndex(fixture.eventId);
        assertEquals(CampaignEventState.RESOLVED,
                CampaignEventState.fromByte(loaded.eventState[row]));
        assertEquals(5, loaded.eventCiviliansRescued[row]);
        assertEquals(1, loaded.chronicleCount);
    }

    @Test
    void missingOrMalformedReportsFailClosed() {
        Fixture fixture = new Fixture();

        assertEquals(SilentColonyMissionResolution.Result.NO_REPORT,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(3,
                                AbandonedColonyArchiveOutcome.NONE), 22));
        assertEquals(SilentColonyMissionResolution.Result.INVALID,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(9,
                                AbandonedColonyArchiveOutcome.LOST), 22));
        assertEquals(SilentColonyMissionResolution.Result.INVALID,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(-2,
                                AbandonedColonyArchiveOutcome.LOST), 22));
        assertEquals(SilentColonyMissionResolution.Result.INVALID,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(3,
                                AbandonedColonyArchiveOutcome.RECOVERED,
                                fixture.threatSeed + 1L, fixture.market, 8,
                                8, 2), 22));
        assertEquals(CampaignEventState.COMMITTED, fixture.eventState());
        assertEquals(0, fixture.state.chronicleCount);
    }

    @Test
    void mismatchedLineageEconomicsAndLifecycleDoNotMutate() {
        Fixture fixture = new Fixture();

        assertEquals(SilentColonyMissionResolution.Result.INVALID,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome("silent-colony:999",
                                MissionSource.CAMPAIGN_EVENT, 0), 22));
        assertEquals(SilentColonyMissionResolution.Result.INVALID,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(
                                SilentColonyMissionKey.encode(fixture.eventId),
                                MissionSource.GENERATED, 0), 22));
        assertEquals(SilentColonyMissionResolution.Result.INVALID,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(
                                SilentColonyMissionKey.encode(fixture.eventId),
                                MissionSource.CAMPAIGN_EVENT, 1), 22));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.PENDING_CHOICE.toByte();
        assertEquals(SilentColonyMissionResolution.Result.NOT_COMMITTED,
                SilentColonyMissionResolution.apply(fixture.state,
                        fixture.outcome(3,
                                AbandonedColonyArchiveOutcome.RECOVERED), 22));
        assertEquals(0, fixture.state.chronicleCount);
    }

    private static CampaignState roundTrip(CampaignState state)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (CampaignState) input.readObject();
        }
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("hesperus_ruins");
        final long threatSeed = 991L;
        final long eventId = SilentColonyEvent.prepare(state, 77L, market,
                20, 23, 40, 25, 8, threatSeed);
        final int row = state.eventIndex(eventId);

        Fixture() {
            state.eventState[row] = CampaignEventState.COMMITTED.toByte();
            state.eventDecisionTick[row] = 20;
        }

        CampaignEventState eventState() {
            return CampaignEventState.fromByte(state.eventState[row]);
        }

        MissionOutcome outcome(int rescued,
                               AbandonedColonyArchiveOutcome archive) {
            return outcome(rescued, archive, threatSeed, market, 8,
                    8, rescued);
        }

        MissionOutcome outcome(int rescued,
                               AbandonedColonyArchiveOutcome archive,
                               long seed, int outcomeMarket, int atRisk,
                               int representatives, int evacuated) {
            return outcome(SilentColonyMissionKey.encode(eventId),
                    MissionSource.CAMPAIGN_EVENT, 0, rescued, archive, seed,
                    outcomeMarket, atRisk, representatives, evacuated);
        }

        MissionOutcome outcome(String missionId, MissionSource source,
                               int payout) {
            return outcome(missionId, source, payout, 3,
                    AbandonedColonyArchiveOutcome.RECOVERED, threatSeed,
                    market, 8, 8, 3);
        }

        MissionOutcome outcome(String missionId, MissionSource source,
                               int payout, int rescued,
                               AbandonedColonyArchiveOutcome archive,
                               long seed, int outcomeMarket, int atRisk,
                               int representatives, int evacuated) {
            return new MissionOutcome(false, missionId, "Silent Colony",
                    MissionType.EXTRACTION, RiskLevel.HIGH, source,
                    payout, payout, 8, 2,
                    null, null, null, null, 0, 0f, null,
                    "Hesperus Ruins", null, "neutral",
                    -1L, eventId, outcomeMarket, seed, atRisk, rescued,
                    representatives, evacuated, archive,
                    0, 0, 0, Collections.emptySet(),
                    Collections.emptySet(), Collections.emptySet());
        }
    }
}
