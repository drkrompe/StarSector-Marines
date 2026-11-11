package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.derelict.BuildQueue;
import com.dillon.starsectormarines.battle.derelict.ClaimLattice;
import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.HaulerWork;
import com.dillon.starsectormarines.battle.derelict.Salvage;
import com.dillon.starsectormarines.battle.derelict.StakeTrain;
import com.dillon.starsectormarines.battle.derelict.StripperWork;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.LongSurveyGround;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The fallen hull's rules in motion, recorded through the look preset at 1920x1080 as MP4s with their captions burned
 * in, for the owner's picks (`long-survey-arc.md`). Each clip is played once unrecorded to find its moment, then
 * again and recorded round it; the battle is never steered, posed or judged, and only the stake clip's relay cut is
 * made by hand (the company's charge on a node, which no one plays here).
 *
 * <ul>
 *   <li>{@code forge}: The Fall as launched (LS1, its owned seed): the forge hall roof off, a forge cycle from eight
 *       seconds before a print to the printed walker walking out of the bay, real time.</li>
 *   <li>{@code watch}: The Fall: from a few seconds before the hull's watch first fires into the town, following the
 *       fight, real time.</li>
 *   <li>{@code stripper}, {@code hauler}: the Stakes chapter's ground stood up with the hull's rules installed as The
 *       Fall installs them, a stripper and a hauler at the bay mouth and stand-in salvage piles beside the town's
 *       buildings at the haul roads' far ends (no ground lays salvage yet): a stripper walking to a pile and cutting a
 *       load, and a hauler loading at it and walking the haul road home while the hull's store rises.</li>
 *   <li>{@code stake}: the same ground with the survey program and a store that pays for a stake: the train carrying
 *       it out, planted, raised, established, then the node its relay runs through cut and the stake stranded.</li>
 * </ul>
 *
 * <p>A clip longer than about twenty seconds plays at a stated whole-number speed-up. Opt-in:
 * {@code -Dsnapshot.hullVideo.clips} (all five by default), {@code -Dsnapshot.hullVideo.option} names the option
 * drawn (with {@code -Dsnapshot.artOverlay} for an option that has not shipped) and is added to each file name.
 */
public final class LongSurveyHullVideoSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final float DT = BattleSimulation.TICK_DT;
    /** About how long a clip runs on screen; longer moments play faster, by a whole number. */
    private static final float CLIP_SECONDS = 18f;
    private static final float PILE_METAL = 400f;

    @Override public String id() { return "long-survey-hull-video"; }

    @Override public String label() { return "The fallen hull's rules in motion: forge, strippers, haulers, stakes, the watch"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        TileRegistryTestInstaller.install(context.projectRoot());
        Set<String> clips = new HashSet<>(Arrays.asList(System.getProperty("snapshot.hullVideo.clips",
                "forge,watch,stripper,hauler,stake").split(",")));
        String option = System.getProperty("snapshot.hullVideo.option", "").trim();
        String suffix = option.isEmpty() ? "" : "-" + option;
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = new HeadlessStrings(context.modRoot())) {
            if (clips.contains("forge") || clips.contains("watch")) fall(context, clips, suffix, out);
            if (clips.contains("stripper") || clips.contains("hauler")) work(context, clips, suffix, out);
            if (clips.contains("stake")) stake(context, suffix, out);
        }
        return out;
    }

    // ---- The Fall ----

    private static void fall(SnapshotContext context, Set<String> clips, String suffix, List<SnapshotArtifact> out)
            throws Exception {
        int maxTicks = Math.round(Float.parseFloat(System.getProperty("snapshot.hullVideo.fallSeconds", "900")) / DT);
        int printTick = -1, watchTick = -1;
        // First pass, unrecorded: the first print, and the first time the watch fires into the town.
        try (var prepared = launchFall()) {
            BattleSimulation sim = prepared.simulation();
            FallenHull hull = sim.derelictSecurity() == null ? null : sim.derelictSecurity().hull();
            if (hull == null) {
                log("no fallen hull stands in The Fall");
                return;
            }
            Faction watch = sim.identity().faction(hull.forgeId());
            FallenHullSite.Rect box = hull.site().hull();
            Deque<Integer> townShots = new ArrayDeque<>();
            Map<String, Integer> tally = new TreeMap<>();
            int printed = hull.forge().printed();
            for (int t = 1; t <= maxTicks && (printTick < 0 || watchTick < 0); t++) {
                if (!step(sim, null)) break;
                if (printTick < 0 && hull.forge().printed() > printed) {
                    printTick = t;
                    log("first print at %.1f s", t * DT);
                }
                for (ShotEvent shot : sim.getShotsThisFrame()) {
                    if (shot.shooterFaction == watch && box.chebyshev((int) shot.toX, (int) shot.toY) > 6) townShots.add(t);
                    tally.merge(shot.shooterFaction + (box.chebyshev((int) shot.toX, (int) shot.toY) > 6 ? " town" : " hull"),
                            1, Integer::sum);
                }
                while (!townShots.isEmpty() && townShots.peekFirst() < t - Math.round(2f / DT)) townShots.pollFirst();
                if (watchTick < 0 && townShots.size() >= 6) {
                    watchTick = t;
                    log("the watch fires into the town at %.1f s", t * DT);
                }
                if (t % Math.round(30f / DT) == 0) {
                    log("%.0f s: printed %d, store %.0f, walkers %d, rounds %s", t * DT, hull.forge().printed(),
                            hull.hullStore().metal(), sim.derelictSecurity().walkers().size(), tally);
                }
            }
        }
        if (clips.contains("forge")) {
            if (printTick < 0) log("no print: no forge clip");
            else forgeClip(context, printTick, suffix, out);
        }
        if (clips.contains("watch")) {
            if (watchTick < 0) log("the watch never fired into the town: no watch clip");
            else watchClip(context, watchTick, suffix, out);
        }
    }

    /** The second print, thirty seconds after the first: from eight seconds before it to the walker out of the bay. */
    private static void forgeClip(SnapshotContext context, int firstPrint, String suffix, List<SnapshotArtifact> out)
            throws Exception {
        int print = firstPrint + Math.round(30f / DT);
        int from = print - Math.round(8f / DT), to = print + Math.round(14f / DT);
        try (var prepared = launchFall()) {
            BattleSimulation sim = prepared.simulation();
            FallenHull hull = sim.derelictSecurity().hull();
            DerelictSecurityDirector director = sim.derelictSecurity();
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            playTo(sim, fx, from);
            World world = sim.world();
            float fx0 = world.renderX(hull.forgeId()), fy0 = world.renderY(hull.forgeId());
            float mx = hull.site().mouthX() + .5f, my = hull.site().mouthY() + .5f;
            Set<Long> before = members(sim, director);
            int printedBefore = hull.forge().printed();
            Camera camera = new Camera(fx0 * .4f + mx * .6f, fy0 * .4f + my * .6f, frame(fx0, fy0, mx, my, 30f) * .8f);
            Take take = Take.open(context, fx, "forge-cycle" + suffix, 1, 2, camera.now(), false);
            long printedId = 0L;
            String printedFrame = null;
            for (int t = from; t <= to; t++) {
                if (t > from && !step(sim, fx)) break;
                if (printedId == 0L && hull.forge().printed() > printedBefore) {
                    for (DerelictSecurityDirector.Walker w : director.walkers()) {
                        for (int i = 0; i < sim.squadMemberCount(w.squadId()); i++) {
                            long id = sim.squadMemberAt(w.squadId(), i);
                            if (before.contains(id)) continue;
                            printedId = id;
                            printedFrame = w.frame().name().toLowerCase(Locale.ROOT);
                        }
                    }
                    if (printedId != 0L) log("printed %s (%d) at %.1f s", printedFrame, printedId, t * DT);
                }
                if ((t - from) % take.every != 0) continue;
                if (printedId != 0L && world.isAlive(printedId)) {
                    float wx = world.renderX(printedId), wy = world.renderY(printedId);
                    camera.toward(fx0 * .25f + wx * .75f, fy0 * .25f + wy * .75f, 30f);
                }
                float left = (print - t) * DT;
                List<String> lines = new ArrayList<>();
                lines.add(String.format("autoforge %s, prints every 30 s: %s", hull.forge().lit() ? "lit" : "out",
                        left > 0 ? String.format("next print in %.0f s", Math.ceil(left)) : "printed"));
                lines.add(String.format("hull store %.0f metal, %d printed", hull.hullStore().metal(),
                        hull.forge().printed()));
                if (printedFrame != null) lines.add("the printed " + printedFrame + " walks out of the bay mouth");
                take.frame(sim, camera.now(), "Forge cycle - The Fall, real time", lines);
                if (Math.abs(left) < DT) take.keep();
            }
            out.addAll(take.finish());
        }
    }

    /** The watch's fight in the town, from four seconds before its first volley there, following the shots. */
    private static void watchClip(SnapshotContext context, int volley, String suffix, List<SnapshotArtifact> out)
            throws Exception {
        int from = Math.max(1, volley - Math.round(6f / DT)), to = from + Math.round(20f / DT);
        try (var prepared = launchFall()) {
            BattleSimulation sim = prepared.simulation();
            FallenHull hull = sim.derelictSecurity().hull();
            Faction watch = sim.identity().faction(hull.forgeId());
            FallenHullSite.Rect box = hull.site().hull();
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            playTo(sim, fx, from);
            float sx = 0f, sy = 0f;
            int n = 0;
            Camera camera = null;
            Take take = null;
            int rounds = 0, down = 0;
            Deque<float[]> recent = new ArrayDeque<>();
            for (int t = from; t <= to; t++) {
                if (t > from && !step(sim, fx)) break;
                for (ShotEvent shot : sim.getShotsThisFrame()) {
                    if (shot.shooterFaction == watch) {
                        rounds++;
                        if (box.chebyshev((int) shot.toX, (int) shot.toY) > 6) recent.add(new float[]{shot.toX, shot.toY, t});
                    }
                }
                for (long id : sim.getDeathsThisFrame()) if (sim.identity().faction(id) != watch) down++;
                while (!recent.isEmpty() && recent.peekFirst()[2] < t - Math.round(3f / DT)) recent.pollFirst();
                sx = 0f;
                sy = 0f;
                n = 0;
                for (float[] r : recent) {
                    sx += r[0];
                    sy += r[1];
                    n++;
                }
                if (camera == null) {
                    // Until the first volley, the camera waits where the town meets the hull's nearest edge.
                    camera = new Camera(n > 0 ? sx / n : box.left() + (box.right() - box.left()) / 2f,
                            n > 0 ? sy / n : box.bottom(), 56f);
                    take = Take.open(context, fx, "watch-fight" + suffix, 1, 2, camera.now(), true);
                }
                if ((t - from) % take.every != 0) continue;
                if (n > 0) camera.toward(sx / n, sy / n, 56f);
                take.frame(sim, camera.now(), "The watch in the town - The Fall, real time", List.of(
                        "the hull's walkers and flocks against the colony's defenders",
                        String.format("watch rounds fired %d, their side down %d", rounds, down)));
                if (t == volley + Math.round(4f / DT)) take.keep();
            }
            if (take != null) out.addAll(take.finish());
        }
    }

    private static MissionLaunch.PreparedBattle launchFall() {
        var mission = DebugMissionCatalog.Scenario.LONG_SURVEY_FALL.mission();
        var ops = new MarineOpsContext(null);
        ops.resetDebugPreset(mission);
        var preset = DebugMissionCatalog.preset(mission);
        var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
        var plan = MissionLaunch.captureScenario(ops, mission, lift, FlybyRoster.EMPTY, List.of(), List.of(), null,
                mission.battleSeed);
        return MissionLaunch.finishSimulation(ops, mission, FlybyRoster.EMPTY, plan, plan.builder().get());
    }

    // ---- The claim: the Stakes chapter's ground ----

    /** The Stakes chapter's ground stood up with the hull's rules on it, playing with no winner. */
    private record Claim(BattleSimulation sim, FallenHull hull) implements AutoCloseable {
        @Override public void close() { sim.close(); }
    }

    private static Claim claim(List<DerelictWalkerFrame> walkers, BuildQueue queue, float feedstock) {
        StoryMap chapter = StoryMap.LONG_SURVEY_STAKES;
        SurfacePalette home = StoryMapPools.pool(chapter).home();
        LongSurveyGround.Ground ground = LongSurveyGround.ground(chapter, home);
        BattleSimulation sim = LongSurveyGround.stand(ground, home);
        sim.setMissionCompletionEnabled(false);
        FallenHullSite probe = LongSurveyGround.rulesSite(ground.hull(), List.of(), List.of(), 0f);
        List<FallenHullSite.Posting> postings = new ArrayList<>();
        for (DerelictWalkerFrame f : walkers) postings.add(new FallenHullSite.Posting(f, probe.mouthX(), probe.mouthY()));
        FallenHull hull = FallenHull.install(sim, LongSurveyGround.rulesSite(ground.hull(), claimPiles(sim, probe),
                postings, feedstock), queue, FlockLivery.DOMAIN);
        if (hull == null) {
            sim.close();
            throw new IllegalStateException("the hull would not stand on " + chapter);
        }
        return new Claim(sim, hull);
    }

    /** A program the store cannot pay for, so the store's metal stays where the haulers put it. */
    private static BuildQueue idle() {
        return new BuildQueue(List.of(BuildQueue.Item.walker(DerelictWalkerFrame.LINE, 1.0e9f)));
    }

    /**
     * A stripper walking to salvage and cutting a load; a hauler loading at it and walking the haul road home. The
     * first pass times them, for each clip's speed; the second starts and stops each on what it sees.
     */
    private static void work(SnapshotContext context, Set<String> clips, String suffix, List<SnapshotArtifact> out)
            throws Exception {
        List<DerelictWalkerFrame> walkers = List.of(DerelictWalkerFrame.STRIPPER, DerelictWalkerFrame.HAULER);
        int maxTicks = Math.round(600f / DT);
        int full = -1, load = -1, home = -1;
        try (Claim c = claim(walkers, idle(), 0f)) {
            for (int t = 1; t <= maxTicks && home < 0; t++) {
                if (!step(c.sim, null)) break;
                StripperWork s = first(c.sim, c.hull.strippers());
                HaulerWork h = first(c.sim, c.hull.haulers());
                if (s != null && full < 0 && s.load() >= StripperWork.CAPACITY) full = t;
                if (h != null && load < 0 && h.load() > 0f) load = t;
                if (load >= 0 && c.hull.hullStore().metal() > 0f) home = t;
                if (h != null && t % Math.round(30f / DT) == 0) {
                    HaulerWork.Goal g = h.goal();
                    log("%.0f s: hauler %s with %d at (%.1f, %.1f), load %.0f; forge lit %s", t * DT, g.kind(),
                            g.with(), g.x(), g.y(), h.load(), c.hull.forge().lit());
                }
            }
            log("stripper full %.1f s; hauler loads %.1f s, home %.1f s", full * DT, load * DT, home * DT);
        }
        if (load < 0 && full < 0) {
            log("the stripper never filled and the hauler never loaded: no work clips");
            return;
        }
        // -Dsnapshot.hullVideo.workCells frames the work clips that close (cells across), and
        // -Dsnapshot.hullVideo.workSpeed caps their speed-up, so the walkers' arms read.
        float sCells = Float.parseFloat(System.getProperty("snapshot.hullVideo.workCells", "30"));
        float hCells = Float.parseFloat(System.getProperty("snapshot.hullVideo.workCells", "40"));
        int cap = Integer.parseInt(System.getProperty("snapshot.hullVideo.workSpeed", "99"));
        int sSpeed = Math.min(cap, speedFor((full > 0 ? full : load) + Math.round(3f / DT)));
        int hSpeed = Math.min(cap, home > 0 ? speedFor(home - load + Math.round(6f / DT)) : 4);
        boolean wantS = clips.contains("stripper"), wantH = clips.contains("hauler");
        try (Claim c = claim(walkers, idle(), 0f)) {
            BattleSimulation sim = c.sim;
            World world = sim.world();
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            Take st = null, ht = null;
            Camera sc = null, hc = null;
            int sEnd = -1, hEnd = -1, hStart = -1;
            float lastLoad = 0f;
            for (int t = 1; t <= maxTicks; t++) {
                if (!step(sim, fx)) break;
                long stripper = firstId(sim, c.hull.strippers());
                long hauler = firstId(sim, c.hull.haulers());
                StripperWork s = first(sim, c.hull.strippers());
                HaulerWork h = first(sim, c.hull.haulers());
                // The stripper from its walk out until its load is first full or first handed on, and two seconds more.
                if (wantS && sEnd < 0 && s != null && (s.load() >= StripperWork.CAPACITY || s.load() < lastLoad)) {
                    sEnd = t + Math.round(2f / DT);
                }
                if (s != null) lastLoad = s.load();
                if (wantS && stripper != 0L && (sEnd < 0 || t <= sEnd) && t % sSpeed == 0) {
                    float x = world.renderX(stripper), y = world.renderY(stripper);
                    if (s != null && s.goal().kind() == StripperWork.Kind.CUT) {
                        // Cutting: the walker and the salvage its arms reach to, both in frame.
                        x = (x + s.goal().x()) / 2f;
                        y = (y + s.goal().y()) / 2f;
                    }
                    if (st == null) {
                        sc = new Camera(x, y, sCells);
                        st = Take.open(context, fx, "stripper-cuts" + suffix, sSpeed, sSpeed, sc.now(), true);
                    }
                    sc.toward(x, y, sCells);
                    Salvage pile = nearestPile(c.hull, world.x(stripper), world.y(stripper));
                    boolean cutting = pile != null && Math.max(Math.abs(pile.x - world.x(stripper)),
                            Math.abs(pile.y - world.y(stripper))) <= StripperWork.REACH_CELLS + .5f
                            && s != null && s.load() < StripperWork.CAPACITY;
                    st.frame(sim, sc.now(), "Stripper - The Stakes ground" + speedNote(sSpeed), List.of(
                            s == null ? "" : s.load() >= StripperWork.CAPACITY ? "full: it waits for a hauler"
                                    : cutting ? "cutting the salvage, 4 metal a second" : "walking to the nearest salvage on the claim",
                            String.format("its load %.0f of %.0f metal%s", s == null ? 0f : s.load(), StripperWork.CAPACITY,
                                    pile == null ? "" : String.format("; the pile holds %.0f", pile.metal())),
                            "the scrap heaps are stand-in salvage (the ground lays none yet)"));
                    if (cutting && st.kept == null && s.load() > StripperWork.CAPACITY / 2f) st.keep();
                }
                // The hauler from its first load until the hull's store has it and its arms are tucked again.
                if (wantH && hStart < 0 && h != null && h.load() > 0f) hStart = t;
                if (wantH && hStart > 0 && hEnd < 0 && c.hull.hullStore().metal() > 0f) hEnd = t + Math.round(4.5f / DT);
                if (wantH && hauler != 0L && hStart > 0 && (hEnd < 0 || t <= hEnd) && (t - hStart) % hSpeed == 0) {
                    float x = world.renderX(hauler), y = world.renderY(hauler);
                    if (ht == null) {
                        hc = new Camera(x, y, hCells);
                        ht = Take.open(context, fx, "hauler-hauls" + suffix, hSpeed, hSpeed, hc.now(), true);
                    }
                    hc.toward(x, y, hCells);
                    boolean atStripper = stripper != 0L && Math.hypot(world.x(stripper) - world.x(hauler),
                            world.y(stripper) - world.y(hauler)) < 4f;
                    String doing = h == null ? "" : c.hull.hullStore().metal() > 0f && h.load() == 0f
                            ? "unloaded into the hull's store at the bay mouth"
                            : h.load() >= HaulerWork.CAPACITY ? "full: walking the haul road home to the bay mouth"
                            : atStripper ? "loading at the stripper, until it is full"
                            : "waiting on the stripper's next load";
                    ht.frame(sim, hc.now(), "Hauler - The Stakes ground" + speedNote(hSpeed), List.of(doing,
                            String.format("hauler load %.0f of %.0f metal; the forge's feedstock (hull store) %.0f metal",
                                    h == null ? 0f : h.load(), HaulerWork.CAPACITY, c.hull.hullStore().metal())));
                    if (ht.kept == null && h != null && h.load() >= HaulerWork.CAPACITY && !atStripper) ht.keep();
                }
                if ((!wantS || sEnd > 0 && t > sEnd) && (!wantH || hEnd > 0 && t > hEnd)) break;
            }
            if (st != null) out.addAll(st.finish());
            if (ht != null) out.addAll(ht.finish());
        }
    }

    /** A stake from the train carrying it out to established, then the node its relay runs through cut. */
    private static void stake(SnapshotContext context, String suffix, List<SnapshotArtifact> out) throws Exception {
        List<DerelictWalkerFrame> walkers = List.of(DerelictWalkerFrame.HAULER, DerelictWalkerFrame.ROUNDS);
        float store = BuildQueue.STAKE_COST * 3f;
        int maxTicks = Math.round(900f / DT);
        int train = -1, planted = -1, established = -1, point = -1;
        try (Claim c = claim(walkers, BuildQueue.survey(), store)) {
            Set<Integer> starting = new HashSet<>(c.hull.stakes().keySet());
            log("starting stakes %s", starting);
            for (int t = 1; t <= maxTicks && established < 0; t++) {
                if (!step(c.sim, null)) break;
                if (train < 0 && !c.hull.trains().isEmpty()) train = t;
                for (Map.Entry<Integer, SurveyStake> e : c.hull.stakes().entrySet()) {
                    if (starting.contains(e.getKey())) continue;
                    if (point < 0) {
                        point = e.getKey();
                        planted = t;
                    }
                    if (e.getKey() == point && e.getValue().stage() == SurveyStake.Stage.ESTABLISHED) established = t;
                }
            }
            log("train %.1f s, stake %d planted %.1f s, established %.1f s", train * DT, point, planted * DT,
                    established * DT);
        }
        if (planted < 0 || established < 0) {
            log("no stake was planted and established: no stake clip");
            return;
        }
        int from = Math.max(1, planted - Math.round(15f / DT));
        int cutAt = established + Math.round(4f / DT), to = cutAt + Math.round(10f / DT);
        int speed = speedFor(to - from);
        try (Claim c = claim(walkers, BuildQueue.survey(), store)) {
            BattleSimulation sim = c.sim;
            World world = sim.world();
            ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
            playTo(sim, fx, from);
            ClaimLattice.Point p = c.hull.lattice().point(point);
            Camera camera = new Camera(p.x() + .5f, p.y() + .5f, 40f);
            Take take = Take.open(context, fx, "stake-life" + suffix, speed, speed, camera.now(), true);
            String cutNote = null;
            for (int t = from; t <= to; t++) {
                if (t > from && !step(sim, fx)) break;
                SurveyStake stake = c.hull.stakes().get(point);
                if (t == cutAt && stake != null) {
                    int via = stake.parent != ClaimLattice.HULL && c.hull.stakes().containsKey(stake.parent)
                            ? stake.parent : point;
                    Long node = sim.derelictSecurity().nodes().get(FallenHull.zoneOf(via));
                    if (node != null) {
                        sim.applyExternalDamage(node, 1.0e6f, 1.0e6f);
                        cutNote = via == point ? "its own node destroyed (by hand, for the company's charge)"
                                : "the node of the stake its relay runs through destroyed (by hand, for the company's charge)";
                        log("cut node %d of point %d at %.1f s", node, via, t * DT);
                    }
                }
                if ((t - from) % take.every != 0) continue;
                float tx = p.x() + .5f, ty = p.y() + .5f, cells = 40f;
                if (stake == null) {
                    for (StakeTrain tr : c.hull.trains()) {
                        if (tr.plan.point().id() != point || sim.squadMemberCount(tr.haulerSquad) <= 0) continue;
                        long h = sim.squadMemberAt(tr.haulerSquad, 0);
                        float hx = world.renderX(h), hy = world.renderY(h);
                        tx = (hx + tx) / 2f;
                        ty = (hy + ty) / 2f;
                        cells = frame(hx, hy, p.x(), p.y(), 40f);
                    }
                } else if (stake.parent != ClaimLattice.HULL && c.hull.stakes().containsKey(stake.parent)) {
                    ClaimLattice.Point q = c.hull.lattice().point(stake.parent);
                    tx = (q.x() + p.x()) / 2f + .5f;
                    ty = (q.y() + p.y()) / 2f + .5f;
                    cells = frame(q.x(), q.y(), p.x(), p.y(), 44f);
                }
                camera.toward(tx, ty, cells);
                List<String> lines = new ArrayList<>();
                if (stake == null) {
                    lines.add("a printed stake carried out by its train to a lattice point");
                } else {
                    lines.add(String.format("stake %s%s", stake.stage().name().toLowerCase(Locale.ROOT),
                            stake.stage() == SurveyStake.Stage.ESTABLISHED || stake.stranded() ? ""
                                    : String.format(", %.0f%% to %s", 100f * stake.progress(),
                                    stake.stage() == SurveyStake.Stage.PLANTED ? "raised" : "established")));
                    lines.add(stake.stranded() ? "relay cut: stranded, it keeps what it has and builds nothing again"
                            : "relay linked back to the hull: it builds");
                }
                if (cutNote != null) lines.add(cutNote);
                take.frame(sim, camera.now(), "Survey stake - The Stakes ground" + speedNote(speed), lines);
                if (take.kept == null && stake != null && stake.stage() == SurveyStake.Stage.RAISED) take.keep();
            }
            out.addAll(take.finish());
        }
    }

    // ---- helpers ----

    /** The whole-number speed-up that plays {@code ticks} in about {@link #CLIP_SECONDS}. */
    private static int speedFor(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks * DT / CLIP_SECONDS));
    }

    private static String speedNote(int speed) {
        return speed == 1 ? ", real time" : ", " + speed + "x speed";
    }

    /** Cells across that hold two points with a margin, never under {@code min}. */
    private static float frame(float ax, float ay, float bx, float by, float min) {
        float span = Math.max(Math.abs(ax - bx), Math.abs(ay - by) * W / (float) H);
        return Math.max(min, span * 1.4f + 16f);
    }

    private static Set<Long> members(BattleSimulation sim, DerelictSecurityDirector director) {
        Set<Long> out = new HashSet<>();
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            for (int i = 0; i < sim.squadMemberCount(w.squadId()); i++) out.add(sim.squadMemberAt(w.squadId(), i));
        }
        return out;
    }

    private static <T> T first(BattleSimulation sim, Map<Integer, T> work) {
        for (Map.Entry<Integer, T> e : work.entrySet()) if (sim.squadMemberCount(e.getKey()) > 0) return e.getValue();
        return null;
    }

    private static long firstId(BattleSimulation sim, Map<Integer, ?> work) {
        for (Integer squad : work.keySet()) if (sim.squadMemberCount(squad) > 0) return sim.squadMemberAt(squad, 0);
        return 0L;
    }

    private static Salvage nearestPile(FallenHull hull, float x, float y) {
        Salvage best = null;
        double bestD = Double.MAX_VALUE;
        for (Salvage s : hull.salvage()) {
            double d = Math.hypot(s.x - x, s.y - y);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /**
     * Stand-in salvage piles on the claim (no ground lays salvage yet): clear ground beside a haul road near the edge
     * of the hull zone's reach, so a hauler walks the road home with the load; one a road, three at most.
     */
    private static List<FallenHullSite.Pile> claimPiles(BattleSimulation sim, FallenHullSite site) {
        int reach = site.perimeterCells() - 4;
        List<FallenHullSite.Pile> out = new ArrayList<>();
        for (int[] road : site.haulRoads()) {
            int[] best = null;
            int bestScore = Integer.MAX_VALUE;
            for (int i = 0; i < road.length / 2; i++) {
                int rx = road[2 * i], ry = road[2 * i + 1];
                int off = site.hull().chebyshev(rx, ry);
                if (off < reach - 8 || off > reach) continue;
                for (int y = ry - 3; y <= ry + 3; y++) {
                    for (int x = rx - 3; x <= rx + 3; x++) {
                        if (Math.max(Math.abs(x - rx), Math.abs(y - ry)) < 2 || !clear(sim, x, y)) continue;
                        int score = Math.abs(off - (reach - 3)) + (besideBuilding(sim, x, y) ? 0 : 4);
                        if (score < bestScore) {
                            bestScore = score;
                            best = new int[]{x, y};
                        }
                    }
                }
            }
            if (best == null) continue;
            boolean apart = true;
            for (FallenHullSite.Pile p : out) apart &= Math.max(Math.abs(p.x() - best[0]), Math.abs(p.y() - best[1])) > 8;
            if (apart) out.add(new FallenHullSite.Pile(best[0], best[1], PILE_METAL));
            if (out.size() >= 3) break;
        }
        log("piles %s", out);
        return out;
    }

    private static boolean clear(BattleSimulation sim, int cx, int cy) {
        for (int y = cy - 1; y <= cy + 1; y++) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) return false;
                if (sim.getTopology().getBuildingId(x, y) != 0 || sim.getTopology().isWall(x, y)) return false;
            }
        }
        return true;
    }

    private static boolean besideBuilding(BattleSimulation sim, int x, int y) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 2 : d == 1 ? -2 : 0), ny = y + (d == 2 ? 2 : d == 3 ? -2 : 0);
            if (sim.getGrid().inBounds(nx, ny) && sim.getTopology().getBuildingId(nx, ny) != 0) return true;
        }
        return false;
    }

    /** One tick, with roofs faded as the screen fades them and the impact effects fed; false once it stops. */
    private static boolean step(BattleSimulation sim, ReviewImpactFx fx) {
        int before = sim.getSimTickIndex();
        sim.advance(DT);
        // The screen fades roofs toward what the company sees.
        BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), DT);
        if (fx != null) fx.feed(sim);
        return sim.getSimTickIndex() != before;
    }

    /** Plays to tick {@code to}, feeding the effects only over the last two seconds. */
    private static void playTo(BattleSimulation sim, ReviewImpactFx fx, int to) {
        for (int t = 1; t < to; t++) {
            if (!step(sim, t >= to - Math.round(2f / DT) ? fx : null)) return;
        }
    }

    /** A camera that eases toward where it is told, so a following frame does not jump. */
    private static final class Camera {
        private float x, y, cells;

        Camera(float x, float y, float cells) {
            this.x = x;
            this.y = y;
            this.cells = cells;
        }

        void toward(float tx, float ty, float tc) {
            x += (tx - x) * .12f;
            y += (ty - y) * .12f;
            cells += (tc - cells) * .06f;
        }

        float[] now() {
            return new float[]{x, y, cells};
        }
    }

    /** One recording: frames through the look preset with the caption burned in, streamed into ffmpeg. */
    private static final class Take {
        final String name;
        final int every;
        final ReviewImpactFx fx;
        final FfmpegVideo encoder;
        BattleReviewFrameRenderer renderer;
        BufferedImage last, kept;
        int frames;
        /** Places ringed on the frame, in cells: stand-in salvage the ground does not draw. */
        List<float[]> marks = List.of();

        private Take(String name, int every, ReviewImpactFx fx, FfmpegVideo encoder, BattleReviewFrameRenderer renderer) {
            this.name = name;
            this.every = every;
            this.fx = fx;
            this.encoder = encoder;
            this.renderer = renderer;
        }

        /** A recording played at {@code speed} times real time, a frame every {@code every} ticks. */
        static Take open(SnapshotContext context, ReviewImpactFx fx, String name, int speed, int every, float[] focus,
                         boolean roofs) throws IOException {
            Path scratch = context.projectRoot().resolve("build/tmp/look-video");
            Files.createDirectories(scratch);
            Path file = Files.createTempFile(scratch, name + "-", ".mp4");
            FfmpegVideo encoder = FfmpegVideo.open(file, W, H, Math.round(speed / DT) + "/" + every);
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, focus[0],
                    focus[1], focus[2]);
            log("%s: %dx speed, a frame every %d ticks", name, speed, every);
            return new Take(name, every, fx, encoder, roofs ? renderer : renderer.withoutRoofs());
        }

        void frame(BattleSimulation sim, float[] focus, String title, List<String> lines) throws IOException {
            renderer = renderer.refocus(focus[0], focus[1], focus[2]);
            BufferedImage image = renderer.render(sim, "", ReviewAnnotations.NONE, fx);
            ring(image, focus, marks);
            caption(image, title, lines);
            encoder.add(image);
            last = image;
            frames++;
        }

        /** Keeps this frame as the clip's still. */
        void keep() {
            if (last == null) return;
            kept = new BufferedImage(last.getWidth(), last.getHeight(), BufferedImage.TYPE_INT_RGB);
            kept.getGraphics().drawImage(last, 0, 0, null);
        }

        List<SnapshotArtifact> finish() throws IOException {
            Path video = encoder.finish();
            encoder.close();
            log("%s: %d frames", name, frames);
            List<SnapshotArtifact> out = new ArrayList<>();
            out.add(SnapshotArtifact.video("look/" + name + ".mp4", video, List.of()));
            if (kept == null) keep();
            if (kept != null) out.add(new SnapshotArtifact("look/" + name + ".png", kept));
            return out;
        }
    }

    /** A dashed amber ring, two cells across, at each place, for a frame centred on {@code focus}. */
    private static void ring(BufferedImage image, float[] focus, List<float[]> places) {
        if (places.isEmpty()) return;
        float px = W / focus[2];
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{12f, 10f}, 0f));
            g.setColor(new Color(255, 190, 60, 230));
            int r = Math.round(px * 1.2f);
            for (float[] p : places) {
                int x = Math.round(W / 2f + (p[0] - focus[0]) * px), y = Math.round(H / 2f - (p[1] - focus[1]) * px);
                g.drawOval(x - r, y - r, 2 * r, 2 * r);
            }
        } finally {
            g.dispose();
        }
    }

    /** The caption, white on a dark plate in the frame's lower left. */
    private static void caption(BufferedImage image, String title, List<String> lines) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font big = new Font(Font.SANS_SERIF, Font.BOLD, 30), small = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
            List<String> shown = new ArrayList<>();
            for (String l : lines) if (l != null && !l.isEmpty()) shown.add(l);
            int width = g.getFontMetrics(big).stringWidth(title);
            for (String l : shown) width = Math.max(width, g.getFontMetrics(small).stringWidth(l));
            int height = 44 + 32 * shown.size() + 16;
            int x = 28, y = H - 28 - height;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRoundRect(x, y, width + 40, height, 14, 14);
            g.setColor(Color.WHITE);
            g.setFont(big);
            g.drawString(title, x + 20, y + 40);
            g.setFont(small);
            for (int i = 0; i < shown.size(); i++) g.drawString(shown.get(i), x + 20, y + 74 + 32 * i);
        } finally {
            g.dispose();
        }
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [long-survey-hull-video] " + format + "%n", args);
    }
}

