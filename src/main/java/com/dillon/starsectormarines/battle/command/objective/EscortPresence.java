package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Who stands at a cell, as the escorted-package objectives read it ({@code extraction-command.md}, Generic recovery
 * law): the settled Marine combatant that controls it, and whether any combatant of a side is there. Shared by every
 * family that holds a source, escorts a package and boards it, so their presence rules cannot drift apart.
 */
final class EscortPresence {

    /** The controlling escort, or {@link #NONE}: the lowest squad id wins, then the lowest unit, so it is stable. */
    record Escort(boolean present, int squadId) {
        static final Escort NONE = new Escort(false, -1);
    }

    private EscortPresence() {}

    /**
     * The settled Marine combatant within {@code radius} of the cell, in {@code requiredZone} when it is not negative.
     */
    static Escort nearestMarine(BattleView sim, int x, int y, int radius, int requiredZone) {
        int radiusSquared = radius * radius;
        int selectedSquad = Integer.MAX_VALUE;
        long selectedUnit = Long.MAX_VALUE;
        boolean found = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE
                    || !sim.identity().type(unit).combatant
                    // A body that cannot move, such as a boat's turret, settles nothing and escorts nothing.
                    || !sim.world().hasMovement(unit)
                    || !sim.movement().settled(unit)) continue;
            int unitX = sim.world().cellX(unit);
            int unitY = sim.world().cellY(unit);
            int dx = unitX - x;
            int dy = unitY - y;
            if (dx * dx + dy * dy > radiusSquared) continue;
            if (requiredZone >= 0 && sim.getZoneGraph().zoneIdAt(unitX, unitY) != requiredZone) continue;
            Squad squad = sim.squadOf(unit);
            int squadId = squad != null ? squad.id : -1;
            int sortSquad = squadId >= 0 ? squadId : Integer.MAX_VALUE;
            if (!found || sortSquad < selectedSquad || (sortSquad == selectedSquad && unit < selectedUnit)) {
                found = true;
                selectedSquad = sortSquad;
                selectedUnit = unit;
            }
        }
        return found ? new Escort(true, selectedSquad != Integer.MAX_VALUE ? selectedSquad : -1) : Escort.NONE;
    }

    /** Whether any combatant of {@code faction} is within {@code radius}, in {@code requiredZone} when not negative. */
    static boolean combatantPresent(BattleView sim, Faction faction, int x, int y, int radius, int requiredZone) {
        int radiusSquared = radius * radius;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != faction || !sim.identity().type(unit).combatant) continue;
            int unitX = sim.world().cellX(unit);
            int unitY = sim.world().cellY(unit);
            int dx = unitX - x;
            int dy = unitY - y;
            if (dx * dx + dy * dy > radiusSquared) continue;
            if (requiredZone < 0 || sim.getZoneGraph().zoneIdAt(unitX, unitY) == requiredZone) return true;
        }
        return false;
    }
}
