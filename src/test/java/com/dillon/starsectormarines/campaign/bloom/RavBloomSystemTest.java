package com.dillon.starsectormarines.campaign.bloom;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.dillon.starsectormarines.campaign.systems.CampaignEventLifecycleSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavBloomSystemTest {

    private static final BloomTuning TUNING = BloomTuning.DEFAULT;
    private static final BloomTuning HARSH = new BloomTuning(BloomTuning.Severity.DARK_AND_SPREAD,
            BloomTuning.Frequency.SEASONAL, BloomTuning.PlayerColonies.EXPOSED);

    @Test
    void eruptionRaisesTheDistressCallThroughTheSharedGate() {
        Fixture f = new Fixture().colony("asharu", "corvus", 5, 0f);
        RavBloom bloom = f.system.wakeAt(f.state, "asharu", 100);

        f.runTo(100 + bloom.stirDays - 1);
        assertEquals(BloomStage.STIRRING, bloom.stage());
        assertEquals(0, f.state.eventCount);

        f.runTo(100 + bloom.stirDays);
        assertEquals(BloomStage.ERUPTED, bloom.stage());
        int row = f.state.eventIndex(bloom.rescueEventId());
        assertTrue(row >= 0);
        assertEquals(CampaignEventState.PENDING_CHOICE, CampaignEventState.fromByte(f.state.eventState[row]));
        assertEquals(bloom.eruptDay() + RavBloomRules.CALL_WINDOW_DAYS, f.state.eventDeadlineTick[row]);
    }

    @Test
    void anOpenEventHoldsTheCallAndACallThatNeverGetsThroughGoesDark() {
        Fixture f = new Fixture().colony("asharu", "corvus", 5, 0f);
        long blocker = CivilianRescueEvent.prepare(f.state, 1L,
                f.state.marketRegistry.intern("elsewhere"), 0, 1000, 1, 1, 100);
        RavBloom bloom = f.system.wakeAt(f.state, "asharu", 100);

        f.runTo(100 + bloom.stirDays + RavBloomRules.CALL_WINDOW_DAYS + 1);

        assertEquals(-1L, bloom.rescueEventId());
        assertEquals(BloomStage.DARK, bloom.stage());
        assertTrue(f.state.eventIndex(blocker) >= 0);
    }

    @Test
    void atTheBenignStageTheMilitaryPutsAnUnansweredBloomDownAndOnlyTheConditionCost() {
        Fixture f = new Fixture().colony("asharu", "corvus", 5, 0f);
        RavBloom bloom = f.system.wakeAt(f.state, "asharu", 100);
        f.runTo(100 + bloom.stirDays);
        CivilianRescueEvent.refuse(f.state, bloom.rescueEventId(), bloom.eruptDay());

        f.runTo(bloom.eruptDay() + 1);
        assertEquals(BloomStage.DARK, bloom.stage());
        f.runTo(bloom.darkDay() + BloomTuning.Severity.BENIGN.burnoutDays);

        assertEquals(BloomEnd.PUT_DOWN, bloom.end());
        assertEquals(5, f.world.get("asharu").size);
        assertEquals(0, bloom.seeded());
        assertTrue(f.world.log.contains("dark asharu"));
        assertTrue(f.world.log.contains("ended asharu PUT_DOWN unrest 0"));
    }

    @Test
    void atAHarsherTierARefusedCallEatsTheColonyDownAndBurnsOut() {
        Fixture f = new Fixture(HARSH).colony("asharu", "corvus", 5, 0f);
        RavBloom bloom = f.system.wakeAt(f.state, "asharu", 100);
        f.runTo(100 + bloom.stirDays);
        assertEquals(CivilianRescueEvent.Result.REFUSED,
                CivilianRescueEvent.refuse(f.state, bloom.rescueEventId(), bloom.eruptDay()));

        f.runTo(bloom.eruptDay() + 1);
        assertEquals(BloomStage.DARK, bloom.stage());
        assertTrue(bloom.refused());
        assertEquals(-2, (int) f.world.reputation.get("independent"));

        f.runTo(bloom.darkDay() + HARSH.severity.burnoutDays);
        assertEquals(BloomEnd.BURNED_OUT, bloom.end());
        assertTrue(f.world.log.contains("dark asharu disrupted"));
        assertEquals(RavBloomRules.MIN_SIZE, f.world.get("asharu").size);
        assertEquals(2, bloom.sizeLost());
        assertTrue(f.state.ravBlooms().resting("asharu", bloom.endDay() + 364));
        assertFalse(f.state.ravBlooms().resting("asharu", bloom.endDay() + 365));
    }

    @Test
    void collapsingTheMotherBoreEndsTheOutbreakAndRestsTheReservoir() {
        Fixture f = new Fixture().add(new BloomMarket("jangala", "Jangala", "jangala", "hegemony", 6,
                3650, false, false, false, true, true, true, 0f, 0f, 2f, 1.5f, 0f));
        RavBloom bloom = f.system.wakeAt(f.state, "jangala", 100);
        f.runTo(100 + bloom.stirDays);
        assertTrue(bloom.quarantined());

        int atRisk = f.evacuate(bloom, 0.2, true);
        f.runTo(f.day + 1);

        assertEquals(BloomEnd.CONTAINED, bloom.end());
        assertEquals(atRisk / 5, bloom.evacuated());
        assertEquals(6, f.world.get("jangala").size);
        assertEquals(3, (int) f.world.reputation.get(RavBloomRules.HEGEMONY));
        assertEquals(1, (int) f.world.reputation.get(RavBloomRules.CHURCH));
        assertTrue(f.state.ravBlooms().resting("jangala", f.day + RavBloomRules.REST_AFTER_CONTAINED_DAYS - 2));
        assertTrue(f.state.ravBlooms().playerAnswered());
    }

    @Test
    void liftingHalfTheColonyStarvesTheBloom() {
        Fixture f = new Fixture().colony("asharu", "corvus", 5, 0f);
        RavBloom bloom = f.system.wakeAt(f.state, "asharu", 100);
        f.runTo(100 + bloom.stirDays);

        f.evacuate(bloom, 0.5, false);
        f.runTo(f.day + 1);
        assertEquals(BloomStage.STARVED, bloom.stage());

        f.runTo(bloom.starvedDay() + RavBloomRules.STARVED_DAYS);
        assertEquals(BloomEnd.STARVED_OUT, bloom.end());
        assertEquals(5, f.world.get("asharu").size);
    }

    @Test
    void anEndedBloomClosesItsStillOpenCall() {
        Fixture f = new Fixture().colony("asharu", "corvus", 5, 0f);
        RavBloom bloom = f.system.wakeAt(f.state, "asharu", 100);
        f.runTo(100 + bloom.stirDays);
        int row = f.state.eventIndex(bloom.rescueEventId());
        f.state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        f.state.eventDecisionTick[row] = f.day;

        f.runTo(bloom.eruptDay() + RavBloomRules.DARK_AFTER_DAYS + TUNING.severity.burnoutDays);

        assertEquals(TUNING.severity.unansweredEnd, bloom.end());
        assertEquals(CampaignEventState.EXPIRED, CampaignEventState.fromByte(f.state.eventState[row]));
    }

    @Test
    void cargoLandsInTheSameSystemBeforeTheNearestWorld() {
        Fixture f = new Fixture()
                .colony("source", "corvus", 5, 0f)
                .colony("near", "other", 5, 1f)
                .colony("sibling", "corvus", 4, 5f)
                .colony("far", "far", 5, 20f);
        RavBloom from = f.system.wakeAt(f.state, "source", 100);
        List<BloomMarket> markets = f.world.markets();

        assertEquals("sibling", f.system.spreadTarget(f.state.ravBlooms(), from, markets, 100).id);

        f.world.add(TestBloomWorld.withSize(f.world.get("sibling"), 2));
        assertEquals("near", f.system.spreadTarget(f.state.ravBlooms(), from, f.world.markets(), 100).id);

        f.world.decivilize("near");
        assertNull(f.system.spreadTarget(f.state.ravBlooms(), from, f.world.markets(), 100));
    }

    @Test
    void playerColoniesFollowTheOwnersPick() {
        BloomMarket mine = new BloomMarket("mine", "Mine", "home", "player", 4, 3650, false, true,
                false, false, true, false, 0f, 0f, 30f, 0.5f, 1f);
        Fixture never = new Fixture(new BloomTuning(TUNING.severity, TUNING.frequency,
                BloomTuning.PlayerColonies.NEVER)).colony("source", "home", 5, 0f).add(mine);
        RavBloom from = never.system.wakeAt(never.state, "source", 100);
        assertNull(never.system.spreadTarget(never.state.ravBlooms(), from, never.world.markets(), 100));

        Fixture afterFirst = new Fixture(new BloomTuning(TUNING.severity, TUNING.frequency,
                BloomTuning.PlayerColonies.AFTER_FIRST)).colony("source", "home", 5, 0f).add(mine);
        RavBloom first = afterFirst.system.wakeAt(afterFirst.state, "source", 100);
        assertNull(afterFirst.system.spreadTarget(afterFirst.state.ravBlooms(), first,
                afterFirst.world.markets(), 100));
        afterFirst.runTo(100 + first.stirDays);
        afterFirst.evacuate(first, 0.1, false);
        afterFirst.runTo(afterFirst.day + 1);
        assertNotNull(afterFirst.system.spreadTarget(afterFirst.state.ravBlooms(), first,
                afterFirst.world.markets(), afterFirst.day));
    }

    @Test
    void trafficAndInflowDrawTheSpores() {
        BloomMarket quiet = new BloomMarket("q", "q", "s", "independent", 5, 3650, false, false,
                false, false, false, false, 0f, 0f, 10f, 0f, 0f);
        BloomMarket busy = new BloomMarket("q", "q", "s", "independent", 5, 3650, false, false,
                false, false, false, false, 0f, 0f, 10f, 1.5f, 1f);
        assertTrue(RavBloomRules.wakeWeight(busy, 1L) > RavBloomRules.wakeWeight(quiet, 1L));
        BloomMarket young = new BloomMarket("q", "q", "s", "independent", 5, 30, false, false,
                false, false, false, false, 0f, 0f, 10f, 1.5f, 1f);
        assertEquals(0.0, RavBloomRules.wakeWeight(young, 1L));
    }

    @Test
    void reservoirsAreFixedForTheSave() {
        BloomMarket market = new TestBloomWorld(1L).colony("asharu", "corvus", 5, 0f).get("asharu");
        boolean first = RavBloomRules.hasReservoir(42L, market);
        assertEquals(first, RavBloomRules.hasReservoir(42L, market));
        boolean differs = false;
        for (long seed = 0; seed < 64 && !differs; seed++) {
            differs = RavBloomRules.hasReservoir(seed, market) != first;
        }
        assertTrue(differs, "a reservoir is a fact of the save, not of the market");
    }

    /** A sector in memory and the clock that walks the bloom through it. */
    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final TestBloomWorld world;
        final RavBloomSystem system;
        final CampaignEventLifecycleSystem lifecycle = new CampaignEventLifecycleSystem();
        int day = -1;

        Fixture() {
            this(TUNING);
        }

        Fixture(BloomTuning tuning) {
            world = new TestBloomWorld(0L);
            // No weekly draw, so nothing wakes that the test did not wake.
            system = new RavBloomSystem(world, tuning, false);
        }

        Fixture colony(String id, String systemId, int size, float x) {
            world.colony(id, systemId, size, x);
            return this;
        }

        Fixture add(BloomMarket market) {
            world.add(market);
            return this;
        }

        void runTo(int last) {
            for (int d = Math.max(0, day + 1); d <= last; d++) {
                lifecycle.tick(state, d);
                system.tick(state, d);
                day = d;
            }
        }

        /** Commits and resolves the bloom's call with a share of the cohort lifted out. */
        int evacuate(RavBloom bloom, double share, boolean motherCollapsed) {
            int row = state.eventIndex(bloom.rescueEventId());
            state.eventState[row] = CampaignEventState.COMMITTED.toByte();
            state.eventDecisionTick[row] = day;
            int atRisk = state.eventCiviliansAtRisk[row];
            CivilianRescueEvent.resolve(state, bloom.rescueEventId(), (int) (atRisk * share), day);
            if (motherCollapsed) state.ravBlooms().recordMotherBoreCollapsed(bloom.rescueEventId());
            return atRisk;
        }
    }
}
