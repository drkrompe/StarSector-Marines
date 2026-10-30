package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.ops.RiskLevel;
import java.util.List;
import java.util.Map;

/**
 * Sable's Garnir installation as authored, not as the lift brought to it can afford.
 *
 * <p>Sized against the company the chapter expects: five squads and four machines, one of them an
 * LRM carrier that strips an exposed squad from beyond rifle range. The force does not shrink for a
 * smaller company (Conquest's rule), so the fight keeps targets and guns enough to contest the road
 * and the compound. UNTUNED: the counts were chosen for that expected company and have not been
 * played against it; settle them with a player-controlled pass.
 */
final class ColdAccountForce {
    private ColdAccountForce() {}

    static final int REGULARS = 28;
    static final int MILITIA = 40;
    static final List<MechVariant> MACHINES = List.of(MechVariant.HOUND, MechVariant.WARDEN);
    static final int PATROL = 7;

    /**
     * The outpost's emplacements: the garrison rung's gates and wall, so the bombardment's demolition
     * is unchanged, with heavier guns on the gates and a rocket battery whose reach exceeds the LRM
     * carrier's standoff.
     */
    static final Fortification FORTIFICATION = new Fortification(Fortification.GARRISON.gates(),
            Fortification.GARRISON.wallHp(), Map.of(DefensePostKind.LARGE, 2, DefensePostKind.MEDIUM, 2,
                    DefensePostKind.LIGHT, 2, DefensePostKind.ARTILLERY, 1));

    /** The fixed point-defence mount on the compound's open ground. */
    static final String POINT_DEFENCE = "structure.post-point-defence";

    static DefenderRoster roster(RiskLevel risk) {
        return DefenderRoster.explicit(REGULARS, MILITIA, MACHINES, PATROL, risk);
    }

    /** Stands a defender point-defence mount on the nearest open outdoor cell to each anchor. */
    static void installPointDefence(BattleSimulation sim, List<int[]> anchors) {
        for (int[] anchor : anchors) {
            int[] cell = openCell(sim.getGrid(), sim.getTopology(), anchor[0], anchor[1]);
            sim.pointDefense().placeFixed(sim, Faction.DEFENDER, cell[0], cell[1], POINT_DEFENCE);
        }
    }

    private static int[] openCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        for (int r = 0; r <= 6; r++)
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                int cx = x + dx, cy = y + dy;
                if (grid.isWalkable(cx, cy) && !grid.isDoorway(cx, cy) && topology.getBuildingId(cx, cy) == 0)
                    return new int[]{cx, cy};
            }
        throw new IllegalStateException("No open ground for Cold Account point defence near " + x + "," + y);
    }
}
