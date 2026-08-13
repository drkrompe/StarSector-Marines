package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefectorAsylumEventTest {

    @Test
    void preparationSnapshotsSourceAndKeepsFirstTerms() {
        Fixture fixture = new Fixture();

        long repeated = DefectorAsylumEvent.prepare(fixture.state, 999L,
                fixture.chain, fixture.actor, fixture.target, fixture.market,
                40, 43, 99, 88, 70_000);

        assertEquals(fixture.event, repeated);
        assertEquals(1, fixture.state.eventCount);
        assertEquals(fixture.chain, fixture.state.eventSourceChainId[fixture.row]);
        assertEquals(fixture.actor, fixture.state.eventActorHouseId[fixture.row]);
        assertEquals(fixture.target, fixture.state.eventTargetHouseId[fixture.row]);
        assertEquals(fixture.market, fixture.state.eventMarketId[fixture.row]);
        assertEquals(20, fixture.state.eventSuppliesRequired[fixture.row]);
        assertEquals(10, fixture.state.eventFuelRequired[fixture.row]);
        assertEquals(40_000, fixture.state.eventCreditsOffered[fixture.row]);
    }

    @Test
    void preparationRejectsFabricatedOrMalformedSource() {
        Fixture fixture = new Fixture(false);

        assertEquals(-1L, DefectorAsylumEvent.prepare(fixture.state, 77L,
                999L, fixture.actor, fixture.target, fixture.market,
                20, 23, 20, 10, 40_000));
        assertEquals(-1L, DefectorAsylumEvent.prepare(fixture.state, 77L,
                fixture.chain, fixture.target, fixture.actor, fixture.market,
                20, 23, 20, 10, 40_000));
        assertEquals(-1L, DefectorAsylumEvent.prepare(fixture.state, 77L,
                fixture.chain, fixture.actor, fixture.target, fixture.market,
                20, 19, 20, 10, 40_000));
        assertEquals(-1L, DefectorAsylumEvent.prepare(fixture.state, 77L,
                fixture.chain, fixture.actor, fixture.target, fixture.market,
                20, 23, 0, 10, 40_000));
        assertEquals(0, fixture.state.eventCount);
    }

    @Test
    void commitmentChargesOnceAndFreezesFollowupWindow() {
        Fixture fixture = new Fixture();
        TestAsylumStore poor = new TestAsylumStore(19, 100);
        TestAsylumStore funded = new TestAsylumStore(100, 100);

        assertEquals(DefectorAsylumEvent.Result.INSUFFICIENT_RESOURCES,
                DefectorAsylumEvent.commit(
                        fixture.state, fixture.event, 21, poor));
        assertEquals(CampaignEventState.PENDING_CHOICE, fixture.state());
        assertEquals(19, poor.supplies);

        assertEquals(DefectorAsylumEvent.Result.COMMITTED,
                DefectorAsylumEvent.commit(
                        fixture.state, fixture.event, 21, funded));
        assertEquals(CampaignEventState.COMMITTED, fixture.state());
        assertEquals(21, fixture.state.eventDecisionTick[fixture.row]);
        assertEquals(31, fixture.state.eventFollowupTick[fixture.row]);
        assertEquals(34, fixture.state.eventFollowupDeadlineTick[fixture.row]);
        assertEquals(80, funded.supplies);
        assertEquals(90, funded.fuel);

        assertEquals(DefectorAsylumEvent.Result.NOT_READY,
                DefectorAsylumEvent.commit(
                        fixture.state, fixture.event, 22, funded));
        assertEquals(80, funded.supplies);
        assertEquals(90, funded.fuel);
    }

    @Test
    void refusalBeforePromiseIsTerminalAndChargesNothing() {
        Fixture fixture = new Fixture();
        TestAsylumStore store = new TestAsylumStore(100, 100);

        assertEquals(DefectorAsylumEvent.Result.REFUSED,
                DefectorAsylumEvent.refuse(fixture.state, fixture.event, 20));
        assertEquals(CampaignEventState.REFUSED, fixture.state());
        assertEquals(DefectorAsylumOutcome.NONE, fixture.outcome());
        assertEquals(DefectorAsylumEvent.Result.ALREADY_TERMINAL,
                DefectorAsylumEvent.commit(
                        fixture.state, fixture.event, 20, store));
        assertEquals(100, store.supplies);
        assertEquals(100, store.fuel);
    }

    @Test
    void followupCannotOpenEarlyAndProtectionResolvesExactlyOnce() {
        Fixture fixture = new Fixture();
        fixture.commit();

        assertEquals(DefectorAsylumEvent.Result.NOT_READY,
                DefectorAsylumEvent.advanceToFollowup(
                        fixture.state, fixture.event, 29));
        assertEquals(DefectorAsylumEvent.Result.FOLLOWUP_READY,
                DefectorAsylumEvent.advanceToFollowup(
                        fixture.state, fixture.event, 30));
        assertEquals(CampaignEventState.PENDING_FOLLOWUP, fixture.state());
        assertEquals(DefectorAsylumEvent.Result.PROTECTED,
                DefectorAsylumEvent.protect(
                        fixture.state, fixture.event, 30));

        assertEquals(CampaignEventState.RESOLVED, fixture.state());
        assertEquals(DefectorAsylumOutcome.PROTECTED, fixture.outcome());
        assertEquals(30, fixture.state.eventResolvedTick[fixture.row]);
        assertEquals(DefectorAsylumEvent.Result.ALREADY_TERMINAL,
                DefectorAsylumEvent.protect(
                        fixture.state, fixture.event, 31));
    }

    @Test
    void betrayalPaysExactlyOnceAndCannotFollowProtection() {
        Fixture fixture = new Fixture();
        TestCreditStore credits = new TestCreditStore();
        fixture.commit();
        DefectorAsylumEvent.advanceToFollowup(
                fixture.state, fixture.event, 30);

        assertEquals(DefectorAsylumEvent.Result.BETRAYED,
                DefectorAsylumEvent.betray(
                        fixture.state, fixture.event, 32, credits));
        assertEquals(40_000, credits.credits);
        assertEquals(DefectorAsylumOutcome.BETRAYED, fixture.outcome());
        assertEquals(DefectorAsylumEvent.Result.ALREADY_TERMINAL,
                DefectorAsylumEvent.betray(
                        fixture.state, fixture.event, 32, credits));
        assertEquals(40_000, credits.credits);
        assertEquals(DefectorAsylumEvent.Result.ALREADY_TERMINAL,
                DefectorAsylumEvent.protect(
                        fixture.state, fixture.event, 32));
    }

    @Test
    void betrayalFailsClosedAfterOfferDeadline() {
        Fixture fixture = new Fixture();
        TestCreditStore credits = new TestCreditStore();
        fixture.commit();
        DefectorAsylumEvent.advanceToFollowup(
                fixture.state, fixture.event, 30);

        assertEquals(DefectorAsylumEvent.Result.NOT_READY,
                DefectorAsylumEvent.betray(
                        fixture.state, fixture.event, 34, credits));
        assertEquals(0, credits.credits);
        assertEquals(CampaignEventState.PENDING_FOLLOWUP, fixture.state());
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("event_market");
        final long actor = house(state, market, "Actor");
        final long target = house(state, market, "Target");
        final long chain = state.addAutonomousChain(actor, target, market, 7,
                (byte) 1, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 45, (byte) 32, 1);
        final long event;
        final int row;

        Fixture() {
            this(true);
        }

        Fixture(boolean prepare) {
            event = prepare ? DefectorAsylumEvent.prepare(state, 77L,
                    chain, actor, target, market,
                    20, 23, 20, 10, 40_000) : -1L;
            row = prepare ? state.eventIndex(event) : -1;
        }

        void commit() {
            DefectorAsylumEvent.commit(
                    state, event, 20, new TestAsylumStore(100, 100));
        }

        CampaignEventState state() {
            return CampaignEventState.fromByte(state.eventState[row]);
        }

        DefectorAsylumOutcome outcome() {
            return DefectorAsylumOutcome.fromByte(state.eventDefectorOutcome[row]);
        }
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }

    private static final class TestAsylumStore
            implements DefectorAsylumEvent.AsylumStore {
        int supplies;
        int fuel;

        TestAsylumStore(int supplies, int fuel) {
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

    private static final class TestCreditStore
            implements DefectorAsylumEvent.CreditStore {
        int credits;

        @Override
        public boolean grant(int credits) {
            this.credits += credits;
            return true;
        }
    }
}
