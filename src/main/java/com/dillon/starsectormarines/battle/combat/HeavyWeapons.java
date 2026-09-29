package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Random;

/**
 * Chassis-mounted weapons on motorized / heavy units. Modular mech hardpoints
 * use it today; future tanks and hovercraft can hook in through the same
 * {@link MechLoadoutComponent} state bag.
 *
 * <p>The split from {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons} is along the unit's character —
 * handheld squad weapons vs vehicle-mounted hardpoints — not along weapon
 * class. A future "infantry rocket launcher" would still live in infantry;
 * a hypothetical mech-mounted rifle would live here.
 *
 * <p>Every installed mount owns an independent firing track. Direct weapons
 * and SRMs resolve as modeled ground-level rounds, while LRM artillery retains
 * its indirect scatter/projectile procedure. Continuation pumps every queued
 * burst or salvo at per-component spacing in {@link #tick}.
 *
 * <p>Smoking-wreck spawn for dead mechs is no longer here — it moved to the
 * {@code MechWreckSystem} death-event handler, so it reacts to the one death
 * seam instead of re-scanning the unit list each tick.
 */
public class HeavyWeapons {

    private static final float SHOT_LIFETIME = 0.15f;

    private final UnitRosterService roster;
    private final NavigationGrid grid;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final Detonations detonations;

    /**
     * Reused per-tick gather of the live mechs before the continuation pass.
     * The pass fires weapons, which can kill a target and release it from the
     * registry mid-pass (swap-and-pop); gathering first makes the iteration a
     * snapshot, so a release doesn't reshuffle the slots out from under it.
     * Only mechs are gathered (a handful per battle), so the copy is cheap.
     */
    private final LongArrayList mechScratch = new LongArrayList();

    /** The battle's seeded stream — see {@code BattleSimulation.random()}. */
    private final Random rng;

    public HeavyWeapons(UnitRosterService roster, NavigationGrid grid,
                        BallisticResolver resolver,
                        ShotService shots, Detonations detonations, Random rng) {
        this.roster = roster;
        this.grid = grid;
        this.resolver = resolver;
        this.shots = shots;
        this.detonations = detonations;
        this.rng = rng;
    }

    /**
     * Per-tick pass: replenishes finite missile racks and drains queued rounds
     * from every installed mech mount.
     */
    public void tick() {
        tick(0L, null, false);
    }

    /** Manual triggers join the same mount clocks and continuation pass as autonomous fire. */
    public void tick(long controlledId, PointFireAim aim, boolean trigger) {
        tick(controlledId, aim, trigger, 0);
    }

    /** Zero selects all direct mounts; positive values select a stable hardpoint ordinal plus one. */
    public void tick(long controlledId, PointFireAim aim, boolean trigger, int selectedWeapon) {
        if (trigger && roster.isAliveById(controlledId)
                && roster.world().hasMechLoadout(controlledId)) {
            MechLoadoutComponent loadout = roster.world().mechLoadout(controlledId);
            for (MechWeaponMount mount : loadout.mounts()) {
                if (mount == null || mount.cooldown > 0f || mount.burstRemaining > 0 || !mount.hasAmmo()) continue;
                if (!selected(mount, selectedWeapon)) continue;
                if (firePointRound(controlledId, mount, aim)) mount.commitTrigger(0L, aim);
            }
        }
        advanceMechWeapons(controlledId, aim, selectedWeapon);
    }

    /** Carrier capability shared by the selector and the fire procedure. Ammunition is readiness, not eligibility. */
    public static boolean supportsPointFire(MechWeaponMount mount) {
        if (mount == null) return false;
        WeaponDef weapon = mount.weaponDef();
        return weapon.arcHeight == 0f && !weapon.indirectFire
                && Float.isFinite(weapon.range) && weapon.range > 0f;
    }

    private static boolean selected(MechWeaponMount mount, int selection) {
        return selection == 0 || selection == mount.slot.ordinal() + 1;
    }

    /**
     * Convenience overload — full accuracy. Used by all the precision-fire
     * code paths (chaingun, SRM, line-of-sight LRMs).
     */
    public void fireMechWeapon(long shooter, long target, WeaponDef weapon) {
        fireMechWeapon(shooter, target, weapon, 1.0f);
    }

    /**
     * Fires one round of a mech chassis weapon. Damage / accuracy / vsTurret
     * pull from the {@link WeaponDef} parameter rather than the shooter's
     * baked Entity stats — concurrent mounts can carry very different numbers,
     * so the weapon's profile drives the math.
     * Caller is responsible for cooldown / ammo / range gating before calling.
     *
     * <p>{@code accuracyMult} scales the weapon's base accuracy at the hit
     * roll. Set to 1.0 for line-of-sight fire; the LRM indirect-fire path
     * passes the installed definition's no-LOS accuracy multiplier.
     */
    public void fireMechWeapon(long shooter, long target, WeaponDef weapon, float accuracyMult) {
        World world = roster.world();
        fireMechWeaponAt(shooter, target, weapon, accuracyMult,
                world.renderX(shooter), world.renderY(shooter));
    }

    /** Fires from the installed mount's posed hardpoint rather than the chassis center. */
    public void fireMechWeapon(long shooter, long target, MechWeaponMount mount,
                               float accuracyMult) {
        if (!canFireMechMount(shooter, mount)) return;
        int releaseIndex = MechHardpointGeometry.nextReleaseIndex(mount);
        MechHardpointGeometry.Point muzzle = muzzle(shooter, mount, releaseIndex);
        fireMechWeaponAt(shooter, target, mount.weaponDef(), accuracyMult,
                muzzle.x(), muzzle.y());
    }

    /** The barrel cannot put its source through structural terrain, including its own end cell. */
    public boolean canFireMechMount(long shooter, MechWeaponMount mount) {
        World world = roster.world();
        if (mount == null || !roster.isAliveById(shooter) || !world.hasMechLoadout(shooter)
                || world.mechLoadout(shooter).mount(mount.slot) != mount) return false;
        MechHardpointGeometry.Point muzzle = muzzle(shooter, mount, MechHardpointGeometry.nextReleaseIndex(mount));
        float x = world.renderX(shooter);
        float y = world.renderY(shooter);
        long wall = grid.firstWallOnLine(x, y, muzzle.x(), muzzle.y());
        return (int) wall == -1 && (int) (wall >>> 32) == -1
                && grid.firstProjectileBlockingEdgeBarrierOnLine(x, y, muzzle.x(), muzzle.y()) == null;
    }

    private void fireMechWeaponAt(long shooter, long target, WeaponDef weapon,
                                  float accuracyMult, float fromX, float fromY) {
        roster.telemetry().recordRoundFired(shooter);
        if (weapon.arcHeight <= 0f) {
            fireDirectRound(shooter, target, weapon, accuracyMult, fromX, fromY);
            return;
        }

        fireIndirectRound(shooter, target, weapon, accuracyMult, fromX, fromY);
    }

    /** Modeled ground-level round for chaingun, cannon, and SRM tracks. */
    private void fireDirectRound(long shooter, long target, WeaponDef weapon,
                                 float accuracyMult, float fromX, float fromY) {
        World world = roster.world();
        float effectiveAccuracy = weapon.accuracy * accuracyMult;
        Faction shooterFaction = roster.identity().faction(shooter);
        float distToTarget = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = RangeFalloff.spread(
                weapon.hitSpread, distToTarget, weapon.range);
        BallisticResolver.Source source = new BallisticResolver.Source(shooter, fromX, fromY, 0f, shooterFaction);
        BallisticResolver.Resolution res = resolver.resolve(source, target,
                effectiveAccuracy, effectiveSpread, weapon.roundVelocity,
                weapon.range, weapon.bodyPenetrations, rng);
        deliverDirectRound(shooter, weapon, res, fromX, fromY);
    }

    /** Refuses unsupported or physically unaimed triggers before spending a mount resource. */
    private boolean firePointRound(long shooter, MechWeaponMount mount, PointFireAim aim) {
        if (!canFireMechMount(shooter, mount)) return false;
        WeaponDef weapon = mount.weaponDef();
        if (!supportsPointFire(mount)) return false;
        MechLoadoutComponent loadout = roster.world().mechLoadout(shooter);
        float hipFacing = roster.entityWorld().getFloat(shooter, roster.components().MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        if (!loadout.isPointAimedAt(aim, roster.world().renderX(shooter),
                roster.world().renderY(shooter), hipFacing)) return false;
        MechHardpointGeometry.Point muzzle = muzzle(shooter, mount, MechHardpointGeometry.nextReleaseIndex(mount));
        if (!loadout.isPointAimedAt(aim, muzzle.x(), muzzle.y(), hipFacing)) return false;
        BallisticResolver.Source source = new BallisticResolver.Source(shooter, muzzle.x(), muzzle.y(),
                0f, roster.identity().faction(shooter));
        // Mech mounts retain their own accuracy contract; no infantry training or stance factor.
        float spread = RangeFalloff.spread(weapon.hitSpread, weapon.range, weapon.range);
        BallisticResolver.Resolution resolution = resolver.resolvePoint(source, aim.x(), aim.y(),
                weapon.accuracy, spread, weapon.roundVelocity, weapon.range, weapon.bodyPenetrations, rng);
        roster.telemetry().recordRoundFired(shooter);
        deliverDirectRound(shooter, weapon, resolution, muzzle.x(), muzzle.y());
        return true;
    }

    /** AI and point fire share contact, delayed payload, interception, and presentation. */
    private void deliverDirectRound(long shooter, WeaponDef weapon, BallisticResolver.Resolution res,
                                    float fromX, float fromY) {
        Faction shooterFaction = roster.identity().faction(shooter);
        float moraleImpact = roster.moraleImpact(shooter);
        float contactDamage = weapon.contactDamage > 0f
                ? weapon.contactDamage
                : weapon.aoeRadius <= 0f ? weapon.damage : 0f;
        float contactPenetration = weapon.contactDamage > 0f
                ? weapon.contactPenetration : weapon.penetration;
        if (contactDamage > 0f) {
            for (BallisticResolver.BodyHit bodyHit : res.bodyHits()) {
                float appliedDamage = bodyHit.friendly()
                        ? contactDamage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                        : contactDamage;
                shots.queueImpact(new ShotService.PendingImpact(
                        bodyHit.victimId(), shooter, bodyHit.flightTime(), appliedDamage,
                        contactPenetration, moraleImpact, bodyHit.friendly()));
            }
        }

        long[] areaExclusions = new long[contactDamage > 0f ? res.bodyHits().size() : 0];
        for (int i = 0; i < areaExclusions.length; i++) {
            areaExclusions[i] = res.bodyHits().get(i).victimId();
        }

        if (weapon.aoeRadius > 0f) {
            PendingDetonation onArrival = res.impacts()
                    ? new PendingDetonation(
                            shooter,
                            res.endX(), res.endY(), res.flightTime(),
                            weapon.aoeRadius, weapon.damage, weapon.penetration,
                            weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                            weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                            /*friendlyFireImmune*/ false,
                            0L, 0f, 0f, /*authoredAftermath*/ false,
                            areaExclusions)
                    : null;
            // Rocket-class rounds own a Projectile so future point defense can
            // intercept the payload. Gun-launched HE remains a ballistic
            // ShotEvent paired with a timed detonation; it must not inherit a
            // rocket's boost curve merely because both are explosive.
            if (weapon.interceptableProjectile) {
                shots.queueProjectile(new Projectile(
                        fromX, fromY, res.endX(), res.endY(),
                        weapon.boostRamp, /*arcHeight*/ 0f,
                        shooterFaction, /*aerialDelivery*/ false,
                        res.flightTime(), onArrival, weapon.id,
                        weapon.pointDefenseTarget));
            } else if (onArrival != null) {
                detonations.queue(onArrival);
            }
        }

        shots.postShot(new ShotEvent(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                null, null, null, weapon, moraleImpact,
                res.victimId() != 0L, res.kind(), shooter));
    }

    /** Legacy indirect scatter/projectile procedure retained for LRM artillery. */
    private void fireIndirectRound(long shooter, long target, WeaponDef weapon,
                                   float accuracyMult, float fromX, float fromY) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        float moraleImpact = roster.moraleImpact(shooter);
        float distToTarget = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = RangeFalloff.spread(
                weapon.hitSpread, distToTarget, weapon.range);
        boolean hit = rng.nextFloat() < weapon.accuracy * accuracyMult;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(
                world.renderX(target), world.renderY(target),
                hit, effectiveSpread, rng);

        PendingDetonation onArrival = new PendingDetonation(
                shooter,
                ep.x(), ep.y(), weapon.flightSec,
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, /*aerialDelivery*/ true,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false);
        shots.queueProjectile(new Projectile(
                fromX, fromY, ep.x(), ep.y(),
                weapon.boostRamp, weapon.arcHeight,
                shooterFaction, /*aerialDelivery*/ true,
                weapon.flightSec, onArrival, weapon.id,
                weapon.pointDefenseTarget));
        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(shooter, fromX, fromY, ep.x(), ep.y(), hit,
                shooterFaction, lifetime, null, null, null, weapon, moraleImpact));
    }

    /**
     * Per-tick mech-weapon continuation — runs the three chassis tracks
     * (chaingun burst, SRM salvo, LRM salvo) for every unit with a
     * {@link MechLoadoutComponent}. Mirrors {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#tick} for the
     * marine primary side; lives separate because the mech burst state is on
     * the loadout, not the unit.
     *
     * <p>The trigger decisions (start a burst / launch a salvo / lob an LRM)
     * happen inside {@code MechCombatantBehavior.tryFireMechWeapons}. This pass
     * handles continuation — emitting queued rounds at their proper spacing —
     * ticks down per-weapon cooldowns, and advances each finite missile rack's
     * installed replenisher cadence.
     */
    private void advanceMechWeapons(long controlledId, PointFireAim aim, int selectedWeapon) {
        // Gather the live mechs first (walking the MECH_LOADOUT query — only mech
        // entities match it, so no scan over the whole registry), then run the
        // continuation pass over the snapshot. Other arrivals in this phase
        // can release a target and swap-and-pop the registry; iterating a
        // snapshot keeps that from corrupting the pass. The query excludes
        // CORPSE (live mechs only), and isLive still guards an entity released
        // earlier this same drain.
        mechScratch.clear();
        EntityWorld entityWorld = roster.entityWorld();
        BattleComponents components = roster.components();
        for (ArchetypeTable t : entityWorld.matched(components.mechLoadouts)) {
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                long uid = t.entityAt(r);
                if (roster.isLive(uid)) mechScratch.add(uid);
            }
        }
        World world = roster.world();
        for (int i = 0, n = mechScratch.size(); i < n; i++) {
            long u = mechScratch.getLong(i);
            if (!roster.isAliveById(u)) continue; // killed earlier in this same pass
            MechLoadoutComponent m = world.mechLoadout(u);

            for (MechWeaponMount mount : m.mounts()) {
                if (mount == null) continue;
                mount.advanceReplenishment(BattleSimulation.TICK_DT,
                        m.missileReplenisher());
                if (mount.cooldown > 0f) mount.cooldown -= BattleSimulation.TICK_DT;
                if (mount.burstRemaining <= 0) continue;
                mount.burstTimer -= BattleSimulation.TICK_DT;
                if (mount.burstTimer > 0f) continue;

                if (mount.burstPointAim != null) {
                    if (u != controlledId || !selected(mount, selectedWeapon)) {
                        mount.clearBurst();
                        continue;
                    }
                    // Manual bursts follow the live cursor. A refused release uses its
                    // scheduled slot so blocked barrels cannot retain a stale backlog.
                    firePointRound(u, mount, aim);
                    finishBurstRound(mount);
                    continue;
                }
                // A controlled chassis never continues autonomous target bursts.
                long target = mount.burstTargetId;
                if (u == controlledId || !roster.isAliveById(target)) {
                    mount.clearBurst();
                    continue;
                }
                if (!m.isAimedAt(target) || !canFireMechMount(u, mount)) continue;

                WeaponDef weapon = mount.weaponDef();
                float accuracyMult = 1f;
                if (WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(weapon.id)) {
                    boolean hasLos = grid.hasLineOfFire(
                            world.x(u), world.y(u),
                            world.x(target), world.y(target));
                    accuracyMult = hasLos ? 1f : weapon.noLosAccuracyMult;
                }
                fireMechWeapon(u, target, mount, accuracyMult);
                finishBurstRound(mount);
            }
        }
    }

    private static void finishBurstRound(MechWeaponMount mount) {
        mount.burstRemaining--;
        if (mount.burstRemaining <= 0) mount.clearBurst();
        else mount.burstTimer = mount.weaponDef().burstSpacing;
    }

    private MechHardpointGeometry.Point muzzle(long shooter, MechWeaponMount mount,
                                               int releaseIndex) {
        World world = roster.world();
        MechLoadoutComponent loadout = world.mechLoadout(shooter);
        float waistOffsetX = 0f;
        float waistOffsetY = 0f;
        EntityWorld entityWorld = roster.entityWorld();
        BattleComponents components = roster.components();
        if (entityWorld.has(shooter, components.MECH_GAIT_STATE)) {
            MechGaitState gait = (MechGaitState) entityWorld.getObject(
                    shooter, components.MECH_GAIT_STATE,
                    BattleComponents.MECH_GAIT_STATE_STATE);
            if (gait != null) {
                waistOffsetX = gait.waistOffsetX();
                waistOffsetY = gait.waistOffsetY();
            }
        }
        return MechHardpointGeometry.muzzle(
                world.renderX(shooter), world.renderY(shooter),
                waistOffsetX, waistOffsetY, loadout.torsoFacingDegrees,
                loadout, mount, releaseIndex);
    }
}
