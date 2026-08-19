package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.CampaignEventLifecycleSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SilentColonyEventTest {

    @Test
    void preparationIsTriggerUniqueAndKeepsFirstHiddenSnapshot() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("silent-colony");

        long first = SilentColonyEvent.prepare(state, 77L, market,
                20, 23, 40, 25, 8, 991L);
        long replay = SilentColonyEvent.prepare(state, 77L, market,
                30, 33, 90, 80, 20, 1234L);

        assertEquals(first, replay);
        assertEquals(1, state.eventCount);
        int row = state.eventIndex(first);
        assertEquals(CampaignEventType.SILENT_COLONY,
                CampaignEventType.fromByte(state.eventType[row]));
        assertEquals(20, state.eventCreatedTick[row]);
        assertEquals(40, state.eventSuppliesRequired[row]);
        assertEquals(8, state.eventCiviliansAtRisk[row]);
        assertEquals(991L, state.eventColonyThreatSeed[row]);
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                archiveOutcome(state, row));
    }

    @Test
    void invalidPreparationAppendsNothing() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("silent-colony");

        assertEquals(-1L, SilentColonyEvent.prepare(state, -1L, market,
                20, 23, 40, 25, 8, 991L));
        assertEquals(-1L, SilentColonyEvent.prepare(state, 1L, 99,
                20, 23, 40, 25, 8, 991L));
        assertEquals(-1L, SilentColonyEvent.prepare(state, 1L, market,
                20, 19, 40, 25, 8, 991L));
        assertEquals(-1L, SilentColonyEvent.prepare(state, 1L, market,
                20, 23, 0, 25, 8, 991L));
        assertEquals(-1L, SilentColonyEvent.prepare(state, 1L, market,
                20, 23, 40, 0, 8, 991L));
        assertEquals(-1L, SilentColonyEvent.prepare(state, 1L, market,
                20, 23, 40, 25, 0, 991L));
        assertEquals(-1L, SilentColonyEvent.prepare(state, 1L, market,
                20, 23, 40, 25, 8, -1L));
        assertEquals(0, state.eventCount);
    }

    @Test
    void commitmentIsAtomicAndChargesOnlyOnce() {
        Fixture fixture = new Fixture();
        TestStore poor = new TestStore(39, 100);
        TestStore funded = new TestStore(100, 100);

        assertEquals(SilentColonyEvent.Result.INSUFFICIENT_RESOURCES,
                SilentColonyEvent.commit(fixture.state, fixture.eventId,
                        20, poor));
        assertEquals(CampaignEventState.PENDING_CHOICE, fixture.state());
        assertEquals(39, poor.supplies);
        assertEquals(100, poor.fuel);

        assertEquals(SilentColonyEvent.Result.COMMITTED,
                SilentColonyEvent.commit(fixture.state, fixture.eventId,
                        21, funded));
        assertEquals(CampaignEventState.COMMITTED, fixture.state());
        assertEquals(21, fixture.state.eventDecisionTick[fixture.row]);
        assertEquals(60, funded.supplies);
        assertEquals(75, funded.fuel);
        assertEquals(SilentColonyEvent.Result.NOT_READY,
                SilentColonyEvent.commit(fixture.state, fixture.eventId,
                        21, funded));
        assertEquals(60, funded.supplies);
        assertEquals(75, funded.fuel);
    }

    @Test
    void refusalAndExpiryRevealNothingAndConsumeNothing() {
        Fixture refused = new Fixture();
        TestStore store = new TestStore(100, 100);

        assertEquals(SilentColonyEvent.Result.REFUSED,
                SilentColonyEvent.refuse(refused.state, refused.eventId, 20));
        assertEquals(CampaignEventState.REFUSED, refused.state());
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                archiveOutcome(refused.state, refused.row));
        assertEquals(SilentColonyEvent.Result.ALREADY_TERMINAL,
                SilentColonyEvent.commit(refused.state, refused.eventId,
                        20, store));
        assertEquals(100, store.supplies);
        assertEquals(100, store.fuel);

        Fixture expired = new Fixture();
        new CampaignEventLifecycleSystem().tick(expired.state, 24);
        assertEquals(CampaignEventState.EXPIRED, expired.state());
        assertEquals(-1, expired.state.eventResolvedTick[expired.row]);
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                archiveOutcome(expired.state, expired.row));
    }

    @Test
    void explicitMissionReportClampsSurvivorsAndFreezesArchiveRecovery() {
        Fixture fixture = new Fixture();
        SilentColonyEvent.commit(fixture.state, fixture.eventId, 20,
                new TestStore(100, 100));

        assertEquals(SilentColonyEvent.Result.NOT_READY,
                SilentColonyEvent.resolve(fixture.state, fixture.eventId,
                        4, true, 19));
        assertEquals(SilentColonyEvent.Result.RESOLVED,
                SilentColonyEvent.resolve(fixture.state, fixture.eventId,
                        99, true, 25));

        assertEquals(CampaignEventState.RESOLVED, fixture.state());
        assertEquals(8, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(AbandonedColonyArchiveOutcome.RECOVERED,
                archiveOutcome(fixture.state, fixture.row));
        assertEquals(25, fixture.state.eventResolvedTick[fixture.row]);
        assertEquals(SilentColonyEvent.Result.ALREADY_TERMINAL,
                SilentColonyEvent.resolve(fixture.state, fixture.eventId,
                        0, false, 26));
        assertEquals(8, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(AbandonedColonyArchiveOutcome.RECOVERED,
                archiveOutcome(fixture.state, fixture.row));
    }

    @Test
    void measuredTotalFailureStillHasExplicitArchiveOutcome() {
        Fixture fixture = new Fixture();
        SilentColonyEvent.commit(fixture.state, fixture.eventId, 20,
                new TestStore(100, 100));

        assertEquals(SilentColonyEvent.Result.RESOLVED,
                SilentColonyEvent.resolve(fixture.state, fixture.eventId,
                        0, false, 25));

        assertEquals(0, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(AbandonedColonyArchiveOutcome.LOST,
                archiveOutcome(fixture.state, fixture.row));
    }

    private static AbandonedColonyArchiveOutcome archiveOutcome(
            CampaignState state, int row) {
        return AbandonedColonyArchiveOutcome.fromByte(
                state.eventColonyArchiveOutcome[row]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("silent-colony");
        final long eventId = SilentColonyEvent.prepare(state, 77L, market,
                20, 23, 40, 25, 8, 991L);
        final int row = state.eventIndex(eventId);

        CampaignEventState state() {
            return CampaignEventState.fromByte(state.eventState[row]);
        }
    }

    private static final class TestStore
            implements SilentColonyEvent.ExpeditionStore {
        int supplies;
        int fuel;

        TestStore(int supplies, int fuel) {
            this.supplies = supplies;
            this.fuel = fuel;
        }

        @Override
        public boolean consume(int supplies, int fuel) {
            if (this.supplies < supplies || this.fuel < fuel) return false;
            this.supplies -= supplies;
            this.fuel -= fuel;
            return true;
        }
    }
}
