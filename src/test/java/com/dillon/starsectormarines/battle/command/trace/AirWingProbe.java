package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.airsupport.AirMission;
import com.dillon.starsectormarines.battle.airsupport.StandingPatrol;
import com.dillon.starsectormarines.battle.airsupport.WingAirbase;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.CommsKit;
import com.dillon.starsectormarines.battle.airsupport.ForwardObservers;
import com.dillon.starsectormarines.battle.airsupport.RovingObservers;
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
        int sent = 0, moves = 0, roving = 0;
        for (RovingObservers teams : pool.roving(Faction.ALLY)) {
            sent += teams.sent();
            moves += teams.moves();
            roving += teams.observers().size();
        }
        json.put("rovingSent", sent).put("rovingMoves", moves).put("rovingAlive", roving);
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
        // The airbases it flies from, where it has them: what rolled off them, what stands in each base's bays now, the
        // crews standing, the rebuilds begun, the standing patrol.
        if (pool.based(Faction.ALLY)) {
            JSONArray bases = new JSONArray();
            for (WingAirbase base : pool.bases(Faction.ALLY)) {
                JSONObject berths = new JSONObject();
                int wrecks = 0;
                for (AirfieldService.Berth berth : base.berths()) {
                    String state = berth.state.name().toLowerCase(Locale.ROOT);
                    berths.put(state, berths.optInt(state) + 1);
                    if (berth.wreckOnPad) wrecks++;
                }
                int[] b = base.bounds();
                int crew = 0;
                for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                    long u = sim.liveUnitAt(i);
                    if (sim.identity().faction(u) != Faction.ALLY || !sim.ambientTasks().assigned().contains(u)) continue;
                    float x = sim.world().x(u), y = sim.world().y(u);
                    if (x >= b[0] && x <= b[2] + 1 && y >= b[1] && y <= b[3] + 1) crew++;
                }
                bases.put(new JSONObject().put("x0", b[0]).put("y0", b[1]).put("bays", berths)
                        .put("wrecksOnBays", wrecks).put("crewStanding", crew));
            }
            int patrols = 0;
            for (StandingPatrol patrol : pool.patrols(Faction.ALLY)) patrols += patrol.launched();
            json.put("airbases", new JSONObject().put("basedLaunches", pool.basedLaunches(Faction.ALLY))
                    .put("rebuildsBegun", sim.getAirfieldService().rebuildsBegun())
                    .put("patrolsLaunched", patrols).put("bases", bases));
        }
        json.put("airToAir", new JSONObject().put("roundsFired", sim.airToAir().roundsFired())
                .put("roundsHit", sim.airToAir().roundsHit()).put("flierHits", sim.airToAir().flierHits()));
        System.out.println("[air-wing] " + run + " " + json);
    }
}
