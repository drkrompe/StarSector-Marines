package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.airsupport.AirMission;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.CommsKit;
import com.dillon.starsectormarines.battle.airsupport.ForwardObservers;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prints one {@code [air-wing] <run> <json>} line per battle with an allied air wing (`air-nouns.md`, Forward observer):
 * the observers posted and still carrying their radios, and what the wing was called for, flew, brought home and lost
 * per errand. A battle with no allied pool prints nothing.
 */
final class AirWingProbe {

    private AirWingProbe() {}

    static void print(String run, BattleSimulation sim) throws Exception {
        AirPoolService pool = sim.airPool();
        if (pool == null || !pool.hasPool(Faction.ALLY)) return;
        int posted = 0, alive = 0;
        for (ForwardObservers posts : pool.observers(Faction.ALLY)) {
            posted += posts.posted();
            for (int i = 0; i < posts.posts(); i++) {
                long observer = posts.observer(i);
                if (observer != 0L && sim.world().isAlive(observer) && CommsKit.carried(sim.world(), observer)) alive++;
            }
        }
        JSONObject json = new JSONObject().put("observersPosted", posted).put("observersAlive", alive)
                .put("refusedTooDefended", pool.director(Faction.ALLY).refusals());
        for (AirMission job : AirMission.values()) {
            AirPoolService.MissionTally t = pool.tally(Faction.ALLY, job);
            json.put(job.name().toLowerCase(Locale.ROOT), new JSONObject().put("called", t.called()).put("launched", t.launched())
                    .put("returned", t.returned()).put("aborted", t.aborted()).put("lost", t.lost()));
        }
        JSONArray losses = new JSONArray();
        for (AirPoolService.Event e : pool.events()) {
            if (e.side() != Faction.ALLY || e.type() != AirPoolService.EventType.LOST) continue;
            float nearest = Float.MAX_VALUE;
            int known = 0;
            for (SwarmBore bore : sim.swarmBores().bores()) {
                List<Long> guns = new ArrayList<>(bore.colonies());
                guns.addAll(bore.outposts());
                for (long gun : guns) {
                    float d = (float) Math.hypot(sim.world().x(gun) - e.x(), sim.world().y(gun) - e.y());
                    nearest = Math.min(nearest, d);
                    if (d <= 30f && pool.director(Faction.ALLY).knows(gun)) known++;
                }
            }
            losses.put(new JSONObject().put("tick", e.tick()).put("kind", e.kind().name())
                    .put("x", Math.round(e.x())).put("y", Math.round(e.y()))
                    .put("nearestColony", nearest == Float.MAX_VALUE ? -1 : Math.round(nearest))
                    .put("knownColoniesWithin30", known));
        }
        json.put("losses", losses);
        System.out.println("[air-wing] " + run + " " + json);
    }
}
