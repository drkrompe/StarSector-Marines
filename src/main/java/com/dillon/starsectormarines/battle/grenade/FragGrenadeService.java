package com.dillon.starsectormarines.battle.grenade;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Squad-coordination reservations for committed fragmentation-grenade throws. */
public final class FragGrenadeService {

    private final Map<Long, Reservation> byCarrier = new LinkedHashMap<>();

    /**
     * Reserves a landing footprint. Friendly overlapping reservations are rejected,
     * while another carrier may reserve a genuinely separate cluster.
     */
    public synchronized boolean tryReserve(long carrierId, int squadId, Faction faction,
                                           float targetX, float targetY, float blastRadius) {
        if (carrierId == 0L || faction == null || blastRadius <= 0f) return false;
        Reservation own = byCarrier.get(carrierId);
        if (own != null) return sameFootprint(own, targetX, targetY, blastRadius);
        for (Reservation other : byCarrier.values()) {
            if (other.faction != faction) continue;
            if (overlaps(other.targetX, other.targetY, other.blastRadius,
                    targetX, targetY, blastRadius)) return false;
        }
        byCarrier.put(carrierId, new Reservation(carrierId, squadId, faction,
                targetX, targetY, blastRadius));
        return true;
    }

    public synchronized Reservation reservationFor(long carrierId) {
        return byCarrier.get(carrierId);
    }

    public synchronized void release(long carrierId) {
        byCarrier.remove(carrierId);
    }

    public synchronized List<Reservation> snapshot() {
        return List.copyOf(new ArrayList<>(byCarrier.values()));
    }

    public synchronized void removeStale(ReservationValidity validity) {
        byCarrier.values().removeIf(reservation -> !validity.isValid(reservation));
    }

    private static boolean sameFootprint(Reservation reservation, float x, float y,
                                         float radius) {
        return Math.abs(reservation.targetX - x) < 0.01f
                && Math.abs(reservation.targetY - y) < 0.01f
                && Math.abs(reservation.blastRadius - radius) < 0.01f;
    }

    private static boolean overlaps(float ax, float ay, float ar,
                                    float bx, float by, float br) {
        float dx = bx - ax;
        float dy = by - ay;
        float reach = ar + br;
        return dx * dx + dy * dy <= reach * reach;
    }

    @FunctionalInterface
    public interface ReservationValidity {
        boolean isValid(Reservation reservation);
    }

    public record Reservation(long carrierId, int squadId, Faction faction,
                              float targetX, float targetY, float blastRadius) {
    }
}
