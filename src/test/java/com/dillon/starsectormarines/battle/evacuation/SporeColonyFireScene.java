package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.GunshipOrbit;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spore colonies firing at a gunship, close up, for how the anti-air reads
 * (`colony-breach-nouns.md`, Spore colony): the sac flaring as it spits, the
 * shot in flight, the burst at the craft and the haze it leaves. One loop per
 * tier of bore, each with a craft orbiting inside every colony's reach:
 * <ul>
 *   <li>{@code fissure}: an unfed bore and its one fissure colony (spore burst).</li>
 *   <li>{@code bore}: a bore fed to bore tier, so a bore-grade colony (spore flak) fires too.</li>
 *   <li>{@code maw}: a bore fed to maw, a colony of each grade (spore lance) at a heavy transport.</li>
 * </ul>
 * Recorded every few ticks, because a burst lives for half a second; the
 * mechanics of reach, grade and collapse are {@link SwarmSporeColonyScene}'s.
 * The verdicts assert only that colonies fire at the craft and the bore's own
 * body never does.
 */
public final class SporeColonyFireScene implements BehaviorScene {

    static final int CENTRE = 45;
    private static final int TICKS = 300;
    private static final int FRAME_EVERY = 3;
    private static final float ORBIT = 7.5f;

    @Override public String id() { return "spore-colony-fire"; }

    @Override public String label() {
        return "Spore colonies firing at a gunship, close up: the sac's flare, the shot, the airburst and its haze,"
                + " one loop per grade";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            return List.of(
                    playOne("fissure", ShuttleType.KITE, 0f, frames),
                    playOne("bore", ShuttleType.KITE, 4f, frames),
                    playOne("maw", ShuttleType.VALKYRIE, 9f, frames));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private SceneReport playOne(String loop, ShuttleType type, float biomass, FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(90, 90).seed(20261021L).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, CENTRE, CENTRE);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            if (biomass > 0f) sim.swarmBores().feed(sim, bore, biomass);
            long craft = sim.spawnSortie(type, Faction.MARINE,
                    CENTRE, CENTRE, CENTRE, CENTRE + (int) ORBIT, 88, CENTRE + (int) ORBIT, 0);
            var body = sim.world().kinematics(craft);
            body.facingDegrees = -90;
            body.vx = 4;
            var mission = sim.world().mission(craft);
            GunshipOrbit.begin(mission, CENTRE, CENTRE, -90);
            mission.orbitRadius = ORBIT;
            mission.state = ShuttleState.ORBIT;
            sim.world().setMaxHp(craft, 20000);
            sim.world().setHp(craft, 20000);
            Bursts bursts = new Bursts(bore);
            SceneRun.of(world).observe(bursts)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + bore.tier() + " bore / "
                            + SwarmBores.livingColonies(sim, bore) + " colonies / tick " + tick)
                    .run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("colonies-fire-at-the-craft", bursts.fromColonies > 0,
                    bursts.fromColonies + " bursts from the colonies"));
            verdicts.add(Verdict.of("the-bore-body-never-fires", bursts.fromBore == 0,
                    bursts.fromBore + " bursts from the bore's own body"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("bursts", bursts.fromColonies);
            metrics.put("colonies", bore.colonies().size());
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static final class Bursts implements TickObserver {
        private final SwarmBore bore;
        int fromColonies;
        int fromBore;

        Bursts(SwarmBore bore) {
            this.bore = bore;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == bore.entityId()) fromBore++;
                else if (bore.colonies().contains(shot.shooterId)) fromColonies++;
            }
        }
    }
}
