package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Render-tier ownership classification: how a unit relates to the person
 * <em>looking at the screen</em>, as opposed to which side it fights for.
 * {@link Faction} is the simulation's side-of-the-fight identity; allegiance is
 * the presentation reading of that identity from the player's chair, and it is
 * what ownership-coded decoration (durability bars today, tracers and highlights
 * later) styles against.
 *
 * <p>The two are deliberately separate nouns. The simulation may one day field
 * several mutually hostile factions, or place the camera behind a non-player
 * side; the number of visual ownership buckets a player can read at a glance
 * stays four either way. Keeping the mapping here means the sim never grows a
 * render concern and the render tier never hard-codes {@code Faction.MARINE}
 * into every decoration.
 *
 * <p><b>Current mapping.</b> The player's side is {@code MARINE} by standing
 * convention across the codebase (mission victory, deployment, fog contributor
 * set). {@code CIVILIAN} is the non-combatant bucket and reads {@link #NEUTRAL};
 * everything else reads {@link #ENEMY}. {@link #ALLY} has no producer yet — the
 * simulation has no friendly non-player faction — but it is a fully styled
 * classification so that the allied-faction work anticipated by
 * {@code fog-of-war-nouns.md} (law 2, allied contributors to player sight) has
 * nothing left to design on the presentation side. See
 * {@code battle-render-nouns.md}.
 */
public enum Allegiance {

    /** The player's own company — their marines, mechs, drones, and emplacements. */
    PLAYER,

    /** Friendly to the player but not under their command: client militia, garrison auxiliaries. */
    ALLY,

    /** Non-combatants who belong to neither side's fight. */
    NEUTRAL,

    /** Hostile to the player. */
    ENEMY;

    /** True for the two buckets the player should read as "on my side". */
    public boolean friendly() {
        return this == PLAYER || this == ALLY;
    }

    /**
     * The player's reading of a simulation faction. Never null — every
     * {@link Faction} resolves, and an unrecognized future faction reads
     * {@link #ENEMY} rather than silently styling as friendly.
     */
    public static Allegiance of(Faction faction) {
        if (faction == null) return ENEMY;
        return switch (faction) {
            case MARINE -> PLAYER;
            case CIVILIAN -> NEUTRAL;
            default -> ENEMY;
        };
    }
}
