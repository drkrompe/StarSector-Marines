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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Missile exchanges between hulls (<code>warship-tactics.md</code>, missile behaviour): ships that launch into windows
 * rather than rippling their racks at first reach, point defence turned on the salvos coming at its own hull, and
 * magazines spent as a resource.
 *
 * <p>Two matchups, each played twice: with launch windows ({@link MissileWindow}) and with
 * {@code missileWindows(false)}, the control, where every missile launches as soon as it reaches. {@code fleet} is
 * three a side of stock missile fits, so every role meets point defence: an Enforcer (Harpoons and Reapers), a Shrike
 * (a Sabot pod, PD lasers) and a Vigilance (a Typhoon) against a Medusa (Sabots, PD lasers), a Hammerhead (Harpoons, PD
 * lasers) and a Vigilance. {@code pack} is two Enforcers flying the relay on a Dominator (Support): a front shield to
 * get round, a battery of vulcans and flak, and Pilum racks whose magazines refill, the striker's launches read
 * against the tank's hold on the target.
 *
 * <p>Verdicts are mechanics: every gated launch went into a window its role can use; torpedo, finisher and saturation launches go
 * into an opening (overload, shield down, flank, high flux, worn, a striker's moment, a salvo) more often than the
 * control's; point defence
 * engages missiles aimed at hulls; no magazine ever holds or spends more than it carries and has reloaded; and in the
 * pack a launcher joins a packmate's salvo or strikes while the tank holds aggro. Launches by window and role, point
 * defence engagements and intercepts, hit share and the fight's outcome are readings.
 */
public final class WarshipMissileExchangeScene implements BehaviorScene {

    private static final int TICKS = Integer.getInteger("battle.scene.missileExchange.ticks", 15000);
    private static final int FRAME_EVERY = Integer.getInteger("battle.scene.missileExchange.frameEvery", 30);
    /** The first tick a frame is recorded on, so a review can open where the missiles start flying. */
    private static final int FRAME_FROM = Integer.getInteger("battle.scene.missileExchange.frameFrom", 0);
    /** The last tick a frame is recorded on. */
    private static final int FRAME_TO = Integer.getInteger("battle.scene.missileExchange.frameTo", Integer.MAX_VALUE);
    private static final float TICK_SECONDS = 1f / 30f;
    /** Gated launches into an opening must beat the control's share by this much (or both be all openings). */
    private static final float OPENING_LEAD = 0.1f;

    private record Spec(String hull, String variant) {}

    private record Loop(String id, List<Spec> west, List<Spec> east, boolean windows, String control) {}

    private static final List<Spec> FLEET_WEST = List.of(new Spec("enforcer", "enforcer_Balanced"),
            new Spec("shrike", "shrike_Attack"), new Spec("vigilance", "vigilance_Strike"));
    private static final List<Spec> FLEET_EAST = List.of(new Spec("medusa", "medusa_Starting"),
            new Spec("hammerhead", "hammerhead_Balanced"), new Spec("vigilance", "vigilance_Strike"));
    private static final List<Spec> PACK = List.of(new Spec("enforcer", "enforcer_Balanced"),
            new Spec("enforcer", "enforcer_Balanced"));
    private static final List<Spec> PACK_TARGET = List.of(new Spec("dominator", "dominator_Support"));

    private static final List<Loop> LOOPS = List.of(
            new Loop("fleet-control", FLEET_WEST, FLEET_EAST, false, null),
            new Loop("fleet", FLEET_WEST, FLEET_EAST, true, "fleet-control"),
            new Loop("pack-control", PACK, PACK_TARGET, false, null),
            new Loop("pack", PACK, PACK_TARGET, true, "pack-control"));

    @Override public String id() { return "warship-missile-exchange"; }

    @Override public String label() {
        return "Warship missile exchange: launches into windows, point defence on the salvos, magazines as a resource";
    }

    @Override public int frameDelayMillis() { return 66; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        String only = System.getProperty("battle.scene.missileExchange.only");
        List<String> wanted = only == null || only.isBlank() ? null : List.of(only.split(","));
        List<SceneReport> reports = new ArrayList<>();
        Map<String, Result> played = new LinkedHashMap<>();
        for (Loop loop : LOOPS) {
            boolean asked = wanted == null || wanted.contains(loop.id());
            boolean needed = wanted != null && LOOPS.stream().anyMatch(l -> loop.id().equals(l.control())
                    && wanted.contains(l.id()));
            if (!asked && !needed) continue;
            Result r = playOne(loop, asked ? frames : FrameSink.NONE);
            played.put(loop.id(), r);
            reports.add(report(loop, r, loop.control() == null ? null : played.get(loop.control())));
        }
        return reports;
    }

    private record Result(Trace trace, BattleSimulation sim) {}

    private Result playOne(Loop loop, FrameSink frames) throws Exception {
        List<Warship> west = fit(loop.west(), Faction.ALLY, 270f);
        List<Warship> east = fit(loop.east(), Faction.DEFENDER, 90f);
        float reach = 0f;
        float length = 0f;
        for (Warship s : concat(west, east)) {
            reach = Math.max(reach, WarshipShipFire.longestReach(s));
            length = Math.max(length, s.lengthCells);
        }
        float start = 1.3f * reach + 2f * length;
        int width = Math.round(start + 6f * length + 60f);
        int height = Math.round(Math.max(2.2f * reach + 4f * length, 200f));
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(width - 4, height - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261019L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        place(west, width * 0.5f - start * 0.5f, height * 0.5f, length);
        place(east, width * 0.5f + start * 0.5f, height * 0.5f, length);
        for (Warship s : concat(west, east)) sim.addWarship(s);
        sim.warships().shipFire().missileWindows(loop.windows());
        Trace trace = new Trace(west, east);
        SceneRun run = SceneRun.of(world).observe(trace);
        if (frames != FrameSink.NONE) {
            run.frames(new Window(frames), loop.id(), FRAME_EVERY, tick -> trace.caption(loop.id(), tick));
        }
        run.run(TICKS);
        return new Result(trace, sim);
    }

    /** Passes on only the frames inside the review's tick window, so a review can be cut to the missile exchange. */
    private record Window(FrameSink inner) implements FrameSink {
        @Override public void frame(String loopId, BattleSimulation sim, int tick, String caption) {
            inner.frame(loopId, sim, tick, caption);
        }

        @Override public void advanced(String loopId, BattleSimulation sim, int tick) {
            inner.advanced(loopId, sim, tick);
        }

        @Override public boolean records(String loopId, BattleSimulation sim, int tick, int sceneEveryTicks) {
            return tick >= FRAME_FROM && tick <= FRAME_TO && inner.records(loopId, sim, tick, sceneEveryTicks);
        }
    }

    private static List<Warship> fit(List<Spec> specs, Faction side, float facing) throws Exception {
        List<Warship> out = new ArrayList<>();
        for (Spec s : specs) out.add(Warship.fit(s.hull(), s.variant(), side, 0f, 0f, facing));
        return out;
    }

    private static void place(List<Warship> ships, float x, float midY, float length) {
        float spacing = 1.5f * length;
        for (int i = 0; i < ships.size(); i++) {
            Warship s = ships.get(i);
            s.x = x;
            s.y = midY + (i - (ships.size() - 1) * 0.5f) * spacing;
            s.stationTo(s.x, s.y);
            s.limitFlux();
            s.useDoctrine();
            s.shipFire(ShipFireOrder.ALL);
            s.groundFire(Warship.GroundFire.HOLD);
        }
    }

    private static List<Warship> concat(List<Warship> a, List<Warship> b) {
        List<Warship> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private SceneReport report(Loop loop, Result r, Result control) {
        Trace t = r.trace();
        WarshipShipFire fire = r.sim().warships().shipFire();
        WarshipPointDefence pd = r.sim().warships().pointDefence();
        List<Verdict> verdicts = new ArrayList<>();
        int gated = fire.gatedLaunches();
        int openers = fire.openerLaunches();
        float openingShare = openers == 0 ? 0f : fire.openerIntoOpening() / (float) openers;
        if (loop.windows()) {
            verdicts.add(Verdict.of("gated-launches-fit-their-role",
                    gated > 0 && fire.gatedFittingRole() == gated,
                    String.format(Locale.ROOT, "%d of %d launches by torpedo, finisher, anti-shield and saturation "
                            + "launchers went into a window their role can use; by window %s",
                            fire.gatedFittingRole(), gated, fire.launchesByWindow())));
            if (control != null) {
                WarshipShipFire cf = control.sim().warships().shipFire();
                int cg = cf.openerLaunches();
                float controlShare = cg == 0 ? 0f : cf.openerIntoOpening() / (float) cg;
                verdicts.add(Verdict.of("launches-wait-for-openings",
                        cg == 0 || openingShare >= controlShare + OPENING_LEAD
                                || controlShare >= 1f - OPENING_LEAD && openingShare >= controlShare,
                        String.format(Locale.ROOT, "%.0f%% of %d torpedo, finisher and saturation launches into an "
                                        + "opening against the control's %.0f%% of %d (control by window %s)",
                                100f * openingShare, openers, 100f * controlShare, cg, cf.launchesByWindow())));
            }
            if (loop.id().startsWith("pack")) {
                int joined = fire.coordinatedLaunches();
                verdicts.add(Verdict.of("the-pack-launches-together", joined > 0,
                        String.format(Locale.ROOT, "%d launches joined a packmate's salvo or struck while the tank "
                                + "held aggro; %d salvos opened", joined, fire.salvosOpened())));
            }
        }
        // Point defence is judged only where missiles were sent at a hull that carries some.
        verdicts.add(Verdict.of("point-defence-engages-missiles",
                t.missilesAtDefendedHull == 0 || pd.hullMissileEngagements() > 0,
                String.format(Locale.ROOT, "%d engagements at missiles and warheads aimed at hulls, %d destroyed; %d "
                                + "missiles sent at a hull carrying point defence", pd.hullMissileEngagements(),
                        pd.hullMissileIntercepts(), t.missilesAtDefendedHull)));
        verdicts.add(Verdict.of("magazines-are-never-exceeded", t.magazineFaults == 0 && t.missileMounts > 0,
                String.format(Locale.ROOT, "%d missile mounts checked every tick, %d faults%s; %d rounds reloaded",
                        t.missileMounts, t.magazineFaults, t.firstFault == null ? "" : " (" + t.firstFault + ")",
                        t.reloaded())));

        Map<String, Number> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : fire.launchesByWindow().entrySet()) {
            metrics.put("launches." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : fire.launchesByRole().entrySet()) {
            metrics.put("role." + e.getKey(), e.getValue());
        }
        metrics.put("gatedLaunches", gated);
        metrics.put("openerLaunches", openers);
        metrics.put("openerIntoOpeningPercent", Math.round(100f * openingShare));
        metrics.put("coordinatedLaunches", fire.coordinatedLaunches());
        metrics.put("salvosOpened", fire.salvosOpened());
        metrics.put("missilesFired", fire.missilesFired());
        metrics.put("missilesShotDown", fire.missilesShotDown());
        metrics.put("missilesOnHull", fire.missilesOnHull());
        metrics.put("missilesStrayed", fire.missilesStrayed());
        metrics.put("warheadsReleased", fire.warheadsReleased());
        metrics.put("warheadsOnHull", fire.warheadsOnHull());
        metrics.put("warheadsShotDown", fire.warheadsShotDown());
        int bodies = fire.missilesOnHull() + fire.missilesStrayed() + fire.missilesShotDown() + fire.warheadsOnHull()
                + fire.warheadsStrayed() + fire.warheadsShotDown();
        metrics.put("missileHitPercent", bodies == 0 ? 0
                : Math.round(100f * (fire.missilesOnHull() + fire.warheadsOnHull()) / bodies));
        metrics.put("missileInterceptPercent", bodies == 0 ? 0
                : Math.round(100f * (fire.missilesShotDown() + fire.warheadsShotDown()) / bodies));
        metrics.put("pdEngagements", pd.hullMissileEngagements());
        metrics.put("pdIntercepts", pd.hullMissileIntercepts());
        metrics.put("reloaded", t.reloaded());
        metrics.put("ammoLeftWest", t.ammoLeft(t.west));
        metrics.put("ammoLeftEast", t.ammoLeft(t.east));
        metrics.put("hullLostWest", Math.round(t.hullLost(t.west)));
        metrics.put("hullLostEast", Math.round(t.hullLost(t.east)));
        metrics.put("lostWest", t.lost(t.west));
        metrics.put("lostEast", t.lost(t.east));
        metrics.put("firstLaunchSeconds", t.firstLaunchTick < 0 ? -1 : Math.round(t.firstLaunchTick * TICK_SECONDS));
        metrics.put("decidedSeconds", t.decidedTick < 0 ? -1 : Math.round(t.decidedTick * TICK_SECONDS));
        return new SceneReport(id(), loop.id(), TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final List<Warship> west;
        final List<Warship> east;
        final List<Warship> all;
        int missileMounts;
        int magazineFaults;
        String firstFault;
        int firstLaunchTick = -1;
        int missilesAtDefendedHull;
        private final Set<WarshipShell> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int decidedTick = -1;
        private final Map<Warship.Mount, Integer> firstShots = new IdentityHashMap<>();

        Trace(List<Warship> west, List<Warship> east) {
            this.west = west;
            this.east = east;
            this.all = concat(west, east);
            for (Warship s : all) for (Warship.Mount m : s.mounts()) if (m.weapon.missile()) missileMounts++;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (Warship s : all) {
                for (Warship.Mount m : s.mounts()) {
                    if (!m.weapon.missile()) continue;
                    if (m.shotsFired > 0 && firstLaunchTick < 0) firstLaunchTick = tick;
                    int max = m.ammoMax();
                    if (max <= 0) continue;
                    // The magazine is set when the ship turns on a hull; a shot before that is ground fire, held here.
                    firstShots.putIfAbsent(m, m.shotsFired);
                    int spent = m.shotsFired - firstShots.get(m);
                    boolean fault = m.ammoRemaining() < 0 || m.ammoRemaining() > max || spent > max + m.reloaded();
                    if (fault) {
                        magazineFaults++;
                        if (firstFault == null) {
                            firstFault = String.format(Locale.ROOT, "%s %s at tick %d: %d left of %d, %d spent, %d "
                                    + "reloaded", s.hullId, m.weapon.id(), tick, m.ammoRemaining(), max, spent,
                                    m.reloaded());
                        }
                    }
                }
            }
            for (WarshipShell shell : sim.warships().shells()) {
                Warship at = shell.hullTarget();
                if (at == null || !shell.weapon.missile() || !seen.add(shell)) continue;
                for (Warship.Mount m : at.mounts()) {
                    if (m.pointDefence()) {
                        missilesAtDefendedHull++;
                        break;
                    }
                }
            }
            if (decidedTick < 0 && (lost(west) == west.size() || lost(east) == east.size())) decidedTick = tick;
        }

        int reloaded() {
            int n = 0;
            for (Warship s : all) for (Warship.Mount m : s.mounts()) n += m.reloaded();
            return n;
        }

        int ammoLeft(List<Warship> side) {
            int n = 0;
            for (Warship s : side) {
                if (s.destroyed()) continue;
                for (Warship.Mount m : s.mounts()) if (m.weapon.missile() && m.ammoRemaining() > 0) n += m.ammoRemaining();
            }
            return n;
        }

        float hullLost(List<Warship> side) {
            float n = 0f;
            for (Warship s : side) n += s.maxHitPoints - Math.max(0f, s.hitPoints());
            return n;
        }

        int lost(List<Warship> side) {
            int n = 0;
            for (Warship s : side) if (s.destroyed()) n++;
            return n;
        }

        String caption(String loop, int tick) {
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "%s / %.0fs", loop, tick * TICK_SECONDS));
            for (Warship s : all) {
                if (s.destroyed()) {
                    b.append(" / ").append(s.hullId).append(" down");
                    continue;
                }
                int ammo = 0;
                for (Warship.Mount m : s.mounts()) if (m.weapon.missile() && m.ammoRemaining() > 0) ammo += m.ammoRemaining();
                b.append(String.format(Locale.ROOT, " / %s %.0f%% f%.0f%%%s%s m%d", s.hullId, 100f * s.hullFraction(),
                        100f * s.fluxFraction(), s.overloaded() ? " OVL" : s.shieldUp() ? " sh" : "",
                        s.manoeuvre().role == WarshipManoeuvre.Role.STRIKER ? " striker"
                                : s.manoeuvre().role == WarshipManoeuvre.Role.TANK ? " tank" : "", ammo));
            }
            return b.toString();
        }
    }
}
