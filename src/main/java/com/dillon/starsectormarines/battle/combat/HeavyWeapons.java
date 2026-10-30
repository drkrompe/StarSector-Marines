package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.RotaryFeed;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.MechRunSystem;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
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
    /**
     * A manual aim point nearer the chassis centre than this many hull widths is pushed out to it
     * along the same bearing, so every barrel converges ahead of its own muzzle rather than on a
     * point beside or behind it.
     */
    static final float MIN_POINT_AIM_HULL_WIDTHS = 1.5f;
    /**
     * A pilot cannot lob an arcing rack's salvo at a point nearer the chassis centre than this many cells (or the
     * weapon's own authored minimum, where larger): the trigger is refused and the HUD reads TOO CLOSE, rather than
     * the salvo coming down on the mech that threw it.
     */
    public static final float MIN_MANUAL_ARC_RANGE_CELLS = ManualPointFire.MIN_ARC_RANGE_CELLS;

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
    /** Running mech orders, whose autonomous fire pays for the pace; {@code null} for none. */
    private final MechRunSystem run;
    private boolean laneDiscipline;

    /**
     * Whether a chassis's own fire control holds a round with a friend in its lane or a burst that would reach a
     * friend or the chassis itself ({@link BallisticResolver#friendInLane}, {@link BallisticResolver#friendInBlast}).
     * A pilot's own point fire is never held.
     */
    public void setLaneDiscipline(boolean laneDiscipline) {
        this.laneDiscipline = laneDiscipline;
    }

    public HeavyWeapons(UnitRosterService roster, NavigationGrid grid,
                        BallisticResolver resolver,
                        ShotService shots, Detonations detonations, Random rng) {
        this(roster, grid, resolver, shots, detonations, rng, null);
    }

    public HeavyWeapons(UnitRosterService roster, NavigationGrid grid,
                        BallisticResolver resolver,
                        ShotService shots, Detonations detonations, Random rng, MechRunSystem run) {
        this.roster = roster;
        this.grid = grid;
        this.resolver = resolver;
        this.shots = shots;
        this.detonations = detonations;
        this.rng = rng;
        this.run = run;
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
        tick(controlledId, aim, trigger, false, TriggerPicks.LINKED);
    }

    /**
     * The left button fires what its pick names in the primary group (LINKED, or one mount) and the
     * right button what its pick names in the secondary group; see {@link MechFireGroups}. A mount
     * neither pick names holds fire. Each mount still obeys its own cycle, burst, ammunition and the
     * chassis flux reservoir. A rotary mount fires for as long as its button is held
     * ({@link #advanceRotary}).
     */
    public void tick(long controlledId, PointFireAim aim, boolean primary, boolean secondary, TriggerPicks picks) {
        int continuing = 0;
        int held = 0;
        if (roster.isAliveById(controlledId) && roster.world().hasMechLoadout(controlledId)) {
            MechLoadoutComponent loadout = roster.world().mechLoadout(controlledId);
            MechWeaponMount[] mounts = loadout.mounts();
            int left = picks.leftMask(MechFireGroups.mounts(mounts));
            int right = picks.rightMask(MechFireGroups.mounts(mounts));
            continuing = left | right;
            int pulled = (primary ? left : 0) | (secondary ? right : 0);
            held = pulled;
            for (MechWeaponMount mount : mounts) {
                // A rotary gun fires while held, in the continuation pass (advanceRotary).
                if (mount == null || mount.isRotary()) continue;
                // A held rack chains its salvos (SalvoChain); letting go starts its cycle from the last one.
                if (!MechFireGroups.contains(pulled, mount)) {
                    mount.releaseChain();
                    continue;
                }
                mount.holdChain();
                if (!mount.triggerReady()) continue;
                float flux = mount.triggerFlux();
                if (!loadout.flux.permits(flux, false)) continue;
                if (fireRoundAtPoint(controlledId, mount, aim, true)) {
                    loadout.flux.commit(flux);
                    mount.commitTrigger(0L, aim);
                    if (mount.chains()) mount.holdSalvos++;
                }
            }
        }
        advanceMechWeapons(controlledId, aim, continuing, held);
    }

    /**
     * Pulls a mount's trigger on a world point, which is how a company opens a wall: nothing is aimed at (a wall is not a
     * body), the round flies to the point or stops at the first wall in its way, and its detonation does the structural
     * damage the weapon's data says. The mount's own gates apply as for any trigger: cycle, burst, ammunition, flux, a
     * barrel that is not through a wall, and a torso turned onto the point. Returns whether a round left.
     */
    public boolean fireMountAtPoint(long shooter, MechWeaponMount mount, PointFireAim aim) {
        if (mount == null || aim == null || !roster.isAliveById(shooter) || !roster.world().hasMechLoadout(shooter)) {
            return false;
        }
        MechLoadoutComponent loadout = roster.world().mechLoadout(shooter);
        if (mount.isRotary()) return fireRotaryAtPoint(shooter, loadout, mount, aim);
        if (mount.cycleRemaining > 0f || mount.burstRemaining > 0 || !mount.hasAmmo()
                || !loadout.flux.permits(mount.component.fluxPerTrigger(), true)) return false;
        if (!fireRoundAtPoint(shooter, mount, aim, false)) return false;
        loadout.flux.commit(mount.component.fluxPerTrigger());
        mount.commitTrigger(0L, aim);
        return true;
    }

    /**
     * A rotary gun opening a wall: one round off the belt at a time, spaced at its full rate, with the
     * flux each round costs. The belt is the limit, as it is for any other fire.
     */
    private boolean fireRotaryAtPoint(long shooter, MechLoadoutComponent loadout, MechWeaponMount mount,
                                      PointFireAim aim) {
        float flux = mount.component.fluxPerRound();
        if (mount.cycleRemaining > 0f || mount.rotary.beltRounds() < 1 || !loadout.flux.permits(flux, true)) {
            return false;
        }
        if (!fireRoundAtPoint(shooter, mount, aim, false)) return false;
        loadout.flux.commit(flux);
        mount.rotary.spend();
        mount.cycleRemaining = 1f / mount.rotary.def().fullRate();
        return true;
    }

    /**
     * One round at a point: a flat-trajectory mount by its ordinary point round, an arcing one by its scatter.
     * {@code manual} is a pilot's trigger: its rockets burst where they are thrown, and its arcing racks drop on
     * the thrown point by {@link GroundBurstAim} rather than by the target-relative hit roll a company's breach
     * shot keeps.
     */
    private boolean fireRoundAtPoint(long shooter, MechWeaponMount mount, PointFireAim aim, boolean manual) {
        WeaponDef weapon = mount.weaponDef();
        if (weapon.arcHeight <= 0f && !weapon.indirectFire) return firePointRound(shooter, mount, aim, manual);
        if (!canFireMechMount(shooter, mount) || !Float.isFinite(weapon.range) || weapon.range <= 0f) return false;
        World world = roster.world();
        MechLoadoutComponent loadout = world.mechLoadout(shooter);
        float hipFacing = roster.entityWorld().getFloat(shooter, roster.components().MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        MechHardpointGeometry.Point muzzle = muzzle(shooter, mount, MechHardpointGeometry.nextReleaseIndex(mount));
        float centreX = world.renderX(shooter);
        float centreY = world.renderY(shooter);
        if (!loadout.isPointAimedAt(aim, centreX, centreY, hipFacing)) return false;
        Faction shooterFaction = roster.identity().faction(shooter);
        float accuracy = weapon.accuracy * sensorAccuracy(loadout);
        boolean hit;
        ShotEndpoint.Endpoint ep;
        if (manual) {
            if (tooClose(mount, (float) Math.hypot(aim.x() - centreX, aim.y() - centreY))) return false;
            PointFireAim target = withStandoff(aim, centreX, centreY, standoffCells(loadout));
            ep = ManualPointFire.arcEndpoint(centreX, centreY, target.x(), target.y(), accuracy, weapon, rng);
            hit = true;
        } else {
            float distance = RangeFalloff.dist(world.x(shooter), world.y(shooter), aim.x(), aim.y());
            float spread = RangeFalloff.spread(weapon.hitSpread, distance, weapon.range);
            hit = rng.nextFloat() < accuracy;
            ep = ShotEndpoint.resolve(aim.x(), aim.y(), hit, spread, rng);
        }
        roster.telemetry().recordRoundFired(shooter);
        launchArc(shooter, weapon, ep, hit, muzzle.x(), muzzle.y(), shooterFaction, roster.moraleImpact(shooter));
        return true;
    }

    /**
     * Carrier capability shared by the selector and the fire procedure: any ranged mount, flat or arcing, can be
     * fired at a cursor point. Ammunition is readiness, not eligibility.
     */
    public static boolean supportsPointFire(MechWeaponMount mount) {
        return mount != null && ManualPointFire.supportsPointFire(mount.weaponDef());
    }

    /**
     * The nearest point, in cells from the chassis centre, a pilot may aim this mount at: zero for a flat-fire
     * mount, {@link #MIN_MANUAL_ARC_RANGE_CELLS} or the weapon's authored minimum for an arcing rack.
     */
    public static float manualMinRangeCells(MechWeaponMount mount) {
        return mount != null ? ManualPointFire.minRangeCells(mount.weaponDef()) : 0f;
    }

    /** Whether a pilot's point {@code distance} cells from the chassis centre is inside this mount's minimum. */
    public static boolean tooClose(MechWeaponMount mount, float distance) {
        return Float.isFinite(distance) && distance < manualMinRangeCells(mount);
    }

    /** A flat-trajectory mount, whose round flies a lane to the point rather than arcing over it. */
    public static boolean firesFlat(MechWeaponMount mount) {
        return supportsPointFire(mount) && !ManualPointFire.arcs(mount.weaponDef());
    }

    /**
     * The radius, in cells, of the disk a pilot's round from this mount lands in when aimed {@code distance} cells
     * from the chassis centre, or a negative value for a mount whose rounds do not burst where they are thrown (a
     * gun). Smoke on the lane widens a rocket's disk further when it fires; this is the clear-air figure.
     */
    public static float manualScatterRadiusCells(MechLoadoutComponent loadout, MechWeaponMount mount, float distance) {
        if (mount == null) return -1f;
        WeaponDef weapon = mount.weaponDef();
        return ManualPointFire.scatterRadiusCells(weapon, weapon.accuracy * sensorAccuracy(loadout), distance,
                standoffCells(loadout));
    }

    /** How far a pilot's round from this mount keeps most of its point-blank chance; see {@link ManualPointFire}. */
    public static float manualOptimalRangeCells(MechLoadoutComponent loadout, MechWeaponMount mount) {
        if (mount == null) return 0f;
        WeaponDef weapon = mount.weaponDef();
        return ManualPointFire.optimalRangeCells(weapon.range, weapon.accuracy * sensorAccuracy(loadout),
                weapon.hitSpread, weapon.aoeRadius, PointAimDispersion.WEAPON);
    }

    private static float standoffCells(MechLoadoutComponent loadout) {
        return MIN_POINT_AIM_HULL_WIDTHS * LayeredMechAppearance.hullWidthCells(loadout.variant.renderScale);
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

    /**
     * The chassis's own aim over every mount: the ship's fire-control calibration, and what a lost
     * head leaves of it (whole while its sensors stand).
     */
    private static float sensorAccuracy(MechLoadoutComponent loadout) {
        MechSections sections = loadout.sections();
        float sensors = sections != null && sections.sensorsLost() ? MechSections.SENSOR_LOSS_ACCURACY : 1f;
        return sensors * loadout.fireControlAccuracy;
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
        World world = roster.world();
        if (world.hasMechLoadout(shooter)) accuracyMult *= sensorAccuracy(world.mechLoadout(shooter));
        if (run != null) accuracyMult *= run.accuracy(shooter);
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
        // Fire control looks down the lane first: a friend between the barrel and the target holds the round.
        if (laneDiscipline && resolver.friendInLane(shooter, fromX, fromY, shooterFaction,
                world.x(target), world.y(target), target)) return;
        BallisticResolver.Source source = new BallisticResolver.Source(shooter, fromX, fromY, 0f, shooterFaction);
        BallisticResolver.Resolution res = resolver.resolve(source, target,
                effectiveAccuracy, effectiveSpread, weapon.roundVelocity,
                weapon.range, weapon.bodyPenetrations, rng);
        // And on where the round will burst: one that would reach a friend, or the chassis itself, is not fired.
        if (laneDiscipline && weapon.aoeRadius > 0f && res.impacts()
                && resolver.friendInBlast(shooter, shooterFaction, res.endX(), res.endY(), weapon.aoeRadius)) return;
        deliverDirectRound(shooter, weapon, res, fromX, fromY);
    }

    /**
     * Refuses unsupported or physically unaimed triggers before spending a mount resource.
     *
     * <p>Alignment is the torso's, judged from the chassis centre: the torso turned onto the point is
     * what aims every mount on it. Each barrel then fires from its own muzzle at the point, so paired
     * and shoulder barrels converge on it instead of each needing the point inside its own few degrees,
     * which a laterally offset muzzle loses at close range. A {@code manual} rocket bursts where it is
     * thrown ({@link BallisticResolver#resolveGroundBurst}); gun rounds, and every round a pilot did not
     * aim, keep the bearing-only point contract.
     */
    private boolean firePointRound(long shooter, MechWeaponMount mount, PointFireAim aim, boolean manual) {
        if (!canFireMechMount(shooter, mount)) return false;
        WeaponDef weapon = mount.weaponDef();
        if (!firesFlat(mount)) return false;
        MechLoadoutComponent loadout = roster.world().mechLoadout(shooter);
        float hipFacing = roster.entityWorld().getFloat(shooter, roster.components().MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        float centreX = roster.world().renderX(shooter);
        float centreY = roster.world().renderY(shooter);
        if (!loadout.isPointAimedAt(aim, centreX, centreY, hipFacing)) return false;
        if (manual && tooClose(mount, (float) Math.hypot(aim.x() - centreX, aim.y() - centreY))) return false;
        PointFireAim target = withStandoff(aim, centreX, centreY, standoffCells(loadout));
        MechHardpointGeometry.Point muzzle = muzzle(shooter, mount, MechHardpointGeometry.nextReleaseIndex(mount));
        if (!target.validFrom(muzzle.x(), muzzle.y())) return false;
        BallisticResolver.Source source = new BallisticResolver.Source(shooter, muzzle.x(), muzzle.y(),
                0f, roster.identity().faction(shooter));
        // Mech mounts retain their own accuracy contract; no infantry training or stance factor.
        float spread = RangeFalloff.spread(weapon.hitSpread, weapon.range, weapon.range);
        float accuracy = weapon.accuracy * sensorAccuracy(loadout);
        BallisticResolver.Resolution resolution = manual && bursts(weapon)
                ? resolver.resolveGroundBurst(source, target.x(), target.y(), accuracy, spread,
                        weapon.roundVelocity, weapon.range, weapon.bodyPenetrations, rng)
                : resolver.resolvePoint(source, target.x(), target.y(), accuracy, spread,
                        weapon.roundVelocity, weapon.range, weapon.bodyPenetrations, rng);
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
        // A rocket's contact warhead rides its detonation, so point defence that stops the
        // rocket stops the shaped charge too. A gun round's contact is the shell itself.
        boolean contactRidesRocket = weapon.contactDamage > 0f && weapon.aoeRadius > 0f
                && weapon.interceptableProjectile;
        long rocketContactId = 0L;
        float rocketContactDamage = 0f;
        if (contactRidesRocket && !res.bodyHits().isEmpty()) {
            BallisticResolver.BodyHit first = res.bodyHits().get(0);
            rocketContactId = first.victimId();
            rocketContactDamage = first.friendly()
                    ? contactDamage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT : contactDamage;
        }
        if (contactDamage > 0f && !contactRidesRocket) {
            for (BallisticResolver.BodyHit bodyHit : res.bodyHits()) {
                float appliedDamage = bodyHit.friendly()
                        ? contactDamage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                        : contactDamage;
                shots.queueImpact(new ShotService.PendingImpact(
                        bodyHit.victimId(), shooter, bodyHit.flightTime(), appliedDamage,
                        contactPenetration, moraleImpact, bodyHit.friendly(), bodyHit.part())
                        .at(bodyHit.x(), bodyHit.y()));
            }
        }

        // An area round's structure damage is its detonation's; only a direct payload wears what it stopped in.
        if (weapon.aoeRadius <= 0f) shots.queueStrike(res, shooter, contactDamage, contactPenetration, weapon.burns());

        long[] areaExclusions = new long[contactDamage > 0f && !contactRidesRocket ? res.bodyHits().size() : 0];
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
                            rocketContactId, rocketContactDamage,
                            rocketContactId != 0L ? contactPenetration : 0f,
                            /*authoredAftermath*/ false,
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
        if (laneDiscipline && weapon.aoeRadius > 0f
                && resolver.friendInBlast(shooter, shooterFaction, ep.x(), ep.y(), weapon.aoeRadius)) return;
        launchArc(shooter, weapon, ep, hit, fromX, fromY, shooterFaction, moraleImpact,
                hit && weapon.contactDamage > 0f ? target : 0L);
    }

    /** The flight and the delayed detonation of an arcing round whose endpoint has been decided. */
    private void launchArc(long shooter, WeaponDef weapon, ShotEndpoint.Endpoint ep, boolean hit,
                           float fromX, float fromY, Faction shooterFaction, float moraleImpact) {
        launchArc(shooter, weapon, ep, hit, fromX, fromY, shooterFaction, moraleImpact, 0L);
    }

    /**
     * As above; a guided warhead that hit its roll lands its contact payload on
     * {@code contactTarget} when it detonates, and that body takes no share of the area.
     */
    private void launchArc(long shooter, WeaponDef weapon, ShotEndpoint.Endpoint ep, boolean hit,
                           float fromX, float fromY, Faction shooterFaction, float moraleImpact,
                           long contactTarget) {
        PendingDetonation onArrival = new PendingDetonation(
                shooter,
                ep.x(), ep.y(), weapon.flightSec,
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, /*aerialDelivery*/ true,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                contactTarget, contactTarget != 0L ? weapon.contactDamage : 0f,
                contactTarget != 0L ? weapon.contactPenetration : 0f,
                /*authoredAftermath*/ false,
                contactTarget != 0L ? new long[] {contactTarget} : new long[0]);
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
     * ticks down mechanical loader/cycler clocks, and advances each finite missile rack's
     * installed replenisher cadence.
     */
    private void advanceMechWeapons(long controlledId, PointFireAim aim, int controlledMask, int heldMask) {
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
            m.flux.advance(BattleSimulation.TICK_DT);

            for (MechWeaponMount mount : m.mounts()) {
                if (mount == null) continue;
                mount.advanceReplenishment(BattleSimulation.TICK_DT,
                        m.missileReplenisher());
                mount.advanceClocks(BattleSimulation.TICK_DT);
                if (mount.isRotary()) {
                    advanceRotary(u, m, mount, u == controlledId, MechFireGroups.contains(heldMask, mount), aim);
                    continue;
                }
                if (mount.burstRemaining <= 0) continue;
                mount.burstTimer -= BattleSimulation.TICK_DT;
                if (mount.burstTimer > 0f) continue;

                if (mount.burstPointAim != null) {
                    boolean manual = u == controlledId && MechFireGroups.contains(controlledMask, mount);
                    // A wall the chassis has been told to open: the burst finishes on the point it began on, for as
                    // long as that is still the point it is aiming at.
                    boolean breaching = !manual && mount.burstPointAim.equals(m.breachAim());
                    if (!manual && !breaching) {
                        mount.clearBurst();
                        continue;
                    }
                    // Manual bursts follow the live cursor. A refused release uses its
                    // scheduled slot so blocked barrels cannot retain a stale backlog.
                    fireRoundAtPoint(u, mount, manual ? aim : mount.burstPointAim, manual);
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
                if (weapon.indirectFire) {
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

    /**
     * A rotary mount's tick: spin, coast and belt advance under one rule, and the rounds it asks for go to
     * the pilot's cursor or the AI's leased target. Each round commits its own flux; a round its gates refuse
     * uses its slot and spends nothing ({@link RotaryFeed}).
     */
    private void advanceRotary(long u, MechLoadoutComponent m, MechWeaponMount mount, boolean controlled,
                               boolean held, PointFireAim aim) {
        RotaryFeed feed = mount.rotary;
        if (controlled) feed.releaseLease();
        // A beam's autonomous reserve is the reservoir's share, not a belt (RotaryFeed.observeFlux).
        feed.observeFlux(m.flux.value() / m.flux.capacity());
        int rounds = feed.advance(BattleSimulation.TICK_DT, controlled && held && aim != null);
        float flux = mount.component.fluxPerRound();
        for (int r = 0; r < rounds; r++) {
            if (!m.flux.permits(flux, !controlled)) break;
            if (controlled) {
                if (!fireRoundAtPoint(u, mount, aim, true)) break;
            } else {
                long target = feed.leaseTarget();
                if (!roster.isAliveById(target)) {
                    feed.releaseLease();
                    break;
                }
                if (!m.isAimedAt(target) || !canFireMechMount(u, mount)) break;
                fireMechWeapon(u, target, mount, 1f);
            }
            m.flux.commit(flux);
            feed.spend();
        }
    }

    /** A rocket with a warhead: the rounds a pilot throws at ground rather than along a bearing. */
    private static boolean bursts(WeaponDef weapon) {
        return ManualPointFire.bursts(weapon);
    }

    /** The aim point, pushed out along its bearing from the centre to at least {@code minimum} cells. */
    static PointFireAim withStandoff(PointFireAim aim, float centreX, float centreY, float minimum) {
        float dx = aim.x() - centreX;
        float dy = aim.y() - centreY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance >= minimum || distance <= 1e-6f) return aim;
        float scale = minimum / distance;
        return new PointFireAim(centreX + dx * scale, centreY + dy * scale);
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
