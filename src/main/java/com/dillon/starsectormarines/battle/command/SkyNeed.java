package com.dillon.starsectormarines.battle.command;

/**
 * Why a side wants the sky opened, as a plan reads it. Two separate reasons, both from the side's own knowledge:
 *
 * @param shut    the anti-air it has sighted over a fort refuses the strikes it could place (see {@link AirSupportFacts#blocked()})
 * @param flownAt a unit of the side has lately had a hostile armed aircraft in view: its muster or its march is being
 *                bombed, and the aircraft are being called onto it by someone who can see it
 */
public record SkyNeed(boolean shut, boolean flownAt) {

    /** The sky is nobody's concern. */
    public static final SkyNeed NONE = new SkyNeed(false, false);
    /** Shut out of the sky and not being flown at: the old boolean form of the question. */
    public static final SkyNeed SHUT = new SkyNeed(true, false);

    /** Whether the side wants the sky for any reason. */
    public boolean any() {
        return shut || flownAt;
    }

    /** The old boolean form: a side that is shut out of the sky. */
    public static SkyNeed of(boolean shut) {
        return shut ? SHUT : NONE;
    }
}
