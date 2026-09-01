package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatDeck;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Berth projection and refit command surface for the campaign-authoritative
 * {@link BoatDeck}.
 *
 * <p>The Mech Lab's grammar over a different noun: an overview of everything in
 * the room, one selected asset, its slots, and a catalog scoped to the selected
 * slot. What differs is what a fitting <em>is</em> — installed work rather than
 * a spare — so there is no stock column, nothing comes back to stores, and the
 * only question a row has to answer is whether the fleet can pay for it.
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
                "Select a berth to inspect the boat standing in it and refit her.");
        feedbackClasses = reactor.signal("boat-deck-feedback tone-muted surface-dark");
        deckSummary = reactor.computed(this::buildDeckSummary);
        boatRows = reactor.computed(this::buildBoatRows);
        selectedBoatName = reactor.computed(() -> {
            CampaignBoat boat = selectedBoat();
            return boat != null ? boat.displayName() : "NO BOAT SELECTED";
        });
        selectedBoatIdentity = reactor.computed(() -> {
            CampaignBoat boat = selectedBoat();
            return boat == null ? "Select a berth to open her."
                    : boat.pattern().displayName() + "  ·  " + berthLabel(selectedBerth.get());
        });
        performanceMeters = reactor.computed(this::buildPerformanceMeters);
        slotRows = reactor.computed(this::buildSlotRows);
        selectedSlotTitle = reactor.computed(() -> slotLabel(selectedSlot.get()));
        selectedSlotCopy = reactor.computed(() -> slotCopy(selectedSlot.get()));
        catalogRows = reactor.computed(this::buildCatalogRows);
        overviewClasses = reactor.computed(() -> fittingFocused()
                ? "boat-overview panel hidden" : "boat-overview panel");
        fittingClasses = reactor.computed(() -> fittingFocused()
                ? "boat-fitting" : "boat-fitting hidden");
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
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

    public Runnable selectBoatAction(int berthIndex) { return () -> selectBoat(berthIndex); }
    public Runnable selectSlotAction(BoatFittingSlot slot) { return () -> selectSlot(slot); }
    public Runnable fitAction(String fittingId) { return () -> fit(fittingId); }
    public Runnable backToDeckAction() { return this::showDeckOverview; }

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
        List<CampaignBoat> boats = deck.airworthy();
        StringBuilder summary = new StringBuilder();
        summary.append(boats.size())
                .append(boats.size() == 1 ? " boat aboard" : " boats aboard");
        ShuttleType pattern = boats.isEmpty() ? null : boats.get(0).pattern();
        if (pattern != null) summary.append("  ·  ").append(pattern.displayName());
        if (carrierName != null && !carrierName.isBlank()) {
            summary.append("  ·  carried by ").append(carrierName);
        }
        BoatDeck.LeftBehind left = deck.leftBehind();
        if (left.any()) {
            summary.append("  ·  ").append(left.count())
                    .append(left.count() == 1 ? " boat" : " boats")
                    .append(" left with her last ship");
        }
        return summary.toString();
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
            rows.add(new BoatRow(base, base + ":berth", base + ":name", base + ":pattern",
                    base + ":plating", base + ":drive",
                    index == selected ? "boat-card selected"
                            : vacant ? "boat-card vacant" : "boat-card",
                    berthLabel(index),
                    vacant ? "EMPTY BERTH" : boat.displayName(),
                    vacant ? "Nothing standing here" : boat.pattern().displayName(),
                    vacant ? "—" : boat.plating().displayName(),
                    vacant ? "—" : boat.drive().displayName(),
                    vacant, () -> selectBoat(berth)));
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
                meter("hull", "HULL", Math.round(fitted.maxHp()) + " HP",
                        fitted.maxHp(),
                        pattern.maxHp() * bestFactor(BoatFittingSlot.PLATING,
                                BoatFitting::hullFactor)),
                meter("speed", "SPEED", number(fitted.maxSpeed()) + " CELLS/S",
                        fitted.maxSpeed(),
                        pattern.maxSpeed() * bestFactor(BoatFittingSlot.DRIVE,
                                BoatFitting::speedFactor)),
                meter("seats", "SEATS", pattern.capacity + " SEATS",
                        pattern.capacity, pattern.capacity),
                meter("hardpoints", "HARDPOINTS", pattern.hardpoints()
                                + (pattern.hardpoints() == 1 ? " MOUNT" : " MOUNTS"),
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
            // A standard fitting is what the boat left the yard with and costs
            // nothing to put back, so the only thing that can stop it is
            // already being on.
            boolean affordable = fitting.bill() == null
                    || resources.canAfford(fitting.bill());
            String base = "boat-catalog:" + fitting.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":name", base + ":line",
                    base + ":tier",
                    base + ":effect", base + ":provenance", base + ":reason",
                    base + ":action", base + ":materials",
                    installed ? "catalog-row selected" : "catalog-row",
                    fitting.displayName(), tierLabel(fitting), effect(fitting),
                    fitting.provenance(),
                    installed ? "FITTED" : affordable ? "" : "SHORT " + shortest(fitting.bill()),
                    "FIT",
                    installed || !affordable,
                    materialRows(base, fitting.bill()),
                    () -> fit(fitting.id())));
        }
        return List.copyOf(rows);
    }

    private List<MaterialRow> materialRows(String ownerId, FabricationCost bill) {
        if (bill == null) return List.of();
        List<MaterialRow> rows = new ArrayList<>(bill.lines().size());
        for (FabricationCost.Line line : bill.lines()) {
            int available = resources.available(line.commodityId());
            String id = ownerId + ":material:" + line.commodityId();
            rows.add(new MaterialRow(id, id + ":icon", id + ":label",
                    available >= line.quantity() ? "material-cost" : "material-cost short",
                    resources.commodityIcon(line.commodityId()),
                    resources.commodityName(line.commodityId()).toUpperCase(Locale.ROOT)
                            + " " + available + " / " + line.quantity()));
        }
        return List.copyOf(rows);
    }

    private void selectBoat(int berthIndex) {
        List<CampaignBoat> berths = deck.boats();
        if (berthIndex < 0 || berthIndex >= berths.size()) return;
        CampaignBoat boat = berths.get(berthIndex);
        if (boat == null) return;
        selectedBerth.set(berthIndex);
        selectedSlot.set(BoatFittingSlot.PLATING);
        feedbackText.set("Inspecting " + boat.displayName() + ". Nothing has been refitted.");
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
        revision.update(value -> value + 1);
        deckChanged.run();
    }

    private void selectSlot(BoatFittingSlot slot) {
        if (slot == null || selectedBoat() == null) return;
        selectedSlot.set(slot);
        feedbackText.set(slotLabel(slot) + " selected. "
                + "Everything the yard can put here is shown with its bill.");
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
    }

    private void showDeckOverview() {
        selectedBerth.set(NO_BERTH);
        feedbackText.set("Boat deck restored. Select a berth to open the boat standing in it.");
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
            case FITTED -> fitting.displayName() + " fitted to " + boat.displayName()
                    + " out of fleet stores.";
            case ALREADY_FITTED -> fitting.displayName() + " is already fitted to "
                    + boat.displayName() + ".";
            case CANNOT_AFFORD -> "Refit blocked: required fleet materials are short.";
            default -> "Refit blocked: nothing aboard was changed.";
        });
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
        if (result.succeeded()) deckChanged.run();
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

    private static float bestFactor(BoatFittingSlot slot, FittingFactor factor) {
        float best = 1f;
        for (BoatFitting fitting : BoatFitting.catalog(slot)) {
            best = Math.max(best, factor.of(fitting));
        }
        return best;
    }

    private static String effect(BoatFitting fitting) {
        if (fitting.standard()) return "Yard standard";
        if (fitting.slot() == BoatFittingSlot.PLATING) {
            return "Hull ×" + factor(fitting.hullFactor());
        }
        return "Speed ×" + factor(fitting.speedFactor())
                + "  ·  Accel ×" + factor(fitting.accelFactor());
    }

    private static String factor(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String tierLabel(BoatFitting fitting) {
        return "TIER " + fitting.tier();
    }

    private static String slotLabel(BoatFittingSlot slot) {
        return slot == BoatFittingSlot.DRIVE ? "DRIVE" : "PLATING";
    }

    private static String slotCopy(BoatFittingSlot slot) {
        return slot == BoatFittingSlot.DRIVE
                ? "What gets her off the deck and away again. A quicker boat is not a "
                        + "faster turnaround: the rotation is a fact about hands."
                : "What an anti-air gun has to get through on the run in. Plate is "
                        + "welded on, so what comes off is scrapped rather than stored.";
    }

    private static String berthLabel(int index) {
        return String.format(Locale.ROOT, "BERTH %02d", index + 1);
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
                          String plating, String drive, boolean disabled, Runnable select)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "berthId" -> berthId; case "nameId" -> nameId;
            case "patternId" -> patternId; case "platingId" -> platingId;
            case "driveId" -> driveId; case "classes" -> classes; case "berth" -> berth;
            case "name" -> name; case "pattern" -> pattern; case "plating" -> plating;
            case "drive" -> drive; case "disabled" -> disabled; case "select" -> select;
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
                             boolean actionDisabled, List<MaterialRow> materials,
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
            case "action" -> action;
            default -> throw unknown("boat-catalog", p); }; }
    }

    public record MaterialRow(String id, String iconId, String labelId,
                              String classes, String icon, String label)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "iconId" -> iconId; case "labelId" -> labelId;
            case "classes" -> classes; case "icon" -> icon; case "label" -> label;
            default -> throw unknown("boat-material", p); }; }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
