package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

import java.util.Collections;

/**
 * Squad-cohesion role for defenders pegged to a tactical node. A garrison
 * squad has three modes, driven by {@link Squad#alertLevel}:
 *
 * <ul>
 *   <li><b>UNAWARE</b> — idle at the unit's home cell (the spawn cell
 *       {@link com.dillon.starsectormarines.battle.BattleSetup} assigned
 *       around the squad's tactical node). Members who drift off home for
 *       any reason path back; members already at home hold position.</li>
 *   <li><b>SUSPICIOUS</b> — squadmate took fire but no current LOS. Members
 *       move toward the squad's last-known-enemy cell so the garrison
 *       investigates the noise instead of staring at a wall.</li>
 *   <li><b>ENGAGED</b> — falls through to {@link CombatantBehavior}. The
 *       garrison fights normally; cohesion bias + cover-aware firing-position
 *       picker keep them anchored to the node without explicit holding logic.</li>
 * </ul>
 *
 * <p>If the unit has no squad or no assigned node, GarrisonBehavior
 * degenerates to {@link CombatantBehavior} — a defensive backstop so a
 * misconfigured spawn doesn't freeze the unit.
 */
public final class GarrisonBehavior implements UnitBehavior {

    public static final GarrisonBehavior INSTANCE = new GarrisonBehavior();

    private GarrisonBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        Squad squad = sim.getSquad(u.squadId);
        if (squad == null) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        if (squad.alertLevel == SquadAlertLevel.ENGAGED) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            moveToward(u, sim, squad.lastSeenEnemyX, squad.lastSeenEnemyY);
            return;
        }

        // UNAWARE — hold at home cell. If we don't have one (defensive
        // fallback), settle on the current cell.
        int hx = u.homeCellX >= 0 ? u.homeCellX : u.cellX;
        int hy = u.homeCellY >= 0 ? u.homeCellY : u.cellY;
        if (u.cellX == hx && u.cellY == hy) {
            sim.setPath(u, Collections.emptyList());
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            return;
        }
        moveToward(u, sim, hx, hy);
    }

    private static void moveToward(Unit u, BattleSimulation sim, int tx, int ty) {
        if (u.moveProgress == 0f && (u.path.isEmpty() || u.pathIdx >= u.path.size())) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                    u.cellX, u.cellY, tx, ty, sim.getOccupancyMap()));
        }
        if (!u.path.isEmpty() && u.pathIdx < u.path.size()) {
            sim.advanceMovement(u);
        } else {
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
        }
    }
}
