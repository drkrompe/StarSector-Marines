package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** One return trip per committed aircraft, with seats reserved across concurrent lifts. */
final class SurvivorPickupFleet {
    static final class Carrier {
        final ShuttleMission delivery;
        final ShuttleType type;
        final Airframe frame;
        final boolean mech;
        ShuttleMission pickup;
        int nextBoardTick;
        int landedTick = -1;

        Carrier(ShuttleMission delivery, ShuttleType type, Airframe frame, boolean mech) {
            this.delivery = delivery; this.type = type;
            this.frame = frame; this.mech = mech;
        }
        int capacity() { return mech ? 1 : type.capacity; }
        boolean boarding() {
            return pickup != null && pickup.pickup.state() == PickupManifest.State.ABOARD
                    && pickup.state == ShuttleState.LANDED && pickup.awaitingEvacuees && !pickup.pickup.full();
        }
        int openSeats() {
            return pickup != null && pickup.awaitingEvacuees
                    ? capacity() - pickup.pickup.passengers().size() : 0;
        }
        boolean readyToBoard(int tick) { return boarding() && tick >= nextBoardTick; }
        void boarded(int tick) { nextBoardTick = tick + 24; }
    }
    record Waiting(long id, boolean mech, float x, float y) { }
    private final List<Carrier> carriers = new ArrayList<>();
    private final Map<Long, Carrier> ramps = new HashMap<>();

    List<Carrier> carriers() { return List.copyOf(carriers); }
    void register(Carrier carrier) { carriers.add(carrier); }

    /** Launch returns null if no safe, separate landing position is available yet. */
    void dispatch(int infantry, int mechs, Function<Carrier, ShuttleMission> launch) {
        carriers.removeIf(c -> c.delivery.destroyed || (c.pickup != null
                && c.pickup.pickup.state() == PickupManifest.State.LOST));
        for (Carrier c : carriers) {
            if (c.pickup != null && c.pickup.pickup.state() == PickupManifest.State.RECOVERED) {
                c.pickup = null;
                c.landedTick = -1;
            }
        }
        int infantrySeats = 0, mechSeats = 0;
        for (Carrier c : carriers) {
            if (c.mech) mechSeats += c.openSeats(); else infantrySeats += c.openSeats();
        }
        for (Carrier c : carriers) {
            if (c.pickup != null || c.delivery.state != ShuttleState.GONE) continue;
            if (c.mech ? mechSeats >= mechs : infantrySeats >= infantry) continue;
            c.pickup = launch.apply(c);
            if (c.pickup == null) continue;
            if (c.mech) mechSeats += c.openSeats(); else infantrySeats += c.openSeats();
        }
    }

    /** Keep a person's ramp until that craft closes, so two boats do not compete for one queue. */
    void assignRamps(List<Waiting> waiting) {
        Map<Long, Waiting> live = new HashMap<>();
        for (Waiting person : waiting) live.put(person.id(), person);
        ramps.entrySet().removeIf(e -> !live.containsKey(e.getKey()) || !carriers.contains(e.getValue())
                || !e.getValue().boarding() || e.getValue().mech != live.get(e.getKey()).mech());
        Map<Carrier, Integer> assigned = new HashMap<>();
        for (Carrier c : ramps.values()) assigned.merge(c, 1, Integer::sum);
        for (Waiting person : waiting) {
            if (ramps.containsKey(person.id())) continue;
            Carrier nearest = null;
            double distance = Double.POSITIVE_INFINITY;
            for (Carrier c : carriers) {
                if (!c.boarding() || c.mech != person.mech() || assigned.getOrDefault(c, 0) >= c.openSeats()) continue;
                double d = Math.hypot(person.x() - c.pickup.lzX, person.y() - c.pickup.lzY);
                if (d < distance) { nearest = c; distance = d; }
            }
            if (nearest != null) {
                ramps.put(person.id(), nearest);
                assigned.merge(nearest, 1, Integer::sum);
            }
        }
    }
    Carrier ramp(long id) {
        Carrier c = ramps.get(id);
        return c != null && c.boarding() ? c : null;
    }
}
