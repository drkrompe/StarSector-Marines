package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * What has already been laid out, kept for as long as the game is running.
 *
 * <p>Laying a deck out is the most expensive thing the operations shell does —
 * seconds of work for a capital — and the panel is built afresh every time the
 * player opens it. Without somewhere for the answers to live, the company's own
 * ship is generated from nothing on every visit and the fleet comparison lays
 * out the whole fleet again, having laid out exactly the same fleet a minute
 * ago.
 *
 * <p><b>Keyed on what a deck is generated from, never on which ship it is.</b>
 * A hull that has been refitted carries different crew and hold figures, owes a
 * different room program, and is therefore a different deck and misses. Battle
 * damage does not, because a hull is read as she would be whole — which is what
 * keeps a ship from being redesigned around a bad afternoon, and here means a
 * ship that came home shot up is still the ship we laid out.
 *
 * <p><b>Two things are kept, on very different terms.</b> What is aboard a hull
 * is a dozen counts, so it is kept for every hull the player has ever looked
 * over. A deck is megabytes of grid, so exactly one is kept — the ship the
 * company lives aboard, which is the one asked for again on every single visit.
 * Candidate hulls are laid out for the question and dropped.
 *
 * <p>Reusing a deck is only sound because running one leaves no mark on it:
 * nobody aboard the company ship shoots, so her floor, her doors, her walls and
 * her fixtures read identically after a watch has been living on them. A deck
 * that could be fought over would need a copy rather than the original.
 */
public final class LaidDecks {

    /**
     * How many hulls' worth of counts to keep before starting over.
     *
     * <p>A fleet is tens of ships and each entry is a handful of records, so
     * this is not a memory bound worth tuning — it is a bound at all, so that a
     * long session that loads several saves cannot accumulate readings of hulls
     * from games nobody is playing any more.
     */
    private static final int HULLS_REMEMBERED = 256;

    private static final Map<Hull, ShipInterior> ABOARD = new ConcurrentHashMap<>();

    /**
     * Where hulls are laid out, and how much of the machine that is allowed to
     * take.
     *
     * <p>Half the cores, and never the whole machine. The campaign is still
     * being drawn while this work runs, and a shell that opens instantly by
     * taking every core to do it has moved the stutter rather than removed it.
     * Deliberately not the common pool for the same reason — its parallelism is
     * every core but one, which on a small machine is every core the game has.
     *
     * <p>Daemon threads, so a hull still being laid out is never what keeps the
     * game from closing.
     */
    private static final Executor YARD = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            runnable -> {
                Thread hand = new Thread(runnable, "marine-ops-deck-layout");
                hand.setDaemon(true);
                hand.setPriority(Thread.NORM_PRIORITY - 2);
                return hand;
            });

    private static Hull homeHull;
    private static MapResult homeMap;
    private static DeckGraph homeRooms;

    private LaidDecks() { }

    /**
     * What a deck is generated from: the facts, not the ship.
     *
     * <p>The outline is held by name rather than by shape because that is what
     * identifies it — the same hull read twice gives two equal outlines that no
     * two objects would compare equal, and a key that never matches is a cache
     * that never hits.
     */
    private record Hull(HullClass hullClass, HullRole role, int minCrew, int maxCrew,
                        int cargo, float aspect, String outline, long seed) {

        static Hull of(CompanyShip ship, long seed) {
            return new Hull(ship.hullClass(), ship.role(), ship.minCrew(), ship.maxCrew(),
                    ship.cargo(), ship.aspect(),
                    ship.outline() == null ? null : ship.outline().source(), seed);
        }
    }

    /** A deck as it was generated, before anybody was aboard it. */
    record Laid(MapResult map, DeckGraph rooms) { }

    /**
     * What is aboard this hull if anybody has already looked, and null if
     * nobody has.
     *
     * <p>For a caller that would rather show the answer now than wait for one:
     * the fleet comparison fills in every hull it already knows before it puts
     * a single one down to be laid out.
     */
    public static ShipInterior known(CompanyShip ship, long seed) {
        return ship == null ? null : ABOARD.get(Hull.of(ship, seed));
    }

    /**
     * What is aboard this hull, laying her deck out only if nobody has asked
     * before.
     *
     * <p>Safe to call from several threads at once, which is how the fleet
     * comparison reads a fleet.
     */
    public static ShipInterior aboard(CompanyShip ship, long seed) {
        if (ship == null) throw new IllegalArgumentException("a ship is required");
        Hull hull = Hull.of(ship, seed);
        ShipInterior read = ABOARD.get(hull);
        if (read != null) return read;
        read = ShipInterior.of(ship, seed);
        remember(hull, read);
        return read;
    }

    /**
     * Record what is aboard a hull whose deck the caller had already laid out,
     * so the same hull is not laid out again to answer the same question.
     */
    public static void aboard(CompanyShip ship, long seed, ShipInterior read) {
        if (ship == null || read == null) return;
        remember(Hull.of(ship, seed), read);
    }

    private static void remember(Hull hull, ShipInterior read) {
        if (ABOARD.size() >= HULLS_REMEMBERED) ABOARD.clear();
        ABOARD.put(hull, read);
    }

    /**
     * The deck of the ship the company lives aboard, laid out once and kept
     * until she is no longer that ship.
     *
     * <p>One at a time on purpose. She is the hull asked for on every visit, so
     * keeping her is the difference between opening the panel and waiting for
     * it; every other hull is looked at once and would only be evicting her.
     */
    static synchronized Laid homeDeck(CompanyShip ship, long seed) {
        Hull hull = Hull.of(ship, seed);
        if (hull.equals(homeHull) && homeMap != null) return new Laid(homeMap, homeRooms);
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult map = generator.generateDeck(ship.deckPlan(), seed, ship.outline());
        homeHull = hull;
        homeMap = map;
        homeRooms = generator.getLastDeckGraph();
        return new Laid(map, homeRooms);
    }

    /** Whether this hull's deck is the one being kept. */
    static synchronized boolean isHomeDeck(CompanyShip ship, long seed) {
        return ship != null && homeMap != null && Hull.of(ship, seed).equals(homeHull);
    }

    /**
     * Do this away from the frame.
     *
     * <p>Everything laying a deck out touches is either ours or immutable, so
     * the only rule is the one the callers keep: whatever reads the campaign or
     * the game's assets is read before the work is handed over, and whatever
     * draws is done after it comes back.
     */
    public static <T> CompletableFuture<T> off(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, YARD);
    }

    /**
     * Forget everything. For a caller that is measuring what laying out costs,
     * which a kept answer would otherwise report as free.
     */
    public static synchronized void forget() {
        ABOARD.clear();
        homeHull = null;
        homeMap = null;
        homeRooms = null;
    }
}
