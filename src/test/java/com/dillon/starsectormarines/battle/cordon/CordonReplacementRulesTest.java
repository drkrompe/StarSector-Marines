package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonAlliedRules.Garrison;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The allied line command's replacement rule ({@code cordon-nouns.md}, Replacements) on hand-built garrisons. */
class CordonReplacementRulesTest {

    private static final int TEAM = 4;

    private static Garrison g(int number, int standing, int coming) {
        return new Garrison(number, true, false, 12, standing, coming);
    }

    @Test
    void aRedoubtAsksOnceATeamsWorthOfMenIsDown() {
        assertEquals(0, CordonAlliedRules.teamsShort(g(1, 9, 0), TEAM), "three down is not a team");
        assertEquals(1, CordonAlliedRules.teamsShort(g(1, 8, 0), TEAM));
        assertEquals(2, CordonAlliedRules.teamsShort(g(1, 3, 0), TEAM));
        assertEquals(0, CordonAlliedRules.teamsShort(g(1, 16, 0), TEAM), "a column that joined raises nothing");
    }

    @Test
    void squadsAlreadyComingCountAgainstTheShortfall() {
        assertEquals(1, CordonAlliedRules.teamsShort(g(1, 4, 1), TEAM));
        assertEquals(0, CordonAlliedRules.teamsShort(g(1, 4, 2), TEAM));
    }

    @Test
    void aFallenOrGivenUpRedoubtIsNeverRefilled() {
        Garrison fallen = new Garrison(2, false, false, 12, 0, 0);
        assertEquals(0, CordonAlliedRules.teamsShort(fallen, TEAM));
        assertNull(CordonAlliedRules.replacement(List.of(fallen), TEAM, 2, 0, 2, false));
        assertArrayEquals(new int[]{1, 1}, CordonAlliedRules.replacement(List.of(fallen, g(1, 8, 0)), TEAM, 2, 0, 2, false));
    }

    @Test
    void theRedoubtMostShortIsServedACallingOneBreakingATie() {
        assertArrayEquals(new int[]{3, 2},
                CordonAlliedRules.replacement(List.of(g(1, 8, 0), g(3, 2, 0)), TEAM, 2, 0, 2, false));
        Garrison calling = new Garrison(4, true, true, 12, 8, 0);
        assertArrayEquals(new int[]{4, 1},
                CordonAlliedRules.replacement(List.of(g(1, 8, 0), calling), TEAM, 2, 0, 2, false));
    }

    @Test
    void aCallIsCappedByItsSizeTheLinesRoomInFlightAndTheCooldown() {
        List<Garrison> bled = List.of(g(1, 0, 0));
        assertArrayEquals(new int[]{1, 2}, CordonAlliedRules.replacement(bled, TEAM, 2, 0, 2, false), "three short, two a call");
        assertArrayEquals(new int[]{1, 1}, CordonAlliedRules.replacement(bled, TEAM, 2, 1, 2, false), "one already in flight");
        assertNull(CordonAlliedRules.replacement(bled, TEAM, 2, 2, 2, false), "the line's room in flight is full");
        assertNull(CordonAlliedRules.replacement(bled, TEAM, 2, 0, 2, true), "the last call's cooldown runs");
        assertNull(CordonAlliedRules.replacement(List.of(g(1, 12, 0)), TEAM, 2, 0, 2, false), "nobody short");
    }

    private static Garrison camp(int standing, int coming) {
        return new Garrison(CordonAlliedRules.CAMP, true, false, 8, standing, coming);
    }

    @Test
    void theDefensesBehindTheLineAreRegarrisonedNeverTheTownOutsideTheWire() {
        assertTrue(CordonAlliedRules.regarrisoned(CordonAlliedRules.Defense.REDOUBT));
        assertTrue(CordonAlliedRules.regarrisoned(CordonAlliedRules.Defense.CAMP));
        assertFalse(CordonAlliedRules.regarrisoned(CordonAlliedRules.Defense.TOWN));
        assertFalse(CordonAlliedRules.regarrisoned(CordonAlliedRules.Defense.OUTER_PICKET));
        assertFalse(CordonAlliedRules.regarrisoned(CordonAlliedRules.Defense.PICKET_LINE), "it falls back before it is a team short");
    }

    @Test
    void aFallenCampIsNeverRefilled() {
        Garrison fallen = new Garrison(CordonAlliedRules.CAMP, false, false, 8, 0, 0);
        assertEquals(0, CordonAlliedRules.teamsShort(fallen, TEAM));
        assertNull(CordonAlliedRules.replacement(List.of(fallen), TEAM, 2, 0, 2, false));
    }

    @Test
    void theCampAndTheRedoubtsShareOneLinesCaps() {
        List<Garrison> both = List.of(g(1, 8, 0), camp(0, 0));
        assertNull(CordonAlliedRules.replacement(both, TEAM, 2, 2, 2, false), "two on the way anywhere fill the line");
        assertNull(CordonAlliedRules.replacement(both, TEAM, 2, 0, 2, true), "one cooldown for every post");
        assertArrayEquals(new int[]{CordonAlliedRules.CAMP, 1}, CordonAlliedRules.replacement(both, TEAM, 2, 1, 2, false));
    }

    @Test
    void theMostShortPostIsServedFirstARedoubtBeforeTheCampOnATie() {
        assertArrayEquals(new int[]{CordonAlliedRules.CAMP, 2},
                CordonAlliedRules.replacement(List.of(g(1, 8, 0), camp(0, 0)), TEAM, 2, 0, 2, false));
        assertArrayEquals(new int[]{1, 1},
                CordonAlliedRules.replacement(List.of(g(1, 8, 0), camp(4, 0)), TEAM, 2, 0, 2, false));
        assertArrayEquals(new int[]{1, 1},
                CordonAlliedRules.replacement(List.of(g(1, 8, 0), camp(0, 1)), TEAM, 2, 0, 2, false), "the camp's coming squad counts");
    }

    @Test
    void onlyTheRoadReplacesLosses() {
        assertTrue(CordonAlliedRules.replacesLosses(CordonVariant.ROAD));
        assertFalse(CordonAlliedRules.replacesLosses(CordonVariant.LINE));
    }
}
