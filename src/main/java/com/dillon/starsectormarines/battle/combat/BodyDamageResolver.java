package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.HitLog;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.HullTurretSection;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Random;

/**
 * Applies the shared armour/structure law to a body that is not a roster unit.
 *
 * <p>It replaces one resolver per carrier. Those two were identical to the line
 * — same {@link DurabilityModel}, same telemetry seam, same overkill clamp —
 * and differed only in which service they asked whether the target was still
 * reachable and which system they told about the kill. Both of those are the
 * {@link BodyCarrier}'s to answer, so there is nothing left to duplicate.
 *
 * <p>What stays split is the sink, because that difference is real: a chassis
 * stops and becomes scenery, an aircraft has to light the cook-off, leave its
 * wreck and give the runway back. Common route, per-carrier sink — and a roster
 * unit keeps {@code DamageResolver}, whose death cascade (corpse pose, equipment
 * drop, squad-leader promotion, the death mailbox) has nothing to do for either
 * of these.
 *

 * <p>A ground hull's plate depends on the arc a hit arrives on ({@link ArmorArcs}, profiles on
 * {@link VehicleType#armorArcs()}): the hull's arc is read against the hull's facing, a turret hit's against the
 * turret's. The arc is read from where the hit's shooter stands, which no random draw decides.
 *
 * <p>A ground hull is resolved by location ({@link HullLocations}): for a hull whose turret is a section of it
 * ({@link HullTurretSection}) a roll on the battle's random stream decides whether the hit lands on the turret,
 * then a drive roll whose share depends on the arc, and the hit meets the plate on the side it arrives from. Only
 * the hull's own structure decides death; a hull whose drive goes is {@link Crippled}, slowed but not stranded. A hit with no
 * origin meets the front plate and rolls no drive.
 *
 * <p>An attributed hit on an armed hull is noted by its crew, shooter and bearing, so two machines hitting it from two
 * directions put it in crossfire ({@link com.dillon.starsectormarines.battle.vehicle.Crossfire}).
 *
 * <p>Serial only: it holds one reusable {@link DurabilityModel.Resolution},
 * which is the shape both resolvers it replaces already had.
 */
public final class BodyDamageResolver {

    private final UnitRosterService roster;
    private final Random rng;
    private final DurabilityModel.Resolution durability = new DurabilityModel.Resolution();
    private final HullLocations.Hit locatedHit = new HullLocations.Hit();

    public BodyDamageResolver(UnitRosterService roster, Random rng) {
        this.roster = roster;
        this.rng = rng;
    }

    public void resolve(BodyCarrier carrier, long targetId, long attackerId,
                        float damage, float penetration) {
        if (!carrier.isTargetable(targetId)) return;
        apply(carrier, targetId, attackerId, damage, penetration);
    }

    /**
     * A hit aimed at the body by a shooter that can reach where it is, so an airborne body is a legal
     * target. The ordinary route refuses one, because a blast or a bullet fired at the ground must never
     * reach a machine in the air; an aimed shot has already answered that question (the shooter's weapon
     * may engage this class of craft) and is owed the same armour and structure law as any other hit.
     */
    public void resolveAimed(BodyCarrier carrier, long targetId, long attackerId,
                             float damage, float penetration) {
        if (!carrier.isPresent(targetId)) return;
        apply(carrier, targetId, attackerId, damage, penetration);
    }

    private void apply(BodyCarrier carrier, long targetId, long attackerId,
                       float damage, float penetration) {
        World world = roster.world();
        float hpBefore = world.hp(targetId);
        float armorBefore = world.armor(targetId);
        float postCover = damage * world.damageTakenMult(targetId);
        float rating = world.armorRating(targetId);
        ConvoyService convoy = carrier instanceof ConvoyService c ? c : null;
        GroundTurret turret = convoy != null ? convoy.turret(targetId) : null;
        HullTurretSection section = turret != null ? turret.section : null;
        HullLocations locations = convoy != null ? convoy.locations(targetId) : null;
        // No origin to read, no arc: the hull's nominal plate.
        boolean origin = hasOrigin(world, targetId, attackerId);
        ArmorArcs.Profile arcs = origin ? arcsOf(convoy, targetId) : ArmorArcs.Profile.FLAT;
        float dx = arcs != ArmorArcs.Profile.FLAT ? world.x(attackerId) - world.x(targetId) : 0f;
        float dy = arcs != ArmorArcs.Profile.FLAT ? world.y(attackerId) - world.y(targetId) : 0f;
        ArmorArcs.Arc hullArc = arcs == ArmorArcs.Profile.FLAT ? ArmorArcs.Arc.SIDE
                : ArmorArcs.classify(convoy.body(targetId).facingDegrees, dx, dy);
        float armorDamage;
        float hpAfter;
        // What the round was read against, for the hit log only.
        float hitRating = rating * arcs.rating(hullArc);
        String hitPart = null;
        if (locations != null) {
            GroundBody body = convoy.body(targetId);
            float facing = body != null ? body.facingDegrees : 0f;
            ArmorArcs.Arc turretArc = section == null || arcs == ArmorArcs.Profile.FLAT ? ArmorArcs.Arc.SIDE
                    : ArmorArcs.classify(turret.facingDeg, dx, dy);
            boolean onTurret = section != null && section.struck(rng.nextFloat());
            // A hit with no origin meets the front plate and never the drive: the hull's one pool, as it was.
            boolean onDrive = !onTurret && origin && rng.nextFloat() < locations.driveHitShare(hullArc);
            HullLocations.Plate plate = origin ? locations.plateFor(hullArc, facing, dx, dy) : HullLocations.Plate.FRONT;
            locations.resolve(onTurret, onDrive, plate, postCover,
                    penetration, rating * arcs.rating(turretArc), arcs.plate(turretArc),
                    rating * arcs.rating(hullArc), arcs.plate(hullArc), hpBefore, durability, locatedHit);
            if (onTurret) hitRating = rating * arcs.rating(turretArc);
            hitPart = onTurret ? "TURRET" : onDrive ? "DRIVE" : "HULL:" + plate;
            armorDamage = locatedHit.armorDamage();
            hpAfter = locatedHit.hullDestroyed() ? 0f : Math.max(0f, hpBefore - locatedHit.structureDamage());
            if (locatedHit.driveDestroyed() && body != null) body.crippled = true;
        } else {
            DurabilityModel.resolvePlatedInto(postCover, penetration,
                    armorBefore, rating * arcs.rating(hullArc), arcs.plate(hullArc), hpBefore, durability);
            armorDamage = durability.armorDamage();
            hpAfter = Math.max(0f, hpBefore - durability.structureDamage());
        }
        if (turret != null && origin) {
            // The crew notes where the hit came from: two machines apart is a crossfire (Crossfire).
            turret.crossfire.record(attackerId, (float) Math.toDegrees(Math.atan2(
                    world.y(attackerId) - world.y(targetId), world.x(attackerId) - world.x(targetId))));
        }
        if (armorDamage > 0f) {
            world.setArmor(targetId, Math.max(0f, armorBefore - armorDamage));
        }
        world.setHp(targetId, hpAfter);

        float applied = armorDamage + (hpBefore - hpAfter);
        CombatTelemetryService telemetry = roster.telemetry();
        HitLog hitLog = telemetry.hitLog();
        if (hitLog.recording()) {
            hitLog.record(new HitLog.Hit(hitLog.tick(), targetId, HitLog.label(roster, targetId),
                    attackerId, HitLog.label(roster, attackerId), damage, postCover, penetration, hitRating,
                    arcs == ArmorArcs.Profile.FLAT ? null : hullArc.name(), hitPart, 0f, armorDamage,
                    hpBefore - hpAfter, hpAfter, world.armor(targetId), hpAfter <= 0f));
        }
        if (attackerId != targetId && carrier.faction(targetId) == Faction.DEFENDER) {
            telemetry.recordToLedger(attackerId, applied, hpAfter <= 0f, false);
        }
        if (telemetry.isRecorded(attackerId) && attackerId != targetId) {
            boolean friendly = roster.identity().faction(attackerId)
                    .friendlyTo(carrier.faction(targetId));
            telemetry.recordDamageDealt(attackerId, applied, friendly);
            if (hpAfter <= 0f && !friendly) telemetry.recordKill(attackerId);
        }
        if (hpAfter <= 0f) carrier.destroy(targetId);
    }

    /** A ground hull's arcs, or {@link ArmorArcs.Profile#FLAT} for any other carried body. */
    private static ArmorArcs.Profile arcsOf(ConvoyService convoy, long id) {
        if (convoy == null) return ArmorArcs.Profile.FLAT;
        VehicleType type = convoy.vehicleType(id);
        return type != null && convoy.body(id) != null ? type.armorArcs() : ArmorArcs.Profile.FLAT;
    }

    /**
     * Whether the hit has an origin to read an arc from: a shooter, other than the body itself, still standing
     * where it can be asked. Every hit with one (an aimed round, an arcing shell, a missile, a blast) is read from
     * where its shooter stands now; one without (unattributed or scripted damage, a shooter already gone) meets
     * the hull's nominal plate, on no arc.
     */
    private static boolean hasOrigin(World world, long targetId, long attackerId) {
        return attackerId != CombatTelemetryService.NO_ATTACKER && attackerId != targetId && world.isAlive(attackerId);
    }

}
