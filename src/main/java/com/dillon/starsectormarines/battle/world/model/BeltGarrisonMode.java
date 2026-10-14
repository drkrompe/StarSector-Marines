package com.dillon.starsectormarines.battle.world.model;

import java.util.Locale;

/**
 * How a fort's belt (its trench and sandbag nests) comes to be manned, chosen by {@code -Dbattle.fort.beltGarrison}.
 * One switch for the generator that may seat a born garrison and the defender commander that may man the belt in answer
 * to pressure, so a control run cannot switch on half of a mode.
 *
 * <ul>
 *   <li>{@link #REACTIVE} (default; also {@code reactive}): a token picket holds each fort's belt by its main gate
 *       from the first tick, and the commander mans the rest under believed pressure from its mobile pool.</li>
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

    /**
     * Whether each fort seats a token picket by its main gate: the eyes that raise the pressure the reactive pool
     * answers, and somebody in the works when it is rushed. Its size is {@code battle.fort.picketSize}.
     */
    public boolean picket() {
        return this == REACTIVE;
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
