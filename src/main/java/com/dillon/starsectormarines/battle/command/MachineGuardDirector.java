package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntBinaryOperator;

/**
 * Which of a side's free machines stands guard by which of its manned places (`garrisonables.md`, machine guard): the
 * commander-tier half of the machine guard, beside {@link GarrisonDirector}'s manning. It knows nothing of which side
 * it serves, nor whether a place is a stretch of a run or a building's envelope ({@link #placeManned}); it is given
 * the places the side mans this pulse (and which of them are pressed), the side's free mech
 * lances, and answers which lance is attached to which place and when it stands down. The machine-tier half — where
 * the chassis stands, which way it faces, what it engages — is {@code MachineGuards}, reached through the order a
 * commander gives ({@link CommandEffects#guardPlace}).
 *
 * <p><b>Laws.</b> Machines are scarce and are not pulled off higher-value work: only a lance the caller offers as free
 * (not answering a threat, not committed to a company or a capture) is attached, and one already fighting only to a
 * place near enough that its fight is the garrison's ({@link #CONTACT_REACH_CELLS}). A place is guarded only
 * while it is manned and pressed, one lance a place, at most {@link #MAX_NEW_PER_PULSE} attached a pulse and never more
 * at once than {@link #cap}: two, and at most half the side's lances rounded up, so a side with two keeps one roving.
 * A lance is sent only if it can arrive while the pressure lasts ({@link GarrisonDirector#MAX_ETA_TICKS}), and only at
 * useful strength. A guard stands down when its place is no longer manned, when the place has stayed below the start
 * pressure for {@link #COOL_PULSES} pulses, or when its lance falls below useful strength.
 */
final class MachineGuardDirector {

    /** Most lances one side keeps on guard at once. UNTUNED. */
    static final int MAX_GUARDS = 2;
    /** Most lances newly attached in one pulse. UNTUNED. */
    static final int MAX_NEW_PER_PULSE = 1;
    /** Pulses a guarded place may stay below the start pressure before its guard stands down: the manning dwell. */
    static final int COOL_PULSES = GarrisonDirector.DWELL_PULSES;
    /** Nearest (Chebyshev cells) a lance already in contact must be to a place to be attached to it. UNTUNED. */
    static final int CONTACT_REACH_CELLS = 48;

    /**
     * A lance the caller offers, at its anchor cell, with its strength. {@code inContact}: it is already fighting, so
     * it is attached only to a place close enough that its fight is plausibly the one pressing the garrison
     * ({@link #CONTACT_REACH_CELLS}); a guard there is the same fight from a better cell.
     */
    record Machine(int squadId, int x, int y, int alive, int original, boolean inContact) {

        Machine(int squadId, int x, int y, int alive, int original) {
            this(squadId, x, y, alive, original, false);
        }
    }

    /** A place the side mans this pulse, at a cell of it; {@code hot} when believed pressure is at the start level. */
    record Place(int garrisonable, int cellX, int cellY, boolean hot) { }

    /** A lance newly attached to a place, to be ordered onto the place's cell. */
    record Attach(int squadId, int garrisonable, int cellX, int cellY) { }

    private static final class Guard {
        final int cellX;
        final int cellY;
        int coolPulses;

        Guard(int cellX, int cellY) {
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    private final Map<Integer, Guard> guards = new TreeMap<>();

    /** Lances on guard at once for a side fielding {@code machineSquads}: half rounded up, never above {@link #MAX_GUARDS}. */
    static int cap(int machineSquads) {
        return Math.max(0, Math.min(MAX_GUARDS, (machineSquads + 1) / 2));
    }

    /** Whether a lance is at useful strength: at least half the chassis it started with, and one at the least. */
    static boolean useful(int alive, int original) {
        return alive >= 1 && alive * 2 >= Math.max(alive, original);
    }

    /** Whether the lance stands guard by some place. */
    boolean holds(int squadId) {
        return guards.containsKey(squadId);
    }

    /** Lances on guard. */
    int count() {
        return guards.size();
    }

    /** Drops a guard the order could not be given for, or whose lance is gone. */
    void forget(int squadId) {
        guards.remove(squadId);
    }

    /**
     * Stands or stands down every guard for this pulse. A guard whose lance is not among {@code guarding} (gone, under
     * another authority, or taken up by higher-value work), whose place is not manned or has cooled for the dwell, or
     * whose lance is below useful strength stands down.
     *
     * @param placeAt the garrisonable at a cell this pulse, or a negative id: a run shelling has renumbered is followed
     * @return the lances standing down this pulse, in id order
     */
    List<Integer> review(Map<Integer, Machine> guarding, List<Place> manned, IntBinaryOperator placeAt) {
        List<Integer> down = new ArrayList<>();
        for (var it = guards.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Machine machine = guarding.get(entry.getKey());
            Guard guard = entry.getValue();
            if (machine == null) {
                // Taken up by higher-value work, or gone: the order it was given is withdrawn either way.
                it.remove();
                down.add(entry.getKey());
                continue;
            }
            int place = placeAt.applyAsInt(guard.cellX, guard.cellY);
            Place now = place < 0 ? null : find(manned, place);
            if (now != null && now.hot()) guard.coolPulses = 0;
            else if (now != null) guard.coolPulses++;
            if (now == null || guard.coolPulses >= COOL_PULSES || !useful(machine.alive(), machine.original())) {
                it.remove();
                down.add(entry.getKey());
            }
        }
        return down;
    }

    /**
     * Attaches free lances to pressed places nobody guards: the places in the order given (the caller's most pressed
     * first), each the nearest useful lance that can arrive in time, ties by squad id, within this pulse's share and
     * the side's {@link #cap}.
     *
     * @param machineSquads every lance the side fields, for the cap
     */
    List<Attach> attach(List<Machine> free, List<Place> manned, int machineSquads, IntBinaryOperator placeAt) {
        List<Attach> out = new ArrayList<>();
        int budget = Math.min(MAX_NEW_PER_PULSE, cap(machineSquads) - guards.size());
        if (budget <= 0 || free.isEmpty()) return out;
        Set<Integer> guarded = new HashSet<>();
        for (Guard guard : guards.values()) guarded.add(placeAt.applyAsInt(guard.cellX, guard.cellY));
        Set<Integer> used = new HashSet<>();
        for (Place place : manned) {
            if (out.size() >= budget) break;
            if (!place.hot() || guarded.contains(place.garrisonable())) continue;
            Machine best = null;
            int bestEta = Integer.MAX_VALUE;
            for (Machine machine : free) {
                if (used.contains(machine.squadId()) || guards.containsKey(machine.squadId())
                        || !useful(machine.alive(), machine.original())) continue;
                if (machine.inContact() && Math.max(Math.abs(machine.x() - place.cellX()),
                        Math.abs(machine.y() - place.cellY())) > CONTACT_REACH_CELLS) continue;
                int eta = GarrisonDirector.etaTicks(machine.x(), machine.y(), place.cellX(), place.cellY());
                if (eta > GarrisonDirector.MAX_ETA_TICKS) continue;
                if (eta < bestEta || (eta == bestEta && machine.squadId() < best.squadId())) {
                    best = machine;
                    bestEta = eta;
                }
            }
            if (best == null) continue;
            used.add(best.squadId());
            guarded.add(place.garrisonable());
            guards.put(best.squadId(), new Guard(place.cellX(), place.cellY()));
            out.add(new Attach(best.squadId(), place.garrisonable(), place.cellX(), place.cellY()));
        }
        return out;
    }

    /**
     * The garrisonable an order mans, or -1: the stretch a run-scoped area defence stands on, or the envelope of the
     * building a hold of an envelope names (found by the building, since a commander's facts may carry a copy of its
     * node, and by the order's cell when the registry no longer offers it). An order that mans nothing answers -1.
     */
    static int placeManned(ObjectiveAssignment order, GarrisonableRegistry places) {
        if (order == null || places == null) return -1;
        if (order.isGarrison()) return places.idAt(order.targetCellX(), order.targetCellY());
        if (!order.isEnvelope()) return -1;
        int envelope = places.envelopeOf(order.targetNode());
        if (envelope >= 0) return envelope;
        int at = places.idAt(order.targetCellX(), order.targetCellY());
        return at >= 0 && places.isEnvelope(at) ? at : -1;
    }

    /**
     * The places a side mans, from the ids of the places its squads are ordered to man (stretches of runs and buildings'
     * envelopes alike, and buildings their own garrison stands in): the pressed ones first, in
     * {@code hotPlaces}' order (the manning director's, most pressed first), then the rest by id.
     */
    static List<Place> manned(Set<Integer> mannedIds, int[] hotPlaces, GarrisonableRegistry places) {
        List<Place> out = new ArrayList<>();
        Set<Integer> hot = new HashSet<>();
        for (int place : hotPlaces) {
            hot.add(place);
            if (mannedIds.contains(place)) out.add(new Place(place, places.centreX(place), places.centreY(place), true));
        }
        for (int place : new TreeSet<>(mannedIds)) {
            if (!hot.contains(place)) out.add(new Place(place, places.centreX(place), places.centreY(place), false));
        }
        return out;
    }

    private static Place find(List<Place> places, int garrisonable) {
        for (Place place : places) if (place.garrisonable() == garrisonable) return place;
        return null;
    }
}
