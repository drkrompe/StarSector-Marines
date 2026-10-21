package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which Latchkey Yard sites the constables hold, and which are left to the company when a section is wiped out
 * ({@link YardOwnersCommand#orphanedSites}). Dispatch is site 0, the operator entrance site 2.
 */
class YardOwnersSitesTest {

    @Test
    void theFirstSectionHoldsDispatchAndTheRestTheEntrance() {
        assertEquals(0, YardOwnersCommand.constableSite(0));
        assertEquals(2, YardOwnersCommand.constableSite(1));
        assertEquals(2, YardOwnersCommand.constableSite(2));
    }

    @Test
    void aSiteIsOrphanedOnlyWhenEverySectionOwingItIsDead() {
        List<Integer> sections = List.of(3, 4, 8);
        assertEquals(0, YardOwnersCommand.orphanedSites(sections, id -> true));
        assertEquals(0, YardOwnersCommand.orphanedSites(sections, id -> id != 4), "another section still owes the entrance");
        assertEquals(1 << 2, YardOwnersCommand.orphanedSites(sections, id -> id == 3));
        assertEquals(1 | 1 << 2, YardOwnersCommand.orphanedSites(sections, id -> false));
        assertEquals(1, YardOwnersCommand.orphanedSites(List.of(3, 4), id -> Set.of(4).contains(id)));
        assertEquals(0, YardOwnersCommand.orphanedSites(List.of(), id -> false), "no constables owe nothing");
    }
}
