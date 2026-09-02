package com.dillon.starsectormarines.battle.unit;

/**
 * Side a unit fights for in a battle simulation. Marines are the player's own
 * company; defenders are whatever opposed force the mission generated (faction
 * garrison, pirate gang, etc). ALLY is a friendly force that is not the
 * player's — a client's militia, a patron's garrison auxiliaries — hostile to
 * the defender, friendly to the marines, and commanded by somebody other than
 * the player. CIVILIAN is the neutral bucket: non-combatants present on the map
 * (residents, lab techs, dockworkers) who don't count toward any side's
 * elimination objective. Tier comes from {@link UnitType}, not faction.
 *
 * <p><b>Hostility is a relation, read here and nowhere else.</b> Every site
 * that decides a target, an enemy, a friendly-fire concern, or a protected
 * non-target asks {@link #hostileTo} or {@link #friendlyTo} rather than
 * comparing identities. A chain of {@code != MARINE} tests answers the
 * two-sided question correctly and the three-sided one silently wrong — an
 * allied militia reads as an enemy to the player's own squads — so the chain is
 * not allowed to exist. {@link #hostile(byte, byte)} and
 * {@link #friendly(byte, byte)} are the same two answers off a precomputed
 * table, for the denormalised ordinal columns the spatial index keeps.
 *
 * <p>The relation in words: MARINE and ALLY are friendly to each other and
 * hostile to DEFENDER; DEFENDER is hostile to both; CIVILIAN is hostile to
 * nobody and nobody is hostile to it; and nothing is ever hostile to its own
 * faction. Marine and allied direct fire excludes civilian bodies from both
 * intended and incidental contacts; defender fire can still target or
 * incidentally hit them.
 *
 * <p>New values append. {@code CommanderService} pulses commanders in
 * declaration order and {@link UnitSpatialIndex} sizes its per-faction slices
 * from {@code values().length}, so inserting one in the middle would renumber
 * both.
 *
 * <p>Win condition (v1): last faction with surviving combatants wins.
 */
public enum Faction {
    MARINE,
    DEFENDER,
    CIVILIAN,
    ALLY;

    /** Row width of {@link #HOSTILE} / {@link #FRIENDLY}. */
    private static final int STRIDE;

    /** {@code HOSTILE[a * STRIDE + b]} — may {@code a} shoot at {@code b}. */
    private static final boolean[] HOSTILE;

    /** {@code FRIENDLY[a * STRIDE + b]} — is {@code b} one of {@code a}'s own. */
    private static final boolean[] FRIENDLY;

    static {
        Faction[] all = values();
        STRIDE = all.length;
        HOSTILE = new boolean[STRIDE * STRIDE];
        FRIENDLY = new boolean[STRIDE * STRIDE];
        for (Faction a : all) {
            for (Faction b : all) {
                int slot = a.ordinal() * STRIDE + b.ordinal();
                HOSTILE[slot] = computeHostile(a, b);
                FRIENDLY[slot] = computeFriendly(a, b);
            }
        }
    }

    /**
     * True when this faction fights {@code other} — the one question target
     * acquisition, an enemy scan, and an area weapon's exclusion list ask.
     * Never true of a faction against itself, and never true either way for
     * {@link #CIVILIAN}. A {@code null} other is not hostile: an id that
     * carries no identity is not a target.
     */
    public boolean hostileTo(Faction other) {
        return other != null && HOSTILE[ordinal() * STRIDE + other.ordinal()];
    }

    /**
     * True when {@code other} is one of this faction's own — the question a
     * friendly-fire attribution, a line-of-fire block, and a kill credit ask.
     * A faction is always friendly to itself; MARINE and ALLY are friendly to
     * each other. {@link #CIVILIAN} is friendly to nothing but itself, which is
     * deliberate: a civilian caught by a defender round is neither a squadmate
     * nor a legitimate kill, and the two sites that care about that difference
     * say so themselves.
     */
    public boolean friendlyTo(Faction other) {
        return other != null && FRIENDLY[ordinal() * STRIDE + other.ordinal()];
    }

    /**
     * {@link #hostileTo} by ordinal, for the denormalised {@code byte} faction
     * columns {@link UnitSpatialIndex} keeps beside its snapshot positions.
     * Out-of-range ordinals throw rather than answering.
     */
    public static boolean hostile(byte a, byte b) {
        return HOSTILE[a * STRIDE + b];
    }

    /** {@link #friendlyTo} by ordinal. See {@link #hostile(byte, byte)}. */
    public static boolean friendly(byte a, byte b) {
        return FRIENDLY[a * STRIDE + b];
    }

    private static boolean computeHostile(Faction a, Faction b) {
        if (a == b) return false;
        if (a == CIVILIAN || b == CIVILIAN) return false;
        return a == DEFENDER || b == DEFENDER;
    }

    private static boolean computeFriendly(Faction a, Faction b) {
        if (a == b) return true;
        return (a == MARINE && b == ALLY) || (a == ALLY && b == MARINE);
    }
}
