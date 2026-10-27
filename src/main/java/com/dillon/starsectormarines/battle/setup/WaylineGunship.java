package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import java.util.ArrayList;

/** Wayline directs one named aircraft from public progress and friendly positions, never hidden enemies. */
public final class WaylineGunship {
    public static final String CALLSIGN = "Talon Two";
    /**
     * Station time Talon Two is kept topped up to: it stays over the map until the battle ends rather than heading
     * home (owner, 2026-10-27), and only the battle's end or its loss takes it away.
     */
    static final float STATION_SECONDS = 60f;
    private final boolean freight;
    private final StoryBeatLog log;
    private long aircraft;
    private ShuttleMission sortie;
    /** Whether Talon Two has gone ahead to the Dispatch trench. */
    private boolean ahead;
    private ThunderRunAirPass freightPass;

    public WaylineGunship(boolean freight, StoryBeatLog log) {
        this.freight = freight;
        this.log = log;
    }
    public long aircraft() { return freightPass == null ? aircraft : freightPass.aircraft(); }
    public boolean launched() { return freightPass != null || sortie != null; }
    public boolean lost() { return freightPass != null ? freightPass.lost() : sortie != null && sortie.destroyed; }
    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), "supply_route." + (freight ? "thunder_run" : "junction")
                + ".gunship_" + key, StoryBeatLog.Audience.PLAYER);
    }

    public void tick(BattleSimulation sim) {
        if (sim.isComplete()) return;
        if (freight) {
            if (freightPass == null && sim.getThunderRun().phase() >= 1)
                freightPass = new ThunderRunAirPass(sim, log);
            if (freightPass != null) freightPass.tick(sim);
            return;
        }
        var sites = new ArrayList<>(sim.getCompoundService().getRecords());
        if (sites.size() < 4) return;
        if (sortie == null) {
            boolean ready = companyNear(sim, sites.get(1).captureCellX, sites.get(1).captureCellY, 38f);
            if (!ready) return;
            float x = sites.get(1).captureCellX;
            float y = sites.get(1).captureCellY;
            aircraft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, x, y, -12f, y, -12f, y);
            sortie = sim.world().mission(aircraft);
            sortie.orbitSecondsLeft = STATION_SECONDS;
            sim.getEntityWorld().setObject(aircraft, sim.getBattleComponents().IDENTITY,
                    BattleComponents.IDENTITY_NAME, CALLSIGN + " / Wayline gunship");
            beat(sim, "inbound");
        }
        if (sortie.destroyed) { beat(sim, "lost"); return; }
        if (sortie.state == ShuttleState.GONE) return;
        if (sortie.state == ShuttleState.ORBIT && sim.world().hp(aircraft) >= sim.world().maxHp(aircraft) * .8f)
            beat(sim, "on_station");
        if (sim.world().hp(aircraft) < sim.world().maxHp(aircraft) * .8f) beat(sim, "hit");
        if (sortie.state != ShuttleState.ORBIT) return;
        sortie.orbitSecondsLeft = STATION_SECONDS;
        // Wayline sends it ahead rather than leaving it over ground already taken: once the Weigh Station
        // falls, Talon Two goes on to the trench in front of Dispatch and works that later obstacle while the
        // company takes the Freight Yard. The trench is an authored work on the briefing map, not a hidden enemy.
        if (!ahead && sites.get(1).state == CompoundState.MARINE_HELD) {
            ahead = true;
            int[] trench = JunctionCompound.trenchCentre(JunctionCompound.TRENCHES.size() - 1);
            sortie.orbitCentreX = trench[0] + .5f;
            sortie.orbitCentreY = trench[1] + .5f;
            beat(sim, "working_trench");
        }
        // Once the Freight Yard is the company's, it circles over Junction Dispatch until the battle ends.
        if (ahead && sites.get(2).state == CompoundState.MARINE_HELD) {
            var dispatch = sites.get(3);
            sortie.orbitCentreX = (dispatch.captureCellX >= 0 ? dispatch.captureCellX : dispatch.node.anchorX) + .5f;
            sortie.orbitCentreY = (dispatch.captureCellY >= 0 ? dispatch.captureCellY : dispatch.node.anchorY) + .5f;
        }
    }

    private static boolean companyNear(BattleSimulation sim, float x, float y, float radius) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant
                    || !sim.world().isAlive(id)) continue;
            float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
            if (dx * dx + dy * dy <= radius * radius) return true;
        }
        return false;
    }
}
