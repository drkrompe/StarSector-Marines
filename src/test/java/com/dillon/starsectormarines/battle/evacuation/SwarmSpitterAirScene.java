package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.GunshipOrbit;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.drone.Drone;
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
 * The Spitter's rung of the air threat ladder: a Spitter alone with one hostile
 * flier and nothing else to lob at.
 * <ul>
 *   <li>{@code drone}: a hovering Marine drone. Light air: the Spitter lobs at
 *       it and the splash hurts it.</li>
 *   <li>{@code armoured-gunship}: a gunship orbiting low with its skin intact.
 *       The acid cannot bite that skin, so the Spitter never lobs at it.</li>
 *   <li>{@code stripped-gunship}: the same orbit with the skin already stripped.
 *       Now the acid bites, goes up and lands on the hull through the
 *       aimed-damage path: the rule is the craft's durability, not its kind.</li>
 *   <li>{@code high-flier}: the stripped gunship at cruise altitude. Out of
 *       reach; never lobbed at.</li>
 * </ul>
 * The gunship is given fixture structure so the orbit lasts the loop.
 */
public final class SwarmSpitterAirScene implements BehaviorScene {

    private static final int TICKS = 450;
    private static final float ORBIT_RADIUS = 6f;

    @Override public String id() { return "swarm-spitter-air"; }

    @Override public String label() {
        return "Swarm Spitter vs air: drones and stripped low craft, never intact skin or cruise altitude";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            return List.of(playDrone(frames),
                    playOne("armoured-gunship", 0.3f, false, FrameSink.NONE),
                    playOne("stripped-gunship", 0.3f, true, frames),
                    playOne("high-flier", 1f, true, FrameSink.NONE));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private SceneReport playDrone(FrameSink frames) throws Exception {
        String loop = "drone";
        SceneWorld world = SceneBuilder.openGround(60, 60).seed(20261021L).missionCompletion(false)
                .build();
        try (BattleSimulation sim = world.sim()) {
            long spitter = sim.spawn(SwarmStrains.spec("Spitter", UnitType.SWARM_SPITTER, 30, 30));
            long drone = sim.spawn(Drone.create("Drone", Faction.MARINE, 39, 30, 0L));
            float initial = sim.world().hp(drone);
            DroneTrace trace = new DroneTrace(spitter, drone);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 15, tick -> loop + " / spitter / tick " + tick)
                    .run(TICKS);
            float lost = initial - trace.lastHp;
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("lobs-at-the-drone", trace.lobs > 0, trace.lobs + " lobs"));
            verdicts.add(Verdict.of("acid-hurts-the-drone", trace.lobs == 0 || lost > 0f,
                    "drone lost " + lost + " of " + initial + (trace.died ? " and died" : "")));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("lobs", trace.lobs);
            metrics.put("droneHpLost", lost);
            metrics.put("droneDied", trace.died ? 1 : 0);
            metrics.put("spitterAlive", sim.world().isAlive(spitter) ? 1 : 0);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private SceneReport playOne(String loop, float altitude, boolean stripped, FrameSink frames)
            throws Exception {
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
            if (stripped) sim.world().setArmor(craft, 0f);
            float initial = sim.world().hp(craft) + sim.world().armor(craft);
            Trace trace = new Trace(spitter, craft, altitude);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 15, tick -> loop + " / spitter / tick " + tick)
                    .run(TICKS);
            float damage = initial - (sim.world().hp(craft) + sim.world().armor(craft));
            int landed = sim.swarmStrikes().airRoundsLanded();
            List<Verdict> verdicts = new ArrayList<>();
            if (!stripped) {
                verdicts.add(Verdict.of("intact-skin-not-lobbed-at", trace.lobs == 0 && landed == 0,
                        trace.lobs + " lobs, " + landed + " rounds landed; skin rating "
                                + sim.world().armorRating(craft) + ", craft within range on "
                                + trace.inRangeTicks + " ticks"));
            } else if (altitude <= SwarmSpitterBehavior.LOW_ALTITUDE) {
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

    private static final class DroneTrace implements TickObserver {
        private final long spitter;
        private final long drone;
        int lobs;
        float lastHp;
        boolean died;

        DroneTrace(long spitter, long drone) {
            this.spitter = spitter;
            this.drone = drone;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (sim.world().isAlive(drone)) {
                lastHp = sim.world().hp(drone);
            } else if (!died) {
                died = true;
                lastHp = 0f;
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == spitter) lobs++;
            }
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
