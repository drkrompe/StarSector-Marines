package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Direct phase ownership/physics tests without a battle, renderer, or map. */
class ShotServiceProjectilePhaseTest {
    @Test void sharedHazardViewExcludesFreshLaunchesButReservationsIncludeThem() {
        ShotService shots = new ShotService(null, true);
        Projectile existing = projectile(1, 2f);
        Projectile firstLaunch = projectile(2, 1f);
        Projectile secondLaunch = projectile(3, 1f);
        shots.queueProjectile(existing);

        assertTrue(shots.beginMemberUpdates());
        List<Projectile> hazards = shots.snapshotActiveProjectiles();
        assertSame(hazards, shots.snapshotActiveProjectiles());
        assertThrows(UnsupportedOperationException.class, () -> hazards.add(firstLaunch));
        Iterable<Projectile> reservations = shots.committedProjectiles();
        shots.queueProjectile(firstLaunch);
        shots.queueProjectile(secondLaunch);

        assertEquals(List.of(existing), hazards);
        assertEquals(List.of(existing), shots.getActiveProjectiles());
        assertEquals(List.of(existing, firstLaunch, secondLaunch), collect(reservations));
        assertEquals(1f, firstLaunch.remainingTime, "publication does not age the physical round");
        shots.finishMemberUpdates();
        assertEquals(List.of(existing, firstLaunch, secondLaunch), shots.getActiveProjectiles());

        assertTrue(shots.beginMemberUpdates());
        assertEquals(List.of(existing, firstLaunch, secondLaunch), shots.snapshotActiveProjectiles());
        assertNotSame(hazards, shots.snapshotActiveProjectiles());
        shots.finishMemberUpdates();
    }

    @Test void disabledControlRetainsImmediateVisibilityAndIndependentCopies() {
        ShotService shots = new ShotService(null, false);
        assertFalse(shots.beginMemberUpdates());
        List<Projectile> before = shots.snapshotActiveProjectiles();
        Projectile launch = projectile(1, 1f);
        shots.queueProjectile(launch);
        assertTrue(before.isEmpty());
        assertEquals(List.of(launch), shots.getActiveProjectiles());
        assertEquals(List.of(launch), shots.snapshotActiveProjectiles());
        assertNotSame(shots.snapshotActiveProjectiles(), shots.snapshotActiveProjectiles());
        assertEquals(List.of(launch), collect(shots.committedProjectiles()));
    }

    @Test void successfulJoinPreservesLaunchTickAgingInterceptionAndLaterSerialFire() {
        ShotService shots = new ShotService(null, true);
        Projectile arriving = projectile(1, 0.1f);
        Projectile intercepted = projectile(2, 1f);
        shots.beginMemberUpdates();
        shots.queueProjectile(arriving);
        shots.queueProjectile(intercepted);
        assertThrows(IllegalStateException.class, () -> shots.tickProjectiles(0.1f, det -> fail()));
        shots.finishMemberUpdates();

        // Point defence still sees member launches before this tick's aging.
        assertTrue(shots.getActiveProjectiles().contains(intercepted));
        intercepted.intercepted = true;
        Projectile serialLaunch = projectile(3, 0.5f);
        shots.queueProjectile(serialLaunch);
        List<PendingDetonation> arrivals = new ArrayList<>();
        shots.tickProjectiles(0.1f, arrivals::add);
        assertEquals(List.of(arriving.onArrival), arrivals);
        assertEquals(List.of(arriving), shots.getProjectilesArrivedThisFrame());
        assertEquals(List.of(intercepted), shots.getProjectilesInterceptedThisFrame());
        assertEquals(List.of(serialLaunch), shots.getActiveProjectiles());
        assertEquals(0.4f, serialLaunch.remainingTime, 0.00001f);
    }

    @Test void concurrentAppendIntentsPublishExactlyOnceAfterJoin() throws Exception {
        ShotService shots = new ShotService(null, true);
        shots.beginMemberUpdates();
        List<Projectile> hazards = shots.snapshotActiveProjectiles();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> joins = new ArrayList<>();
            for (int writer = 0; writer < 4; writer++) {
                int writerId = writer;
                joins.add(workers.submit(() -> {
                    for (int i = 0; i < 64; i++) {
                        shots.queueProjectile(projectile(writerId * 64 + i, 1f));
                        assertSame(hazards, shots.snapshotActiveProjectiles());
                        for (Projectile launch : shots.committedProjectiles()) {
                            assertNotNull(launch.onArrival);
                            assertEquals(1f, launch.remainingTime);
                        }
                    }
                }));
            }
            for (Future<?> join : joins) join.get(5, TimeUnit.SECONDS);
            assertTrue(shots.getActiveProjectiles().isEmpty());
            assertTrue(hazards.isEmpty());
            assertEquals(256, collect(shots.committedProjectiles()).size());
            shots.finishMemberUpdates();
            assertEquals(256, shots.getActiveProjectiles().size());
            assertEquals(256, new HashSet<>(shots.getActiveProjectiles()).size());
            assertThrows(IllegalStateException.class, shots::finishMemberUpdates);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test void abandonedPhaseNeverPublishesPartialOrLateWorkerIntents() throws Exception {
        ShotService shots = new ShotService(null, true);
        Projectile existing = projectile(1, 2f);
        shots.queueProjectile(existing);
        shots.beginMemberUpdates();
        shots.queueProjectile(projectile(2, 1f));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch continueAfterFailure = new CountDownLatch(1);
        try {
            Future<?> lateWriter = worker.submit(() -> {
                entered.countDown();
                try {
                    assertTrue(continueAfterFailure.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                shots.queueProjectile(projectile(3, 1f));
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            shots.abandonMemberUpdates();
            continueAfterFailure.countDown();
            lateWriter.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(existing), shots.getActiveProjectiles());
            assertEquals(List.of(existing), shots.snapshotActiveProjectiles());
            assertThrows(IllegalStateException.class, shots::finishMemberUpdates);
            assertThrows(IllegalStateException.class, shots::beginMemberUpdates);
            assertThrows(IllegalStateException.class, () -> shots.tickProjectiles(1f, det -> fail()));

            // The terminal failure belongs to this battle, not another owner.
            ShotService otherBattle = new ShotService(null, true);
            Projectile independent = projectile(4, 1f);
            otherBattle.queueProjectile(independent);
            assertTrue(otherBattle.beginMemberUpdates());
            assertEquals(List.of(independent), otherBattle.snapshotActiveProjectiles());
            otherBattle.finishMemberUpdates();
        } finally {
            continueAfterFailure.countDown();
            worker.shutdownNow();
        }
    }

    @Test void workerCannotPublishOrAbandonTheHostPhase() throws Exception {
        ShotService shots = new ShotService(null, true);
        shots.beginMemberUpdates();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                shots.queueProjectile(projectile(1, 1f));
                assertThrows(IllegalStateException.class, shots::finishMemberUpdates);
                assertThrows(IllegalStateException.class, shots::abandonMemberUpdates);
            }).get(5, TimeUnit.SECONDS);
            assertTrue(shots.getActiveProjectiles().isEmpty());
            shots.finishMemberUpdates();
            assertEquals(1, shots.getActiveProjectiles().size());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test void memberPublicationAndReadsDoNotAcquireTheActiveListMonitor() throws Exception {
        ShotService shots = new ShotService(null, true);
        Projectile existing = projectile(1, 2f);
        Projectile launch = projectile(2, 1f);
        shots.queueProjectile(existing);
        shots.beginMemberUpdates();
        List<Projectile> hazards = shots.snapshotActiveProjectiles();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            // Hold the exact monitor used by the legacy append/copy path.
            // The worker must finish all three operations without acquiring it.
            synchronized (shots.getActiveProjectiles()) {
                worker.submit(() -> {
                    shots.queueProjectile(launch);
                    assertSame(hazards, shots.snapshotActiveProjectiles());
                    assertEquals(List.of(existing), hazards);
                    assertEquals(List.of(existing, launch), collect(shots.committedProjectiles()));
                }).get(5, TimeUnit.SECONDS);
            }
            assertEquals(List.of(existing), shots.getActiveProjectiles());
            shots.finishMemberUpdates();
            assertEquals(List.of(existing, launch), shots.getActiveProjectiles());
        } finally {
            worker.shutdownNow();
        }
    }

    private static List<Projectile> collect(Iterable<Projectile> projectiles) {
        List<Projectile> result = new ArrayList<>();
        projectiles.forEach(result::add);
        return result;
    }

    private static Projectile projectile(int id, float flightTime) {
        PendingDetonation payload = new PendingDetonation(id, 10, 20, flightTime,
                2f, 10f, 1f, 0, Faction.MARINE, false);
        return new Projectile(0, 0, 10, 20, false, 0,
                Faction.MARINE, false, flightTime, payload);
    }
}
