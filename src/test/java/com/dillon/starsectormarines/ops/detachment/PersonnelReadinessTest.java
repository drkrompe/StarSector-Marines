package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonnelReadinessTest {

    @Test
    void distinguishesSelectionShortfallFromCompanyShortfall() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        MarineSquad first = roster.squads().get(0);

        PersonnelReadiness selected = PersonnelReadiness.assess(
                roster, Set.of(first.id()), 8);

        assertEquals(6, selected.selectedReady());
        assertEquals(10, selected.companyReady());
        assertEquals(2, selected.selectedShortfall());
        assertEquals(0, selected.companyShortfall());
        assertFalse(selected.ready());
        assertFalse(selected.needsRecruitment());
    }

    @Test
    void emptySelectionUsesTheWholeLineRoster() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);

        PersonnelReadiness readiness = PersonnelReadiness.assess(
                roster, Collections.emptySet(), 12);

        assertEquals(10, readiness.selectedReady());
        assertEquals(2, readiness.selectedShortfall());
        assertEquals(2, readiness.companyShortfall());
        assertTrue(readiness.needsRecruitment());
    }
}
