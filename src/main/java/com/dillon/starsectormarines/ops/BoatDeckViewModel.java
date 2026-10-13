package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas.RecoveryMarker;
import com.dillon.starsectormarines.ops.CompanyResources.Row;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFabricationCatalog;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Berth projection and refit command surface for the campaign-authoritative
 * {@link BoatDeck}.
 *
 * <p>The Mech Lab's grammar over a different noun: an overview of everything in
 * the room, one selected asset, its slots, and a catalog scoped to the selected
 * slot. What differs is what a fitting <em>is</em> — installed work rather than
 * a spare — so nothing comes back to stores. A separate strip shows shared fleet
 * material stock while each fitting row answers whether the fleet can pay for it.
 *
 * <p>It reads the deck and never reconciles it. Which hull the company is on is
 * the screen's question, asked once on attach through {@code ShipsBoatsAboard};
 * a view model that reconciled on every projection would be reaching for
 * campaign state from inside a signal.
 */
public final class BoatDeckViewModel {

    /** No berth selected: the room is showing its overview rather than a boat. */
    private static final int NO_BERTH = -1;

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
    private final MutableSignal<String> feedbackText;
    private final MutableSignal<String> feedbackClasses;
    private final ComputedSignal<String> deckSummary;
    private final ComputedSignal<List<BoatRow>> boatRows;
    private final ComputedSignal<String> selectedBoatName;
    private final ComputedSignal<String> selectedBoatIdentity;
    private final ComputedSignal<List<PerformanceMeter>> performanceMeters;
    private final ComputedSignal<List<SlotRow>> slotRows;
    private final ComputedSignal<String> selectedSlotTitle;
    private final ComputedSignal<String> selectedSlotCopy;
    private final ComputedSignal<List<CatalogRow>> catalogRows;
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
        slotRows = reactor.computed(this::buildSlotRows);
        selectedSlotTitle = reactor.computed(() -> slotLabel(selectedSlot.get()));
        selectedSlotCopy = reactor.computed(() -> {
            revision.get();
            CampaignBoat boat = selectedBoat();
            return boat != null && boat.pattern() == ShuttleType.HERMES
                    ? Strings.get("openingBoatDeckTheHermesIsAPatchedCourierWith")
                    : slotCopy(selectedSlot.get());
        });
        catalogRows = reactor.computed(this::buildCatalogRows);
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
                    : materialRows("boat-fabrication", resources.quote(recipe.bill()));
        });
        materialStock = reactor.computed(this::buildMaterialStock);
        fabricationActionClasses = reactor.computed(() -> affordableToBuild()
                ? "fabricate-action" : "fabricate-action blocked");
        fabricationBlocked = reactor.computed(() -> !affordableToBuild());
        fabricationReason = reactor.computed(() -> {
            BoatFabricationCatalog.Recipe recipe = fabricationRecipe();
            if (recipe == null || resources.canAfford(resources.quote(recipe.bill()))) return "";
            return Strings.format("openingBoatDeckShort", shortest(resources.quote(recipe.bill())));
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
                damaged ? materialRows("boat-recovery", quote.bill()) : List.of(),
                !damaged || !resources.canAfford(quote.bill()), () -> {
                    BoatWorkshop.Result result = workshop.expedite(quote, day());
                    feedbackText.set(Strings.get(result.status() == BoatWorkshop.Status.REPAIRED
                            ? "boatRepairCompleted" : result.status() == BoatWorkshop.Status.CANNOT_AFFORD
                            ? "boatRepairShort" : "boatRepairChanged"));
                    revision.update(value -> value + 1);
                    if (result.succeeded()) deckChanged.run();
                });
    }

    static String recoveryStatus(CampaignBoat boat, float day) {
        return boat.airworthy(day) ? Strings.get("boatReady") : Strings.format("boatRepairRemaining",
                Math.round(boat.condition(day) * 100f), Math.ceil(boat.repairDaysRemaining(day) * 10f) / 10d);
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
    public Signal<List<PerformanceMeter>> performanceMeters() { return performanceMeters; }
    public Signal<List<SlotRow>> slotRows() { return slotRows; }
    public Signal<String> selectedSlotTitle() { return selectedSlotTitle; }
    public Signal<String> selectedSlotCopy() { return selectedSlotCopy; }
    public Signal<List<CatalogRow>> catalogRows() { return catalogRows; }
    public Signal<String> overviewClasses() { return overviewClasses; }
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
    public Runnable fitAction(String fittingId) { return () -> fit(fittingId); }
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
        revision.update(value -> value + 1);
    }

    private String buildDeckSummary() {
        revision.get();
        List<CampaignBoat> boats = deck.boats().stream().filter(Objects::nonNull).toList();
        List<String> summary = new ArrayList<>();
        summary.add(Strings.format("openingBoatCount", boats.size()));
        if (deck.airworthy(day()).size() < boats.size())
            summary.add(Strings.format("boatDeckReadiness", deck.airworthy(day()).size(), boats.size()));
        ShuttleType pattern = boats.isEmpty() ? null : boats.get(0).pattern();
        if (pattern != null) summary.add(pattern.displayName());
        if (carrierName != null && !carrierName.isBlank())
            summary.add(Strings.format("openingBoatCarrier", carrierName));
        int wrecks = (int) deck.vacantBerths().stream().filter(deck::wreckInBerth).count();
        if (wrecks > 0) summary.add(Strings.format("openingBoatWrecks", wrecks));
        int empty = deck.vacantBerths().size() - wrecks;
        if (empty > 0) summary.add(Strings.format("openingBoatVacancies", empty));
        BoatDeck.LeftBehind left = deck.leftBehind();
        if (left.any()) summary.add(Strings.format("openingBoatLeftBehind", left.count()));
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
                    vacant ? buildableBill() : boat.plating().displayName(),
                    vacant ? "" : boat.airworthy(day()) ? boat.drive().displayName() : recoveryStatus(boat, day()),
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
    private List<PerformanceMeter> buildPerformanceMeters() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        ShuttleType pattern = boat.pattern();
        FittedBoat fitted = boat.freezeForDeployment();
        return List.of(
                meter("hull", Strings.get("openingBoatDeckHull"), Strings.format("openingBoatDeckHp", Math.round(fitted.maxHp() * boat.condition(day()))),
                        fitted.maxHp() * boat.condition(day()),
                        pattern.maxHp() * bestFactor(pattern, BoatFittingSlot.PLATING,
                                BoatFitting::hullFactor)),
                meter("speed", Strings.get("openingBoatDeckSpeed"), Strings.format("openingBoatDeckCellsS", number(fitted.maxSpeed())),
                        fitted.maxSpeed(),
                        pattern.maxSpeed() * bestFactor(pattern, BoatFittingSlot.DRIVE,
                                BoatFitting::speedFactor)),
                meter("seats", Strings.get("openingBoatDeckSeats"), Strings.format("openingBoatDeckSeats2", pattern.capacity),
                        pattern.capacity, pattern.capacity),
                meter("hardpoints", Strings.get("openingBoatDeckHardpoints"), Strings.format("openingBoatMounts", pattern.hardpoints()),
                        pattern.hardpoints(), pattern.hardpoints()));
    }

    private List<SlotRow> buildSlotRows() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        List<SlotRow> rows = new ArrayList<>(BoatFittingSlot.values().length);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitting = boat.fittingIn(slot);
            String base = "boat-slot:" + slot.name().toLowerCase(Locale.ROOT);
            rows.add(new SlotRow(base, base + ":name", base + ":fitting", base + ":tier",
                    slot == selectedSlot.get() ? "boat-slot selected" : "boat-slot",
                    slotLabel(slot), fitting.displayName(), tierLabel(fitting),
                    () -> selectSlot(slot)));
        }
        return List.copyOf(rows);
    }

    private List<CatalogRow> buildCatalogRows() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        BoatFittingSlot slot = selectedSlot.get();
        String installedId = boat.fittingIn(slot).id();
        List<CatalogRow> rows = new ArrayList<>();
        for (BoatFitting fitting : BoatFitting.catalog(slot)) {
            boolean installed = fitting.id().equals(installedId);
            if (boat.pattern() == ShuttleType.HERMES && !installed) continue;
            if (!installed && !fitting.supports(boat.pattern())) continue;
            // A standard fitting is what the boat left the yard with and costs
            // nothing to put back, so the only thing that can stop it is
            // already being on.
            boolean affordable = resources.quote(fitting.bill()) == null
                    || resources.canAfford(resources.quote(fitting.bill()));
            String base = "boat-catalog:" + fitting.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":name", base + ":line",
                    base + ":tier",
                    base + ":effect", base + ":provenance", base + ":reason",
                    base + ":action", base + ":materials",
                    installed ? "catalog-row selected" : "catalog-row",
                    fitting.displayName(), tierLabel(fitting), effect(fitting),
                    fitting.provenance(),
                    installed ? boat.pattern() == ShuttleType.HERMES ? Strings.get("openingBoatDeckFittedNoUpgrades") : Strings.get("openingBoatDeckFitted")
                            : affordable ? "" : Strings.format("openingBoatDeckShort", shortest(resources.quote(fitting.bill()))),
                    Strings.get("openingBoatDeckFit"),
                    installed || !affordable,
                    installed ? List.of() : materialRows(base, resources.quote(fitting.bill())),
                    () -> fit(fitting.id())));
        }
        return List.copyOf(rows);
    }

    private List<Row> materialRows(String ownerId, FabricationCost bill) {
        if (bill == null) return List.of();
        return bill.lines().stream().sorted(Comparator.comparingInt(line -> CompanyResources.order(line.commodityId()))).map(line -> CompanyResources.cost(
                ownerId + ":material:" + line.commodityId(), resources.commodityName(line.commodityId()),
                resources.commodityIcon(line.commodityId()), resources.available(line.commodityId()), line.quantity())).toList();
    }

    private List<Row> buildMaterialStock() {
        revision.get();
        return List.of(Commodities.SUPPLIES, Commodities.HEAVY_MACHINERY,
                Commodities.METALS, Commodities.RARE_METALS).stream().map(commodity ->
                CompanyResources.stock("boat-material-stock:" + commodity, resources.commodityName(commodity),
                        resources.commodityIcon(commodity), resources.available(commodity))).toList();
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
        feedbackText.set(selectedBoat().pattern() == ShuttleType.HERMES
                ? Strings.get("openingBoatDeckHermesFittingsAreInspectionOnlyReplaceThe")
                : Strings.format("openingBoatDeckSelectedEverythingTheYardCanPutHere", slotLabel(slot)));
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
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
            case CANNOT_AFFORD -> Strings.get("openingBoatDeckRefitBlockedRequiredFleetMaterialsAreShort");
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
            case BERTH_OCCUPIED -> Strings.format("openingBoatDeckAlreadyHasABoatStandingInIt", berthLabel(berthIndex));
            default -> Strings.get("openingBoatDeckFabricationBlockedNothingAboardWasChanged");
        });
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        if (result.status() == BoatWorkshop.Status.FABRICATED) {
            selectedBerth.set(berthIndex);
            selectedSlot.set(BoatFittingSlot.PLATING);
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
        return recipe != null && resources.canAfford(resources.quote(recipe.bill()));
    }

    /** What a vacant berth would be built back up as, for its card. */
    private String buildablePattern() {
        BoatFabricationCatalog.Recipe recipe =
                BoatFabricationCatalog.recipe(deck.pattern());
        return recipe == null ? Strings.get("openingBoatDeckNothingStandingHere")
                : Strings.format("openingBoatDeckCanBeBuiltHere", recipe.pattern().displayName());
    }

    /** The bill for that, short enough to sit on one line of a card. */
    private String buildableBill() {
        BoatFabricationCatalog.Recipe recipe =
                BoatFabricationCatalog.recipe(deck.pattern());
        if (recipe == null) return "";
        StringBuilder bill = new StringBuilder();
        for (FabricationCost.Line line : resources.quote(recipe.bill()).lines()) {
            if (bill.length() > 0) bill.append("  ·  ");
            bill.append(resources.commodityName(line.commodityId()))
                    .append(' ').append(line.quantity());
        }
        return bill.toString();
    }

    private CampaignBoat selectedBoat() {
        revision.get();
        int berth = selectedBerth.get();
        List<CampaignBoat> berths = deck.boats();
        return berth >= 0 && berth < berths.size() ? berths.get(berth) : null;
    }

    /** The first line of a bill the hold cannot cover, for a row that has to say why. */
    private String shortest(FabricationCost bill) {
        if (bill == null) return "";
        for (FabricationCost.Line line : bill.lines()) {
            if (resources.available(line.commodityId()) < line.quantity()) {
                return resources.commodityName(line.commodityId()).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    private static float bestFactor(ShuttleType pattern, BoatFittingSlot slot, FittingFactor factor) {
        float best = 1f;
        for (BoatFitting fitting : BoatFitting.catalog(slot)) {
            if (!fitting.supports(pattern)) continue;
            best = Math.max(best, factor.of(fitting));
        }
        return best;
    }

    private static String effect(BoatFitting fitting) {
        if (fitting.standard()) return Strings.get("openingBoatDeckYardStandard");
        if (fitting.slot() == BoatFittingSlot.PLATING) {
            return Strings.format("openingBoatDeckHull2", factor(fitting.hullFactor()));
        }
        return Strings.format("openingBoatDeckSpeedAccel", factor(fitting.speedFactor()), factor(fitting.accelFactor()));
    }

    private static String factor(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String tierLabel(BoatFitting fitting) {
        return Strings.format("openingBoatDeckTier", fitting.tier());
    }

    private static String slotLabel(BoatFittingSlot slot) {
        return slot == BoatFittingSlot.DRIVE ? Strings.get("openingBoatDeckDrive") : Strings.get("openingBoatDeckPlating");
    }

    private static String slotCopy(BoatFittingSlot slot) {
        return slot == BoatFittingSlot.DRIVE
                ? Strings.get("openingBoatDeckWhatGetsHerOffTheDeckAnd")
                : Strings.get("openingBoatDeckWhatAnAntiAirGunHasTo");
    }

    private static String berthLabel(int index) {
        return String.format(Locale.ROOT, Strings.get("openingBoatDeckBerthD"), index + 1);
    }

    private static PerformanceMeter meter(String suffix, String label, String value,
                                          float amount, float maximum) {
        int percent = maximum > 0f ? Math.round(amount / maximum * 100f) : 0;
        String base = "boat-meter:" + suffix;
        return new PerformanceMeter(base, base + ":label", base + ":value",
                base + ":track", base + ":fill", label, value,
                "width: " + Math.max(0, Math.min(100, percent)) + "%;");
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return Integer.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
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

    public record PerformanceMeter(String id, String labelId, String valueId, String trackId,
                                   String fillId, String label, String value, String fillStyle)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "labelId" -> labelId; case "valueId" -> valueId;
            case "trackId" -> trackId; case "fillId" -> fillId; case "label" -> label;
            case "value" -> value; case "fillStyle" -> fillStyle;
            default -> throw unknown("boat-meter", p); }; }
    }

    public record SlotRow(String id, String nameId, String fittingId, String tierId,
                          String classes, String name, String fitting, String tier,
                          Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "nameId" -> nameId; case "fittingId" -> fittingId;
            case "tierId" -> tierId; case "classes" -> classes; case "name" -> name;
            case "fitting" -> fitting; case "tier" -> tier; case "select" -> select;
            default -> throw unknown("boat-slot", p); }; }
    }

    public record CatalogRow(String id, String copyId, String nameId, String lineId,
                             String tierId,
                             String effectId, String provenanceId, String reasonId,
                             String actionId, String materialsId,
                             String classes, String name, String tier, String effect,
                             String provenance, String reason, String actionLabel,
                             boolean actionDisabled, List<Row> materials,
                             Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "copyId" -> copyId; case "nameId" -> nameId;
            case "lineId" -> lineId;
            case "tierId" -> tierId; case "effectId" -> effectId;
            case "provenanceId" -> provenanceId; case "reasonId" -> reasonId;
            case "actionId" -> actionId; case "materialsId" -> materialsId;
            case "classes" -> classes; case "name" -> name; case "tier" -> tier;
            case "effect" -> effect; case "provenance" -> provenance;
            case "reason" -> reason; case "actionLabel" -> actionLabel;
            case "actionDisabled" -> actionDisabled; case "materials" -> materials;
            case "materialsStyle" -> CompanyResources.billHeight(materials);
            case "copyStyle" -> "height: " + (110 + materials.size() * 36) + "px;";
            case "cardStyle" -> "height: " + (190 + materials.size() * 36) + "px;";
            case "action" -> action;
            default -> throw unknown("boat-catalog", p); }; }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
