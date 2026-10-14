package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.List;

/**
 * Playback-side recovered Hound: installs Mech Support configured with the one recovered chassis, the way the campaign
 * resolves it for an owned mech bay, requests it at the first landing pad it may target, and tallies what it did.
 */
final class HoundSupportTally {
    private int requestedAt = -1;
    private int landedAt = -1;
    private int lostAt = -1;
    private long hound;
    private int kills;
    private float damage;

    static HoundSupportTally install(BattleSimulation sim) {
        sim.getCommandPowerService().register(MechSupport.configured(List.of(WaylineHound.deployment())));
        return new HoundSupportTally();
    }

    /** Requests the sortie once, on the first tick the power may be used on a pad. */
    void request(BattleSimulation sim) {
        if (requestedAt >= 0) return;
        CommandPowerService service = sim.getCommandPowerService();
        CommandPower support = service.getPower(MechSupport.ID);
        if (support == null || !service.canActivate(support)) return;
        for (var pad : sim.getMarineLandingPads()) {
            if (!support.canTarget(pad.centerX, pad.centerY, sim)) continue;
            service.requestActivation(MechSupport.ID, pad.centerX, pad.centerY);
            requestedAt = sim.getSimTickIndex() + 1;
            return;
        }
    }

    void observe(BattleSimulation sim) {
        if (hound == 0L) {
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                if (sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id) == UnitType.HEAVY_MECH) {
                    hound = id;
                    landedAt = sim.getSimTickIndex();
                    break;
                }
            }
            return;
        }
        if (lostAt >= 0) return;
        if (sim.world().isAlive(hound)) {
            kills = sim.telemetry().kills(hound);
            damage = sim.telemetry().damageDealt(hound);
        } else {
            lostAt = sim.getSimTickIndex();
        }
    }

    String describe() {
        return "hound requestedAt=" + requestedAt + " landedAt=" + landedAt + " lostAt=" + lostAt
                + " kills=" + kills + " damage=" + Math.round(damage);
    }
}
