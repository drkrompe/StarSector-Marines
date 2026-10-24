package com.dillon.starsectormarines.battle.turret;

/**
 * How a side's emplacements are built, which decides how they are drawn and nothing else (`ground-turret-tiers.md`).
 * A style is chosen for the posts a generator stands, from who holds the ground and how ready they were for a
 * fight, and a structure keeps the style it was built in when it changes hands. Each style names its art by
 * {@link #key} in a structure's {@code looks}; {@link #BRUTALIST} is the art a structure carries by default.
 *
 * <p>Each tech family (`tech-families.md`) builds a prepared and a militia style: low-tech pours {@link #BRUTALIST}
 * or digs {@link #EARTHWORK}; midline sets up the League's {@link #MODULAR} panels or {@link #BARRIER} cells, or the
 * Diktat's {@link #EXPORT} panels or {@link #BLOCKWORK}; high-tech grows {@link #COMPOSITE} field-emitter shells or
 * drops {@link #DEPLOYED} kit. {@link #DERELICT} is old works, outside the families, and {@link #PATCHWORK} is what
 * pirates make of them.
 *
 * <p>Presentation, as a tech family is, with one exception: a look may fit its structure's flux shield
 * ({@link StructureLook#shield}), which a battle reads.
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
    /** Midline (Diktat), prepared: export-grade sand-ochre ceramic-steel panels with red unit bands. */
    EXPORT("export", false),
    /** Midline (Diktat), militia: the same ochre heads in moulded orange-red and white barrier blocks. */
    BLOCKWORK("blockwork", true),
    /**
     * High-tech, prepared: pearl-grey hex-armoured composite with glass canopies and blue field-projector rings, each
     * hardpoint under a directional flux shield ({@link StructureLook#shield}).
     */
    COMPOSITE("composite", false),
    /** High-tech, militia: the same pods on folding pylons and pads behind hard-light barrier panes. */
    DEPLOYED("deployed", true),
    /**
     * Pirate works: old colony steel patched with scrap plate, drums and tarps. Its guns are the derelict guns
     * ({@link #basis}); its walls, parapets, barricades and gate are its own.
     */
    PATCHWORK("patchwork", false, DERELICT);

    /** The id a structure's {@code looks} entry is keyed by. */
    public final String key;
    /**
     * Whether these are field works thrown up by a garrison without prepared defences: the post's ring is
     * embanked rather than poured and its ground is left unpaved.
     */
    public final boolean militia;
    /**
     * The style whose art this one wears wherever it has none of its own, or null for a style that falls back to the
     * default art directly.
     */
    public final EmplacementStyle basis;

    EmplacementStyle(String key, boolean militia) {
        this(key, militia, null);
    }

    EmplacementStyle(String key, boolean militia, EmplacementStyle basis) {
        this.key = key;
        this.militia = militia;
        this.basis = basis;
    }
}
