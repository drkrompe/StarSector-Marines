package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;

/**
 * The conversions from the game's ship-combat units to ground-battle cells.
 *
 * <p>{@link #cells} is the density that sizes every aircraft ({@link AirScale}), and it is what a gun's reach against
 * the ground, a shell's blast and a round's speed are read at: a Devastator Cannon that reaches 900 units reaches
 * about forty cells. {@link #hullCells} is what an overhead ship's <em>body</em> is read at: its length and beam, the
 * pivot, the slots its guns and bays sit in, its engine nozzles and its shield bubble, all {@link #HULL_PRESENCE}
 * times larger. A warship over a ground battle is a giant beside the fighters that share the density ladder: at the
 * ladder's own size a Wolf was four cells long and a capital a compound's corner. {@link #hullReach} is what a gun
 * fired at another <em>hull</em> reaches: the game's range read at the hull's own drawn scale and then shortened by
 * {@link #HULL_REACH_SHARE}, so ships fight a few of their own lengths apart rather than across the map. Ground fire,
 * anti-air and point defence keep {@link #cells}, so nothing a ship does to the ground or the air changes with how
 * big it is drawn.
 */
public final class WarshipScale {

    /**
     * How many times larger an overhead hull's body is than the shared density ladder draws it. One factor for every
     * hull, so the ladder between frigate and capital is the game's own. {@code -Dbattle.warship.hullPresence=1} is
     * the control that draws hulls at the shared ladder's size.
     */
    public static final float HULL_PRESENCE = Float.parseFloat(System.getProperty("battle.warship.hullPresence", "6"));

    /**
     * The share of the game's range, read at the hull's drawn scale, that a gun reaches against another hull. The
     * game's ranges are open-space ranges, six frigate lengths and more; in atmosphere ships of the line slug it out
     * two or three lengths apart, with the order between guns (who outranges whom) kept exactly.
     * {@code -Dbattle.warship.hullReachShare} re-dials it.
     */
    public static final float HULL_REACH_SHARE =
            Float.parseFloat(System.getProperty("battle.warship.hullReachShare", "0.5"));

    /**
     * How far apart hulls fight in atmosphere, against the base game's ranges read at {@link #HULL_REACH_SHARE}: every
     * gun's reach at a hull, and so every standoff, run and station keyed off it, is this many times longer, and a
     * round fired at a hull flies this many times faster ({@link #hullRoundSpeed}), so it crosses the longer range in
     * the time it crossed the shorter one and the hull fight's clock ({@link #HULL_TEMPO}) keeps its meaning. Ships of
     * the line fight at a distance with the ground between them, not nose to nose.
     * {@code -Dbattle.warship.hullRange=1} is the control.
     */
    public static final float HULL_RANGE = Float.parseFloat(System.getProperty("battle.warship.hullRange", "2.5"));

    private WarshipScale() {}

    /** Cells a second a round fired at a hull flies: the game's muzzle speed, times {@link #HULL_RANGE}. */
    public static float hullRoundSpeed(float speedGameUnits) {
        return cells(speedGameUnits) * HULL_RANGE;
    }

    /** The reach, in cells, of a gun firing at a hull: the game's range at the hull's drawn scale, shortened. */
    public static float hullReach(float rangeGameUnits) {
        return cells(rangeGameUnits) * HULL_REACH_MULTIPLIER;
    }

    /** {@link #hullReach} over {@link #cells}: the range multiplier a gun firing at a hull reaches by. */
    public static final float HULL_REACH_MULTIPLIER = HULL_PRESENCE * HULL_REACH_SHARE * HULL_RANGE;

    /**
     * The hull fight's clock (`warship-tactics.md`, D1): everything a fight between hulls spends per second (a gun's
     * cycle at a hull, the missile ripple, and on the duel footing flux dissipation, shield upkeep and overload)
     * runs at {@code 1 / HULL_TEMPO}. Derived, not tuned: a round between hulls flies {@link #HULL_REACH_MULTIPLIER}
     * times as far as in the base game at {@link #HULL_RANGE} times the base game's speed, so it spends
     * {@code HULL_PRESENCE * HULL_REACH_SHARE} times as long in the air; at the same tempo the rounds in the air at
     * once, and the share of a fight a round spends crossing, are the base game's. Damage, armour, shields and hit
     * points are untouched. {@code -Dbattle.warship.hullTempo=1} is the control.
     */
    public static final float HULL_TEMPO = Math.max(1f, Float.parseFloat(
            System.getProperty("battle.warship.hullTempo", Float.toString(HULL_PRESENCE * HULL_REACH_SHARE))));

    /**
     * How much longer a hull lasts against another hull's guns: the hull fight's durability. A round one warship lands
     * on another meets the shield and the armour exactly as the base game's does (its damage type, the armour
     * reduction read off the plate left where it entered, the glance), and then whatever the armour lets through, to
     * the plate and to the hull alike, is this many times smaller. One factor for every hull and every gun, applied
     * after the armour rule, so every ratio the base game keeps (frigate to capital, kinetic to high explosive, alpha
     * to sustained) is kept: a fight between hulls only takes longer to wear through, plate first, sections giving way
     * and mounts going with the plate under them, rather than ending in a volley or two. Shields are untouched, so the
     * flux war and its overloads are the base game's. Nothing else that hurts a hull is touched: a bomb, a battery, a
     * ground round or an orbital strike lands at its own strength. {@code -Dbattle.warship.hullDurability=1} is the
     * control.
     */
    public static final float HULL_DURABILITY = Math.max(1f, Float.parseFloat(
            System.getProperty("battle.warship.hullDurability", "3")));

    /** Game units to cells, for reach, blasts and speeds. */
    public static float cells(float gameUnits) {
        return gameUnits * AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
    }

    /** Hull-spec pixels to cells, for a warship's own body: its extent, slots, nozzles and shield. */
    public static float hullCells(float hullPixels) {
        return cells(hullPixels) * HULL_PRESENCE;
    }
}
