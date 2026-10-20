package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Isolated weapon duty cycles against an explicitly inert, durable range target. */
public final class HoundFluxScene implements BehaviorScene {
    @Override public String id() { return "hound-flux"; }
    @Override public String label() { return "Hound burst flux, AI dissipation hold and reverse drive"; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String mode : List.of("held-fire", "paced-bursts", "ai-fire", "reverse-strafe")) {
            SceneBuilder builder = SceneBuilder.openGround(48, 28).missionCompletion(false);
            var lance = builder.squad("hound").faction(Faction.MARINE).size(1).at(14, 12)
                    .mech(MechVariant.HOUND, MechRole.BALANCED)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, 14, 12));
            boolean moving = mode.equals("reverse-strafe"), ai = mode.equals("ai-fire");
            if (!moving) lance.stationary();
            lance.done();
            builder.unit("range-target", Faction.DEFENDER, UnitType.MARINE, 24, 12);
            var scene = builder.build();
            try (BattleSimulation sim = scene.sim()) {
                sim.random().setSeed(1011L);
                long mech = scene.members("hound")[0];
                long dummy = scene.unit("range-target");
                sim.world().setMaxHp(dummy, 100000f);
                sim.world().setHp(dummy, 100000f);
                var loadout = sim.world().mechLoadout(mech);
                sim.getRoster().entityWorld().setFloat(mech, sim.getRoster().components().MECH_LOCOMOTION,
                        BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, -90f);
                loadout.torsoFacingDegrees = -90f;
                if (!ai && !sim.directControl().enter(mech)) throw new IllegalStateException("Hound entry refused");
                float startX = sim.world().x(mech), startY = sim.world().y(mech), reverseX = startX;
                float maxFlux = 0f;
                int dissipationTicks = 0, resumed = 0, earlyRounds = 0, roundsAtDissipation = -1;
                boolean bounded = true, vented = false, facing = true, handback = false;
                for (int tick = 0; tick < 900; tick++) {
                    if (!ai) {
                        boolean fire = !moving && (!mode.equals("paced-bursts") || tick % 240 < 30);
                        float dx = moving && tick < 90 ? -1f : 0f;
                        float dy = moving && tick >= 90 && tick < 180 ? 1f : 0f;
                        sim.directControl().submit(new ManualIntent(dx, dy, 24.5f, 12.5f, fire));
                    }
                    sim.advance(BattleSimulation.TICK_DT);
                    int rounds = sim.telemetry().roundsFired(mech);
                    if (tick == 89) { reverseX = sim.world().x(mech); earlyRounds = rounds; }
                    maxFlux = Math.max(maxFlux, loadout.flux.value());
                    bounded &= loadout.flux.value() >= 0f && loadout.flux.value() <= 100f;
                    if (ai ? loadout.flux.aiDissipating() : loadout.flux.venting()) {
                        dissipationTicks++;
                        if (roundsAtDissipation < 0) roundsAtDissipation = rounds;
                        vented = true;
                    } else if (vented && rounds > roundsAtDissipation) resumed++;
                    if (moving && tick < 180) {
                        float desired = MechLocomotion.continuousFacing(24.5f - sim.world().x(mech),
                                12.5f - sim.world().y(mech));
                        facing &= Math.abs(MechLocomotion.deltaDegrees(sim.world().mechHipFacingDegrees(mech), desired)) < 12f;
                    }
                    if (!ai && tick == 300) {
                        float flux = loadout.flux.value();
                        sim.directControl().exit();
                        handback = loadout.flux.value() == flux && sim.directControl().enter(mech)
                                && loadout.flux.value() == flux;
                    }
                    if (tick % 30 == 0) frames.frame(mode, sim, tick,
                            mode + " | flux " + Math.round(loadout.flux.value()) + "% | rounds " + rounds);
                }
                int total = sim.telemetry().roundsFired(mech);
                List<Verdict> verdicts = new ArrayList<>();
                verdicts.add(Verdict.of("flux-bounded", bounded, "peak flux " + maxFlux));
                if (moving) {
                    verdicts.add(Verdict.of("reverse-strafe", reverseX < startX - .5f
                            && sim.world().y(mech) > startY + .5f && facing,
                            "backed to x=" + reverseX + ", strafed to y=" + sim.world().y(mech) + ", hips track aim=" + facing));
                } else {
                    verdicts.add(Verdict.of("firing", earlyRounds > 0 && total > earlyRounds,
                            "first three seconds=" + earlyRounds + ", total=" + total));
                    if (!mode.equals("paced-bursts")) verdicts.add(Verdict.of("dissipate-and-resume",
                            dissipationTicks > 0 && resumed > 0, "dissipation ticks=" + dissipationTicks + ", resumed=" + resumed));
                    else verdicts.add(Verdict.of("paced-headroom", maxFlux < 75f, "paced bursts retain flux headroom"));
                }
                if (!ai) verdicts.add(Verdict.of("handback", handback, "takeover preserves flux reservoir"));
                reports.add(new SceneReport(id(), mode, 900, verdicts, Map.of("peakFlux", maxFlux,
                        "rounds", total, "earlyRounds", earlyRounds, "dissipationTicks", dissipationTicks)));
            }
        }
        return reports;
    }
}
