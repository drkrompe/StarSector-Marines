package com.dillon.starsectormarines.battle.turret;

/**
 * How a side's emplacements are built, which decides how they are drawn and nothing else (`ground-turret-tiers.md`).
 * A style is chosen for the posts a generator stands, from who holds the ground and how ready they were for a
 * fight, and a structure keeps the style it was built in when it changes hands. Each style names its art by
 * {@link #key} in a structure's {@code looks}; {@link #BRUTALIST} is the art a structure carries by default.
 *
 * <p>Presentation only, as a tech family is (`tech-families.md`): no rule reads it.
 */
public enum EmplacementStyle {
    /** Low-tech, prepared: poured concrete collars and plinths, olive cast heads. */
    BRUTALIST("brutalist"),
    /** Low-tech, militia: sandbag and gabion works, field-built heads. */
    EARTHWORK("earthwork"),
    /** Abandoned works: rusted colony steel and faded hazard paint, for derelict sites. */
    DERELICT("derelict");

    /** The id a structure's {@code looks} entry is keyed by. */
    public final String key;

    EmplacementStyle(String key) {
        this.key = key;
    }
}
