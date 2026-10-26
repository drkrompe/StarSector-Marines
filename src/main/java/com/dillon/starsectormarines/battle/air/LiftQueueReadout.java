package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * The lift queue as the player reads it: the ferry craft in the air and what each carries,
 * then what is waiting (the squad loads still to fly and each queued machine drop) with an
 * estimate of when it sets down.
 *
 * <p>Every figure is a reading of live {@link ShuttleMission}s, as {@link LiftSchedule}'s
 * are: a craft's re-arm countdown, where it is and how fast it flies. A queued drop's time
 * is worked out by handing the queue, oldest first, to whichever craft that can lift it
 * frees up soonest, so the estimate follows the same rule the craft do. It is an estimate:
 * a craft lost or a stalled unload moves it.
 */
public final class LiftQueueReadout {

    private LiftQueueReadout() {}

    /** What a sortie carries. */
    public enum Cargo { SQUAD, MACHINE }

    /**
     * A ferry craft in the air with its load.
     *
     * @param marines            passengers aboard (a squad load), or 1 for a machine
     * @param machine            the machine aboard, or {@code null} for a squad load
     * @param secondsToTouchdown sim-seconds until it is down; 0 once on the pad
     */
    public record Flight(long craftId, Airframe frame, Cargo cargo, int marines, ArmorLoad machine,
                         float secondsToTouchdown, boolean onPad) {}

    /**
     * Something waiting for a ferry craft.
     *
     * @param loads squad sorties still to fly (for {@link Cargo#SQUAD}); 1 for a machine
     * @param drop  the queued machine drop, or {@code null} for squad loads
     * @param eta   sim-seconds until it sets down, or {@code NaN} when no live craft can fly it
     */
    public record Waiting(Cargo cargo, int loads, LiftQueue.MachineDrop drop, float eta) {}

    /** One reading. */
    public record Reading(List<Flight> flights, List<Waiting> waiting) {
        public static final Reading EMPTY = new Reading(List.of(), List.of());

        public boolean isEmpty() {
            return flights.isEmpty() && waiting.isEmpty();
        }
    }

    /** One ferry craft as the estimate needs it. */
    record Craft(long id, ShuttleMission mission, Airframe frame, float x, float y, float speed) {}

    /** Reads the player's ferry and lift queue off {@code sim}. */
    public static Reading capture(BattleView sim) {
        if (sim == null || sim.world() == null) return Reading.EMPTY;
        World world = sim.world();
        List<Craft> craft = new ArrayList<>();
        for (long id : sim.getAirEntityIds()) {
            if (world.airFaction(id) != Faction.MARINE) continue;
            ShuttleMission mission = world.mission(id);
            AirBody body = world.kinematics(id);
            Airframe frame = world.airframe(id);
            if (mission == null || body == null || frame == null) continue;
            if (!mission.ferry && !LiftQueue.ferryCandidate(mission)) continue;
            craft.add(new Craft(id, mission, frame, body.x, body.y,
                    FittedBoat.handling(frame, mission).maxSpeed()));
        }
        LiftQueue queue = sim.liftQueue();
        return read(craft, queue == null ? List.of() : queue.queued());
    }

    static Reading read(List<Craft> craft, List<LiftQueue.MachineDrop> queued) {
        List<Flight> flights = new ArrayList<>();
        int squadLoads = 0;
        float nextSquad = Float.NaN;
        List<float[]> free = new ArrayList<>();
        List<Craft> lifters = new ArrayList<>();
        for (Craft c : craft) {
            ShuttleMission m = c.mission();
            if (m.destroyed || m.state == ShuttleState.GONE || m.state == ShuttleState.CRASHING) continue;
            Flight flight = flight(c);
            if (flight != null) flights.add(flight);
            int waiting = squadLoadsWaiting(m);
            if (waiting > 0) {
                squadLoads += waiting;
                float eta = nextSquadTouchdown(c);
                if (Float.isNaN(nextSquad) || eta < nextSquad) nextSquad = eta;
            }
            if (c.frame().liftRating() != LiftRating.NONE && m.ferry) {
                lifters.add(c);
                free.add(new float[]{freeAt(c)});
            }
        }
        List<Waiting> waiting = new ArrayList<>();
        if (squadLoads > 0) waiting.add(new Waiting(Cargo.SQUAD, squadLoads, null, nextSquad));
        for (LiftQueue.MachineDrop drop : queued) {
            int best = -1;
            for (int i = 0; i < lifters.size(); i++) {
                if (!LiftAdmission.check(drop.liftClass(), lifters.get(i).frame()).admitted()) continue;
                if (best < 0 || free.get(i)[0] < free.get(best)[0]) best = i;
            }
            if (best < 0) {
                waiting.add(new Waiting(Cargo.MACHINE, 1, drop, Float.NaN));
                continue;
            }
            Craft c = lifters.get(best);
            float speed = speed(c);
            float[] route = drop.route();
            float eta = free.get(best)[0]
                    + dist(route[0], route[1], drop.lzX(), drop.lzY()) / speed
                    + LiftSchedule.SETTLE_SECONDS;
            free.get(best)[0] = eta + c.mission().deboardInterval
                    + dist(drop.lzX(), drop.lzY(), route[2], route[3]) / speed
                    + c.mission().rearmDelay;
            waiting.add(new Waiting(Cargo.MACHINE, 1, drop, eta));
        }
        return new Reading(List.copyOf(flights), List.copyOf(waiting));
    }

    /** The craft's load if it is on its way down or on the pad with one; otherwise null. */
    private static Flight flight(Craft c) {
        ShuttleMission m = c.mission();
        if (m.standby || m.marinesRemaining <= 0) return null;
        boolean machine = m.machineDropId != 0L && m.armorDrop != null && m.armorDrop.length > 0;
        Cargo cargo = machine ? Cargo.MACHINE : Cargo.SQUAD;
        ArmorLoad load = machine ? m.armorDrop[0] : null;
        return switch (m.state) {
            case INCOMING -> new Flight(c.id(), c.frame(), cargo, m.marinesRemaining, load,
                    dist(c.x(), c.y(), m.lzX, m.lzY) / speed(c) + LiftSchedule.SETTLE_SECONDS, false);
            case PAD_DESCENT, LANDED -> new Flight(c.id(), c.frame(), cargo, m.marinesRemaining, load,
                    0f, true);
            default -> null;
        };
    }

    /** Squad sorties this craft has still to start: its current one if it is waiting, then the rest. */
    static int squadLoadsWaiting(ShuttleMission m) {
        if (m.standby || m.machineDropId != 0L) return 0;
        int after = Math.max(0, m.totalCycles - m.currentCycle - 1);
        boolean currentWaiting = m.state == ShuttleState.PENDING && m.marinesRemaining > 0;
        return after + (currentWaiting ? 1 : 0);
    }

    /** Sim-seconds until this craft's next squad load that is not yet in the air sets down. */
    private static float nextSquadTouchdown(Craft c) {
        ShuttleMission m = c.mission();
        if (m.state == ShuttleState.PENDING) return Math.max(0f, m.pendingDelay) + down(c);
        return sortieLeft(c) + m.rearmDelay + down(c);
    }

    /** Sim-seconds until this craft is free to take a machine drop. */
    static float freeAt(Craft c) {
        ShuttleMission m = c.mission();
        if (m.standby) return Math.max(0f, m.pendingDelay);
        float t = m.state == ShuttleState.PENDING
                ? Math.max(0f, m.pendingDelay) + down(c) + unload(m, m.marinesRemaining) + back(c)
                : sortieLeft(c);
        int after = Math.max(0, m.totalCycles - m.currentCycle - 1);
        t += after * (m.rearmDelay + down(c) + unload(m, m.seatsPerSortie) + back(c));
        return t + m.rearmDelay;
    }

    /** Sim-seconds until the sortie in the air ends at its exit point. */
    private static float sortieLeft(Craft c) {
        ShuttleMission m = c.mission();
        float speed = speed(c);
        return switch (m.state) {
            case INCOMING -> dist(c.x(), c.y(), m.lzX, m.lzY) / speed + LiftSchedule.SETTLE_SECONDS
                    + unload(m, m.marinesRemaining) + back(c);
            case PAD_DESCENT, LANDED -> unload(m, m.marinesRemaining) + back(c);
            case DEPARTING -> dist(c.x(), c.y(), m.exitX, m.exitY) / speed;
            default -> Math.max(0f, m.pendingDelay);
        };
    }

    private static float down(Craft c) {
        ShuttleMission m = c.mission();
        return dist(m.entryX, m.entryY, m.lzX, m.lzY) / speed(c) + LiftSchedule.SETTLE_SECONDS;
    }

    private static float back(Craft c) {
        ShuttleMission m = c.mission();
        return dist(m.lzX, m.lzY, m.exitX, m.exitY) / speed(c);
    }

    private static float unload(ShuttleMission m, int passengers) {
        return Math.max(0, passengers) * m.deboardInterval;
    }

    private static float speed(Craft c) {
        return Math.max(0.1f, c.speed());
    }

    private static float dist(float ax, float ay, float bx, float by) {
        return (float) Math.hypot(bx - ax, by - ay);
    }
}
