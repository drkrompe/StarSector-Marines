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
    @Test void bothSearchesGatherAlliesOnlyOncePerSearch() {
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
            TacticalScoring scoring = new TacticalScoring(nav, roster, new AttackerIndexService(roster),
                    null, new DoodadService(grid));
            TickInnerProfile.setCurrent(profile);
            for (boolean within : new boolean[]{false, true}) {
                profile.reset();
                assertNotNull(pick(scoring, within, self, target));
                assertTrue(profile.countOf(TickInnerProfile.Bucket.FIRING_SPREAD_CANDIDATE) > 1);
                assertEquals(1, profile.countOf(TickInnerProfile.Bucket.FIRING_SPREAD_QUERY));
            }
        } finally {
            TickInnerProfile.setCurrent(previous);
        }
    }

    private static int[] pick(TacticalScoring scoring, boolean within, long self, long target) {
        return within ? scoring.findFiringPositionWithin(self, target, 16, 10, 12, -1)
                : scoring.findFiringPosition(self, target, -1, -1);
    }

}
