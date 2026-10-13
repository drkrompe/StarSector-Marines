package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.control.ShipCommandService.Notice;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.tender.TenderDroneArt;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.ui.panel.ShipOrderTargetingPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.BattleShipCommandOverlayModel.ActionState;
import com.dillon.starsectormarines.ops.BattleShipCommandOverlayModel.ShipState;
import com.dillon.starsectormarines.ops.BattleShipCommandOverlayModel.State;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Projects the battle's exact ships and queues commands; selection does not take the helm. */
final class BattleShipCommandController {
    private static final String MOVE_ICON = "graphics/ui/equipment/movement.svg";
    private static final String HOLD_ICON = "graphics/ui/equipment/protection.svg";
    private static final String AUTO_ICON = "graphics/ui/briefing/support.svg";
    private final Supplier<BattleSimulation> simulation;
    private final Selection selection;
    private final ShipOrderTargetingPanel targeting;
    private final Consumer<String> powerToggle;
    private final Supplier<String> powerTarget;
    private final Runnable selectionChanged;
    private final Map<Warship, Integer> ids = new IdentityHashMap<>();
    private final Map<Warship, Map<String, Runnable>> callbacks = new IdentityHashMap<>();
    private BattleSimulation previous;
    private Warship previousSelection;
    private int nextId;

    BattleShipCommandController(Supplier<BattleSimulation> simulation, Selection selection,
                                ShipOrderTargetingPanel targeting, Consumer<String> powerToggle,
                                Supplier<String> powerTarget, Runnable selectionChanged) {
        this.simulation = simulation;
        this.selection = selection;
        this.targeting = targeting;
        this.powerToggle = powerToggle;
        this.powerTarget = powerTarget;
        this.selectionChanged = selectionChanged;
    }

    State state() {
        BattleSimulation sim = simulation.get();
        if (sim != previous) {
            ids.clear(); callbacks.clear(); nextId = 0; previous = sim;
            previousSelection = null; targeting.cancel();
        }
        if (sim == null || sim.isComplete() || sim.directControl().active()) return State.EMPTY;
        List<Warship> roster = roster(sim);
        Warship selected = selection.getSelectedWarship();
        if (selected != null && !roster.contains(selected)) { selection.clear(); selected = null; }
        if (previousSelection != selected) {
            targeting.cancel();
            if (previousSelection != null && "yard-construction".equals(powerTarget.get())) powerToggle.accept("yard-construction");
            previousSelection = selected;
        }
        ids.keySet().retainAll(roster); callbacks.keySet().retainAll(roster);
        List<ShipState> ships = new ArrayList<>();
        for (Warship ship : roster) {
            int id = ids.computeIfAbsent(ship, key -> ++nextId);
            Tender tender = ShipOrderTargetingPanel.tender(sim, ship);
            ships.add(new ShipState(Integer.toString(id), name(sim, ship, tender), ship.spritePath,
                    status(ship, tender), ship.hitPoints() / Math.max(1f, ship.maxHitPoints), ship == selected,
                    callback(ship, "select", () -> select(ship))));
        }
        if (selected == null) return new State(ships, "", "", List.of());
        Tender tender = ShipOrderTargetingPanel.tender(sim, selected);
        return new State(ships, name(sim, selected, tender), hint(sim, selected, tender), actions(sim, selected, tender));
    }

    static List<Warship> roster(BattleSimulation sim) {
        List<Warship> result = new ArrayList<>();
        for (Warship ship : sim.warships().ships()) if (selectable(ship)) result.add(ship);
        for (Tender tender : sim.tenders().tenders()) {
            if (selectable(tender.ship()) && tender.anchorAlive() && !result.contains(tender.ship())) result.add(tender.ship());
        }
        return result;
    }

    private static boolean selectable(Warship ship) {
        return ship.faction == Faction.MARINE && !ship.destroyed() && !ship.departing() && !ship.departed();
    }

    private void select(Warship ship) {
        BattleSimulation sim = simulation.get();
        if (sim == null || sim.directControl().active() || !roster(sim).contains(ship)) return;
        selectionChanged.run();
        targeting.cancel();
        if (selection.getSelectedWarship() == ship) selection.clear();
        else selection.selectWarship(ship);
    }

    private List<ActionState> actions(BattleSimulation sim, Warship ship, Tender tender) {
        List<ActionState> actions = new ArrayList<>();
        boolean mobile = ShipCommandService.eligibility(ship, tender, Faction.MARINE) == Notice.NONE;
        boolean grounded = tender != null && tender.grounded();
        actions.add(action(ship, "move", text("Move"), MOVE_ICON,
                text(grounded ? "GroundedHint" : "MoveHint"), grounded ? text("Grounded") : "RMB", mobile,
                targeting.ship() == ship && targeting.option() == null, () -> arm(ship, null)));
        if (!grounded) {
            actions.add(action(ship, "hold", text("Hold"), HOLD_ICON, text("HoldHint"), "", mobile,
                    ship.remoteOrder() == Warship.RemoteOrder.HOLD, () -> order(ship, () -> sim.shipCommands().requestHold(ship))));
            actions.add(action(ship, "auto", text("Auto"), AUTO_ICON, text("AutoHint"), "", mobile,
                    ship.remoteOrder() == Warship.RemoteOrder.NONE, () -> order(ship, () -> sim.shipCommands().requestAutonomous(ship))));
        }
        if (tender != null) {
            if (grounded) {
                CommandPower power = sim.getCommandPowerService().getPower("yard-construction");
                if (power instanceof YardConstruction yard && yard.source() == tender) {
                    actions.add(action(ship, "build-vulcan-turret", buildLabel(BuildOption.VULCAN_TURRET),
                            buildIcon(BuildOption.VULCAN_TURRET), text("GroundBuildHint"), text("Build"),
                            sim.getCommandPowerService().canActivate(power), power.id.equals(powerTarget.get()),
                            () -> powerToggle.accept(power.id)));
                }
            } else {
                for (BuildOption option : BuildOption.ALL) {
                    boolean ready = mobile && TenderPilot.liveSites(tender) < TenderPilot.MAX_SITES
                            && (!tender.metered() || tender.stock() >= option.cost());
                    String detail = Strings.format("battleShipBuildHint", option.label(), Math.round(option.buildSeconds()));
                    actions.add(action(ship, "build-" + option.id(), buildLabel(option), buildIcon(option), detail,
                            Math.round(option.buildSeconds()) + "s", ready,
                            targeting.ship() == ship && targeting.option() == option, () -> arm(ship, option)));
                }
                actions.add(action(ship, "cancel", text("Cancel"), "graphics/ui/company/condition-repair.svg",
                        text("CancelHint"), Integer.toString(TenderPilot.liveSites(tender)), mobile && TenderPilot.liveSites(tender) > 0,
                        false, () -> order(ship, () -> sim.shipCommands().requestCancelBuilds(tender))));
                actions.add(action(ship, "drones", text("Drones"), TenderDroneArt.spritePath(),
                        text("DroneHint"), text(tender.defenceOut() ? "Deployed" : "Recalled"), mobile, tender.defenceOut(),
                        () -> order(ship, () -> sim.shipCommands().requestDefence(tender))));
            }
        }
        actions.add(action(ship, "company", text("Company"), "graphics/ui/briefing/infantry.svg",
                text("CompanyHint"), "", true, false, () -> { selectionChanged.run(); targeting.cancel(); selection.clear(); }));
        return List.copyOf(actions);
    }

    private ActionState action(Warship ship, String id, String label, String icon, String hint, String status,
                               boolean enabled, boolean armed, Runnable action) {
        return new ActionState(id, label, icon, hint, status, enabled, armed, callback(ship, id, action));
    }

    private Runnable callback(Warship ship, String id, Runnable action) {
        return callbacks.computeIfAbsent(ship, key -> new LinkedHashMap<>()).computeIfAbsent(id, key -> action);
    }

    private void arm(Warship ship, BuildOption option) {
        selectionChanged.run();
        targeting.toggle(ship, option);
    }
    private void order(Warship ship, Runnable request) {
        if (selection.getSelectedWarship() != ship) return;
        selectionChanged.run(); targeting.cancel(); request.run();
    }

    private String hint(BattleSimulation sim, Warship ship, Tender tender) {
        Notice notice = targeting.active() ? targeting.notice() : sim.shipCommands().notice(ship);
        if (notice.ordinal() >= Notice.UNAVAILABLE.ordinal()) return text("Notice" + notice.name());
        if (targeting.active()) return text(targeting.option() == null ? "PlaceMove" : "PlaceBuild");
        if ("yard-construction".equals(powerTarget.get())) return text("PlaceBuild");
        return text(tender != null && tender.grounded() ? "GroundedHint" : "SelectedHint");
    }
    private static String status(Warship ship, Tender tender) {
        if (tender != null && tender.grounded()) return text("Grounded");
        return text(switch (ship.remoteOrder()) {
            case MOVE -> ship.isMoving() ? "Moving" : "Holding";
            case HOLD -> "Holding";
            case BUILD -> tender != null && TenderPilot.liveSites(tender) == 0 ? "Holding" : "Building";
            case NONE -> "Auto";
        });
    }
    private static String name(BattleSimulation sim, Warship ship, Tender tender) {
        if (ship.supportSource() != null) return ship.supportSource().shipName();
        if (tender != null && tender.grounded() && sim.getColdStart() != null) return "Morrow";
        return ship.hullId.isEmpty() ? text("Heading")
                : Character.toUpperCase(ship.hullId.charAt(0)) + ship.hullId.substring(1).replace('_', ' ');
    }
    private static String buildLabel(BuildOption option) {
        return Strings.get("battleShipBuildLabel" + option.id());
    }
    private static String buildIcon(BuildOption option) {
        if (option.kind() == BuildOption.Kind.POINT_DEFENCE) return "graphics/ui/armory/special-point-defence-pod.png";
        return TurretCatalogRegistry.requireStructure(option.structureId()).mount.spritePath;
    }
    private static String text(String suffix) { return Strings.get("battleShip" + suffix); }
}
