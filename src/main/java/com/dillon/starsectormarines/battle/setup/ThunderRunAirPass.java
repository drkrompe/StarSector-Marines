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

/** Authored flight through the ridge batteries' coverage; missiles alone decide whether Kestrel survives. */
public final class ThunderRunAirPass {
    private final StoryBeatLog log;
    private final long aircraft;
    private final ShuttleMission sortie;
    private final float initialHp;

    public ThunderRunAirPass(BattleSimulation sim, StoryBeatLog log) {
        this.log = log;
        aircraft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, 190f, 75f,
                165f, 156f, 165f, 156f);
        sortie = sim.world().mission(aircraft);
        sortie.orbitSecondsLeft = 180f;
        sortie.gunshipIngress = new GunshipIngress(List.of(new Point(165, 90), new Point(160, 52),
                new Point(160, 10), new Point(208, 12), new Point(230, 42), new Point(220, 70)));
        initialHp = sim.world().hp(aircraft);
        sim.getEntityWorld().setObject(aircraft, sim.getBattleComponents().IDENTITY,
                BattleComponents.IDENTITY_NAME, WaylineGunship.CALLSIGN + " / Wayline gunship");
        beat(sim, "inbound");
    }

    public long aircraft() { return aircraft; }
    public boolean lost() { return sortie.destroyed; }

    public void tick(BattleSimulation sim) {
        if (sortie.destroyed) { beat(sim, "lost"); return; }
        if (sortie.state == ShuttleState.GONE) { beat(sim, "returned"); return; }
        if (sortie.gunshipIngress.waypoint() >= 1) beat(sim, "pass_committed");
        if (sortie.lastMissileLaunchTick >= 0) beat(sim, "missile_warning");
        if (sim.world().hp(aircraft) < initialHp) beat(sim, "hit");
        if (sortie.state == ShuttleState.ORBIT) beat(sim, "on_station");
    }

    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), "supply_route.thunder_run.gunship_" + key,
                StoryBeatLog.Audience.PLAYER);
    }
}
