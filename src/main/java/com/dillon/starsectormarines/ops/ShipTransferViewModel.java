package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.InteriorChange;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The fleet as places the company could live, and what moving would do to them.
 *
 * <p>Every candidate is read as a generated deck, because the question is not
 * how large a hull is but what would be aboard her. A ship's plan and her
 * facility counts come from one generation apiece, held once the player has
 * looked at her — generating every deck in the fleet to draw one list would put
 * a second of work in front of a screen that shows one ship at a time.
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
    }

    private final Supplier<List<Candidate>> fleet;
    private final Supplier<CompanyShipDesignation.Home> home;
    private final Consumer<String> moveAboard;
    private final Map<String, ShipInterior> interiors = new HashMap<>();
    private final Map<String, CompanyDeck> plans = new HashMap<>();
    private final MutableSignal<String> selectedShipId;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<CandidateRow>> candidateRows;
    private final ComputedSignal<List<FacilityCell>> facilityCells;
    private final ComputedSignal<String> selectedName;
    private final ComputedSignal<String> selectedSummary;
    private final ComputedSignal<String> verdict;
    private final ComputedSignal<String> transferLabel;
    private final ComputedSignal<String> transferClasses;
    private final ComputedSignal<String> roomTitle;
    private final ComputedSignal<String> roomCopy;
    private final ComputedSignal<String> contextLabel;

    /** The player's own fleet, and their own company's standing for quarters. */
    public ShipTransferViewModel(Reactor reactor) {
        this(reactor, ShipTransferViewModel::playerFleet,
                CompanyShipDesignation::home,
                ShipTransferViewModel::designate);
    }

    public ShipTransferViewModel(Reactor reactor, Supplier<List<Candidate>> fleet,
                                 Supplier<CompanyShipDesignation.Home> home,
                                 Consumer<String> moveAboard) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (fleet == null) throw new IllegalArgumentException("a fleet is required");
        if (home == null) throw new IllegalArgumentException("a home is required");
        if (moveAboard == null) throw new IllegalArgumentException("a transfer is required");
        this.fleet = fleet;
        this.home = home;
        this.moveAboard = moveAboard;
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
            if (isHome(selected())) return "THE COMPANY LIVES HERE";
            return unquartered() ? "QUARTER THE COMPANY HERE" : "MOVE THE COMPANY ABOARD";
        });
        transferClasses = reactor.computed(() -> {
            revision.get();
            return "transfer-commit" + (isHome(selected()) ? " current" : "");
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
                return standing.formerShipName()
                        + (standing.lostInAction() ? " was lost. " : " is gone. ")
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
        if (ship == null) return null;
        return plans.computeIfAbsent(ship.id(),
                id -> new CompanyDeck(ship.ship(), CompanyShipDesignation.deckSeedFor(id)));
    }

    /** Show this ship. Ignored for one the player does not own. */
    public void select(String shipId) {
        selectedShipId.set(shipId);
    }

    /** Move the company to the selected ship. */
    public void commit() {
        Candidate ship = selected();
        if (ship == null || isHome(ship)) return;
        moveAboard.accept(ship.id());
        refresh();
    }

    public void refresh() {
        revision.update(value -> value + 1);
    }

    private List<CandidateRow> buildCandidateRows() {
        revision.get();
        ShipInterior quarters = interiorOf(homeShip());
        List<CandidateRow> rows = new ArrayList<>();
        for (Candidate ship : fleet.get()) {
            ShipInterior interior = interiorOf(ship);
            boolean here = isHome(ship);
            String classes = "transfer-row"
                    + (ship.id().equals(selectedShipId.get()) ? " selected" : "")
                    + (here ? " current" : "");
            String id = "transfer-ship:" + ship.id();
            rows.add(new CandidateRow(id, id + ":name", id + ":hull", id + ":detail",
                    id + ":cost", classes, ship.name(), ship.designation(),
                    berths(interior), here ? "HOME" : cost(quarters, interior),
                    () -> selectedShipId.set(ship.id())));
        }
        return List.copyOf(rows);
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

    private ShipInterior interiorOf(Candidate ship) {
        if (ship == null) return null;
        return interiors.computeIfAbsent(ship.id(), id -> ShipInterior.of(
                ship.ship(), CompanyShipDesignation.deckSeedFor(id)));
    }

    private Candidate homeShip() {
        return find(home.get().shipId());
    }

    private Candidate selected() {
        Candidate chosen = find(selectedShipId.get());
        if (chosen != null) return chosen;
        List<Candidate> ships = fleet.get();
        return ships.isEmpty() ? null : ships.get(0);
    }

    private Candidate find(String shipId) {
        if (shipId == null) return null;
        for (Candidate ship : fleet.get()) {
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
            case CONTROL_ROOM -> "Bridge";
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
