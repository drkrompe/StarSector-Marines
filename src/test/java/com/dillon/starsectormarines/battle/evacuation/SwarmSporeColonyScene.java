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
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hostile craft orbiting over a live bore: the spore colonies
 * (`colony-breach-nouns.md`, Spore colony).
 * <ul>
 *   <li>{@code fissure-light}: a light craft on an orbit inside a fissure
 *       colony's reach. The colony engages it.</li>
 *   <li>{@code fissure-wide}: a wider orbit, beyond a fissure colony's reach from
 *       anywhere on its bore's crescent. Nothing fires.</li>
 *   <li>{@code maw-heavy}: a heavy armed transport on the wide orbit over a bore
 *       fed to maw through the biomass seam. The bore has sprouted a colony of
 *       each grade; the maw-grade colony reaches the craft and carries the most
 *       penetration.</li>
 *   <li>{@code colonies-killed}: a bore fed to bore tier (two colonies); the
 *       fixture kills the colonies one by one while
 *       the bore lives. The sky opens: not one burst after the last colony dies,
 *       and the bore still stands.</li>
 *   <li>{@code collapsed}: the same two-colony bore; the fixture destroys it. Its colonies wither
 *       with it, and not one burst follows.</li>
 * </ul>
 * The craft is given fixture durability so the orbit lasts the loop. Every
 * burst is checked against its own colony's reach, and none comes from the
 * bore itself. The report records which grade engaged which craft with what
 * penetration.
 */
public final class SwarmSporeColonyScene implements BehaviorScene {

    private static final int TICKS = 600;
    private static final int CENTRE = 45;
    private static final float INSIDE_ORBIT = 7f;
    private static final float WIDE_ORBIT = 17f;
    /**
     * An orbit a fissure colony never reaches wherever on its bore's crescent it stands: its reach, plus the furthest a
     * growth site lies from the mouth ({@link BoreGrowthSites}), plus slack for the craft's turn.
     */
    private static final float FISSURE_CLEAR_ORBIT = 21f;
    private static final int KILL_TICK = 300;

    @Override public String id() { return "swarm-spore-colonies"; }

    @Override public String label() {
        return "Swarm bore: spore colonies engage aircraft inside their grade's reach, grow in number and"
                + " punch with tier, and die one by one or with the bore";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            return List.of(
                    playOne("fissure-light", ShuttleType.KITE, INSIDE_ORBIT, 0f, frames),
                    playOne("fissure-wide", ShuttleType.KITE, FISSURE_CLEAR_ORBIT, 0f, FrameSink.NONE),
                    playOne("maw-heavy", ShuttleType.VALKYRIE, WIDE_ORBIT, 9f, frames),
                    playOne("colonies-killed", ShuttleType.KITE, INSIDE_ORBIT, 4f, frames),
                    playOne("collapsed", ShuttleType.KITE, INSIDE_ORBIT, 4f, FrameSink.NONE));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private SceneReport playOne(String loop, ShuttleType type, float orbit, float biomass,
                                FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(90, 90).seed(20261021L).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, CENTRE, CENTRE);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            if (biomass > 0f) sim.swarmBores().feed(sim, bore, biomass);
            long craft = sim.spawnSortie(type, Faction.MARINE,
                    CENTRE, CENTRE, CENTRE, CENTRE + (int) orbit, 88, CENTRE + (int) orbit, 0);
            var body = sim.world().kinematics(craft);
            body.facingDegrees = -90;
            body.vx = 4;
            var mission = sim.world().mission(craft);
            GunshipOrbit.begin(mission, CENTRE, CENTRE, -90);
            mission.orbitRadius = orbit;
            mission.state = ShuttleState.ORBIT;
            sim.world().setMaxHp(craft, 20000);
            sim.world().setHp(craft, 20000);
            float initial = sim.world().hp(craft) + sim.world().armor(craft);
            Trace trace = new Trace(loop, bore, craft);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 15, tick -> loop + " / " + bore.tier() + " bore / "
                            + SwarmBores.livingColonies(sim, bore) + " colonies / tick " + tick)
                    .run(TICKS);
            float damage = initial - (sim.world().hp(craft) + sim.world().armor(craft));

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("colonies-not-the-bore-fire", trace.fromBore == 0,
                    trace.fromBore + " bursts from the bore's own body"));
            verdicts.add(Verdict.of("never-fires-beyond-its-reach", trace.beyondReach == 0,
                    trace.beyondReach + " of " + trace.bursts + " bursts at a craft beyond the colony's reach"));
            switch (loop) {
                case "fissure-light" -> {
                    verdicts.add(Verdict.of("a-fissure-sprouts-one-colony", bore.colonies().size()
                                    == BoreTier.FISSURE.sporeColonies,
                            bore.colonies().size() + " colonies"));
                    verdicts.add(Verdict.of("engages-a-craft-in-reach", trace.bursts > 0 && damage > 0f,
                            trace.bursts + " bursts, hull and armour lost " + damage));
                }
                case "fissure-wide" -> verdicts.add(Verdict.of("silent-beyond-a-fissure-colony's-reach",
                        trace.bursts == 0, trace.bursts + " bursts"));
                case "maw-heavy" -> {
                    verdicts.add(Verdict.of("biomass-grew-the-bore-to-maw", bore.tier() == BoreTier.MAW,
                            "tier " + bore.tier() + " after " + bore.feeds() + " feed of " + bore.biomassFed()));
                    verdicts.add(Verdict.of("a-colony-of-each-grade", trace.grades(sim).size() == 3,
                            "colony grades " + trace.grades(sim)));
                    verdicts.add(Verdict.of("the-maw-colony-reaches-the-heavy-craft",
                            trace.byGrade.getOrDefault(BoreTier.MAW, 0) > 0,
                            "bursts by grade " + trace.byGrade));
                    verdicts.add(Verdict.of("penetration-climbs-with-grade", penetrationClimbs(),
                            "penetration fissure/bore/maw " + penetration(BoreTier.FISSURE) + "/"
                                    + penetration(BoreTier.BORE) + "/" + penetration(BoreTier.MAW)));
                }
                case "colonies-killed" -> {
                    verdicts.add(Verdict.of("engaged-before-the-colonies-died", trace.burstsBefore > 0,
                            trace.burstsBefore + " bursts before tick " + KILL_TICK));
                    verdicts.add(Verdict.of("killing-colonies-opens-the-sky",
                            trace.burstsAfter == 0 && !bore.collapsed()
                                    && SwarmBores.livingColonies(sim, bore) == 0,
                            trace.burstsAfter + " bursts after the last colony died at tick " + trace.skyOpenTick
                                    + "; bore collapsed " + bore.collapsed()));
                }
                case "collapsed" -> {
                    verdicts.add(Verdict.of("colonies-wither-with-the-bore",
                            bore.collapsed() && SwarmBores.livingColonies(sim, bore) == 0
                                    && trace.burstsAfter == 0,
                            "collapsed at tick " + bore.collapsedTick() + "; "
                                    + SwarmBores.livingColonies(sim, bore) + " colonies alive; "
                                    + trace.burstsAfter + " bursts after"));
                }
                default -> throw new IllegalStateException(loop);
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("bursts", trace.bursts);
            metrics.put("craftDamage", damage);
            metrics.put("craftMaxHp", type.maxHp());
            metrics.put("coloniesSprouted", bore.colonies().size());
            metrics.put("skyOpenTick", trace.skyOpenTick);
            for (BoreTier grade : BoreTier.values()) {
                metrics.put(grade.beatName + "Bursts", trace.byGrade.getOrDefault(grade, 0));
                metrics.put(grade.beatName + "Penetration", penetration(grade));
                metrics.put(grade.beatName + "Reach", reach(grade));
            }
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static float penetration(BoreTier grade) {
        return colony(grade).mount.weapon.penetration;
    }

    private static float reach(BoreTier grade) {
        return colony(grade).mount.weapon.range;
    }

    private static StructureDef colony(BoreTier grade) {
        return TurretCatalogRegistry.requireStructure(grade.sporeColonyStructure());
    }

    private static boolean penetrationClimbs() {
        return penetration(BoreTier.FISSURE) < penetration(BoreTier.BORE)
                && penetration(BoreTier.BORE) < penetration(BoreTier.MAW)
                && reach(BoreTier.FISSURE) < reach(BoreTier.BORE) && reach(BoreTier.BORE) < reach(BoreTier.MAW);
    }

    private static final class Trace implements TickObserver {
        private final String loop;
        private final SwarmBore bore;
        private final long craft;
        private final Map<Long, BoreTier> gradeOf = new LinkedHashMap<>();
        final Map<BoreTier, Integer> byGrade = new EnumMap<>(BoreTier.class);
        int bursts;
        int beyondReach;
        int fromBore;
        int burstsBefore;
        int burstsAfter;
        /** The first tick from {@link #KILL_TICK} on with no colony of the bore's alive, or -1. */
        int skyOpenTick = -1;

        Trace(String loop, SwarmBore bore, long craft) {
            this.loop = loop;
            this.bore = bore;
            this.craft = craft;
        }

        List<BoreTier> grades(BattleSimulation sim) {
            List<BoreTier> grades = new ArrayList<>();
            for (long colony : bore.colonies()) {
                BoreTier grade = gradeOf.get(colony);
                if (grade != null && !grades.contains(grade)) grades.add(grade);
            }
            return grades;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (long colony : bore.colonies()) {
                if (gradeOf.containsKey(colony) || sim.resolveUnit(colony) == 0L) continue;
                String structure = sim.turretState().structureId(colony);
                for (BoreTier grade : BoreTier.values()) {
                    if (grade.sporeColonyStructure().equals(structure)) gradeOf.put(colony, grade);
                }
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == bore.entityId()) {
                    fromBore++;
                    continue;
                }
                BoreTier grade = gradeOf.get(shot.shooterId);
                if (grade == null) continue;
                bursts++;
                byGrade.merge(grade, 1, Integer::sum);
                if (tick < KILL_TICK) burstsBefore++;
                else if (skyOpenTick >= 0) burstsAfter++;
                if (sim.resolveUnit(shot.shooterId) == 0L) continue;
                float d = (float) Math.hypot(sim.world().x(craft) - sim.world().x(shot.shooterId),
                        sim.world().y(craft) - sim.world().y(shot.shooterId));
                // Half a cell of slack: the craft moves between the shot and this read.
                if (d > reach(grade) + 0.5f) beyondReach++;
            }
            if (tick == KILL_TICK && loop.equals("collapsed")) {
                sim.applyDamage(bore.entityId(), 1_000_000f, 100f);
            }
            if (loop.equals("colonies-killed") && tick >= KILL_TICK && (tick - KILL_TICK) % 30 == 0) {
                for (long colony : bore.colonies()) {
                    if (sim.resolveUnit(colony) == 0L) continue;
                    sim.applyDamage(colony, 1_000_000f, 100f);
                    break;
                }
            }
            if (skyOpenTick < 0 && tick >= KILL_TICK && SwarmBores.livingColonies(sim, bore) == 0) skyOpenTick = tick;
        }
    }
}
