package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

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
 *   <li><b>ENGAGED</b> — fights normally, but with movement bounded to
 *       {@link #GARRISON_HOLD_RADIUS} cells of the unit's home cell. Engaged
 *       garrison members peek around corners, swap cover stacks one over,
 *       fire from their post — but won't chase marines off the wall. Mechs
 *       delegate to {@link CombatantBehavior} since their LRMs fire indirect
 *       and their own kinematic stays close to the threat anyway.</li>
 * </ul>
 *
 * <p>If the unit has no squad, GarrisonBehavior degenerates to
 * {@link CombatantBehavior} — a defensive backstop so a misconfigured spawn
 * doesn't freeze the unit.
 */
public final class GarrisonBehavior implements UnitBehavior {

    public static final GarrisonBehavior INSTANCE = new GarrisonBehavior();

    /**
     * Cell radius around the unit's home cell inside which an engaged garrison
     * will reposition for firing. Tight — about one block of slack so the
     * unit can flank to the corner of their post or step to a better cover
     * stack, but won't walk into the open street to chase. Outside this
     * radius the unit holds position and waits for the target to step into
     * existing LOS.
     */
    public static final float GARRISON_HOLD_RADIUS = 6f;

    private GarrisonBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        Squad squad = sim.getSquad(u.squadId);
        if (squad == null) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        // Retreat overrides every alert state: when the squad has been routed
        // to a fallback node, every member walks to their freshly-assigned
        // home cell regardless of whether they currently see an enemy. The
        // sim's updateSquadFallback drops the flag once everyone's arrived,
        // at which point normal engagement resumes at the new post.
        if (squad.fallbackInProgress) {
            int hx = u.homeCellX >= 0 ? u.homeCellX : u.cellX;
            int hy = u.homeCellY >= 0 ? u.homeCellY : u.cellY;
            if (u.cellX == hx && u.cellY == hy) {
                holdPosition(u, sim);
                return;
            }
            moveToward(u, sim, hx, hy);
            return;
        }
        if (squad.alertLevel == SquadAlertLevel.ENGAGED) {
            engaged(u, sim);
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
            holdPosition(u, sim);
            return;
        }
        moveToward(u, sim, hx, hy);
    }

    /**
     * Combat dispatch for an engaged garrison member. Picks a target, fires
     * when in range + LOS, and otherwise repositions within
     * {@link #GARRISON_HOLD_RADIUS} of home cell. Mechs delegate to
     * {@link CombatantBehavior} so their three-track chassis fire (chaingun /
     * SRM / LRM) keeps working — the radius bound mainly matters for infantry
     * who'd otherwise run toward a sniping marine and lose their cover post.
     */
    private static void engaged(Unit u, BattleSimulation sim) {
        if (u.mech != null) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }

        if (u.target == null || !u.target.isAlive()) {
            u.target = TacticalScoring.findBestTarget(u, sim);
        }
        if (u.target == null) {
            holdPosition(u, sim);
            return;
        }

        if (u.cooldownTimer > 0f) u.cooldownTimer -= BattleSimulation.TICK_DT;

        float dist = TacticalScoring.cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        boolean inRange = dist <= u.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(u.cellX, u.cellY, u.target.cellX, u.target.cellY);

        if (inRange && visible) {
            if (u.cooldownTimer <= 0f) {
                sim.fireShot(u, u.target);
                u.cooldownTimer = u.attackCooldown;
                if (u.primaryWeapon != null && u.primaryWeapon.burstCount > 1) {
                    u.burstRemaining = u.primaryWeapon.burstCount - 1;
                    u.burstTimer = u.primaryWeapon.burstSpacing;
                    u.burstTarget = u.target;
                }
            }
            // Hold position while firing — garrisons don't sidestep between
            // shots. The cover stack at the post is exactly what they're paid
            // to occupy.
            holdPosition(u, sim);
            return;
        }

        // Out of range or no LOS — try to reposition within hold radius. If
        // no firing position satisfies both range + LOS and the radius bound,
        // stand pat. The target may step into existing LOS, or a squadmate's
        // fire may flush them out.
        int homeX = u.homeCellX >= 0 ? u.homeCellX : u.cellX;
        int homeY = u.homeCellY >= 0 ? u.homeCellY : u.cellY;
        int[] firingPos = TacticalScoring.findFiringPositionWithin(
                u, u.target, sim, homeX, homeY, GARRISON_HOLD_RADIUS);
        if (firingPos == null || (firingPos[0] == u.cellX && firingPos[1] == u.cellY)) {
            holdPosition(u, sim);
            return;
        }
        moveToward(u, sim, firingPos[0], firingPos[1]);
    }

    private static void moveToward(Unit u, BattleSimulation sim, int tx, int ty) {
        if (u.moveProgress == 0f && u.pathIdx >= u.pathCellCount()) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                    u.cellX, u.cellY, tx, ty, sim.getOccupancyMap()));
        }
        if (u.pathIdx < u.pathCellCount()) {
            sim.advanceMovement(u);
        } else {
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
        }
    }

    private static void holdPosition(Unit u, BattleSimulation sim) {
        sim.clearPath(u);
        u.moveProgress = 0f;
        u.renderX = u.cellX;
        u.renderY = u.cellY;
    }
}
