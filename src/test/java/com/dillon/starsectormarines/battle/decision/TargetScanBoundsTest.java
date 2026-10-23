package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Direct scorer inputs; no battle simulation, tick, generation, or renderer. */
class TargetScanBoundsTest {
    @Test void boundKeepsBeliefAndMountRange() {
        assertEquals(24f, TacticalScoring.targetScanLimit(Float.POSITIVE_INFINITY, 24, 0));
        assertEquals(160f, TacticalScoring.targetScanLimit(Float.POSITIVE_INFINITY, 24, 160));
        assertEquals(18f, TacticalScoring.targetScanLimit(18, 24, 160));
        assertEquals(Float.POSITIVE_INFINITY,
                TacticalScoring.targetScanLimit(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 0));
    }

    @Test void emptySightDoesNotWalkToUnknownEnemiesAcrossTheMap() {
        try (Fixture f = new Fixture()) {
            f.enemy(180, 3);
            assertEquals(0L, f.pick(f.scoring));
            assertEquals(0, f.profile.countOf(TickInnerProfile.Bucket.TARGET_SCAN_VISIT),
                    "the scan stops at sight and never reaches the unknown enemy");
        }
    }

    @Test void distantBelievedMoverRemainsFallbackOutsideSightAndOldObservation() {
        try (Fixture f = new Fixture()) {
            long enemy = f.enemy(180, 3);
            SquadBeliefTestAccess.observeDirect(f.squad, enemy, 20, 3, 1);
            assertEquals(enemy, f.pick(f.scoring));
        }
    }

    @Test void inclusiveSightBoundaryAndDenseRosterTieRemainUnchanged() {
        try (Fixture f = new Fixture()) {
            long first = f.enemy(29, 3);
            f.enemy(29, 3);
            assertEquals(first, f.pick(f.scoring));
        }
    }

    @Test void snapshotDriftIntoSightIsNotPruned() {
        try (Fixture f = new Fixture()) {
            f.roster.world().setPos(f.self, 7.5f, 3.5f);
            long enemy = f.enemy(32, 3);
            // Snapshot is in the next bucket beyond sight; live position moved
            // into range. Without drift padding the ring would stop too soon.
            f.roster.world().setPos(enemy, 31.5f, 3.5f);
            assertEquals(enemy, f.pick(f.scoring));
        }
    }

    @Test void rememberedPassengerWithHealthButNoPositionDoesNotEnterTheBound() {
        try (Fixture f = new Fixture()) {
            long passenger = f.enemy(180, 3);
            SquadBeliefTestAccess.observeDirect(f.squad, passenger, 180, 3, 1);
            var components = f.roster.components();
            // The same component transition as VehicleTransportService.mount:
            // boarding preserves identity/health and removes grid position/movement.
            f.roster.entityWorld().transmute(passenger,
                    new ComponentType[]{components.RIDING},
                    new ComponentType[]{components.POSITION, components.MOVEMENT});
            f.nav.getUnitIndex().rebuild(f.roster);
            assertTrue(f.roster.isAliveById(passenger));
            assertTrue(f.roster.isRiding(passenger));
            assertFalse(f.roster.world().hasPosition(passenger));
            assertEquals(0L, f.pick(f.scoring),
                    "a remembered passenger is absent from spatial candidates and has no position to read");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NavigationService nav;
        final UnitRosterService roster;
        final TacticalScoring scoring;
        final Squad squad;
        final long self;
        final TickInnerProfile profile = new TickInnerProfile();
        final TickInnerProfile previous = TickInnerProfile.currentIfBound();

        Fixture() {
            NavigationGrid grid = new NavigationGrid(256, 8);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 256; x++) grid.setWalkableFloor(x, y);
            nav = new NavigationService(grid, new CellTopology(256, 8), false);
            roster = new UnitRosterService(nav.getUnitIndex(), null);
            nav.setRoster(roster);
            roster.setNavigationGrid(grid);
            int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            squad = roster.getSquad(squadId);
            self = roster.spawn(new EntitySpec("self", Faction.MARINE, UnitType.MARINE, 5, 3).squad(squadId));
            roster.vision().setVisionRange(self, 24);
            roster.world().setAttackRange(self, 24);
            scoring = new TacticalScoring(nav, roster, new AttackerIndexService(roster), null, null);
            TickInnerProfile.setCurrent(profile);
        }

        long enemy(int x, int y) {
            long id = roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, x, y));
            nav.getUnitIndex().rebuild(roster);
            return id;
        }

        long pick(TacticalScoring scoring) {
            profile.reset();
            return scoring.findBestTarget(roster.world().x(self), roster.world().y(self),
                    Faction.MARINE, squad.id, self);
        }

        @Override public void close() {
            TickInnerProfile.setCurrent(previous);
            nav.close();
        }
    }
}
