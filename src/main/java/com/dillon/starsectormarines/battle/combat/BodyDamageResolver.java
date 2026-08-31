package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

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
 * <p>Serial only: it holds one reusable {@link DurabilityModel.Resolution},
 * which is the shape both resolvers it replaces already had.
 */
public final class BodyDamageResolver {

    private final UnitRosterService roster;
    private final DurabilityModel.Resolution durability = new DurabilityModel.Resolution();

    public BodyDamageResolver(UnitRosterService roster) {
        this.roster = roster;
    }

    public void resolve(BodyCarrier carrier, long targetId, long attackerId,
                        float damage, float penetration) {
        if (!carrier.isTargetable(targetId)) return;
        World world = roster.world();
        float hpBefore = world.hp(targetId);
        float armorBefore = world.armor(targetId);
        DurabilityModel.resolveInto(damage * world.damageTakenMult(targetId), penetration,
                armorBefore, world.armorRating(targetId), hpBefore, durability);
        if (durability.armorDamage() > 0f) {
            world.setArmor(targetId, Math.max(0f, armorBefore - durability.armorDamage()));
        }
        float hpAfter = Math.max(0f, hpBefore - durability.structureDamage());
        world.setHp(targetId, hpAfter);

        float applied = durability.armorDamage() + (hpBefore - hpAfter);
        CombatTelemetryService telemetry = roster.telemetry();
        if (telemetry.isRecorded(attackerId) && attackerId != targetId) {
            boolean friendly = roster.identity().faction(attackerId) == carrier.faction(targetId);
            telemetry.recordDamageDealt(attackerId, applied, friendly);
            if (hpAfter <= 0f && !friendly) telemetry.recordKill(attackerId);
        }
        if (hpAfter <= 0f) carrier.destroy(targetId);
    }
}
