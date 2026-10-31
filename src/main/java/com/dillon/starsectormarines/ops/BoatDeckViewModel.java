package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.fitting.FittedBadge;
import com.dillon.starsectormarines.ops.fitting.FittingCard;
import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.ops.fitting.FittingMeter;
import com.dillon.starsectormarines.ops.battleview.BoatHardware;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas.RecoveryMarker;
import com.dillon.starsectormarines.ops.CompanyResources.Row;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.air.engine.WeaponSlot;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFabricationCatalog;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.marine.BoatHardpoint;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.ops.battleview.BoatDoll;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.ReserveHangar;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Berth projection and refit command surface for the campaign-authoritative
 * {@link BoatDeck}.
 *
 * <p>The Mech Lab's grammar over a different noun: an overview of everything in
 * the room, one selected asset, its slots, and a catalog scoped to the selected
 * slot. Parts are kept the lab's way: every part, the stock ones too, drags off
 * into stores and back on from there, and a socket can stand empty. A boat
 * missing a required part warns on the board, the way a mech without its engine
 * does, and stays off the lift until one is fitted. A separate strip shows
 * the company's credits and whether the fleet is docked at a port that sells,
 * while each card answers whether the company can buy one more there.
 *
 * <p>It reads the deck and never reconciles it. Which hull the company is on is
 * the screen's question, asked once on attach through {@code ShipsBoatsAboard};
 * a view model that reconciled on every projection would be reaching for
 * campaign state from inside a signal.
 */
public final class BoatDeckViewModel {

    /** No berth selected: the room is showing its overview rather than a boat. */
    private static final int NO_BERTH = -1;
    /** No hardpoint selected: the selected slot is one of the boat's fittings. */
    private static final int NO_HARDPOINT = -1;

    private final BoatDeck deck;
    private final DoubleSupplier campaignDay;
    private final ComputedSignal<Recovery> recovery;
    private float lastDay = Float.NaN;

    private final FabricationResources resources;
    private final BoatWorkshop workshop;
    private final String carrierName;
    private final Runnable deckChanged;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<Integer> selectedBerth;
    private final MutableSignal<BoatFittingSlot> selectedSlot;
    /** The hardpoint whose door gun the room is opened on, or {@link #NO_HARDPOINT} while a fitting slot is. */
    private final MutableSignal<Integer> selectedHardpoint;
    private final MutableSignal<String> feedbackText;
    private final MutableSignal<String> feedbackClasses;
    private final ComputedSignal<String> deckSummary;
    private final ComputedSignal<List<BoatRow>> boatRows;
    private final ComputedSignal<String> selectedBoatName;
    private final ComputedSignal<String> selectedBoatIdentity;
    private final ComputedSignal<List<FittingMeter>> performanceMeters;
    private final ComputedSignal<List<ReadinessRow>> readinessRows;
    private final ComputedSignal<String> selectedSlotTitle;
    private final ComputedSignal<String> selectedSlotCopy;
    private final ComputedSignal<List<CatalogRow>> catalogRows;
    /** What the stores show: one kind of socket's parts, or null for every part the boat can take. */
    private final MutableSignal<BoatDoll.Kind> storesFilter;
    /** The part whose RECYCLE was pressed once and waits for its confirming press; null for none. */
    private String armedRecycle;
    /** The boat whose SCRAP was pressed once and waits for its confirming press; null for none. */
    private String scrapArmed;
    /** The boat whose STORE has been pressed once, or null. */
    private String storeArmed;
    /** The company's reserve hangar and what the home ship's refit lets it hold; null until the screen attaches it. */
    private ReserveHangar reserve;
    private IntSupplier reserveCapacity = () -> 0;
    private final ComputedSignal<List<FittingFilterPill>> filterPills;
    private final ComputedSignal<BoatDoll> doll;
    private final ComputedSignal<String> overviewClasses;
    private final ComputedSignal<String> fittingClasses;
    private final ComputedSignal<String> fabricationClasses;
    private final ComputedSignal<String> fabricationBerthLabel;
    private final ComputedSignal<String> fabricationPatternName;
    private final ComputedSignal<String> fabricationCopy;
    private final ComputedSignal<List<Row>> fabricationMaterials;
    private final ComputedSignal<List<Row>> materialStock;
    private final ComputedSignal<String> fabricationActionClasses;
    private final ComputedSignal<Boolean> fabricationBlocked;
    private final ComputedSignal<String> fabricationReason;

    public BoatDeckViewModel(Reactor reactor, BoatDeck deck, FabricationResources resources) {
        this(reactor, deck, resources, null, () -> { });
    }

    public BoatDeckViewModel(Reactor reactor, BoatDeck deck, FabricationResources resources,
                             String carrierName) {
        this(reactor, deck, resources, carrierName, () -> { });
    }

    /**
     * @param carrierName the ship whose berths these are, or null when nobody
     *     can say — the summary then omits the clause rather than naming a hull
     *     it is guessing at
     * @param deckChanged run after any fit or selection, so the room picture is
     *     invalidated by the same act that changed what it is a picture of
     */
    public BoatDeckViewModel(Reactor reactor, BoatDeck deck, FabricationResources resources,
                             String carrierName, Runnable deckChanged) {
        this(reactor, deck, resources, carrierName, deckChanged, CampaignClock::dayFloat);
    }

    public BoatDeckViewModel(Reactor reactor, BoatDeck deck, FabricationResources resources,
                             String carrierName, Runnable deckChanged, DoubleSupplier campaignDay) {
        this.campaignDay = campaignDay;
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (deck == null) throw new IllegalArgumentException("boat deck is required");
        if (resources == null || deckChanged == null) {
            throw new IllegalArgumentException(
                    "fabrication resources and change callback are required");
        }
        this.deck = deck;
        this.resources = resources;
        this.workshop = new BoatWorkshop(deck, resources);
        this.carrierName = carrierName;
        this.deckChanged = deckChanged;
        revision = reactor.signal(0);
        selectedBerth = reactor.signal(NO_BERTH);
        selectedSlot = reactor.signal(BoatFittingSlot.PLATING);
        selectedHardpoint = reactor.signal(NO_HARDPOINT);
        storesFilter = reactor.signal(null);
        feedbackText = reactor.signal(
                Strings.get("openingBoatDeckSelectABerthToInspectTheBoat"));
        feedbackClasses = reactor.signal("boat-deck-feedback tone-muted surface-dark");
        recovery = reactor.computed(this::buildRecovery);
        deckSummary = reactor.computed(this::buildDeckSummary);
        boatRows = reactor.computed(this::buildBoatRows);
        selectedBoatName = reactor.computed(() -> {
            CampaignBoat boat = selectedBoat();
            return boat != null ? boat.displayName() : Strings.get("openingBoatDeckNoBoatSelected");
        });
        selectedBoatIdentity = reactor.computed(() -> {
            CampaignBoat boat = selectedBoat();
            return boat == null ? Strings.get("openingBoatDeckSelectABerthToOpenHer")
                    : boat.pattern().displayName() + "  ·  " + berthLabel(selectedBerth.get());
        });
        performanceMeters = reactor.computed(this::buildPerformanceMeters);
        readinessRows = reactor.computed(this::buildReadiness);
        selectedSlotTitle = reactor.computed(() -> storesFilter.get() == null
                ? Strings.get("openingMechLabAllPartsButton") : kindLabel(storesFilter.get()));
        selectedSlotCopy = reactor.computed(() -> {
            revision.get();
            BoatDoll.Kind filter = storesFilter.get();
            if (filter == BoatDoll.Kind.HARDPOINT) return Strings.get("boatDeckHardpointCopy");
            return filter == null ? Strings.get("boatDeckStoresCopy") : slotCopy(fittingSlot(filter));
        });
        catalogRows = reactor.computed(this::buildStores);
        filterPills = reactor.computed(this::buildFilterPills);
        doll = reactor.computed(this::buildDoll);
        overviewClasses = reactor.computed(() -> fittingFocused() || fabricationFocused()
                ? "boat-overview panel hidden" : "boat-overview panel");
        fittingClasses = reactor.computed(() -> fittingFocused()
                ? "boat-fitting" : "boat-fitting hidden");
        fabricationClasses = reactor.computed(() -> fabricationFocused()
                ? "boat-fabrication" : "boat-fabrication hidden");
        fabricationBerthLabel = reactor.computed(() -> fabricationFocused()
                ? berthLabel(selectedBerth.get()) : "");
        fabricationPatternName = reactor.computed(() -> {
            BoatFabricationCatalog.Recipe recipe = fabricationRecipe();
            return recipe == null ? Strings.get("openingBoatDeckNoBerthSelected") : recipe.pattern().displayName();
        });
        fabricationCopy = reactor.computed(() -> {
            BoatFabricationCatalog.Recipe recipe = fabricationRecipe();
            return recipe == null ? "" : deck.wreckInBerth(selectedBerth.get())
                    ? Strings.get("openingBoatDeckOriginalHermesWreckPatchHerHullAnd")
                    : recipe.provenance();
        });
        fabricationMaterials = reactor.computed(() -> {
            BoatFabricationCatalog.Recipe recipe = fabricationRecipe();
            return recipe == null ? List.of()
                    : priceRows("boat-fabrication", workshop.fabricationQuote(recipe.pattern()));
        });
        materialStock = reactor.computed(this::buildMaterialStock);
        fabricationActionClasses = reactor.computed(() -> affordableToBuild()
                ? "fabricate-action" : "fabricate-action blocked");
        fabricationBlocked = reactor.computed(() -> !affordableToBuild());
        fabricationReason = reactor.computed(() -> {
            BoatFabricationCatalog.Recipe recipe = fabricationRecipe();
            if (recipe == null) return "";
            if (!resources.atMarket()) return Strings.get("fabricationNoPortShort");
            if (resources.canAfford(workshop.fabricationQuote(recipe.pattern()))) return "";
            return Strings.format("openingBoatDeckShort", Strings.upper(Strings.get("companyResourceCredits")));
        });
    }

    public Signal<Recovery> recovery() { return recovery; }
    public RecoveryMarker berthRecovery(int index) {
        List<CampaignBoat> boats = deck.boats();
        if (index < 0 || index >= boats.size() || boats.get(index) == null) return null;
        CampaignBoat boat = boats.get(index);
        float now = day();
        return new RecoveryMarker(boat.condition(now), boat.repairDaysRemaining(now));
    }
    public float day() { return (float) campaignDay.getAsDouble(); }

    public void refreshTime() {
        float now = day();
        if (now != lastDay) { lastDay = now; revision.update(value -> value + 1); }
    }

    public CampaignBoat boat(String id) { return deck.boatById(id); }
    public void inspect(String id) {
        for (int i = 0; i < deck.boats().size(); i++) {
            CampaignBoat boat = deck.boats().get(i);
            if (boat != null && boat.id().equals(id)) { selectBoat(i); return; }
        }
    }

    private Recovery buildRecovery() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        BoatWorkshop.RepairQuote quote = boat == null ? null : workshop.repairQuote(boat.id(), day());
        boolean damaged = quote != null;
        return new Recovery(boat == null ? "boat-recovery hidden" : "boat-recovery panel",
                damaged ? "height: 304px;" : "height: 66px;",
                boat == null ? "" : recoveryStatus(boat, day()),
                boat == null ? "width: 100%;" : "width: " + Math.round(boat.condition(day()) * 100f) + "%;",
                damaged ? "boat-recovery-order" : "boat-recovery-order hidden",
                damaged ? priceRows("boat-recovery", quote.bill()) : List.of(),
                !damaged || !resources.canBuy(quote.bill()), () -> {
                    BoatWorkshop.Result result = workshop.expedite(quote, day());
                    feedbackText.set(Strings.get(switch (result.status()) {
                        case REPAIRED -> "boatRepairCompleted";
                        case CANNOT_AFFORD -> "boatRepairShort";
                        case NO_MARKET -> "boatRepairNoPort";
                        default -> "boatRepairChanged";
                    }));
                    revision.update(value -> value + 1);
                    if (result.succeeded()) deckChanged.run();
                });
    }

    /** Whether she can fly, and if not why: a missing required part first, since only the player fits one. */
    static String recoveryStatus(CampaignBoat boat, float day) {
        if (!boat.missingParts().isEmpty()) return Strings.format("boatIncomplete", missingNames(boat));
        return boat.airworthy(day) ? Strings.get("boatReady") : Strings.format("boatRepairRemaining",
                Math.round(boat.condition(day) * 100f), Math.ceil(boat.repairDaysRemaining(day) * 10f) / 10d);
    }

    /** The required parts a boat is missing, named for her status line. */
    public static String missingNames(CampaignBoat boat) {
        List<String> names = new ArrayList<>();
        for (BoatFittingSlot slot : boat.missingParts()) names.add(slotLabel(slot).toLowerCase(Strings.locale()));
        return String.join(Strings.get("separatorDot"), names);
    }


    public record Recovery(String classes, String style, String status, String fillStyle, String orderClasses,
                           List<Row> materials, boolean disabled, Runnable expedite) implements MarkupPropertySource {
        @Override public Object markupProperty(String name) { return switch (name) {
            case "classes" -> classes; case "style" -> style; case "status" -> status; case "fillStyle" -> fillStyle;
            case "orderClasses" -> orderClasses; case "materials" -> materials;
            case "disabled" -> disabled; case "expedite" -> expedite;
            default -> throw unknown("boat-recovery", name);
        }; }
    }

    public Signal<String> deckSummary() { return deckSummary; }
    public Signal<List<BoatRow>> boatRows() { return boatRows; }
    public Signal<String> selectedBoatName() { return selectedBoatName; }
    public Signal<String> selectedBoatIdentity() { return selectedBoatIdentity; }
    public Signal<List<FittingMeter>> performanceMeters() { return performanceMeters; }
    /** Why the selected boat can or cannot fly, line by line, each opening the socket it is about. */
    public Signal<List<ReadinessRow>> readinessRows() { return readinessRows; }
    public Signal<List<FittingFilterPill>> filterPills() { return filterPills; }
    public Signal<String> filterClasses() {
        return filterPills.map(pills -> pills.isEmpty() ? "category-filters hidden" : "category-filters");
    }
    /** The stage heading's way back to the whole deck, shown while the room is opened on a boat. */
    public Signal<String> backClasses() {
        return selectedBerth.map(berth -> fittingFocused() ? "stage-back console-action" : "hidden");
    }
    public Signal<String> selectedSlotTitle() { return selectedSlotTitle; }
    public Signal<String> selectedSlotCopy() { return selectedSlotCopy; }
    public Signal<List<CatalogRow>> catalogRows() { return catalogRows; }
    /** The selected boat as the doll draws her, or null while the room is not opened on one. */
    public Signal<BoatDoll> doll() { return doll; }
    public Signal<String> overviewClasses() { return overviewClasses; }
    /** The yard's catalog stands left of the bay while the room is opened on a boat, as the lab's stores do. */
    public Signal<String> catalogClasses() { return selectedBerth.map(berth -> fittingFocused()
            ? "panel catalog-panel" : "hidden"); }
    public Signal<String> stageTitle() { return selectedBerth.map(berth -> { revision.get(); return fittingFocused()
            ? Strings.format("boatDeckStageBerth", berthLabel(berth))
            : Strings.get("openingUiBoatDeckBoatBayBerths"); }); }
    public Signal<String> stageStatus() { return selectedBerth.map(berth -> { revision.get(); return fittingFocused()
            ? Strings.get("boatDeckSelectSlot") : Strings.get("openingUiBoatDeckDeckCrewOnWatch"); }); }
    /** SCRAP beside the stage heading while the room is opened on a boat; CONFIRM once armed. */
    public Signal<String> scrapLabel() { return selectedBerth.map(berth -> { revision.get();
        CampaignBoat boat = selectedBoat();
        return boat != null && boat.id().equals(scrapArmed) ? Strings.get("openingMechLabScrapConfirm")
                : Strings.get("openingMechLabScrap"); }); }
    public Signal<String> scrapClasses() { return selectedBerth.map(berth -> { revision.get();
        CampaignBoat boat = selectedBoat();
        if (!fittingFocused() || boat == null || !BoatWorkshop.scrappable(boat.pattern())) return "hidden";
        return boat.id().equals(scrapArmed) ? "boat-scrap console-action armed" : "boat-scrap console-action"; }); }
    public Runnable scrapAction() { return this::scrapSelected; }

    /** Attaches the company's reserve hangar: STORE beside SCRAP and the Reserve strip under the boats aboard. */
    public void reserve(ReserveHangar hangar, IntSupplier capacity) {
        reserve = hangar;
        reserveCapacity = capacity;
        revision.update(value -> value + 1);
    }

    /** The template's STORE and Reserve strip properties. */
    public void putReserveProps(Map<String, Object> props) {
        props.put("storeLabel", selectedBerth.map(berth -> { revision.get();
            CampaignBoat boat = selectedBoat();
            return boat != null && boat.id().equals(storeArmed) ? Strings.get("reserveStoreConfirm") : Strings.get("reserveStore"); }));
        props.put("storeClasses", selectedBerth.map(berth -> { revision.get();
            CampaignBoat boat = selectedBoat();
            if (!fittingFocused() || boat == null || reserve == null || reserveCapacity.getAsInt() <= 0) return "hidden";
            return boat.id().equals(storeArmed) ? "reserve-store console-action armed" : "reserve-store console-action"; }));
        props.put("storeAction", (Runnable) this::storeSelected);
        props.put("reserveClasses", selectedBerth.map(berth -> { revision.get();
            return fittingFocused() || fabricationFocused() || reserve == null
                    || reserveCapacity.getAsInt() <= 0 && reserve.boats().isEmpty() ? "hidden" : "panel reserve-strip"; }));
        props.put("reserveTitle", revision.map(r -> reserve == null ? ""
                : ReserveRow.title(reserve.stored(), reserveCapacity.getAsInt())));
        props.put("reserveRows", revision.map(r -> reserveRows()));
    }

    List<ReserveRow> reserveRows() {
        if (reserve == null) return List.of();
        List<ReserveRow> rows = new ArrayList<>();
        for (CampaignBoat boat : reserve.boats()) {
            rows.add(new ReserveRow("boat-reserve-" + boat.id(), boat.displayName(), boat.pattern().displayName(),
                    ReserveHangar.vacantBerthFor(deck, boat) >= 0, () -> recall(boat)));
        }
        return rows;
    }
    public Signal<String> fittingClasses() { return fittingClasses; }
    public Signal<String> fabricationClasses() { return fabricationClasses; }
    public Signal<String> fabricationBerthLabel() { return fabricationBerthLabel; }
    public Signal<String> fabricationPatternName() { return fabricationPatternName; }
    public Signal<String> fabricationCopy() { return fabricationCopy; }
    public Signal<List<Row>> fabricationMaterials() { return fabricationMaterials; }
    public Signal<List<Row>> materialStock() { return materialStock; }
    public Signal<String> fabricationActionClasses() { return fabricationActionClasses; }
    public Signal<Boolean> fabricationBlocked() { return fabricationBlocked; }
    public Signal<String> fabricationActionLabel() { return selectedBerth.map(berth -> { revision.get(); return deck.wreckInBerth(berth) ? Strings.get("openingBoatDeckRepairHermes") : Strings.get("openingBoatDeckCommission"); }); }
    public Signal<String> fabricationReason() { return fabricationReason; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

    public Runnable selectBoatAction(int berthIndex) { return () -> selectBoat(berthIndex); }
    public Runnable selectSlotAction(BoatFittingSlot slot) { return () -> selectSlot(slot); }

    /**
     * Whether a stores part carried onto the board would fit the socket under it: a socket
     * that {@linkplain #socketTakes takes the part}, and a spare to fit.
     * The drag lights the socket green or red by this, so a refusal shows before the part is
     * let go.
     */
    public boolean canDropOnSocket(BoatDoll.Kind kind, String itemId, String key) {
        return socketTakes(kind, itemId, key) && deck.spares(itemId) > 0;
    }

    /** Whether a stores card can be picked up: a spare is in stores. A card with none only offers BUY. */
    public boolean canPickUp(BoatDoll.Kind kind, String itemId) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || kind == null || itemId == null) return false;
        return deck.spares(itemId) > 0;
    }

    /** Whether the socket holds a part that can be lifted off: anything fitted, stock parts too. */
    public boolean canLiftOff(String key) {
        return installedIn(key) != null;
    }

    /** The kind of socket a doll key names, or null for an unknown one. */
    public BoatDoll.Kind kindOf(String key) {
        if (key == null) return null;
        if (BoatDoll.hardpointOf(key) >= 0) return BoatDoll.Kind.HARDPOINT;
        BoatFittingSlot slot = slotOf(key);
        return slot == null ? null : dollKind(slot);
    }

    /**
     * What a socket holds, as a drag lifting it off carries it; null for an unknown socket. A
     * hardpoint answers by position: the gun in that row, or a 2x2 gun in either.
     */
    public String installedIn(String key) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || key == null) return null;
        int hardpoint = BoatDoll.hardpointOf(key);
        if (hardpoint >= 0) {
            BoatGun gun = BoatHardpoint.occupant(boat.gunsAt(hardpoint), BoatDoll.positionOf(key));
            return gun == null ? null : gun.id();
        }
        BoatFittingSlot slot = slotOf(key);
        BoatFitting fitting = slot == null ? null : boat.fittingIn(slot);
        return fitting == null ? null : fitting.id();
    }

    /**
     * Whether a part lifted off one socket can go straight onto another: a gun, to another
     * hardpoint position, the other position of its own hardpoint included for a 2x1 gun.
     */
    public boolean canMoveBetweenSockets(String from, String to) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || from == null || to == null || from.equals(to) || !canLiftOff(from)) return false;
        int source = BoatDoll.hardpointOf(from);
        int target = BoatDoll.hardpointOf(to);
        if (source < 0 || !boat.hasHardpoint(target)) return false;
        int fromPosition = BoatDoll.positionOf(from), toPosition = BoatDoll.positionOf(to);
        BoatGun gun = BoatHardpoint.occupant(boat.gunsAt(source), fromPosition);
        if (source == target && (fromPosition == toPosition || !gun.half())) return false;
        BoatGun[] landing = source == target
                ? BoatHardpoint.placed(boat.gunsAt(source), fromPosition, null) : boat.gunsAt(target);
        return BoatHardpoint.occupant(landing, toPosition) != gun;
    }

    /** Moves a gun lifted off one hardpoint position onto another; see {@link BoatWorkshop#moveGun}. */
    public void moveBetweenSockets(String from, String to) {
        CampaignBoat boat = selectedBoat();
        if (!canMoveBetweenSockets(from, to)) {
            selectDollSlot(to);
            return;
        }
        int source = BoatDoll.hardpointOf(from);
        int target = BoatDoll.hardpointOf(to);
        BoatGun gun = BoatHardpoint.occupant(boat.gunsAt(source), BoatDoll.positionOf(from));
        BoatWorkshop.Result result = workshop.moveGun(boat.id(), source, BoatDoll.positionOf(from),
                target, BoatDoll.positionOf(to));
        selectDollSlot(to);
        report(result, result.succeeded()
                ? Strings.format("boatDeckGunMoved", gun.displayName(), hardpointLabel(source), hardpointLabel(target))
                : Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged"));
    }

    /**
     * Takes the part in one socket off into stores, as dropping it on the stores does, and
     * leaves the socket empty. Taking out a required part is allowed: she stays off the lift
     * until one is fitted, and the feedback says so.
     */
    public void returnToStores(String key) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || !canLiftOff(key)) return;
        String name = partName(installedIn(key));
        int hardpoint = BoatDoll.hardpointOf(key);
        BoatFittingSlot slot = hardpoint >= 0 ? null : slotOf(key);
        BoatWorkshop.Result result = hardpoint >= 0
                ? workshop.stripGun(boat.id(), hardpoint, BoatDoll.positionOf(key))
                : workshop.strip(boat.id(), slot);
        selectDollSlot(key);
        report(result, !result.succeeded() ? Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged")
                : slot != null && CampaignBoat.REQUIRED_SLOTS.contains(slot)
                ? Strings.format("boatDeckRequiredStripped", name, boat.displayName())
                : Strings.format("boatDeckReturnedToStores", name, boat.displayName()));
    }

    /** Builds one spare of a part into stores out of the fleet's hold. */
    private void fabricatePart(String itemId) {
        BoatWorkshop.Result result = workshop.fabricatePart(itemId);
        report(result, switch (result.status()) {
            case PART_FABRICATED -> Strings.format("boatDeckPartBuilt", partName(itemId));
            case CANNOT_AFFORD -> Strings.get("openingBoatDeckRefitBlockedRequiredFleetMaterialsAreShort");
            case NO_MARKET -> Strings.get("fabricationNoPort");
            case NOT_LEARNED -> Strings.get("boatDeckPartNotLearned");
            default -> Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged");
        });
    }

    /**
     * Breaks one spare down for credits. The first press arms the card and says what would come
     * back; the second, on the same card, commits.
     */
    private void recyclePart(String itemId) {
        if (!itemId.equals(armedRecycle)) {
            FabricationCost back = workshop.recycleQuote(itemId);
            armedRecycle = itemId;
            feedbackText.set(Strings.format("fittingRecycleReview", partName(itemId), recoveryLine(back)));
            feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
            revision.update(value -> value + 1);
            return;
        }
        BoatWorkshop.Result result = workshop.recycle(itemId);
        report(result, switch (result.status()) {
            case RECYCLED -> Strings.format("fittingRecycled", partName(itemId));
            case WORKSHOP_IDLE -> Strings.get("fittingRecycleWorkshopIdle");
            default -> Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged");
        });
    }

    /** "1,250 credits": what a recycle would pay back. */
    private static String recoveryLine(FabricationCost back) {
        return back == null ? "" : CompanyResources.creditAmount(back.credits());
    }

    private static List<Row> recoveryRows(String ownerId, FabricationCost back) {
        return back == null ? List.of() : List.of(CompanyResources.recoveredCredits(ownerId + ":recovered:credits", back.credits()));
    }

    private void report(BoatWorkshop.Result result, String text) {
        armedRecycle = null;
        scrapArmed = null;
        storeArmed = null;
        feedbackText.set(text);
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
        if (result.succeeded()) deckChanged.run();
    }

    private static String partName(String itemId) {
        BoatFitting fitting = BoatFitting.findById(itemId);
        if (fitting != null) return fitting.displayName();
        BoatGun gun = BoatGun.findById(itemId);
        return gun != null ? gun.displayName() : "";
    }

    private static BoatFittingSlot slotOf(String key) {
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            if (BoatDoll.slotKey(slot).equals(key)) return slot;
        }
        return null;
    }

    /**
     * Whether the socket is one this part goes in, whatever it costs: the right kind of
     * socket, a part this boat's pattern can carry, and not what is already there.
     */
    private boolean socketTakes(BoatDoll.Kind kind, String itemId, String key) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || kind == null || key == null) return false;
        int hardpoint = BoatDoll.hardpointOf(key);
        if (kind == BoatDoll.Kind.HARDPOINT) {
            BoatGun gun = BoatGun.findById(itemId);
            return gun != null && boat.hasHardpoint(hardpoint)
                    && BoatHardpoint.occupant(boat.gunsAt(hardpoint), BoatDoll.positionOf(key)) != gun
                    && gun.supports(boat.pattern());
        }
        BoatFitting fitting = BoatFitting.findById(itemId);
        return fitting != null && hardpoint < 0 && BoatDoll.slotKey(fitting.slot()).equals(key)
                && fitting != boat.fittingIn(fitting.slot()) && fitting.supports(boat.pattern());
    }

    /**
     * Fits a carried stores part to the socket it was let go over, opening that socket the
     * way a click on it would. A socket the part does not go in opens and says so; one the
     * hold cannot pay for refuses the way the yard does, naming the line that is short.
     */
    public void dropOnSocket(BoatDoll.Kind kind, String itemId, String key) {
        boolean takes = socketTakes(kind, itemId, key);
        selectDollSlot(key);
        if (!takes) return;
        if (kind == BoatDoll.Kind.HARDPOINT) fitGun(BoatDoll.hardpointOf(key), BoatDoll.positionOf(key), itemId);
        else fit(itemId);
    }

    /** Selects whichever slot a doll card names; see {@link BoatDoll.Slot#key}. */
    public void selectDollSlot(String key) {
        if (key == null) return;
        int hardpoint = BoatDoll.hardpointOf(key);
        if (hardpoint >= 0) {
            selectHardpoint(hardpoint);
            return;
        }
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            if (BoatDoll.slotKey(slot).equals(key)) selectSlot(slot);
        }
    }
    public Runnable backToDeckAction() { return this::showDeckOverview; }

    /** Builds the hull's own pattern into the berth the room is opened on. */
    public Runnable fabricateAction() { return () -> fabricate(selectedBerth.get()); }

    public Runnable fabricateAction(int berthIndex) { return () -> fabricate(berthIndex); }

    /** The berth the room is looking at, or -1 while it is showing the whole deck. */
    public int selectedBerthIndex() {
        revision.get();
        return selectedBerth.get();
    }

    /** Whether the room is opened on one boat rather than on the deck. */
    public boolean fittingFocused() {
        return selectedBoat() != null;
    }

    /**
     * Whether the room is opened on an empty berth, which is the one place a
     * boat can be built. A berth is either standing a boat or standing empty,
     * so this and {@link #fittingFocused} are never both true.
     */
    public boolean fabricationFocused() {
        revision.get();
        int berth = selectedBerth.get();
        List<CampaignBoat> berths = deck.boats();
        return berth >= 0 && berth < berths.size() && berths.get(berth) == null;
    }

    /**
     * Reprojects the campaign deck after it has been reconciled against the
     * ship, which may have moved the company's boats into different berths.
     * The selection is dropped rather than carried, because berth three on the
     * old hull is not berth three on this one.
     */
    public void refresh() {
        selectedBerth.set(NO_BERTH);
        selectedSlot.set(BoatFittingSlot.PLATING);
        selectedHardpoint.set(NO_HARDPOINT);
        storesFilter.set(null);
        revision.update(value -> value + 1);
    }

    private String buildDeckSummary() {
        revision.get();
        List<CampaignBoat> boats = deck.boats().stream().filter(Objects::nonNull).toList();
        List<String> summary = new ArrayList<>();
        summary.add(Strings.format("openingBoatCount", boats.size()));
        if (deck.flightReady(day()).size() < boats.size())
            summary.add(Strings.format("boatDeckReadiness", deck.flightReady(day()).size(), boats.size()));
        ShuttleType pattern = boats.isEmpty() ? null : boats.get(0).pattern();
        if (pattern != null) summary.add(pattern.displayName());
        if (carrierName != null && !carrierName.isBlank())
            summary.add(Strings.format("openingBoatCarrier", carrierName));
        int wrecks = (int) deck.vacantBerths().stream().filter(deck::wreckInBerth).count();
        if (wrecks > 0) summary.add(Strings.format("openingBoatWrecks", wrecks));
        int empty = deck.vacantBerths().size() - wrecks;
        if (empty > 0) summary.add(Strings.format("openingBoatVacancies", empty));
        return String.join("  ·  ", summary);
    }

    private List<BoatRow> buildBoatRows() {
        revision.get();
        int selected = selectedBerth.get();
        List<CampaignBoat> berths = deck.boats();
        List<BoatRow> rows = new ArrayList<>(berths.size());
        for (int index = 0; index < berths.size(); index++) {
            CampaignBoat boat = berths.get(index);
            String base = "boat:" + index;
            int berth = index;
            boolean vacant = boat == null;
            String classes = (vacant ? "boat-card vacant" : "boat-card")
                    + (index == selected ? " selected" : "");
            rows.add(new BoatRow(base, base + ":berth", base + ":name", base + ":pattern",
                    base + ":plating", base + ":drive",
                    classes,
                    berthLabel(index),
                    vacant ? (deck.wreckInBerth(index) ? Strings.get("openingBoatDeckWreckedHermes") : Strings.get("openingBoatDeckEmptyBerth")) : boat.displayName(),
                    vacant ? (deck.wreckInBerth(index) ? Strings.get("openingBoatDeckOriginalShipSBoatAwaitingRepair") : buildablePattern()) : boat.pattern().displayName(),
                    vacant ? buildableBill() : boat.plating() != null ? boat.plating().displayName()
                            : Strings.get("boatDeckBareHull"),
                    vacant ? "" : boat.flightReady(day()) ? boat.drive().displayName() : recoveryStatus(boat, day()),
                    () -> selectBoat(berth)));
        }
        return List.copyOf(rows);
    }

    /**
     * What this boat is against what the catalog could make her.
     *
     * <p>Hull and speed are measured against a ceiling the fittings can move,
     * so a standard boat reads as having somewhere to go. Seats and hardpoints
     * fill their own ceiling because they are facts about the pattern: no
     * fitting in slice 1 adds a seat, and a bar that could never fill would be
     * a promise the room does not keep.
     */
    private List<FittingMeter> buildPerformanceMeters() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        ShuttleType pattern = boat.pattern();
        FittedBoat fitted = boat.freezeForDeployment();
        BoatFitting cabinFit = boat.fittingIn(BoatFittingSlot.CABIN);
        int cabin = cabinFit == null ? 0 : cabinFit.seats();
        int seats = cabin > 0 ? cabin : pattern.capacity;
        int maxSeats = pattern.capacity;
        for (BoatFitting part : BoatFitting.catalog(BoatFittingSlot.CABIN)) maxSeats = Math.max(maxSeats, part.seats());
        // A damaged hull reads as a deficit under her pattern's own figure; plating is armour over it,
        // read against the pattern's stock skin, so a refit is gain past it and a bare hull a deficit.
        float armorCeiling = pattern.armor() * bestFactor(pattern, BoatFittingSlot.PLATING, BoatFitting::armorFactor);
        return List.of(
                FittingMeter.of("boat-meter:hull", Strings.get("openingBoatDeckHull"),
                        Strings.format("openingBoatDeckHp", Math.round(fitted.maxHp() * boat.condition(day()))),
                        fitted.maxHp() * boat.condition(day()), pattern.maxHp(),
                        fitted.maxHp() * boat.condition(day()), pattern.maxHp(), FittingMeter.PLATE_TONE),
                FittingMeter.of("boat-meter:armor", Strings.get("boatDeckArmor"),
                        Strings.format("boatDeckArmorValue", Math.round(fitted.armor())),
                        fitted.armor(), armorCeiling, fitted.armor(), pattern.armor(), FittingMeter.PLATE_TONE),
                FittingMeter.of("boat-meter:speed", Strings.get("openingBoatDeckSpeed"),
                        Strings.format("openingBoatDeckCellsS", number(fitted.maxSpeed())), fitted.maxSpeed(),
                        pattern.maxSpeed() * bestFactor(pattern, BoatFittingSlot.DRIVE, BoatFitting::speedFactor),
                        fitted.maxSpeed(), pattern.maxSpeed(), FittingMeter.ENGINE_TONE),
                fitted.cradled()
                        ? FittingMeter.of("boat-meter:seats", Strings.get("openingBoatDeckSeats"),
                                Strings.format("boatDeckSeatsAndLift", seats,
                                        LiftReadout.ratingName(fitted.liftRating())),
                                seats, maxSeats)
                        : FittingMeter.of("boat-meter:seats", Strings.get("openingBoatDeckSeats"),
                                Strings.format("openingBoatDeckSeats2", seats), seats, maxSeats));
    }

    /**
     * The stores' filter strip: every part, then one pill per kind of socket this boat has,
     * each on its sockets' colour with a piece of that kind drawn on it.
     */
    private List<FittingFilterPill> buildFilterPills() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        BoatDoll.Kind current = storesFilter.get();
        List<FittingFilterPill> pills = new ArrayList<>();
        pills.add(new FittingFilterPill("boat-filter:all", Strings.get("boatDeckFilterAll"), null, null,
                FittingFilterPill.classes(current == null), () -> storesFilter.set(null)));
        for (BoatDoll.Kind kind : offeredKinds(boat.pattern())) {
            pills.add(new FittingFilterPill("boat-filter:" + kind.name().toLowerCase(Locale.ROOT), kindLabel(kind),
                    BoatHardware.iconPath(kind, standardPiece(boat, kind)), BoatHardware.toneHex(kind),
                    FittingFilterPill.classes(kind == current), () -> storesFilter.set(kind)));
        }
        return List.copyOf(pills);
    }

    /**
     * What the yard can put on this boat, as the lab's stores show a mech's: every part of the
     * kinds the filter lets through, each a card to be dragged onto a socket on the board.
     *
     * <p>Parts are kept as the lab keeps them: a card counts the spares in stores, and one with
     * none shows its bill and a BUY that builds one. The stock parts are counted like any
     * other. Whether a particular socket takes a part is the drop's to say
     * ({@link #canDropOnSocket}), not the card's.
     */
    private List<CatalogRow> buildStores() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        BoatDoll.Kind filter = storesFilter.get();
        List<CatalogRow> rows = new ArrayList<>();
        for (BoatDoll.Kind kind : offeredKinds(boat.pattern())) {
            if (filter != null && filter != kind) continue;
            if (kind == BoatDoll.Kind.HARDPOINT) {
                for (BoatGun gun : BoatGun.catalog()) {
                    List<Integer> mounts = mountsCarrying(boat, gun);
                    if (mounts.isEmpty() && !gun.supports(boat.pattern())) continue;
                    rows.add(gunCard(boat, gun, mounts));
                }
                continue;
            }
            BoatFittingSlot slot = fittingSlot(kind);
            for (BoatFitting fitting : BoatFitting.catalog(slot)) {
                boolean installed = fitting == boat.fittingIn(slot);
                if (!installed && !fitting.supports(boat.pattern())) continue;
                rows.add(fittingCard(boat, fitting, installed));
            }
        }
        return List.copyOf(rows);
    }

    private CatalogRow gunCard(CampaignBoat boat, BoatGun gun, List<Integer> mounts) {
        boolean free = false;
        FabricationCost bill = resources.quote(gun.bill());
        boolean held = selectedHardpoint.get() != NO_HARDPOINT && mounts.contains(selectedHardpoint.get());
        // A pod of two of this gun reads as its hardpoint twice over, not as two hardpoints.
        String on = mounts.stream().distinct().map(index -> Strings.format(
                        mounts.indexOf(index) == mounts.lastIndexOf(index) ? "boatDeckHardpointShort"
                                : "boatDeckHardpointPair", index + 1))
                .reduce((a, b) -> a + "  ·  " + b).orElse("");
        return card(BoatDoll.Kind.HARDPOINT, gun.id(), "boat-catalog:gun:" + gun.id(), gun.displayName(),
                gun.tier(), gun.effect(),
                on.isEmpty() ? "" : Strings.format("boatDeckFittedOn", on), !mounts.isEmpty(), held, free, bill);
    }

    private CatalogRow fittingCard(CampaignBoat boat, BoatFitting fitting, boolean installed) {
        boolean held = installed && selectedHardpoint.get() == NO_HARDPOINT && selectedSlot.get() == fitting.slot();
        return card(dollKind(fitting.slot()), fitting.id(), "boat-catalog:" + fitting.id(), fitting.displayName(),
                fitting.tier(), effect(fitting), installed ? "" : change(boat, fitting), installed, held,
                false, resources.quote(fitting.bill()));
    }

    /**
     * One stores card: what it is, its tier and how many are in stores, what it does, what it
     * would change or where it is fitted, and its footprint. With none in stores and no free
     * yard part to fall back on, it is greyed with its price and a BUY beside it, as the lab's are.
     * BUY is refused away from a civilized port, and the card says so.
     */
    private CatalogRow card(BoatDoll.Kind kind, String itemId, String id, String name, int tier,
                            String effect, String delta, boolean fitted, boolean held, boolean free,
                            FabricationCost bill) {
        int spares = deck.spares(itemId);
        boolean build = !free && spares == 0 && bill != null;
        // A part the company has not learned cannot be built; the company store sells its pattern.
        boolean locked = build && !deck.knowsPart(itemId);
        boolean affordable = build && !locked && resources.canBuy(bill);
        String classes = "catalog-row" + (held ? " selected" : "") + (build ? " requires-fabrication" : "");
        int[] footprint = BoatHardware.footprint(kind, itemId);
        String detail = Strings.format("boatDeckGrid", footprint[0], footprint[1]);
        // A spare in stores can be broken down while the machine shop runs, but never a part she is wearing; its refund takes
        // the strip a price would, in green.
        FabricationCost back = !free && !fitted && spares > 0 ? workshop.recycleQuote(itemId) : null;
        List<Row> materials = build ? priceRows(id, bill) : recoveryRows(id, back);
        String stock = free ? kindLine(tier, kind)
                : Strings.format("openingMechLabOwnFree", kindLine(tier, kind), MechLabViewModel.spareCount(spares));
        FittingCard card = new FittingCard(id, classes, "catalog-preview", name,
                FittedBadge.label(fitted), FittedBadge.classes(fitted),
                stock, effect, delta,
                delta.isEmpty() ? "catalog-delta neutral" : fitted ? "label heading catalog-delta neutral"
                        : delta.startsWith("-") ? "label heading catalog-delta negative"
                        : "label heading catalog-delta positive",
                detail, locked ? Strings.get("boatDeckPartLocked") : build ? Strings.get("openingMechLabFab") : "",
                locked ? Strings.get("boatDeckPartLockedHint")
                : affordable ? Strings.get("boatDeckFabHint")
                        : !resources.atMarket() ? Strings.get("fabricationNoPort")
                        : Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor"),
                !build || !affordable, true, materials,
                materials.isEmpty() ? "hidden" : back != null ? "material-costs recovery" : "material-costs",
                build && !locked ? () -> fabricatePart(itemId) : null, null, null,
                back == null ? null : new FittingCard.Recycle(itemId.equals(armedRecycle), () -> recyclePart(itemId)));
        return new CatalogRow(card, kind, itemId);
    }

    /** What fitting this would do to her figures against what she carries now. */
    private String change(CampaignBoat boat, BoatFitting fitting) {
        ShuttleType pattern = boat.pattern();
        BoatFitting fitted = boat.fittingIn(fitting.slot());
        // Against an empty socket the change is the whole part, measured from the stock one's figures,
        // or, for plating, from the bare hull's no armour.
        BoatFitting installed = fitted != null ? fitted : BoatFitting.standard(fitting.slot());
        float armorNow = fitted != null ? fitted.armorFactor() : 0f;
        return switch (fitting.slot()) {
            case PLATING -> signed(Math.round(pattern.armor() * (fitting.armorFactor() - armorNow)),
                    Strings.get("boatDeckArmor"));
            case DRIVE -> signed(pattern.maxSpeed() * (fitting.speedFactor() - installed.speedFactor()),
                    Strings.get("openingBoatDeckSpeed"));
            case LIFT -> Strings.format("boatDeckLiftEffect", LiftReadout.ratingName(fitting.liftOn(pattern)));
            case CABIN, EMITTER, POWER -> effect(fitting);
        };
    }

    private static String signed(float amount, String stat) {
        String value = number(Math.abs(amount));
        return (amount < 0f ? "-" : "+") + value + " " + stat;
    }

    /** The hardpoints a gun is on, in order, once for each of it there: a pod of two lists its hardpoint twice. */
    private static List<Integer> mountsCarrying(CampaignBoat boat, BoatGun gun) {
        List<Integer> mounts = new ArrayList<>();
        for (int index = 0; index < boat.pattern().hardpoints(); index++) {
            for (BoatGun fitted : boat.gunsOn(index)) if (fitted == gun) mounts.add(index);
        }
        return mounts;
    }

    /**
     * The selected boat's readiness, in the order it matters: each required part she is missing
     * (she cannot fly), then what she flies without (a bare hull, no shield, empty mounts), then
     * her frame. Every line opens the socket it is about. A boat with nothing missing or bare
     * reads as one line saying so.
     */
    private List<ReadinessRow> buildReadiness() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        ShuttleType pattern = boat.pattern();
        List<ReadinessRow> rows = new ArrayList<>();
        for (BoatFittingSlot slot : boat.missingParts()) {
            rows.add(readiness("missing:" + slot.name(), "danger",
                    Strings.format("boatReadinessMissing", sentenceCase(slotLabel(slot))), Strings.get("boatReadinessMissingDetail"),
                    BoatDoll.slotKey(slot)));
        }
        FittedBoat fitted = boat.freezeForDeployment();
        if (boat.plating() == null) {
            rows.add(readiness("bare-hull", "warn", Strings.get("boatReadinessBareHull"),
                    Strings.format("boatReadinessBareHullDetail", Math.round(fitted.maxHp())),
                    BoatDoll.slotKey(BoatFittingSlot.PLATING)));
        }
        if (boat.fittingIn(BoatFittingSlot.EMITTER) == null) {
            rows.add(readiness("no-shield", "warn", Strings.get("boatReadinessNoShield"),
                    Strings.get("boatReadinessNoShieldDetail"), BoatDoll.slotKey(BoatFittingSlot.EMITTER)));
        }
        // A mount is empty with no gun on it at all; one 2x1 gun with room beside it is armed.
        int bare = 0;
        int guns = 0;
        int firstBare = -1;
        for (int index = 0; index < pattern.hardpoints(); index++) {
            int on = boat.gunsOn(index).size();
            guns += on;
            if (on > 0) continue;
            bare++;
            if (firstBare < 0) firstBare = index;
        }
        if (bare > 0) {
            int armed = pattern.hardpoints() - bare;
            rows.add(readiness("bare-mounts", "warn",
                    Strings.format("boatReadinessBareMounts", bare, pattern.hardpoints()),
                    bare == pattern.hardpoints() ? Strings.get("boatReadinessNoGunsDetail")
                            : Strings.format("boatReadinessBareMountsDetail", guns, armed),
                    BoatDoll.HARDPOINT_KEY + firstBare));
        }
        if (rows.isEmpty()) {
            rows.add(readiness("whole", "good", Strings.get("boatReadinessWhole"),
                    Strings.get("boatReadinessWholeDetail"), null));
        }
        return List.copyOf(rows);
    }

    private static String sentenceCase(String label) {
        String lower = label.toLowerCase(Strings.locale());
        return lower.isEmpty() ? lower : lower.substring(0, 1).toUpperCase(Strings.locale()) + lower.substring(1);
    }

    private ReadinessRow readiness(String key, String tone, String title, String detail, String socket) {
        String id = "boat-readiness:" + key;
        return new ReadinessRow(id, "readiness-row readiness-" + tone + (socket != null ? " readiness-link" : ""),
                "readiness-bar readiness-bar-" + tone, title, detail,
                socket == null ? () -> { } : () -> selectDollSlot(socket));
    }

    /** The kinds of socket a pattern has, in the board's order: guns, plating, drive, cabin, then power, hold, emitter. */
    private static List<BoatDoll.Kind> offeredKinds(ShuttleType pattern) {
        List<BoatDoll.Kind> kinds = new ArrayList<>();
        if (pattern.hardpoints() > 0) kinds.add(BoatDoll.Kind.HARDPOINT);
        kinds.add(BoatDoll.Kind.PLATING);
        kinds.add(BoatDoll.Kind.DRIVE);
        kinds.add(BoatDoll.Kind.CABIN);
        kinds.add(BoatDoll.Kind.POWER);
        if (liftable(pattern)) kinds.add(BoatDoll.Kind.LIFT);
        kinds.add(BoatDoll.Kind.EMITTER);
        return kinds;
    }

    /** The part a filter pill draws: what the yard puts in that kind of socket. */
    private static String standardPiece(CampaignBoat boat, BoatDoll.Kind kind) {
        if (kind == BoatDoll.Kind.HARDPOINT) {
            BoatGun gun = boat.gunOn(0);
            return gun == null ? null : gun.id();
        }
        for (BoatFitting fitting : BoatFitting.catalog(fittingSlot(kind))) {
            if (fitting.standard()) return fitting.id();
        }
        return null;
    }

    private static BoatFittingSlot fittingSlot(BoatDoll.Kind kind) {
        return switch (kind) {
            case PLATING -> BoatFittingSlot.PLATING;
            case DRIVE -> BoatFittingSlot.DRIVE;
            case LIFT -> BoatFittingSlot.LIFT;
            case CABIN -> BoatFittingSlot.CABIN;
            case EMITTER -> BoatFittingSlot.EMITTER;
            case POWER -> BoatFittingSlot.POWER;
            case HARDPOINT -> throw new IllegalArgumentException("a hardpoint holds a gun, not a fitting");
        };
    }

    /** A kind of socket as the stores' heading names it. */
    private static String kindLabel(BoatDoll.Kind kind) {
        return switch (kind) {
            case HARDPOINT -> Strings.get("boatDeckStoresGuns");
            case PLATING -> Strings.get("openingBoatDeckPlating");
            case DRIVE -> Strings.get("openingBoatDeckDrive");
            case LIFT -> Strings.get("boatDeckLift");
            case CABIN -> Strings.get("boatDeckCabin");
            case EMITTER -> Strings.get("boatDeckEmitter");
            case POWER -> Strings.get("boatDeckPower");
        };
    }

    /** One part of that kind, for a card's first line. */
    /** A part's tier and kind, as its stores card and its sheet both head it. */
    static String kindLine(int tier, BoatDoll.Kind kind) {
        return Strings.format("openingBoatDeckTier", tier) + "  ·  " + kindSingular(kind);
    }

    private static String kindSingular(BoatDoll.Kind kind) {
        return switch (kind) {
            case HARDPOINT -> Strings.get("boatDeckStoresGun");
            case PLATING -> Strings.get("openingBoatDeckPlating");
            case DRIVE -> Strings.get("openingBoatDeckDrive");
            case LIFT -> Strings.get("boatDeckLift");
            case CABIN -> Strings.get("boatDeckCabin");
            case EMITTER -> Strings.get("boatDeckEmitter");
            case POWER -> Strings.get("boatDeckPower");
        };
    }

    /**
     * The selected boat as the doll draws her: her sprite, and every slot she
     * has with where it sits. A hardpoint stands where her hull's weapon slot
     * is, read off the same spec the battle mounts her guns from, so the spot
     * on the doll is the spot the gun fires from.
     */
    private BoatDoll buildDoll() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return null;
        ShuttleType pattern = boat.pattern();
        int hardpoint = selectedHardpoint.get();
        List<BoatDoll.Slot> slots = new ArrayList<>();
        List<BoatFittingSlot> missing = boat.missingParts();
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            if (slot == BoatFittingSlot.LIFT && !liftable(pattern)) continue;
            BoatFitting fitting = boat.fittingIn(slot);
            boolean selected = hardpoint == NO_HARDPOINT && slot == selectedSlot.get();
            slots.add(fitting == null
                    ? BoatDoll.Slot.fitting(slot, null, slotLabel(slot), Strings.get(missing.contains(slot)
                            ? "boatDeckSocketMissing" : slot == BoatFittingSlot.PLATING ? "boatDeckBareHull"
                            : "boatDeckSocketEmpty"), "", selected, missing.contains(slot))
                    : BoatDoll.Slot.fitting(slot, fitting.id(), slotLabel(slot), fitting.displayName(),
                            tierLabel(fitting), selected, false));
        }
        List<WeaponSlot> mounts = TurretSlotResolver.mounts(pattern.renderHullId());
        for (int index = 0; index < pattern.hardpoints(); index++) {
            BoatGun[] pair = boat.gunsAt(index);
            BoatGun gun = pair[BoatHardpoint.TOP], pod = pair[BoatHardpoint.BOTTOM];
            BoatGun named = gun != null ? gun : pod;
            WeaponSlot mount = index < mounts.size() ? mounts.get(index) : null;
            String fitted = named == null ? Strings.get("boatDeckHardpointBare")
                    : gun != null && pod != null ? Strings.format("boatDeckPodFitted", gun.displayName(), pod.displayName())
                    : named.displayName();
            slots.add(BoatDoll.Slot.hardpoint(index, gun == null ? null : gun.id(), pod == null ? null : pod.id(),
                    hardpointLabel(index), fitted, named == null ? "" : gunTier(boat, index, named),
                    index == hardpoint, mount));
        }
        return new BoatDoll(pattern, boat.displayName(), pattern.displayName(), List.copyOf(slots));
    }

    /** A price as its one credits row, against what the company holds. */
    private List<Row> priceRows(String ownerId, FabricationCost bill) {
        if (bill == null) return List.of();
        return CompanyResources.credits(ownerId + ":material:credits", resources.credits(), bill.credits());
    }

    /** The company's credits, and whether the fleet is at a port that will sell. */
    private List<Row> buildMaterialStock() {
        revision.get();
        return List.of(CompanyResources.stock("boat-material-stock:credits", Strings.get("companyResourceCredits"),
                        CompanyResources.CREDITS_ICON, resources.credits()),
                CompanyResources.port("boat-material-stock:port", resources.atMarket()));
    }

    /**
     * Opens whichever face the chosen berth calls for: the boat standing in it,
     * or — for a berth with nothing in it — what the yard could build there.
     */
    private void selectBoat(int berthIndex) {
        List<CampaignBoat> berths = deck.boats();
        if (berthIndex < 0 || berthIndex >= berths.size()) return;
        CampaignBoat boat = berths.get(berthIndex);
        selectedBerth.set(berthIndex);
        selectedSlot.set(BoatFittingSlot.PLATING);
        selectedHardpoint.set(NO_HARDPOINT);
        storesFilter.set(null);
        feedbackText.set(boat != null
                ? Strings.format("openingBoatDeckInspectingNothingHasBeenRefitted", boat.displayName())
                : deck.wreckInBerth(berthIndex) ? Strings.format("openingBoatDeckAnOriginalHermesWreckAwaitsRepairIn", berthLabel(berthIndex))
                : Strings.format("openingBoatDeckIsEmptyTheYardCanBuildThe", berthLabel(berthIndex)));
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
        revision.update(value -> value + 1);
        deckChanged.run();
    }

    private void selectSlot(BoatFittingSlot slot) {
        if (slot == null || selectedBoat() == null) return;
        selectedSlot.set(slot);
        selectedHardpoint.set(NO_HARDPOINT);
        storesFilter.set(dollKind(slot));
        feedbackText.set(Strings.format("openingBoatDeckSelectedEverythingTheYardCanPutHere", slotLabel(slot)));
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
    }

    private void selectHardpoint(int hardpoint) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || !boat.hasHardpoint(hardpoint)) return;
        selectedHardpoint.set(hardpoint);
        storesFilter.set(BoatDoll.Kind.HARDPOINT);
        feedbackText.set(Strings.format("openingBoatDeckSelectedEverythingTheYardCanPutHere", hardpointLabel(hardpoint)));
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
    }

    private void fitGun(int hardpoint, int position, String gunId) {
        CampaignBoat boat = selectedBoat();
        BoatGun gun = BoatGun.findById(gunId);
        if (boat == null || gun == null) return;
        BoatWorkshop.Result result = workshop.fitGun(boat.id(), hardpoint, position, gunId);
        feedbackText.set(switch (result.status()) {
            case FITTED -> Strings.format("boatDeckGunFitted", gun.displayName(), hardpointLabel(hardpoint), boat.displayName());
            case ALREADY_FITTED -> Strings.format("openingBoatDeckIsAlreadyFittedTo", gun.displayName(), boat.displayName());
            case NO_SPARE -> Strings.format("boatDeckNoSpare", gun.displayName());
            case INCOMPATIBLE_FITTING -> Strings.get("boatDeckHardpointHermes");
            default -> Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged");
        });
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
        if (result.succeeded()) deckChanged.run();
    }

    /**
     * SCRAP takes two presses, as the lab's does: the first arms it and says what comes back,
     * the second breaks the boat up through the workshop and returns to the deck with her
     * berth vacant.
     */
    /**
     * STORE takes two presses, as SCRAP does: the first arms it, the second takes the boat off
     * her berth into the reserve hangar, fit and repair clock and all, and leaves the berth vacant.
     */
    private void storeSelected() {
        CampaignBoat boat = selectedBoat();
        if (boat == null || reserve == null) return;
        scrapArmed = null;
        if (!boat.id().equals(storeArmed)) {
            storeArmed = boat.id();
            feedbackText.set(Strings.format("boatDeckStoreArmed", boat.displayName()));
            feedbackClasses.set("boat-deck-feedback tone-accent surface-dark");
            revision.update(value -> value + 1);
            return;
        }
        storeArmed = null;
        String berth = berthLabel(selectedBerth.get());
        int capacity = reserveCapacity.getAsInt();
        ReserveHangar.Status status = reserve.storeBoat(deck, boat.id(), capacity);
        if (status != ReserveHangar.Status.STORED) {
            feedbackText.set(status == ReserveHangar.Status.HANGAR_FULL
                    ? Strings.format("reserveHangarFull", capacity)
                    : Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged"));
            feedbackClasses.set("boat-deck-feedback tone-danger surface-dark");
            revision.update(value -> value + 1);
            return;
        }
        selectedBerth.set(NO_BERTH);
        feedbackText.set(Strings.format("boatDeckStored", boat.displayName(), berth));
        feedbackClasses.set("boat-deck-feedback tone-good surface-dark");
        revision.update(value -> value + 1);
        deckChanged.run();
    }

    private void recall(CampaignBoat boat) {
        if (reserve == null) return;
        storeArmed = null;
        scrapArmed = null;
        int berth = ReserveHangar.vacantBerthFor(deck, boat);
        boolean recalled = reserve.recallBoat(deck, boat.id()) == ReserveHangar.Status.RECALLED;
        feedbackText.set(recalled ? Strings.format("boatDeckRecalled", boat.displayName(), berthLabel(berth))
                : Strings.format("boatDeckRecallNoBerth", boat.displayName()));
        feedbackClasses.set(recalled ? "boat-deck-feedback tone-good surface-dark" : "boat-deck-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
        if (recalled) deckChanged.run();
    }

    private void scrapSelected() {
        CampaignBoat boat = selectedBoat();
        if (boat == null || !BoatWorkshop.scrappable(boat.pattern())) return;
        storeArmed = null;
        FabricationCost back = workshop.scrapQuote(boat.id());
        String hull = back == null ? Strings.get("openingMechLabNoSalvage") : recoveryLine(back);
        if (!boat.id().equals(scrapArmed)) {
            scrapArmed = boat.id();
            feedbackText.set(Strings.format("boatDeckScrapArmed", boat.displayName(), hull));
            feedbackClasses.set("boat-deck-feedback tone-danger surface-dark");
            revision.update(value -> value + 1);
            return;
        }
        scrapArmed = null;
        BoatWorkshop.Result result = workshop.scrap(boat.id());
        if (!result.succeeded()) {
            report(result, Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged"));
            return;
        }
        selectedBerth.set(NO_BERTH);
        report(result, Strings.format("boatDeckScrapped", boat.displayName(), hull));
    }

    private void showDeckOverview() {
        selectedBerth.set(NO_BERTH);
        feedbackText.set(Strings.get("openingBoatDeckSelectABerthToInspectItsBoat"));
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
        revision.update(value -> value + 1);
        deckChanged.run();
    }

    private void fit(String fittingId) {
        CampaignBoat boat = selectedBoat();
        BoatFitting fitting = BoatFitting.findById(fittingId);
        if (boat == null || fitting == null) return;
        BoatWorkshop.Result result = workshop.fit(boat.id(), fittingId);
        feedbackText.set(switch (result.status()) {
            case FITTED -> Strings.format("openingBoatDeckFittedToOutOfFleetStores", fitting.displayName(), boat.displayName());
            case ALREADY_FITTED -> Strings.format("openingBoatDeckIsAlreadyFittedTo", fitting.displayName(), boat.displayName());
            case NO_SPARE -> Strings.format("boatDeckNoSpare", fitting.displayName());
            case INCOMPATIBLE_FITTING -> Strings.get("openingBoatDeckTheHermesHasNoUpgradePathReplace");
            default -> Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged");
        });
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
        if (result.succeeded()) deckChanged.run();
    }

    /**
     * Builds the hull's own pattern into one empty berth.
     *
     * <p>The berth stays selected afterwards, which is what turns the card into
     * a boat card and the fabrication pane into that boat's fitting pane: the
     * player is looking at the berth, and what is standing in it has changed.
     */
    private void fabricate(int berthIndex) {
        boolean wreck = deck.wreckInBerth(berthIndex);
        BoatWorkshop.Result result = workshop.fabricate(berthIndex);
        CampaignBoat built = result.boat();
        feedbackText.set(switch (result.status()) {
            case FABRICATED -> Strings.format(wreck ? "openingBoatRepaired" : "openingBoatBuilt", built.displayName(), berthLabel(berthIndex));
            case CANNOT_AFFORD ->
                    Strings.get("openingBoatDeckFabricationBlockedRequiredFleetMaterialsAreShort");
            case NO_MARKET -> Strings.get("fabricationNoPort");
            case BERTH_OCCUPIED -> Strings.format("openingBoatDeckAlreadyHasABoatStandingInIt", berthLabel(berthIndex));
            default -> Strings.get("openingBoatDeckFabricationBlockedNothingAboardWasChanged");
        });
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        if (result.status() == BoatWorkshop.Status.FABRICATED) {
            selectedBerth.set(berthIndex);
            selectedSlot.set(BoatFittingSlot.PLATING);
            selectedHardpoint.set(NO_HARDPOINT);
        }
        revision.update(value -> value + 1);
        if (result.succeeded()) deckChanged.run();
    }

    /** What the yard would build into the berth the room is opened on. */
    private BoatFabricationCatalog.Recipe fabricationRecipe() {
        if (!fabricationFocused()) return null;
        return BoatFabricationCatalog.recipe(deck.pattern());
    }

    private boolean affordableToBuild() {
        BoatFabricationCatalog.Recipe recipe = fabricationRecipe();
        return recipe != null && resources.canBuy(workshop.fabricationQuote(recipe.pattern()));
    }

    /** What a vacant berth would be built back up as, for its card. */
    private String buildablePattern() {
        BoatFabricationCatalog.Recipe recipe =
                BoatFabricationCatalog.recipe(deck.pattern());
        return recipe == null ? Strings.get("openingBoatDeckNothingStandingHere")
                : Strings.format("openingBoatDeckCanBeBuiltHere", recipe.pattern().displayName());
    }

    /** The price for that, short enough to sit on one line of a card. */
    private String buildableBill() {
        BoatFabricationCatalog.Recipe recipe =
                BoatFabricationCatalog.recipe(deck.pattern());
        return recipe == null ? "" : CompanyResources.creditAmount(workshop.fabricationQuote(recipe.pattern()).credits());
    }

    /** The selected boat's pattern, for what her parts do on her; null with no boat selected. */
    public ShuttleType selectedPattern() {
        CampaignBoat boat = selectedBoat();
        return boat == null ? null : boat.pattern();
    }

    private CampaignBoat selectedBoat() {
        revision.get();
        int berth = selectedBerth.get();
        List<CampaignBoat> berths = deck.boats();
        return berth >= 0 && berth < berths.size() ? berths.get(berth) : null;
    }

    private static float bestFactor(ShuttleType pattern, BoatFittingSlot slot, FittingFactor factor) {
        float best = 1f;
        for (BoatFitting fitting : BoatFitting.catalog(slot)) {
            if (!fitting.supports(pattern)) continue;
            best = Math.max(best, factor.of(fitting));
        }
        return best;
    }

    private String effect(BoatFitting fitting) {
        CampaignBoat boat = selectedBoat();
        return switch (fitting.slot()) {
            case PLATING -> Strings.format("boatDeckArmorEffect", factor(fitting.armorFactor()));
            case LIFT -> Strings.format("boatDeckLiftEffect",
                    LiftReadout.ratingName(fitting.liftOn(boat == null ? null : boat.pattern())));
            case DRIVE -> Strings.format("boatDeckDriveEffect", factor(fitting.speedFactor()),
                    factor(fitting.deboardFactor()));
            case CABIN -> Strings.format("boatDeckCabinEffect",
                    fitting.seats() > 0 ? fitting.seats() : boat == null ? 0 : boat.pattern().capacity);
            case EMITTER -> fitting.standard() ? Strings.get("boatDeckEmitterStandard")
                    : Strings.format("boatDeckEmitterEffect", Math.round(fitting.shieldArcDeg()),
                            factor(fitting.shieldEfficiency()));
            case POWER -> Strings.format("boatDeckPowerEffect", factor(fitting.fluxCapacityFactor()),
                    factor(fitting.fluxDissipationFactor()));
        };
    }

    static String factor(float value) {
        return String.format(Strings.locale(), "%.2f", value);
    }

    private static String tierLabel(BoatFitting fitting) {
        return Strings.format("openingBoatDeckTier", fitting.tier());
    }

    private static String slotLabel(BoatFittingSlot slot) {
        return switch (slot) {
            case DRIVE -> Strings.get("openingBoatDeckDrive");
            case LIFT -> Strings.get("boatDeckLift");
            case PLATING -> Strings.get("openingBoatDeckPlating");
            case CABIN -> Strings.get("boatDeckCabin");
            case EMITTER -> Strings.get("boatDeckEmitter");
            case POWER -> Strings.get("boatDeckPower");
        };
    }

    static BoatDoll.Kind dollKind(BoatFittingSlot slot) {
        return switch (slot) {
            case PLATING -> BoatDoll.Kind.PLATING;
            case DRIVE -> BoatDoll.Kind.DRIVE;
            case LIFT -> BoatDoll.Kind.LIFT;
            case CABIN -> BoatDoll.Kind.CABIN;
            case EMITTER -> BoatDoll.Kind.EMITTER;
            case POWER -> BoatDoll.Kind.POWER;
        };
    }

    private static String hardpointLabel(int hardpoint) {
        return Strings.format("boatDeckHardpoint", hardpoint + 1);
    }

    /** A yard gun reads as yard issue as well as its tier, so a refit reads as one. */
    private static String gunTier(CampaignBoat boat, int hardpoint, BoatGun gun) {
        return gun.standardOn(boat.pattern(), hardpoint)
                ? Strings.format("openingBoatDeckTier", gun.tier()) + "  ·  " + Strings.get("boatDeckGunStandard")
                : Strings.format("openingBoatDeckTier", gun.tier());
    }

    private static String slotCopy(BoatFittingSlot slot) {
        return switch (slot) {
            case DRIVE -> Strings.get("openingBoatDeckWhatGetsHerOffTheDeckAnd");
            case LIFT -> Strings.get("boatDeckLiftCopy");
            case PLATING -> Strings.get("openingBoatDeckWhatAnAntiAirGunHasTo");
            case CABIN -> Strings.get("boatDeckCabinCopy");
            case EMITTER -> Strings.get("boatDeckEmitterCopy");
            case POWER -> Strings.get("boatDeckPowerCopy");
        };
    }

    private static boolean liftable(ShuttleType pattern) {
        for (BoatFitting fitting : BoatFitting.catalog(BoatFittingSlot.LIFT)) {
            if (!fitting.standard() && fitting.supports(pattern)) return true;
        }
        return false;
    }



    private static String berthLabel(int index) {
        return String.format(Strings.locale(), Strings.get("openingBoatDeckBerthD"), index + 1);
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return Integer.toString(Math.round(value));
        return String.format(Strings.locale(), "%.1f", value);
    }

    private interface FittingFactor { float of(BoatFitting fitting); }

    public record BoatRow(String id, String berthId, String nameId, String patternId,
                          String platingId, String driveId,
                          String classes, String berth, String name, String pattern,
                          String plating, String drive, Runnable select)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "berthId" -> berthId; case "nameId" -> nameId;
            case "patternId" -> patternId; case "platingId" -> platingId;
            case "driveId" -> driveId; case "classes" -> classes; case "berth" -> berth;
            case "name" -> name; case "pattern" -> pattern; case "plating" -> plating;
            case "drive" -> drive; case "select" -> select;
            default -> throw unknown("boat", p); }; }
    }

    /** The selected socket's card: the kit's slot card, and which socket and piece it shows. */
    /** One line of a boat's readiness: its tone, what it says, and the socket a click opens. */
    public record ReadinessRow(String id, String classes, String barClasses, String title, String detail,
                               Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String name) { return switch (name) {
            case "id" -> id; case "classes" -> classes; case "barClasses" -> barClasses;
            case "title" -> title; case "detail" -> detail; case "select" -> select;
            case "barId" -> id + ":bar"; case "textId" -> id + ":text";
            case "titleId" -> id + ":title"; case "detailId" -> id + ":detail";
            default -> throw unknown("boat-readiness", name);
        }; }
    }

    /** A part in the stores: the kit's card, and which piece it is, which a drag carries. */
    public record CatalogRow(FittingCard card, BoatDoll.Kind kind, String itemId) implements MarkupPropertySource {
        public String id() { return card.id(); }
        public String previewId() { return card.previewId(); }
        public String classes() { return card.classes(); }
        public String name() { return card.name(); }
        public String badge() { return card.badge(); }
        public String detail() { return card.detail(); }
        public List<Row> materials() { return card.materials(); }

        @Override public Object markupProperty(String p) { return card.markupProperty(p); }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
