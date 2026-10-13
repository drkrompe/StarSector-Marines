package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An Onslaught coming into a battle from the map edge through the cloud deck and
 * leaving by the same road: the descent, the shadow that closes on it, the heat
 * on its hull, the dust under it; and the climb away.
 *
 * <p>The verdicts are the simulation's side of it: the clocks the presentation
 * reads run, the ship settles on its station, and the sounds it asks for are
 * asked for once. The picture is the other half and is read by eye from the
 * recording; the curves behind it are pinned by {@code WarshipAtmosphereTest}.
 */
public final class ShipArrivalScene implements BehaviorScene {

    static final int WIDTH = 160;
    static final int HEIGHT = 90;
    private static final int ARRIVAL_TICKS = 360;
    private static final int DEPARTURE_TICKS = 360;
    /** Seconds the ship hovers on station before it leaves, in the departure loop. */
    private static final float STAY_SECONDS = 1.5f;
    /** The altitude a hovering capital's shadow is cast from, in cells; the renderer's own figure. */
    private static final float HOVER_CELLS = 16f;
    private static final float STATION_Y = 62f;

    @Override public String id() { return "ship-arrival"; }

    @Override public String label() {
        return "Ship arrival: a capital drops through the cloud deck at the map edge, settles, and climbs away";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        reports.add(playOne("arrival", frames));
        reports.add(playOne("departure", frames));
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean arrival = loop.equals("arrival");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // A few marines beneath the approach, so the ground it works has something on it,
        // and one distant defender so the simulation never decides the battle.
        for (int i = 0; i < 6; i++) {
            builder.unit("m" + i, Faction.MARINE, UnitType.MARINE, 38 + (i % 3) * 2, 56 + (i / 3) * 3);
        }
        builder.unit("d0", Faction.DEFENDER, UnitType.MARINE, 150, 80);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261013L);

        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 0f, 0f, 0f);
        DebugWarship.Placement p = DebugWarship.place(Faction.MARINE, TraversalAxis.WEST_TO_EAST,
                WIDTH, HEIGHT, ship.lengthCells);
        // Up the map from centre, so the shadow of a high ship lands on the map rather than off its foot.
        ship.x = p.x();
        ship.y = STATION_Y;
        ship.facingDegrees = p.facingDegrees();
        ship.desiredHeading = p.facingDegrees();
        ship.stationTo(p.stationX(), STATION_Y);
        ship.order(WarshipOrder.HoldFire.INSTANCE);
        if (arrival) {
            ship.arriveFromAbove();
        } else {
            // Already on station: the loop is about leaving, not arriving.
            ship.x = p.stationX();
            ship.stayFor(STAY_SECONDS);
        }
        sim.addWarship(ship);

        Trace trace = new Trace(ship);
        int ticks = arrival ? ARRIVAL_TICKS : DEPARTURE_TICKS;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 4, tick -> caption(loop, ship, tick))
                .run(ticks);
        return report(loop, ship, trace, ticks);
    }

    private static String caption(String loop, Warship ship, int tick) {
        float lift = WarshipAtmosphere.lift(ship.entrySeconds(), ship.departingSeconds(), ship.departing());
        return String.format(Locale.ROOT,
                "%s / tick %d / altitude %.0f cells / shadow softness %.2f / glow %.2f / hull seen %.2f",
                loop, tick,
                WarshipAtmosphere.altitudeCells(HOVER_CELLS, lift),
                WarshipAtmosphere.shadowSoftness(lift),
                WarshipAtmosphere.reentryGlow(ship.entrySeconds()),
                WarshipAtmosphere.hullVisibility(ship.entrySeconds(), ship.departingSeconds(), ship.departing()));
    }

    private SceneReport report(String loop, Warship ship, Trace trace, int ticks) {
        List<Verdict> verdicts = new ArrayList<>();
        Map<WarshipSoundCue.Kind, Integer> cues = trace.cues;
        if (loop.equals("arrival")) {
            double headway = ship.x - DebugWarship.place(Faction.MARINE, TraversalAxis.WEST_TO_EAST,
                    WIDTH, HEIGHT, ship.lengthCells).x();
            verdicts.add(Verdict.of("descent-clock-ran",
                    ship.entrySeconds() >= WarshipAtmosphere.DESCENT_SECONDS,
                    "entry clock at the end " + ship.entrySeconds() + " s of a "
                            + WarshipAtmosphere.DESCENT_SECONDS + " s descent"));
            // A capital crawls: the descent is over in six seconds, the trip to its station is not.
            verdicts.add(Verdict.of("makes-for-station", headway > 4.0,
                    "moved " + headway + " cells toward its station"));
            verdicts.add(Verdict.of("report-once", count(cues, WarshipSoundCue.Kind.ARRIVAL_REPORT) == 1,
                    "arrival reports " + count(cues, WarshipSoundCue.Kind.ARRIVAL_REPORT)));
            verdicts.add(Verdict.of("settle-once", count(cues, WarshipSoundCue.Kind.ARRIVAL_SETTLE) == 1,
                    "settle thuds " + count(cues, WarshipSoundCue.Kind.ARRIVAL_SETTLE)));
            verdicts.add(Verdict.of("no-roar-on-arrival", count(cues, WarshipSoundCue.Kind.DEPARTURE_ROAR) == 0,
                    "lift-off roars " + count(cues, WarshipSoundCue.Kind.DEPARTURE_ROAR)));
        } else {
            verdicts.add(Verdict.of("climb-clock-ran",
                    ship.departing() && ship.departingSeconds() >= WarshipAtmosphere.CLIMB_SECONDS,
                    "climbing for " + ship.departingSeconds() + " s of a "
                            + WarshipAtmosphere.CLIMB_SECONDS + " s climb"));
            verdicts.add(Verdict.of("roar-once", count(cues, WarshipSoundCue.Kind.DEPARTURE_ROAR) == 1,
                    "lift-off roars " + count(cues, WarshipSoundCue.Kind.DEPARTURE_ROAR)));
            verdicts.add(Verdict.of("climb-once", count(cues, WarshipSoundCue.Kind.DEPARTURE_CLIMB) == 1,
                    "climbing roars " + count(cues, WarshipSoundCue.Kind.DEPARTURE_CLIMB)));
            verdicts.add(Verdict.of("no-arrival-sounds", count(cues, WarshipSoundCue.Kind.ARRIVAL_REPORT) == 0,
                    "arrival reports " + count(cues, WarshipSoundCue.Kind.ARRIVAL_REPORT)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("ticks", ticks);
        metrics.put("cueCount", cues.values().stream().mapToInt(Integer::intValue).sum());
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static int count(Map<WarshipSoundCue.Kind, Integer> cues, WarshipSoundCue.Kind kind) {
        return cues.getOrDefault(kind, 0);
    }

    private static final class Trace implements TickObserver {
        private final Warship ship;
        final Map<WarshipSoundCue.Kind, Integer> cues = new EnumMap<>(WarshipSoundCue.Kind.class);

        Trace(Warship ship) { this.ship = ship; }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (WarshipSoundCue cue : sim.warships().cuesThisFrame()) cues.merge(cue.kind(), 1, Integer::sum);
        }
    }
}
