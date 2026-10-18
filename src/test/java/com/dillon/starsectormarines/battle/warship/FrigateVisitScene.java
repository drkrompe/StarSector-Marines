package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.control.ShipCommandService;
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
 * Slice 6 of the frigate duel ({@code warship-behaviours.md}, ship against ship): one finite visit, nobody's to command. The two Wolf (H) are put
 * on the player's own side here on purpose: an allied hull is already outside every player gate by its side, so
 * only a player-side hull can show that a mission's visit, not the side, is what keeps the player's hands off it.
 *
 * <p>Loops: {@code visit} (the Wolfs arrive through the cloud deck on a ten-second visit, fight two Hound (P) and
 * leave the ordinary way once the air has been quiet for the dwell), {@code outlasted} (a hostile Hound sits out of
 * everyone's reach and then climbs out at tick 900: the visit stays while it is over the battle and leaves a dwell
 * after it has gone) and {@code own-ship} (the control: the same Wolf with no visit is the player's to command).
 */
public final class FrigateVisitScene implements BehaviorScene {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 68;
    private static final float DWELL_SECONDS = 10f;
    private static final int OUTLAST_TICK = 900;
    /** The window grows with the hull fight's durability, as the fight it watches does. */
    private static final int TICKS = Math.round(3600 * WarshipScale.HULL_DURABILITY);
    private static final int FRAME_EVERY = 8;
    /** Ticks per second of simulation. */
    private static final float TICK_HZ = 30f;

    @Override public String id() { return "frigate-visit"; }

    @Override public String label() {
        return "Frigate visit: two Wolf (H) arrive through the deck on a mission's visit, nobody's to command, and "
                + "leave once no hostile hull has been over the battle for the dwell";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("visit", "outlasted", "own-ship")) reports.add(report(loop, playOne(loop, frames)));
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(4).at(4, 62).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(4).at(112, 62).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        boolean onVisit = !loop.equals("own-ship");
        boolean fight = loop.equals("visit");
        List<Warship> wolves = new ArrayList<>();
        for (float y : new float[]{26f, 42f}) {
            Warship wolf = ship("wolf_hegemony", "wolf_hegemony_Assault", Faction.MARINE, 20f, y, 0f,
                    fight ? ShipFireOrder.ALL : ShipFireOrder.OFF);
            wolf.groundFire(Warship.GroundFire.HOLD);
            if (onVisit) {
                // Pointed at the Hounds' line to the east, where the visit is going.
                wolf.arriveFromAbove(100f, y);
                wolf.visit(DWELL_SECONDS);
            }
            wolves.add(wolf);
        }
        List<Warship> hounds = new ArrayList<>();
        if (fight) {
            for (float y : new float[]{26f, 42f}) {
                hounds.add(ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 100f, y, 180f,
                        ShipFireOrder.ALL));
            }
        } else {
            // Out of everyone's reach and fighting nobody: it is simply a hostile hull over the battle.
            Warship far = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 110f, 60f, 180f,
                    ShipFireOrder.OFF);
            hounds.add(far);
        }
        for (Warship w : wolves) sim.addWarship(w);
        for (Warship h : hounds) sim.addWarship(h);
        Trace trace = new Trace(wolves, hounds);
        TickObserver script = (s, tick) -> {
            if (!fight && tick == OUTLAST_TICK) hounds.get(0).climbOut(new float[][]{{119f, 66f}}, WIDTH, HEIGHT);
        };
        SceneRun.of(world).observe(script).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return new Run(trace, sim);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, float facing,
                                ShipFireOrder order) throws Exception {
        Warship ship = Warship.fit(hull, variant, side, x, y, facing);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(order);
        ship.useDoctrine();
        return ship;
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        List<Verdict> verdicts = new ArrayList<>();
        if (loop.equals("own-ship")) {
            verdicts.add(Verdict.of("a-player-side-hull-without-a-visit-is-the-players",
                    t.commandableTicks == t.liveTicks && t.liveTicks > 0,
                    t.commandableTicks + " of " + t.liveTicks + " live Wolf-ticks open to command and piloting"));
            verdicts.add(Verdict.of("it-stays-without-a-visit", t.departures() == 0,
                    t.departures() + " Wolfs left"));
        } else {
            verdicts.add(Verdict.of("it-arrives-through-the-deck",
                    t.wolves.stream().allMatch(Warship::arrivedFromAbove), "both Wolfs entered from above"));
            verdicts.add(Verdict.of("nobody-can-command-a-hull-on-a-visit", t.commandableTicks == 0 && t.liveTicks > 0,
                    t.commandableTicks + " of " + t.liveTicks + " live Wolf-ticks open to remote command or piloting"));
            verdicts.add(Verdict.of("it-stays-while-a-hostile-hull-is-over-the-battle", t.leftWithHostileOver == 0,
                    t.leftWithHostileOver + " visit ends began with a hostile hull still over the battle"));
            // A hull the doctrine withdrew left for its own reason; every other departure is the visit's end, and must
            // come a dwell after the last hostile hull has gone, and not much later.
            int dwellTicks = Math.round(DWELL_SECONDS * TICK_HZ);
            boolean timely = t.lastHostileGoneTick >= 0 && t.departures() == t.wolves.size();
            for (int at : t.visitEndTicks) {
                int after = at - t.lastHostileGoneTick;
                timely &= after >= dwellTicks - 1 && after <= dwellTicks + 2 * (int) TICK_HZ;
            }
            verdicts.add(Verdict.of("it-leaves-a-dwell-after-the-last-hostile-hull",
                    timely,
                    String.format(Locale.ROOT, "last hostile hull gone at tick %d; visit ends at %s, withdrawn by "
                                    + "doctrine %d (dwell %d ticks)", t.lastHostileGoneTick, t.visitEndTicks,
                            t.withdrawn, dwellTicks)));
            verdicts.add(Verdict.of("it-leaves-over-the-edge-by-its-own-propulsion",
                    t.wolves.stream().allMatch(Warship::physicallyDeparted),
                    t.physicallyGone() + " of " + t.wolves.size() + " Wolfs carried off the map"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("lastHostileGoneTick", t.lastHostileGoneTick);
        metrics.put("firstDepartureTick", t.firstDepartureTick);
        metrics.put("lastDepartureTick", t.lastDepartureTick);
        metrics.put("wolvesGone", t.physicallyGone());
        metrics.put("wolvesWithdrawnByDoctrine", t.withdrawn);
        for (int i = 0; i < t.hullAtQuietPercent.size(); i++) {
            metrics.put("wolf" + i + "HullPercentWhenQuiet", t.hullAtQuietPercent.get(i));
        }
        for (int i = 0; i < t.hullAtLeavingPercent.size(); i++) {
            metrics.put("departure" + i + "HullPercent", t.hullAtLeavingPercent.get(i));
        }
        metrics.put("houndsLost", t.lost(t.hounds));
        metrics.put("houndsDeparted", t.departedCount(t.hounds));
        metrics.put("wolfHullLost", Math.round(t.hullLost(t.wolves)));
        metrics.put("roundsAtHulls", run.sim().warships().shipFire().roundsFired());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final List<Warship> wolves;
        final List<Warship> hounds;
        int liveTicks;
        int commandableTicks;
        int leftWithHostileOver;
        int lastHostileGoneTick = -1;
        int firstDepartureTick = -1;
        int lastDepartureTick = -1;
        private final boolean[] wasDeparting;
        final List<Integer> visitEndTicks = new ArrayList<>();
        final List<Integer> hullAtQuietPercent = new ArrayList<>();
        final List<Integer> hullAtLeavingPercent = new ArrayList<>();
        int withdrawn;

        Trace(List<Warship> wolves, List<Warship> hounds) {
            this.wolves = wolves;
            this.hounds = hounds;
            this.wasDeparting = new boolean[wolves.size()];
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s / tick %d", loop, tick));
            for (Warship w : wolves) s.append(describe("Wolf", w));
            for (Warship h : hounds) s.append(describe("Hound", h));
            return s.toString();
        }

        private static String describe(String name, Warship ship) {
            if (ship.destroyed()) return " / " + name + " down";
            if (ship.departed()) return " / " + name + " gone";
            if (ship.departing()) return " / " + name + " leaving";
            if (ship.missionOwned()) {
                return String.format(Locale.ROOT, " / %s %.0f%% quiet %.0fs", name, ship.hullFraction() * 100f,
                        ship.visitQuietSeconds());
            }
            return String.format(Locale.ROOT, " / %s %.0f%%", name, ship.hullFraction() * 100f);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            boolean hostileOver = false;
            for (Warship h : hounds) hostileOver |= !h.destroyed() && !h.departed();
            if (hostileOver) lastHostileGoneTick = -1;
            else if (lastHostileGoneTick < 0) {
                lastHostileGoneTick = tick;
                hullAtQuietPercent.clear();
                for (Warship w : wolves) hullAtQuietPercent.add(Math.round(w.hullFraction() * 100f));
            }
            for (int i = 0; i < wolves.size(); i++) {
                Warship w = wolves.get(i);
                if (!w.destroyed() && !w.departing() && !w.departed()) {
                    liveTicks++;
                    boolean open = ShipCommandService.eligibility(w, null, Faction.MARINE)
                            == ShipCommandService.Notice.NONE || WarshipPilot.eligible(w, Faction.MARINE);
                    if (open) commandableTicks++;
                }
                if (w.departing() && !wasDeparting[i]) {
                    wasDeparting[i] = true;
                    hullAtLeavingPercent.add(Math.round(w.hullFraction() * 100f));
                    if (w.posture() == WarshipPosture.WITHDRAW) withdrawn++;
                    else {
                        visitEndTicks.add(tick);
                        if (hostileOver) leftWithHostileOver++;
                    }
                    if (firstDepartureTick < 0) firstDepartureTick = tick;
                    lastDepartureTick = tick;
                }
            }
        }

        int departures() {
            int n = 0;
            for (boolean d : wasDeparting) if (d) n++;
            return n;
        }

        int physicallyGone() {
            int n = 0;
            for (Warship w : wolves) if (w.physicallyDeparted()) n++;
            return n;
        }

        int lost(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.destroyed()) n++;
            return n;
        }

        int departedCount(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.departing() || s.departed()) n++;
            return n;
        }

        float hullLost(List<Warship> ships) {
            float lost = 0f;
            for (Warship s : ships) lost += s.maxHitPoints - Math.max(0f, s.hitPoints());
            return lost;
        }
    }
}
