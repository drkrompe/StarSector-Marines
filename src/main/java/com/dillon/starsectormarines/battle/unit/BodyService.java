package com.dillon.starsectormarines.battle.unit;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * The registry of {@link BodyCarrier}s and the one place a consumer asks about
 * a body whose carrier it does not care about.
 *
 * <p>Everything downstream of "a thing that can be shot" used to re-derive the
 * carrier for itself: the index rebuild had a path per kind, ballistics branched
 * twice, the splash sweep ran a loop per kind, and the damage service dispatched
 * into a resolver per kind. Adding the third kind meant editing all of them, and
 * three sites were missed. Here a carrier registers once and every consumer that
 * goes through this service is correct for a fourth by construction.
 *
 * <p><b>Deliberately not on {@code World}.</b> That by-id facade is being retired
 * in favour of per-component services, so a new fat {@code world.isTargetable(id)}
 * would be a regression. This is a Service in the same sense
 * {@code ConvoyService} and {@code AirTargetService} are — those two are its
 * first carriers.
 *
 * <p><b>Snapshot.</b> {@link #refresh()} rebuilds a flat id list from the
 * carriers once per tick, in the same serial setup phase the spatial index
 * rebuilds in, so a consumer that has to walk every off-roster body does it over
 * one array with no allocation and no per-kind knowledge. The snapshot holds
 * every body a carrier owns, wrecks and off-map craft included; callers filter
 * with {@link #isTargetable(long)}. That way an id that stopped being a body
 * mid-tick is answered honestly rather than needing removal, and a stale entry
 * costs one predicate.
 *
 * <p><b>Threading.</b> {@link #refresh()}, {@link #register} and {@link #admit}
 * are serial-phase only. Every read is safe from the parallel
 * {@code UPDATE_UNITS} dispatch, the same contract {@code UnitSpatialIndex}
 * carries.
 */
public final class BodyService {

    private final UnitRosterService roster;

    /** Registered carriers, fixed after setup; scanned linearly because N is 2. */
    private BodyCarrier[] carriers = new BodyCarrier[0];

    /** Grow-and-stay snapshot rebuilt by {@link #refresh()}; steady-state allocation is zero. */
    private long[] bodyIds = new long[8];
    private int bodyCount;

    /** Held so {@link #refresh()} does not mint a capture per tick. */
    private final LongConsumer appender = this::append;

    public BodyService(UnitRosterService roster) {
        this.roster = roster;
    }

    /**
     * Adds a carrier. This is the whole of what a new kind of body has to do to
     * reach every consumer below — there is no second site to edit.
     */
    public void register(BodyCarrier carrier) {
        int n = carriers.length;
        carriers = Arrays.copyOf(carriers, n + 1);
        carriers[n] = carrier;
    }

    /** The carrier that owns {@code id}, or {@code null} for an ordinary roster unit. */
    public BodyCarrier carrierOf(long id) {
        for (BodyCarrier carrier : carriers) {
            if (carrier.owns(id)) return carrier;
        }
        return null;
    }

    /**
     * Whether {@code id} names something that can be perceived and acted on
     * right now — a live roster unit, or a carried body its carrier says is
     * reachable. The single answer behind the liveness gates that used to spell
     * out "a live unit, or a targetable convoy vehicle" and thereby left air
     * out.
     */
    public boolean isTargetable(long id) {
        BodyCarrier carrier = carrierOf(id);
        return carrier != null ? carrier.isTargetable(id) : roster.isLive(id);
    }

    /** Rebuilds the snapshot from the carriers. Serial setup phase, once per tick. */
    public void refresh() {
        bodyCount = 0;
        for (BodyCarrier carrier : carriers) carrier.forEachBody(appender);
    }

    /**
     * Appends a body that appeared mid-tick, so a chassis put on the map by a
     * serial system is visible to the very next query rather than to the one
     * after the next {@link #refresh()}. Mirrors
     * {@code UnitRosterService.indexVehicle}.
     */
    public void admit(long id) {
        for (int i = 0; i < bodyCount; i++) {
            if (bodyIds[i] == id) return;
        }
        append(id);
    }

    /** How many off-roster bodies the snapshot holds. */
    public int bodyCount() {
        return bodyCount;
    }

    /** The off-roster body at {@code index} in {@code [0, bodyCount())}. */
    public long bodyAt(int index) {
        return bodyIds[index];
    }

    private void append(long id) {
        if (bodyCount == bodyIds.length) {
            bodyIds = Arrays.copyOf(bodyIds, bodyCount << 1);
        }
        bodyIds[bodyCount++] = id;
    }
}
