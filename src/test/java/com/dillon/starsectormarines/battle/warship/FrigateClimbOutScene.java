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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slice 5 of the frigate duel ({@code warship-behaviours.md}, ship against ship): the rig climbs out. A Salvage Rig with two Hound (P) escorts
 * leaves along a route to the east edge, climbing as it goes, at its own speed.
 *
 * <p>Loops: {@code escape} (nobody hunting: the rig is gone over the edge by its own propulsion, its escorts keep
 * it as their lead all the way and follow it out once it has gone), {@code hunted} (two Wolf (H) from the west
 * under ship fire with the duel helm: the climbing rig is still a hull and can be hit until it is gone) and
 * {@code held} (the control: the rig is not sent away, so nobody leaves). Escaping and being shot down are readings.
 */
public final class FrigateClimbOutScene implements BehaviorScene {

    private static final int WIDTH = 140;
    /** Room for the rig and an escort clear of it on either beam: hulls are physical bodies and do not overlap. */
    private static final int HEIGHT = 100;
    private static final float RIG_X = 70f;
    private static final float RIG_Y = 50f;
    private static final float[][] ROUTE = {{96f, 40f}, {139f, 36f}};
    private static final int CLIMB_TICK = 60;
    private static final int TICKS = 5400;
    private static final int FRAME_EVERY = 8;

    @Override public String id() { return "frigate-climb-out"; }

    @Override public String label() {
        return "Frigate climb-out: a Salvage Rig leaves along a route over the map edge with its Hound (P) escorts, "
                + "hittable until it is gone";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("escape", "hunted", "held")) reports.add(report(loop, playOne(loop, frames)));
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(4).at(4, 94).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(4).at(120, 94).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, RIG_X, RIG_Y, 90f);
        rig.stationTo(RIG_X, RIG_Y);
        rig.limitFlux();
        List<Warship> hounds = new ArrayList<>();
        for (float side : new float[]{-1f, 1f}) {
            // Abreast the rig, clear of it: the rig lies east-west, a Hound north-south.
            Warship probe = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 0f, 0f, 0f);
            float dy = side * (0.5f * rig.widthCells + 0.5f * probe.lengthCells + WarshipEscort.GAP_CELLS);
            Warship hound = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, RIG_X - 6f,
                    RIG_Y + dy, 0f);
            hound.escort(rig);
            hounds.add(hound);
        }
        List<Warship> wolves = new ArrayList<>();
        if (loop.equals("hunted")) {
            for (float y : new float[]{34f, 54f}) {
                Warship wolf = ship("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, 10f, y, 0f);
                wolf.groundFire(Warship.GroundFire.HOLD);
                wolves.add(wolf);
            }
        }
        sim.addWarship(rig);
        for (Warship h : hounds) sim.addWarship(h);
        for (Warship w : wolves) sim.addWarship(w);
        Trace trace = new Trace(rig, hounds, wolves);
        boolean climb = !loop.equals("held");
        TickObserver script = (s, tick) -> {
            if (climb && tick == CLIMB_TICK) rig.climbOut(ROUTE, WIDTH, HEIGHT);
        };
        SceneRun.of(world).observe(script).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return new Run(trace, sim);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, float facing)
            throws Exception {
        Warship ship = Warship.fit(hull, variant, side, x, y, facing);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(ShipFireOrder.ALL);
        ship.useDoctrine();
        return ship;
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "escape" -> {
                verdicts.add(Verdict.of("the-rig-escapes-over-the-edge-by-its-own-propulsion",
                        t.rig.physicallyDeparted(),
                        String.format(Locale.ROOT, "rig %s at tick %d", t.rig.physicallyDeparted() ? "physically gone"
                                : t.rig.departed() ? "gone by timeout" : "still here", t.rigGoneTick)));
                verdicts.add(Verdict.of("escorts-keep-a-climbing-lead-and-follow-it-out",
                        t.escortClimbTicks > 0 && t.escortLostLeadTicks == 0 && t.departedEscorts() == 2,
                        String.format(Locale.ROOT, "%d escort-ticks with the rig climbing, %d of them without it as "
                                + "lead; %d of 2 escorts gone over the edge after it", t.escortClimbTicks,
                                t.escortLostLeadTicks, t.departedEscorts())));
                verdicts.add(Verdict.of("the-climb-follows-the-way-flown",
                        t.liftMonotonic && t.liftAtHalfway > 0.1f && t.liftAtHalfway < 0.9f,
                        String.format(Locale.ROOT, "climb clock never fell; at half the route flown it read %.2f of "
                                + "the climb", t.liftAtHalfway)));
            }
            case "hunted" -> verdicts.add(Verdict.of("a-climbing-hull-can-still-be-hit",
                    t.wolfReachedClimbingRig == 0 || t.hitsWhileClimbing > 0,
                    t.wolfReachedClimbingRig == 0
                            ? "no Wolf had the climbing rig in reach, so nothing to show"
                            : String.format(Locale.ROOT, "rig lost %d hull while climbing, in reach of a Wolf for %d "
                            + "ticks", Math.round(t.hitsWhileClimbing), t.wolfReachedClimbingRig)));
            default -> verdicts.add(Verdict.of("a-held-rig-and-its-escorts-stay",
                    !t.rig.departing() && t.departedEscorts() == 0,
                    "rig departing " + t.rig.departing() + ", escorts gone " + t.departedEscorts()));
        }
        verdicts.add(Verdict.of("nobody-leaves-the-map-except-by-departing", t.offMapUndeparted == 0,
                t.offMapUndeparted + " ship-ticks off the map without departing"));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("rigEscaped", t.rig.physicallyDeparted() ? 1 : 0);
        metrics.put("rigLost", t.rig.destroyed() ? 1 : 0);
        metrics.put("rigGoneTick", t.rigGoneTick);
        metrics.put("rigHullLostWhileClimbing", Math.round(t.hitsWhileClimbing));
        metrics.put("escortsGone", t.departedEscorts());
        metrics.put("houndsLost", t.lost(t.hounds));
        metrics.put("wolvesLost", t.lost(t.wolves));
        metrics.put("roundsAtHulls", run.sim().warships().shipFire().roundsFired());
        metrics.put("missilesFired", run.sim().warships().shipFire().missilesFired());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Warship rig;
        final List<Warship> hounds;
        final List<Warship> wolves;
        int escortClimbTicks;
        int escortLostLeadTicks;
        int offMapUndeparted;
        int rigGoneTick = -1;
        int wolfReachedClimbingRig;
        float hitsWhileClimbing;
        boolean liftMonotonic = true;
        float liftAtHalfway = Float.NaN;
        private float lastClimb;
        private float lastRigHp;
        private boolean wasClimbing;

        Trace(Warship rig, List<Warship> hounds, List<Warship> wolves) {
            this.rig = rig;
            this.hounds = hounds;
            this.wolves = wolves;
            this.lastRigHp = rig.hitPoints();
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s / tick %d", loop, tick));
            s.append(describe("Rig", rig));
            for (Warship h : hounds) s.append(describe("Hound", h));
            for (Warship w : wolves) s.append(describe("Wolf", w));
            return s.toString();
        }

        private static String describe(String name, Warship ship) {
            if (ship.destroyed()) return " / " + name + " down";
            if (ship.physicallyDeparted()) return " / " + name + " gone";
            if (ship.departing()) return String.format(Locale.ROOT, " / %s climbing %.0f%%", name,
                    ship.hullFraction() * 100f);
            return String.format(Locale.ROOT, " / %s %.0f%%", name, ship.hullFraction() * 100f);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            List<Warship> all = new ArrayList<>(hounds);
            all.addAll(wolves);
            all.add(rig);
            for (Warship s : all) {
                if (s.destroyed() || s.departed()) continue;
                boolean off = s.x < 0f || s.y < 0f || s.x > sim.getGrid().getWidth() || s.y > sim.getGrid().getHeight();
                if (off && !s.departing()) offMapUndeparted++;
            }
            if (rigGoneTick < 0 && (rig.departed() || rig.destroyed())) rigGoneTick = tick;
            boolean climbing = rig.climbingOut() && !rig.destroyed();
            if (climbing) {
                float climb = rig.climbSeconds();
                if (climb + 1e-4f < lastClimb) liftMonotonic = false;
                lastClimb = climb;
                if (Float.isNaN(liftAtHalfway) && rig.x >= 0.5f * (RIG_X + ROUTE[ROUTE.length - 1][0])) {
                    liftAtHalfway = climb / WarshipAtmosphere.CLIMB_SECONDS;
                }
                for (Warship h : hounds) {
                    if (h.destroyed() || h.departing()) continue;
                    escortClimbTicks++;
                    if (WarshipEscort.liveLead(h) != rig) escortLostLeadTicks++;
                }
                for (Warship w : wolves) {
                    if (!w.destroyed() && !w.departing()
                            && rig.distanceTo(w.centerX(), w.centerY()) <= WarshipShipFire.longestReach(w)) {
                        wolfReachedClimbingRig++;
                        break;
                    }
                }
            }
            // Hull lost on any tick that began with the rig climbing, the killing blow included.
            float hp = Math.max(0f, rig.hitPoints());
            if (wasClimbing && hp < lastRigHp) hitsWhileClimbing += lastRigHp - hp;
            lastRigHp = hp;
            wasClimbing = climbing;
        }

        int departedEscorts() {
            int n = 0;
            for (Warship h : hounds) if (h.physicallyDeparted()) n++;
            return n;
        }

        int lost(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.destroyed()) n++;
            return n;
        }
    }
}
