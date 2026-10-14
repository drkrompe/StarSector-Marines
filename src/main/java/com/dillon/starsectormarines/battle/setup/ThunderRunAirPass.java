package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.GunshipIngress;
import com.dillon.starsectormarines.battle.air.GunshipIngress.Point;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import java.util.List;

/** Authored flight through the ridge batteries' coverage; missiles alone decide whether Talon Two survives. */
public final class ThunderRunAirPass {
    private final StoryBeatLog log;
    private final long aircraft;
    private final ShuttleMission sortie;
    private final float initialHp;
    private boolean withdrawing;

    public ThunderRunAirPass(BattleSimulation sim, StoryBeatLog log) {
        this.log = log;
        aircraft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, 190f, 75f,
                -12f, 30f, 332f, 100f);
        sortie = sim.world().mission(aircraft);
        sortie.orbitSecondsLeft = 180f;
        // Fly ahead of the eastbound column, across the cross street toward the ridge.
        sortie.gunshipIngress = new GunshipIngress(List.of(new Point(40, 30), new Point(240, 30),
                new Point(260, 65), new Point(230, 100), new Point(200, 100)));
        initialHp = sim.world().hp(aircraft);
        sim.getEntityWorld().setObject(aircraft, sim.getBattleComponents().IDENTITY,
                BattleComponents.IDENTITY_NAME, WaylineGunship.CALLSIGN + " / Wayline gunship");
        beat(sim, "inbound");
    }

    public long aircraft() { return aircraft; }
    public boolean lost() { return sortie.destroyed; }
    public boolean withdrawing() { return withdrawing; }

    public void tick(BattleSimulation sim) {
        if (sortie.destroyed) { beat(sim, "lost"); return; }
        if (sortie.state == ShuttleState.GONE) { beat(sim, "returned"); return; }
        if (!withdrawing && sortie.gunshipIngress.waypoint() >= 1) beat(sim, "pass_committed");
        if (sortie.lastMissileLaunchTick >= 0) {
            beat(sim, "missile_warning");
            if (!withdrawing) {
                withdrawing = true;
                var body = sim.world().kinematics(aircraft);
                // A visible forward break away from the ridge, then leave the corridor.
                // Steering retains velocity and the hull's normal turn/acceleration limits.
                sortie.gunshipIngress = new GunshipIngress(List.of(
                        new Point(body.x + 30f, body.y + 25f),
                        new Point(body.x + 85f, body.y + 48f)));
                sortie.exitX = body.x + 130f;
                sortie.exitY = 156f;
                sortie.gunshipWithdrawAfterIngress = true;
                sortie.state = ShuttleState.INCOMING;
                beat(sim, "evading");
            }
        }
        if (sim.world().hp(aircraft) < initialHp) beat(sim, "hit");
        if (sortie.state == ShuttleState.ORBIT) beat(sim, "on_station");
    }

    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), "supply_route.thunder_run.gunship_" + key,
                StoryBeatLog.Audience.PLAYER);
    }
}
