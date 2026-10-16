package com.dillon.starsectormarines.battle.air;

/**
 * Whether one carrier can lift one machine, and if not, why.
 *
 * <p>Pure and reusable: the Mech Support and Armour Support carrier choice, the
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
}
