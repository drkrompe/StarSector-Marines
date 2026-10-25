package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fresh bloom's satellite bore on new creep, no swarm director, and a marine line with flak out past the nest
 * (`colony-breach-nouns.md`, Brood spire: a fresh bloom grows them by eating).
 * <ul>
 *   <li>{@code fed}: the bore is fed a colonist every few seconds until it reaches maw. Once its creep holds ground for
 *       one, it raises a spire: the root digs out to the spot and thickens over {@link BroodSpires#RISE_SECONDS},
 *       then the spire stands at its end, and with the line's flak on the map it puts its first patrol up.</li>
 *   <li>{@code starved}: the same bore left unfed over the same time stays short of maw and raises nothing.</li>
 *   <li>{@code colony-guns}: as {@code fed}, with a colony's heavy machine gun where the flak was: a gun that tracks
 *       fliers meets the flier law, so the spire flies over a colony with no flak of its own.</li>
 * </ul>
 */
public final class FedSpireScene implements BehaviorScene {

    static final int BORE_X = HarrierPatrolScene.BORE_X, BORE_Y = HarrierPatrolScene.BORE_Y;
    static final int LINE_X = HarrierPatrolScene.FAR_X, LINE_Y = HarrierPatrolScene.FAR_Y;
    /** Colonists fed, and the seconds between them. */
    static final int FEEDS = 9;
    static final float FEED_SECONDS = 3f;
    static final int TICKS = 6300;

    @Override public String id() { return "fed-spire"; }

    @Override public String label() {
        return "Fed spire: a fresh bore fed to maw raises a brood spire, root first, which then flies;"
                + " starved, it raises none";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("fed", true, BroodSpireScene.FLAK, frames),
                run("starved", false, BroodSpireScene.FLAK, FrameSink.NONE),
                run("colony-guns", true, ColonyMilitia.EMPLACEMENT_STRUCTURE, FrameSink.NONE));
    }

    private SceneReport run(String loop, boolean fed, String gun, FrameSink frames) {
        try (BattleSimulation sim = HarrierPatrolScene.builder(LINE_X, LINE_Y).build().sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            sim.creepField();
            sim.spawn(MapTurret.create("gun", Faction.MARINE, gun, LINE_X + 3, LINE_Y));
            BroodSpires spires = sim.broodSpires();
            int feedTicks = Math.round(FEED_SECONDS / BattleSimulation.TICK_DT), feeds = 0;
            int mawTick = -1, riseTick = -1, standTick = -1, flyTick = -1, ticks = TICKS;
            float rootAtRise = -1f;
            for (int tick = 1; tick <= TICKS; tick++) {
                if (fed && feeds < FEEDS && tick % feedTicks == 0) {
                    sim.swarmBores().feed(sim, bore, 1f);
                    feeds++;
                }
                sim.advance(BattleSimulation.TICK_DT);
                if (mawTick < 0 && bore.tier() == BoreTier.MAW) mawTick = tick;
                if (riseTick < 0 && !spires.rising().isEmpty()) {
                    riseTick = tick;
                    rootAtRise = spires.rising().get(0).progress(tick);
                }
                if (standTick < 0 && !spires.spires().isEmpty()) standTick = tick;
                if (flyTick < 0 && !spires.spires().isEmpty()
                        && (spires.spires().get(0).patrol() != null || spires.spires().get(0).packs() > 0)) {
                    flyTick = tick;
                }
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "fed spire / " + loop + " / tick " + tick + " / " + bore.tier()
                            + ", fed " + feeds + (standTick > 0 ? ", spire stands" : riseTick > 0 ? ", root digging" : ""));
                }
                if (flyTick > 0) {
                    ticks = tick;
                    break;
                }
            }
            int lead = Math.round(BroodSpires.RISE_SECONDS / BattleSimulation.TICK_DT);
            List<Verdict> verdicts = new ArrayList<>();
            if (fed) {
                verdicts.add(Verdict.of("a-fed-bore-at-maw-raises-a-spire", mawTick > 0 && standTick > mawTick
                                && spires.spires().size() == 1,
                        "maw at tick " + mawTick + ", spire stood at tick " + standTick));
                verdicts.add(Verdict.of("its-root-comes-first", riseTick > 0 && rootAtRise < 0.1f
                                && standTick - riseTick >= lead,
                        "root began at tick " + riseTick + ", spire stood " + (standTick - riseTick) + " ticks on (lead "
                                + lead + ")"));
                verdicts.add(Verdict.of("then-it-flies", flyTick > standTick, "first sortie at tick " + flyTick
                        + " under the line's " + gun));
            } else {
                verdicts.add(Verdict.of("a-starved-bore-raises-none", spires.spires().isEmpty()
                                && spires.rising().isEmpty() && bore.tier() != BoreTier.MAW,
                        bore.tier() + " by tick " + ticks + ", " + spires.spires().size() + " spires"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("mawTick", mawTick);
            metrics.put("riseTick", riseTick);
            metrics.put("standTick", standTick);
            metrics.put("flyTick", flyTick);
            metrics.put("biomassFed", bore.biomassFed());
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        }
    }
}
