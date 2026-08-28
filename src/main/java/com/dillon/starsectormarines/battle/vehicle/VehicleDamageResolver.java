package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.function.LongConsumer;

/** Applies the shared armor/structure law to convoy entities. */
public final class VehicleDamageResolver {

    private final UnitRosterService roster;
    private final ConvoyService convoy;
    private final DurabilityModel.Resolution durability = new DurabilityModel.Resolution();
    private LongConsumer destructionSink;

    public VehicleDamageResolver(UnitRosterService roster) {
        this.roster = roster;
        this.convoy = roster.convoy();
    }

    /** Setup-time cycle break: GroundSystem is constructed after DamageService. */
    public void setDestructionSink(LongConsumer sink) {
        this.destructionSink = sink;
    }

    public void resolve(long targetId, long attackerId, float damage,
                        float penetration, float moraleImpact) {
        if (!convoy.isTargetable(targetId)) return;
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
            boolean friendly = roster.identity().faction(attackerId) == convoy.faction(targetId);
            telemetry.recordDamageDealt(attackerId, applied, friendly);
            if (hpAfter <= 0f && !friendly) telemetry.recordKill(attackerId);
        }
        if (hpAfter <= 0f && destructionSink != null) destructionSink.accept(targetId);
    }
}
