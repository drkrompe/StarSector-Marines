package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
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
 * Aircraft against a ship: a defenders' bomber wing against a marine hull, in five loops.
 *
 * <ul>
 *   <li>{@code control-no-strike}: the same Enforcer under its doctrine and no wing. Nothing touches the hull and it
 *       never takes the air fight.</li>
 *   <li>{@code strike-shield-down}: the wing flies; the hull's shield is held down and its air fire is off, so what
 *       the wing does to the plate is read bare. Craft are aimed at the hull and the armour rule takes hit points.</li>
 *   <li>{@code strike-shielded}: the same wing at the same hull with its omni shield on automatic. The shield rises
 *       on the landings and takes them as flux; the hull loses less than the bare one.</li>
 *   <li>{@code strike-doctrine}: the shielded hull under its doctrine. It sees the wing as a threat (the posture goes
 *       to air superiority with armed craft as the reason) and its flak shoots some of the wing down.</li>
 *   <li>{@code weak-ordnance}: a wing of beam fighters against an Onslaught, whose plate their beams glance off.
 *       Not aimed at the hull, not counted as a threat, nothing taken.</li>
 * </ul>
 *
 * Every loop keeps a distant marine and defender: the dispatcher needs a friendly body to say which edge the wing is
 * from, and a lone side would stop the clock.
 */
public final class WarshipAirStrikeScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 64;
    private static final int TICKS = 2400;
    private static final float SHIP_X = 110f;
    private static final float SHIP_Y = 32f;

    @Override public String id() { return "warship-air-strike"; }

    @Override public String label() {
        return "Warship air strike: hostile bombers choose a hull the ordnance can hurt, the armour rule and shield take the hits, "
                + "and the ship's threat picture and flak respond";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run control = playOne("control-no-strike", frames);
        reports.add(report("control-no-strike", control, null));
        Run bare = playOne("strike-shield-down", frames);
        reports.add(report("strike-shield-down", bare, null));
        Run shielded = playOne("strike-shielded", frames);
        reports.add(report("strike-shielded", shielded, bare));
        Run doctrine = playOne("strike-doctrine", frames);
        reports.add(report("strike-doctrine", doctrine, shielded));
        Run weak = playOne("weak-ordnance", frames);
        reports.add(report("weak-ordnance", weak, null));
        return reports;
    }

    private record Run(Trace trace, Warship ship, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean weak = loop.equals("weak-ordnance");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 60);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 195, 6);
        for (int i = 0; i < 4; i++) {
            builder.unit("edge-" + i, Faction.DEFENDER, UnitType.MARINE, 196 + (i % 2), 40 + i * 3);
        }
        if (weak) {
            // Ground worth a sortie, far from the hull: the wing flies at it because the hull is not an eligible target.
            for (int i = 0; i < 6; i++) {
                builder.unit("block-" + i, Faction.MARINE, UnitType.MARINE, 30 + (i % 3) * 2, 12 + (i / 3) * 2);
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261013L);
        if (!loop.equals("control-no-strike")) {
            FighterProfile profile = weak ? FighterProfile.THUNDER : FighterProfile.DAGGER;
            sim.setFlybyRoster(new FlybyRoster(List.of(new FighterWing(profile, Faction.DEFENDER, 4, 3f, 2.5f))));
        }
        Warship ship = weak
                ? Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, SHIP_X, SHIP_Y, 90f)
                : Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, SHIP_X, SHIP_Y, 90f);
        ship.stationTo(SHIP_X, SHIP_Y);
        ship.limitFlux();
        switch (loop) {
            case "strike-shield-down" -> {
                ship.airFire(AirFireOrder.OFF);
                ship.shieldAuto(false);
                ship.raiseShield(false);
            }
            case "strike-shielded" -> ship.airFire(AirFireOrder.OFF);
            case "strike-doctrine", "control-no-strike", "weak-ordnance" -> ship.useDoctrine();
            default -> { }
        }
        sim.addWarship(ship);
        Trace trace = new Trace(ship);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 12, tick -> String.format(Locale.ROOT,
                        "%s / tick %d / %s / hull %.0f%% / shield flux %.0f / craft lost %d",
                        loop, tick, ship.posture().label(), 100f * ship.hullFraction(),
                        ship.shieldAbsorbedFlux(), trace.craftLost))
                .run(TICKS);
        return new Run(trace, ship, sim);
    }

    private SceneReport report(String loop, Run run, Run other) {
        Trace t = run.trace();
        Warship ship = run.ship();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control-no-strike" -> {
                verdicts.add(Verdict.of("hull-untouched", ship.hullFraction() == 1f && ship.shieldAbsorbedFlux() == 0f,
                        String.format(Locale.ROOT, "hull %.3f, shield flux %.1f", ship.hullFraction(), ship.shieldAbsorbedFlux())));
                verdicts.add(Verdict.of("no-air-fight", !t.sawAirSuperiority && t.peakArmedCraft == 0,
                        "air superiority " + t.sawAirSuperiority + ", armed craft seen " + t.peakArmedCraft));
            }
            case "strike-shield-down" -> {
                verdicts.add(Verdict.of("craft-fly-at-the-hull", t.craftAimedAtHull >= 2,
                        t.craftAimedAtHull + " strike craft were sent at the hull, " + t.craftSeen + " flew"));
                verdicts.add(Verdict.of("the-hull-is-hurt", ship.hullFraction() < 1f,
                        String.format(Locale.ROOT, "hull %.3f of 1 after the wing", ship.hullFraction())));
                verdicts.add(Verdict.of("at-most-two-on-one-hull", t.peakOnHull <= 2,
                        t.peakOnHull + " craft on the hull at once (cap 2)"));
                verdicts.add(Verdict.of("a-bare-hull-is-no-shield-hit", ship.shieldAbsorbedFlux() == 0f,
                        "shield took " + ship.shieldAbsorbedFlux()));
            }
            case "strike-shielded" -> {
                float bareLost = 1f - other.ship().hullFraction();
                float lost = 1f - ship.hullFraction();
                verdicts.add(Verdict.of("craft-fly-at-the-hull", t.craftAimedAtHull >= 2,
                        t.craftAimedAtHull + " strike craft were sent at the hull"));
                verdicts.add(Verdict.of("the-shield-takes-the-landings", ship.shieldAbsorbedFlux() > 0f && ship.shieldHits() > 0,
                        String.format(Locale.ROOT, "%d hits, %.0f flux absorbed", ship.shieldHits(), ship.shieldAbsorbedFlux())));
                verdicts.add(Verdict.of("the-hull-loses-less-than-bare", lost < bareLost,
                        String.format(Locale.ROOT, "hull lost %.4f against %.4f with the shield down", lost, bareLost)));
            }
            case "strike-doctrine" -> {
                verdicts.add(Verdict.of("armed-craft-are-a-threat", t.peakArmedCraft >= 1,
                        t.peakArmedCraft + " armed craft at most in the threat picture"));
                verdicts.add(Verdict.of("takes-the-air-fight", t.sawAirSuperiority,
                        "first air-superiority at tick " + t.firstAirTick + ": " + t.airReason));
                verdicts.add(Verdict.of("the-reason-names-the-craft", t.airReason.contains("armed craft"), t.airReason));
                verdicts.add(Verdict.of("flak-kills-some", t.craftLost >= 1,
                        t.craftLost + " of " + t.craftSeen + " strike craft lost"));
                verdicts.add(Verdict.of("the-hull-fares-no-worse-than-undefended",
                        ship.hullFraction() >= other.ship().hullFraction(),
                        String.format(Locale.ROOT, "hull %.4f against %.4f with no air fire", ship.hullFraction(), other.ship().hullFraction())));
            }
            default -> {
                verdicts.add(Verdict.of("the-wing-flew-at-the-ground", t.craftSeen >= 1,
                        t.craftSeen + " strike craft flew, so the empty verdicts below are not vacuous"));
                verdicts.add(Verdict.of("not-sent-at-the-hull", t.craftAimedAtHull == 0,
                        t.craftAimedAtHull + " craft aimed at a hull their beams glance off"));
                verdicts.add(Verdict.of("not-a-threat", t.peakArmedCraft == 0 && !t.sawAirSuperiority,
                        t.peakArmedCraft + " counted, air superiority " + t.sawAirSuperiority));
                verdicts.add(Verdict.of("hull-untouched", ship.hullFraction() == 1f,
                        String.format(Locale.ROOT, "hull %.3f", ship.hullFraction())));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hullFraction", Math.round(ship.hullFraction() * 10000f) / 10000f);
        metrics.put("shieldHits", ship.shieldHits());
        metrics.put("shieldFlux", Math.round(ship.shieldAbsorbedFlux()));
        metrics.put("craftSeen", t.craftSeen);
        metrics.put("craftLost", t.craftLost);
        metrics.put("craftAimedAtHull", t.craftAimedAtHull);
        metrics.put("peakOnHull", t.peakOnHull);
        metrics.put("peakArmedCraft", t.peakArmedCraft);
        metrics.put("firstAirTick", t.firstAirTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final Warship ship;
        private final Set<Long> seen = new HashSet<>();
        private final Set<Long> aimed = new HashSet<>();
        int craftSeen;
        int craftLost;
        int craftAimedAtHull;
        int peakOnHull;
        int peakArmedCraft;
        int firstAirTick = -1;
        boolean sawAirSuperiority;
        String airReason = "";
        private int losses;

        private Trace(Warship ship) {
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            float reach = 0.5f * Math.max(ship.lengthCells, ship.widthCells) + 2f;
            int onHull = 0;
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission mission = sim.world().mission(id);
                if (mission == null || !mission.strikeSortie) continue;
                if (seen.add(id)) craftSeen++;
                boolean working = mission.state == ShuttleState.INCOMING || mission.state == ShuttleState.REPOSITION
                        || mission.state == ShuttleState.ATTACK_RUN;
                if (Math.hypot(mission.lzX - ship.centerX(), mission.lzY - ship.centerY()) <= reach) {
                    if (!working) continue;
                    onHull++;
                    if (aimed.add(id)) craftAimedAtHull++;
                }
            }
            peakOnHull = Math.max(peakOnHull, onHull);
            List<AirLoss> lost = sim.getAirLosses();
            if (lost.size() > losses) {
                for (int i = losses; i < lost.size(); i++) {
                    if (lost.get(i).frame() instanceof FighterProfile) craftLost++;
                }
                losses = lost.size();
            }
            peakArmedCraft = Math.max(peakArmedCraft,
                    AirThreatPicture.take(sim, ship, sim.warships().ships()).armedCraft());
            if (ship.posture() == WarshipPosture.AIR_SUPERIORITY && !sawAirSuperiority) {
                sawAirSuperiority = true;
                firstAirTick = tick;
                airReason = ship.postureReason();
            }
        }
    }
}
