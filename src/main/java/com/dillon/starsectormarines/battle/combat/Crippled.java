package com.dillon.starsectormarines.battle.combat;

/**
 * What a machine keeps of its movement once what moves it is gone: a ground hull whose drive is shot to zero, a mech
 * whose legs are. It is crippled, not stranded (owner): it still drives, reverses, turns and walks, at this share of
 * its speed, so an order or a story that needs it somewhere still gets it there, late. See
 * {@code combat-durability-nouns.md}.
 */
public final class Crippled {

    /** Share of its top speed, and of its turn on the spot, a crippled machine keeps. */
    public static final float SPEED_SHARE = .35f;

    private Crippled() {
    }
}
