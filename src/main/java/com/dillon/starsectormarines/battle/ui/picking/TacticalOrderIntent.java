package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;

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
    /** A friendly transport with room: walk over and get in. */
    MOUNT("GET IN", true),
    /** The selected transport, carrying somebody: everybody out. */
    DISMOUNT("UNLOAD", true),
    /** A transport that cannot take this squad — full, or somebody else's. */
    MOUNT_BLOCKED("NO ROOM", false);

    private final String label;
    private final boolean actionable;

    TacticalOrderIntent(String label, boolean actionable) {
        this.label = label;
        this.actionable = actionable;
    }

    /** Short caption for the cursor marker. */
    public String label() { return label; }

    /** Whether the click would be carried out, as opposed to refused. */
    public boolean actionable() { return actionable; }

    /**
     * Resolves what a right-click at ({@code cellX}, {@code cellY}) would mean
     * for the current selection. Pure: nothing is queued and nothing changes.
     */
    public static TacticalOrderIntent resolve(BattleSimulation sim, Selection selection,
                                              int cellX, int cellY) {
        if (sim == null || selection == null) return NONE;
        if (!sim.getGrid().inBounds(cellX, cellY)) return NONE;

        long vehicle = selection.getSelectedVehicleId();
        if (vehicle != 0L) {
            if (sim.transport().pointsAt(vehicle, cellX, cellY)) {
                return sim.transport().manifest(vehicle).isEmpty() ? NONE : DISMOUNT;
            }
            return MOVE;
        }

        long mech = selection.getSelectedUnitEntityId();
        if (mech != 0L && sim.world().hasMechLoadout(mech)) return MOVE;

        int squadId = selection.getSelectedSquadId();
        if (squadId == Selection.NONE) return NONE;
        Squad squad = sim.getSquad(squadId);
        if (squad == null) return NONE;

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
        return MOVE;
    }
}
