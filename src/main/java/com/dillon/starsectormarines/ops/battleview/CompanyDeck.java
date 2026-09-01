package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineSoldier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The company ship's interior, as operations screens see it.
 *
 * <p>One deck, generated once from the hull and then answered from. The screens
 * that look at parts of the ship are cameras onto this, so a berthing screen and
 * the Mech Lab are framings of one interior rather than two rooms kept in step
 * by hand — and what one of them shows as damaged the other shows as damaged.
 *
 * <p><b>What the ship has is a question with a real answer.</b> A hull that
 * structurally cannot hold a vehicle bay does not acquire one by being refitted,
 * and a hull whose program owed a room the deck could not fit does not have that
 * room either. Both come back the same way here, because from the player's side
 * they are the same fact: there is no such place aboard. That is what a screen
 * consults before offering to take them to it.
 *
 * <p><b>One scene, one clock, held across page flips.</b> Not a scene per
 * screen, and emphatically not a scene rebuilt when the player opens a page.
 * The question a shared ship can answer and a per-screen one cannot is where
 * somebody <em>was</em>: flip from berthing to the lab and back, and the
 * technician who was walking to the parts cage should be at the parts cage,
 * because half a minute passed. Give each screen its own simulation and that
 * has no answer at all — the two pages are different ships — and freeze the
 * room the player is not looking at and the answer is "exactly where you left
 * them", which reads as a diorama rather than a crew.
 *
 * <p>So the ship is run by whoever is ticking the panel, not by the page that
 * happens to be up. Time aboard is a property of the ship.
 *
 * <p><b>The whole ship runs, not the part being looked at.</b> She is crewed to
 * her complement and every compartment ticks, whether or not any screen frames
 * it — a ship whose crew exist only where the camera is pointing cannot answer
 * the question above at all. The cost is not the reason to do less: a manned
 * capital transport is a few hundred hands and well under a millisecond a
 * frame, against a sixteen millisecond budget.
 *
 * <p><b>Getting her ready happens away from the frame.</b> Laying a capital's
 * deck out and crewing her is seconds of arithmetic, and doing it on the frame
 * that is drawing the shell is the difference between opening operations and
 * waiting for it. None of that work touches anything the game owns — it is our
 * own generator writing into its own grids — so it is done on another thread,
 * and the shell shows the rooms aboard her as being got ready until they are.
 *
 * <p>Two things are pinned to this thread on either side of that. Who is
 * aboard is read <em>before</em> the work is handed over, because the roster is
 * campaign state and the campaign is still running. Her renderer is built
 * <em>after</em> it comes back, because it reads sheets the game loaded and
 * those belong here.
 */
public final class CompanyDeck {

    private final CompanyShip ship;
    private final long seed;
    private final BattleSprites sprites;
    private final Supplier<List<MechVariant>> lance;
    private final Supplier<List<MarineSoldier>> company;
    /**
     * Whether this is the ship the company lives aboard, whose deck is laid out
     * once and kept. @see LaidDecks
     */
    private final boolean home;
    /** Soldier id to the marine standing aboard, for anyone the ship could billet. */
    private final Map<String, Long> mustered = new HashMap<>();
    /** Soldier id to the compartment their bunk is in. */
    private final Map<String, DeckGraph.Compartment> quarters = new HashMap<>();
    private MapResult deck;
    private DeckGraph rooms;
    private ShipDeckBattleScene scene;
    /** Her deck being laid out and her watch mustered, off the frame. @see #ready() */
    private CompletableFuture<ShipDeckBattleScene> gettingReady;
    /** What she is doing meanwhile; written where the work is and read here. */
    private volatile Work work = Work.LAYING_OUT;
    private float gettingReadySeconds;
    private float elapsedSeconds;

    /**
     * @param seed fixes the layout, so the ship a player leaves is the ship they
     *     come back to; it belongs to the company rather than to the session
     * @param sprites the ship's own sprite cache, or null for a headless caller;
     *     one per ship rather than one per screen, for the same reason there is
     *     one deck
     * @param lance the machines to park in her berths, read when the ship is
     *     first crewed; servicing work only exists while something is berthed
     * @param company the marines to muster into her berthing, read at the same
     *     moment; they are the ship's marine complement, so a ship given none
     *     has no marines aboard rather than anonymous ones
     */
    public CompanyDeck(CompanyShip ship, long seed, BattleSprites sprites,
                       Supplier<List<MechVariant>> lance,
                       Supplier<List<MarineSoldier>> company) {
        this(ship, seed, sprites, lance, company, false);
    }

    private CompanyDeck(CompanyShip ship, long seed, BattleSprites sprites,
                        Supplier<List<MechVariant>> lance,
                        Supplier<List<MarineSoldier>> company, boolean home) {
        if (ship == null) throw new IllegalArgumentException("a company ship is required");
        this.ship = ship;
        this.seed = seed;
        this.sprites = sprites;
        this.lance = lance == null ? List::of : lance;
        this.company = company == null ? List::of : company;
        this.home = home;
    }

    /**
     * The ship the company lives aboard.
     *
     * <p>Told apart from any other hull because hers is the one deck worth
     * keeping: the panel is built afresh every time the player opens it and
     * asks for the same ship every time, so she is laid out once for the whole
     * session rather than once per visit. Everything else about her is still
     * built fresh — she is crewed from the roster as it stands, so a watch is
     * never one the company no longer has.
     */
    public static CompanyDeck home(CompanyShip ship, long seed, BattleSprites sprites,
                                   Supplier<List<MechVariant>> lance,
                                   Supplier<List<MarineSoldier>> company) {
        return new CompanyDeck(ship, seed, sprites, lance, company, true);
    }

    /** A ship nobody will draw, nothing is berthed in, and nobody is billeted on. */
    public CompanyDeck(CompanyShip ship, long seed) {
        this(ship, seed, null, null, null, false);
    }

    /**
     * What a ship that is not ready yet is doing.
     *
     * <p>Two steps rather than a fraction, because the honest answer to "how
     * far along" is not available: half of laying a deck out is a circulation
     * pass that runs until no further cut earns itself, which has no total to
     * count against. What can be said truthfully is which of the two pieces of
     * work she is in, and how long it has been.
     */
    public enum Work {
        /** Her deck: the hull profile, the spine, the compartments, the passages. */
        LAYING_OUT,
        /** Her people: the machines into their berths, the company into the racks. */
        MUSTERING
    }

    public CompanyShip ship() {
        return ship;
    }

    /** @see Work */
    public Work work() {
        return work;
    }

    /**
     * Whether she is still being got ready — false for a hull nobody could live
     * in, which is not waiting for anything.
     */
    public boolean gettingReady() {
        return ship.habitable() && !ready();
    }

    /**
     * How long she has been got ready, in the shell's own clock rather than the
     * wall's: it counts the frames the player has actually been waiting.
     */
    public float gettingReadySeconds() {
        return gettingReadySeconds;
    }

    /**
     * Whether the ship has somewhere of this kind at all.
     *
     * <p>The question a room view asks before it offers itself. An uninhabitable
     * hull has nothing, which is the honest answer rather than an error: a
     * company quartered on a frigate has no rooms to walk around, and the
     * screens should say so by being unavailable.
     */
    public boolean has(RoomPurpose purpose) {
        if (purpose == null) return false;
        if (!ship.habitable()) return false;
        return rooms().largest(purpose) != null;
    }

    /**
     * The compartment this ship means by a purpose, or {@code null} when she has
     * none. Callers that have already asked {@link #has} may take it as present.
     */
    public DeckGraph.Compartment room(RoomPurpose purpose) {
        if (!ship.habitable()) return null;
        return rooms().largest(purpose);
    }

    /**
     * Every compartment of one purpose, ordered from fore to aft and then by
     * stable deck identity. Room screens use this when a hull carries more than
     * one facility of the same kind instead of silently trapping the camera in
     * whichever one happened to be largest.
     */
    public List<DeckGraph.Compartment> rooms(RoomPurpose purpose) {
        if (purpose == null || !ship.habitable()) return List.of();
        List<DeckGraph.Compartment> found = new ArrayList<>();
        for (DeckGraph.Compartment compartment : rooms().compartments()) {
            if (compartment.purpose() == purpose) found.add(compartment);
        }
        found.sort(Comparator.comparingInt(DeckGraph.Compartment::foreFrame)
                .thenComparingInt(DeckGraph.Compartment::id));
        return List.copyOf(found);
    }

    /**
     * The generated deck: grid, topology, fixtures, and everything standing on
     * it.
     *
     * @throws IllegalStateException if the hull has no playable interior; ask
     *     {@link CompanyShip#habitable} first, or {@link #has}, which answers
     *     for an uninhabitable ship without generating anything
     */
    public MapResult map() {
        generate();
        return deck;
    }

    /** The deck's compartments. @see #map() */
    public DeckGraph rooms() {
        generate();
        return rooms;
    }

    /** The ship's sprite cache, shared by every screen that draws her. */
    public BattleSprites sprites() {
        return sprites;
    }

    /**
     * The live ship: one simulation, crewed to her complement, that every room
     * view frames.
     *
     * <p>Built and manned on first call and then handed back unchanged, which is
     * the whole point — a screen that got a fresh scene would be looking at a
     * different crew each time the player walked in.
     */
    public ShipDeckBattleScene scene() {
        ensureSceneSprites();
        if (scene != null) return scene;
        if (!ship.habitable()) {
            throw new IllegalStateException(
                    "a " + ship.hullClass() + " has no interior to walk around");
        }
        // Somebody wants her now. Wait for the work rather than hand back
        // nothing: the routes to the pages that would have to cope with nothing
        // are closed until she is ready, so anybody arriving here has decided
        // they would rather wait.
        adopt(getReady().join());
        return scene;
    }

    /**
     * Whether she can be walked around yet, setting her being got ready going
     * if nobody has.
     *
     * <p>What the shell asks before it offers a way aboard, and what the panel
     * asks before it runs her. Answering no is not a failure — it is the whole
     * point, and it lasts as long as laying out a deck and mustering a watch
     * takes, which for a capital is seconds.
     */
    public boolean ready() {
        if (scene != null) return true;
        if (!ship.habitable()) return false;
        CompletableFuture<ShipDeckBattleScene> work = getReady();
        if (!work.isDone()) return false;
        adopt(work.join());
        return true;
    }

    /**
     * Lay her out and muster her watch, away from this thread.
     *
     * <p>The roster is read here rather than out there. Who is aboard is
     * campaign state, the campaign is still running, and a supplier called from
     * another thread would be reading the company while it changes.
     */
    private CompletableFuture<ShipDeckBattleScene> getReady() {
        if (gettingReady != null) return gettingReady;
        List<MechVariant> berthed = lance.get();
        List<MarineSoldier> roll = company.get();
        gettingReady = LaidDecks.off(() -> muster(berthed, roll));
        return gettingReady;
    }

    /** @see #getReady() */
    private ShipDeckBattleScene muster(List<MechVariant> berthed, List<MarineSoldier> roll) {
        work = Work.LAYING_OUT;
        generate();
        work = Work.MUSTERING;
        ShipDeckBattleScene manned = new ShipDeckBattleScene(deck, rooms, seed, null);
        manned.occupyGantries(berthed);
        // The company musters before the ship is crewed, so the berthing is
        // filled by marines who are on the roster rather than topped up with
        // hands who are not.
        musterCompany(manned, roll);
        manned.manDeck();
        return manned;
    }

    /**
     * Take delivery of the ship, and give her the means to draw herself.
     *
     * <p>On this thread, because a renderer reads sheets the game loaded.
     */
    private void adopt(ShipDeckBattleScene manned) {
        ensureSceneSprites();
        manned.attachRenderer(sprites);
        scene = manned;
    }

    /**
     * Load every sheet the deck's own scene can draw.
     *
     * <p>Here rather than on the screens, because <b>the ship is one scene and
     * the screens are only cameras onto it.</b> Each screen listing what it
     * expects to have in shot is a list that is right until somebody frames the
     * ship differently: the room views each loaded the ground and the crew, the
     * Mech Lab loaded the machines as well, and the whole-ship view — which sees
     * all of it at once — was written loading none of it and rendered a deck
     * with no floor and no people in it. The set belongs to the deck, which
     * knows what it contains, and is reached through the one door every screen
     * already comes through.
     *
     * <p>Every loader below is one-shot and the cache is the ship's, so calling
     * this per frame costs a handful of already-loaded checks.
     *
     * <p>Only what a deck actually paints. {@code INDOOR} plate resolves to the
     * urban sheet and a door threshold to the road sheet; the outdoor surfaces,
     * nature and water are ground a ship does not have.
     */
    private void ensureSceneSprites() {
        if (sprites == null) return;
        sprites.ensureTileSheet();
        sprites.ensureRoadSheet();
        sprites.ensureDoodadSheet();
        sprites.ensureLayeredUnitSprites();
        sprites.ensureLayeredMechSprites();
        sprites.ensureMarineSecondarySprites();
    }

    /**
     * Billet the company across every berthing the ship has, biggest first.
     *
     * <p>Every one of them, not just the compartment a screen would frame. The
     * marines aboard are the roster and no more, so a second bunkroom left out
     * of the muster would be filled by the general crewing pass with marines who
     * are on no muster roll - and the player would find strangers asleep in
     * their own ship.
     */
    private void musterCompany(ShipDeckBattleScene manned, List<MarineSoldier> roll) {
        List<MarineSoldier> remaining = roll == null ? List.of() : roll;
        for (DeckGraph.Compartment berthing : berthings()) {
            long[] billeted = manned.muster(berthing, remaining);
            int taken = 0;
            for (int index = 0; index < billeted.length; index++) {
                if (billeted[index] == 0L) continue;
                String soldier = remaining.get(index).id();
                mustered.put(soldier, billeted[index]);
                quarters.put(soldier, berthing);
                taken++;
            }
            remaining = remaining.subList(taken, remaining.size());
        }
    }

    /** The ship's berthing compartments, the largest first. */
    private List<DeckGraph.Compartment> berthings() {
        List<DeckGraph.Compartment> found = new ArrayList<>();
        for (DeckGraph.Compartment room : rooms().compartments()) {
            if (room.purpose() == RoomPurpose.BARRACKS) found.add(room);
        }
        found.sort(Comparator.comparingInt(DeckGraph.Compartment::area).reversed());
        return found;
    }

    /**
     * The marine standing aboard for this soldier, or zero for one the ship had
     * no billet for.
     *
     * <p>How a berthing screen connects the list it is showing to the people in
     * the room: the selected squad is not a subset of the scene, it is a subset
     * of the roster, and the join between the two is made here rather than
     * rediscovered by matching names.
     */
    public long marineFor(String soldierId) {
        Long aboard = mustered.get(soldierId);
        return aboard == null ? 0L : aboard;
    }

    /**
     * The berthing most of these marines bunk in, or the ship's principal one
     * when none of them came aboard.
     *
     * <p>Most rather than first: a squad straddles two bunkrooms when the muster
     * runs out of racks part way through it, and the room that is theirs is the
     * one holding more of them. A squad away on a stationing has nobody aboard
     * at all, and the ship's principal berthing stands in - a screen still has
     * to name and draw somewhere, and nowhere reads as a fault rather than as a
     * squad being elsewhere.
     */
    public DeckGraph.Compartment quartersFor(List<MarineSoldier> squad) {
        Map<DeckGraph.Compartment, Integer> tally = new HashMap<>();
        DeckGraph.Compartment best = null;
        int most = 0;
        for (MarineSoldier soldier : squad) {
            DeckGraph.Compartment berthing = quartersOf(soldier.id());
            if (berthing == null) continue;
            int held = tally.merge(berthing, 1, Integer::sum);
            if (held <= most) continue;
            most = held;
            best = berthing;
        }
        return best != null ? best : room(RoomPurpose.BARRACKS);
    }

    /**
     * The compartment this soldier's bunk is in, or {@code null} for one the
     * ship had no billet for.
     *
     * <p>Where they sleep rather than where they are. A screen framing a squad
     * wants the room that is theirs, and reading it off current positions would
     * have the camera follow whoever happened to be walking to the mess.
     */
    public DeckGraph.Compartment quartersOf(String soldierId) {
        return quarters.get(soldierId);
    }

    /** Whether the ship has been built and crewed yet. */
    public boolean live() {
        return scene != null;
    }

    /**
     * Run the ship on, crewing her if this is the first tick.
     *
     * <p>Called by the panel rather than by a screen, so the whole ship keeps
     * working while the player is reading a page that frames none of it. A
     * screen owning this is what makes the crew stop when nobody is watching,
     * and gating it on a room view having been opened is the same mistake one
     * step removed — the ship would then start when first looked at, so the
     * berthing screen a player opens first would always show a watch that had
     * just come on.
     */
    public void advance(float dt) {
        if (!(dt > 0f)) return;
        if (!ship.habitable()) return;
        // Not yet, and not waited for. A ship still being got ready has no
        // clock to run, and the frame this is called on is the one the player
        // is looking at.
        if (!ready()) {
            gettingReadySeconds += dt;
            return;
        }
        elapsedSeconds += dt;
        scene.advanceTo(elapsedSeconds);
    }

    /** How long the ship has been running since she was first looked at. */
    public float elapsedSeconds() {
        return elapsedSeconds;
    }

    /**
     * Put the ship away with the dialog she was being looked at through.
     *
     * <p>Owned here rather than by a screen: the room views come and go while
     * the player clicks around, and any of them closing her would end the
     * continuity the rest of them depend on.
     */
    public void dismiss() {
        if (scene != null) scene.close();
        scene = null;
        // Whatever was being got ready is let go rather than waited for. The
        // deck it was laying out is kept by LaidDecks either way, so the work
        // is not wasted even when its ship is.
        gettingReady = null;
        work = Work.LAYING_OUT;
        gettingReadySeconds = 0f;
        mustered.clear();
        quarters.clear();
        elapsedSeconds = 0f;
    }

    private synchronized void generate() {
        if (deck != null) return;
        if (!ship.habitable()) {
            throw new IllegalStateException(
                    "a " + ship.hullClass() + " has no interior to walk around");
        }
        if (home) {
            LaidDecks.Laid laid = LaidDecks.homeDeck(ship, seed);
            deck = laid.map();
            rooms = laid.rooms();
            return;
        }
        ShipDeckGenerator generator = new ShipDeckGenerator();
        deck = generator.generateDeck(ship.deckPlan(), seed, ship.outline());
        rooms = generator.getLastDeckGraph();
    }
}
