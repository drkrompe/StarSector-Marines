package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.fitting.FittedBadge;
import com.dillon.starsectormarines.ops.fitting.FittingCard;
import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.ops.fitting.FittingMeter;
import com.dillon.starsectormarines.ops.fitting.FittingSlotCard;
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
import com.dillon.starsectormarines.marine.BoatFrame;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.ops.battleview.BoatDoll;
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
    private final ComputedSignal<List<SlotRow>> slotRows;
    private final ComputedSignal<String> selectedSlotTitle;
    private final ComputedSignal<String> selectedSlotCopy;
    private final ComputedSignal<List<CatalogRow>> catalogRows;
    /** What the stores show: one kind of socket's parts, or null for every part the boat can take. */
    private final MutableSignal<BoatDoll.Kind> storesFilter;
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
        slotRows = reactor.computed(this::buildSelectedMount);
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
    public Signal<List<FittingMeter>> performanceMeters() { return performanceMeters; }
    public Signal<List<SlotRow>> slotRows() { return slotRows; }
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
     * that {@linkplain #socketTakes takes the part}, and a spare to fit, or the socket's own
     * yard-standard part. The drag lights the socket green or red by this, so a refusal shows
     * before the part is let go.
     */
    public boolean canDropOnSocket(BoatDoll.Kind kind, String itemId, String key) {
        if (!socketTakes(kind, itemId, key)) return false;
        CampaignBoat boat = selectedBoat();
        int before = BoatFrame.weight(boat);
        if (kind == BoatDoll.Kind.HARDPOINT) {
            int hardpoint = BoatDoll.hardpointOf(key);
            BoatGun gun = BoatGun.findById(itemId);
            return (gun.standardOn(boat.pattern(), hardpoint) || deck.spares(itemId) > 0)
                    && BoatFrame.admits(boat, before, BoatFrame.weightWithGun(boat, hardpoint, gun));
        }
        BoatFitting fitting = BoatFitting.findById(itemId);
        return (fitting.standard() || deck.spares(itemId) > 0)
                && BoatFrame.admits(boat, before, BoatFrame.weightWith(boat, fitting));
    }

    /**
     * Whether a stores card can be picked up: a spare is in stores, or it is a yard-standard
     * part one of this boat's sockets has for nothing. A card with neither only offers BUY.
     */
    public boolean canPickUp(BoatDoll.Kind kind, String itemId) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || kind == null || itemId == null) return false;
        if (deck.spares(itemId) > 0) return true;
        if (kind != BoatDoll.Kind.HARDPOINT) {
            BoatFitting fitting = BoatFitting.findById(itemId);
            return fitting != null && fitting.standard();
        }
        BoatGun gun = BoatGun.findById(itemId);
        return gun != null && !mountsStandardFor(boat, gun).isEmpty();
    }

    /** Whether the socket holds a part that can be lifted off: anything but its yard-standard part. */
    public boolean canLiftOff(String key) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || key == null) return false;
        int hardpoint = BoatDoll.hardpointOf(key);
        if (hardpoint >= 0) {
            BoatGun gun = boat.gunOn(hardpoint);
            return gun != null && !gun.standardOn(boat.pattern(), hardpoint);
        }
        BoatFittingSlot slot = slotOf(key);
        return slot != null && !boat.fittingIn(slot).standard();
    }

    /** The kind of socket a doll key names, or null for an unknown one. */
    public BoatDoll.Kind kindOf(String key) {
        if (key == null) return null;
        if (BoatDoll.hardpointOf(key) >= 0) return BoatDoll.Kind.HARDPOINT;
        BoatFittingSlot slot = slotOf(key);
        return slot == null ? null : dollKind(slot);
    }

    /** What a socket holds, as a drag lifting it off carries it; null for an unknown socket. */
    public String installedIn(String key) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || key == null) return null;
        int hardpoint = BoatDoll.hardpointOf(key);
        if (hardpoint >= 0) {
            BoatGun gun = boat.gunOn(hardpoint);
            return gun == null ? null : gun.id();
        }
        BoatFittingSlot slot = slotOf(key);
        return slot == null ? null : boat.fittingIn(slot).id();
    }

    /** Whether a part lifted off one socket can go straight onto another: a gun, to another hardpoint. */
    public boolean canMoveBetweenSockets(String from, String to) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || from == null || to == null || from.equals(to) || !canLiftOff(from)) return false;
        int source = BoatDoll.hardpointOf(from);
        int target = BoatDoll.hardpointOf(to);
        return source >= 0 && target >= 0 && boat.gunOn(target) != null && boat.gunOn(target) != boat.gunOn(source);
    }

    /** Moves a gun lifted off one hardpoint onto another; see {@link BoatWorkshop#moveGun}. */
    public void moveBetweenSockets(String from, String to) {
        CampaignBoat boat = selectedBoat();
        if (!canMoveBetweenSockets(from, to)) {
            selectDollSlot(to);
            return;
        }
        int source = BoatDoll.hardpointOf(from);
        int target = BoatDoll.hardpointOf(to);
        BoatGun gun = boat.gunOn(source);
        BoatWorkshop.Result result = workshop.moveGun(boat.id(), source, target);
        selectDollSlot(to);
        report(result, result.succeeded()
                ? Strings.format("boatDeckGunMoved", gun.displayName(), hardpointLabel(source), hardpointLabel(target))
                : Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged"));
    }

    /**
     * Takes the part in one socket off into stores, as dropping it on the stores does; the
     * socket goes back to its yard-standard part.
     */
    public void returnToStores(String key) {
        CampaignBoat boat = selectedBoat();
        if (boat == null || !canLiftOff(key)) return;
        String name = partName(installedIn(key));
        int hardpoint = BoatDoll.hardpointOf(key);
        BoatWorkshop.Result result = hardpoint >= 0
                ? workshop.stripGun(boat.id(), hardpoint)
                : workshop.strip(boat.id(), slotOf(key));
        selectDollSlot(key);
        report(result, result.succeeded()
                ? Strings.format("boatDeckReturnedToStores", name, boat.displayName())
                : Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged"));
    }

    /** Builds one spare of a part into stores out of the fleet's hold. */
    private void fabricatePart(String itemId) {
        BoatWorkshop.Result result = workshop.fabricatePart(itemId);
        report(result, switch (result.status()) {
            case PART_FABRICATED -> Strings.format("boatDeckPartBuilt", partName(itemId));
            case CANNOT_AFFORD -> Strings.get("openingBoatDeckRefitBlockedRequiredFleetMaterialsAreShort");
            default -> Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged");
        });
    }

    private void report(BoatWorkshop.Result result, String text) {
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

    /** The hardpoints whose yard gun this is, in order. */
    private static List<Integer> mountsStandardFor(CampaignBoat boat, BoatGun gun) {
        List<Integer> mounts = new ArrayList<>();
        for (int index = 0; index < boat.pattern().hardpoints(); index++) {
            if (gun.standardOn(boat.pattern(), index)) mounts.add(index);
        }
        return mounts;
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
            BoatGun installed = hardpoint < 0 ? null : boat.gunOn(hardpoint);
            return gun != null && installed != null && installed != gun && gun.supports(boat.pattern());
        }
        BoatFitting fitting = BoatFitting.findById(itemId);
        return fitting != null && hardpoint < 0 && BoatDoll.slotKey(fitting.slot()).equals(key)
                && !fitting.id().equals(boat.fittingIn(fitting.slot()).id()) && fitting.supports(boat.pattern());
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
        if (kind == BoatDoll.Kind.HARDPOINT) fitGun(BoatDoll.hardpointOf(key), itemId);
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
    private List<FittingMeter> buildPerformanceMeters() {
        revision.get();
        CampaignBoat boat = selectedBoat();
        if (boat == null) return List.of();
        ShuttleType pattern = boat.pattern();
        FittedBoat fitted = boat.freezeForDeployment();
        int cabin = boat.fittingIn(BoatFittingSlot.CABIN).seats();
        int seats = cabin > 0 ? cabin : pattern.capacity;
        int maxSeats = pattern.capacity;
        for (BoatFitting part : BoatFitting.catalog(BoatFittingSlot.CABIN)) maxSeats = Math.max(maxSeats, part.seats());
        int weight = BoatFrame.weight(boat);
        int rating = BoatFrame.rating(pattern);
        // A damaged hull reads as a deficit under her pattern's own figure, a refit as gain past it.
        return List.of(
                FittingMeter.of("boat-meter:hull", Strings.get("openingBoatDeckHull"),
                        Strings.format("openingBoatDeckHp", Math.round(fitted.maxHp() * boat.condition(day()))),
                        fitted.maxHp() * boat.condition(day()),
                        pattern.maxHp() * bestFactor(pattern, BoatFittingSlot.PLATING, BoatFitting::hullFactor),
                        fitted.maxHp() * boat.condition(day()), pattern.maxHp(), FittingMeter.PLATE_TONE),
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
                                Strings.format("openingBoatDeckSeats2", seats), seats, maxSeats),
                FittingMeter.of("boat-meter:frame", Strings.get("boatDeckFrame"),
                        Strings.format("boatDeckFrameValue", weight, rating), weight, rating,
                        weight, weight, FittingMeter.FLUX_TONE),
                FittingMeter.of("boat-meter:hardpoints", Strings.get("openingBoatDeckHardpoints"),
                        Strings.format("openingBoatMounts", pattern.hardpoints()),
                        pattern.hardpoints(), pattern.hardpoints(), pattern.hardpoints(), pattern.hardpoints(),
                        FittingMeter.WEAPON_TONE));
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
     * none shows its bill and a BUY that builds one. A yard-standard part is every socket's for
     * nothing and is never counted. Whether a particular socket takes a part is the drop's to
     * say ({@link #canDropOnSocket}), not the card's.
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
                boolean installed = fitting.id().equals(boat.fittingIn(slot).id());
                if (!installed && !fitting.supports(boat.pattern())) continue;
                rows.add(fittingCard(boat, fitting, installed));
            }
        }
        return List.copyOf(rows);
    }

    private CatalogRow gunCard(CampaignBoat boat, BoatGun gun, List<Integer> mounts) {
        boolean free = mountsStandardFor(boat, gun).size() == boat.pattern().hardpoints();
        FabricationCost bill = free ? null : resources.quote(gun.bill());
        boolean held = selectedHardpoint.get() != NO_HARDPOINT && mounts.contains(selectedHardpoint.get());
        String on = mounts.stream().map(index -> Strings.format("boatDeckHardpointShort", index + 1))
                .reduce((a, b) -> a + "  ·  " + b).orElse("");
        return card(BoatDoll.Kind.HARDPOINT, gun.id(), "boat-catalog:gun:" + gun.id(), gun.displayName(),
                gun.tier(), gun.effect(),
                on.isEmpty() ? "" : Strings.format("boatDeckFittedOn", on), !mounts.isEmpty(), held, free, bill);
    }

    private CatalogRow fittingCard(CampaignBoat boat, BoatFitting fitting, boolean installed) {
        boolean held = installed && selectedHardpoint.get() == NO_HARDPOINT && selectedSlot.get() == fitting.slot();
        return card(dollKind(fitting.slot()), fitting.id(), "boat-catalog:" + fitting.id(), fitting.displayName(),
                fitting.tier(), effect(fitting), installed ? "" : change(boat, fitting), installed, held,
                fitting.standard(), fitting.standard() ? null : resources.quote(fitting.bill()));
    }

    /**
     * One stores card: what it is, its tier and how many are in stores, what it does, what it
     * would change or where it is fitted, and its footprint. With none in stores and no free
     * yard part to fall back on, it is greyed with its bill and a BUY beside it, as the lab's are.
     */
    private CatalogRow card(BoatDoll.Kind kind, String itemId, String id, String name, int tier,
                            String effect, String delta, boolean fitted, boolean held, boolean free,
                            FabricationCost bill) {
        int spares = deck.spares(itemId);
        boolean build = !free && spares == 0 && bill != null;
        boolean affordable = build && resources.canAfford(bill);
        String classes = "catalog-row" + (held ? " selected" : "") + (build ? " requires-fabrication" : "");
        int[] footprint = BoatHardware.footprint(kind, itemId);
        int weight = partWeight(kind, itemId, free);
        String detail = Strings.format("boatDeckGrid", footprint[0], footprint[1])
                + (weight > 0 ? "  ·  " + Strings.format("boatDeckWeight", weight) : "");
        List<Row> materials = build ? materialRows(id, bill) : List.of();
        String stock = free ? kindLine(tier, kind)
                : Strings.format("openingMechLabOwnFree", kindLine(tier, kind), MechLabViewModel.spareCount(spares));
        FittingCard card = new FittingCard(id, classes, "catalog-preview", name,
                FittedBadge.label(fitted), FittedBadge.classes(fitted),
                stock, effect, delta,
                delta.isEmpty() ? "catalog-delta neutral" : fitted ? "label heading catalog-delta neutral"
                        : delta.startsWith("-") ? "label heading catalog-delta negative"
                        : "label heading catalog-delta positive",
                detail, build ? Strings.get("openingMechLabFab") : "",
                affordable ? Strings.get("boatDeckFabHint")
                        : Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor"),
                !build || !affordable, true, materials,
                materials.isEmpty() ? "hidden" : "material-costs",
                build ? () -> fabricatePart(itemId) : null, null, null);
        return new CatalogRow(card, kind, itemId);
    }

    /** What fitting this would do to her figures against what she carries now. */
    private String change(CampaignBoat boat, BoatFitting fitting) {
        ShuttleType pattern = boat.pattern();
        BoatFitting installed = boat.fittingIn(fitting.slot());
        return switch (fitting.slot()) {
            case PLATING -> signed(Math.round(pattern.maxHp() * (fitting.hullFactor() - installed.hullFactor())),
                    Strings.get("openingBoatDeckHull"));
            case DRIVE -> signed(pattern.maxSpeed() * (fitting.speedFactor() - installed.speedFactor()),
                    Strings.get("openingBoatDeckSpeed"));
            case LIFT -> Strings.format("boatDeckLiftEffect", LiftReadout.ratingName(fitting.liftRating()));
            case CABIN, EMITTER, POWER -> effect(fitting);
        };
    }

    private static String signed(float amount, String stat) {
        String value = number(Math.abs(amount));
        return (amount < 0f ? "-" : "+") + value + " " + stat;
    }

    /** The hardpoints a gun is on, in order. */
    private static List<Integer> mountsCarrying(CampaignBoat boat, BoatGun gun) {
        List<Integer> mounts = new ArrayList<>();
        for (int index = 0; index < boat.pattern().hardpoints(); index++) {
            if (boat.gunOn(index) == gun) mounts.add(index);
        }
        return mounts;
    }

    /**
     * The socket the board has selected, as the lab's selected-mount card shows one: what it
     * is and holds, what that does, and its kind and size, with the piece on its bricks.
     */
    private List<SlotRow> buildSelectedMount() {
        BoatDoll shown = buildDoll();
        if (shown == null) return List.of();
        for (BoatDoll.Slot slot : shown.slots()) {
            if (!slot.selected()) continue;
            int[] socket = BoatHardware.socket(slot.kind());
            String base = "boat-mount:" + slot.key();
            String key = slot.key();
            FittingSlotCard card = new FittingSlotCard(base, "doll-slot selected", slot.title(),
                    FittedBadge.label(true), FittedBadge.slotClasses(true), slot.fitted(), slot.tier(),
                    kindSingular(slot.kind()) + " / " + Strings.format("boatDeckGrid", socket[0], socket[1]),
                    false, "hidden", () -> selectDollSlot(key), null);
            return List.of(new SlotRow(card, key, slot.kind(), slot.itemId()));
        }
        return List.of();
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

    /** What a stores part weighs on this boat: nothing for a free yard part. */
    private static int partWeight(BoatDoll.Kind kind, String itemId, boolean free) {
        if (free) return 0;
        if (kind == BoatDoll.Kind.HARDPOINT) {
            BoatGun gun = BoatGun.findById(itemId);
            return gun == null ? 0 : gun.weight();
        }
        BoatFitting fitting = BoatFitting.findById(itemId);
        return fitting == null ? 0 : fitting.weight();
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
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            if (slot == BoatFittingSlot.LIFT && !liftable(pattern)) continue;
            BoatFitting fitting = boat.fittingIn(slot);
            slots.add(BoatDoll.Slot.fitting(slot, fitting.id(), slotLabel(slot), fitting.displayName(),
                    tierLabel(fitting), hardpoint == NO_HARDPOINT && slot == selectedSlot.get()));
        }
        List<WeaponSlot> mounts = TurretSlotResolver.mounts(pattern.renderHullId());
        for (int index = 0; index < pattern.hardpoints(); index++) {
            BoatGun gun = boat.gunOn(index);
            if (gun == null) continue;
            WeaponSlot mount = index < mounts.size() ? mounts.get(index) : null;
            slots.add(BoatDoll.Slot.hardpoint(index, gun.id(), hardpointLabel(index), gun.displayName(),
                    gunTier(boat, index, gun), index == hardpoint, mount));
        }
        return new BoatDoll(pattern, boat.displayName(), pattern.displayName(), List.copyOf(slots));
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
        if (boat == null || boat.gunOn(hardpoint) == null) return;
        selectedHardpoint.set(hardpoint);
        storesFilter.set(BoatDoll.Kind.HARDPOINT);
        feedbackText.set(Strings.format("openingBoatDeckSelectedEverythingTheYardCanPutHere", hardpointLabel(hardpoint)));
        feedbackClasses.set("boat-deck-feedback tone-muted surface-dark");
    }

    private void fitGun(int hardpoint, String gunId) {
        CampaignBoat boat = selectedBoat();
        BoatGun gun = BoatGun.findById(gunId);
        if (boat == null || gun == null) return;
        BoatWorkshop.Result result = workshop.fitGun(boat.id(), hardpoint, gunId);
        feedbackText.set(switch (result.status()) {
            case FITTED -> Strings.format("boatDeckGunFitted", gun.displayName(), hardpointLabel(hardpoint), boat.displayName());
            case ALREADY_FITTED -> Strings.format("openingBoatDeckIsAlreadyFittedTo", gun.displayName(), boat.displayName());
            case NO_SPARE -> Strings.format("boatDeckNoSpare", gun.displayName());
            case OVER_FRAME -> Strings.format("boatDeckOverFrame", gun.displayName(),
                    BoatFrame.weightWithGun(boat, hardpoint, gun), BoatFrame.rating(boat.pattern()));
            case INCOMPATIBLE_FITTING -> Strings.get("boatDeckHardpointHermes");
            default -> Strings.get("openingBoatDeckRefitBlockedNothingAboardWasChanged");
        });
        feedbackClasses.set(result.succeeded()
                ? "boat-deck-feedback tone-good surface-dark"
                : "boat-deck-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
        if (result.succeeded()) deckChanged.run();
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
            case OVER_FRAME -> Strings.format("boatDeckOverFrame", fitting.displayName(),
                    BoatFrame.weightWith(boat, fitting), BoatFrame.rating(boat.pattern()));
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
                return Strings.upper(resources.commodityName(line.commodityId()));
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

    private String effect(BoatFitting fitting) {
        CampaignBoat boat = selectedBoat();
        return switch (fitting.slot()) {
            case PLATING -> Strings.format("openingBoatDeckHull2", factor(fitting.hullFactor()));
            case LIFT -> Strings.format("boatDeckLiftEffect", LiftReadout.ratingName(fitting.liftRating()));
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

    private static BoatDoll.Kind dollKind(BoatFittingSlot slot) {
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

    /** A yard gun reads as yard issue rather than a tier, so a refit reads as one. */
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
    public record SlotRow(FittingSlotCard card, String key, BoatDoll.Kind kind, String itemId)
            implements MarkupPropertySource {
        public String id() { return card.id(); }
        public String gridId() { return card.gridId(); }
        public String classes() { return card.classes(); }
        public String name() { return card.name(); }
        public String fitting() { return card.component(); }
        public String tier() { return card.telemetry(); }
        public Runnable select() { return card.select(); }

        @Override public Object markupProperty(String p) { return card.markupProperty(p); }
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
