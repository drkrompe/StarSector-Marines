package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierCarry;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Harrier pack fetching the dead home to its nest (`colony-breach-nouns.md`, Harrier, Carrying feed): a quiet
 * maw, a few of the swarm's own dead lying out on the field, and a marine fire team the pack is released over.
 * <ul>
 *   <li>{@code carries}: the pack kills the team, and with nothing left to hunt sends carriers, never more than a
 *       third of it at once, to fly the marines it killed and the Rav dead home into the bore's mouth. A carrier
 *       never bites, and each comes back to the pack.</li>
 * </ul>
 * The pack is released without the flier law's anti-air, as a scene's control may: what is measured is the carrying.
 */
public final class HarrierCarryScene implements BehaviorScene {

    static final int W = 120, H = 80;
    static final int BORE_X = 30, BORE_Y = 40;
    static final int TEAM_X = 62, TEAM_Y = 40;
    static final int PACK = 6, RAV_DEAD = 4;
    static final int TICKS = 6000;

    @Override public String id() { return "harrier-carry"; }

    @Override public String label() {
        return "Harrier carrying: a pack with nothing to hunt sends a third of itself to fly the dead home into the"
                + " bore's mouth; carriers never bite and come back";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("carries", frames));
    }

    private SceneReport run(String loop, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261025L).missionCompletion(false);
        builder.squad("team").faction(Faction.MARINE).size(3).at(TEAM_X, TEAM_Y).primary("weapon.field-rifle")
                .stationary().done();
        try (BattleSimulation sim = builder.build().sim()) {
            HarrierPatrolScene.quietMaw(sim, BORE_X, BORE_Y);
            List<Long> ravDead = new ArrayList<>();
            for (int i = 0; i < RAV_DEAD; i++) {
                ravDead.add(SwarmStrains.spawn(sim, "Fallen " + i, UnitType.SWARM_RUNNER, TEAM_X + 2 + i * 2, TEAM_Y + 9));
            }
            SwarmHarvest harvest = sim.swarmHarvest();
            HarrierPack pack = null;
            int mostOut = 0, overLimit = 0, carrierBites = 0, eventsSeen = 0, ticks = TICKS;
            Set<Long> carrying = new HashSet<>();
            for (int tick = 1; tick <= TICKS; tick++) {
                if (tick == 2) for (long id : ravDead) sim.applyDamage(id, 1e6f, 1e6f);
                if (tick == 30) pack = sim.swarmFliers().release(sim, HarrierLook.DEFAULT, TEAM_X, TEAM_Y - 6, PACK, 0L);
                sim.advance(BattleSimulation.TICK_DT);
                List<SwarmFliers.Event> events = sim.swarmFliers().events();
                for (; eventsSeen < events.size(); eventsSeen++) {
                    SwarmFliers.Event e = events.get(eventsSeen);
                    if (e.kind() == SwarmFliers.Event.Kind.STRIKE && carrying.contains(e.harrier())) carrierBites++;
                }
                carrying.clear();
                if (pack != null) {
                    for (HarrierFlight m : pack.members) if (m.carry != null) carrying.add(m.id);
                    int out = pack.carriersOut();
                    mostOut = Math.max(mostOut, out);
                    if (out > HarrierCarry.carriersFor(pack.members.size())) overLimit++;
                }
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "harrier carry / tick " + tick + " / " + harvest.hauledByFliers()
                            + " fetched / " + carrying.size() + " carrying");
                }
                if (pack != null && tick > 300 && carrying.isEmpty() && marines(sim) == 0 && harvest.carrying() == 0
                        && harvest.nearestLoose(sim, W / 2f, H / 2f, 1000f) == 0L) {
                    ticks = tick;
                    break;
                }
            }
            int snatched = 0, fed = 0;
            for (SwarmFliers.Event e : sim.swarmFliers().events()) {
                if (e.kind() == SwarmFliers.Event.Kind.SNATCHED) snatched++;
                if (e.kind() == SwarmFliers.Event.Kind.FED) fed++;
            }
            int left = pack == null ? 0 : pack.members.size();
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("it-flies-the-dead-home-into-the-bore",
                    harvest.hauledByFliers() >= 3 && harvest.delivered() >= 1 && harvest.recycled() >= 1,
                    harvest.hauledByFliers() + " fetched (" + harvest.delivered() + " marines, " + harvest.recycled()
                            + " Rav), " + snatched + " snatched, " + fed + " fed, by tick " + ticks));
            verdicts.add(Verdict.of("never-more-than-a-third-away", overLimit == 0 && mostOut >= 1,
                    "at most " + mostOut + " out of " + PACK + ", over the limit on " + overLimit + " ticks"));
            verdicts.add(Verdict.of("a-carrier-never-bites", carrierBites == 0, carrierBites + " bites by carriers"));
            verdicts.add(Verdict.of("its-carriers-come-back", left > 0 && pack.carriersOut() == 0,
                    left + " left in the pack, " + (pack == null ? 0 : pack.carriersOut()) + " still out"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("fetched", harvest.hauledByFliers());
            metrics.put("marinesFed", harvest.delivered());
            metrics.put("ravFed", harvest.recycled());
            metrics.put("mostOut", mostOut);
            metrics.put("ticks", ticks);
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        }
    }

    private static int marines(BattleSimulation sim) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.MARINE) n++;
        return n;
    }
}
