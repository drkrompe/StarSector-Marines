package com.dillon.starsectormarines.ops;

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
    public void select(int id) { selected = id; reviewed = null; feedback = ""; refocus(); refresh(); }
    public void refocus() {
        var ship = deck.get();
        if (ship != null && ship.ready()) focus.accept(ship.rooms().compartments().stream().filter(r -> r.id() == selected).findFirst().orElse(null));
    }
    public static Map<String, Object> workshopOnly() {
        var m = new LinkedHashMap<String, Object>();
        m.put("classes", "condition-panel"); m.put("listClasses", "hidden"); m.put("headingClasses", "hidden"); m.put("rooms", List.of()); m.put("portrait", CompanyManagementGuide.portrait());
        m.put("title", "SHIP CONDITION"); m.put("copy", ""); m.put("benefit", ""); m.put("bill", ""); m.put("reason", "");
        m.put("action", (Runnable) () -> {}); m.put("actionLabel", "REVIEW REPAIR"); m.put("disabled", true);
        m.put("actionClasses", "hidden"); m.put("detailClasses", "hidden"); m.put("workshopClasses", "restoration-panel");
        m.put("feedback", ""); m.put("summary", "ROOM SURVEY");
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
            String status = sealed ? "SEALED / DAMAGED" : graph.damaged(room.id()) ? "DAMAGED / ACCESSIBLE"
                    : graph.emergencyLighting(room.id()) ? "FAULTY POWER" : "WORKING";
            rooms.add(Map.of("nameId", "survey-name-" + room.id(), "statusId", "survey-status-" + room.id(), "id", "survey-room-" + room.id(), "label", name(room) + " / " + (room.id() + 1),
                    "status", status, "classes", room.id() == selected ? "survey-room selected" : "survey-room",
                    "select", selection(room.id())));
        }
        if (state.applies()) rooms.add(0, Map.of("nameId", "survey-power-name", "statusId", "survey-power-status", "id", "survey-power", "label", "DISTRIBUTION CIRCUITS", "status", state.power() ? "RESTORED" : "FAULTY POWER",
                "classes", selected == -1 ? "survey-room selected" : "survey-room", "select", selection(-1)));
        var selectedRoom = graph.compartments().stream().filter(r -> r.id() == selected).findFirst().orElse(null);
        boolean workshop = selectedRoom != null && selectedRoom.equals(graph.largest(RoomPurpose.PARTS_CAGE)) && ship.ship().workshop() != WorkshopState.NONE;
        var m = workshopOnly();
        m.put("rooms", List.copyOf(rooms));
        m.put("listClasses", "survey-list console-well"); m.put("headingClasses", "label heading tone-accent survey-heading");
        m.put("summary", graph.compartments().stream().filter(r -> graph.damaged(r.id())).count() + " DAMAGED ROOMS / SELECT TO INSPECT");
        m.put("workshopClasses", workshop ? "restoration-panel" : "hidden");
        m.put("detailClasses", workshop ? "hidden" : "condition-detail");
        m.put("title", selectedRoom == null ? (state.applies() ? "DISTRIBUTION CIRCUITS" : "SHIP CONDITION") : name(selectedRoom));
        String copy = "She's been through hard service. The scars stay, but this room is safe to use.";
        String benefit = "IN SERVICE: " + function(selectedRoom == null ? null : selectedRoom.purpose());
        String reason = "No structural work required here.";
        String bill = "";
        Work work = null;
        boolean bay = false;
        if (state.applies() && selectedRoom == null) {
            copy = state.power() ? "Steady lights again. We have replaced the tired distribution runs. Sealed rooms still need their own repairs."
                    : "We are nursing old cable and scorched breakers. The working rooms are safe, but too many watches are being stood under emergency lamps.";
            benefit = "Restore steady lights throughout the working compartments. Sealed rooms need separate structural repairs.";
            if (!state.power()) work = Work.POWER;
        } else if (selectedRoom != null && state.applies() && selectedRoom.purpose() == RoomPurpose.VEHICLE_BAY) {
            copy = state.mechBay() ? "Wayward has a home. The recovery crew has plated the breach, restored the gantry and made the bay ready for her."
                    : "The pressure skin is torn and the gantry is wrecked. Nobody goes through that hatch. We will need a safe berth before we bring our first mech aboard.";
            benefit = state.mechBay() ? "RESTORED: One working mech gantry. Visit Mech Lab to fit Wayward."
                    : "MISSION 2: Complete Take Back the Depot. Mech Lab restoration is included with Wayward's recovery; no separate credit bill.";
            reason = state.mechBay() ? "Restored with the depot recovery." : "Locked until the second story mission is complete.";
        } else if (selectedRoom != null && state.applies() && selectedRoom.purpose() == RoomPurpose.MESS_HALL) {
            copy = state.messHall() ? "Hot meals and a table together. You can hear the difference already. They have somewhere to be a company again."
                    : "The mess took a hit. We are feeding everyone from field rations in the working quarters, but they deserve better. The yard can seal the breach and put the galley and tables right.";
            benefit = (state.messHall() ? "RESTORED: " : "NEXT: ") + "Company infantry recover morale 25% faster once incoming fire stops. Applies to future deployments from this active company home.";
            reason = state.messHall() ? "Morale limits and recovery delay are unchanged. No additional food upkeep." : "Field rations keep the company ready for its first mission.";
            if (!state.messHall()) work = Work.MESS_HALL;
        } else if (selectedRoom != null && state.applies() && ShipConditionStage.spareBillets(graph).contains(selectedRoom)) {
            copy = state.billets() ? "Fresh racks, sealed plating and room for the company to grow. Let us keep these quarters fit for the people who sleep here."
                    : "These two bunkrooms took the same hit. Racks are crushed and the pressure seals are gone. The yard can reopen both as one job.";
            int bunks = ShipConditionStage.spareBillets(graph).size() * RoomRecipe.TROOP_BERTHING.provides();
            benefit = (state.billets() ? "RESTORED: " : "NEXT: Restore ") + bunks + " usable company billets in the reserved bunkrooms. Existing quarters remain available.";
            if (!state.billets()) work = Work.BILLETS;
        } else if (selectedRoom != null && selectedRoom.purpose() == RoomPurpose.HANGAR && graph.damaged(selectedRoom.id())) {
            copy = "A second boat belongs here once the yard makes the pressure door and launch rails safe. We kept the space; now we can earn it back.";
            benefit = "Restore the second shuttle berth and launch opening. Commission its boat separately.";
            bay = true;
        } else if (selectedRoom != null && graph.emergencyLighting(selectedRoom.id())) {
            copy = "The fittings still do their job, but this room is running on tired circuits. We can mend the distribution without moving anyone out.";
            reason = "Select DISTRIBUTION CIRCUITS to review the ship's lighting repair.";
        }
        if (work != null || bay) {
            var offer = work == null ? null : offers.apply(work);
            var boat = bay ? bayOffer.get() : null;
            int price = bay ? CompanyBayRestoration.CREDIT_COST : work.price;
            int credits = bay ? boat.credits() : offer.credits();
            String refusal = bay ? boat.refusal() : offer.refusal();
            String key = bay ? "bay" : work.name();
            bill = String.format(Locale.ROOT, "CREDITS  COST %,d / AVAILABLE %,d / SHORT BY %,d", price, credits, Math.max(0, price - credits));
            reason = refusal == null ? "Planetary yard labor and materials included." : refusal;
            m.put("actionClasses", "restoration-action console-action");
            m.put("actionLabel", key.equals(reviewed) ? String.format(Locale.ROOT, "AUTHORIZE - %,d", price) : "REVIEW REPAIR");
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
        if (!key.equals(reviewed)) { reviewed = key; feedback = "Review the cost and restored facilities before authorizing."; }
        else {
            String refusal = key.equals("bay") ? repairBay.get() : repair.apply(Work.valueOf(key));
            feedback = refusal == null ? "Yard work complete." : refusal;
            reviewed = null;
            if (refusal == null) changed.run();
        }
        refresh();
    }
    private static String name(Compartment room) {
        return switch (room.purpose()) {
            case VEHICLE_BAY -> "MECH LAB";
            case BARRACKS -> "COMPANY QUARTERS";
            case PARTS_CAGE -> "WORKSHOP / PARTS";
            case HANGAR -> "SHUTTLE BAY";
            default -> room.purpose().name().replace('_', ' ');
        };
    }
    private static String function(RoomPurpose purpose) {
        if (purpose == null) return "Shipboard services.";
        return switch (purpose) {
            case BARRACKS -> "Company billets and rest stations.";
            case CREW_QUARTERS -> "Quarters for the ship's crew.";
            case ARMORY -> "Equipment issue and company loadouts.";
            case HANGAR -> "Boat berthing, commissioning and fitting.";
            case VEHICLE_BAY -> "Mech berthing, fabrication and fitting.";
            case BRIDGE -> "Navigation and the company command watch.";
            case ENGINE_ROOM -> "Engine watch and machinery service.";
            case MESS_HALL -> "Meals and off-watch crew routines.";
            case STOCKROOM -> "Cargo stowage and stores duties.";
            default -> "Reachable fittings and crew work stations.";
        };
    }
}
