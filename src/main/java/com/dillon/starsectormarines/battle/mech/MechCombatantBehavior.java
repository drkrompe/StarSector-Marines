package com.dillon.starsectormarines.battle.mech;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.CombatantBehavior;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Mech slice of the combatant loop: three concurrent weapon tracks
 * (chaingun, SRM pod, LRM artillery) with independent gating, and a
 * "stand off at LRM range when not in close engagement" movement pattern.
 * Sibling of {@link com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior};
 * {@link CombatantBehavior} picks between the two on presence of a
 * {@link MechLoadoutState} component.
 *
 * <p>No squad cohesion — mechs are typically solo or paired and don't
 * participate in fireteam centroid logic.
 */
public final class MechCombatantBehavior implements UnitBehavior {

    public static final MechCombatantBehavior INSTANCE = new MechCombatantBehavior();

    private MechCombatantBehavior() {}

    @Override
    public void update(Entity u, BattleSimulation sim) {
        Entity target = sim.getTacticalScoring().refreshTargetIfNotShootable(u);
        sim.world().setTargetId(u.entityId, Entity.idOf(target));
        if (target == null) return;

        // The mech's loadout is a component, reached by id (zero-alloc direct
        // store lookup, not the cold-face handle — this is per-tick decide work).
        MechLoadoutState m = sim.world().component(u.entityId, MechLoadoutState.class);

        float dist = TacticalScoring.cellDistance(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean inRange = dist <= sim.world().attackRange(u.entityId);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));

        // The fire pass runs OUTSIDE the marine's `inRange && visible` gate
        // because LRMs are indirect-fire-capable: a mech with line of sight
        // blocked by a building still lobs artillery over it (with an accuracy
        // penalty). Chaingun + SRM still need LOS — that gating lives inside
        // tryFireMechWeapons.
        if (inRange) {
            tryFireMechWeapons(u, m, target, dist, sim, visible);
        }

        // Close engagement = in chaingun range with LOS. Outside that, the
        // mech advances toward a firing position so it can re-acquire LOS for
        // its short-range weapons (LRMs already fire from here via the indirect
        // path above).
        boolean closeEngagement = inRange && visible && dist <= m.srmPod.range;
        if (!closeEngagement && sim.world().moveProgress(u.entityId) == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(u, target);
            if (dest == null) {
                // No reachable firing or vantage cell. Drop the target; the
                // mech's next acquisition cycle picks something it can engage.
                // LRMs already fired indirectly this tick if range allowed.
                sim.world().setTargetId(u.entityId, 0L);
            } else {
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), dest[0], dest[1], sim.getOccupancyMap()));
            }
        }
        if (u.pathIdx < u.pathCellCount()) {
            sim.advanceMovement(u);
        } else {
            sim.world().setMoveProgress(u.entityId, 0f);
            u.setRenderPos(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId));
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
     *       hit roll is scaled by {@link com.dillon.starsectormarines.battle.mech.MechWeapon#LRM_NO_LOS_ACC_MULT}
     *       — "we know roughly where they are, but we can't see them, so a
     *       chunk of the salvo flies wide."</li>
     * </ul>
     */
    public static void tryFireMechWeapons(Entity u, MechLoadoutState m, Entity target, float dist, BattleControl sim, boolean hasLos) {
        tryFireChaingun(u, m, target, dist, sim, hasLos);
        tryFireSrm(u, m, target, dist, sim, hasLos);
        tryFireLrm(u, m, target, dist, sim, hasLos);
    }

    /** Chaingun track: close-band sustained fire — needs LOS, fires when target is within chaingun range and the weapon is off cooldown. */
    public static void tryFireChaingun(Entity u, MechLoadoutState m, Entity target, float dist, BattleControl sim, boolean hasLos) {
        if (hasLos && m.chaingunCooldown <= 0f && m.chaingunBurstRemaining <= 0
                && dist <= m.chaingun.range) {
            sim.fireMechWeapon(u, target, m.chaingun);
            m.chaingunCooldown = m.chaingun.cooldown;
            if (m.chaingun.burstCount > 1) {
                m.chaingunBurstRemaining = m.chaingun.burstCount - 1;
                m.chaingunBurstTimer = m.chaingun.burstSpacing;
                m.chaingunBurstTargetId = target.entityId;
            }
        }
    }

    /** SRM pod track: mid-close salvo — needs LOS, ammo-limited. Skip this call from any action whose doctrine withholds SRMs (e.g. LR Support overwatch). */
    public static void tryFireSrm(Entity u, MechLoadoutState m, Entity target, float dist, BattleControl sim, boolean hasLos) {
        if (hasLos && m.srmCooldown <= 0f && m.srmAmmoSalvos > 0 && m.srmSalvoRemaining <= 0
                && dist <= m.srmPod.range) {
            sim.fireMechWeapon(u, target, m.srmPod);
            m.srmAmmoSalvos--;
            m.srmCooldown = m.srmPod.cooldown;
            if (m.srmPod.burstCount > 1) {
                m.srmSalvoRemaining = m.srmPod.burstCount - 1;
                m.srmSalvoTimer = m.srmPod.burstSpacing;
                m.srmSalvoTargetId = target.entityId;
            }
        }
    }

    /**
     * LRM artillery track: long-band indirect-fire salvo. Gated to outside
     * chaingun range (no point lobbing artillery at point-blank targets) and
     * only fires when not actively in close engagement. No-LOS shots get the
     * indirect-fire accuracy penalty {@link MechWeapon#LRM_NO_LOS_ACC_MULT}.
     */
    public static void tryFireLrm(Entity u, MechLoadoutState m, Entity target, float dist, BattleControl sim, boolean hasLos) {
        if (m.lrmCooldown <= 0f && m.lrmAmmoSalvos > 0 && m.lrmSalvoRemaining <= 0
                && dist <= m.lrmArtillery.range
                && dist >  m.chaingun.range) {
            float accMult = hasLos
                    ? 1.0f
                    : com.dillon.starsectormarines.battle.mech.MechWeapon.LRM_NO_LOS_ACC_MULT;
            sim.fireMechWeapon(u, target, m.lrmArtillery, accMult);
            m.lrmAmmoSalvos--;
            m.lrmCooldown = m.lrmArtillery.cooldown;
            if (m.lrmArtillery.burstCount > 1) {
                m.lrmSalvoRemaining = m.lrmArtillery.burstCount - 1;
                m.lrmSalvoTimer = m.lrmArtillery.burstSpacing;
                m.lrmSalvoTargetId = target.entityId;
            }
        }
    }
}
