package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.MechLoadoutState;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Default combat loop: acquire target, fire when in range with LOS, otherwise
 * path to a firing position. The behavior every unit had before the role
 * system landed — now with squad cohesion: if a squadmate has drifted more
 * than {@link #COHESION_RADIUS} cells from the squad center, they path
 * toward the centroid before resuming normal engagement, so fireteams move
 * as a group instead of scattering.
 *
 * <p>Cohesion is a soft leash: once within radius, normal targeting takes
 * over. A marine with a great firing position 10 cells out from the squad
 * will be pulled back; one with a position 5 cells out will hold.
 */
public final class CombatantBehavior implements UnitBehavior {

    public static final CombatantBehavior INSTANCE = new CombatantBehavior();

    /** Squadmate leash radius in cells. Outside this, the unit prioritizes rejoining the squad over picking a firing position. */
    public static final float COHESION_RADIUS = 12f;

    /** Probability that, after firing, a unit picks a different firing position. Models routine sidestepping between shots. */
    private static final float REPOSITION_CHANCE = 0.30f;

    private CombatantBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        // Mid-aim: the marine is locked into the rocket animation. Tick the
        // timer down, launch at the midpoint, and short-circuit the rest of
        // the behavior — no movement, no primary fire, no re-targeting.
        if (u.secondaryActionTimer > 0f && u.secondaryWeapon != null) {
            u.secondaryActionTimer -= BattleSimulation.TICK_DT;
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            float fireAt = u.secondaryWeapon.aimDuration * 0.5f;
            if (!u.secondaryFiredThisAction && u.secondaryActionTimer <= fireAt) {
                if (u.secondaryAimTarget != null && u.secondaryAimTarget.isAlive()) {
                    sim.fireSecondary(u, u.secondaryAimTarget);
                }
                u.secondaryFiredThisAction = true;
                u.secondaryCooldownTimer = u.secondaryWeapon.cooldown;
            }
            if (u.secondaryActionTimer <= 0f) {
                u.secondaryActionTimer = 0f;
                u.secondaryAimTarget = null;
            }
            return;
        }

        if (u.target == null || !u.target.isAlive()) {
            u.target = TacticalScoring.findBestTarget(u, sim);
        }
        if (u.target == null) return; // nothing to do — usually a win-condition frame

        if (u.cooldownTimer > 0f) u.cooldownTimer -= BattleSimulation.TICK_DT;
        if (u.secondaryCooldownTimer > 0f) u.secondaryCooldownTimer -= BattleSimulation.TICK_DT;

        float dist = TacticalScoring.cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        boolean inRange = dist <= u.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(u.cellX, u.cellY, u.target.cellX, u.target.cellY);

        // Mech path runs OUTSIDE the marine's `inRange && visible` gate
        // because LRMs are indirect-fire-capable: a mech with line of sight
        // blocked by a building still lobs artillery over it (with an accuracy
        // penalty). Chaingun + SRM still need LOS — that gating lives inside
        // tryFireMechWeapons. Movement uses the standard firing-position
        // pathfinder when not in close engagement so the mech advances to
        // re-acquire LOS for its short-range weapons.
        if (u.mech != null) {
            if (inRange) {
                tryFireMechWeapons(u, dist, sim, visible);
            }
            boolean closeEngagement = inRange && visible
                    && dist <= u.mech.srmPod.range;
            if (!closeEngagement && u.moveProgress == 0f) {
                int[] dest = TacticalScoring.findFiringPosition(u, u.target, sim);
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                        u.cellX, u.cellY, dest[0], dest[1], sim.getOccupancyMap()));
            }
            if (u.pathIdx < u.pathCellCount()) {
                sim.advanceMovement(u);
            } else {
                u.moveProgress = 0f;
                u.renderX = u.cellX;
                u.renderY = u.cellY;
            }
            return;
        }

        if (inRange && visible) {
            // Secondary takes priority against hardened targets — rockets are
            // wasted on infantry but knock turrets out fast. Starts an aim
            // cycle (handled at the top of update on subsequent ticks) instead
            // of firing immediately, so the launch reads as a deliberate
            // animation rather than a flash. Falls through to primary fire
            // when no secondary is ready.
            boolean startedSecondary = false;
            if (u.secondaryWeapon != null && u.secondaryAmmo > 0 && u.secondaryCooldownTimer <= 0f
                    && u.target instanceof MapTurret
                    && dist <= u.secondaryWeapon.range) {
                u.secondaryActionTimer = u.secondaryWeapon.aimDuration;
                u.secondaryFiredThisAction = false;
                u.secondaryAimTarget = u.target;
                startedSecondary = true;
            }
            if (!startedSecondary && u.cooldownTimer <= 0f) {
                sim.fireShot(u, u.target);
                u.cooldownTimer = u.attackCooldown;
                // Queue follow-up burst rounds — InfantryWeapons.tick
                // emits them at burstSpacing intervals. Cooldown was already
                // set, so the next burst can't start until the timer drains.
                if (u.primaryWeapon != null && u.primaryWeapon.burstCount > 1) {
                    u.burstRemaining = u.primaryWeapon.burstCount - 1;
                    u.burstTimer = u.primaryWeapon.burstSpacing;
                    u.burstTarget = u.target;
                }
                if (sim.getRng().nextFloat() < REPOSITION_CHANCE) {
                    int[] firingPos = TacticalScoring.findFiringPosition(u, u.target, sim, u.cellX, u.cellY);
                    if (firingPos[0] != u.cellX || firingPos[1] != u.cellY) {
                        sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, firingPos[0], firingPos[1], sim.getOccupancyMap()));
                    }
                }
            }
            if (u.pathIdx < u.pathCellCount()) {
                sim.advanceMovement(u);
            } else {
                u.moveProgress = 0f;
                u.renderX = u.cellX;
                u.renderY = u.cellY;
            }
        } else {
            // Out of range or no LOS. If we've drifted away from our squad,
            // path back toward the centroid first — the firing-position picker
            // doesn't know about cohesion, so it'd happily send us solo across
            // the map toward a distant target.
            if (u.moveProgress == 0f) {
                int[] dest = cohesionOverride(u, sim);
                if (dest == null) dest = TacticalScoring.findFiringPosition(u, u.target, sim);
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, dest[0], dest[1], sim.getOccupancyMap()));
            }
            sim.advanceMovement(u);
        }
    }

    /**
     * Triggers the three mech weapons in their respective bands. Each track is
     * independent and may fire on the same tick:
     * <ul>
     *   <li><b>Chaingun</b> — close band, LOS-required. Fires when target is
     *       within chaingun range, the weapon is off cooldown, and there's
     *       direct line of sight. Per-burst rounds queue onto
     *       {@link MechLoadoutState} for {@code HeavyWeapons.tick}
     *       to emit at the proper spacing.</li>
     *   <li><b>SRM pod</b> — mid-close band, LOS-required. Same gating as
     *       chaingun plus an ammo check.</li>
     *   <li><b>LRM artillery</b> — long band, INDIRECT-FIRE OK. Fires when
     *       target is in LRM range, off cooldown, ammo &gt; 0, AND outside
     *       chaingun range (the "we're not currently engaged at short range"
     *       gate the user spec'd). When {@code hasLos} is false, the rocket's
     *       hit roll is scaled by {@link com.dillon.starsectormarines.battle.MechWeapon#LRM_NO_LOS_ACC_MULT}
     *       — "we know roughly where they are, but we can't see them, so a
     *       chunk of the salvo flies wide."</li>
     * </ul>
     */
    private static void tryFireMechWeapons(Unit u, float dist, BattleSimulation sim, boolean hasLos) {
        MechLoadoutState m = u.mech;
        // Chaingun: close-band sustained fire — needs LOS.
        if (hasLos && m.chaingunCooldown <= 0f && m.chaingunBurstRemaining <= 0
                && dist <= m.chaingun.range) {
            sim.fireMechWeapon(u, u.target, m.chaingun);
            m.chaingunCooldown = m.chaingun.cooldown;
            if (m.chaingun.burstCount > 1) {
                m.chaingunBurstRemaining = m.chaingun.burstCount - 1;
                m.chaingunBurstTimer = m.chaingun.burstSpacing;
                m.chaingunBurstTarget = u.target;
            }
        }
        // SRM pod: mid-close salvo — needs LOS.
        if (hasLos && m.srmCooldown <= 0f && m.srmAmmoSalvos > 0 && m.srmSalvoRemaining <= 0
                && dist <= m.srmPod.range) {
            sim.fireMechWeapon(u, u.target, m.srmPod);
            m.srmAmmoSalvos--;
            m.srmCooldown = m.srmPod.cooldown;
            if (m.srmPod.burstCount > 1) {
                m.srmSalvoRemaining = m.srmPod.burstCount - 1;
                m.srmSalvoTimer = m.srmPod.burstSpacing;
                m.srmSalvoTarget = u.target;
            }
        }
        // LRM artillery: long-band indirect-fire salvo. Gated to outside
        // chaingun range (no point lobbing artillery at point-blank targets)
        // and only fires when not actively in close engagement. No-LOS shots
        // get the indirect-fire accuracy penalty.
        if (m.lrmCooldown <= 0f && m.lrmAmmoSalvos > 0 && m.lrmSalvoRemaining <= 0
                && dist <= m.lrmArtillery.range
                && dist >  m.chaingun.range) {
            float accMult = hasLos
                    ? 1.0f
                    : com.dillon.starsectormarines.battle.MechWeapon.LRM_NO_LOS_ACC_MULT;
            sim.fireMechWeapon(u, u.target, m.lrmArtillery, accMult);
            m.lrmAmmoSalvos--;
            m.lrmCooldown = m.lrmArtillery.cooldown;
            if (m.lrmArtillery.burstCount > 1) {
                m.lrmSalvoRemaining = m.lrmArtillery.burstCount - 1;
                m.lrmSalvoTimer = m.lrmArtillery.burstSpacing;
                m.lrmSalvoTarget = u.target;
            }
        }
    }

    /**
     * Returns the squad-centroid cell when the unit is more than
     * {@link #COHESION_RADIUS} cells from it; null otherwise (normal targeting
     * takes over). Solo units — no squad, or squad of one alive — always
     * return null. Reads the cached {@link Squad#centroidX} / {@link Squad#centroidY}
     * filled once per tick by the sim's alert-update pass; excludes self by
     * subtracting our contribution before averaging the remaining members.
     */
    private static int[] cohesionOverride(Unit self, BattleSimulation sim) {
        if (self.squadId == Unit.NO_SQUAD) return null;
        Squad squad = sim.getSquad(self.squadId);
        if (squad == null || squad.aliveMembers <= 1) return null;

        // squad.centroid is sum/count over all alive members including self.
        // Reconstruct the others-only centroid: (sum - self) / (count - 1).
        int othersCount = squad.aliveMembers - 1;
        float sumX = squad.centroidX * squad.aliveMembers - self.cellX;
        float sumY = squad.centroidY * squad.aliveMembers - self.cellY;
        float cx = sumX / othersCount;
        float cy = sumY / othersCount;
        float dx = cx - self.cellX;
        float dy = cy - self.cellY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= COHESION_RADIUS) return null;
        return new int[]{Math.round(cx), Math.round(cy)};
    }
}
