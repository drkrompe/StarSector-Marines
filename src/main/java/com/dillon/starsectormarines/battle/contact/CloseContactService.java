package com.dillon.starsectormarines.battle.contact;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Owns the two pieces of state a close-contact activation needs: which contact
 * each committed carrier has reserved, and which map cells were authored as
 * breach points.
 *
 * <p>Reservations exist so several carriers standing on the same casualty do
 * not each spend a payload on it. A reservation is a claim, not a payload: an
 * interrupted commitment releases it at no cost, and a carrier holds at most
 * one at a time.
 *
 * <p>A <b>breach point</b> is an authored wall or door cell a breaching tool
 * may cut. Nothing derives one from the map: a breacher considers only cells
 * registered here, which is what keeps the tool from chewing arbitrary
 * obstacles. Consuming a breach point retires it, so the same authored opening
 * is not cut twice.
 */
public final class CloseContactService {

    /** Reservation key for one authored breach point; disjoint from entity ids, which are positive. */
    public static long breachPointKey(int cellX, int cellY) {
        return -(((long) cellY << 20) | (cellX & 0xFFFFF)) - 1L;
    }

    private final Map<Long, Long> carrierByReservedContact = new LinkedHashMap<>();
    private final Set<Long> breachPoints = new LinkedHashSet<>();

    /** Authors one wall/door cell as a legal breaching target. */
    public synchronized void registerBreachPoint(int cellX, int cellY) {
        breachPoints.add(breachPointKey(cellX, cellY));
    }

    public synchronized boolean isBreachPoint(int cellX, int cellY) {
        return breachPoints.contains(breachPointKey(cellX, cellY));
    }

    public synchronized int breachPointCount() {
        return breachPoints.size();
    }

    /** Retires an authored breach point once its opening has been cut. */
    public synchronized void consumeBreachPoint(int cellX, int cellY) {
        breachPoints.remove(breachPointKey(cellX, cellY));
    }

    /**
     * Atomically claims one contact for one carrier. A carrier may hold only
     * one claim, so committing to a new contact drops the previous claim.
     */
    public synchronized boolean tryReserve(long carrierId, long contactKey) {
        if (carrierId == 0L || contactKey == 0L) return false;
        Long holder = carrierByReservedContact.get(contactKey);
        if (holder != null && holder != carrierId) return false;
        carrierByReservedContact.values().removeIf(value -> value == carrierId);
        carrierByReservedContact.put(contactKey, carrierId);
        return true;
    }

    public synchronized void releaseReservation(long carrierId) {
        carrierByReservedContact.values().removeIf(value -> value == carrierId);
    }

    public synchronized boolean isReservedBy(long carrierId, long contactKey) {
        Long holder = carrierByReservedContact.get(contactKey);
        return holder != null && holder == carrierId;
    }

    public synchronized boolean isReserved(long contactKey) {
        return carrierByReservedContact.containsKey(contactKey);
    }

    /**
     * The contact key this carrier is committed to, or {@code 0L} when it holds
     * no claim. This — not the shared aim-target column — is what identifies a
     * committed contact, because a breach point is a map cell rather than an
     * entity and must never be handed to an entity resolver.
     */
    public synchronized long reservedContactOf(long carrierId) {
        for (Map.Entry<Long, Long> entry : carrierByReservedContact.entrySet()) {
            if (entry.getValue() == carrierId) return entry.getKey();
        }
        return 0L;
    }

    /**
     * Drops claims whose carrier or unit contact has left the battle, and
     * claims on breach points that are no longer authored (already cut). Death
     * therefore releases a commitment even when the dying carrier never runs
     * another tick.
     */
    public synchronized void tick(BattleControl sim) {
        NavigationGrid grid = sim.getGrid();
        carrierByReservedContact.entrySet().removeIf(entry -> {
            if (sim.resolveUnit(entry.getValue()) == 0L) return true;
            long contactKey = entry.getKey();
            if (contactKey > 0L) return sim.resolveUnit(contactKey) == 0L;
            if (!breachPoints.contains(contactKey)) return true;
            int cellX = breachCellX(contactKey);
            int cellY = breachCellY(contactKey);
            return grid.inBounds(cellX, cellY) && grid.isWalkable(cellX, cellY);
        });
    }

    /** Cell X of a breach-point reservation key. */
    public static int breachCellX(long contactKey) {
        return (int) ((-(contactKey + 1L)) & 0xFFFFF);
    }

    /** Cell Y of a breach-point reservation key. */
    public static int breachCellY(long contactKey) {
        return (int) ((-(contactKey + 1L)) >> 20);
    }
}
