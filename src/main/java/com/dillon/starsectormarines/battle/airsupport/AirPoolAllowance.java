package com.dillon.starsectormarines.battle.airsupport;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What one side's air pool is, as the mission declared it: a stock per kind and whether the side's own AI directs the
 * pool or leaves it to a player.
 *
 * <p>Declared the way a mech-drop allowance is: nothing about the map, the market or the force committed decides it.
 * The pool is a regenerating establishment rather than a count of calls, so a strike over a strong defence visibly
 * drains it and time refills it.
 *
 * @param stocks          the declared stock of each kind; a kind absent here is {@link AirPoolStock#NONE}
 * @param aiDirected      whether the side's {@link AirStrikeDirector} calls strikes through its comms operators.
 *                        {@code false} leaves the pool to the player's air-strike power or an explicit caller
 * @param debugStrikeTick a DEBUG/evidence statement: when {@code >= 0} the director places one strike on the first
 *                        target it can designate once that sim tick is reached, however it would otherwise judge it
 */
public record AirPoolAllowance(Map<AirPoolKind, AirPoolStock> stocks, boolean aiDirected, int debugStrikeTick) {

    /** The production value of {@link #debugStrikeTick}. */
    public static final int NO_DEBUG_STRIKE = -1;

    public static final AirPoolAllowance NONE = new AirPoolAllowance(Map.of(), false, NO_DEBUG_STRIKE);

    public AirPoolAllowance {
        EnumMap<AirPoolKind, AirPoolStock> kept = new EnumMap<>(AirPoolKind.class);
        if (stocks != null) {
            for (Map.Entry<AirPoolKind, AirPoolStock> e : stocks.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isNone()) kept.put(e.getKey(), e.getValue());
            }
        }
        stocks = Collections.unmodifiableMap(kept);
        if (debugStrikeTick < 0) debugStrikeTick = NO_DEBUG_STRIKE;
    }

    /** A pool of bombers and fighters, directed by whoever the caller says. */
    public static AirPoolAllowance of(AirPoolStock bombers, AirPoolStock fighters, boolean aiDirected) {
        EnumMap<AirPoolKind, AirPoolStock> map = new EnumMap<>(AirPoolKind.class);
        if (bombers != null) map.put(AirPoolKind.BOMBER, bombers);
        if (fighters != null) map.put(AirPoolKind.FIGHTER, fighters);
        return new AirPoolAllowance(map, aiDirected, NO_DEBUG_STRIKE);
    }

    /** This allowance with its director placing a strike once {@code tick} is reached. */
    public AirPoolAllowance strikeAtTick(int tick) {
        return new AirPoolAllowance(stocks, aiDirected, tick);
    }

    /** The stock of {@code kind}; {@link AirPoolStock#NONE} when none. */
    public AirPoolStock stock(AirPoolKind kind) {
        return stocks.getOrDefault(kind, AirPoolStock.NONE);
    }

    public boolean isNone() {
        return stocks.isEmpty();
    }
}
