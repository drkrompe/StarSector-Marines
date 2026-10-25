package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A quiet maw's brood spire, a rifle line with flak well out from it, and one marine alone far behind the line
 * (`colony-breach-nouns.md`, Brood spire, Raids). The direct way from the spire to him crosses the flak.
 * <ul>
 *   <li>{@code raid}: its raid due, the spire sends {@link BroodSpires#RAID} past the line to him, by a way that keeps
 *       out of the flak's reach; they dive on him, fly home and land on the hooks.</li>
 *   <li>{@code covered}: a second flak stands by him, so nothing soft is left in raid range. The spire sends no raid,
 *       and patrols instead.</li>
 * </ul>
 */
public final class HarrierRaidScene implements BehaviorScene {

    static final int W = 220, H = 140;
    static final int BORE_X = 40, BORE_Y = 70;
    static final int LINE_X = 115, LINE_Y = 70;
    static final int REAR_X = 165, REAR_Y = 70;
    static final float FLAK_REACH = 26f;
    static final int TICKS = 7200;

    @Override public String id() { return "harrier-raid"; }

    @Override public String label() {
        return "Harrier raid: a quiet spire sends four round the flak to a lone marine behind the line, dives on him and"
                + " comes home to land; with every soft target covered it patrols instead";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("raid", false, frames), run("covered", true, FrameSink.NONE));
    }

    private SceneReport run(String loop, boolean covered, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261025L).missionCompletion(false);
        builder.squad("line").faction(Faction.MARINE).size(6).at(LINE_X, LINE_Y).primary("weapon.field-rifle")
                .stationary().done();
        builder.squad("rear").faction(Faction.MARINE).size(1).at(REAR_X, REAR_Y).primary("weapon.field-rifle")
                .stationary().done();
        SceneWorld scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            HarrierPatrolScene.quietMaw(sim, BORE_X, BORE_Y);
            sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, LINE_X + 2, LINE_Y));
            if (covered) sim.spawn(MapTurret.create("flak-rear", Faction.MARINE, BroodSpireScene.FLAK, REAR_X + 2, REAR_Y));
            long rear = firstOf(sim, scene.squadId("rear"));
            BroodSpires spires = sim.broodSpires();
            BroodSpires.Spire spire = spires.spires().get(0);
            // Its raid due now, its patrol not for a while yet.
            spire.lastRaidTick = -BroodSpires.raidGapTicks(spire.entityId, 0);
            HarrierPack raid = null;
            float nearestFlak = Float.MAX_VALUE;
            int launchTick = -1, homeTick = -1, ticks = TICKS;
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                HarrierPack now = spire.patrol();
                if (now != null && raid == null) {
                    raid = now;
                    launchTick = tick;
                }
                if (now != null && now.patrol().raid() && !now.patrol().striking() && now.unhatched() == 0) {
                    for (HarrierFlight m : now.members) {
                        nearestFlak = Math.min(nearestFlak, (float) Math.hypot(m.body.x - (LINE_X + 2.5f), m.body.y - (LINE_Y + 0.5f)));
                    }
                }
                if (raid != null && now == null && homeTick < 0) homeTick = tick;
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "harrier raid / " + loop + " / tick " + tick + " / "
                            + spire.raids() + " raids, " + spire.patrols() + " patrols");
                }
                if (homeTick > 0 || covered && raid != null) {
                    ticks = tick;
                    break;
                }
            }
            int strikesOnRear = 0, landed = 0;
            for (SwarmFliers.Event e : sim.swarmFliers().events()) {
                if (e.kind() == SwarmFliers.Event.Kind.STRIKE && e.target() == rear) strikesOnRear++;
                if (e.kind() == SwarmFliers.Event.Kind.LANDED) landed++;
            }
            List<Verdict> verdicts = new ArrayList<>();
            if (!covered) {
                verdicts.add(Verdict.of("a-quiet-spire-raids-past-the-line",
                        spire.raids() == 1 && raid != null && raid.patrol().raid(),
                        spire.raids() + " raids, sortie at tick " + launchTick));
                verdicts.add(Verdict.of("its-way-keeps-out-of-the-flak", nearestFlak > FLAK_REACH,
                        "came within " + nearestFlak + " cells of the flak, which reaches " + FLAK_REACH));
                verdicts.add(Verdict.of("it-dives-on-the-lone-marine", strikesOnRear > 0, strikesOnRear + " bites on him"));
                verdicts.add(Verdict.of("it-comes-home-and-lands", homeTick > 0 && landed > 0,
                        landed + " landed, home at tick " + homeTick));
            } else {
                verdicts.add(Verdict.of("no-raid-on-a-covered-target-it-patrols", spire.raids() == 0
                                && raid != null && !raid.patrol().raid(),
                        spire.raids() + " raids, " + spire.patrols() + " patrols by tick " + ticks));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("launchTick", launchTick);
            metrics.put("homeTick", homeTick);
            metrics.put("nearestFlak", nearestFlak);
            metrics.put("strikesOnRear", strikesOnRear);
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        }
    }

    private static long firstOf(BattleSimulation sim, int squadId) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long u = sim.liveUnitAt(i);
            if (sim.squad().hasSquad(u) && sim.squad().squadId(u) == squadId) return u;
        }
        throw new IllegalStateException("no unit in squad " + squadId);
    }
}
