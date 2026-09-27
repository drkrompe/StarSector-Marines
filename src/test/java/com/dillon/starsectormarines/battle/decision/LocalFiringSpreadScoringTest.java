package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Small scorer fixture, with no simulation/tick/generated map. */
class LocalFiringSpreadScoringTest {
    @Test void bothSearchesKeepWinnerWhileGatheringOnlyOncePerSearch() {
        NavigationGrid grid = new NavigationGrid(48, 32);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 48; x++) grid.setWalkableFloor(x, y);
        TickInnerProfile previous = TickInnerProfile.currentIfBound();
        TickInnerProfile profile = new TickInnerProfile();
        try (NavigationService nav = new NavigationService(grid, new CellTopology(48, 32), false)) {
            UnitRosterService roster = new UnitRosterService(nav.getUnitIndex(), null);
            nav.setRoster(roster);
            roster.setNavigationGrid(grid);
            long self = roster.spawn(new EntitySpec("self", Faction.MARINE, UnitType.MARINE, 12, 10));
            long target = roster.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 28, 10));
            roster.world().setAttackRange(self, 24);
            for (int i = 0; i < 30; i++) {
                long ally = roster.spawn(new EntitySpec("ally" + i, i % 3 == 0 ? Faction.ALLY : Faction.MARINE,
                        UnitType.MARINE, 10 + i % 6, 8 + i / 6));
                roster.world().setPathRef(ally, new int[]{15, 10 + i % 3});
            }
            nav.getUnitIndex().rebuild(roster);
            nav.getDestIndex().rebuild(roster);
            TacticalScoring control = scoring(false, nav, roster, grid);
            TacticalScoring local = scoring(true, nav, roster, grid);
            TickInnerProfile.setCurrent(profile);
            for (boolean within : new boolean[]{false, true}) {
                profile.reset();
                int[] expected = pick(control, within, self, target);
                int oldQueries = profile.countOf(TickInnerProfile.Bucket.FIRING_SPREAD_QUERY);
                int oldCandidates = profile.countOf(TickInnerProfile.Bucket.FIRING_SPREAD_CANDIDATE);
                profile.reset();
                int[] actual = pick(local, within, self, target);
                assertNotNull(expected);
                assertArrayEquals(expected, actual);
                assertEquals(oldCandidates, profile.countOf(TickInnerProfile.Bucket.FIRING_SPREAD_CANDIDATE));
                assertEquals(1, profile.countOf(TickInnerProfile.Bucket.FIRING_SPREAD_QUERY));
                assertTrue(oldQueries > 1);
            }
        } finally {
            TickInnerProfile.setCurrent(previous);
        }
    }

    private static int[] pick(TacticalScoring scoring, boolean within, long self, long target) {
        return within ? scoring.findFiringPositionWithin(self, target, 16, 10, 12, -1)
                : scoring.findFiringPosition(self, target, -1, -1);
    }

    private static TacticalScoring scoring(boolean enabled, NavigationService nav,
                                           UnitRosterService roster, NavigationGrid grid) {
        String key = "battle.targeting.localFiringSpread", previous = System.getProperty(key);
        try {
            System.setProperty(key, Boolean.toString(enabled));
            return new TacticalScoring(nav, roster, new AttackerIndexService(roster), null, new DoodadService(grid));
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }
}
