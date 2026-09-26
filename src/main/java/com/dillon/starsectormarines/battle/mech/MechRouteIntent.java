package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.ContinuousRoute;
import com.dillon.starsectormarines.battle.infantry.ApproachBound;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.Objects;

/** Per-mech tactical candidate ownership while a clearance proof runs asynchronously. */
public final class MechRouteIntent {
    private static final int MAX_FAILED_CANDIDATES = 32;
    private final long[] failures = new long[MAX_FAILED_CANDIDATES];
    private int failureCount;
    private Object purpose;
    private long context, revision;
    private long candidateBasis = Long.MIN_VALUE;
    private boolean pending;
    private boolean pendingBoundDetour;
    private int pendingX, pendingY;

    public static MechRouteIntent forMember(long member, Object purpose, long context, BattleView sim) {
        MechRouteIntent intent = sim.world().mechLoadout(member).routeIntent;
        intent.begin(purpose, context, sim.getGrid().topologyRevision());
        return intent;
    }

    void begin(Object nextPurpose, long nextContext, long nextRevision) {
        if (!Objects.equals(purpose, nextPurpose) || context != nextContext
                || revision != nextRevision) {
            purpose = nextPurpose;
            context = nextContext;
            revision = nextRevision;
            failureCount = 0;
            candidateBasis = Long.MIN_VALUE;
            pending = false;
        }
    }

    public boolean rejected(int x, int y) {
        if (failureCount == failures.length) return true;
        long cell = cellKey(x, y);
        for (int i = 0; i < failureCount; i++) if (failures[i] == cell) return true;
        return false;
    }

    public boolean pending() { return pending; }

    public void cancel() { pending = false; }

    /** Discards tactical navigation ownership so handback starts from the current body position. */
    public void reset() {
        purpose = null;
        context = 0L;
        revision = 0L;
        candidateBasis = Long.MIN_VALUE;
        failureCount = 0;
        pending = false;
        pendingBoundDetour = false;
        pendingX = 0;
        pendingY = 0;
    }

    /** A moved tactical anchor may make old refusals irrelevant, after its retained proof finishes. */
    public void refreshCandidates(long basis) {
        if (!pending && candidateBasis != basis) {
            failureCount = 0;
            candidateBasis = basis;
        }
    }

    /** Refuse a settled offset endpoint when the scored center's firing lane did not survive resolution. */
    public boolean rejectSettledPerch(long member, float targetX, float targetY,
                                       float minimumRange, float maximumRange, BattleControl sim) {
        ContinuousRoute route = sim.movement().continuousRoute(member);
        if (pending || route == null || !route.completed()
                || rejected(route.requestedCellX(), route.requestedCellY())
                || !sim.movement().atCell(member, route.requestedCellX(), route.requestedCellY())) return false;
        float centerX = route.requestedCellX() + 0.5f;
        float centerY = route.requestedCellY() + 0.5f;
        double scoredDistance = Math.hypot(centerX - targetX, centerY - targetY);
        if (scoredDistance < minimumRange || scoredDistance > maximumRange
                || !sim.getGrid().hasLineOfFire(centerX, centerY, targetX, targetY)) return false;
        float actualX = sim.world().x(member), actualY = sim.world().y(member);
        double actualDistance = Math.hypot(actualX - targetX, actualY - targetY);
        if (actualDistance >= minimumRange && actualDistance <= maximumRange
                && sim.getGrid().hasLineOfFire(actualX, actualY, targetX, targetY)) return false;
        record(route.requestedCellX(), route.requestedCellY(), PathRequestStatus.FAILED);
        sim.clearPath(member);
        return true;
    }

    /** Cheap local footprint filter; connectivity still requires the asynchronous proof. */
    public static boolean candidate(long member, int x, int y, BattleView sim) {
        if (!sim.getGrid().isWalkable(x, y)
                || sim.world().mechLoadout(member).routeIntent.rejected(x, y)) return false;
        float radius = sim.physicalRadius(member);
        for (int yy = 0; yy <= 2; yy++) {
            for (int xx = 0; xx <= 2; xx++) {
                if (ManualTerrainMotion.canStand(sim.getGrid(), x + xx * 0.5f,
                        y + yy * 0.5f, radius)) return true;
            }
        }
        return false;
    }

    /** Returns true for the tick that polls a retained candidate, even when that proof just failed. */
    public boolean resume(long member, BattleControl sim) {
        if (!pending) return false;
        moveToward(member, pendingX, pendingY, sim);
        return true;
    }

    public PathRequestStatus moveToward(long member, int x, int y, BattleControl sim) {
        return moveToward(member, x, y, sim, false);
    }

    public PathRequestStatus moveToward(long member, int x, int y, BattleControl sim, boolean boundDetour) {
        if (pending) {
            x = pendingX;
            y = pendingY;
            boundDetour = pendingBoundDetour;
        }
        if (rejected(x, y)) return PathRequestStatus.FAILED;
        if (sim.movement().atCell(member, x, y)) {
            pending = false;
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return PathRequestStatus.READY;
        }
        if (!pending && sim.movement().pathTargetsCell(member, x, y)
                && sim.world().pathIdx(member) < Paths.cellCount(sim.world().path(member))) {
            sim.advanceMovement(member);
            return PathRequestStatus.READY;
        }
        if (!pending && !sim.movement().mayRepath(member)) return PathRequestStatus.PENDING;
        PathRequestStatus status = sim.requestPath(member, x, y);
        if (status == PathRequestStatus.READY && boundDetour
                && !worthWalking(sim.movement().continuousRoute(member))) {
            sim.clearPath(member);
            status = PathRequestStatus.FAILED;
        }
        record(x, y, status);
        pendingBoundDetour = boundDetour;
        if (status == PathRequestStatus.READY) sim.advanceMovement(member);
        return status;
    }

    static boolean worthWalking(ContinuousRoute route) {
        if (route == null) return false;
        double distance = 0d;
        for (int i = 1; i < route.pointCount(); i++) {
            distance += Math.hypot(route.x(i) - route.x(i - 1), route.y(i) - route.y(i - 1));
        }
        double straight = Math.hypot(route.endX() - route.x(0), route.endY() - route.y(0));
        return distance <= ApproachBound.DETOUR_SLACK + ApproachBound.DETOUR_RATIO * straight;
    }

    void record(int x, int y, PathRequestStatus status) {
        pending = status == PathRequestStatus.PENDING;
        pendingX = x;
        pendingY = y;
        if (status == PathRequestStatus.FAILED && !rejected(x, y)) {
            failures[failureCount++] = cellKey(x, y);
        }
    }

    public static long cellKey(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
}
