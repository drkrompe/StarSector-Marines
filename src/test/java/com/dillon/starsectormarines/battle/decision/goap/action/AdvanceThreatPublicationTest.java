package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.AttackerIndexService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises production scoring/publication directly, without a battle simulation. */
class AdvanceThreatPublicationTest {
    @Test
    void parallelMembersPublishOneCompleteDecisionPerTick() throws Exception {
        for (boolean isolated : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                ExecutorService workers = Executors.newFixedThreadPool(2);
                CountDownLatch computing = new CountDownLatch(1);
                CountDownLatch finish = new CountDownLatch(1);
                CountDownLatch contenderEntered = new CountDownLatch(1);
                f.beforeScoring = () -> {
                    assertTrue(Thread.holdsLock(isolated ? f.squad.advanceThreatLock : f.squad.lock));
                    computing.countDown();
                    await(finish);
                };
                try {
                    Future<?> owner = workers.submit(() -> f.update(isolated));
                    assertTrue(computing.await(2, TimeUnit.SECONDS));
                    Future<?> contender = workers.submit(() -> {
                        contenderEntered.countDown();
                        f.update(isolated);
                        f.assertCompleteDecision();
                    });
                    assertTrue(contenderEntered.await(2, TimeUnit.SECONDS));
                    assertEquals(-1, f.squad.advanceThreatTick, "in-progress scoring is not published");
                    assertFalse(contender.isDone(), "a sibling cannot consume a partial decision");
                    finish.countDown();
                    owner.get(2, TimeUnit.SECONDS);
                    contender.get(2, TimeUnit.SECONDS);
                    assertEquals(1, f.computations.get());
                    f.assertCompleteDecision();
                    f.update(isolated);
                    assertEquals(1, f.computations.get(), "same-tick fast path reuses the publication");
                    f.tick++;
                    f.update(isolated);
                    assertEquals(2, f.computations.get(), "next tick refreshes immediately");
                    f.assertCompleteDecision();
                } finally {
                    finish.countDown();
                    workers.shutdownNow();
                    assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    void isolatedDecisionCompletesWhileAnotherThreadOwnsTheBroadSquadLock() throws Exception {
        try (Fixture f = new Fixture()) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            f.beforeScoring = () -> {
                assertFalse(Thread.holdsLock(f.squad.lock));
                assertTrue(Thread.holdsLock(f.squad.advanceThreatLock));
            };
            try {
                synchronized (f.squad.lock) {
                    worker.submit(() -> f.update(true)).get(2, TimeUnit.SECONDS);
                    f.assertCompleteDecision();
                }
            } finally {
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void controlKeepsOriginalMonitorAndHostResetCanRefreshTheSameTick() {
        try (Fixture f = new Fixture()) {
            f.beforeScoring = () -> {
                assertTrue(Thread.holdsLock(f.squad.lock));
                assertFalse(Thread.holdsLock(f.squad.advanceThreatLock));
            };
            f.update(false);
            f.squad.advanceThreatTick = -1; // Host control-entry boundary, after workers joined.
            f.update(false);
            assertEquals(2, f.computations.get());
            f.assertCompleteDecision();
        }
    }

    @Test
    void failedScorerAccessDoesNotMarkThisTickComplete() {
        try (Fixture f = new Fixture()) {
            f.beforeScoring = () -> { throw new IllegalStateException("test failure"); };
            assertThrows(IllegalStateException.class, () -> f.update(true));
            assertEquals(-1, f.squad.advanceThreatTick);
            f.beforeScoring = () -> {};
            f.update(true);
            f.assertCompleteDecision();
        }
    }

    @Test
    void configuredProductionEntryDelegatesToTheSameScoringAndPublicationPath() {
        try (Fixture f = new Fixture()) {
            AbstractZoneAction.updateAdvanceThreat(f.squad, f.sim, 25, 3);
            f.assertCompleteDecision();
            assertEquals(1, f.computations.get());
            AbstractZoneAction.updateAdvanceThreat(f.squad, f.sim, 25, 3);
            assertEquals(1, f.computations.get());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NavigationService nav;
        final UnitRosterService roster;
        final TacticalScoring scoring;
        final Squad squad;
        final long enemy;
        final BattleControl sim;
        final AtomicInteger computations = new AtomicInteger();
        int tick = 10;
        Runnable beforeScoring = () -> {};

        Fixture() {
            NavigationGrid grid = new NavigationGrid(32, 8);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
            nav = new NavigationService(grid, new CellTopology(32, 8), false);
            roster = new UnitRosterService(nav.getUnitIndex(), null);
            nav.setRoster(roster);
            roster.setNavigationGrid(grid);
            int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            squad = roster.getSquad(squadId);
            roster.spawn(new EntitySpec("friend", Faction.MARINE, UnitType.MARINE, 5, 3).squad(squadId));
            enemy = roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 10, 3));
            squad.centroidX = 5.5f;
            squad.centroidY = 3.5f;
            SquadBeliefTestAccess.observeDirect(squad, enemy, 10, 3, tick);
            nav.getUnitIndex().rebuild(roster);
            scoring = new TacticalScoring(nav, roster, new AttackerIndexService(roster), null, null);
            sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getSimTickIndex" -> tick;
                        case "getTacticalScoring" -> {
                            computations.incrementAndGet();
                            beforeScoring.run();
                            yield scoring;
                        }
                        default -> throw new AssertionError("Unexpected battle dependency: " + method.getName());
                    });
        }

        void update(boolean isolated) {
            AbstractZoneAction.updateAdvanceThreat(squad, sim, 25, 3, isolated);
        }

        void assertCompleteDecision() {
            assertEquals(tick, squad.advanceThreatTick);
            assertEquals(enemy, squad.advanceThreatId);
            assertEquals(1f, squad.advanceEngageWeight);
            assertTrue(squad.advanceEngageCommitted);
            assertEquals(AbstractZoneAction.ADVANCE_LEASH_MAX, squad.advanceEngageLeash);
            assertEquals(1, squad.advanceThreatFoes);
            assertEquals(1, squad.advanceThreatFriends);
            assertEquals(10, squad.advanceThreatAnchorX);
            assertEquals(3, squad.advanceThreatAnchorY);
            assertFalse(squad.advanceThreatRetreating);
        }

        @Override public void close() { nav.close(); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
