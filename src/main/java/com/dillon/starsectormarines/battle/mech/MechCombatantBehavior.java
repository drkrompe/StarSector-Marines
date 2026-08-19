package com.dillon.starsectormarines.battle.mech;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.CombatantBehavior;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * Mech slice of the combatant loop: concurrent installed hardpoints with
 * independent gating, and a
 * "stand off at LRM range when not in close engagement" movement pattern.
 * Sibling of {@link com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior};
 * {@link CombatantBehavior} picks between the two on presence of a
 * {@link MechLoadoutComponent} component.
 *
 * <p>No squad cohesion — mechs are typically solo or paired and don't
 * participate in fireteam centroid logic.
 */
public final class MechCombatantBehavior implements UnitBehavior {

    public static final MechCombatantBehavior INSTANCE = new MechCombatantBehavior();

    private MechCombatantBehavior() {}

    @Override
    public void update(long u, BattleSimulation sim) {
        long target = sim.getTacticalScoring().refreshTargetIfNotShootable(u);
        sim.world().setTargetId(u, target);
        if (target == 0L) return;

        // The mech's loadout is a component, reached by id (zero-alloc direct
        // store lookup, not the cold-face handle — this is per-tick decide work).
        MechLoadoutComponent m = sim.world().mechLoadout(u);

        float dist = TacticalScoring.cellDistance(sim.world().x(u), sim.world().y(u), sim.world().x(target), sim.world().y(target));
        boolean inRange = dist <= sim.world().attackRange(u);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(u), sim.world().cellY(u), sim.world().cellX(target), sim.world().cellY(target));

        // The fire pass runs OUTSIDE the marine's `inRange && visible` gate
        // because LRMs are indirect-fire-capable: a mech with line of sight
        // blocked by a building still lobs artillery over it (with an accuracy
        // penalty). Chaingun + SRM still need LOS — that gating lives inside
        // tryFireMechWeapons.
        if (inRange) {
            tryFireMechWeapons(u, m, target, dist, sim, visible);
        }

        // Close engagement = in the preferred supplied direct band with LOS. Outside that, the
        // mech advances toward a firing position so it can re-acquire LOS for
        // its short-range weapons (LRMs already fire from here via the indirect
        // path above).
        float preferredDirectRange = m.preferredDirectRange();
        boolean closeEngagement = inRange && visible && dist <= preferredDirectRange;
        if (!closeEngagement && sim.movement().mayRepath(u)) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(u, target);
            if (dest == null) {
                // No reachable firing or vantage cell. Drop the target; the
                // mech's next acquisition cycle picks something it can engage.
                // LRMs already fired indirectly this tick if range allowed.
                sim.world().setTargetId(u, 0L);
            } else {
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(u), sim.world().cellY(u), dest[0], dest[1], sim.getOccupancyMap()));
            }
        }
        if (sim.world().pathIdx(u) < Paths.cellCount(sim.world().path(u))) {
            sim.advanceMovement(u);
        }
    }

    /**
     * Triggers all installed mech components in their respective bands. Each mount is
     * independent and may fire on the same tick:
     * <ul>
     *   <li><b>Chaingun</b> — close band, LOS-required. Fires when target is
     *       within chaingun range, the weapon is off cooldown, and there's
     *       direct line of sight. Per-burst rounds queue onto
     *       {@link MechLoadoutComponent} for {@code HeavyWeapons.tick}
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
    public static void tryFireMechWeapons(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        for (MechWeaponMount mount : m.mounts()) {
            tryFireMount(u, m, mount, target, dist, sim, hasLos);
        }
    }

    /** Arms-track compatibility entry point; it may carry chainguns or linear cannons. */
    public static void tryFireChaingun(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        tryFireMount(u, m, m.mount(MechMountSlot.ARMS), target, dist, sim, hasLos);
    }

    /** Fires every installed SRM component; used by doctrines that permit close missiles. */
    public static void tryFireSrm(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        for (MechWeaponMount mount : m.mounts()) {
            if (mount != null && mount.weapon() == MechWeapon.SRM_POD) {
                tryFireMount(u, m, mount, target, dist, sim, hasLos);
            }
        }
    }

    /**
     * Fires every installed LRM component. Gated to outside the arms range
     * (no point lobbing artillery at point-blank targets) and
     * only fires when not actively in close engagement. No-LOS shots get the
     * indirect-fire accuracy penalty {@link MechWeapon#LRM_NO_LOS_ACC_MULT}.
     */
    public static void tryFireLrm(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        for (MechWeaponMount mount : m.mounts()) {
            if (mount != null && mount.weapon() == MechWeapon.LRM_ARTILLERY) {
                tryFireMount(u, m, mount, target, dist, sim, hasLos);
            }
        }
    }

    private static void tryFireMount(long u, MechLoadoutComponent loadout,
                                     MechWeaponMount mount, long target, float dist,
                                     BattleControl sim, boolean hasLos) {
        if (mount == null || !loadout.isAimedAt(target) || mount.cooldown > 0f
                || mount.burstRemaining > 0 || !mount.hasAmmo()) return;
        MechWeapon weapon = mount.weapon();
        if (dist > weapon.range) return;
        boolean indirect = weapon == MechWeapon.LRM_ARTILLERY;
        if (!indirect && !hasLos) return;
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        float minimumIndirectRange = arms != null ? arms.weapon().range : 0f;
        if (indirect && dist <= minimumIndirectRange) return;

        float accuracyMult = indirect && !hasLos ? MechWeapon.LRM_NO_LOS_ACC_MULT : 1f;
        sim.fireMechWeapon(u, target, weapon, accuracyMult);
        mount.consumeTrigger();
        mount.cooldown = weapon.cooldown;
        if (mount.component.projectilesPerTrigger > 1) {
            mount.burstRemaining = mount.component.projectilesPerTrigger - 1;
            mount.burstTimer = weapon.burstSpacing;
            mount.burstTargetId = target;
        }
    }
}
