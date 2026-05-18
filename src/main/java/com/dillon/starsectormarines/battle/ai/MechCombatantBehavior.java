package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MechLoadoutState;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Mech slice of the combatant loop: three concurrent weapon tracks
 * (chaingun, SRM pod, LRM artillery) with independent gating, and a
 * "stand off at LRM range when not in close engagement" movement pattern.
 * Sibling of {@link com.dillon.starsectormarines.battle.ai.goap.GoapInfantryBehavior};
 * {@link CombatantBehavior} picks between the two based on {@link Unit#mech}.
 *
 * <p>No squad cohesion — mechs are typically solo or paired and don't
 * participate in fireteam centroid logic.
 */
public final class MechCombatantBehavior implements UnitBehavior {

    public static final MechCombatantBehavior INSTANCE = new MechCombatantBehavior();

    private MechCombatantBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (u.target == null || !u.target.isAlive()) {
            u.target = TacticalScoring.findBestTarget(u, sim);
        }
        if (u.target == null) return;

        float dist = TacticalScoring.cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        boolean inRange = dist <= u.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(u.cellX, u.cellY, u.target.cellX, u.target.cellY);

        // The fire pass runs OUTSIDE the marine's `inRange && visible` gate
        // because LRMs are indirect-fire-capable: a mech with line of sight
        // blocked by a building still lobs artillery over it (with an accuracy
        // penalty). Chaingun + SRM still need LOS — that gating lives inside
        // tryFireMechWeapons.
        if (inRange) {
            tryFireMechWeapons(u, dist, sim, visible);
        }

        // Close engagement = in chaingun range with LOS. Outside that, the
        // mech advances toward a firing position so it can re-acquire LOS for
        // its short-range weapons (LRMs already fire from here via the indirect
        // path above).
        boolean closeEngagement = inRange && visible && dist <= u.mech.srmPod.range;
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
    static void tryFireMechWeapons(Unit u, float dist, BattleSimulation sim, boolean hasLos) {
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
}
