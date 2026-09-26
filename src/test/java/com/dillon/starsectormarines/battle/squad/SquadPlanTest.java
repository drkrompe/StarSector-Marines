package com.dillon.starsectormarines.battle.squad;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SquadPlanTest {

    @Test
    void slotLookupUsesIndexedAccessForRandomAccessMembers() {
        SquadPlan.Step step = new SquadPlan.Step(null);
        List<Long> members = new ArrayList<>(List.of(1001L, 1002L, 1003L)) {
            @Override
            public Iterator<Long> iterator() {
                throw new AssertionError("slot lookup must not allocate a member iterator");
            }
        };
        step.assignments.put("any", members);

        assertEquals("any", step.slotOf(1001L));
        assertEquals("any", step.slotOf(1002L));
        assertEquals("any", step.slotOf(1003L));
        assertNull(step.slotOf(2001L));
    }

    @Test
    void slotLookupPreservesSlotOrderAndSkipsEmptySlots() {
        SquadPlan.Step step = new SquadPlan.Step(null);
        assertNull(step.slotOf(1001L));
        step.assignments.put("empty", List.of());
        step.assignments.put("first", List.of(1001L, 1002L));
        step.assignments.put("second", List.of(1002L, 1003L));

        assertEquals("first", step.slotOf(1002L));
        assertEquals("second", step.slotOf(1003L));
        assertNull(step.slotOf(2001L));
    }

    @Test
    void slotLookupSeesRoleRebindingWithoutRetainingOldAssignments() {
        SquadPlan.Step step = new SquadPlan.Step(null);
        step.assignments.put("old", List.of(1001L, 1002L));
        assertEquals("old", step.slotOf(1001L));

        step.assignments.clear();
        step.assignments.put("new", List.of(1002L, 1003L));

        assertNull(step.slotOf(1001L));
        assertEquals("new", step.slotOf(1002L));
        assertEquals("new", step.slotOf(1003L));
    }

    @Test
    void slotLookupStillSupportsSequentialMemberLists() {
        SquadPlan.Step step = new SquadPlan.Step(null);
        step.assignments.put("any", new LinkedList<>(List.of(1001L, 1002L)));

        assertEquals("any", step.slotOf(1002L));
        assertNull(step.slotOf(2001L));
    }
}
