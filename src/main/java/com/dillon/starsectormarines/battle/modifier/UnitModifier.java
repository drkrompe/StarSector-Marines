package com.dillon.starsectormarines.battle.modifier;

import java.util.Set;

/**
 * One named source of change to a unit's stats: being dug in, a running suit system, an officer's
 * specialty. A modifier reads the state that makes it true and never writes to the unit, so it
 * holds exactly as long as its condition does.
 *
 * <p>Every modifier has a name the player can read, because a squad's fighting quality must be
 * determined by what the player can see ({@code progression-nouns.md}); {@link
 * UnitModifiers#explain} lists the ones applying to a unit.
 */
public interface UnitModifier {

    /**
     * The name the player reads for this modifier on {@code unit}: a {@code strings.json} key or
     * text, as {@code Strings.resolve} accepts. Per unit, because a suit system is named by the
     * suit that carries it.
     */
    String name(long unit);

    /** The stats this modifier can change. It is never asked about any other. */
    Set<UnitStat> stats();

    /** Whether it applies to {@code unit} right now. */
    boolean appliesTo(long unit);

    /** Added to the base before multipliers, for a unit it {@linkplain #appliesTo applies to}. */
    default float flat(long unit, UnitStat stat) {
        return 0f;
    }

    /** Multiplies the stat after flat contributions, for a unit it applies to. */
    default float multiplier(long unit, UnitStat stat) {
        return 1f;
    }
}
