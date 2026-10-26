package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A player's carrier bay keeps its airframe on station: one that comes home
 * flies again after the refit, one that is shot down is replaced after the
 * longer respawn, and the bay outlasts any stock of sorties.
 *
 * <p>Two loops over the same ground. {@code refit}: the airframe is left alone
 * and every sortie comes home. {@code loss}: the scene shoots down the first
 * sortie fifteen seconds after it launches, a scripted kill so the verdict is
 * about the replacement and not about who won a fight. The defenders are bare,
 * unarmed bodies held at full health, so the concentration the bay is sent at
 * stays a target for the whole run and a gap between sorties is the bay's
 * clock, not a search for something to attack.
 */
public final class FighterBayRenewalScene implements BehaviorScene {

    private static final int TICKS = 300 * 30;
    private static final int KILL_AFTER_TICKS = 15 * 30;
    /** Ticks the dispatcher may add to a bay's clock: one tick of ordering and one retry hold-off. */
    private static final int DISPATCH_SLACK_TICKS = 5 * 30;

    @Override public String id() { return "fighter-bay-renewal"; }
    @Override public String label() { return "Carrier bay: refit after a sortie, replacement after a loss, no end"; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("refit", "loss")) reports.add(playOne(loop, frames));
        return reports;
    }

    /** One sortie the bay flew: when it launched and how it ended. */
    private static final class Sortie {
        final long id;
        final ShuttleMission mission;
        final int launched;
        int lost = -1;
        int home = -1;
        Sortie(long id, ShuttleMission mission, int launched) {
            this.id = id; this.mission = mission; this.launched = launched;
        }
        boolean ended() { return lost >= 0 || home >= 0; }
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean loss = loop.equals("loss");
        var builder = SceneBuilder.openGround(80, 50).missionCompletion(false);
        builder.unit("company", Faction.MARINE, UnitType.MARINE, 6, 25);
        List<String> defenders = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            String name = "defender-" + i;
            defenders.add(name);
            builder.unit(name, Faction.DEFENDER, UnitType.MILITIA, 60 + i % 3, 24 + i / 3);
        }
        var world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            sim.random().setSeed(20261026L);
            sim.setFlybyRoster(new FlybyRoster(List.of(FighterWing.bay(FighterProfile.BROADSWORD,
                    Faction.MARINE, 2f, PlayerFleetWings.REFIT_SEC, PlayerFleetWings.REPLACEMENT_SEC))));
            long[] held = defenders.stream().mapToLong(world::unit).toArray();
            // Deep enough that no gun run kills one in a tick, topped up every tick after.
            for (long d : held) sim.world().setMaxHp(d, 1_000_000f);
            List<Sortie> sorties = new ArrayList<>();
            var run = SceneRun.of(world).observe((s, tick) -> {
                var w = s.world();
                for (long d : held) if (w.isAlive(d)) w.setHp(d, w.maxHp(d));
                for (long id : s.getAirEntityIds()) {
                    ShuttleMission m = w.mission(id);
                    if (m == null || !m.strikeSortie || m.homeBerth != null) continue;
                    if (sorties.stream().noneMatch(o -> o.mission == m)) sorties.add(new Sortie(id, m, tick));
                }
                for (Sortie o : sorties) {
                    if (o.ended()) continue;
                    if (loss && o == sorties.get(0) && !o.mission.destroyed
                            && tick - o.launched >= KILL_AFTER_TICKS) {
                        s.applyAimedDamage(o.id, 0L, 10000f, 100f);
                    }
                    if (o.mission.destroyed) o.lost = tick;
                    else if (!w.isAlive(o.id) || w.mission(o.id) != o.mission) o.home = tick;
                }
                if (tick % 30 == 0) frames.frame(loop, s, tick, loop + " / sorties " + sorties.size());
                frames.advanced(loop, s, tick);
            });
            run.run(TICKS);

            List<Sortie> flown = sorties;
            int refitTicks = Math.round(PlayerFleetWings.REFIT_SEC * 30f);
            int replacementTicks = Math.round(PlayerFleetWings.REPLACEMENT_SEC * 30f);
            List<Verdict> verdicts = new ArrayList<>();
            StringBuilder gaps = new StringBuilder();
            boolean gapsHold = flown.size() >= 2;
            for (int i = 1; i < flown.size(); i++) {
                Sortie before = flown.get(i - 1);
                int ended = before.lost >= 0 ? before.lost : before.home;
                int clock = before.lost >= 0 ? replacementTicks : refitTicks;
                int gap = ended < 0 ? -1 : flown.get(i).launched - ended;
                gaps.append(before.lost >= 0 ? "lost" : "home").append("->").append(gap).append("t ");
                gapsHold &= ended >= 0 && gap >= clock - 1 && gap <= clock + DISPATCH_SLACK_TICKS;
            }
            if (loss) {
                Sortie first = flown.isEmpty() ? null : flown.get(0);
                verdicts.add(Verdict.of("first-sortie-shot-down", first != null && first.lost >= 0,
                        "first sortie lost at tick " + (first == null ? "-" : first.lost)));
                verdicts.add(Verdict.of("lost-airframe-replaced", flown.size() >= 2 && first.lost >= 0,
                        "sorties flown=" + flown.size()));
                verdicts.add(Verdict.of("replacement-waits-the-respawn", gapsHold, gaps.toString().trim()));
            } else {
                verdicts.add(Verdict.of("every-sortie-came-home",
                        flown.stream().noneMatch(o -> o.lost >= 0), "losses="
                                + flown.stream().filter(o -> o.lost >= 0).count()));
                verdicts.add(Verdict.of("bay-outlasts-a-two-sortie-stock", flown.size() > 2,
                        "sorties flown=" + flown.size()));
                verdicts.add(Verdict.of("next-sortie-waits-the-refit", gapsHold, gaps.toString().trim()));
            }
            return new SceneReport(id(), loop, run.tick(), verdicts,
                    Map.of("sorties", flown.size(),
                            "losses", flown.stream().filter(o -> o.lost >= 0).count()));
        }
    }
}
