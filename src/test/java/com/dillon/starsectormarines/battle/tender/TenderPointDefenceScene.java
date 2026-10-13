package com.dillon.starsectormarines.battle.tender;

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
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipLoadout;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hostile warship's Locust volley against a drone tender hovering still, recorded four ways.
 *
 * <p>The tender has no guns, so its drones' mining lasers are all the point defence it has.
 * {@code light} is a handful of missiles, which the wing should mostly burn out of the air;
 * {@code heavy} is a swarm bigger than three drones can burn through in the time it crosses,
 * which should saturate them; {@code recalled} is the same swarm with the wing docked, the
 * control that says the drones, and not the map, did the work; and {@code builders} is the
 * swarm arriving while the builder drones are out working a site with the wing docked, which
 * says that a builder does not shoot.
 */
public final class TenderPointDefenceScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;
    private static final int TICKS = 900;
    /** When the hostile ship is given its order, after the wing has launched and settled. */
    private static final int VOLLEY_TICK = 240;
    private static final float TENDER_X = 100f;
    private static final float TENDER_Y = 40f;
    private static final float HOSTILE_X = 62f;
    private static final float HOSTILE_Y = 40f;
    private static final float SITE_X = 104.5f;
    private static final float SITE_Y = 40.5f;
    private static final int LIGHT_ROUNDS = 3;
    /** A light volley is one missile at a time, this many ticks apart: a wing burns about one Locust in a second and a half. */
    private static final int LIGHT_SPACING_TICKS = 75;
    private static final int HEAVY_ROUNDS = 40;
    private static final String[] SLOTS = {"WS 021", "WS 022", "WS 023", "WS 024"};

    @Override public String id() {
        return "tender-point-defence";
    }

    @Override public String label() {
        return "Drone tender point defence: its defence drones burn a light missile volley out of the air, a heavy swarm "
                + "saturates them, and with the wing docked or only builders out nothing is shot down";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("light", "heavy", "recalled", "builders")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private static WarshipLoadout locusts(int mounts) {
        return new WarshipLoadout() {
            @Override public String hullId() { return "onslaught"; }

            @Override public String weaponInSlot(String slotId) {
                for (int i = 0; i < mounts; i++) if (SLOTS[i].equals(slotId)) return "locust";
                return null;
            }
        };
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean light = loop.equals("light");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 135, 75);
        builder.unit("far-friend", Faction.MARINE, UnitType.MARINE, 3, 3);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        Tender tender = Tender.fit(Faction.MARINE, TENDER_X, TENDER_Y, -90f);
        if (loop.equals("recalled") || loop.equals("builders")) tender.defenceOut(false);
        sim.tenders().add(sim, tender);
        if (loop.equals("builders") && tender.lay(BuildOption.VULCAN_TURRET, SITE_X, SITE_Y, sim.getGrid()) == null) {
            throw new IllegalStateException("the scene's site was refused");
        }
        Warship hostile = Warship.fit(locusts(light ? 1 : SLOTS.length), Faction.DEFENDER, HOSTILE_X, HOSTILE_Y, -90f);
        hostile.limitOrdnance(light ? 0 : HEAVY_ROUNDS);
        sim.addWarship(hostile);
        Trace trace = new Trace(tender);
        TickObserver script = (s, tick) -> {
            if (tick == VOLLEY_TICK) {
                hostile.order(WarshipOrder.FireOnArea.saturating(TENDER_X, TENDER_Y, 5f, true));
            }
            if (light && tick >= VOLLEY_TICK && (tick - VOLLEY_TICK) % LIGHT_SPACING_TICKS == 0
                    && (tick - VOLLEY_TICK) / LIGHT_SPACING_TICKS < LIGHT_ROUNDS) {
                hostile.limitOrdnance(1);
            }
        };
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 6, tick -> loop + " / " + tender.statusLine() + " / tick " + tick)
                .run(TICKS);
        return report(loop, tender, hostile, sim, trace);
    }

    private SceneReport report(String loop, Tender tender, Warship hostile, BattleSimulation sim, Trace trace) {
        int fired = 0;
        for (Warship.Mount m : hostile.mounts()) if (!m.pointDefence()) fired += m.shotsFired;
        TenderPointDefence pd = tender.pointDefence();
        int stopped = pd.intercepts();
        int inFlight = sim.warships().shells().size();
        int landed = fired - stopped - inFlight;
        float stoppedShare = fired == 0 ? 0f : (float) stopped / fired;
        float hull = tender.ship().hullFraction();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "light" -> {
                verdicts.add(Verdict.of("a-light-volley-is-mostly-shot-down", fired == LIGHT_ROUNDS && stopped * 2 > fired,
                        String.format(Locale.ROOT, "%d of %d missiles shot down, %d landed", stopped, fired, landed)));
                verdicts.add(Verdict.of("the-wing-is-what-did-it", pd.beamSeconds() > 0f && trace.peakDefendersOut > 0,
                        String.format(Locale.ROOT, "%.1f drone-seconds of beam, %d defence drones out", pd.beamSeconds(),
                                trace.peakDefendersOut)));
                verdicts.add(Verdict.of("the-hull-is-untouched", hull >= 0.999f,
                        String.format(Locale.ROOT, "hull at %d%%", Math.round(hull * 100f))));
            }
            case "heavy" -> {
                verdicts.add(Verdict.of("a-heavy-swarm-saturates-the-wing",
                        fired >= HEAVY_ROUNDS * 0.9f && stopped > 0 && stoppedShare <= 0.5f && landed >= 10,
                        String.format(Locale.ROOT, "%d of %d missiles shot down (%d%%), %d landed", stopped, fired,
                                Math.round(stoppedShare * 100f), landed)));
                verdicts.add(Verdict.of("the-wing-still-works-the-whole-time", pd.beamSeconds() > 3f,
                        String.format(Locale.ROOT, "%.1f drone-seconds of beam", pd.beamSeconds())));
            }
            case "recalled" -> {
                verdicts.add(Verdict.of("docked-drones-do-not-fire",
                        stopped == 0 && pd.engagements() == 0 && trace.peakDefendersOut == 0,
                        stopped + " shot down with " + trace.peakDefendersOut + " drones ever out"));
                verdicts.add(Verdict.of("the-swarm-lands-in-full", fired >= HEAVY_ROUNDS * 0.9f && landed >= fired - inFlight,
                        landed + " of " + fired + " landed, " + inFlight + " still in the air"));
            }
            default -> {
                verdicts.add(Verdict.of("builders-were-out", trace.peakBuildersOut > 0,
                        trace.peakBuildersOut + " builders out"));
                verdicts.add(Verdict.of("builders-do-not-shoot", stopped == 0 && pd.engagements() == 0,
                        stopped + " shot down, " + pd.engagements() + " engagements"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fired", fired);
        metrics.put("shotDown", stopped);
        metrics.put("landed", landed);
        metrics.put("engagements", pd.engagements());
        metrics.put("beamSeconds", pd.beamSeconds());
        metrics.put("hullPercent", Math.round(hull * 100f));
        metrics.put("defendersLost", tender.defendersLost());
        metrics.put("peakDefendersOut", trace.peakDefendersOut);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final Tender tender;
        int peakDefendersOut;
        int peakBuildersOut;

        private Trace(Tender tender) {
            this.tender = tender;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            peakDefendersOut = Math.max(peakDefendersOut, tender.defendersOut());
            peakBuildersOut = Math.max(peakBuildersOut, tender.buildersOut());
        }
    }
}
