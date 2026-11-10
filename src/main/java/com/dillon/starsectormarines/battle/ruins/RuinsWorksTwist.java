package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;

/**
 * How a ruins site's works bend round its vault prize ({@code ruins-expedition.md}, The vault and its prize): one rule
 * of the works changed per theme, so what the site was built round changes what the company does there, not only how
 * its rooms look. Each twist is built from the works' own parts (the hall, its junctions, the seal doors, the records)
 * and is read off the prize alone, so it is never stored.
 *
 * <p>Every twist keeps the works' law that nothing is locked for good: a door power cannot open can be forced, and a
 * door no charge breaks has its power.
 */
public enum RuinsWorksTwist {
    /** Plain works: no prize, or a theme with no twist. */
    NONE,
    /** A depot: every layer seals two side rooms as stores where it has them. */
    STACKED_STORES,
    /** An armory: the sealed stores are bunkers no charge breaks; only their power opens them. */
    BUNKERED_STORES,
    /** A server hall: the hall still runs when the company lands, so the vault has power and the batteries are awake. */
    LIVE_HALL,
    /** A factory floor: a junction thrown to its store locks there; the line does not run backwards. */
    INTERLOCKED_LINE,
    /** A frozen site: the hall's heat valves thaw slowly, so restarting it takes three times as long. */
    HEAT_VALVES,
    /** Ring tunnels: a powered seal door other than the vault's cycles, open for a while and shut for a while. */
    MAGNET_GATES,
    /** A shaft: the vault's seal hangs on the last junction's store side, against the last store. */
    SHAFT_FEED,
    /** Vats and greenhouses: growth jams the stores, so power never opens one; a charge forces it. */
    OVERGROWN_STORES,
    /** Reactor halls: the hall carries only the way on; a store is powered only by giving up a door onward. */
    COOLANT_LOAD,
    /** A refinery or tether anchor: forcing a seal door vents its pressure, as loud as restarting the hall. */
    PRESSURE_DOORS,
    /** A holosuite: its directory still answers, so the records are read when the company lands. */
    OPEN_DIRECTORY;

    /** How many times as long the hall takes to restart through {@link #HEAT_VALVES}. UNTUNED. */
    public static final float HEAT_VALVE_FACTOR = 3f;
    /** Seconds a {@link #MAGNET_GATES} door stands open in each cycle. UNTUNED. */
    public static final float MAGNET_OPEN_SECONDS = 20f;
    /** Seconds a {@link #MAGNET_GATES} door stands shut in each cycle. UNTUNED. */
    public static final float MAGNET_SHUT_SECONDS = 10f;

    /** The twist a prize gives its site's works; none for no prize. */
    public static RuinsWorksTwist of(RuinsVaultPrize prize) {
        return prize == null ? NONE : of(prize.theme());
    }

    /** The twist a theme gives its site's works. */
    public static RuinsWorksTwist of(RuinsVaultTheme theme) {
        if (theme == null) return NONE;
        return switch (theme) {
            case DEPOT -> STACKED_STORES;
            case ARMORY -> BUNKERED_STORES;
            case SERVER_HALL -> LIVE_HALL;
            case PRISTINE_NANOFORGE, CORRUPTED_NANOFORGE, DRONE_REPLICATOR -> INTERLOCKED_LINE;
            case CRYOARITHMETIC_ENGINE -> HEAT_VALVES;
            case SYNCHROTRON -> MAGNET_GATES;
            case MANTLE_BORE -> SHAFT_FEED;
            case SOIL_NANITES, BIOFACTORY_EMBRYO -> OVERGROWN_STORES;
            case FUSION_LAMP, PLASMA_DYNAMO, HYPERSHUNT_TAP -> COOLANT_LOAD;
            case CATALYTIC_CORE, FULLERENE_SPOOL -> PRESSURE_DOORS;
            case DEALMAKER_HOLOSUITE -> OPEN_DIRECTORY;
        };
    }

    /** How many side rooms of a layer are sealed as stores, given the works' own count. */
    public int stores(int plain, int rooms) {
        return this == STACKED_STORES ? Math.min(Math.max(plain, 2), rooms) : plain;
    }

    /** Whether a sealed store's door is a bunker no charge breaks. */
    public boolean hardenedStores() { return this == BUNKERED_STORES; }

    /** Whether power opens a sealed store's door. */
    public boolean powerOpensStores() { return this != OVERGROWN_STORES; }

    /** Whether the hall is running when the company lands. */
    public boolean hallStartsRunning() { return this == LIVE_HALL; }

    /** Whether the records are read when the company lands. */
    public boolean recordsStartRead() { return this == OPEN_DIRECTORY; }

    /** Seconds the hall's restart takes, given the works' own. */
    public float restartSeconds(float plain) {
        return this == HEAT_VALVES ? plain * HEAT_VALVE_FACTOR : plain;
    }

    /** Whether a junction may be thrown from {@code from} to {@code to}. */
    public boolean throwable(PowerGraph.Branch from, PowerGraph.Branch to) {
        return this != INTERLOCKED_LINE || from != PowerGraph.Branch.STORE || to == PowerGraph.Branch.STORE;
    }

    /** The hall's capacity, given the load of the whole way on and the works' own capacity. */
    public int capacity(int onward, int plain) {
        return this == COOLANT_LOAD ? Math.max(1, onward) : plain;
    }

    /** The vault's access, given the access the site drew and whether its seal may cross to the store side. */
    public VaultAccess access(VaultAccess drawn, boolean crossable) {
        return this == SHAFT_FEED && crossable ? VaultAccess.CROSSED_FEED : drawn;
    }

    /** Whether forcing a seal door is loud. */
    public boolean loudForcing() { return this == PRESSURE_DOORS; }

    /**
     * Whether a powered seal door (never the vault's) stands open at {@code seconds} into the battle: always, but
     * through {@link #MAGNET_GATES} only in the open part of its cycle.
     */
    public boolean openPhase(float seconds) {
        if (this != MAGNET_GATES) return true;
        return seconds % (MAGNET_OPEN_SECONDS + MAGNET_SHUT_SECONDS) < MAGNET_OPEN_SECONDS;
    }

    /** The string key of the twist's line on the works' row; null for none. */
    public String lineKey() {
        if (this == NONE) return null;
        StringBuilder key = new StringBuilder("ruinsTwist");
        for (String part : name().split("_")) key.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        return key.toString();
    }
}
