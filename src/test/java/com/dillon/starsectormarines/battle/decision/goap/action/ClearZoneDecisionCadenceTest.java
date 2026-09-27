package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.AttackerIndexService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real action/scorer on two units and a tiny grid; no battle loop or generation. */
class ClearZoneDecisionCadenceTest {
    @Test void stableTargetSkipsDecisionScansButChecksAndAuthorsFireEveryTick() {
        try (Fixture f = new Fixture(true)) {
            assertEquals(ActionStatus.RUNNING, f.execute());
            int visits = f.visits;
            int shotChecks = f.grid.shotChecks;
            f.roster.combat().clearPrimaryFire(f.self);
            f.tick++;
            assertEquals(ActionStatus.RUNNING, f.execute());
            assertEquals(visits, f.visits, "retained live hostile proves NOT-clear without another roster scan");
            assertEquals(1, f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_DECISION_REUSE));
            assertTrue(f.grid.shotChecks > shotChecks);
            assertEquals(f.enemy, f.roster.combat().fireTargetId(f.self));
        }
    }

    @Test void controlStillPerformsTheZoneScanOnEveryTick() {
        try (Fixture f = new Fixture(false)) {
            f.execute();
            int visits = f.visits;
            f.tick++;
            f.execute();
            assertTrue(f.visits > visits);
            assertEquals(0, f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_DECISION_REUSE));
        }
    }

    @Test void failedSearchBackoffAvoidsSelectionAndNeverResumesAnOldPath() {
        try (Fixture f = new Fixture(true)) {
            f.grid.shots = false;
            f.grid.sight = false;
            f.execute();
            assertEquals(0, f.roster.combat().targetId(f.self));
            int selections = f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_TARGET_SELECT);
            int searches = f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION);
            assertTrue(searches > 0);
            for (int i = 0; i < 10; i++) {
                f.tick++;
                assertEquals(ActionStatus.RUNNING, f.execute());
            }
            assertEquals(selections, f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_TARGET_SELECT));
            assertEquals(searches, f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION));
            assertEquals(10, f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_NEGATIVE_REUSE));
            assertEquals(0, f.advances);
            f.tick += 21;
            f.execute();
            assertTrue(f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION) > searches);
        }
    }

    @Test void newlyLegalShotInterruptsNegativeBackoffImmediately() {
        try (Fixture f = new Fixture(true)) {
            f.grid.shots = false;
            f.grid.sight = false;
            f.execute();
            int searches = f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION);
            f.grid.shots = true;
            f.tick++;
            f.execute();
            assertEquals(f.enemy, f.roster.combat().targetId(f.self));
            assertEquals(f.enemy, f.roster.combat().fireTargetId(f.self));
            assertEquals(searches, f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION));
        }
    }

    @Test void externalReplacementTargetIsNeverOverwrittenByNegativeIdentity() {
        try (Fixture f = new Fixture(true)) {
            f.grid.shots = false;
            f.grid.sight = false;
            f.execute();
            long replacement = f.enemy(5, 2);
            f.roster.world().setTargetId(f.self, replacement);
            f.grid.shots = true;
            f.grid.sight = true;
            f.tick++;
            f.execute();
            assertEquals(replacement, f.roster.combat().targetId(f.self));
            assertEquals(replacement, f.roster.combat().fireTargetId(f.self));
            assertEquals(0, f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_NEGATIVE_REUSE));
        }
    }

    @Test void topologyChangeImmediatelyRetriesAFailedSearch() {
        try (Fixture f = new Fixture(true)) {
            f.grid.shots = false;
            f.grid.sight = false;
            f.execute();
            int searches = f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION);
            f.grid.setWalkable(15, 5, false);
            f.tick++;
            f.execute();
            assertTrue(f.profile.countOf(TickInnerProfile.Bucket.FIRING_POSITION) > searches);
            assertEquals(0, f.profile.countOf(TickInnerProfile.Bucket.CLEAR_ZONE_NEGATIVE_REUSE));
        }
    }

    @Test void releasedTargetCannotKeepTheRoomOccupiedEvenIfItsHealthRowSurvives() {
        for (boolean negative : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(true)) {
                f.grid.shots = !negative;
                f.grid.sight = !negative;
                f.execute();
                f.roster.release(f.enemy);
                f.tick++;
                assertEquals(ActionStatus.SUCCESS, f.execute());
            }
        }
    }

    @Test void movedOutOfZoneTargetInvalidatesBeforeCadenceExpires() {
        try (Fixture f = new Fixture(true)) {
            f.execute();
            f.roster.world().setPos(f.enemy, 30.5f, 2.5f);
            f.tick++;
            assertEquals(ActionStatus.SUCCESS, f.execute());
        }
    }

    @Test void successIsNeverCachedAcrossANewEnemyArrival() {
        try (Fixture f = new Fixture(true)) {
            f.roster.release(f.enemy);
            assertEquals(ActionStatus.SUCCESS, f.execute());
            long arrival = f.enemy(6, 2);
            f.tick++;
            assertEquals(ActionStatus.RUNNING, f.execute());
            assertEquals(arrival, f.roster.combat().fireTargetId(f.self));
        }
    }

    @Test void centeredSettledMemberDoesNotBuildTrivialRoutesBetweenShots() {
        try (Fixture f = new Fixture(true)) {
            f.roster.combat().setCooldownTimer(f.self, 1f);
            f.execute();
            f.tick++;
            f.execute();
            assertEquals(f.enemy, f.roster.combat().targetId(f.self));
            assertEquals(0, f.profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(0, f.paths);
            assertEquals(0, f.advances);
        }
    }

    @Test void offCenterSettledMemberStillRoutesToTheClearFiringCellCenter() {
        try (Fixture f = new Fixture(true)) {
            f.grid.centerOnly = true;
            f.roster.world().setPos(f.self, 2.1f, 2.5f);
            f.execute();
            assertTrue(f.profile.countOf(TickInnerProfile.Bucket.PATHFIND) > 0);
            assertEquals(1, f.paths);
            assertEquals(1, f.advances);
        }
    }

    private static final class TestGrid extends NavigationGrid {
        boolean shots = true;
        boolean sight = true;
        boolean centerOnly;
        int shotChecks;

        TestGrid() {
            super(16, 6);
            for (int y = 0; y < 6; y++) for (int x = 0; x < 16; x++) setWalkableFloor(x, y);
        }

        @Override public boolean hasLineOfSight(int x0, int y0, int x1, int y1) { return sight; }

        @Override public boolean hasLineOfFire(float x0, float y0, float x1, float y1) {
            shotChecks++;
            return shots && (!centerOnly || x0 == Math.floor(x0) + 0.5);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TestGrid grid = new TestGrid();
        final NavigationService nav = new NavigationService(grid, new CellTopology(16, 6), false);
        final UnitRosterService roster = new UnitRosterService(nav.getUnitIndex(), null);
        final TickInnerProfile profile = new TickInnerProfile();
        final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();
        final Squad squad;
        final long self;
        final long enemy;
        final ClearZone action;
        final TacticalScoring scoring;
        final BattleControl sim;
        int tick = 100;
        int visits;
        int advances;
        int paths;

        Fixture(boolean cadence) {
            nav.setRoster(roster);
            roster.setNavigationGrid(grid);
            int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            squad = roster.getSquad(squadId);
            self = roster.spawn(new EntitySpec("self", Faction.MARINE, UnitType.MARINE, 2, 2).squad(squadId));
            enemy = enemy(6, 2);
            roster.world().setAttackRange(self, 10f);
            roster.combat().setCooldownTimer(self, 0f);
            scoring = new TacticalScoring(nav, roster, new AttackerIndexService(roster), null,
                    new DoodadService(grid));
            action = new ClearZone(nav.getZoneGraph().zoneIdAt(2, 2), cadence);
            sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> nav.getZoneGraph();
                        case "getOccupancyMap" -> nav.getOccupancyMap();
                        case "world" -> roster.world();
                        case "identity" -> roster.identity();
                        case "combat" -> roster.combat();
                        case "movement" -> roster.movement();
                        case "targetOf" -> roster.combat().targetId((long) args[0]);
                        case "getTacticalScoring" -> scoring;
                        case "getSimTickIndex" -> tick;
                        case "liveUnitIndexOf" -> roster.indexOf((long) args[0]);
                        case "liveUnitCount" -> roster.liveCount();
                        case "liveUnitAt" -> { visits++; yield roster.get((int) args[0]); }
                        case "advanceMovement" -> { advances++; yield null; }
                        case "setPath" -> { paths++; yield null; }
                        default -> throw new AssertionError("Unexpected battle dependency: " + method.getName());
                    });
            TickInnerProfile.setCurrent(profile);
        }

        long enemy(int x, int y) {
            long id = roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, x, y));
            nav.getUnitIndex().rebuild(roster);
            return id;
        }

        ActionStatus execute() { return action.execute(self, squad, sim); }

        @Override public void close() {
            TickInnerProfile.setCurrent(previousProfile);
            nav.close();
        }
    }
}
