package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The roster ranks the company without arithmetic, on an absolute scale. */
class CompanyRosterViewTest {

    private static MarineRoster company() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY * 2);
        return roster;
    }

    @Test
    void aptitudeSortsBestFirstAndTheFallenAreNotListed() {
        MarineRoster roster = company();
        String fallen = roster.soldiers().get(0).id();
        roster.applySoldierOutcome(Map.of(fallen, MarineSoldierStatus.KIA), 10f, 1f);

        List<MarineSoldier> sorted = CompanyRosterView.sorted(roster, CompanyRosterView.Sort.APTITUDE);
        assertFalse(sorted.stream().anyMatch(s -> s.id().equals(fallen)));
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i - 1).aptitude().ordinal() >= sorted.get(i).aptitude().ordinal());
        }
        List<MarineSoldier> byName = CompanyRosterView.sorted(roster, CompanyRosterView.Sort.NAME);
        for (int i = 1; i < byName.size(); i++) {
            assertTrue(byName.get(i - 1).name().compareTo(byName.get(i).name()) <= 0);
        }
    }

    @Test
    void stepsAreAbsoluteAndMarkTheCompanyMedian() {
        List<Map<String, Object>> steps = CompanyRosterView.steps("x", SoldierAptitude.STEADY.ordinal(),
                SoldierAptitude.GIFTED.ordinal());
        assertEquals(4, steps.size());
        assertEquals("roster-step roster-step-on", steps.get(1).get("classes"));
        assertEquals("roster-step roster-step-off roster-step-median", steps.get(2).get("classes"));
    }

    @Test
    void theMedianIsTheMiddleAptitudeOfTheCompany() {
        MarineRoster roster = company();
        List<MarineSoldier> marines = CompanyRosterView.sorted(roster, CompanyRosterView.Sort.NAME);
        List<SoldierAptitude> aptitudes = marines.stream().map(MarineSoldier::aptitude).sorted().toList();
        assertEquals(aptitudes.get(aptitudes.size() / 2), CompanyRosterView.medianAptitude(marines));
    }

    @Test
    void rowsArePagedAndCarryEveryAxisTheSimulationReads() {
        MarineRoster roster = company();
        List<Map<String, Object>> first = CompanyRosterView.rows(roster, CompanyRosterView.Sort.BAND, 0, 0);
        assertEquals(CompanyRosterView.PAGE_SIZE, first.size());
        assertEquals(2, CompanyRosterView.pages(roster.soldiers().size()));
        Map<String, Object> row = first.get(0);
        for (String key : List.of("name", "squad", "status", "aptitude", "band", "weapon", "armor", "service")) {
            assertTrue(row.get(key) instanceof String text && !text.isBlank(), key);
        }
        assertEquals(roster.soldiers().size() - CompanyRosterView.PAGE_SIZE,
                CompanyRosterView.rows(roster, CompanyRosterView.Sort.BAND, 1, 0).size());
    }
}
