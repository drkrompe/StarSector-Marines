package com.dillon.starsectormarines.battle.mech;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.CombatantBehavior;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
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
        long target = MechTargeting.refreshTarget(u, sim);
        sim.world().setTargetId(u, target);
        if (target == 0L) return;

        // The mech's loadout is a component, reached by id (zero-alloc direct
        // store lookup, not the cold-face handle — this is per-tick decide work).
        MechLoadoutComponent m = sim.world().mechLoadout(u);

        float dist = TacticalScoring.cellDistance(sim.world().x(u), sim.world().y(u), sim.world().x(target), sim.world().y(target));
        boolean inRange = dist <= sim.world().attackRange(u);
        boolean visible = sim.getTacticalScoring().hasClearShot(u, target);

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
        MechRouteIntent route = MechRouteIntent.forMember(u, MechCombatantBehavior.class, target, sim);
        route.refreshCandidates(MechRouteIntent.cellKey(sim.world().cellX(target), sim.world().cellY(target)));
        route.rejectSettledPerch(u, sim.world().x(target), sim.world().y(target),
                0f, preferredDirectRange, sim);
        if (!closeEngagement && route.resume(u, sim)) return;
        if (!closeEngagement && sim.movement().mayRepath(u)) {
            int[] dest = EngageAtCurrentBand.findMediumDirectPosition(u, target, preferredDirectRange, sim);
            if (dest != null) {
                route.moveToward(u, dest[0], dest[1], sim);
                return;
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
     *       hit roll is scaled by the installed LRM definition's no-LOS multiplier
     *       — "we know roughly where they are, but we can't see them, so a
     *       chunk of the salvo flies wide."</li>
     * </ul>
     */
    public static void tryFireMechWeapons(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        MechWeaponMount[] mounts = m.mounts();
        float armorRating = MechFireOrder.machineArmorRating(target, sim);
        if (armorRating <= 0f) {
            for (MechWeaponMount mount : mounts) {
                tryFireMount(u, m, mount, target, dist, sim, hasLos);
            }
            return;
        }
        // Against a machine, the mounts that best defeat its armor get the reservoir first.
        for (int index : MechFireOrder.order(mounts, armorRating)) {
            tryFireMount(u, m, mounts[index], target, dist, sim, hasLos);
        }
    }

    /** Fires whichever direct-fire weapons are installed on the arm mounts. */
    public static void tryFireArms(long u, MechLoadoutComponent m, long target,
                                   float dist, BattleControl sim, boolean hasLos) {
        for (MechMountSlot slot : MechLoadoutComponent.ARM_SLOTS) {
            tryFireMount(u, m, m.mount(slot), target, dist, sim, hasLos);
        }
    }

    /** Compatibility name retained for older callers and focused weapon tests. */
    public static void tryFireChaingun(long u, MechLoadoutComponent m, long target,
                                       float dist, BattleControl sim, boolean hasLos) {
        tryFireArms(u, m, target, dist, sim, hasLos);
    }

    /** Fires every installed SRM component; used by doctrines that permit close missiles. */
    public static void tryFireSrm(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        for (MechWeaponMount mount : m.mounts()) {
            if (mount != null && WeaponRegistry.isMechSrm(mount.weaponId())) {
                tryFireMount(u, m, mount, target, dist, sim, hasLos);
            }
        }
    }

    /**
     * Fires every installed LRM component. Gated to outside the arms range
     * (no point lobbing artillery at point-blank targets) and
     * only fires when not actively in close engagement. No-LOS shots get the
     * indirect-fire accuracy penalty authored on the installed weapon.
     */
    public static void tryFireLrm(long u, MechLoadoutComponent m, long target, float dist, BattleControl sim, boolean hasLos) {
        for (MechWeaponMount mount : m.mounts()) {
            if (mount != null
                    && WeaponRegistry.isMechLrm(mount.weaponId())) {
                tryFireMount(u, m, mount, target, dist, sim, hasLos);
            }
        }
    }

    private static void tryFireMount(long u, MechLoadoutComponent loadout,
                                     MechWeaponMount mount, long target, float dist,
                                     BattleControl sim, boolean hasLos) {
        if (mount != null && mount.isRotary()) {
            tryFireRotary(u, loadout, mount, target, dist, sim, hasLos);
            return;
        }
        if (mount == null || !loadout.isAimedAt(target) || mount.cycleRemaining > 0f
                || mount.burstRemaining > 0 || !mount.hasAmmo()
                || !loadout.flux.permits(mount.component.fluxPerTrigger(), true)
                || !sim.canFireMechMount(u, mount)) return;
        // An anti-armor warhead is wasted on a man: the rack keeps its pack for armor.
        if (mount.component.antiArmorOnly() && !sim.isHardenedTarget(target)) return;
        WeaponDef weapon = mount.weaponDef();
        if (dist > weapon.range) return;
        boolean indirect = weapon.indirectFire;
        if (!indirect && !hasLos) return;
        float minimumIndirectRange = loadout.armsRange();
        if (indirect && dist <= minimumIndirectRange) return;

        float accuracyMult = indirect && !hasLos ? weapon.noLosAccuracyMult : 1f;
        sim.fireMechWeapon(u, target, mount, accuracyMult);
        loadout.flux.commit(mount.component.fluxPerTrigger());
        mount.commitTrigger(target, null);
    }

    /**
     * How far past its reach a rotary gun's target may stand and still count as an engagement, for which
     * the barrels are kept turning rather than left to spin down.
     */
    static final float ROTARY_ENGAGED_REACH = 1.5f;

    /**
     * A rotary gun fires while a target is exposed to it: the gunner leases the trigger on that target for
     * a few ticks, and the fire pass releases rounds at the spin's rate off the belt, holding the reserve
     * back ({@link RotaryFeed}). Engaged without a shot (the torso still turning, the lane blocked, the
     * target behind cover or just out of reach), the barrels are kept turning so the next shot comes at
     * speed.
     */
    private static void tryFireRotary(long u, MechLoadoutComponent loadout, MechWeaponMount mount,
                                      long target, float dist, BattleControl sim, boolean hasLos) {
        WeaponDef weapon = mount.weaponDef();
        boolean exposed = hasLos && dist <= weapon.range && loadout.isAimedAt(target)
                && sim.canFireMechMount(u, mount);
        if (exposed) mount.rotary.demand(target);
        else if (dist <= weapon.range * ROTARY_ENGAGED_REACH) mount.rotary.hold();
    }
}
