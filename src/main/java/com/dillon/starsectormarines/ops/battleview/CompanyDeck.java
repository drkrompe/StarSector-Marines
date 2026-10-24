package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.ObjLongConsumer;
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
    /**
     * What the deck draws with: the set handed in, or, for a deck {@linkplain #leasedFrom leased}
     * from the ledger, the set its current lease loads, which is null while it holds none.
     */
    private BattleSprites sprites;
    /** Where a leased deck takes its textures from; null when its sprites were handed in. */
    private TextureLedger ledger;
    /** What the deck holds while the dialog is open and no battle is. @see #releaseTextures() */
    private TextureLease lease;
    /** The textures her scene draws, preloaded a slice a frame. @see #preloadTextures */
    private TextureLoadQueue loading;
    /**
     * The files the company's deck held when she last gave her textures back: there is one company
     * deck, and a deck laid out afresh in another dialog, or another game session, draws much the
     * same art. Loaded back a texture a frame before her loaders run, so only the first visit after
     * an install has to find out what she draws. Kept in {@link #HELD_FILE} in the game's common
     * folder; null until read. GL thread only.
     */
    private static List<String> lastHeld;
    static final String HELD_FILE = "marines_company_deck_textures.txt";
    /** A preload step longer than this held a frame the player saw; logged in the dev build. */
    private static final long SLOW_STEP_NANOS = 16_000_000L;
    private long slowStepNanos = SLOW_STEP_NANOS;
    private ObjLongConsumer<TextureLoadQueue.Step> slowStep = (step, nanos) -> {
        if (DevConfig.DEV_MODE) {
            Global.getLogger(CompanyDeck.class).info("company deck textures: preloading " + step.stage()
                    + " held the frame " + nanos / 1_000_000L + " ms");
        }
    };
    private final Supplier<List<MechVariant>> lance;
    private final Supplier<List<MarineSoldier>> company;
    /** Which of her boat berths the company still has a boat for. @see #getReady() */
    private final Supplier<boolean[]> boatsHeld;
    private final Supplier<boolean[]> boatWrecks;
    private Supplier<boolean[]> groundedBoats = () -> null;

    /** Snapshot on the UI thread before preparing the scene. */
    public CompanyDeck groundedBoats(Supplier<boolean[]> grounded) {
        if (gettingReady != null) throw new IllegalStateException("deck preparation already started");
        groundedBoats = grounded;
        return this;
    }

    /** The door guns on each of her boats, mount by mount; read on this thread, as she is adopted and on each {@link #refitDoorGuns()}. */
    private Supplier<String[][]> doorGuns = () -> null;

    public CompanyDeck doorGuns(Supplier<String[][]> guns) {
        doorGuns = guns == null ? () -> null : guns;
        return this;
    }

    /** The company's vehicles, parked in the berths after the lance; read on this thread, as the lance is. */
    private Supplier<List<VehicleDeploymentSpec>> vehicles = List::of;

    public CompanyDeck vehicles(Supplier<List<VehicleDeploymentSpec>> parked) {
        vehicles = parked == null ? List::of : parked;
        return this;
    }

    /**
     * Puts the company's door guns on her parked boats again, after a refit.
     *
     * <p>Nothing to do before she is ready: adopting her reads them.
     */
    public void refitDoorGuns() {
        if (scene != null) scene.fitDoorGuns(doorGuns.get());
    }

    /**
     * Owns her textures the way a battle does ({@code battle-textures.md}): a lease from
     * {@code textures}, loading only what her scene shows, preloaded while she is off screen and
     * given back by {@link #releaseTextures()} when a battle starts or the dialog closes.
     */
    public CompanyDeck leasedFrom(TextureLedger textures) {
        if (sprites != null) throw new IllegalStateException("this deck draws with the sprites it was handed");
        ledger = textures;
        return this;
    }

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
    /**
     * Which cores' Deficit dressing the deck shows. Presentation only, so it is
     * changed on the scene in place rather than by laying the ship out again.
     */
    private volatile CoreDressing dressing;
    /** Which facility tiers fitted into existing rooms the deck shows; presentation only, like {@link #dressing}. */
    private volatile FacilityDressing facilities = FacilityDressing.NONE;
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
     * @param boatsHeld which of her boat berths the company still has a boat
     *     for, in the Boat Deck's own berth order, or null for a hull whose
     *     every berth is held
     */
    public CompanyDeck(CompanyShip ship, long seed, BattleSprites sprites,
                       Supplier<List<MechVariant>> lance,
                       Supplier<List<MarineSoldier>> company,
                       Supplier<boolean[]> boatsHeld) {
        this(ship, seed, sprites, lance, company, boatsHeld, null, false);
    }

    private CompanyDeck(CompanyShip ship, long seed, BattleSprites sprites,
                        Supplier<List<MechVariant>> lance,
                        Supplier<List<MarineSoldier>> company,
                        Supplier<boolean[]> boatsHeld, Supplier<boolean[]> boatWrecks, boolean home) {
        if (ship == null) throw new IllegalArgumentException("a company ship is required");
        this.ship = ship;
        this.seed = seed;
        this.sprites = sprites;
        this.lance = lance == null ? List::of : lance;
        this.company = company == null ? List::of : company;
        this.boatsHeld = boatsHeld == null ? () -> null : boatsHeld;
        this.boatWrecks = boatWrecks == null ? () -> null : boatWrecks;
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
                                   Supplier<List<MarineSoldier>> company,
                                   Supplier<boolean[]> boatsHeld) {
        return new CompanyDeck(ship, seed, sprites, lance, company, boatsHeld, null, true);
    }

    public static CompanyDeck home(CompanyShip ship, long seed, BattleSprites sprites,
                                   Supplier<List<MechVariant>> lance, Supplier<List<MarineSoldier>> company,
                                   Supplier<boolean[]> boatsHeld, Supplier<boolean[]> wrecks) {
        return new CompanyDeck(ship, seed, sprites, lance, company, boatsHeld, wrecks, true);
    }

    /** A ship nobody will draw, nothing is berthed in, and nobody is billeted on. */
    public CompanyDeck(CompanyShip ship, long seed) {
        this(ship, seed, null, null, null, null, null, false);
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
        return rooms().compartments().stream().anyMatch(r -> r.purpose() == purpose && !rooms().damaged(r.id()));
    }

    /**
     * The compartment this ship means by a purpose, or {@code null} when she has
     * none. Callers that have already asked {@link #has} may take it as present.
     */
    public DeckGraph.Compartment room(RoomPurpose purpose) {
        if (!ship.habitable()) return null;
        if (purpose == RoomPurpose.BARRACKS) return rooms().compartments().stream()
                .filter(room -> room.purpose() == purpose && !rooms().damaged(room.id()))
                .max(Comparator.comparingInt(DeckGraph.Compartment::area)).orElse(null);
        return rooms().largest(purpose);
    }

    /**
     * Every compartment of one purpose, ordered from fore to aft, port before
     * starboard for rooms abreast, and then by stable deck identity. Room screens use this when a hull carries more than
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
                .thenComparing(DeckGraph.Compartment::side)
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

    /**
     * The ship's sprite cache, shared by every screen that draws her. A leased deck opens its
     * lease here, so what a screen loads through it is given back with the deck's own textures.
     * Not to be kept: a release drops it, and the next call hands out a new one.
     */
    public BattleSprites sprites() {
        openTextures();
        return sprites;
    }

    /**
     * Her scene, ready to be drawn: crewed, and with every texture it paints on the card. Null
     * only while she is not live yet. A deck screen is never shown a blank or a loading screen:
     * her art is normally preloaded by then ({@link #preloadTextures}), and whatever is left is
     * finished here, at once.
     */
    public ShipDeckBattleScene drawable() {
        if (scene == null) return null;
        if (ledger == null) {
            ensureSceneSprites();
            return scene;
        }
        preloadTextures(Long.MAX_VALUE);
        scene.attachRenderer(sprites);
        return scene;
    }

    /**
     * Loads her art for up to {@code budgetNanos}, for a frame on which she is not drawn: the
     * panel calls this every frame the dialog is open outside a battle, so a deck screen opened
     * later finds it on the card. Nothing to do before she is live, or once it is all loaded.
     * A step is never split, so one larger than the budget runs alone in its frame.
     */
    public void preloadTextures(long budgetNanos) {
        if (ledger == null || scene == null) return;
        openTextures();
        if (loading == null) loading = new TextureLoadQueue(sceneTextureSteps()).onSlowStep(slowStepNanos, slowStep);
        if (loading.done()) return;
        loading.advance(budgetNanos);
        if (loading.done() && DevConfig.DEV_MODE) {
            Global.getLogger(CompanyDeck.class).info("company deck textures: loaded; the deck holds "
                    + megabytes(lease.bytesHeld()) + " MB, " + megabytes(ledger.residentBytes())
                    + " MB of mod textures resident in all");
        }
    }

    /** Reports every preload step longer than {@code thresholdNanos}: a frame the player would feel. */
    public CompanyDeck onSlowLoadStep(long thresholdNanos, ObjLongConsumer<TextureLoadQueue.Step> listener) {
        slowStepNanos = thresholdNanos;
        slowStep = listener;
        return this;
    }

    /** Whether her art is all on the card, so a deck screen opens without loading anything. */
    public boolean texturesLoaded() {
        return ledger == null || (loading != null && loading.done());
    }

    /**
     * Gives back every texture her scene loaded, for when a battle starts or the dialog closes
     * ({@code battle-textures.md}). Her renderer goes first, with every GL resource it made, and
     * her sprite set with the lease; the ship herself keeps running. The next
     * {@link #preloadTextures} or {@link #drawable()} opens a new lease and loads again.
     */
    public void releaseTextures() {
        if (lease == null) return;
        if (scene != null) scene.detachRenderer();
        rememberHeld(lease.paths());
        TextureLedger.Release release = lease.release();
        lease = null;
        sprites = null;
        loading = null;
        if (DevConfig.DEV_MODE) {
            Global.getLogger(CompanyDeck.class).info("company deck textures: released " + release.unloaded()
                    + " textures, " + megabytes(release.bytesFreed()) + " MB freed (" + release.kept()
                    + " kept, still held by other screens or the game's own); "
                    + megabytes(ledger.residentBytes()) + " MB of mod textures resident after");
        }
    }

    /**
     * Hands this deck's lease and sprite set to {@code successor}, the deck laid out in her place,
     * so a ship laid out again keeps what is already on the card rather than unloading it and
     * loading it straight back. What the successor's scene does not draw stays held until it
     * releases.
     */
    public void handTexturesTo(CompanyDeck successor) {
        if (lease == null || successor == null || successor.ledger != ledger || successor.lease != null) return;
        if (scene != null) scene.detachRenderer();
        successor.lease = lease;
        successor.sprites = sprites;
        lease = null;
        sprites = null;
        loading = null;
    }

    /** Whether a leased deck holds textures now. */
    public boolean holdsTextures() {
        return lease != null;
    }

    private static List<String> lastHeld() {
        if (lastHeld == null) {
            lastHeld = List.of();
            try {
                SettingsAPI settings = Global.getSettings();
                if (settings != null && settings.fileExistsInCommon(HELD_FILE)) {
                    List<String> read = new ArrayList<>();
                    for (String line : settings.readTextFileFromCommon(HELD_FILE).split("\n")) {
                        if (!line.isBlank()) read.add(line.trim());
                    }
                    lastHeld = List.copyOf(read);
                }
            } catch (Exception unreadable) {
                // A first visit, then: she surveys what she draws.
            }
        }
        return lastHeld;
    }

    private static void rememberHeld(Collection<String> paths) {
        lastHeld = List.copyOf(paths);
        try {
            Global.getSettings().writeTextFileToCommon(HELD_FILE, String.join("\n", lastHeld));
        } catch (Exception unwritable) {
            // Remembered for this session only.
        }
    }

    private void openTextures() {
        if (ledger == null || lease != null) return;
        lease = ledger.lease("company deck");
        sprites = new BattleSprites(lease);
        loading = null;
    }

    private static long megabytes(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
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
        if (ledger == null) ensureSceneSprites();
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
     * another thread would be reading the company while it changes. Which
     * berths still hold a boat is campaign state on exactly the same terms, so
     * it is read on this thread too.
     */
    private CompletableFuture<ShipDeckBattleScene> getReady() {
        if (gettingReady != null) return gettingReady;
        List<MechVariant> berthed = lance.get();
        List<VehicleDeploymentSpec> parked = vehicles.get();
        List<MarineSoldier> roll = company.get();
        boolean[] held = boatsHeld.get();
        boolean[] wrecks = boatWrecks.get();
        boolean[] grounded = groundedBoats.get();
        gettingReady = LaidDecks.off(() -> muster(berthed, parked, roll, held, wrecks, grounded));
        return gettingReady;
    }

    /** @see #getReady() */
    private ShipDeckBattleScene muster(List<MechVariant> berthed, List<VehicleDeploymentSpec> parked,
                                       List<MarineSoldier> roll,
                                       boolean[] held, boolean[] wrecks, boolean[] grounded) {
        work = Work.LAYING_OUT;
        generate();
        work = Work.MUSTERING;
        ShipDeckBattleScene manned = new ShipDeckBattleScene(deck, rooms, seed, null, held, wrecks, grounded);
        manned.dress(dressing == null ? CoreDressing.of(ship) : dressing);
        manned.dress(facilities);
        manned.occupyGantries(berthed);
        manned.parkVehicles(berthed.size(), parked);
        // The company musters before the ship is crewed, so the berthing is
        // filled by marines who are on the roster rather than topped up with
        // hands who are not.
        musterCompany(manned, roll);
        manned.manDeck(Integer.MAX_VALUE, ship.minCrew());
        return manned;
    }

    /**
     * Take delivery of the ship, and give her the means to draw herself.
     *
     * <p>On this thread, because a renderer reads sheets the game loaded.
     */
    private void adopt(ShipDeckBattleScene manned) {
        scene = manned;
        // A leased deck loads when a screen draws her (#drawable), not when she is ready.
        if (ledger == null) {
            ensureSceneSprites();
            manned.attachRenderer(sprites);
        }
        if (dressing != null) scene.dress(dressing);
        scene.dress(facilities);
        scene.fitDoorGuns(doorGuns.get());
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
     * nature stays outside the hull. Gym pool water uses a registered ship material
     * sheet loaded with the other custom room art.
     */
    void ensureSceneSprites() {
        if (sprites == null) return;
        for (TextureLoadQueue.Step step : sceneTextureSteps()) step.work().run();
    }

    /**
     * {@link #ensureSceneSprites()} as steps, so a leased deck can load across frames. The
     * families are the ones her crew and company wear as they stand
     * ({@link BattleTextureSet#onField}); a body she later shows in a family not named here loads
     * on its first draw. Mechs are the unpainted set alone: nothing aboard binds a livery to a
     * side.
     */
    private List<TextureLoadQueue.Step> sceneTextureSteps() {
        BattleSprites s = sprites;
        List<TextureLoadQueue.Step> steps = new ArrayList<>();
        if (s == null) return steps;
        BattleTextureSet set = scene != null ? BattleTextureSet.onField(scene.simulation()) : null;
        // What she held last time, a texture a step, so loading her again after a battle or in a
        // dialog opened again costs no frame more than one texture; the loaders below then find
        // it all on the card.
        TextureLease held = lease;
        List<String> lastHeld = lastHeld();
        for (String path : lastHeld) {
            steps.add(new TextureLoadQueue.Step("held " + path, () -> {
                try {
                    held.acquire(path);
                } catch (IOException missing) {
                    // The loaders below report it, as they would have the first time.
                }
            }));
        }
        steps.add(new TextureLoadQueue.Step("urban sheet", s::ensureUrbanSheet));
        // Only the catalog sheets her deck places; another loads on its first draw.
        Set<String> placed = scene != null ? scene.terrainSheets() : Set.of();
        Set<String> heldBefore = new HashSet<>(lastHeld);
        for (String path : s.terrainSheetPaths()) {
            if (!placed.contains(path) && !heldBefore.contains(ModTextures.file(path))) continue;
            steps.add(new TextureLoadQueue.Step("terrain " + path, () -> s.ensureTerrainSheet(path)));
        }
        steps.add(new TextureLoadQueue.Step("road sheet", s::ensureRoadSheet));
        steps.add(new TextureLoadQueue.Step("doodad sheets", s::ensureDoodadSheet));
        steps.add(new TextureLoadQueue.Step("infantry shared layers", s::ensureInfantryShared));
        if (set != null) {
            for (LayeredArmorFamily family : set.families()) {
                steps.add(new TextureLoadQueue.Step("family " + family, () -> s.ensureLayeredFamily(family)));
            }
            if (set.ravRigs()) steps.add(new TextureLoadQueue.Step("rav rigs", s::ensureRavRigs));
            if (!set.vehicles().isEmpty()) {
                steps.add(new TextureLoadQueue.Step("vehicles", () -> s.ensureConvoySprites(Set.of())));
                for (VehicleType type : set.vehicles()) {
                    steps.add(new TextureLoadQueue.Step("vehicle " + type, () -> s.loadConvoySprite(type)));
                }
            }
        }
        steps.add(new TextureLoadQueue.Step("mech set", () -> s.ensureLayeredMechSprites(Set.of())));
        steps.add(new TextureLoadQueue.Step("projectiles", s::ensureMarineSecondarySprites));
        steps.add(new TextureLoadQueue.Step("aircraft", s::ensureAirframeSprites));
        // The guns on her parked boats: a gun fitted at the Boat Deck stands on its hull.
        steps.add(new TextureLoadQueue.Step("door guns", s::ensureTurretSprites));
        // Her renderer too, on a frame she is not drawn, so a deck screen's first frame is an
        // ordinary one.
        ShipDeckBattleScene drawn = scene;
        if (ledger != null && drawn != null) {
            List<TextureLoadQueue.Step> finish = List.of(
                    new TextureLoadQueue.Step("renderer", () -> drawn.attachRenderer(s)),
                    // Loads anything still missing before a deck screen can, which is nothing once
                    // the survey or the last visit has named it.
                    new TextureLoadQueue.Step("prewarm", drawn::prewarm));
            if (!lastHeld.isEmpty()) {
                steps.addAll(finish);
            } else {
                // Her first visit this session: what the whole ship would ask for that the set
                // above did not name (damage variants, wear, dressing) is surveyed, then loaded a
                // sheet a step, ahead of the renderer that draws her.
                steps.add(new TextureLoadQueue.Step("survey", () -> {
                    List<TextureLoadQueue.Step> more = new ArrayList<>();
                    for (String path : drawn.surveyTerrain(s)) {
                        more.add(new TextureLoadQueue.Step("surveyed " + path, () -> s.ensureTerrainSheet(path)));
                    }
                    more.addAll(finish);
                    loading.append(more);
                }));
            }
        }
        return steps;
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
            if (room.purpose() == RoomPurpose.BARRACKS && !rooms().damaged(room.id())) found.add(room);
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

    /**
     * Dress the deck for the ship's current core condition: each core's Deficit
     * look shown or cleared. Applied to the running scene at once, or to the
     * scene when it is ready.
     */
    public void dress(CoreDressing state) {
        dress(state, 0f);
    }

    /**
     * Dress the deck for a new core condition over {@code seconds} of the
     * deck's own clock, as a wave from each core's source room
     * ({@link ShipDeckBattleScene#dress(CoreDressing, float)}). A scene not
     * yet built simply starts in the new condition.
     */
    public void dress(CoreDressing state, float seconds) {
        dressing = state;
        if (scene != null) scene.dress(state, seconds);
    }

    /** Whether a dressing wave is still playing on the running deck. */
    public boolean dressingWave() {
        return scene != null && scene.dressing();
    }

    /** How far the running deck's dressing wave has come, 0 to 1; 1 when none is playing. */
    public float dressingProgress() {
        return scene == null ? 1f : scene.dressingProgress();
    }

    /**
     * Dress the rooms that carry a facility for the tiers bought: grow racks in
     * the provisions store, the wardroom and briefing ends of the mess, racking
     * in the hold. Applied in place, like the cores' dressing.
     */
    public void dress(FacilityDressing state) {
        facilities = state == null ? FacilityDressing.NONE : state;
        if (scene != null) scene.dress(facilities);
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
        releaseTextures();
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
