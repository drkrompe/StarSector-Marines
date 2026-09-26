package com.dillon.starsectormarines.battle.nav;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-only access to the package-local search seam; no battle is needed. */
public final class ControlledDefendRoutes {
    private ControlledDefendRoutes() { }

    public static AsyncDefendTrackRoutes blocked(CountDownLatch started,
                                                 CountDownLatch proceed) {
        return new AsyncDefendTrackRoutes(1, 1, (grid, occupancy, request) -> {
            started.countDown();
            try {
                if (!proceed.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("test never released route worker");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return GridPathfinder.EMPTY_PATH;
            }
            return new int[]{request.startX(), request.startY(),
                    request.goalX(), request.goalY()};
        });
    }
}
