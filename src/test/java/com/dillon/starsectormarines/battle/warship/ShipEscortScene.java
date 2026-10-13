package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An escort and its lead under a strike wave, in four loops.
 *
 * <ul>
 *   <li>{@code control-alone}: a doctrine Onslaught under a defenders' bomber wing and nothing beside it. It takes the
 *       air fight on its own account once the wing is up and its flak downs some craft; the wing still works the hull.</li>
 *   <li>{@code escorted}: the same capital and wing, with an Enforcer ordered to escort it. The escort sits on the lead's
 *       ring, its picture counts the craft aimed at the lead, it takes the air fight with the lead named as the reason
 *       and its flak downs part of the wing, so the lead takes less.</li>
 *   <li>{@code lead-departs}: the pair again, but the lead's station time runs out partway through. The escort drops
 *       the relation, goes back to the station its mission gave it, and is its own ship again.</li>
 *   <li>{@code auto-assigned}: a flagship, a destroyer and a frigate with automatic escorts on and no relation named:
 *       both small hulls follow the flagship, on separate slots of its ring.</li>
 * </ul>
 *
 * Every wing loop keeps a distant marine and defender, as the air-strike scene does, so the dispatcher has a friendly
 * body to place the wing from and a lone side does not stop the clock.
 */
public final class ShipEscortScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 64;
    private static final int TICKS = 2400;
    private static final int AUTO_TICKS = 900;
    private static final float LEAD_X = 110f;
    private static final float LEAD_Y = 32f;
    private static final float ESCORT_HOME_X = 70f;
    private static final float ESCORT_HOME_Y = 12f;
    private static final float LEAD_STAY_SECONDS = 25f;

    @Override public String id() { return "ship-escort"; }

    @Override public String label() {
        return "Ship escort: a smaller combat hull keeps station on its lead, counts and shoots the craft aimed at the lead first, "
                + "and goes back to its own station when the lead is gone";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run alone = playOne("control-alone", frames);
        reports.add(report("control-alone", alone, null));
        Run escorted = playOne("escorted", frames);
        reports.add(report("escorted", escorted, alone));
        Run departs = playOne("lead-departs", frames);
        reports.add(report("lead-departs", departs, null));
        Run auto = playOne("auto-assigned", frames);
        reports.add(report("auto-assigned", auto, null));
        return reports;
    }

    private record Run(Trace trace, Warship lead, Warship escort, Warship third, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean auto = loop.equals("auto-assigned");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 60);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 195, 6);
        for (int i = 0; i < 4; i++) {
            builder.unit("edge-" + i, Faction.DEFENDER, UnitType.MARINE, 196 + (i % 2), 40 + i * 3);
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);
        if (!auto) {
            sim.setFlybyRoster(new FlybyRoster(List.of(new FighterWing(FighterProfile.DAGGER, Faction.DEFENDER, 4, 3f, 2.5f))));
        }
        Warship lead = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, LEAD_X, LEAD_Y, 90f);
        lead.stationTo(LEAD_X, LEAD_Y);
        lead.limitFlux();
        lead.useDoctrine();
        Warship escort = null;
        Warship third = null;
        if (!loop.equals("control-alone")) {
            escort = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, ESCORT_HOME_X, ESCORT_HOME_Y, 90f);
            escort.stationTo(ESCORT_HOME_X, ESCORT_HOME_Y);
            escort.limitFlux();
        }
        if (auto) {
            third = Warship.fit("hound", "hound_Assault", Faction.MARINE, ESCORT_HOME_X, 52f, 90f);
            third.stationTo(ESCORT_HOME_X, 52f);
            sim.warships().autoEscort(true);
        } else if (escort != null) {
            escort.escort(lead);
            if (loop.equals("lead-departs")) lead.stayFor(LEAD_STAY_SECONDS);
        }
        sim.addWarship(lead);
        if (escort != null) sim.addWarship(escort);
        if (third != null) sim.addWarship(third);
        Trace trace = new Trace(lead, escort, third);
        int ticks = auto ? AUTO_TICKS : TICKS;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 12, tick -> String.format(Locale.ROOT,
                        "%s / tick %d / lead hull %.0f%% / escort %s / craft lost %d",
                        loop, tick, 100f * lead.hullFraction(),
                        trace.escort == null ? "none" : trace.escort.posture().label(), trace.craftLost))
                .run(ticks);
        return new Run(trace, lead, escort, third, sim);
    }

    private static float leadPressure(Warship lead) {
        return (1f - lead.hullFraction()) * lead.maxHitPoints + lead.shieldAbsorbedFlux();
    }

    private SceneReport report(String loop, Run run, Run control) {
        Trace t = run.trace();
        Warship lead = run.lead();
        Warship escort = run.escort();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control-alone" -> {
                verdicts.add(Verdict.of("the-wing-works-the-capital", t.craftAimedAtLead >= 2,
                        t.craftAimedAtLead + " strike craft were sent at the lead, " + t.craftSeen + " flew"));
                verdicts.add(Verdict.of("the-capital-fights-its-own-fight", t.craftLost >= 1 && t.leadSawAirSuperiority,
                        t.craftLost + " craft lost to the lead's own flak, lead air superiority " + t.leadSawAirSuperiority));
                verdicts.add(Verdict.of("the-lead-is-hit", leadPressure(lead) > 0f,
                        String.format(Locale.ROOT, "lead pressure %.0f (hull %.3f, shield flux %.0f)",
                                leadPressure(lead), lead.hullFraction(), lead.shieldAbsorbedFlux())));
            }
            case "escorted" -> {
                verdicts.add(Verdict.of("escort-keeps-the-ring", t.ringSamples > 0 && t.maxRingError <= RING_TOLERANCE,
                        String.format(Locale.ROOT, "%d samples once settled, worst ring error %.1f cells", t.ringSamples, t.maxRingError)));
                verdicts.add(Verdict.of("escort-takes-the-air-fight", t.escortSawAirSuperiority,
                        "first air-superiority at tick " + t.firstEscortAirTick + ": " + t.escortAirReason));
                verdicts.add(Verdict.of("the-reason-names-the-lead", t.escortAirReason.contains("at the lead"), t.escortAirReason));
                verdicts.add(Verdict.of("escort-adds-flak", t.craftLost > control.trace().craftLost,
                        t.craftLost + " craft lost against " + control.trace().craftLost + " with the lead alone"));
                verdicts.add(Verdict.of("the-lead-takes-less", leadPressure(lead) < leadPressure(control.lead()),
                        String.format(Locale.ROOT, "lead pressure %.0f against %.0f alone", leadPressure(lead), leadPressure(control.lead()))));
                verdicts.add(Verdict.of("escort-leaves-the-lead-its-own-station", lead.stationX() == LEAD_X && lead.stationY() == LEAD_Y,
                        "lead station " + lead.stationX() + ", " + lead.stationY()));
            }
            case "lead-departs" -> {
                verdicts.add(Verdict.of("escorted-while-the-lead-stayed", t.escortingTicks > 0,
                        t.escortingTicks + " ticks escorting"));
                verdicts.add(Verdict.of("the-lead-left", lead.departing() || lead.departed(),
                        "lead departing " + lead.departing() + ", departed " + lead.departed()));
                verdicts.add(Verdict.of("the-relation-ends", escort.escortLead() == null && !escort.escorting(),
                        "escort lead " + (escort.escortLead() == null ? "none" : "still named")));
                verdicts.add(Verdict.of("back-to-its-own-station", escort.stationX() == ESCORT_HOME_X && escort.stationY() == ESCORT_HOME_Y,
                        "station " + escort.stationX() + ", " + escort.stationY()));
                verdicts.add(Verdict.of("back-on-its-own-ground", Math.hypot(escort.x - ESCORT_HOME_X, escort.y - ESCORT_HOME_Y) < 6f,
                        String.format(Locale.ROOT, "%.1f cells from home at the end", Math.hypot(escort.x - ESCORT_HOME_X, escort.y - ESCORT_HOME_Y))));
                verdicts.add(Verdict.of("its-own-posture-again", escort.posture() != WarshipPosture.AIR_SUPERIORITY,
                        "posture " + escort.posture().label() + ": " + escort.postureReason()));
            }
            default -> {
                Warship third = run.third();
                boolean both = run.escort().escortLead() == lead && third.escortLead() == lead;
                verdicts.add(Verdict.of("both-follow-the-flagship", both,
                        "destroyer leads " + (run.escort().escortLead() == lead) + ", frigate leads " + (third.escortLead() == lead)));
                verdicts.add(Verdict.of("the-flagship-follows-nobody", lead.escortLead() == null, "flagship lead " + lead.escortLead()));
                double apart = Math.hypot(run.escort().stationX() - third.stationX(), run.escort().stationY() - third.stationY());
                verdicts.add(Verdict.of("on-separate-slots", apart > 8.0, String.format(Locale.ROOT, "stations %.1f cells apart", apart)));
                verdicts.add(Verdict.of("both-reach-the-ring", t.maxRingError <= RING_TOLERANCE && t.ringSamples > 0,
                        String.format(Locale.ROOT, "worst ring error %.1f cells over %d samples", t.maxRingError, t.ringSamples)));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("leadHullFraction", Math.round(lead.hullFraction() * 10000f) / 10000f);
        metrics.put("leadShieldFlux", Math.round(lead.shieldAbsorbedFlux()));
        metrics.put("leadPressure", Math.round(leadPressure(lead)));
        metrics.put("craftSeen", t.craftSeen);
        metrics.put("craftLost", t.craftLost);
        metrics.put("craftAimedAtLead", t.craftAimedAtLead);
        metrics.put("escortingTicks", t.escortingTicks);
        metrics.put("firstEscortAirTick", t.firstEscortAirTick);
        metrics.put("maxRingError", Math.round(t.maxRingError * 10f) / 10f);
        return new SceneReport(id(), loop, loop.equals("auto-assigned") ? AUTO_TICKS : TICKS, verdicts, metrics);
    }

    /** Cells an escort's hull may sit from the point of its ring it is stationed on, once settled. */
    private static final float RING_TOLERANCE = 3f;

    private static final class Trace implements TickObserver {
        private final Warship lead;
        private final Warship escort;
        private final Warship third;
        private final Set<Long> seen = new HashSet<>();
        private final Set<Long> aimed = new HashSet<>();
        int craftSeen;
        int craftLost;
        int craftAimedAtLead;
        int escortingTicks;
        int ringSamples;
        float maxRingError;
        int firstEscortAirTick = -1;
        boolean escortSawAirSuperiority;
        boolean leadSawAirSuperiority;
        String escortAirReason = "";
        private int losses;

        private Trace(Warship lead, Warship escort, Warship third) {
            this.lead = lead;
            this.escort = escort;
            this.third = third;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            float reach = 0.5f * Math.max(lead.lengthCells, lead.widthCells) + 2f;
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission mission = sim.world().mission(id);
                if (mission == null || !mission.strikeSortie) continue;
                if (seen.add(id)) craftSeen++;
                if (Math.hypot(mission.lzX - lead.centerX(), mission.lzY - lead.centerY()) <= reach && aimed.add(id)) {
                    craftAimedAtLead++;
                }
            }
            List<AirLoss> lost = sim.getAirLosses();
            if (lost.size() > losses) {
                for (int i = losses; i < lost.size(); i++) {
                    if (lost.get(i).frame() instanceof FighterProfile) craftLost++;
                }
                losses = lost.size();
            }
            if (lead.posture() == WarshipPosture.AIR_SUPERIORITY) leadSawAirSuperiority = true;
            if (escort == null) return;
            if (escort.escorting()) escortingTicks++;
            if (escort.posture() == WarshipPosture.AIR_SUPERIORITY && !escortSawAirSuperiority) {
                escortSawAirSuperiority = true;
                firstEscortAirTick = tick;
                escortAirReason = escort.postureReason();
            }
            if (tick > 600 && !lead.departing()) sampleRing(escort);
            if (third != null && tick > 600) sampleRing(third);
        }

        private void sampleRing(Warship ship) {
            if (!ship.escorting() || ship.isMoving() && Math.hypot(ship.x - ship.stationX(), ship.y - ship.stationY()) > 1f) {
                return;
            }
            ringSamples++;
            float ring = WarshipEscort.ringRadius(lead, ship);
            float err = (float) Math.abs(Math.hypot(ship.centerX() - lead.centerX(), ship.centerY() - lead.centerY()) - ring);
            maxRingError = Math.max(maxRingError, err);
        }
    }
}
