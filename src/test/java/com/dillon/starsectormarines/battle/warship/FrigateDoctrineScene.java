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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slice 4 of {@code atmospheric-frigate-duel.md}: doctrine for duels. Two Wolf (H) hunt a Salvage Rig held on
 * station with its two Hound (P) escorts, over a hostile garrison. Every hull runs the ordinary warship doctrine.
 *
 * <p>Loops: {@code pack} (the Wolfs on ground-fire {@code HOLD}: the one nearer the rig runs it down, the other
 * pins an escort, the escorts hold their ring on the Wolfs' side of the rig, and no Wolf fires at the ground),
 * {@code withdraw} (the same with every Hound already battered, so a hull under the doctrine's withdraw line is
 * seen breaking off through the ordinary departure and firing nothing after) and {@code ground-allowed} (the
 * {@code pack} fight with the Wolfs' ground fire left at {@code ALL}: the garrison's exposure to them is a
 * reading). Verdicts are mechanics; who wins is a reading.
 */
public final class FrigateDoctrineScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 68;
    private static final float WOLF_X = 18f;
    private static final float RIG_X = 112f;
    private static final float MID_Y = 34f;
    private static final int TICKS = 1800;
    private static final int FRAME_EVERY = 6;
    /** A hull under the withdraw line must be leaving within this many ticks (the doctrine runs once a second). */
    private static final int WITHDRAW_GRACE_TICKS = 75;

    @Override public String id() { return "frigate-doctrine"; }

    @Override public String label() {
        return "Frigate doctrine: two Wolf (H) on ground-fire hold split on a Salvage Rig and its two Hound (P) "
                + "escorts; crippled hulls break off through the ordinary departure";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("pack", "withdraw", "ground-allowed")) reports.add(report(loop, playOne(loop, frames)));
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // A hostile garrison across the Wolfs' way in, and a friendly squad well clear of the fight.
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 6; col++) {
                builder.unit("g" + row + "-" + col, Faction.DEFENDER, UnitType.MARINE, 48 + col * 2, 28 + row * 4);
            }
        }
        builder.squad("west-ground").faction(Faction.MARINE).size(4).at(4, 62).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, RIG_X, MID_Y, 180f);
        rig.stationTo(RIG_X, MID_Y);
        rig.limitFlux();
        List<Warship> wolves = new ArrayList<>();
        List<Warship> hounds = new ArrayList<>();
        for (float y : new float[]{MID_Y - 10f, MID_Y + 10f}) {
            Warship wolf = ship("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, WOLF_X, y, 0f);
            wolf.direct(20f, false);
            wolf.groundFire(loop.equals("ground-allowed") ? Warship.GroundFire.ALL : Warship.GroundFire.HOLD);
            wolves.add(wolf);
            Warship hound = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, RIG_X - 8f, y,
                    180f);
            hound.escort(rig);
            if (loop.equals("withdraw")) hound.takeDamage(hound.maxHitPoints * 0.6f, 1000f);
            hounds.add(hound);
        }
        sim.addWarship(rig);
        for (Warship h : hounds) sim.addWarship(h);
        for (Warship w : wolves) sim.addWarship(w);
        List<Long> garrison = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 6; col++) garrison.add(world.unit("g" + row + "-" + col));
        }
        Trace trace = new Trace(rig, wolves, hounds, garrison);
        SceneRun.of(world).observe(trace)
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
        WarshipShipFire fire = run.sim().warships().shipFire();
        List<Verdict> verdicts = new ArrayList<>();
        if (!loop.equals("withdraw")) {
            verdicts.add(Verdict.of("the-pack-splits-on-the-charge-and-an-escort",
                    t.packTicks > 0 && t.splitTicks == t.packTicks,
                    String.format(Locale.ROOT, "%d of %d ticks with both Wolfs hunting while the rig and an escort "
                            + "lived had one on the rig (the nearer, within the switch margin) and one on an escort",
                            t.splitTicks, t.packTicks)));
            verdicts.add(Verdict.of("escorts-hold-their-ring-on-the-threat-side",
                    t.ringTicks > 0 && t.share(t.threatSideTicks, t.ringTicks) >= 0.8f,
                    String.format(Locale.ROOT, "escorts on the Wolfs' side of the rig %.0f%% of %d escort-ticks",
                            100f * t.share(t.threatSideTicks, t.ringTicks), t.ringTicks)));
        }
        if (!loop.equals("ground-allowed")) {
            verdicts.add(Verdict.of("wolfs-on-hold-fire-nothing-at-the-ground", t.wolfGroundShots() == 0,
                    t.wolfGroundShots() + " ground shots from the Wolfs; closest pass over the garrison "
                            + Math.round(t.closestOverGarrison) + " cells"));
        }
        if (loop.equals("withdraw")) {
            verdicts.add(Verdict.of("crippled-hulls-break-off-through-departure",
                    t.crippled > 0 && t.lateWithdrawals == 0,
                    String.format(Locale.ROOT, "%d hulls went under the withdraw line alive; %d were not leaving "
                            + "within %d ticks", t.crippled, t.lateWithdrawals, WITHDRAW_GRACE_TICKS)));
            verdicts.add(Verdict.of("a-leaving-hull-fires-nothing-at-hulls", t.shotsWhileLeaving == 0,
                    t.shotsWhileLeaving + " shots from hulls already leaving"));
        }
        verdicts.add(Verdict.of("nobody-leaves-the-map-except-by-departing", t.offMapUndeparted == 0,
                t.offMapUndeparted + " ship-ticks off the map without departing"));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("missilesFired", fire.missilesFired());
        metrics.put("wolfGroundShots", t.wolfGroundShots());
        metrics.put("rigLost", run.trace().rig.destroyed() ? 1 : 0);
        metrics.put("rigHullLost", Math.round(t.rig.maxHitPoints - Math.max(0f, t.rig.hitPoints())));
        metrics.put("houndsLost", t.lost(t.hounds));
        metrics.put("houndsDeparted", t.departed(t.hounds));
        metrics.put("wolvesLost", t.lost(t.wolves));
        metrics.put("wolvesDeparted", t.departed(t.wolves));
        metrics.put("wolfHullLost", Math.round(t.hullLost(t.wolves)));
        metrics.put("firstRigHitTick", t.firstRigHitTick);
        metrics.put("garrisonAlive", t.garrisonAlive);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Warship rig;
        final List<Warship> wolves;
        final List<Warship> hounds;
        private final Map<Warship, Integer> crippledAt = new HashMap<>();
        private final Map<Warship, Integer> shotsAtDeparture = new HashMap<>();
        int packTicks;
        int splitTicks;
        int ringTicks;
        int threatSideTicks;
        int crippled;
        int lateWithdrawals;
        int shotsWhileLeaving;
        int offMapUndeparted;
        int firstRigHitTick = -1;
        int garrisonAlive;
        float closestOverGarrison = Float.MAX_VALUE;

        private final List<Long> garrison;

        Trace(Warship rig, List<Warship> wolves, List<Warship> hounds, List<Long> garrison) {
            this.garrison = garrison;
            this.rig = rig;
            this.wolves = wolves;
            this.hounds = hounds;
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s / tick %d", loop, tick));
            s.append(describe("Rig", rig));
            for (Warship w : wolves) s.append(describe("Wolf", w));
            for (Warship h : hounds) s.append(describe("Hound", h));
            return s.toString();
        }

        private static String describe(String name, Warship ship) {
            if (ship.destroyed()) return " / " + name + " down";
            if (ship.departing()) return " / " + name + " leaving";
            return String.format(Locale.ROOT, " / %s %.0f%%", name, ship.hullFraction() * 100f);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            List<Warship> all = new ArrayList<>(wolves);
            all.addAll(hounds);
            all.add(rig);
            if (firstRigHitTick < 0 && rig.hitPoints() < rig.maxHitPoints) firstRigHitTick = tick;
            for (Warship s : all) {
                if (s.destroyed() || s.departed()) continue;
                boolean off = s.x < 0f || s.y < 0f || s.x > sim.getGrid().getWidth() || s.y > sim.getGrid().getHeight();
                if (off && !s.departing()) offMapUndeparted++;
                if (s.hasDoctrine() && s.hullFraction() < WarshipDoctrine.WITHDRAW_HULL && !crippledAt.containsKey(s)) {
                    crippledAt.put(s, tick);
                    crippled++;
                }
                Integer at = crippledAt.get(s);
                if (at != null && at >= 0 && tick - at > WITHDRAW_GRACE_TICKS) {
                    if (!s.departing()) lateWithdrawals++;
                    crippledAt.put(s, -1);
                }
                int shots = shots(s);
                if (s.departing()) {
                    Integer before = shotsAtDeparture.putIfAbsent(s, shots);
                    if (before != null && shots > before) {
                        shotsWhileLeaving += shots - before;
                        shotsAtDeparture.put(s, shots);
                    }
                }
            }
            // The pack: both Wolfs hunting while the rig and at least one escort live.
            Warship a = wolves.get(0);
            Warship b = wolves.get(1);
            boolean escortAlive = false;
            for (Warship h : hounds) escortAlive |= live(h) && WarshipEscort.liveLead(h) == rig;
            if (live(a) && live(b) && live(rig) && escortAlive && a.hullTarget() != null && b.hullTarget() != null) {
                packTicks++;
                Warship onRig = a.hullTarget() == rig ? a : b.hullTarget() == rig ? b : null;
                Warship other = onRig == a ? b : a;
                boolean split = onRig != null && other.hullTarget() != rig && hounds.contains(other.hullTarget());
                if (split) {
                    float mine = onRig.distanceTo(rig.centerX(), rig.centerY());
                    float theirs = other.distanceTo(rig.centerX(), rig.centerY());
                    split = mine <= theirs + WarshipShipFire.PACK_SWITCH_CELLS + 0.5f;
                }
                if (split) splitTicks++;
            }
            // Escorts on the ring: on the Wolfs' side of the rig.
            float wx = 0f;
            float wy = 0f;
            int wn = 0;
            for (Warship w : wolves) {
                if (!live(w)) continue;
                wx += w.centerX();
                wy += w.centerY();
                wn++;
            }
            if (wn > 0 && live(rig)) {
                float tx = wx / wn - rig.centerX();
                float ty = wy / wn - rig.centerY();
                for (Warship h : hounds) {
                    if (!live(h) || WarshipEscort.liveLead(h) != rig || h.isMoving()) continue;
                    ringTicks++;
                    if ((h.centerX() - rig.centerX()) * tx + (h.centerY() - rig.centerY()) * ty > 0f) threatSideTicks++;
                }
            }
            garrisonAlive = 0;
            for (long u : garrison) {
                if (!sim.getRoster().isAliveById(u)) continue;
                garrisonAlive++;
                for (Warship w : wolves) {
                    if (live(w)) {
                        closestOverGarrison = Math.min(closestOverGarrison,
                                w.distanceTo(sim.world().x(u), sim.world().y(u)));
                    }
                }
            }
        }

        private static boolean live(Warship s) {
            return !s.destroyed() && !s.departing() && !s.departed();
        }

        private static int shots(Warship s) {
            int n = 0;
            for (Warship.Mount m : s.mounts()) if (!m.pointDefence()) n += m.shotsFired;
            return n;
        }

        int wolfGroundShots() {
            int n = 0;
            for (Warship w : wolves) n += w.groundShots();
            return n;
        }

        float share(int on, int in) {
            return in == 0 ? 0f : (float) on / in;
        }

        float hullLost(List<Warship> ships) {
            float lost = 0f;
            for (Warship s : ships) lost += s.maxHitPoints - Math.max(0f, s.hitPoints());
            return lost;
        }

        int lost(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.destroyed()) n++;
            return n;
        }

        int departed(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.departing() || s.departed()) n++;
            return n;
        }
    }
}
