package com.dillon.starsectormarines.marine;

/**
 * One thing a machine is fitted without, and whether it can go without it (mechs-nouns.md, readiness): the one rule
 * a mech, a vehicle and a shuttle are read by. A {@linkplain Need#REQUIRED required} gap keeps the machine home and
 * warns in red; a {@linkplain Need#RECOMMENDED recommended} one lets it go, worse off, and warns in yellow. The
 * readiness list says each gap in words and the socket cards light the sockets it is about, so the two never
 * disagree.
 *
 * @param part for a missing required part, the place it goes ({@link MechInternal.Role} on a mech or vehicle,
 *             {@link BoatFittingSlot} on a shuttle); null for any other gap
 */
public record FittingGap(Need need, Lack lack, Enum<?> part) {

    /** Whether the machine can go without it. */
    public enum Need { REQUIRED, RECOMMENDED }

    /** What is missing. */
    public enum Lack {
        /** A part the machine cannot go without ({@link #part} names it). */
        PART,
        /** An armor point with no plate: it goes with no armor. */
        ARMOR,
        /** A machine that can carry a weapon and has none anywhere. */
        WEAPON,
        /** A shuttle with no shield emitter: she flies unshielded. */
        SHIELD,
        /** A shuttle with some of her mounts bare and a gun on the rest. */
        MOUNTS
    }

    public FittingGap {
        if (need == null || lack == null) throw new IllegalArgumentException("a gap needs its need and its lack");
        if ((lack == Lack.PART) != (part != null)) {
            throw new IllegalArgumentException("a missing part names its place, and only a missing part does");
        }
    }

    /** A part the machine cannot go without, missing from {@code part}. */
    public static FittingGap missing(Enum<?> part) {
        return new FittingGap(Need.REQUIRED, Lack.PART, part);
    }

    /** Something the machine goes without, worse off. */
    public static FittingGap without(Lack lack) {
        return new FittingGap(Need.RECOMMENDED, lack, null);
    }

    public boolean required() { return need == Need.REQUIRED; }
}
