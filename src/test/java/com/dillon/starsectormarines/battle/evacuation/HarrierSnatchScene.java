package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierCarry;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harriers picking colonists off the ground alive (`colony-breach-nouns.md`, Harrier, Snatching the living): a quiet
 * maw and a handful of colonists out in the open, and a pack released over them.
 * <ul>
 *   <li>{@code snatches}: the pack sends carriers, never more than a third of it at once, to snatch the colonists off
 *       their feet and fly them, alive, to the bore, where they are taken.</li>
 *   <li>{@code shot-down}: the first carrier is shot down on its way home. The colonist it held drops where it fell,
 *       alive, legs hurt, and is not snatched again while it is down.</li>
 * </ul>
 * The pack is released without the flier law's anti-air, as a scene's control may: what is measured is the snatch.
 */
public final class HarrierSnatchScene implements BehaviorScene {

    static final int W = 120, H = 80;
    static final int BORE_X = 30, BORE_Y = 40;
    static final int FOLK_X = 66, FOLK_Y = 40;
    static final int PACK = 6, COLONISTS = 5;
    static final int TICKS = 6000;

    @Override public String id() { return "harrier-snatch"; }

    @Override public String label() {
        return "Harrier snatch: a pack snatches colonists out in the open off their feet and flies them alive into the"
                + " bore; one whose carrier is shot down drops alive, hurt";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("snatches", false, frames), run("shot-down", true, FrameSink.NONE));
    }

    private SceneReport run(String loop, boolean shoot, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261025L).missionCompletion(false);
        try (BattleSimulation sim = builder.build().sim()) {
            HarrierPatrolScene.quietMaw(sim, BORE_X, BORE_Y);
            List<Long> folk = new ArrayList<>();
            for (int i = 0; i < COLONISTS; i++) {
                folk.add(sim.spawn(new EntitySpec("Colonist " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                        FOLK_X + (i % 3) * 3, FOLK_Y - 3 + (i / 3) * 5).role(UnitRole.FLEE)));
            }
            SwarmHarvest harvest = sim.swarmHarvest();
            HarrierPack pack = null;
            long dropped = 0L, shotCarrier = 0L;
            boolean droppedAlive = false, resnatched = false;
            int mostOut = 0, overLimit = 0, dropTick = -1, ticks = TICKS;
            int recovery = Math.round(SwarmHarvest.RECOVERY_SECONDS / BattleSimulation.TICK_DT);
            for (int tick = 1; tick <= TICKS; tick++) {
                if (tick == 30) pack = sim.swarmFliers().release(sim, HarrierLook.DEFAULT, FOLK_X, FOLK_Y - 8, PACK, 0L);
                sim.advance(BattleSimulation.TICK_DT);
                if (pack != null) {
                    int out = pack.carriersOut();
                    mostOut = Math.max(mostOut, out);
                    if (out > HarrierCarry.carriersFor(pack.members.size())) overLimit++;
                    if (shoot && shotCarrier == 0L) {
                        for (HarrierFlight m : pack.members) {
                            long load = harvest.loadOf(m.id);
                            // Shot down well on its way home, clear of the bore.
                            if (load == 0L || m.body.distanceTo(BORE_X + 0.5f, BORE_Y + 0.5f) > 24f) continue;
                            shotCarrier = m.id;
                            dropped = load;
                            sim.applyDamage(m.id, 1e6f, 1e6f);
                            break;
                        }
                    } else if (shoot && dropped != 0L && !droppedAlive && !harvest.isCarried(dropped)) {
                        droppedAlive = sim.world().isAlive(dropped);
                        dropTick = tick;
                    }
                    if (droppedAlive && tick - dropTick < recovery && harvest.isCarried(dropped)) resnatched = true;
                }
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "harrier snatch / " + loop + " / tick " + tick + " / "
                            + harvest.snatchedByFliers() + " snatched, " + harvest.taken() + " taken");
                }
                if (shoot ? droppedAlive && tick > 400 && !harvest.holdsStill(dropped)
                        : harvest.taken() >= COLONISTS && pack.carriersOut() == 0) {
                    ticks = tick;
                    break;
                }
            }
            List<Verdict> verdicts = new ArrayList<>();
            if (!shoot) {
                verdicts.add(Verdict.of("it-snatches-colonists-alive-into-the-bore",
                        harvest.snatchedByFliers() >= 3 && harvest.taken() >= 3,
                        harvest.snatchedByFliers() + " snatched, " + harvest.taken() + " taken alive at the mouth, by tick "
                                + ticks));
                verdicts.add(Verdict.of("never-more-than-a-third-away", overLimit == 0 && mostOut >= 1,
                        "at most " + mostOut + " out of " + PACK + ", over the limit on " + overLimit + " ticks"));
            } else {
                verdicts.add(Verdict.of("shot-down-it-drops-them-alive", droppedAlive && harvest.freed() >= 1,
                        "carrier " + shotCarrier + " shot down; its colonist " + (droppedAlive ? "alive" : "lost")
                                + ", " + harvest.freed() + " freed"));
                verdicts.add(Verdict.of("not-snatched-again-while-down", !resnatched,
                        resnatched ? "snatched again while still down" : "left while down"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("snatched", harvest.snatchedByFliers());
            metrics.put("taken", harvest.taken());
            metrics.put("freed", harvest.freed());
            metrics.put("mostOut", mostOut);
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        }
    }
}
