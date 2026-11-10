package com.dillon.starsectormarines.battle.breach;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which hall wall a squad works when it has nothing else to fight ({@code WorksDemolition}). */
class WorksDemolitionTest {

    /** Machine 0 in a hall whose outline is (10,10), (11,10) and (12,10); machine 1 in the open. */
    private static RaidObjective works() {
        return RaidObjective.works("W", "the works", List.of(new RaidObjective.Site(11, 11, 0),
                        new RaidObjective.Site(40, 40, 0)), 0, 0)
                .withHalls(List.of(new RaidObjective.Hall(List.of(new int[]{10, 10}, new int[]{11, 10}, new int[]{12, 10}),
                        List.of(new int[]{11, 11})), RaidObjective.Hall.NONE), site -> { });
    }

    private static long packed(int x, int y) { return (x & 0xFFFFFFFFL) | ((long) y << 32); }

    @Test
    void aSquadWithNothingToFightWorksTheNearestStandingWallOfTheHall() {
        RaidObjective works = works();
        assertEquals(packed(12, 10), WorksDemolition.cell(works, (x, y) -> true, 20.5f, 10.5f, false));
        assertEquals(packed(11, 10), WorksDemolition.cell(works, (x, y) -> x != 12, 20.5f, 10.5f, false),
                "a fallen wall is passed over for the next standing one");
    }

    @Test
    void anEnemyInSightComesFirstAndAFarHallIsLeftAlone() {
        RaidObjective works = works();
        assertEquals(-1L, WorksDemolition.cell(works, (x, y) -> true, 20.5f, 10.5f, true));
        assertEquals(-1L, WorksDemolition.cell(works, (x, y) -> true, 12.5f + WorksDemolition.REACH + 1f, 10.5f, false));
    }
}
