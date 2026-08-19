package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.DefectorAsylumConsequenceSystem;
import com.dillon.starsectormarines.campaign.systems.CampaignEventLifecycleSystem;
import com.dillon.starsectormarines.campaign.systems.ChainAdvancementSystem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefectorAsylumConsequencesTest {

    @Test
    void refusalRecordsMercyOnlyAndReplaysOnce() {
        Fixture fixture = new Fixture();
        assertEquals(DefectorAsylumEvent.Result.REFUSED,
                DefectorAsylumEvent.refuse(
                        fixture.state, fixture.event, 21));

        assertEquals(DefectorAsylumConsequences.Result.APPLIED,
                DefectorAsylumConsequences.apply(
                        fixture.state, fixture.row, 21));
        assertEquals(-5, fixture.state.moralMercy);
        assertEquals(0, fixture.state.moralIntegrity);
        assertEquals(0, fixture.state.moralStewardship);
        assertEquals(1, fixture.state.moralChoiceCount);
        assertEquals(0, fixture.rep(fixture.actor));
        assertEquals(0, fixture.rep(fixture.target));
        assertEquals(50, fixture.progress());

        assertEquals(DefectorAsylumConsequences.Result.ALREADY_APPLIED,
                DefectorAsylumConsequences.apply(
                        fixture.state, fixture.row, 22));
        assertEquals(-5, fixture.state.moralMercy);
        assertEquals(1, fixture.state.moralChoiceCount);
    }

    @Test
    void protectionChecksActivePlotAndRewardsThreatenedHouseOnce() {
        Fixture fixture = new Fixture();
        fixture.protect();

        DefectorAsylumConsequenceSystem system =
                new DefectorAsylumConsequenceSystem();
        system.tick(fixture.state, 30);
        system.tick(fixture.state, 31);

        assertEquals(30, fixture.progress());
        assertEquals(0, fixture.rep(fixture.actor));
        assertEquals(5, fixture.rep(fixture.target));
        assertEquals(0, fixture.state.moralMercy);
        assertEquals(20, fixture.state.moralIntegrity);
        assertEquals(10, fixture.state.moralStewardship);
        assertEquals(1, fixture.state.moralChoiceCount);
        assertEquals(MoralChoiceSource.DEFECTOR_ASYLUM,
                MoralChoiceSource.fromByte(
                        fixture.state.moralChoiceSourceType[0]));
        assertEquals(fixture.event,
                fixture.state.moralChoiceSourceId[0]);
        assertEquals(30, fixture.state.moralChoiceHappenedTick[0]);
        int repRow = fixture.state.repIndex(fixture.target);
        assertEquals(0, fixture.state.repContractsCompleted[repRow]);
        assertEquals(0, fixture.state.repContractsFailed[repRow]);
        assertEquals(0, fixture.state.repLastContractTick[repRow]);
        assertEquals(0, fixture.state.playerMrbRep);
    }

    @Test
    void betrayalAcceleratesWithoutResolvingAndAppliesFrozenReputation() {
        Fixture fixture = new Fixture();
        fixture.state.chainProgress[fixture.chainRow] = 90;
        fixture.betray();

        assertEquals(DefectorAsylumConsequences.Result.APPLIED,
                DefectorAsylumConsequences.apply(
                        fixture.state, fixture.row, 31));

        assertEquals(110, fixture.progress());
        assertEquals(ChainState.ACTIVE, ChainState.fromByte(
                fixture.state.chainState[fixture.chainRow]));
        assertEquals(5, fixture.rep(fixture.actor));
        assertEquals(-10, fixture.rep(fixture.target));
        assertEquals(-25, fixture.state.moralIntegrity);
        assertEquals(-10, fixture.state.moralStewardship);
        assertEquals(1, fixture.state.moralChoiceCount);
    }

    @Test
    void endedSourceSkipsProgressButKeepsPromiseAndReputationHistory() {
        Fixture fixture = new Fixture();
        fixture.protect();
        fixture.state.chainState[fixture.chainRow] = ChainState.FAILED.toByte();

        assertEquals(DefectorAsylumConsequences.Result.APPLIED,
                DefectorAsylumConsequences.apply(
                        fixture.state, fixture.row, 31));

        assertEquals(50, fixture.progress());
        assertEquals(5, fixture.rep(fixture.target));
        assertEquals(20, fixture.state.moralIntegrity);
        assertEquals(10, fixture.state.moralStewardship);
    }

    @Test
    void changedSourcePayloadCannotRedirectFrozenConsequences() {
        Fixture fixture = new Fixture();
        fixture.protect();
        fixture.state.chainTarget[fixture.chainRow] = fixture.actor;

        assertEquals(DefectorAsylumConsequences.Result.APPLIED,
                DefectorAsylumConsequences.apply(
                        fixture.state, fixture.row, 31));

        assertEquals(50, fixture.progress());
        assertEquals(0, fixture.rep(fixture.actor));
        assertEquals(5, fixture.rep(fixture.target));
        assertEquals(20, fixture.state.moralIntegrity);
    }

    @Test
    void timedOutPromiseReducesPlotBeforeItsDailyAdvancement() {
        Fixture fixture = new Fixture();
        DefectorAsylumEvent.commit(fixture.state, fixture.event, 20,
                new TestAsylumStore());
        DefectorAsylumEvent.advanceToFollowup(
                fixture.state, fixture.event, 30);
        fixture.state.chainProgress[fixture.chainRow] = 99;

        new CampaignEventLifecycleSystem().tick(fixture.state, 34);
        new DefectorAsylumConsequenceSystem().tick(fixture.state, 34);
        new ChainAdvancementSystem().tick(fixture.state, 34);

        assertEquals(CampaignEventState.RESOLVED,
                CampaignEventState.fromByte(
                        fixture.state.eventState[fixture.row]));
        assertEquals(DefectorAsylumOutcome.PROTECTED,
                DefectorAsylumOutcome.fromByte(
                        fixture.state.eventDefectorOutcome[fixture.row]));
        assertEquals(80, fixture.progress());
        assertEquals(ChainState.ACTIVE, ChainState.fromByte(
                fixture.state.chainState[fixture.chainRow]));
    }

    @Test
    void progressAndReputationClampAtTheirDomainBounds() {
        Fixture protection = new Fixture();
        protection.state.chainProgress[protection.chainRow] = 5;
        protection.state.repValue[
                protection.state.ensureRepRow(protection.target)] = 99;
        protection.protect();
        DefectorAsylumConsequences.apply(
                protection.state, protection.row, 31);

        Fixture betrayal = new Fixture();
        betrayal.state.chainProgress[betrayal.chainRow] = Short.MAX_VALUE - 5;
        betrayal.state.repValue[
                betrayal.state.ensureRepRow(betrayal.actor)] = 99;
        betrayal.state.repValue[
                betrayal.state.ensureRepRow(betrayal.target)] = -99;
        betrayal.betray();
        DefectorAsylumConsequences.apply(
                betrayal.state, betrayal.row, 31);

        assertEquals(0, protection.progress());
        assertEquals(100, protection.rep(protection.target));
        assertEquals(Short.MAX_VALUE, betrayal.progress());
        assertEquals(100, betrayal.rep(betrayal.actor));
        assertEquals(-100, betrayal.rep(betrayal.target));
    }

    @Test
    void expiryAndMalformedResolutionFailClosed() {
        Fixture expired = new Fixture();
        expired.state.eventState[expired.row] =
                CampaignEventState.EXPIRED.toByte();
        Fixture malformed = new Fixture();
        malformed.state.eventState[malformed.row] =
                CampaignEventState.RESOLVED.toByte();
        malformed.state.eventResolvedTick[malformed.row] = 30;

        assertEquals(DefectorAsylumConsequences.Result.NEUTRAL,
                DefectorAsylumConsequences.apply(
                        expired.state, expired.row, 30));
        assertEquals(DefectorAsylumConsequences.Result.INVALID,
                DefectorAsylumConsequences.apply(
                        malformed.state, malformed.row, 30));
        assertEquals(0, expired.state.moralChoiceCount);
        assertEquals(0, malformed.state.moralChoiceCount);
        assertEquals(50, malformed.progress());
        assertEquals(0, malformed.state.repCount);
    }

    @Test
    void persistedMoralSourcePreventsEffectsAfterLoad() throws Exception {
        Fixture fixture = new Fixture();
        fixture.betray();
        DefectorAsylumConsequences.apply(fixture.state, fixture.row, 31);
        int progress = fixture.progress();
        int actorRep = fixture.rep(fixture.actor);
        int targetRep = fixture.rep(fixture.target);

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(fixture.state);

        assertEquals(DefectorAsylumConsequences.Result.ALREADY_APPLIED,
                DefectorAsylumConsequences.apply(
                        fixture.state, fixture.row, 40));
        assertEquals(progress, fixture.progress());
        assertEquals(actorRep, fixture.rep(fixture.actor));
        assertEquals(targetRep, fixture.rep(fixture.target));
        assertEquals(1, fixture.state.moralChoiceCount);
    }

    @Test
    void systemDeclaresEveryReadAndWriteTable() {
        DefectorAsylumConsequenceSystem system =
                new DefectorAsylumConsequenceSystem();

        assertEquals(EnumSet.of(CampaignTable.EVENTS, CampaignTable.CHAINS,
                        CampaignTable.HOUSES, CampaignTable.PLAYER_REP,
                        CampaignTable.MORAL_COMPASS),
                system.reads());
        assertEquals(EnumSet.of(CampaignTable.CHAINS,
                        CampaignTable.PLAYER_REP, CampaignTable.MORAL_COMPASS),
                system.writes());
        assertTrue(system.name().contains("DefectorAsylum"));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("event_market");
        final long actor = house(state, market, "Actor");
        final long target = house(state, market, "Target");
        final long chain = state.addAutonomousChain(actor, target, market, 7,
                (byte) 1, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 100, (byte) 32, 1);
        final int chainRow = state.chainIndex(chain);
        final long event = DefectorAsylumEvent.prepare(state, 77L,
                chain, actor, target, market,
                20, 23, 20, 10, 40_000);
        final int row = state.eventIndex(event);

        Fixture() {
            state.chainProgress[chainRow] = 50;
        }

        void protect() {
            DefectorAsylumEvent.commit(state, event, 20,
                    new TestAsylumStore());
            DefectorAsylumEvent.advanceToFollowup(state, event, 30);
            DefectorAsylumEvent.protect(state, event, 30);
        }

        void betray() {
            DefectorAsylumEvent.commit(state, event, 20,
                    new TestAsylumStore());
            DefectorAsylumEvent.advanceToFollowup(state, event, 30);
            DefectorAsylumEvent.betray(state, event, 31,
                    new TestCreditStore());
        }

        int progress() {
            return state.chainProgress[chainRow];
        }

        int rep(long houseId) {
            int row = state.repIndex(houseId);
            return row >= 0 ? state.repValue[row] : 0;
        }
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }

    private static final class TestAsylumStore
            implements DefectorAsylumEvent.AsylumStore {
        @Override
        public boolean consume(int supplies, int fuel) {
            return true;
        }
    }

    private static final class TestCreditStore
            implements DefectorAsylumEvent.CreditStore {
        @Override
        public boolean grant(int credits) {
            return true;
        }
    }
}
