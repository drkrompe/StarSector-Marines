package com.dillon.starsectormarines.battle.fixture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TailMechSupportTest {
    @Test void noInputDefaultNeverSchedulesAnActivation() {
        TailMechSupport helper = new TailMechSupport(0, 301, 240, 300);
        assertFalse(helper.schedule.due(301));
        assertFalse(helper.schedule.due(Integer.MAX_VALUE));
        assertEquals("", helper.insufficientCoverage());
    }

    @Test void waitsForReadinessWithoutBurningSlotsAndRequiresActualChargeDebit() {
        TailMechSupport.Schedule schedule = new TailMechSupport.Schedule(2, 301, 240);
        assertFalse(schedule.due(300));
        assertTrue(schedule.due(301));
        // A caller unable to afford the power leaves it due instead of queuing rejected work.
        assertTrue(schedule.due(320));
        schedule.submit(320, 35);
        assertFalse(schedule.due(321));
        assertTrue(schedule.resolve(34));
        assertFalse(schedule.due(559));
        assertTrue(schedule.due(560));
        schedule.submit(560, 34);
        assertFalse(schedule.resolve(34));
        assertEquals(2, schedule.attempted);
        assertEquals(1, schedule.committed);
        assertFalse(schedule.due(900));
    }

    @Test void refusedTargetConsumesOnlyItsScriptSlotAndMissingDeploymentFailsCoverage() throws Exception {
        TailMechSupport helper = new TailMechSupport(2, 301, 240, 300);
        helper.schedule.reject(301);
        assertFalse(helper.schedule.due(540));
        assertTrue(helper.schedule.due(541));
        assertFalse(helper.insufficientCoverage().isEmpty());
        TailMechSupport accepted = new TailMechSupport(1, 1, 1, 0);
        accepted.schedule.submit(1, 1);
        accepted.schedule.resolve(0);
        assertTrue(accepted.insufficientCoverage().contains("no landed combat mech"));
        assertEquals(0, accepted.json().getInt("uniqueObservedSupportMechs"));
    }

    @Test void censusSeparatesSidesRolesAndActualSupportFromWorksAndExcludesWarmupTime() throws Exception {
        TailMechSupport.Census census = new TailMechSupport.Census();
        census.beginTick();
        census.observe(1, "DEFENDER", "hound", "ASSAULT", false, false);
        census.endTick();
        census.beginTick();
        census.observe(1, "DEFENDER", "hound", "ASSAULT", false, true);
        census.observe(2, "MARINE", "hound", "ASSAULT", true, true);
        census.observe(3, "MARINE", "hound", "ASSAULT", false, true);
        census.observe(4, "MARINE", "sirocco", "LR_SUPPORT", true, true);
        census.endTick();
        census.beginTick();
        census.observe(2, "MARINE", "hound", "BALANCED", true, true);
        census.endTick();
        JSONArray rows = census.json();
        assertEquals(4, rows.length());
        JSONObject defender = rows.getJSONObject(0);
        assertEquals(1, defender.getInt("peakLive"));
        assertEquals(1, defender.getLong("unitTicks"));
        assertEquals(0, defender.getLong("supportUnitTicks"));
        JSONObject assault = rows.getJSONObject(1);
        assertEquals(2, assault.getInt("peakLive"));
        assertEquals(0, assault.getInt("liveAtEnd"));
        assertEquals(2, assault.getInt("uniqueObserved"));
        assertEquals(2, assault.getLong("unitTicks"));
        assertEquals(1, assault.getLong("supportUnitTicks"));
        assertEquals("BALANCED", rows.getJSONObject(3).getString("effectiveRole"));
    }

    @Test void invalidSchedulesAreRejectedAndTickArithmeticDoesNotOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new TailMechSupport.Schedule(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TailMechSupport.Schedule(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TailMechSupport.Schedule(1, 1, 0));
        TailMechSupport.Schedule schedule = new TailMechSupport.Schedule(2, 1, 100);
        schedule.reject(Integer.MAX_VALUE - 2);
        assertFalse(schedule.due(Integer.MAX_VALUE));
    }
}
