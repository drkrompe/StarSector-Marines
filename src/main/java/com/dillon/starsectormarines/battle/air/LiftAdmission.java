package com.dillon.starsectormarines.battle.air;

import java.util.Collection;
import java.util.List;

/**
 * Whether one carrier can lift one machine, and if not, why.
 *
 * <p>Pure and reusable: the Mech Support carrier choice, the
 * briefing's lift line and a per-mission lift budget all ask this one question
 * rather than comparing classes themselves.
 *
 * @param payload what was asked to fly
 * @param rating  what the carrier could take
 * @param refusal {@code null} when admitted
 */
public record LiftAdmission(LiftClass payload, LiftRating rating, Refusal refusal) {

    /** Why a machine is not lifted. */
    public enum Refusal {
        /** The carrier lifts no machine at all — a small boat, or a bus with no cradle. */
        NO_LIFT,
        /** The carrier lifts machines, but not one this heavy. */
        UNDER_RATED
    }

    public boolean admitted() {
        return refusal == null;
    }

    /** Payload class against a carrier rating. */
    public static LiftAdmission check(LiftClass payload, LiftRating rating) {
        if (payload == null) throw new IllegalArgumentException("payload lift class is required");
        LiftRating carried = rating == null ? LiftRating.NONE : rating;
        Refusal refusal = carried.admits(payload) ? null
                : carried == LiftRating.NONE ? Refusal.NO_LIFT : Refusal.UNDER_RATED;
        return new LiftAdmission(payload, carried, refusal);
    }

    /** Payload class against an airframe's own rating ({@link Airframe#liftRating}). */
    public static LiftAdmission check(LiftClass payload, Airframe carrier) {
        return check(payload, carrier == null ? LiftRating.NONE : carrier.liftRating());
    }

    /** How many of {@code load} {@code carrier} admits. */
    public static int admittedCount(List<LiftClass> load, Airframe carrier) {
        int admitted = 0;
        for (LiftClass payload : load) {
            if (payload != null && check(payload, carrier).admitted()) admitted++;
        }
        return admitted;
    }

    /**
     * The company boat best suited to fly {@code load}: among cradled boats
     * not in {@code taken}, the one that admits the most of it,
     * then the lightest rating that does (a better cradle is kept for a heavier
     * call), then the first committed. {@code null} when none admits any of it.
     */
    public static FittedBoat bestCarrier(List<FittedBoat> boats, List<LiftClass> load,
                                         Collection<FittedBoat> taken) {
        if (boats == null || load == null || load.isEmpty()) return null;
        FittedBoat best = null;
        int bestCount = 0;
        for (FittedBoat boat : boats) {
            if (boat == null || !boat.cradled()) continue;
            if (taken != null && taken.contains(boat)) continue;
            int count = admittedCount(load, boat);
            if (count == 0) continue;
            if (best == null || count > bestCount
                    || (count == bestCount
                    && boat.liftRating().ordinal() < best.liftRating().ordinal())) {
                best = boat;
                bestCount = count;
            }
        }
        return best;
    }
}
