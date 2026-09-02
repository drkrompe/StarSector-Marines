package com.dillon.starsectormarines.campaign.polity;

/**
 * The polity's three zero-sum ground doctrine choices, the ground-side parallel
 * of vanilla's faction doctrine points ({@code polity-ground-doctrine.md}).
 *
 * <ul>
 *   <li><b>quality</b> tightens the derived grade table toward the top of what
 *       the polity's industry can already make, and leans its armour higher. It
 *       never admits a grade above that cap — law 3 is not negotiable by a
 *       doctrine point.</li>
 *   <li><b>numbers</b> multiplies the allied garrison's headcount through
 *       {@link #numbersMultiplier()}.</li>
 *   <li><b>heavySupport</b> admits a mech lance, and only where the industry
 *       can fabricate one.</li>
 * </ul>
 *
 * <p>There is deliberately no fourth axis, and no training axis: experience is
 * issued with armour everywhere in this project (law 4), so "better troops" is
 * spelled by the quality point moving the armour table, not by a separate
 * slider.
 */
public record PolityDoctrine(int quality, int numbers, int heavySupport) {

    /** Points the polity has to spend across the three axes. */
    public static final int POINTS = 3;

    /** Ceiling on any one axis, so a polity cannot be all of one thing. */
    public static final int MAX_PER_AXIS = 2;

    /** Headcount gain per point spent on numbers. */
    public static final float NUMBERS_STEP = 0.25f;

    /** Nothing spent — the shape a new colony and a legacy save both load at. */
    public static final PolityDoctrine NONE = new PolityDoctrine(0, 0, 0);

    public PolityDoctrine {
        requireAxis(quality, "quality");
        requireAxis(numbers, "numbers");
        requireAxis(heavySupport, "heavySupport");
        int spent = quality + numbers + heavySupport;
        if (spent > POINTS) {
            throw new IllegalArgumentException("Polity doctrine spends " + spent
                    + " points; only " + POINTS + " are available");
        }
    }

    /** Validating factory — the one a player edit goes through. */
    public static PolityDoctrine of(int quality, int numbers, int heavySupport) {
        return new PolityDoctrine(quality, numbers, heavySupport);
    }

    /**
     * Loading factory: coerces whatever is on disk into a legal allocation
     * rather than refusing to load the save. Each axis is clamped to
     * {@code 0..}{@link #MAX_PER_AXIS}, then the overspend is taken back from
     * heavy support first, then numbers, then quality — a fixed order so a
     * given saved triple always loads as the same doctrine.
     */
    public static PolityDoctrine clamped(int quality, int numbers, int heavySupport) {
        int clampedQuality = clampAxis(quality);
        int clampedNumbers = clampAxis(numbers);
        int clampedSupport = clampAxis(heavySupport);
        int overspend = clampedQuality + clampedNumbers + clampedSupport - POINTS;
        if (overspend > 0) {
            int taken = Math.min(overspend, clampedSupport);
            clampedSupport -= taken;
            overspend -= taken;
        }
        if (overspend > 0) {
            int taken = Math.min(overspend, clampedNumbers);
            clampedNumbers -= taken;
            overspend -= taken;
        }
        if (overspend > 0) {
            clampedQuality -= Math.min(overspend, clampedQuality);
        }
        return new PolityDoctrine(clampedQuality, clampedNumbers, clampedSupport);
    }

    /** How many of the {@link #POINTS} are still unspent. */
    public int pointsRemaining() {
        return POINTS - quality - numbers - heavySupport;
    }

    /** Headcount multiplier the numbers axis contributes to the allied garrison. */
    public float numbersMultiplier() {
        return 1f + NUMBERS_STEP * numbers;
    }

    /** Whether the polity has bought a mech lance at all; the industry still gates it. */
    public boolean wantsHeavySupport() {
        return heavySupport >= 1;
    }

    private static int clampAxis(int value) {
        return Math.max(0, Math.min(MAX_PER_AXIS, value));
    }

    private static void requireAxis(int value, String axis) {
        if (value < 0 || value > MAX_PER_AXIS) {
            throw new IllegalArgumentException("Polity doctrine axis '" + axis
                    + "' must be 0.." + MAX_PER_AXIS + ", got " + value);
        }
    }
}
