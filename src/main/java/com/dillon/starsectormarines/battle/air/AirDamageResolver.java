package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.function.LongConsumer;

/**
 * Applies the shared armor/structure law to an aircraft on its wheels.
 *
 * <p>The air sibling of {@code VehicleDamageResolver}, and the same shape for
 * the same reason: an aircraft is not a grid unit, so the infantry resolver's
 * death cascade — corpse pose, equipment drop, squad-leader promotion, the
 * death mailbox — has nothing to do here. What it does share is the part that
 * matters: the same durability model, so cover, armour and penetration mean on
 * an airframe exactly what they mean on everything else, and the same telemetry
 * seam, so the player is told who burned the aircraft.
 *
 * <p>Every kill converges on {@link AirSystem}'s own shoot-down through the
 * destruction sink. There is one way for an aircraft to die and it lights the
 * cook-off, leaves the wreck and gives the runway back; a second death path
 * here would be a kill that quietly skipped all three.
 */
public final class AirDamageResolver {

    private final UnitRosterService roster;
    private final AirTargetService air;
    private final DurabilityModel.Resolution durability = new DurabilityModel.Resolution();
    private LongConsumer destructionSink;

    public AirDamageResolver(UnitRosterService roster) {
        this.roster = roster;
        this.air = roster.airTargets();
    }

    /** Setup-time cycle break: {@code AirSystem} is constructed after the damage service. */
    public void setDestructionSink(LongConsumer sink) {
        this.destructionSink = sink;
    }

    public void resolve(long targetId, long attackerId, float damage,
                        float penetration, float moraleImpact) {
        if (!air.isTargetable(targetId)) return;
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
            boolean friendly = roster.identity().faction(attackerId) == air.faction(targetId);
            telemetry.recordDamageDealt(attackerId, applied, friendly);
            if (hpAfter <= 0f && !friendly) telemetry.recordKill(attackerId);
        }
        if (hpAfter <= 0f && destructionSink != null) destructionSink.accept(targetId);
    }
}
