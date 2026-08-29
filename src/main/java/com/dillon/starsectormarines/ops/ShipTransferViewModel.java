package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.InteriorChange;
import com.dillon.starsectormarines.ops.battleview.LaidDecks;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The fleet as places the company could live, and what moving would do to them.
 *
 * <p>Every candidate is read as a generated deck, because the question is not
 * how large a hull is but what would be aboard her. A ship's plan and her
 * facility counts come from one generation apiece, held once she has been read.
 *
 * <p>That makes opening this screen a whole fleet's worth of layout work, and
 * a capital's deck is seconds of it. <b>None of it happens on the frame the
 * player is waiting on.</b> The screen opens on a list that says which hulls it
 * is still laying out, the hulls go down across every core at hand, and each
 * row fills in as its deck lands — so a fleet with two capitals in it costs the
 * same to open as a fleet of frigates.
 *
 * <p>The ship on the stage is laid out first, because hers is the plan being
 * drawn and the one the player is waiting to see. Hers is also the only deck
 * kept: a deck is megabytes of grid, and the rest are read for what is aboard
 * them and dropped.
 *
 * <p>The fleet itself is read once rather than once per question asked about
 * it, and a hull is laid out once rather than once for her plan and again for
 * her facility counts. What was aboard a hull outlives the screen entirely —
 * see {@link com.dillon.starsectormarines.ops.battleview.LaidDecks} — so the
 * second visit to this page costs nothing for every ship the first one read.
 *
 * <p>The comparison is always against where they live now. A screen that rated
 * hulls in the abstract would be a datasheet; the player is asking whether to
 * move, and the answer is a difference.
 *
 * <p>Where the ships come from is injected rather than reached for, so the same
 * rows, the same cells and the same wording can be produced from a real fleet
 * and from a fixture. Evidence drawn any other way would be evidence of a
 * screen nobody uses.
 */
public final class ShipTransferViewModel {

    /** Losses named on one row of the fleet list before the rest are counted. */
    private static final int ROW_LOSSES = 2;

    /** The places a company weighs a hull on, in the order they are read. */
    private static final RoomPurpose[] COMPARED = {
            RoomPurpose.BARRACKS, RoomPurpose.VEHICLE_BAY, RoomPurpose.ARMORY,
            RoomPurpose.FIRING_RANGE, RoomPurpose.HANGAR, RoomPurpose.PATIENT_WARD,
            RoomPurpose.MESS_HALL, RoomPurpose.STOCKROOM };

    /**
     * One ship the company could live aboard.
     *
     * @param id whatever identifies her to the caller; also seeds her deck
     * @param lift the hands she carries beyond the ones needed to fly her
     */
    public record Candidate(String id, String name, String designation,
                            int lift, int cargo, CompanyShip ship) {
        public Candidate {
            if (id == null) throw new IllegalArgumentException("a ship needs an identity");
            if (ship == null) throw new IllegalArgumentException("a candidate needs a hull");
        }

        /** Whether a company could be based aboard her at all. */
        public boolean quarters() {
            return ship.hullClass().quarters();
        }
    }

    private final Supplier<List<Candidate>> fleet;
    private final Supplier<CompanyShipDesignation.Home> home;
    private final Consumer<String> moveAboard;
    private final CompanyMeans means;
    private final Map<String, ShipInterior> interiors = new HashMap<>();
    /** Hulls being laid out off this thread, by ship id. @see #advance() */
    private final Map<String, CompletableFuture<Laid>> laying = new LinkedHashMap<>();
    /** The ship the plan view draws — the one hull whose deck is kept. */
    private String stagedShipId;
    private CompanyDeck staged;
    /** The fleet as it stood when this screen last read it. @see #fleet() */
    private List<Candidate> ships;

    /** A hull laid out: what is aboard her, and her deck where it is being kept. */
    private record Laid(ShipInterior interior, CompanyDeck deck) {}
    private final MutableSignal<String> selectedShipId;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<CandidateRow>> candidateRows;
    private final ComputedSignal<List<FacilityCell>> facilityCells;
    private final ComputedSignal<String> selectedName;
    private final ComputedSignal<String> selectedSummary;
    private final ComputedSignal<String> verdict;
    private final ComputedSignal<String> transferLabel;
    private final ComputedSignal<String> transferClasses;
    private final ComputedSignal<String> costLabel;
    private final ComputedSignal<String> costClasses;
    private final ComputedSignal<String> roomTitle;
    private final ComputedSignal<String> roomCopy;
    private final ComputedSignal<String> contextLabel;

    /** The player's own fleet, company and purse. */
    public ShipTransferViewModel(Reactor reactor) {
        this(reactor, ShipTransferViewModel::playerFleet,
                CompanyShipDesignation::home,
                ShipTransferViewModel::designate,
                CompanyMeans.ofPlayer());
    }

    public ShipTransferViewModel(Reactor reactor, Supplier<List<Candidate>> fleet,
                                 Supplier<CompanyShipDesignation.Home> home,
                                 Consumer<String> moveAboard, CompanyMeans means) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (fleet == null) throw new IllegalArgumentException("a fleet is required");
        if (home == null) throw new IllegalArgumentException("a home is required");
        if (moveAboard == null) throw new IllegalArgumentException("a transfer is required");
        if (means == null) throw new IllegalArgumentException("a company is required");
        this.fleet = fleet;
        this.home = home;
        this.moveAboard = moveAboard;
        this.means = means;
        revision = reactor.signal(0);
        selectedShipId = reactor.signal(home.get().shipId());
        candidateRows = reactor.computed(this::buildCandidateRows);
        facilityCells = reactor.computed(this::buildFacilityCells);
        selectedName = reactor.computed(() -> {
            revision.get();
            Candidate ship = selected();
            return ship == null ? "No ship" : ship.name();
        });
        selectedSummary = reactor.computed(this::buildSelectedSummary);
        verdict = reactor.computed(this::buildVerdict);
        transferLabel = reactor.computed(() -> {
            revision.get();
            Candidate ship = selected();
            if (isHome(ship)) return "THE COMPANY LIVES HERE";
            if (ship != null && !ship.quarters()) return "NOT A COMPANY BERTH";
            return unquartered() ? "QUARTER THE COMPANY HERE" : "MOVE THE COMPANY ABOARD";
        });
        transferClasses = reactor.computed(() -> {
            revision.get();
            Candidate ship = selected();
            if (isHome(ship)) return "transfer-commit current";
            if (ship != null && !ship.quarters()) return "transfer-commit unaffordable";
            return affordable() ? "transfer-commit" : "transfer-commit unaffordable";
        });
        costLabel = reactor.computed(this::buildCostLabel);
        costClasses = reactor.computed(() -> {
            revision.get();
            return "label transfer-cost" + (affordable() ? " tone-muted" : " short");
        });
        roomTitle = reactor.computed(() -> {
            revision.get();
            if (home.get().displaced()) return "COMPANY SHIP  //  DISPLACED";
            return unquartered() ? "COMPANY SHIP  //  FOUNDING"
                    : "COMPANY SHIP  //  TRANSFER";
        });
        // Named for the fleet rather than a compartment: this is the only page
        // in the shell that is not aboard anything in particular.
        contextLabel = reactor.computed(() -> {
            revision.get();
            if (home.get().displaced()) return "COMPANY FLEET / DISPLACED";
            return unquartered() ? "COMPANY FLEET / FOUNDING" : "COMPANY FLEET / TRANSFER";
        });
        roomCopy = reactor.computed(() -> {
            revision.get();
            CompanyShipDesignation.Home standing = home.get();
            if (standing.displaced()) {
                return standing.formerShipName() + gone(standing)
                        + "The company needs somewhere to live.";
            }
            return unquartered()
                    ? "The company has to live somewhere. This is the first real "
                            + "decision about what it is for."
                    : "A bigger hull is not automatically a better home. What a ship "
                            + "takes away is the half worth reading.";
        });
    }

    public Signal<List<CandidateRow>> candidateRows() { return candidateRows; }
    public Signal<List<FacilityCell>> facilityCells() { return facilityCells; }
    public Signal<String> selectedName() { return selectedName; }
    public Signal<String> selectedSummary() { return selectedSummary; }
    public Signal<String> verdict() { return verdict; }
    public Signal<String> transferLabel() { return transferLabel; }
    public Signal<String> transferClasses() { return transferClasses; }
    public Signal<String> costLabel() { return costLabel; }
    public Signal<String> costClasses() { return costClasses; }
    public Signal<String> roomTitle() { return roomTitle; }
    public Signal<String> roomCopy() { return roomCopy; }
    public Signal<String> contextLabel() { return contextLabel; }

    /**
     * The selected candidate's deck, for the plan view. Null if she has none.
     *
     * <p>The whole deck rather than only its rooms: the plan draws the
     * passages between them, and a fifth of a ship's walkable area is
     * circulation.
     */
    public CompanyDeck selectedPlan() {
        Candidate ship = selected();
        return ship != null && ship.id().equals(stagedShipId) ? staged : null;
    }

    /**
     * Take delivery of any hull that has finished being laid out.
     *
     * <p>Called by the screen on its own clock rather than waited on, which is
     * the whole point: a deck lands when it lands and the list says so until it
     * does. Nothing here generates anything — the work happened elsewhere and
     * this is where its result becomes something the screen may read, on the
     * one thread that reads it.
     *
     * @return whether anything landed, so a caller may skip the rest of a frame
     */
    /**
     * Whether any hull is still being laid out.
     *
     * <p>For a caller that wants the finished fleet rather than the screen's
     * behaviour — evidence rendering, and the tests that pin what a row says
     * once its deck has landed.
     */
    public boolean reading() {
        return !laying.isEmpty();
    }

    public boolean advance() {
        if (laying.isEmpty()) return false;
        boolean landed = false;
        Iterator<Map.Entry<String, CompletableFuture<Laid>>> reading =
                laying.entrySet().iterator();
        while (reading.hasNext()) {
            Map.Entry<String, CompletableFuture<Laid>> entry = reading.next();
            if (!entry.getValue().isDone()) continue;
            reading.remove();
            Laid laid = entry.getValue().join();
            interiors.put(entry.getKey(), laid.interior());
            if (laid.deck() != null && entry.getKey().equals(stagedShipId)) {
                staged = laid.deck();
            }
            landed = true;
        }
        if (landed) revision.update(value -> value + 1);
        return landed;
    }

    /** Show this ship. Ignored for one the player does not own. */
    public void select(String shipId) {
        selectedShipId.set(shipId);
    }

    /**
     * Move the company to the selected ship, and pay the yard for it.
     *
     * <p>A move nobody can pay for is refused rather than run up as a debt.
     * The company is still standing where it was, which is the honest outcome
     * and the one the screen is already showing.
     */
    public void commit() {
        Candidate ship = selected();
        if (ship == null || isHome(ship) || !ship.quarters()) return;
        TransferCost price = costOf(ship);
        if (price.credits() > means.credits()) return;
        means.charge(price.credits());
        moveAboard.accept(ship.id());
        refresh();
    }

    /**
     * What the yard would want to move the company onto this ship.
     *
     * <p>Free for a company with nowhere to live: see {@link TransferCost}.
     */
    public TransferCost costOf(Candidate ship) {
        if (ship == null || isHome(ship) || !ship.quarters()) return TransferCost.FREE;
        if (unquartered()) return TransferCost.FREE;
        return TransferCost.of(ship.ship().hullClass(), means.marines(), means.walkers());
    }

    /** The price on the ship the player is looking at. */
    public TransferCost selectedCost() {
        return costOf(selected());
    }

    public void refresh() {
        ships = null;
        revision.update(value -> value + 1);
    }

    /**
     * The player's ships, read once rather than once per question.
     *
     * <p>Reading the fleet is not free. A ship that has taken damage is read as
     * she would be without it, which clones her variant, lifts her d-mods and
     * builds a throwaway member to ask the game what that comes to. A dozen
     * signals on this screen each want to know which ship is selected, or which
     * one is home, and every one of them was re-reading the whole fleet to find
     * out.
     */
    private List<Candidate> fleet() {
        if (ships == null) ships = List.copyOf(fleet.get());
        return ships;
    }

    private List<CandidateRow> buildCandidateRows() {
        revision.get();
        List<Candidate> owned = fleet();
        readAhead(owned);
        ShipInterior quarters = interiorOf(homeShip());
        List<CandidateRow> rows = new ArrayList<>();
        for (Candidate ship : owned) {
            ShipInterior interior = interiorOf(ship);
            boolean here = isHome(ship);
            String classes = "transfer-row"
                    + (ship.id().equals(selectedShipId.get()) ? " selected" : "")
                    + (here ? " current" : "")
                    + (ship.quarters() ? "" : " unsupported");
            String id = "transfer-ship:" + ship.id();
            rows.add(new CandidateRow(id, id + ":name", id + ":hull", id + ":detail",
                    id + ":cost", classes, ship.name(), ship.designation(),
                    interior == null ? "laying out her deck" : berths(interior),
                    rowCost(ship, quarters, interior, here),
                    () -> selectedShipId.set(ship.id())));
        }
        return List.copyOf(rows);
    }

    /**
     * The right-hand column of a row: what she is, or what she would cost.
     *
     * <p>Silent rather than wrong while a hull is still being laid out. A
     * comparison needs both decks, and reading a half-laid fleet as though the
     * missing half were a company with nowhere to live would tell the player
     * their own ship had no berthing.
     */
    private String rowCost(Candidate ship, ShipInterior quarters,
                           ShipInterior interior, boolean here) {
        if (here) return "HOME";
        if (!ship.quarters()) return "TOO SMALL TO LIVE ABOARD";
        if (interior == null) return "";
        if (quarters == null && !unquartered()) return "";
        return cost(quarters, interior);
    }

    private List<FacilityCell> buildFacilityCells() {
        revision.get();
        ShipInterior quarters = interiorOf(homeShip());
        ShipInterior interior = interiorOf(selected());
        if (interior == null) return List.of();
        List<FacilityCell> cells = new ArrayList<>(COMPARED.length);
        for (RoomPurpose purpose : COMPARED) {
            ShipInterior.Facility facility = interior.facility(purpose);
            String id = "transfer-facility:" + purpose.name();
            String value;
            String classes = "facility-cell";
            if (!facility.programmed()) {
                value = "none aboard";
                classes += " absent";
            } else {
                value = facility.capacity() > 0
                        ? facility.capacity() + " in " + facility.rooms()
                        : String.valueOf(facility.rooms());
                if (quarters != null && quarters != interior) {
                    int change = new InteriorChange(quarters, interior)
                            .capacityChange(purpose);
                    if (change != 0) {
                        value += "   " + (change > 0 ? "+" : "") + change;
                        classes += change > 0 ? " better" : " worse";
                    }
                }
            }
            cells.add(new FacilityCell(id, id + ":label", id + ":value", classes,
                    label(purpose), value));
        }
        return List.copyOf(cells);
    }

    private String buildSelectedSummary() {
        revision.get();
        Candidate ship = selected();
        if (ship == null) return "The company owns no ship to live aboard.";
        return ship.designation() + "   ·   " + ship.lift()
                + " berths' worth of lift   ·   " + ship.cargo() + " cargo";
    }

    private String buildVerdict() {
        revision.get();
        Candidate ship = selected();
        if (ship == null) return "";
        if (!ship.ship().habitable()) return "No interior. Nobody could live aboard her.";
        if (!ship.quarters()) {
            return "One deck. A boat to send somewhere, not somewhere to live.";
        }
        if (isHome(ship)) return "The company is quartered here.";
        ShipInterior interior = interiorOf(ship);
        if (interior == null) return "";
        // Before there is a home the question is not what the company would
        // give up but what this hull cannot give them, which is the same
        // question asked of a ship rather than of a move.
        if (unquartered()) {
            List<RoomPurpose> absent = missing(interior);
            return absent.isEmpty()
                    ? "She has everywhere the company needs."
                    : "She has no " + list(absent, "or") + ".";
        }
        ShipInterior quarters = interiorOf(homeShip());
        if (quarters == null) return "";
        List<RoomPurpose> lost = new InteriorChange(quarters, interior).lost();
        return lost.isEmpty()
                ? "Nothing aboard would be given up."
                : "Moving here would give up " + list(lost, "and") + ".";
    }

    /**
     * How she went, and who went with her.
     *
     * <p>The toll is named here because this is the screen the player is put on
     * by the loss, and a company that has just been cut in half should not have
     * to work that out from a shorter roster.
     */
    private static String gone(CompanyShipDesignation.Home standing) {
        if (!standing.lostInAction()) return " is gone. ";
        return standing.marinesLost() > 0
                ? " was lost with " + standing.marinesLost() + " marines aboard. "
                : " was lost. ";
    }

    /**
     * The price of the move in money, beside the price of it in rooms.
     *
     * <p>Silent on the ship they already live aboard, because there is no move
     * to price. A company with nowhere to live is told the move is free rather
     * than told nothing, so the absence of a number reads as a decision
     * somebody made rather than as a screen that has not finished loading.
     */
    private String buildCostLabel() {
        revision.get();
        Candidate ship = selected();
        if (ship == null || isHome(ship)) return "";
        TransferCost price = costOf(ship);
        if (price.free()) {
            return unquartered() ? "No charge. There is nowhere to move out of." : "";
        }
        String line = "Refit  \u00b7  " + money(price.credits()) + " credits  \u00b7  "
                + moved(price);
        int shortfall = price.credits() - means.credits();
        return shortfall > 0 ? line + "  \u00b7  " + money(shortfall) + " short" : line;
    }

    /** Who and what the yard is being paid to shift. */
    private static String moved(TransferCost price) {
        String heads = count(price.marines(), "marine");
        return price.walkers() > 0 ? heads + " and " + count(price.walkers(), "walker") : heads;
    }

    private static String count(int many, String thing) {
        return many + " " + thing + (many == 1 ? "" : "s");
    }

    private static String money(int credits) {
        return String.format(Locale.US, "%,d", credits);
    }

    /** Whether the purse covers the move the player is looking at. */
    private boolean affordable() {
        return selectedCost().credits() <= means.credits();
    }

    /**
     * Whether the company has nowhere to live, whether because they have never
     * chosen or because what they chose is gone. Both are answered the same
     * way — a hull is judged on what she lacks, since there is nothing to
     * measure her against.
     */
    private boolean unquartered() {
        return home.get().shipId() == null;
    }

    /** The places a company weighs a hull on that this one does not have. */
    private static List<RoomPurpose> missing(ShipInterior interior) {
        List<RoomPurpose> absent = new ArrayList<>();
        for (RoomPurpose purpose : COMPARED) {
            if (!interior.facility(purpose).programmed()) absent.add(purpose);
        }
        return absent;
    }

    private static String cost(ShipInterior quarters, ShipInterior candidate) {
        if (candidate == null) return "";
        // Founding asks the same question of a ship that transfer asks of a
        // move: what will this hull not do for the company. With no home to
        // measure against, that is simply what she lacks.
        if (quarters == null) {
            return shorten("no ", missing(candidate), "or", "has everywhere");
        }
        return shorten("loses ", new InteriorChange(quarters, candidate).lost(),
                "and", "no loss");
    }

    /**
     * The short form for a row in the fleet list, which has one line for it.
     *
     * <p>Truncated rather than clipped: a hull that gives up five things says so
     * as a count, because a sentence cut off mid-word tells the player less than
     * the number does. The full list is on the ship's own page.
     */
    private static String shorten(String lead, List<RoomPurpose> purposes,
                                 String conjunction, String none) {
        if (purposes.isEmpty()) return none;
        if (purposes.size() <= ROW_LOSSES) return lead + list(purposes, conjunction);
        return lead + list(purposes.subList(0, ROW_LOSSES), conjunction)
                + " +" + (purposes.size() - ROW_LOSSES) + " more";
    }

    /**
     * Set every hull this list will ask about going, and wait for none of them.
     *
     * <p>A row says how many berths a hull has and what moving aboard her would
     * cost the company, and neither is a number any datasheet holds: a room the
     * deck could not fit is a room the ship does not have, so the only way to
     * answer is to lay the deck out. A fleet nobody has looked at yet is
     * therefore a fleet of decks, and a capital's is seconds of work.
     *
     * <p>They are independent — each generated from its own seed into its own
     * grids, and everything they consult is fixed before the game starts — so
     * they go together, and the row for a hull says she is being laid out until
     * she is. The ship on the stage goes first, because hers is the picture the
     * player is waiting for.
     */
    private void readAhead(List<Candidate> owned) {
        Candidate showing = selected();
        if (showing != null && !showing.id().equals(stagedShipId)) {
            stagedShipId = showing.id();
            staged = null;
        }
        if (showing != null) layOut(showing);
        for (Candidate ship : owned) layOut(ship);
    }

    /**
     * Start laying her out, unless she is already read and there is no picture
     * of her wanted, or she is already being laid out.
     */
    private void layOut(Candidate ship) {
        String id = ship.id();
        if (laying.containsKey(id)) return;
        boolean keep = id.equals(stagedShipId);
        boolean wantsDeck = keep && staged == null;
        if (!wantsDeck && interiors.containsKey(id)) return;
        if (!ship.ship().habitable()) {
            interiors.put(id, new ShipInterior(ship.ship(), Map.of()));
            return;
        }
        long seed = CompanyShipDesignation.deckSeedFor(id);
        // A hull somebody has already looked over is answered now rather than
        // put down to be laid out again. The panel is rebuilt every time the
        // player opens it, so without this the second visit costs exactly what
        // the first one did.
        ShipInterior known = LaidDecks.known(ship.ship(), seed);
        if (known != null) interiors.put(id, known);
        if (known != null && !wantsDeck) return;
        laying.put(id, LaidDecks.off(() -> lay(ship, seed, keep)));
    }

    /**
     * One hull laid out, away from the screen's thread.
     *
     * <p>A hull that cannot be laid out is recorded as holding nothing rather
     * than left unread, because an unread hull is asked for again on the next
     * frame and a hull that fails once fails every time.
     */
    private static Laid lay(Candidate ship, long seed, boolean keep) {
        try {
            if (!keep) return new Laid(LaidDecks.aboard(ship.ship(), seed), null);
            // The hull on the stage is drawn as well as counted, so her deck is
            // laid out whether or not what is aboard her is already known - and
            // what is aboard her is then read off it rather than asked for
            // separately.
            CompanyDeck deck = new CompanyDeck(ship.ship(), seed);
            ShipInterior read = ShipInterior.of(ship.ship(), deck.rooms());
            LaidDecks.aboard(ship.ship(), seed, read);
            return new Laid(read, deck);
        } catch (RuntimeException notLaid) {
            Global.getLogger(ShipTransferViewModel.class).warn(
                    "ShipTransferViewModel: could not lay out " + ship.name() + " ("
                            + notLaid.getClass().getSimpleName() + ": "
                            + notLaid.getMessage() + "); she is shown as holding nothing");
            return new Laid(new ShipInterior(ship.ship(), Map.of()), null);
        }
    }

    /** What is aboard her, or null while her deck is still being laid out. */
    private ShipInterior interiorOf(Candidate ship) {
        return ship == null ? null : interiors.get(ship.id());
    }

    private Candidate homeShip() {
        return find(home.get().shipId());
    }

    private Candidate selected() {
        Candidate chosen = find(selectedShipId.get());
        if (chosen != null) return chosen;
        List<Candidate> owned = fleet();
        return owned.isEmpty() ? null : owned.get(0);
    }

    private Candidate find(String shipId) {
        if (shipId == null) return null;
        for (Candidate ship : fleet()) {
            if (ship.id().equals(shipId)) return ship;
        }
        return null;
    }

    private boolean isHome(Candidate ship) {
        return ship != null && ship.id().equals(home.get().shipId());
    }

    private static String berths(ShipInterior interior) {
        if (interior == null) return "";
        ShipInterior.Facility berthing = interior.facility(RoomPurpose.BARRACKS);
        return berthing.programmed() ? berthing.capacity() + " berths" : "no berthing";
    }

    private static List<Candidate> playerFleet() {
        List<Candidate> ships = new ArrayList<>();
        for (FleetMemberAPI member : CompanyShipDesignation.candidates()) {
            CompanyShip hull = CompanyShipResolver.read(member);
            if (hull == null) continue;
            ships.add(new Candidate(member.getId(), name(member), designation(member),
                    Math.max(0, Math.round(member.getMaxCrew() - member.getMinCrew())),
                    Math.round(member.getCargoCapacity()), hull));
        }
        return ships;
    }

    private static void designate(String shipId) {
        for (FleetMemberAPI member : CompanyShipDesignation.candidates()) {
            if (member.getId().equals(shipId)) {
                CompanyShipDesignation.designate(member);
                return;
            }
        }
    }

    private static String name(FleetMemberAPI ship) {
        String named = ship.getShipName();
        return named == null || named.isBlank() ? ship.getHullId() : named;
    }

    private static String designation(FleetMemberAPI ship) {
        return words(ship.getHullSpec().getHullSize().name()) + ", "
                + (ship.getHullSpec().hasDesignation()
                        ? ship.getHullSpec().getDesignation().toLowerCase(Locale.ROOT)
                        : "unclassified");
    }

    /**
     * @param conjunction joins the last two. A list of things given up reads
     *     "and"; a list of things absent reads "or", because "no mech bay and
     *     range" says she has neither only by accident of grammar.
     */
    private static String list(List<RoomPurpose> purposes, String conjunction) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < purposes.size(); index++) {
            if (index > 0) {
                text.append(index == purposes.size() - 1 ? " " + conjunction + " " : ", ");
            }
            text.append(label(purposes.get(index)).toLowerCase(Locale.ROOT));
        }
        return text.toString();
    }

    private static String label(RoomPurpose purpose) {
        return switch (purpose) {
            case BARRACKS -> "Berthing";
            case VEHICLE_BAY -> "Mech bay";
            case PATIENT_WARD -> "Sick bay";
            case CONFERENCE_ROOM -> "Briefing room";
            case STOCKROOM -> "Hold";
            case FIRING_RANGE -> "Range";
            case MESS_HALL -> "Mess";
            case CREW_QUARTERS -> "Crew quarters";
            case BRIDGE -> "Bridge";
            default -> words(purpose.name());
        };
    }

    private static String words(String constant) {
        String spaced = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** One ship in the fleet, as a place the company could live. */
    public record CandidateRow(String id, String nameId, String hullId, String detailId,
                               String costId, String classes, String name, String hull,
                               String detail, String cost, Runnable select)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "hullId" -> hullId;
                case "detailId" -> detailId;
                case "costId" -> costId;
                case "classes" -> classes;
                case "name" -> name;
                case "hull" -> hull;
                case "detail" -> detail;
                case "cost" -> cost;
                case "select" -> select;
                default -> null;
            };
        }
    }

    /** One kind of place aboard the selected ship, and how it compares to home. */
    public record FacilityCell(String id, String labelId, String valueId,
                               String classes, String label, String value)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "labelId" -> labelId;
                case "valueId" -> valueId;
                case "classes" -> classes;
                case "label" -> label;
                case "value" -> value;
                default -> null;
            };
        }
    }
}
