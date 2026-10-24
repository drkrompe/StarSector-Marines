package com.dillon.starsectormarines.battle.turret;

/**
 * How a side's emplacements are built, which decides how they are drawn and nothing else (`ground-turret-tiers.md`).
 * A style is chosen for the posts a generator stands, from who holds the ground and how ready they were for a
 * fight, and a structure keeps the style it was built in when it changes hands. Each style names its art by
 * {@link #key} in a structure's {@code looks}; {@link #BRUTALIST} is the art a structure carries by default.
 *
 * <p>Each tech family (`tech-families.md`) builds a prepared and a militia style: low-tech pours {@link #BRUTALIST}
 * or digs {@link #EARTHWORK}, midline sets up {@link #MODULAR} panels or {@link #BARRIER} cells, high-tech grows
 * {@link #COMPOSITE} shells or drops {@link #DEPLOYED} kit. {@link #DERELICT} is old works, outside the families.
 *
 * <p>Presentation only, as a tech family is: no rule reads it.
 */
public enum EmplacementStyle {
    /** Low-tech, prepared: poured concrete collars and plinths, olive cast heads. */
    BRUTALIST("brutalist", false),
    /** Low-tech, militia: sandbag and gabion works, field-built heads. */
    EARTHWORK("earthwork", true),
    /** Abandoned works: rusted colony steel and faded hazard paint, for derelict sites. */
    DERELICT("derelict", false),
    /** Midline, prepared: League-standard pressed modular panels in slate blue-grey, strip lamps, unit chevrons. */
    MODULAR("modular", false),
    /** Midline, militia: the same standard heads on earth-filled mesh barrier cells, tarps and ammunition cases. */
    BARRIER("barrier", true),
    /** High-tech, prepared: seamless white composite shells with graphite gaps and cyan emitter seams. */
    COMPOSITE("composite", false),
    /** High-tech, militia: the same white pods on drop-deployed graphite pads, struts and foam barrier shells. */
    DEPLOYED("deployed", true);

    /** The id a structure's {@code looks} entry is keyed by. */
    public final String key;
    /**
     * Whether these are field works thrown up by a garrison without prepared defences: the post's ring is
     * embanked rather than poured and its ground is left unpaved.
     */
    public final boolean militia;

    EmplacementStyle(String key, boolean militia) {
        this.key = key;
        this.militia = militia;
    }
}
