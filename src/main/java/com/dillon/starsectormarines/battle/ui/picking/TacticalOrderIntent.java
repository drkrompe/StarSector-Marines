package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.assault.MachineAssaults;
import com.dillon.starsectormarines.battle.assault.MachineGuards;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.HoldPlaceDecision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.OccupyPositionDecision;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * What a right-click would do, asked before it happens.
 *
 * <p>Orders here are contextual: the same click means walk, get in, or unload
 * depending on what is under the cursor and what is inside it. That is a good
 * interaction and an invisible one — a player cannot discover a verb that only
 * announces itself after it has been issued — so the cursor has to be able to
 * say what the click would mean.
 *
 * <p>The resolution has to be the <em>same</em> resolution, not a second copy
 * of it. Every question below is asked of the service that answers it for the
 * order systems too, because a cursor that promises a ride the order then
 * refuses is worse than no cursor at all.
 */
public enum TacticalOrderIntent {

    /** Nothing selected, or nothing this selection can do here. */
    NONE("", false),
    /** Ordinary ground: go there. */
    MOVE("MOVE", true),
    /** Each selected squad resolves the shared click through its own order authority. */
    GROUP_ORDER("GROUP ORDER", true),
    /** A friendly transport with room: walk over and get in. */
    MOUNT("GET IN", true),
    /** The selected transport, carrying somebody: everybody out. */
    DISMOUNT("UNLOAD", true),
    /** A transport that cannot take this squad — full, or somebody else's. */
    MOUNT_BLOCKED("NO ROOM", false),
    /** Infantry over a fighting position or a held building: man it, rather than walk to it. */
    OCCUPY("battleOrderOccupyPosition", true, true),
    /** A held building the squad believes holds an enemy: the same hold, which clears it first. */
    CLEAR("battleOrderClearPlace", true, true),
    /** An uncaptured compound: the click secures it, taking the room by force. */
    SECURE("battleOrderSecureCompound", true, true),
    /**
     * A mech, a lance or an armed vehicle over a trench or a held building the side believes is
     * enemy-held: an assault by fire from a stand-off, never by entering ({@code MachineAssaults}).
     */
    ASSAULT("battleOrderAssaultPlace", true, true),
    /**
     * A mech, a lance or an armed vehicle over a trench or a held building its own side mans: it stands beside the
     * garrison as heavy support, outside the place and off its lanes of fire ({@code MachineGuards}).
     */
    GUARD("battleOrderGuardPlace", true, true),
    /**
     * Player mechs alone, over a friendly infantry squad: they attach to it and stand by their doctrine
     * relative to it until given a direct order of their own ({@code MechAttachmentSystem}).
     */
    ATTACH("battleOrderAttach", true, true);

    private final String label;
    private final boolean actionable;
    private final boolean catalogLabel;

    TacticalOrderIntent(String label, boolean actionable) {
        this(label, actionable, false);
    }

    /** {@code catalogLabel}: {@code label} is a string-catalog key rather than the caption itself. */
    TacticalOrderIntent(String label, boolean actionable, boolean catalogLabel) {
        this.catalogLabel = catalogLabel;
        this.label = label;
        this.actionable = actionable;
    }

    /** Short caption for the cursor marker. */
    public String label() { return catalogLabel ? Strings.get(label) : label; }

    /** Whether the click would be carried out, as opposed to refused. */
    public boolean actionable() { return actionable; }

    /** Whether the click sends the squad into ground the enemy holds, which the cursor says in red. */
    public boolean assault() { return this == CLEAR || this == SECURE || this == ASSAULT; }

    /** Whether the click sends a machine to stand by its own side's garrison, which the cursor says in the friendly colour. */
    public boolean guard() { return this == GUARD; }

    /**
     * Resolves what a right-click at ({@code cellX}, {@code cellY}) would mean
     * for the current selection. Pure: nothing is queued and nothing changes.
     */
    public static TacticalOrderIntent resolve(BattleSimulation sim, Selection selection,
                                              int cellX, int cellY) {
        if (sim == null || selection == null) return NONE;
        if (!sim.getGrid().inBounds(cellX, cellY)) return NONE;
        if (SelectionOrders.attachAnchorAt(sim, selection, cellX, cellY) != Selection.NONE) return ATTACH;
        // A group: squads with vehicles, several vehicles, or several squads.
        if (selection.vehicleCount() > 1 || selection.vehicleCount() > 0 && selection.squadCount() > 0) {
            return GROUP_ORDER;
        }

        long vehicle = selection.getSelectedVehicleId();
        if (vehicle != 0L) {
            if (sim.transport().pointsAt(vehicle, cellX, cellY)) {
                return sim.transport().manifest(vehicle).isEmpty() ? NONE : DISMOUNT;
            }
            // An armed hull assaults a garrisonable its side believes is enemy-held and guards one its side mans;
            // an unarmed one only moves.
            return MachineAssaults.vehicleArmed(sim, vehicle)
                    ? assaultOrMove(sim, sim.convoy().faction(vehicle), cellX, cellY) : MOVE;
        }

        if (selection.squadCount() > 1) return GROUP_ORDER;

        long mech = selection.getSelectedUnitEntityId();
        if (mech != 0L && sim.world().hasMechLoadout(mech)) {
            return assaultOrMove(sim, sim.identity().faction(mech), cellX, cellY);
        }

        int squadId = selection.getSelectedSquadId();
        if (squadId == Selection.NONE) return NONE;
        Squad squad = sim.getSquad(squadId);
        if (squad == null) return NONE;
        // A lance's click goes to its members' own move orders, so it neither rides nor secures: it assaults a
        // garrisonable the side believes is enemy-held, guards one the side mans, and otherwise moves.
        if (squad.isMechSquad()) return assaultOrMove(sim, squad.faction, cellX, cellY);

        int size = sim.squadMemberCount(squadId);
        if (sim.transport().mountableVehicleFor(cellX, cellY, squad.faction, size) != 0L) {
            return MOUNT;
        }
        // A transport that is there but cannot take them is worth saying so
        // about: "no room" is a different answer from "walk there", and the
        // click would otherwise look like it had been ignored.
        if (sim.transport().mountableVehicleFor(cellX, cellY, squad.faction, 0) != 0L) {
            return MOUNT_BLOCKED;
        }
        if (SquadMoveOrderSystem.insideUncapturedCompound(cellX, cellY, sim)) return SECURE;
        if (OccupyPositionDecision.runUnderClick(sim.fightingPositions(),
                DigInSystem.isEnabled(), cellX, cellY) >= 0) {
            return OCCUPY;
        }
        TacticalNode place = HoldPlaceDecision.placeUnderClick(sim.getTacticalMap(), cellX, cellY);
        if (place == null) return MOVE;
        return HoldPlaceDecision.believedOccupied(squad, place) ? CLEAR : OCCUPY;
    }

    private static TacticalOrderIntent assaultOrMove(BattleSimulation sim, Faction side,
                                                     int cellX, int cellY) {
        if (MachineAssaults.targetUnderClick(sim, side, cellX, cellY) != null) return ASSAULT;
        return MachineGuards.targetUnderClick(sim, side, cellX, cellY) != null ? GUARD : MOVE;
    }
}
