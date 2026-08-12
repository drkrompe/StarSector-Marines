package com.dillon.starsectormarines.battle.infantry;

/** Innate combat aptitude. Unlike experience, this does not improve with use. */
public enum SoldierAptitude {
    LIMITED("Limited", 0.92f, 1.08f),
    STEADY("Steady", 1.00f, 1.00f),
    GIFTED("Gifted", 1.07f, 0.94f),
    EXCEPTIONAL("Exceptional", 1.13f, 0.89f);

    public final String displayName;
    public final float accuracyMult;
    public final float spreadMult;

    SoldierAptitude(String displayName, float accuracyMult, float spreadMult) {
        this.displayName = displayName;
        this.accuracyMult = accuracyMult;
        this.spreadMult = spreadMult;
    }
}
