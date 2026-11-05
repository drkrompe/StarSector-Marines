package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * A machine fitted from stores, whatever it is: a mech, a vehicle or a shuttle (mechs-nouns.md, why a machine
 * differs; owner: "Ideally we share code on how equipment works"). What it lacks is read through {@link #gaps}, by one
 * rule on every machine, so a fitting room never decides on its own what is red and what is yellow.
 */
public interface Equipped {

    /** What it is fitted without, in the order it matters: each required part missing, then what it goes without. */
    List<FittingGap> gaps();

    /** Whether it has every part it cannot go without. */
    default boolean complete() {
        for (FittingGap gap : gaps()) if (gap.required()) return false;
        return true;
    }

    /** Whether {@code gaps} holds a gap of {@code lack}; for {@link FittingGap.Lack#PART}, any missing part. */
    static boolean lacks(List<FittingGap> gaps, FittingGap.Lack lack) {
        if (gaps != null) for (FittingGap gap : gaps) if (gap.lack() == lack) return true;
        return false;
    }

    /** Whether {@code gaps} says the required part at {@code part} is missing. */
    static boolean missing(List<FittingGap> gaps, Enum<?> part) {
        if (gaps != null) for (FittingGap gap : gaps) if (gap.required() && gap.part() == part) return true;
        return false;
    }
}
