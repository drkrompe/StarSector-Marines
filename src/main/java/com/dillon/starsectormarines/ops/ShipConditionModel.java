package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.ShipConditionStage;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
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
        this.deck = deck; this.offers = offers; this.repair = repair; this.bayOffer = bayOffer;
        this.repairBay = repairBay; this.focus = focus; this.changed = changed;
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
        m.put("title", Strings.get("openingShipConditionShipCondition")); m.put("copy", ""); m.put("benefit", ""); m.put("bill", ""); m.put("reason", "");
        m.put("action", (Runnable) () -> {}); m.put("actionLabel", Strings.get("openingShipConditionReviewRepair")); m.put("disabled", true);
        m.put("actionClasses", "hidden"); m.put("detailClasses", "hidden"); m.put("workshopClasses", "restoration-panel");
        m.put("feedback", ""); m.put("summary", Strings.get("openingShipConditionRoomSurvey"));
        m.put("powerClasses", "hidden"); m.put("powerLabel", Strings.get("openingShipConditionDistributionCircuits"));
        m.put("powerAction", (Runnable) () -> {});
        m.put("status", "");
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
            String status = sealed ? Strings.get("openingShipConditionSealedDamaged") : graph.damaged(room.id()) ? Strings.get("openingShipConditionDamagedAccessible")
                    : graph.emergencyLighting(room.id()) ? Strings.get("openingShipConditionFaultyPower") : Strings.get("openingShipConditionWorking");
            if (state.applies() && room.purpose() == RoomPurpose.VEHICLE_BAY && state.mechBay())
                status = Strings.format("openingShipConditionBerthsWorking", state.workingMechBerths());
            rooms.add(Map.of("roomId", room.id(), "icon", "graphics/ui/company/condition-" + (sealed ? "sealed" : graph.damaged(room.id()) ? "repair" : graph.emergencyLighting(room.id()) ? "power" : "working") + ".svg",
                    "nameId", "survey-name-" + room.id(), "id", "survey-room-" + room.id(), "label", name(room) + " / " + (room.id() + 1),
                    "status", status, "classes", room.id() == selected ? "survey-room selected" : "survey-room",
                    "select", selection(room.id())));
        }
        var selectedRoom = graph.compartments().stream().filter(r -> r.id() == selected).findFirst().orElse(null);
        boolean workshop = selectedRoom != null && selectedRoom.equals(graph.largest(RoomPurpose.PARTS_CAGE)) && ship.ship().workshop() != WorkshopState.NONE;
        var m = workshopOnly();
        m.put("status", rooms.stream().filter(r -> ((Integer) r.get("roomId")) == selected)
                .map(r -> r.get("status")).findFirst().orElse(state.applies() ? (state.power() ? Strings.get("openingShipConditionRestored") : Strings.get("openingShipConditionFaultyPower")) : Strings.get("openingShipConditionShipboardServices")));
        m.put("powerClasses", state.applies() ? "survey-power console-action" : "hidden");
        m.put("powerLabel", Strings.format("openingShipConditionPowerStatus", state.power() ? Strings.get("openingShipConditionRestored") : Strings.get("openingShipConditionFaultyPower")));
        m.put("powerAction", selection(-1));
        m.put("rooms", List.copyOf(rooms));
        m.put("summary", Strings.format("openingShipConditionDamageInspectionSummary", graph.compartments().stream().filter(r -> graph.damaged(r.id())).count()));
        m.put("workshopClasses", workshop ? "restoration-panel" : "hidden");
        m.put("detailClasses", workshop ? "hidden" : "condition-detail");
        m.put("title", selectedRoom == null ? (state.applies() ? Strings.get("openingShipConditionDistributionCircuits") : Strings.get("openingShipConditionShipCondition")) : name(selectedRoom));
        String copy = Strings.get("openingShipConditionSheSBeenThroughHardServiceThe");
        String benefit = Strings.format("openingShipConditionInService", function(selectedRoom == null ? null : selectedRoom.purpose()));
        String reason = Strings.get("openingShipConditionNoStructuralWorkRequiredHere");
        String bill = "";
        Work work = null;
        boolean bay = false;
        if (state.applies() && selectedRoom == null) {
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
                case 1 -> Work.MECH_BERTH_2;
                case 2 -> Work.MECH_BERTH_3;
                default -> Work.MECH_BERTH_4;
            };
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
        if (work != null || bay) {
            var offer = work == null ? null : offers.apply(work);
            var boat = bay ? bayOffer.get() : null;
            int price = bay ? CompanyBayRestoration.CREDIT_COST : work.price;
            int credits = bay ? boat.credits() : offer.credits();
            String refusal = bay ? boat.refusal() : offer.refusal();
            String key = bay ? "bay" : work.name();
            bill = String.format(Locale.ROOT, Strings.get("openingShipConditionCreditsCostDAvailableDShortBy"), price, credits, Math.max(0, price - credits));
            reason = refusal == null ? Strings.get("openingShipConditionPlanetaryYardLaborAndMaterialsIncluded") : refusal;
            m.put("actionClasses", "restoration-action console-action");
            m.put("actionLabel", key.equals(reviewed) ? String.format(Locale.ROOT, Strings.get("openingShipConditionAuthorizeD"), price) : Strings.get("openingShipConditionReviewRepair"));
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
        else {
            String refusal = key.equals("bay") ? repairBay.get() : repair.apply(Work.valueOf(key));
            feedback = refusal == null ? Strings.get("openingShipConditionYardWorkComplete") : refusal;
            reviewed = null;
            if (refusal == null) changed.run();
        }
        refresh();
    }
    private static String name(Compartment room) {
        return switch (room.purpose()) {
            case VEHICLE_BAY -> Strings.get("openingShipConditionMechLab");
            case BARRACKS -> Strings.get("openingShipConditionCompanyQuarters");
            case PARTS_CAGE -> Strings.get("openingShipConditionWorkshopParts");
            case HANGAR -> Strings.get("openingShipConditionShuttleBay");
            default -> room.purpose().name().replace('_', ' ');
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
            default -> Strings.get("openingShipConditionReachableFittingsAndCrewWorkStations");
        };
    }
}
