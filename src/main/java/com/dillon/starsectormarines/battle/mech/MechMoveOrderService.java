package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.assault.MachineGuard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Battle-local mailbox and state owner for exact-mech move orders.
 *
 * <p>The UI queues the raw clicked cell. {@link MechMoveOrderSystem} validates
 * the selected entity and resolves that request to reachable ground during the
 * serial command phase. The active order is deliberately per chassis rather
 * than per squad: it temporarily owns only that mech's locomotion, leaving its
 * mission directive, squad plan, doctrine, target picture, and weapons intact.
 */
public final class MechMoveOrderService {

    /** One raw UI request awaiting command-phase validation. */
    static final class PendingOrder {
        final long mechId;
        final int cellX;
        final int cellY;
        /** Hip heading to hold on arrival, or {@code NaN} for none. */
        final float facingDegrees;
        /** A commander's guard order rather than a player's click: it may only become a guard. */
        final boolean commanded;

        PendingOrder(long mechId, int cellX, int cellY, float facingDegrees) {
            this(mechId, cellX, cellY, facingDegrees, false);
        }

        PendingOrder(long mechId, int cellX, int cellY, float facingDegrees, boolean commanded) {
            this.mechId = mechId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.facingDegrees = facingDegrees;
            this.commanded = commanded;
        }
    }

    /**
     * Accepted move intent. Requested coordinates retain what the player
     * clicked; destination coordinates name the nearest reachable cell the
     * mech will actually occupy. {@code facingDegrees} is the hip heading the
     * player dragged out for it to stand on once there, or {@code NaN}.
     *
     * <p>With an {@code assault}, the click was on a garrisonable the side believes is
     * enemy-held: the destination is the assault's stand-off, and the order does not end on
     * arrival but holds the mech there, firing on the place, until the assault is over
     * ({@code MachineAssaults}).
     *
     * <p>With a {@code guard}, the click was on a garrisonable the side mans: the destination is a cell beside it,
     * and the order holds the mech there, facing the believed threat, until the garrison has gone
     * ({@code MachineGuards}). {@code commanded} marks a guard a commander gave a lance of its own side rather
     * than one a player clicked.
     */
    public record ActiveOrder(int requestedX, int requestedY,
                              int destinationX, int destinationY, float facingDegrees,
                              MachineAssault assault, MachineGuard guard, boolean commanded) {

        /** A move, or an assault when {@code assault} is given. */
        public ActiveOrder(int requestedX, int requestedY, int destinationX, int destinationY,
                           float facingDegrees, MachineAssault assault) {
            this(requestedX, requestedY, destinationX, destinationY, facingDegrees, assault, null, false);
        }

        /** A plain move with no dragged facing. */
        public ActiveOrder(int requestedX, int requestedY, int destinationX, int destinationY) {
            this(requestedX, requestedY, destinationX, destinationY, Float.NaN, null);
        }

        /** A plain move standing on {@code facingDegrees} once there. */
        public ActiveOrder(int requestedX, int requestedY, int destinationX, int destinationY,
                           float facingDegrees) {
            this(requestedX, requestedY, destinationX, destinationY, facingDegrees, null);
        }
    }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Long, ActiveOrder> active = new ConcurrentHashMap<>();
    private final Map<Long, Float> heldFacing = new ConcurrentHashMap<>();
    /** Chassis whose order the player told to run ({@link #toggleRun}). */
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    /** Queues a one-shot move request for validation on the next fixed tick. */
    public void requestMove(long mechId, int cellX, int cellY) {
        requestMove(mechId, cellX, cellY, Float.NaN);
    }

    /**
     * Queues a move whose mech then stands with its hips on {@code facingDegrees}
     * (the sprite convention: 0 = +Y, positive counterclockwise), or on its own
     * judgement when that is {@code NaN}.
     */
    public void requestMove(long mechId, int cellX, int cellY, float facingDegrees) {
        running.remove(mechId);
        pending.add(new PendingOrder(mechId, cellX, cellY, facingDegrees));
    }

    /**
     * Queues a commander's order for one chassis of a lance of its own side to guard the garrisonable at the cell
     * ({@code MachineGuards}). It becomes a guard or nothing: a place the side no longer mans is not a move.
     */
    public void requestGuard(long mechId, int cellX, int cellY) {
        pending.add(new PendingOrder(mechId, cellX, cellY, Float.NaN, true));
    }

    /** Ends a commander's guard on this chassis, if it stands under one; a player's order is left alone. */
    public void standDownGuard(long mechId) {
        pending.removeIf(order -> order.mechId == mechId && order.commanded);
        ActiveOrder order = active.get(mechId);
        if (order != null && order.guard() != null && order.commanded()) {
            active.remove(mechId, order);
            heldFacing.remove(mechId);
            running.remove(mechId);
        }
    }

    /**
     * Toggles the chassis's order between walking and running, and says whether it now runs. As a
     * squad's run ({@code SquadMoveOrderService#toggleRun}), it is a pace on an order already under
     * way: a mech with none stays walking, a fresh order starts at a walk, and the run ends with the
     * order it was given to.
     */
    public boolean toggleRun(long mechId) {
        if (!active.containsKey(mechId) || running.remove(mechId)) return false;
        running.add(mechId);
        return true;
    }

    /** Whether the chassis's order is run rather than walked. */
    public boolean isRunning(long mechId) {
        return running.contains(mechId) && active.containsKey(mechId);
    }

    /**
     * The hip heading an arrived order left this mech holding, or {@code NaN}.
     * It lasts while the mech stands: the next route it walks, a new order, or a
     * cancel ends it. The upper chassis keeps its own aim within its twist.
     */
    public float heldFacing(long mechId) {
        Float held = heldFacing.get(mechId);
        return held == null ? Float.NaN : held;
    }

    void holdFacing(long mechId, float facingDegrees) {
        if (Float.isNaN(facingDegrees)) heldFacing.remove(mechId);
        else heldFacing.put(mechId, facingDegrees);
    }

    void releaseHeldFacing(long mechId) {
        heldFacing.remove(mechId);
    }

    /** Current accepted order for one exact mech, or {@code null}. */
    public ActiveOrder activeOrder(long mechId) {
        return active.get(mechId);
    }

    /** Cancels both accepted and queued intent for a chassis during serial ownership transfer. */
    public void cancel(long mechId) {
        pending.removeIf(order -> order.mechId == mechId);
        active.remove(mechId);
        heldFacing.remove(mechId);
        running.remove(mechId);
    }

    List<PendingOrder> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOrder> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    void activate(long mechId, ActiveOrder order) {
        active.put(mechId, order);
        heldFacing.remove(mechId);
    }

    void release(long mechId, ActiveOrder order) {
        if (active.remove(mechId, order)) running.remove(mechId);
    }

    void release(long mechId) {
        active.remove(mechId);
        running.remove(mechId);
    }

    Iterable<Map.Entry<Long, ActiveOrder>> activeEntries() {
        return active.entrySet();
    }

    /** Whether any order is accepted, so a per-tick pass with nothing to serve allocates nothing. */
    public boolean hasActiveOrders() {
        return !active.isEmpty();
    }

    /** Accepted orders, snapshotted, for the serial passes that serve them (wall fire, highlights). */
    public List<Map.Entry<Long, ActiveOrder>> activeOrders() {
        return new ArrayList<>(active.entrySet());
    }
}
