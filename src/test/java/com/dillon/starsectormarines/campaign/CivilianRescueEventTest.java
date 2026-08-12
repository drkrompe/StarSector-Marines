package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilianRescueEventTest {

    @Test
    void preparationIsTriggerUniqueAndKeepsFirstSnapshot() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");

        long first = CivilianRescueEvent.prepare(
                state, 77L, market, 20, 21, 40, 25, 120);
        long repeated = CivilianRescueEvent.prepare(
                state, 77L, market, 30, 31, 99, 88, 500);

        assertEquals(first, repeated);
        assertEquals(1, state.eventCount);
        int row = state.eventIndex(first);
        assertEquals(20, state.eventCreatedTick[row]);
        assertEquals(40, state.eventSuppliesRequired[row]);
        assertEquals(120, state.eventCiviliansAtRisk[row]);
    }

    @Test
    void invalidPreparationDoesNotAppend() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");

        assertEquals(-1L, CivilianRescueEvent.prepare(
                state, -1L, market, 20, 21, 40, 25, 120));
        assertEquals(-1L, CivilianRescueEvent.prepare(
                state, 1L, 99, 20, 21, 40, 25, 120));
        assertEquals(-1L, CivilianRescueEvent.prepare(
                state, 1L, market, 20, 19, 40, 25, 120));
        assertEquals(-1L, CivilianRescueEvent.prepare(
                state, 1L, market, 20, 21, 0, 0, 120));
        assertEquals(-1L, CivilianRescueEvent.prepare(
                state, 1L, market, 20, 21, 40, 25, 0));
        assertEquals(0, state.eventCount);
    }

    @Test
    void explicitRefusalIsTerminalAndConsumesNothing() {
        Fixture fixture = new Fixture();
        TestStore store = new TestStore(100, 100);

        assertEquals(CivilianRescueEvent.Result.REFUSED,
                CivilianRescueEvent.refuse(fixture.state, fixture.eventId, 20));
        assertEquals(CampaignEventState.REFUSED, fixture.eventState());
        assertEquals(20, fixture.state.eventDecisionTick[fixture.row]);
        assertEquals(CivilianRescueEvent.Result.ALREADY_TERMINAL,
                CivilianRescueEvent.commit(
                        fixture.state, fixture.eventId, 20, store));
        assertEquals(100, store.supplies);
        assertEquals(100, store.fuel);
    }

    @Test
    void commitmentIsAtomicAndFreezesDecision() {
        Fixture fixture = new Fixture();
        TestStore poor = new TestStore(39, 100);
        TestStore funded = new TestStore(100, 100);

        assertEquals(CivilianRescueEvent.Result.INSUFFICIENT_RESOURCES,
                CivilianRescueEvent.commit(
                        fixture.state, fixture.eventId, 20, poor));
        assertEquals(CampaignEventState.PENDING_CHOICE, fixture.eventState());
        assertEquals(39, poor.supplies);
        assertEquals(100, poor.fuel);

        assertEquals(CivilianRescueEvent.Result.COMMITTED,
                CivilianRescueEvent.commit(
                        fixture.state, fixture.eventId, 21, funded));
        assertEquals(CampaignEventState.COMMITTED, fixture.eventState());
        assertEquals(21, fixture.state.eventDecisionTick[fixture.row]);
        assertEquals(60, funded.supplies);
        assertEquals(75, funded.fuel);
        assertEquals(CivilianRescueEvent.Result.NOT_READY,
                CivilianRescueEvent.commit(
                        fixture.state, fixture.eventId, 21, funded));
        assertEquals(60, funded.supplies);
        assertEquals(75, funded.fuel);
    }

    @Test
    void choiceOutsideWindowDoesNotMutateOrConsume() {
        Fixture fixture = new Fixture();
        TestStore store = new TestStore(100, 100);

        assertEquals(CivilianRescueEvent.Result.NOT_READY,
                CivilianRescueEvent.refuse(fixture.state, fixture.eventId, 19));
        assertEquals(CivilianRescueEvent.Result.NOT_READY,
                CivilianRescueEvent.commit(
                        fixture.state, fixture.eventId, 22, store));
        assertEquals(CampaignEventState.PENDING_CHOICE, fixture.eventState());
        assertEquals(-1, fixture.state.eventDecisionTick[fixture.row]);
        assertEquals(100, store.supplies);
        assertEquals(100, store.fuel);
    }

    @Test
    void committedOutcomePersistsExplicitClampedRescueCount() {
        Fixture fixture = new Fixture();
        TestStore store = new TestStore(100, 100);
        CivilianRescueEvent.commit(fixture.state, fixture.eventId, 20, store);

        assertEquals(CivilianRescueEvent.Result.NOT_READY,
                CivilianRescueEvent.resolve(
                        fixture.state, fixture.eventId, 50, 19));
        assertEquals(CivilianRescueEvent.Result.RESOLVED,
                CivilianRescueEvent.resolve(
                        fixture.state, fixture.eventId, 500, 22));

        assertEquals(CampaignEventState.RESOLVED, fixture.eventState());
        assertEquals(120, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(22, fixture.state.eventResolvedTick[fixture.row]);
        assertEquals(CivilianRescueEvent.Result.ALREADY_TERMINAL,
                CivilianRescueEvent.resolve(
                        fixture.state, fixture.eventId, 0, 23));
        assertEquals(120, fixture.state.eventCiviliansRescued[fixture.row]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long eventId = CivilianRescueEvent.prepare(
                state, 77L, market, 20, 21, 40, 25, 120);
        final int row = state.eventIndex(eventId);

        CampaignEventState eventState() {
            return CampaignEventState.fromByte(state.eventState[row]);
        }
    }

    private static final class TestStore implements CivilianRescueEvent.ReliefStore {
        int supplies;
        int fuel;

        TestStore(int supplies, int fuel) {
            this.supplies = supplies;
            this.fuel = fuel;
        }

        @Override
        public boolean commit(int supplies, int fuel) {
            if (this.supplies < supplies || this.fuel < fuel) return false;
            this.supplies -= supplies;
            this.fuel -= fuel;
            return true;
        }
    }
}
