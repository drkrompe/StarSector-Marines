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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Spitter under a hostile craft orbiting tightly overhead, with nothing else
 * to lob at.
 * <ul>
 *   <li>{@code low-flier}: the craft is held at a low altitude. Acid goes up
 *       and lands on the hull through the aimed-damage path.</li>
 *   <li>{@code high-flier}: the same orbit at cruise altitude. The craft is out
 *       of reach and the Spitter never lobs at it.</li>
 * </ul>
 * The craft is given fixture durability so the orbit lasts the loop.
 */
public final class SwarmSpitterAirScene implements BehaviorScene {

    private static final int TICKS = 450;
    private static final float ORBIT_RADIUS = 6f;

    @Override public String id() { return "swarm-spitter-air"; }

    @Override public String label() {
        return "Swarm Spitter: acid reaches a low flier, never a high one";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            return List.of(playOne("low-flier", 0.3f, frames),
                    playOne("high-flier", 1f, FrameSink.NONE));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private SceneReport playOne(String loop, float altitude, FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(60, 60).seed(20261021L).missionCompletion(false)
                .build();
        try (BattleSimulation sim = world.sim()) {
            long spitter = sim.spawn(SwarmStrains.spec("Spitter", UnitType.SWARM_SPITTER, 30, 30));
            long craft = sim.spawnSortie(ShuttleType.AEROSHUTTLE, Faction.MARINE,
                    30, 30, 30, 30 + (int) ORBIT_RADIUS, 58, 30 + (int) ORBIT_RADIUS, 0);
            var body = sim.world().kinematics(craft);
            body.facingDegrees = -90;
            body.vx = 4;
            var mission = sim.world().mission(craft);
            GunshipOrbit.begin(mission, 30, 30, -90);
            mission.orbitRadius = ORBIT_RADIUS;
            mission.state = ShuttleState.ORBIT;
            sim.world().setMaxHp(craft, 20000);
            sim.world().setHp(craft, 20000);
            float initial = sim.world().hp(craft) + sim.world().armor(craft);
            Trace trace = new Trace(spitter, craft, altitude);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 15, tick -> loop + " / spitter / tick " + tick)
                    .run(TICKS);
            float damage = initial - (sim.world().hp(craft) + sim.world().armor(craft));
            int landed = sim.swarmStrikes().airRoundsLanded();
            List<Verdict> verdicts = new ArrayList<>();
            if (altitude <= SwarmSpitterBehavior.LOW_ALTITUDE) {
                verdicts.add(Verdict.of("lobs-at-the-flier", trace.lobs > 0,
                        trace.lobs + " lobs; craft within range on " + trace.inRangeTicks + " ticks"));
                verdicts.add(Verdict.of("acid-lands-on-the-hull", trace.lobs == 0
                                || (landed > 0 && damage > 0f),
                        landed + " rounds landed, hull and armour lost " + damage));
            } else {
                verdicts.add(Verdict.of("high-flier-out-of-reach", trace.lobs == 0 && landed == 0,
                        trace.lobs + " lobs, " + landed + " rounds landed"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("lobs", trace.lobs);
            metrics.put("roundsLanded", landed);
            metrics.put("craftDamage", damage);
            metrics.put("inRangeTicks", trace.inRangeTicks);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static final class Trace implements TickObserver {
        private final long spitter;
        private final long craft;
        private final float altitude;
        int lobs;
        int inRangeTicks;

        Trace(long spitter, long craft, float altitude) {
            this.spitter = spitter;
            this.craft = craft;
            this.altitude = altitude;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            // The air system steers altitude by flight state; the fixture holds it.
            if (sim.world().isAlive(craft)) sim.world().setAltitudeT(craft, altitude);
            float d = (float) Math.hypot(sim.world().x(craft) - sim.world().x(spitter),
                    sim.world().y(craft) - sim.world().y(spitter));
            if (d <= sim.combat().attackRange(spitter)) inRangeTicks++;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == spitter) lobs++;
            }
        }
    }
}
