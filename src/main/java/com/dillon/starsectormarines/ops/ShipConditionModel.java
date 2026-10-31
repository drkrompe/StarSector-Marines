package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.ShipConditionStage;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitOrder;
import com.dillon.starsectormarines.campaign.refit.RefitText;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration.Work;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Inspection and explicit reviewed orders over campaign-owned condition; no second inventory. */
public final class ShipConditionModel {
    private final Supplier<CompanyDeck> deck;
    private final Function<Work, CompanyShipRestoration.Offer> offers;
    private final Function<Work, String> repair;
    private final Supplier<CompanyBayRestoration.Offer> bayOffer;
    private final Supplier<String> repairBay;
    private final Supplier<ShipCondition> condition;
    private final Function<RefitOrder, ShipRefits.Offer> refitOffers;
    private final Function<RefitOrder, String> refit;
    private final CommodityPresentation goods;
    private final Map<RefitTrack, Runnable> trackSelections = new LinkedHashMap<>();
    private final Map<RefitTrack, Runnable> chipSelections = new LinkedHashMap<>();
    private RefitTrack track = RefitTrack.POWER;
    private boolean systems;
    private BiConsumer<RefitOrder, Supplier<String>> stage = (order, commit) -> commit.get();
    private final Consumer<Compartment> focus;
    private final Runnable changed;
    private final MutableSignal<Map<String, Object>> row;
    private final Runnable action = this::act;
    private final Map<Integer, Runnable> selections = new LinkedHashMap<>();
    private Runnable selection(int id) { return selections.computeIfAbsent(id, key -> () -> select(key)); }
    private int selected = -1;
    private String reviewed, feedback = "";
    public ShipConditionModel(Reactor reactor, Supplier<CompanyDeck> deck,
                              Function<Work, CompanyShipRestoration.Offer> offers, Function<Work, String> repair,
                              Supplier<CompanyBayRestoration.Offer> bayOffer, Supplier<String> repairBay,
                              Consumer<Compartment> focus, Runnable changed) {
        this(reactor, deck, offers, repair, bayOffer, repairBay, () -> ShipCondition.NONE,
                order -> new ShipRefits.Offer(ShipCondition.NONE, 0, ""), order -> "", CommodityPresentation.NONE, focus, changed);
    }
    public ShipConditionModel(Reactor reactor, Supplier<CompanyDeck> deck,
                              Function<Work, CompanyShipRestoration.Offer> offers, Function<Work, String> repair,
                              Supplier<CompanyBayRestoration.Offer> bayOffer, Supplier<String> repairBay,
                              Supplier<ShipCondition> condition, Function<RefitOrder, ShipRefits.Offer> refitOffers,
                              Function<RefitOrder, String> refit, CommodityPresentation goods,
                              Consumer<Compartment> focus, Runnable changed) {
        this.deck = deck; this.offers = offers; this.repair = repair; this.bayOffer = bayOffer;
        this.repairBay = repairBay; this.condition = condition; this.refitOffers = refitOffers; this.refit = refit;
        this.goods = goods;
        this.focus = focus; this.changed = changed;
        row = reactor.signal(workshopOnly());
        refresh();
    }
    public Signal<Map<String, Object>> row() { return row; }
    public int selectedRoomId() { return selected; }
    public void select(int id) { selected = id; reviewed = null; feedback = ""; refocus(); refresh(); }
    public void refocus() {
        var ship = deck.get();
        if (ship != null && ship.ready()) focus.accept(ship.rooms().compartments().stream().filter(r -> r.id() == selected).findFirst().orElse(null));
    }
    public static Map<String, Object> workshopOnly() {
        var m = new LinkedHashMap<String, Object>();
        m.put("classes", "condition-panel"); m.put("rooms", List.of()); m.put("portrait", CompanyManagementGuide.portrait());
        m.put("title", Strings.get("openingShipConditionShipCondition")); m.put("copy", ""); m.put("benefit", ""); m.put("bill", ""); m.put("billRows", List.of()); m.put("reason", "");
        m.put("action", (Runnable) () -> {}); m.put("actionLabel", Strings.get("openingShipConditionReviewRepair")); m.put("disabled", true);
        m.put("actionClasses", "hidden"); m.put("detailClasses", "hidden"); m.put("workshopClasses", "restoration-panel");
        m.put("feedback", "");
        m.put("powerClasses", "hidden"); m.put("powerLabel", Strings.get("openingShipConditionDistributionCircuits"));
        m.put("powerAction", (Runnable) () -> {});
        m.put("status", "");
        m.put("billStyle", "height: 36px;");
        m.put("stripClasses", "hidden"); m.put("chips", List.of()); m.put("stripLabel", "");
        m.put("systemsClasses", "hidden"); m.put("cores", List.of()); m.put("systemsProgress", "");
        m.put("trackIcon", ""); m.put("trackIconClasses", "hidden");
        m.put("needsClasses", "hidden"); m.put("needs", List.of());
        return m;
    }
    public void refresh() {
        var ship = deck.get();
        if (ship == null || !ship.ready()) return;
        var graph = ship.rooms();
        var state = ship.ship().restoration();
        var rooms = new ArrayList<Map<String, Object>>();
        for (var room : graph.compartments().stream().sorted(Comparator.comparing((Compartment c) -> !graph.damaged(c.id())).thenComparingInt(Compartment::id)).toList()) {
            boolean sealed = ShipConditionStage.sealed(graph, room, state)
                    || room.purpose() == RoomPurpose.HANGAR && graph.damaged(room.id())
                    || room.equals(graph.largest(RoomPurpose.PARTS_CAGE)) && ship.ship().workshop() == WorkshopState.BURIED;
            String status = sealed ? Strings.get(graph.reserved(room.id())
                    ? "openingShipConditionSealedReserved" : "openingShipConditionSealedDamaged") : graph.damaged(room.id()) ? Strings.get("openingShipConditionDamagedAccessible")
                    : graph.emergencyLighting(room.id()) ? Strings.get("openingShipConditionFaultyPower") : Strings.get("openingShipConditionWorking");
            if (state.applies() && room.purpose() == RoomPurpose.VEHICLE_BAY && state.mechBay())
                status = Strings.format("openingShipConditionBerthsWorking", state.workingMechBerths());
            String condition = sealed ? "sealed" : graph.damaged(room.id()) ? "repair" : graph.emergencyLighting(room.id()) ? "power" : "";
            String classes = condition.isEmpty() ? "survey-room healthy" : "survey-room";
            rooms.add(Map.of("roomId", room.id(), "icon", condition.isEmpty() ? "" : "graphics/ui/company/condition-" + condition + ".svg",
                    "nameId", "survey-name-" + room.id(), "id", "survey-room-" + room.id(), "label", Strings.format("openingShipConditionRoomNumbered", name(room), room.id() + 1),
                    "status", status, "classes", classes + (room.id() == selected ? " selected" : ""),
                    "select", selection(room.id())));
        }
        var selectedRoom = graph.compartments().stream().filter(r -> r.id() == selected).findFirst().orElse(null);
        boolean workshop = selectedRoom != null && selectedRoom.equals(graph.largest(RoomPurpose.PARTS_CAGE)) && ship.ship().workshop() != WorkshopState.NONE;
        var m = workshopOnly();
        m.put("status", rooms.stream().filter(r -> ((Integer) r.get("roomId")) == selected)
                .map(r -> r.get("status")).findFirst().orElse(state.applies() ? (state.power() ? Strings.get("openingShipConditionRestored") : Strings.get("openingShipConditionFaultyPower")) : Strings.get("openingShipConditionShipboardServices")));
        var refitState = condition.get();
        if (!refitState.applies()) systems = false;
        m.put("powerClasses", state.applies() && !refitState.applies() ? "survey-power console-action" : "hidden");
        m.put("powerLabel", Strings.format("openingShipConditionPowerStatus", state.power() ? Strings.get("openingShipConditionRestored") : Strings.get("openingShipConditionFaultyPower")));
        m.put("powerAction", selection(-1));
        if (state.applies() && refitState.applies() && !systems) {
            m.put("stripClasses", "systems-strip");
            m.put("chips", chips(refitState));
            m.put("stripLabel", Strings.format("openingRefitSystemsProgress", tiersDone(refitState), tiersOffered()));
        }
        if (systems) {
            m.put("systemsClasses", "systems-view");
            m.put("systemsProgress", Strings.format("openingRefitSystemsProgress", tiersDone(refitState), tiersOffered()));
            m.put("cores", ShipSystemsTree.cores(refitState, track, this::trackSelection));
        }
        m.put("rooms", List.copyOf(rooms));
        m.put("workshopClasses", workshop ? "restoration-panel" : "hidden");
        m.put("detailClasses", workshop ? "hidden" : "condition-detail");
        m.put("title", selectedRoom == null ? (state.applies() ? (refitState.applies() ? Strings.get("openingRefitSystemsTitle")
                : Strings.get("openingShipConditionDistributionCircuits")) : Strings.get("openingShipConditionShipCondition")) : name(selectedRoom));
        String copy = Strings.get("openingShipConditionSheSBeenThroughHardServiceThe");
        String benefit = Strings.format("openingShipConditionInService", function(selectedRoom == null ? null : selectedRoom.purpose()));
        String reason = Strings.get("openingShipConditionNoStructuralWorkRequiredHere");
        String bill = "";
        Work work = null;
        RefitTrack shown = null;
        boolean bay = false;
        RefitTrack reserveTrack = selectedRoom != null && graph.reserved(selectedRoom.id())
                ? RefitTrack.forReserve(graph.reserve(selectedRoom.id())) : null;
        if (reserveTrack != null && refitState.applies()) {
            shown = reserveTrack;
            copy = refitState.band(shown) == RefitBand.DEFICIT ? Strings.get("openingShipConditionReservedCompartmentCopy")
                    : Strings.get("openingRefitReserveOpenCopy");
        } else if (selectedRoom != null && graph.reserved(selectedRoom.id())) {
            copy = Strings.get("openingShipConditionReservedCompartmentCopy");
            benefit = Strings.format("openingShipConditionReservedFor", function(selectedRoom.purpose()));
            reason = Strings.get("openingShipConditionReservedNoOrderYet");
        } else if (state.applies() && selectedRoom == null && refitState.applies()) {
            shown = track;
            copy = RefitText.lore(track);
        } else if (state.applies() && selectedRoom == null) {
            copy = state.power() ? Strings.get("openingShipConditionSteadyLightsAgainWeHaveReplacedThe")
                    : Strings.get("openingShipConditionWeAreNursingOldCableAndScorched");
            benefit = Strings.get("openingShipConditionRestoreSteadyLightsThroughoutTheWorkingCompartments");
            if (!state.power()) work = Work.POWER;
        } else if (selectedRoom != null && state.applies() && selectedRoom.purpose() == RoomPurpose.VEHICLE_BAY) {
            int berths = state.workingMechBerths();
            copy = !state.mechBay() ? Strings.get("openingShipConditionThereIsRoomForAFullLance")
                    : berths < 4 ? Strings.get("openingShipConditionWaywardHasAHomeTheRecoveryCrew")
                    : Strings.get("openingShipConditionFourWorkingGantriesWeHaveEarnedBack");
            benefit = !state.mechBay() ? Strings.get("openingShipConditionMissionTakeBackTheDepotRestoresBerth")
                    : Strings.format("openingShipConditionWorkingMechBerths", berths, (berths < 4
                    ? Strings.format("openingShipConditionNextRestoreBerthForOneAdditionalMech", (berths + 1))
                    : Strings.get("openingShipConditionVisitMechLabToFabricateOrFit")));
            reason = state.mechBay() ? Strings.get("openingShipConditionTheFirstBerthWasRestoredWithThe") : Strings.get("openingShipConditionLockedUntilTheSecondStoryMissionIs");
            if (state.mechBay() && berths < 4) work = switch (berths) {
                case 2 -> Work.MECH_BERTH_3;
                default -> Work.MECH_BERTH_4;
            };
        } else if (selectedRoom != null && state.applies() && selectedRoom.purpose() == RoomPurpose.MESS_HALL && refitState.applies()) {
            shown = RefitTrack.MESS;
            copy = state.messHall() ? Strings.get("openingShipConditionHotMealsAndATableTogetherYou")
                    : Strings.get("openingShipConditionTheMessTookAHitWeAre");
        } else if (selectedRoom != null && state.applies() && selectedRoom.purpose() == RoomPurpose.MESS_HALL) {
            copy = state.messHall() ? Strings.get("openingShipConditionHotMealsAndATableTogetherYou")
                    : Strings.get("openingShipConditionTheMessTookAHitWeAre");
            benefit = Strings.format("openingShipConditionCompanyInfantryRecoverMoraleFasterOnceIncoming", (state.messHall() ? Strings.get("openingShipConditionRestored2") : Strings.get("openingShipConditionNext")));
            reason = state.messHall() ? Strings.get("openingShipConditionMoraleLimitsAndRecoveryDelayAreUnchanged") : Strings.get("openingShipConditionFieldRationsKeepTheCompanyReadyFor");
            if (!state.messHall()) work = Work.MESS_HALL;
        } else if (selectedRoom != null && state.applies() && ShipConditionStage.spareBillets(graph).contains(selectedRoom)) {
            copy = state.billets() ? Strings.get("openingShipConditionFreshRacksSealedPlatingAndRoomFor")
                    : Strings.get("openingShipConditionTheseTwoBunkroomsTookTheSameHit");
            int bunks = ShipConditionStage.spareBillets(graph).size() * RoomRecipe.TROOP_BERTHING.provides();
            benefit = Strings.format("openingShipConditionUsableCompanyBilletsInTheReservedBunkrooms", (state.billets() ? Strings.get("openingShipConditionRestored2") : Strings.get("openingShipConditionNextRestore")) + bunks);
            if (!state.billets()) work = Work.BILLETS;
        } else if (selectedRoom != null && selectedRoom.purpose() == RoomPurpose.HANGAR && graph.damaged(selectedRoom.id())) {
            copy = Strings.get("openingShipConditionASecondBoatBelongsHereOnceThe");
            benefit = Strings.get("openingShipConditionRestoreTheSecondShuttleBerthAndLaunch");
            bay = true;
        } else if (selectedRoom != null && graph.emergencyLighting(selectedRoom.id())) {
            copy = Strings.get("openingShipConditionTheFittingsStillDoTheirJobBut");
            reason = Strings.get("openingShipConditionSelectDistributionCircuitsToReviewTheShip");
        }
        if (shown != null) {
            RefitBand band = refitState.band(shown);
            RefitOrder next = refitState.next(shown);
            m.put("status", Strings.format("openingRefitRowState", RefitText.band(band), RefitText.tier(shown, band)));
            if (selectedRoom == null) m.put("title", Strings.upper(RefitText.track(shown)));
            m.put("trackIcon", ShipSystemsTree.icon(shown));
            m.put("trackIconClasses", "survey-track-icon");
            benefit = Strings.format("openingRefitNow", RefitText.effect(shown, band));
            if (next != null) benefit += "\n" + Strings.format("openingRefitNext",
                    Strings.format("openingRefitRowState", RefitText.tier(shown, next.band), RefitText.effect(shown, next.band)));
            reason = shown == RefitTrack.FABRICATION ? Strings.get("openingRefitWorkshopCard") : Strings.get("openingRefitAtCeiling");
            if (next != null && !next.requires.isEmpty()) {
                m.put("needsClasses", "survey-needs");
                m.put("needs", needs(refitState, next));
            }
            if (next != null) {
                var offer = refitOffers.apply(next);
                String key = "refit:" + next.name();
                bill = CompanyResources.creditBill(next.price, offer.credits());
                var rows = new ArrayList<CompanyResources.Row>(CompanyResources.credits("survey-repair-cost", offer.credits(), next.price));
                for (var need : next.cores().entrySet())
                    rows.add(CompanyResources.cost("survey-repair-" + slug(need.getKey()), goods.commodityName(need.getKey()),
                            goods.commodityIcon(need.getKey()), offer.held(need.getKey()), need.getValue()));
                m.put("billRows", List.copyOf(rows));
                m.put("billStyle", CompanyResources.billHeight(rows));
                reason = offer.refusal() != null ? offer.refusal() : next.cores().isEmpty()
                        ? Strings.get("openingShipConditionPlanetaryYardLaborAndMaterialsIncluded")
                        : Strings.get("openingRefitMaterialsFromCargo");
                m.put("actionClasses", "restoration-action console-action");
                m.put("actionLabel", key.equals(reviewed) ? Strings.get("openingShipConditionAuthorizeD") : Strings.get("openingShipConditionReviewRepair"));
                m.put("disabled", offer.refusal() != null && key.equals(reviewed));
                m.put("order", key);
            }
        } else if (work != null || bay) {
            var offer = work == null ? null : offers.apply(work);
            var boat = bay ? bayOffer.get() : null;
            int price = bay ? CompanyBayRestoration.CREDIT_COST : work.price;
            int credits = bay ? boat.credits() : offer.credits();
            String refusal = bay ? boat.refusal() : offer.refusal();
            String key = bay ? "bay" : work.name();
            bill = CompanyResources.creditBill(price, credits);
            m.put("billRows", CompanyResources.credits("survey-repair-cost", credits, price));
            reason = refusal == null ? Strings.get("openingShipConditionPlanetaryYardLaborAndMaterialsIncluded") : refusal;
            m.put("actionClasses", "restoration-action console-action");
            m.put("actionLabel", key.equals(reviewed) ? Strings.get("openingShipConditionAuthorizeD") : Strings.get("openingShipConditionReviewRepair"));
            m.put("disabled", refusal != null && key.equals(reviewed));
            m.put("order", key);
        }
        m.put("copy", copy); m.put("benefit", benefit); m.put("bill", bill); m.put("reason", reason);
        m.put("action", action); m.put("feedback", feedback);
        row.set(Map.copyOf(m));
    }
    private void act() {
        refresh();
        String key = (String) row.get().get("order");
        if (key == null) return;
        if (!key.equals(reviewed)) { reviewed = key; feedback = Strings.get("openingShipConditionReviewTheCostAndRestoredFacilitiesBefore"); }
        else if (key.startsWith("refit:")) {
            // A refit is paid for where the player can watch it land; see RefitReveal.
            RefitOrder order = RefitOrder.valueOf(key.substring(6));
            reviewed = null;
            feedback = "";
            stage.accept(order, () -> commit(order));
        } else {
            String refusal = key.equals("bay") ? repairBay.get()
                    : repair.apply(Work.valueOf(key));
            feedback = refusal == null ? Strings.get("openingShipConditionYardWorkComplete") : refusal;
            reviewed = null;
            if (refusal == null) changed.run();
        }
        refresh();
    }
    /**
     * Who plays an authorized refit out before it is paid for. The default pays at once; the deck
     * overlay stages it on the ship and calls {@code commit} under its curtain.
     */
    public void stageWith(BiConsumer<RefitOrder, Supplier<String>> stage) { this.stage = stage; }

    /** Pays for {@code order} now; the yard's refusal, or null once the work is done. */
    private String commit(RefitOrder order) {
        String refusal = refit.apply(order);
        feedback = refusal == null ? Strings.get("openingShipConditionYardWorkComplete") : refusal;
        if (refusal == null) changed.run();
        refresh();
        return refusal;
    }

    /** Selecting a track keeps the systems view open on it; a review never carries across tracks. */
    public void selectTrack(RefitTrack chosen) { track = chosen; selected = -1; reviewed = null; feedback = ""; refresh(); }

    /** Opens the systems tree on {@code chosen}, leaving the deck behind it. */
    public void openSystems(RefitTrack chosen) { systems = true; selectTrack(chosen); }

    public void closeSystems() {
        if (!systems) return;
        systems = false; reviewed = null; feedback = ""; refresh();
    }

    public boolean systemsOpen() { return systems; }

    private Runnable trackSelection(RefitTrack chosen) {
        return trackSelections.computeIfAbsent(chosen, key -> () -> selectTrack(key));
    }

    /** The deck's entry to the tree: one chip per core, ringed in the colour of its band. */
    private List<Map<String, Object>> chips(ShipCondition state) {
        var chips = new ArrayList<Map<String, Object>>();
        for (RefitTrack core : ShipSystemsTree.BRANCHES.keySet()) {
            String id = "systems-chip-" + ShipSystemsTree.slug(core);
            chips.add(Map.of("id", id, "iconId", id + "-icon", "icon", ShipSystemsTree.icon(core),
                    "style", "width: 30px; height: 44px; padding: 5px; border-width: 2px; background-color: #18221f; background-image: none; border-color: "
                            + ShipSystemsTree.bandColor(state.band(core)) + ";",
                    "select", chipSelections.computeIfAbsent(core, key -> () -> openSystems(key))));
        }
        return List.copyOf(chips);
    }

    /** What the next tier also waits on beyond its own predecessor, met or not. */
    private static List<Map<String, Object>> needs(ShipCondition state, RefitOrder order) {
        var rows = new ArrayList<Map<String, Object>>();
        for (var need : order.requires.entrySet()) {
            boolean met = state.band(need.getKey()).atLeast(need.getValue());
            String id = "survey-need-" + ShipSystemsTree.slug(need.getKey());
            rows.add(Map.of("id", id, "iconId", id + "-icon", "labelId", id + "-label",
                    "icon", ShipSystemsTree.icon(need.getKey()),
                    "label", Strings.format("openingRefitNeed", RefitText.track(need.getKey()), RefitText.band(need.getValue())),
                    "classes", "label survey-need-label " + (met ? "refit-state-improved" : "refit-state-deficit")));
        }
        return List.copyOf(rows);
    }

    private static String slug(String name) { return name.toLowerCase(Locale.ROOT).replace('_', '-'); }

    /** Tiers bought or earned above the battered start, out of every tier the tree offers. */
    static int tiersDone(ShipCondition state) {
        int done = 0;
        for (RefitTrack t : RefitTrack.values())
            done += Math.min(state.band(t).ordinal(), RefitOrder.ceiling(t).ordinal()) - t.start().ordinal();
        return done;
    }

    static int tiersOffered() {
        int offered = 0;
        for (RefitTrack t : RefitTrack.values()) offered += RefitOrder.ceiling(t).ordinal() - t.start().ordinal();
        return offered;
    }

    private static String name(Compartment room) {
        return switch (room.purpose()) {
            case VEHICLE_BAY -> Strings.get("openingShipConditionMechLab");
            case BARRACKS -> Strings.get("openingShipConditionCompanyQuarters");
            case PARTS_CAGE -> Strings.get("openingShipConditionWorkshopParts");
            case HANGAR -> Strings.get("openingShipConditionShuttleBay");
            case FIRING_RANGE -> Strings.get("openingShipConditionFiringRange");
            case GYMNASIUM -> Strings.get("openingShipConditionGymnasium");
            case PATIENT_WARD -> Strings.get("openingShipConditionSickBay");
            case CREW_LOUNGE -> Strings.get("openingShipConditionRecLounge");
            case BRIG -> Strings.get("openingShipConditionBrig");
            default -> Strings.get("openingShipWord" + room.purpose().name());
        };
    }
    private static String function(RoomPurpose purpose) {
        if (purpose == null) return Strings.get("openingShipConditionShipboardServices");
        return switch (purpose) {
            case BARRACKS -> Strings.get("openingShipConditionCompanyBilletsAndRestStations");
            case CREW_QUARTERS -> Strings.get("openingShipConditionQuartersForTheShipSCrew");
            case ARMORY -> Strings.get("openingShipConditionEquipmentIssueAndCompanyLoadouts");
            case HANGAR -> Strings.get("openingShipConditionBoatBerthingCommissioningAndFitting");
            case VEHICLE_BAY -> Strings.get("openingShipConditionMechBerthingFabricationAndFitting");
            case BRIDGE -> Strings.get("openingShipConditionNavigationAndTheCompanyCommandWatch");
            case ENGINE_ROOM -> Strings.get("openingShipConditionEngineWatchAndMachineryService");
            case MESS_HALL -> Strings.get("openingShipConditionMealsAndOffWatchCrewRoutines");
            case STOCKROOM -> Strings.get("openingShipConditionCargoStowageAndStoresDuties");
            case FIRING_RANGE -> Strings.get("openingShipConditionLiveFirePracticeLanes");
            case GYMNASIUM -> Strings.get("openingShipConditionTrainingAndConditioning");
            case PATIENT_WARD -> Strings.get("openingShipConditionTreatmentAndRecovery");
            case CREW_LOUNGE -> Strings.get("openingShipConditionOffWatchRestAndServices");
            case BRIG -> Strings.get("openingShipConditionHoldingCellsForPrisoners");
            default -> Strings.get("openingShipConditionReachableFittingsAndCrewWorkStations");
        };
    }
}
