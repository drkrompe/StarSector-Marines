package com.dillon.starsectormarines.battle.world.model;

import java.util.Locale;

/**
 * How a fort's belt (its trench and sandbag nests) comes to be manned, chosen by {@code -Dbattle.fort.beltGarrison}.
 * One switch for the generator that may seat a born garrison and the defender commander that may man the belt in answer
 * to pressure, so a control run cannot switch on half of a mode.
 *
 * <ul>
 *   <li>{@link #REACTIVE} (default; also {@code reactive}): belts are empty while quiet and the commander mans the ones
 *       under believed pressure from its mobile pool.</li>
 *   <li>{@link #STATIC} ({@code static} or {@code true}): each belt nest seats a born garrison from the first tick, the
 *       behaviour reactive manning replaced. A same-build control.</li>
 *   <li>{@link #OFF} ({@code false} or {@code off}): no belt garrison of either kind.</li>
 * </ul>
 */
public enum BeltGarrisonMode {
    REACTIVE, STATIC, OFF;

    public static final String PROPERTY = "battle.fort.beltGarrison";

    private static final BeltGarrisonMode CURRENT = parse(System.getProperty(PROPERTY));

    /** The mode this JVM was started with. */
    public static BeltGarrisonMode current() {
        return CURRENT;
    }

    /** Whether the generator seats a born squad at each belt nest. */
    public boolean bornGarrison() {
        return this == STATIC;
    }

    /** Whether the defender commander mans belts under pressure. */
    public boolean reactiveManning() {
        return this == REACTIVE;
    }

    static BeltGarrisonMode parse(String value) {
        if (value == null) return REACTIVE;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "false", "off", "none" -> OFF;
            case "static", "true", "born" -> STATIC;
            default -> REACTIVE;
        };
    }
}
