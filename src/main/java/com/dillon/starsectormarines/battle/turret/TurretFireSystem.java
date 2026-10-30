package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.HitResponseSystem;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEndpoint;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.Random;

/**
 * Definition-driven turret fire procedure. Ground-level burst mounts resolve physical
 * rounds; aerial and indirect mounts retain their existing accuracy/scatter
 * procedures. The system owns payload queuing and shot-event posting. Extracted
 * from {@code BattleSimulation.fireShotFrom} so the sim doesn't own weapon logic.
 * Implements {@link TurretFireSink} so consumers (AirSystem,
 * GroundSystem, TurretBehavior) receive the same functional interface they
 * already depend on.
 *
 * <p>A stateless per-shot <b>System</b> — it owns no state (every field is an
 * injected collaborator), resolving one shot per {@link #fire} call. Named
 * {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code ecs-nouns.md}.
 *
 * <p>Hit-response is delegated to the constructor-injected
 * {@link HitResponseSystem}.
 */
public final class TurretFireSystem implements TurretFireSink {

    private static final float SHOT_LIFETIME = 0.15f;
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    private final Random rng;
    private final CellTopology topology;
    private final ShotService shots;
    private final DamageService damageService;
    private final DetonationSink detonationSink;
    private final HitResponseSystem hitResponse;
    private final World world;
    private final BallisticResolver resolver;
    private final CombatTelemetryService telemetry;

    @FunctionalInterface
    public interface DetonationSink {
        void queue(PendingDetonation det);
    }

    /**
     * What the shooter's own motion does to its gun's accuracy, as a multiplier: a hull whose heading is turning
     * under its turret ({@code HullPivot}). One for a shooter it does not know.
     */
    @FunctionalInterface
    public interface ShooterAccuracy {
        ShooterAccuracy NONE = shooterId -> 1f;

        float multiplier(long shooterId);
    }

    private ShooterAccuracy shooterAccuracy = ShooterAccuracy.NONE;

    /**
     * The chance a gun's crew holds a round whose resolved flight would hit their own side, the way a marine's
     * {@code ExperienceTier.friendlyFireHoldChance} does. Zero for a shooter with no such discipline.
     */
    @FunctionalInterface
    public interface FriendlyFireHold {
        FriendlyFireHold NONE = shooterId -> 0f;

        float chance(long shooterId);
    }

    /**
     * The discipline an emplacement's crew works to: they see a friend between the gun and its target about as
     * often as a regular rifleman does, and let go of the trigger as often.
     */
    public static final float CREW_FRIENDLY_HOLD_CHANCE = 0.5f;

    private FriendlyFireHold friendlyFireHold = FriendlyFireHold.NONE;

    /** Whether a burst at a point would reach a body on the shooter's own side. */
    @FunctionalInterface
    public interface FriendsInBlast {
        FriendsInBlast NONE = (shooterId, side, x, y, radius) -> false;

        boolean reaches(long shooterId, Faction side, float x, float y, float radius);
    }

    private FriendsInBlast friendsInBlast = FriendsInBlast.NONE;

    /**
     * Whether a disciplined crew (one with a {@link FriendlyFireHold}) also checks the lane before firing and never
     * bursts a round over its own hull ({@link BallisticResolver#friendInLane}, {@link BallisticResolver#bodyInBlast}).
     */
    private boolean laneDiscipline;

    public void setLaneDiscipline(boolean laneDiscipline) {
        this.laneDiscipline = laneDiscipline;
    }

    /** Incendiary heat a shooter's next round carries beyond its weapon's own: a tube loaded for an incendiary mission. */
    @FunctionalInterface
    public interface RoundHeat {
        RoundHeat NONE = shooterId -> 0;

        int heat(long shooterId);
    }

    private RoundHeat roundHeat = RoundHeat.NONE;

    public void setRoundHeat(RoundHeat roundHeat) {
        this.roundHeat = roundHeat != null ? roundHeat : RoundHeat.NONE;
    }

    /** The heat a shooter's round with this weapon carries: the weapon's own, else what the shooter has loaded. */
    private int heatOf(long shooterId, WeaponDef weapon) {
        return Math.max(weapon.incendiaryHeat(), roundHeat.heat(shooterId));
    }

    public TurretFireSystem(Random rng, CellTopology topology,
                            ShotService shots, DamageService damageService,
                            DetonationSink detonationSink,
                            HitResponseSystem hitResponse, World world,
                            BallisticResolver resolver,
                            CombatTelemetryService telemetry) {
        this.rng = rng;
        this.topology = topology;
        this.shots = shots;
        this.damageService = damageService;
        this.detonationSink = detonationSink;
        this.hitResponse = hitResponse;
        this.world = world;
        this.resolver = resolver;
        this.telemetry = telemetry;
    }

    /** Who reads a shooter's own motion into its accuracy ({@link ShooterAccuracy}). */
    public void setShooterAccuracy(ShooterAccuracy shooterAccuracy) {
        this.shooterAccuracy = shooterAccuracy != null ? shooterAccuracy : ShooterAccuracy.NONE;
    }

    /**
     * Who holds a resolved round that would hit a friend ({@link FriendlyFireHold}), and how a round with a burst
     * learns whether it would reach one ({@link FriendsInBlast}).
     */
    public void setFriendlyFireHold(FriendlyFireHold friendlyFireHold, FriendsInBlast friendsInBlast) {
        this.friendlyFireHold = friendlyFireHold != null ? friendlyFireHold : FriendlyFireHold.NONE;
        this.friendsInBlast = friendsInBlast != null ? friendsInBlast : FriendsInBlast.NONE;
    }

    /**
     * The hold decision: a round that would hit no friend always goes, and draws nothing from {@code rng}, so a gun
     * that never has a friend in its line plays exactly as it did before the discipline existed; a round that would
     * takes one draw against {@code holdChance}.
     */
    public static boolean holdsRound(boolean friendlyHit, float holdChance, Random rng) {
        return friendlyHit && holdChance > 0f && rng.nextFloat() < holdChance;
    }

    /**
     * Fires one round, returning false when the crew held it: a modeled ground round whose resolved flight would
     * hit their own side and which their discipline refused ({@link FriendlyFireHold}). A held round is neither
     * delivered nor counted; the caller breaks off the rest of its burst.
     */
    @Override
    public boolean fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
                     StructureDef structure, long target, boolean aerialShooter, boolean hasLos,
                     float mountFacingDegrees, int releaseIndex) {
        WeaponDef weapon = structure.mount.weapon;
        int tcx = world.cellX(target);
        int tcy = world.cellY(target);
        float facing = Float.isFinite(mountFacingDegrees)
                ? mountFacingDegrees
                : TurretAim.bearingTo(fromX, fromY, world.x(target), world.y(target));
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                fromX, fromY, facing, structure.mount, releaseIndex);
        fromX = muzzle.x();
        fromY = muzzle.y();
        float distToTarget = (float) Math.sqrt(
                (tcx + 0.5f - fromX) * (tcx + 0.5f - fromX) +
                (tcy + 0.5f - fromY) * (tcy + 0.5f - fromY));
        float effectiveAccuracy = weapon.accuracy * shooterAccuracy.multiplier(shooterId);
        if (weapon.indirectFire) {
            float distNorm = Math.min(1f, distToTarget / Math.max(0.0001f, weapon.range));
            float distFalloff = Math.max(0f, 1f - distNorm * distNorm);
            float losMult = hasLos ? 1f : weapon.noLosAccuracyMult;
            effectiveAccuracy *= distFalloff * losMult;
        }
        if (!aerialShooter && weapon.hitscanBeam() && Float.isFinite(mountFacingDegrees)) {
            return fireBeam(shooterId, fromX, fromY, shooterFaction, structure, target, facing,
                    effectiveAccuracy);
        }
        if (!aerialShooter && weapon.arcHeight <= 0f && weapon.projectileCellsPerSec() <= 0f) {
            return fireGroundDirect(shooterId, fromX, fromY, shooterFaction,
                    structure, target, distToTarget, effectiveAccuracy);
        }

        telemetry.recordRoundFired(shooterId);
        if (weapon.projectileCellsPerSec() > 0f) {
            spawnProjectile(shooterId, fromX, fromY, shooterFaction, structure, target, aerialShooter,
                    distToTarget, effectiveAccuracy);
            return true;
        }

        boolean hit = rng.nextFloat() < effectiveAccuracy;
        boolean isAoe = weapon.aoeRadius > 0f;
        boolean aerialDelivery = aerialShooter || weapon.arcHeight > 0f;
        float effectiveSpread = weapon.hitSpread * Math.min(1f, distToTarget / weapon.range);
        float flight = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        // A burst lands where the target will be when it arrives; a contact
        // hit is applied now, so its tracer ends on the target itself.
        float leadTime = isAoe ? flight : 0f;
        float aimX = world.x(target) + resolver.targetVelocityX(target) * leadTime;
        float aimY = world.y(target) + resolver.targetVelocityY(target) * leadTime;

        float toX, toY;
        if (hit) {
            toX = aimX;
            toY = aimY;
            if (effectiveSpread > 0f) {
                float angle = rng.nextFloat() * (float) (Math.PI * 2);
                float r = rng.nextFloat() * effectiveSpread;
                toX += (float) Math.cos(angle) * r;
                toY += (float) Math.sin(angle) * r;
            }
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            spread += effectiveSpread;
            toX = aimX + (float) Math.cos(angle) * spread;
            toY = aimY + (float) Math.sin(angle) * spread;
        }

        if (!isAoe && hit) {
            if (!aerialDelivery || !topology.isRoofIntact(tcx, tcy)) {
                telemetry.recordRoundHit(shooterId);
                damageService.applyDamage(target, shooterId, weapon.damage, weapon.penetration, 1f);
                hitResponse.rollFallbackOnHit(target);
            }
        }

        if (isAoe) {
            detonationSink.queue(new PendingDetonation(
                    shooterId,
                    toX, toY, flight,
                    weapon.aoeRadius, weapon.damage, weapon.penetration,
                    weapon.wallDamage, shooterFaction, aerialDelivery,
                    weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                    /*friendlyFireImmune*/ false, /*authoredAftermath*/ true).incendiary(heatOf(shooterId, weapon))
                    .withBlast(weapon.blast()));
        }
        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(shooterId, fromX, fromY, toX, toY, hit, shooterFaction,
                lifetime, structure, null, null));
        return true;
    }

    /**
     * A player's round at a ground point, under {@link ManualPointFire}'s contract: refused inside the weapon's
     * manual minimum (measured from the mount), an arcing rack's round dropped on the thrown point with its
     * scatter, a rocket burst where it is thrown or on the first thing in its lane, and a gun round flown along
     * the bearing.
     */
    @Override
    public boolean firePoint(long shooterId, float fromX, float fromY, Faction shooterFaction,
                              StructureDef structure, PointFireAim aim,
                              float mountFacingDegrees, int releaseIndex) {
        WeaponDef weapon = structure.mount.weapon;
        if (aim == null || !aim.validFrom(fromX, fromY)
                || !Float.isFinite(mountFacingDegrees)
                || !ManualPointFire.supportsPointFire(weapon)
                || ManualPointFire.tooClose(weapon, (float) Math.hypot(aim.x() - fromX, aim.y() - fromY))) {
            return false;
        }
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                fromX, fromY, mountFacingDegrees, structure.mount, releaseIndex);
        if (!aim.validFrom(muzzle.x(), muzzle.y())) return false;
        float accuracy = weapon.accuracy * shooterAccuracy.multiplier(shooterId);
        if (ManualPointFire.arcs(weapon)) {
            ShotEndpoint.Endpoint ep = ManualPointFire.arcEndpoint(fromX, fromY, aim.x(), aim.y(),
                    accuracy, weapon, rng);
            telemetry.recordRoundFired(shooterId);
            float speed = weapon.projectileCellsPerSec();
            float flight = speed > 0f
                    ? (float) Math.hypot(ep.x() - muzzle.x(), ep.y() - muzzle.y()) / speed
                    : weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
            launch(shooterId, muzzle.x(), muzzle.y(), shooterFaction, structure, ep.x(), ep.y(), flight,
                    /*hit*/ true, 0L, 0f);
            return true;
        }
        BallisticResolver.Source source = new BallisticResolver.Source(
                shooterId, muzzle.x(), muzzle.y(), structure.muzzleHeightCells(), shooterFaction);
        if (weapon.projectileCellsPerSec() > 0f) {
            BallisticResolver.Resolution res = resolver.resolveGroundBurst(source, aim.x(), aim.y(), accuracy,
                    weapon.hitSpread, weapon.directRoundVelocity(), weapon.range, 0, rng);
            telemetry.recordRoundFired(shooterId);
            launchThrownRocket(shooterId, muzzle.x(), muzzle.y(), shooterFaction, structure, res);
            return true;
        }
        BallisticResolver.Resolution res = resolver.resolvePoint(source, aim.x(), aim.y(),
                accuracy, weapon.hitSpread, weapon.directRoundVelocity(), weapon.range, rng);
        telemetry.recordRoundFired(shooterId);
        deliverGroundDirect(shooterId, muzzle.x(), muzzle.y(), shooterFaction, structure, res);
        return true;
    }

    /**
     * A rocket a player threw at ground: it flies to where its resolution ended, the first body or wall in its
     * lane or the scattered point, and bursts there. Its contact warhead rides the detonation onto the first body
     * it met, as an autonomous salvo's does, so point defence that stops the rocket stops the charge with it.
     */
    private void launchThrownRocket(long shooterId, float fromX, float fromY, Faction shooterFaction,
                                    StructureDef structure, BallisticResolver.Resolution res) {
        WeaponDef weapon = structure.mount.weapon;
        float contact = weapon.contactDamage > 0f ? weapon.contactDamage
                : weapon.aoeRadius <= 0f ? weapon.damage : 0f;
        long contactTarget = 0L;
        float contactDamage = 0f;
        if (contact > 0f && !res.bodyHits().isEmpty()) {
            BallisticResolver.BodyHit first = res.bodyHits().get(0);
            contactTarget = first.victimId();
            contactDamage = first.friendly() ? contact * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT : contact;
        }
        launch(shooterId, fromX, fromY, shooterFaction, structure, res.endX(), res.endY(),
                Math.max(res.flightTime(), 0.05f), res.hitIntended(), contactTarget, contactDamage);
    }

    /** Modeled ground-level path for Vulcan/Heavy-MG bursts and any future direct mount. */
    private boolean fireGroundDirect(long shooterId, float fromX, float fromY,
                                  Faction shooterFaction, StructureDef structure,
                                  long target, float distToTarget,
                                  float effectiveAccuracy) {
        WeaponDef weapon = structure.mount.weapon;
        float effectiveSpread = weapon.hitSpread
                * Math.min(1f, distToTarget / Math.max(0.0001f, weapon.range));
        float holdChance = friendlyFireHold.chance(shooterId);
        // A crew that holds for friends also looks down the lane before it fires: a friend between the gun and its
        // target holds the round outright, before anything is committed or drawn.
        // A gun on a plinth fires from its height: a friend under the round's line is not in its way.
        float muzzleZ = structure.muzzleHeightCells();
        if (laneDiscipline && holdChance > 0f && resolver.friendInLane(shooterId, fromX, fromY, muzzleZ,
                shooterFaction, world.x(target), world.y(target), target)) return false;
        BallisticResolver.Source source = new BallisticResolver.Source(
                shooterId, fromX, fromY, muzzleZ, shooterFaction);
        BallisticResolver.Resolution res = resolver.resolve(
                source, target, effectiveAccuracy, effectiveSpread,
                weapon.directRoundVelocity(), weapon.range, rng);
        // Nor does it put a burst where its own hull is: the round lands too close, and that is not a judgement call.
        if (laneDiscipline && holdChance > 0f && weapon.aoeRadius > 0f && res.impacts()
                && resolver.bodyInBlast(shooterId, res.endX(), res.endY(), weapon.aoeRadius)) return false;
        // The round is resolved before the crew decides, as a rifleman's trigger pull is: the hold is on what
        // this round would actually hit -- a friend it strikes, or one inside the burst where it lands -- not on
        // a friend merely standing near the line.
        if (holdChance > 0f) {
            boolean friendlyThreat = res.friendlyHit()
                    || weapon.aoeRadius > 0f && res.impacts()
                    && friendsInBlast.reaches(shooterId, shooterFaction, res.endX(), res.endY(), weapon.aoeRadius);
            if (holdsRound(friendlyThreat, holdChance, rng)) return false;
        }

        telemetry.recordRoundFired(shooterId);
        deliverGroundDirect(shooterId, fromX, fromY, shooterFaction, structure, res);
        return true;
    }

    /**
     * One pulse of a ground beam. It leaves along the mount's facing, not toward the target: aimed at the target's
     * distance on that line, pulled short by how far the turret is still off its target
     * ({@link #beamReachFraction}), and traced as any round is, so it strikes the first body or wall on its way and
     * otherwise scorches the ground. A lance that opens off target therefore lands short and drags across onto it
     * as the turret tracks, and anything the sweep crosses is struck on the way; once on target, every pulse lands.
     */
    private boolean fireBeam(long shooterId, float fromX, float fromY, Faction shooterFaction,
                             StructureDef structure, long target, float facingDeg, float effectiveAccuracy) {
        WeaponDef weapon = structure.mount.weapon;
        float tx = world.x(target);
        float ty = world.y(target);
        float distance = (float) Math.hypot(tx - fromX, ty - fromY);
        if (distance <= 1e-3f) {
            return fireGroundDirect(shooterId, fromX, fromY, shooterFaction, structure, target, distance,
                    effectiveAccuracy);
        }
        float offDeg = TurretAim.shortestAngleDelta(facingDeg, TurretAim.bearingTo(fromX, fromY, tx, ty));
        float reach = distance * beamReachFraction(offDeg);
        // Sprite-angle convention: 0 deg is +Y, positive counter-clockwise (TurretAim.bearingTo).
        double rad = Math.toRadians(facingDeg + 90f);
        float aimX = fromX + (float) Math.cos(rad) * reach;
        float aimY = fromY + (float) Math.sin(rad) * reach;
        float effectiveSpread = weapon.hitSpread * Math.min(1f, distance / Math.max(0.0001f, weapon.range));
        BallisticResolver.Source source = new BallisticResolver.Source(shooterId, fromX, fromY,
                structure.muzzleHeightCells(), shooterFaction);
        BallisticResolver.Resolution res = resolver.resolveGroundBurst(source, aimX, aimY, effectiveAccuracy,
                effectiveSpread, weapon.directRoundVelocity(), weapon.range, 0, rng);
        float holdChance = friendlyFireHold.chance(shooterId);
        if (holdChance > 0f && holdsRound(res.friendlyHit(), holdChance, rng)) return false;
        telemetry.recordRoundFired(shooterId);
        deliverGroundDirect(shooterId, fromX, fromY, shooterFaction, structure, res);
        return true;
    }

    /**
     * How much of the way to its target a beam reaches when the turret is {@code offDeg} off it: all of it on
     * target, falling to {@link #BEAM_SHORT_FRACTION} at the widest a beam opens, so the lance visibly walks in.
     */
    static float beamReachFraction(float offDeg) {
        float t = Math.min(1f, Math.abs(offDeg) / TurretAim.BEAM_OPEN_ARC_DEG);
        return 1f - (1f - BEAM_SHORT_FRACTION) * t;
    }

    /** The share of the target's distance a beam reaches when it opens at the widest arc. */
    static final float BEAM_SHORT_FRACTION = 0.7f;

    private void deliverGroundDirect(long shooterId, float fromX, float fromY,
                                     Faction shooterFaction, StructureDef structure,
                                     BallisticResolver.Resolution res) {
        WeaponDef weapon = structure.mount.weapon;

        if (weapon.aoeRadius > 0f && res.impacts()) {
            queueGroundDetonation(shooterId, shooterFaction, structure, res);
        } else if (weapon.aoeRadius <= 0f && res.victimId() != 0L) {
            float appliedDamage = res.friendlyHit()
                    ? weapon.damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                    : weapon.damage;
            shots.queueImpact(new ShotService.PendingImpact(
                    res.victimId(), shooterId, res.flightTime(), appliedDamage,
                    weapon.penetration, /*moraleImpact*/ 1f, res.friendlyHit(), res.victimPart())
                    .at(res));
        }
        if (weapon.aoeRadius <= 0f) shots.queueStrike(res, shooterId, weapon.damage, weapon.penetration, weapon.burns());

        shots.postShot(new ShotEvent(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                structure, null, null, null, /*moraleImpact*/ 1f,
                res.victimId() != 0L, res.kind(), shooterId));
    }

    private void queueGroundDetonation(long shooterId, Faction shooterFaction,
                                       StructureDef structure,
                                       BallisticResolver.Resolution res) {
        WeaponDef weapon = structure.mount.weapon;
        boolean hasDirectPayload = res.victimId() != 0L && weapon.contactDamage > 0f;
        float directDamage = hasDirectPayload && res.friendlyHit()
                ? weapon.contactDamage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                : hasDirectPayload ? weapon.contactDamage : 0f;
        detonationSink.queue(new PendingDetonation(
                shooterId,
                res.endX(), res.endY(), res.flightTime(),
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                hasDirectPayload ? res.victimId() : 0L,
                directDamage, hasDirectPayload ? weapon.contactPenetration : 0f,
                /*authoredAftermath*/ true).incendiary(heatOf(shooterId, weapon)).withBlast(weapon.blast()));
    }

    private void spawnProjectile(long shooterId, float fromX, float fromY, Faction shooterFaction,
                                 StructureDef structure, long target, boolean aerialShooter,
                                 float distToTarget, float effectiveAccuracy) {
        WeaponDef weapon = structure.mount.weapon;
        boolean aerialDelivery = aerialShooter || weapon.arcHeight > 0f;
        float speed = weapon.projectileCellsPerSec();

        // Lead: aim where the target will be after the flight to where it is
        // now, one step like the direct resolver's. The scatter below is drawn
        // around that point, so the round is no more accurate for leading —
        // it only stops aiming behind a man who is walking. A target that
        // turns or stops during the flight is the error the lead cannot see.
        float targetX = world.x(target);
        float targetY = world.y(target);
        float leadTime = (float) Math.hypot(targetX - fromX, targetY - fromY) / speed;
        float aimX = targetX + resolver.targetVelocityX(target) * leadTime;
        float aimY = targetY + resolver.targetVelocityY(target) * leadTime;

        float distScale = Math.min(1f, distToTarget / Math.max(0.0001f, weapon.range));
        boolean hit = rng.nextFloat() < effectiveAccuracy;
        float scatterRadius = weapon.hitSpread * distScale;
        if (!hit) {
            // Projectile kinds still resolve the same real accuracy roll as
            // instant/tracer kinds. A miss expands beyond the nominal impact
            // pattern instead of merely nudging every round by an
            // accuracy-dependent amount.
            scatterRadius += MISS_OFFSET_MIN
                    + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
        }
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float r = rng.nextFloat() * scatterRadius;
        float toX = aimX + (float) Math.cos(angle) * r;
        float toY = aimY + (float) Math.sin(angle) * r;

        float flightTime = (float) Math.hypot(aimX - fromX, aimY - fromY) / speed;

        // An anti-armor rocket's contact warhead rides its detonation, as a mech rack's does: a round
        // that hit its roll lands the shaped charge on the target, which takes no share of the area,
        // and point defence that stops the rocket stops the charge with it.
        long contactTarget = hit && weapon.contactDamage > 0f ? target : 0L;
        launch(shooterId, fromX, fromY, shooterFaction, structure, toX, toY, flightTime, hit,
                contactTarget, weapon.contactDamage, aerialDelivery);
    }

    private void launch(long shooterId, float fromX, float fromY, Faction shooterFaction, StructureDef structure,
                        float toX, float toY, float flightTime, boolean hit,
                        long contactTarget, float contactDamage) {
        launch(shooterId, fromX, fromY, shooterFaction, structure, toX, toY, flightTime, hit,
                contactTarget, contactDamage, structure.mount.weapon.arcHeight > 0f);
    }

    /**
     * The flight of a round whose endpoint is decided and the burst it carries: a travelling projectile that point
     * defence can stop, or for a weapon with no travelling round a detonation timed to its flight. A
     * {@code contactTarget} takes {@code contactDamage} from the warhead and no share of the area.
     */
    private void launch(long shooterId, float fromX, float fromY, Faction shooterFaction, StructureDef structure,
                        float toX, float toY, float flightTime, boolean hit,
                        long contactTarget, float contactDamage, boolean aerialDelivery) {
        WeaponDef weapon = structure.mount.weapon;
        PendingDetonation onArrival = new PendingDetonation(
                shooterId,
                toX, toY, flightTime,
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, aerialDelivery,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                contactTarget, contactTarget != 0L ? contactDamage : 0f,
                contactTarget != 0L ? weapon.contactPenetration : 0f,
                /*authoredAftermath*/ true,
                contactTarget != 0L ? new long[] {contactTarget} : new long[0]);
        onArrival.incendiary(heatOf(shooterId, weapon)).withBlast(weapon.blast());
        if (weapon.projectileCellsPerSec() > 0f) {
            shots.queueProjectile(new Projectile(fromX, fromY, toX, toY,
                    weapon.boostRamp, weapon.arcHeight,
                    shooterFaction, aerialDelivery, flightTime, onArrival, weapon.id,
                    weapon.pointDefenseTarget));
        } else {
            detonationSink.queue(onArrival);
        }
        shots.postShot(new ShotEvent(shooterId, fromX, fromY, toX, toY, hit, shooterFaction,
                flightTime, structure, null, null));
    }
}
