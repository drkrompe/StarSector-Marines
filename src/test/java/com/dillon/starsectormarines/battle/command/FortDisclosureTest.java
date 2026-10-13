package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fort is known by fair rules: a company that has its wall in view knows there is a fort there, though the platform
 * stands inside the wall where nothing outside can see it. What discloses a structure is any of its cells in view, and
 * nothing else does.
 */
class FortDisclosureTest {

    private static final int PLATFORM_X = 50, PLATFORM_Y = 50;

    private static DefenceStructureFact fort(int... works) {
        return new DefenceStructureFact(Kind.FIRE_CONTROL, 60, 60, Faction.DEFENDER, Faction.DEFENDER, false, 4,
                PLATFORM_X, PLATFORM_Y, 0, Family.BATTERY_FORT, works);
    }

    private static boolean sees(DefenceStructureFact fact, int... visible) {
        Set<Long> in = new HashSet<>();
        for (int i = 0; i + 1 < visible.length; i += 2) in.add(((long) visible[i] << 32) | visible[i + 1]);
        return ConquestCommandFacts.seesAny(fact, (x, y) -> in.contains(((long) x << 32) | y));
    }

    @Test
    void aWallInViewDisclosesTheFortWithoutTheInnerPlatformBeingSeen() {
        DefenceStructureFact fact = fort(10, 10, 11, 10, 12, 10);
        assertTrue(sees(fact, 11, 10), "one cell of the wall is enough");
        assertFalse(sees(fact, PLATFORM_X + 1, PLATFORM_Y), "a cell beside the platform is not the platform or the wall");
    }

    @Test
    void thePlatformStillDisclosesIt() {
        assertTrue(sees(fort(10, 10), PLATFORM_X, PLATFORM_Y));
    }

    @Test
    void nothingInViewDisclosesNothing() {
        assertFalse(sees(fort(10, 10, 11, 10)));
    }

    @Test
    void aBareStructureIsStillDisclosedOnlyByItsOwnCell() {
        DefenceStructureFact bare = new DefenceStructureFact(Kind.SHIELD_GENERATOR, 30, 30, Faction.DEFENDER,
                Faction.DEFENDER, false, 16);
        assertTrue(sees(bare, 30, 30));
        assertFalse(sees(bare, 10, 10));
    }

    @Test
    void aSideIsToldOfWhatItHoldsWhatItSawAndWhatItHasSeenBeforeAndNothingElse() {
        assertTrue(ConquestCommandFacts.isDisclosed(true, false, false), "its own");
        assertTrue(ConquestCommandFacts.isDisclosed(false, true, false), "in view now");
        assertTrue(ConquestCommandFacts.isDisclosed(false, false, true), "seen before");
        assertFalse(ConquestCommandFacts.isDisclosed(false, false, false), "never seen");
    }
}
