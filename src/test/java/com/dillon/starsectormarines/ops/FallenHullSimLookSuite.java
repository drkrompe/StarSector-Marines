package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.derelict.BuildQueue;
import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.HaulerWork;
import com.dillon.starsectormarines.battle.derelict.Salvage;
import com.dillon.starsectormarines.battle.derelict.StakeFootprint;
import com.dillon.starsectormarines.battle.derelict.StakeTrain;
import com.dillon.starsectormarines.battle.derelict.StripperWork;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.setup.LongSurveyGround;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.render2d.BattleCamera;
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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fallen hull's rules running on camera, for the owner's picks page: MP4s through the look preset, 1920x1080 at
 * 100% scale, the camera following each clip's subject, short labels burned in. Nothing is posed: every unit in frame
 * is where the battle's own rules put it. Two battles are played. The Fall, launched as the debug mission player
 * launches it (the hull's watch with its town salvage, the company landing), gives the forge printing, a stripper
 * cutting the town, a hauler carrying the metal home and the watch fighting. The Stakes chapter's ground, which no
 * battle is built on yet, gets the hull's rules as The Fall installs them with a one-stake program, and gives a stake
 * planted, rising and stranded; its relay node is destroyed by the clip (labelled as such), the one scripted event.
 * Each clip starts when its subject is about to do its work and runs 10 to 20 seconds, in real time or at a stated
 * whole-number speed-up. Look recordings only: nothing is judged and no battle is read for who wins. Opt-in;
 * {@code -Dsnapshot.hullSim.only=<clip>[,<clip>]} records only those.
 */
public final class FallenHullSimLookSuite implements SnapshotSuite {

    private static final int W = LookReview.WIDTH, H = LookReview.HEIGHT;
    private static final float DT = BattleSimulation.TICK_DT;
    /** Frames a second in every recording: a speed-up of n records a frame every n ticks. */
    private static final int FPS = 30;
    /** The longest The Fall and the Stakes ground are played waiting for their clips, seconds. */
    private static final float FALL_SECONDS = 900f, STAKES_SECONDS = 600f;
    public static final String ONLY_PROPERTY = "snapshot.hullSim.only";
    /**
     * {@code -Dsnapshot.hullSim.cells=<n>} frames every clip {@code n} cells across, and
     * {@code -Dsnapshot.hullSim.maxSpeed=<n>} caps its speed-up: close looks at the walkers' work arms.
     */
    public static final String CELLS_PROPERTY = "snapshot.hullSim.cells";
    private static final float CELLS = Float.parseFloat(System.getProperty(CELLS_PROPERTY, "0"));
    private static final int SPEED_CAP = Integer.parseInt(System.getProperty("snapshot.hullSim.maxSpeed", "99"));

    @Override public String id() { return "fallen-hull-sim-look"; }

    @Override public String label() {
        return "The fallen hull's rules on camera: forge, stripper, hauler, stake and the watch, MP4s";
    }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Set<String> only = new HashSet<>();
        String o = System.getProperty(ONLY_PROPERTY, "").trim();
        if (!o.isEmpty()) only.addAll(List.of(o.split(",")));
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = new HeadlessStrings(context.modRoot())) {
            if (wants(only, "forge-cycle", "stripper", "hauler", "watch-fights")) fall(context, only, out);
            if (wants(only, "stake-planted", "stake-rises")) stakes(context, only, out);
        }
        return out;
    }

    private static boolean wants(Set<String> only, String... clips) {
        if (only.isEmpty()) return true;
        for (String c : clips) if (only.contains(c)) return true;
        return false;
    }

    private static <T extends Clip> void add(List<Clip> clips, Set<String> only, T clip) {
        if (only.isEmpty() || only.contains(clip.name)) clips.add(clip);
    }

    // ---- The Fall

    private void fall(SnapshotContext context, Set<String> only, List<SnapshotArtifact> out) throws Exception {
        var mission = DebugMissionCatalog.Scenario.LONG_SURVEY_FALL.mission();
        var ops = new MarineOpsContext(null);
        ops.resetDebugPreset(mission);
        var preset = DebugMissionCatalog.preset(mission);
        var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
        var plan = MissionLaunch.captureScenario(ops, mission, lift, FlybyRoster.EMPTY, List.of(), List.of(), null,
                mission.battleSeed);
        try (var prepared = MissionLaunch.finishSimulation(ops, mission, FlybyRoster.EMPTY, plan, plan.builder().get())) {
            BattleSimulation sim = prepared.simulation();
            DerelictSecurityDirector director = sim.derelictSecurity();
            FallenHull hull = director == null ? null : director.hull();
            if (hull == null) {
                log("The Fall stood no fallen hull");
                return;
            }
            log("The Fall: salvage %d piles, walkers out %d, store %.0f", hull.salvage().size(),
                    director.walkers().size(), hull.hullStore().metal());
            List<Clip> clips = new ArrayList<>();
            add(clips, only, new StripperClip(hull, director));
            add(clips, only, new ForgeClip(hull, director));
            add(clips, only, new HaulerClip(hull, director));
            add(clips, only, new FightClip(hull, director));
            play(context, sim, clips, FALL_SECONDS, out);
        }
    }

    // ---- The Stakes chapter's ground

    private void stakes(SnapshotContext context, Set<String> only, List<SnapshotArtifact> out) throws Exception {
        LongSurveyGround.Ground ground = stakesGround(context);
        BattleSimulation sim = stand(ground);
        try {
            // One spire stake, then a walker the forge can never pay for, so one train goes out and nothing else.
            BuildQueue queue = new BuildQueue(List.of(
                    BuildQueue.Item.stake(BuildQueue.STAKE_COST, DerelictWalkerFrame.ROUNDS),
                    BuildQueue.Item.walker(DerelictWalkerFrame.LINE, 1.0e6f)));
            FallenHull hull = FallenHull.install(sim, LongSurveyGround.rulesSite(ground.hull(), List.of(), List.of(),
                    BuildQueue.STAKE_COST), queue, null);
            DerelictSecurityDirector director = sim.derelictSecurity();
            log("Stakes ground: starting stakes %s", hull.stakes().keySet());
            List<Clip> clips = new ArrayList<>();
            add(clips, only, new StakePlantClip(hull));
            add(clips, only, new StakeRiseClip(hull, director));
            play(context, sim, clips, STAKES_SECONDS, out);
        } finally {
            sim.close();
        }
    }

    private static LongSurveyGround.Ground stakesGround(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        StoryMap chapter = StoryMap.LONG_SURVEY_STAKES;
        return LongSurveyGround.ground(chapter, StoryMapPools.pool(chapter).home());
    }

    /** The Stakes ground as a battle, with a still body a side in the map's far corners: the simulation ticks no
     * side that has none. */
    private static BattleSimulation stand(LongSurveyGround.Ground ground) {
        SurfacePalette home = StoryMapPools.pool(ground.chapter()).home();
        int w = ground.map().grid.getWidth(), h = ground.map().grid.getHeight();
        int[] a = openCorner(ground, 3, 3, 1), b = openCorner(ground, w - 4, h - 4, -1);
        return SceneBuilder.onMap(ground.map(), home).seed(ground.battleSeed()).missionCompletion(false)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, a[0], a[1], spec -> spec.moveSpeed(0f))
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, b[0], b[1], spec -> spec.moveSpeed(0f))
                .build().sim();
    }

    private static int[] openCorner(LongSurveyGround.Ground ground, int x0, int y0, int step) {
        var grid = ground.map().grid;
        var topo = ground.map().topology;
        for (int r = 0; r < 40; r++) {
            for (int d = 0; d <= r; d++) {
                int x = x0 + step * d, y = y0 + step * (r - d);
                if (grid.inBounds(x, y) && grid.isWalkable(x, y) && topo.getBuildingId(x, y) == 0 && !topo.isWall(x, y))
                    return new int[] {x, y};
            }
        }
        return new int[] {x0, y0};
    }

    // ---- the recorder

    /**
     * Plays the battle, tick by tick as the screen does, until every clip has recorded or the time is up; each clip
     * starts on its own trigger and records a frame every {@code speed} ticks through its own following camera.
     */
    private static void play(SnapshotContext context, BattleSimulation sim, List<Clip> clips, float seconds,
                             List<SnapshotArtifact> out) throws Exception {
        if (clips.isEmpty()) return;
        Path scratch = context.projectRoot().resolve("build/tmp/look-video");
        Files.createDirectories(scratch);
        ReviewImpactFx fx = new ReviewImpactFx(context.modRoot());
        int ticks = Math.round(seconds / DT);
        try {
            for (int tick = 1; tick <= ticks; tick++) {
                for (Clip c : clips) if (c.recording()) c.beforeTick(sim, tick);
                int before = sim.getSimTickIndex();
                sim.advance(DT);
                if (sim.getSimTickIndex() == before) {
                    log("the battle stopped advancing at %.0f s", tick * DT);
                    break;
                }
                BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), DT);
                fx.feed(sim);
                boolean open = false;
                for (Clip c : clips) {
                    c.observe(sim, tick);
                    if (!c.started && c.ready(sim, tick)) c.start(context, sim, scratch, tick);
                    if (c.recording() && (tick - c.startTick) % c.speed == 0) {
                        c.frame(sim, fx);
                        if (c.frames >= c.maxFrames || (c.frames >= c.minFrames && c.done(sim, tick))) {
                            out.add(c.finish());
                        }
                    }
                    open |= !c.finished;
                }
                if (!open) break;
            }
            for (Clip c : clips) {
                if (c.recording()) out.add(c.finish());
                else if (!c.started) log("%s: never started (%s)", c.name, c.whyNot(sim));
            }
        } finally {
            for (Clip c : clips) c.close();
        }
    }

    /** One recording: its subject, its trigger, its camera and its labels. */
    private abstract static class Clip {
        final String name, title;
        final int speed, minFrames, maxFrames;
        final boolean roofs;
        boolean started, finished;
        int startTick, frames;
        private BattleReviewFrameRenderer renderer;
        private FfmpegVideo encoder;
        private float[] cam;
        private final BufferedImage canvas = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

        Clip(String name, String title, int speed, float minSeconds, float maxSeconds, boolean roofs) {
            this.name = name;
            this.title = title;
            // A capped speed-up runs the clip longer, to cover the same stretch of battle,
            this.speed = Math.max(1, Math.min(speed, SPEED_CAP));
            this.minFrames = Math.round(minSeconds * FPS * speed / this.speed);
            // and a close look follows its subject further, so it may run on to the end of its work.
            this.maxFrames = Math.round(maxSeconds * FPS * speed / this.speed * (this.speed < speed ? 3 : 1));
            this.roofs = roofs;
        }

        /** {@link #focus}, closer where {@link #CELLS_PROPERTY} asks. */
        private float[] framed(BattleSimulation sim) {
            float[] f = focus(sim).clone();
            if (CELLS > 0f) f[2] = CELLS;
            return f;
        }

        boolean recording() { return started && !finished; }

        /** Every tick, recording or not: for a clip that watches for its moment. */
        void observe(BattleSimulation sim, int tick) { }

        abstract boolean ready(BattleSimulation sim, int tick);

        /** Before each tick while recording: the one scripted event a clip may own. */
        void beforeTick(BattleSimulation sim, int tick) { }

        /** Where the camera wants to be: {centre x, centre y, cells across}. */
        abstract float[] focus(BattleSimulation sim);

        abstract void draw(Overlay o, BattleSimulation sim);

        boolean done(BattleSimulation sim, int tick) { return false; }

        String whyNot(BattleSimulation sim) { return "its moment never came"; }


        void start(SnapshotContext context, BattleSimulation sim, Path scratch, int tick) throws Exception {
            started = true;
            startTick = tick;
            float[] f = framed(sim);
            cam = f.clone();
            BattleReviewFrameRenderer r = BattleReviewFrameRenderer.look(context.modRoot(), W, H, f[0], f[1], f[2]);
            renderer = roofs ? r : r.withoutRoofs();
            encoder = FfmpegVideo.open(Files.createTempFile(scratch, name + "-", ".mp4"), W, H, FPS + "/1");
            log("%s: starts at %.1f s", name, tick * DT);
        }

        void frame(BattleSimulation sim, ReviewImpactFx fx) throws Exception {
            float[] f = framed(sim);
            // The camera eases toward its subject rather than snapping to it.
            for (int i = 0; i < 3; i++) cam[i] += (f[i] - cam[i]) * 0.12f;
            BattleReviewFrameRenderer r = renderer.refocus(cam[0], cam[1], cam[2]);
            BufferedImage img = r.render(sim, "", ReviewAnnotations.NONE, fx);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(img, 0, 0, null);
            Overlay o = new Overlay(g, r.cameraOf(sim));
            draw(o, sim);
            o.speed(speed);
            g.dispose();
            encoder.add(canvas);
            frames++;
        }

        SnapshotArtifact finish() throws Exception {
            finished = true;
            Path video = encoder.finish();
            log("%s: %d frames, %.1f s at %s", name, frames, frames / (float) FPS,
                    speed == 1 ? "real time" : speed + "x");
            return SnapshotArtifact.video("look/" + name + ".mp4", video, List.of());
        }

        void close() {
            if (encoder != null) encoder.close();
        }
    }

    // ---- clips

    /** 1. The forge's cycle: from a few seconds before it prints a walker to the walker out of the bay mouth. */
    private static final class ForgeClip extends Clip {
        private final FallenHull hull;
        private final DerelictSecurityDirector director;
        private final Set<Integer> known = new HashSet<>();
        private int printedAtStart = -1, printedTick = -1, walkerSquad = -1;
        private DerelictWalkerFrame walkerFrame;

        ForgeClip(FallenHull hull, DerelictSecurityDirector director) {
            super("forge-cycle", "1  Autoforge (ring forge): a forge cycle prints a walker", 1, 12f, 18f, false);
            this.hull = hull;
            this.director = director;
        }

        @Override boolean ready(BattleSimulation sim, int tick) {
            BuildQueue.Item next = hull.forge().queue().peek();
            if (!hull.forge().lit() || next.kind() != BuildQueue.Kind.WALKER || sim.resolveUnit(hull.forgeId()) == 0L)
                return false;
            if (hull.hullStore().metal() < next.cost()) return false;
            return hull.forge().cycleProgress() >= 1f - 5f / 30f;
        }

        @Override void start(SnapshotContext context, BattleSimulation sim, Path scratch, int tick) throws Exception {
            for (DerelictSecurityDirector.Walker w : director.walkers()) known.add(w.squadId());
            printedAtStart = hull.forge().printed();
            super.start(context, sim, scratch, tick);
        }

        @Override void observe(BattleSimulation sim, int tick) {
            if (!started || walkerSquad >= 0 || hull.forge().printed() == printedAtStart) return;
            for (DerelictSecurityDirector.Walker w : director.walkers()) {
                if (!known.contains(w.squadId()) && sim.squadMemberCount(w.squadId()) > 0) {
                    walkerSquad = w.squadId();
                    walkerFrame = w.frame();
                    printedTick = tick;
                }
            }
        }

        @Override float[] focus(BattleSimulation sim) {
            FallenHullSite site = hull.site();
            float fx = site.forgeX() + .5f, fy = site.forgeY() + .5f, mx = site.mouthX() + .5f, my = site.mouthY() + .5f;
            long walker = member(sim, walkerSquad);
            if (walker != 0L && seconds(printedTick) > 1.5f) {
                World w = sim.world();
                return new float[] {w.renderX(walker), w.renderY(walker), 40f};
            }
            float d = (float) Math.hypot(mx - fx, my - fy);
            return new float[] {(fx + mx) / 2f, (fy + my) / 2f, Math.max(48f, Math.min(110f, d * 1.5f))};
        }

        @Override void draw(Overlay o, BattleSimulation sim) {
            FallenHullSite site = hull.site();
            BuildQueue.Item next = hull.forge().queue().peek();
            String cycle = printedTick < 0 ? "cycle " + Math.round(hull.forge().cycleProgress() * 100f) + "%"
                    : "printed: " + walkerFrame.designation();
            o.title(title, cycle + "   next: " + nameOf(next) + "   hull store " + Math.round(hull.hullStore().metal())
                    + " metal", "roofs lifted to show the forge hall");
            o.label("autoforge", site.forgeX() + .5f, site.forgeY() + .5f, 0, -120);
            o.label("bay mouth", site.mouthX() + .5f, site.mouthY() + .5f, -230, 130);
            long walker = member(sim, walkerSquad);
            if (walker != 0L) {
                o.label("printed " + walkerFrame.designation() + ", out of the bay", sim.world().renderX(walker),
                        sim.world().renderY(walker), 260, -90);
            }
        }

        @Override boolean done(BattleSimulation sim, int tick) {
            return printedTick >= 0 && seconds(printedTick) >= 9f;
        }

        private float seconds(int fromTick) { return fromTick < 0 ? 0f : (startTick + frames * speed - fromTick) * DT; }

        @Override String whyNot(BattleSimulation sim) {
            return "forge lit " + hull.forge().lit() + ", next " + nameOf(hull.forge().queue().peek()) + ", store "
                    + Math.round(hull.hullStore().metal());
        }
    }

    /** 2. A stripper walking to the town's salvage and cutting it. */
    private static final class StripperClip extends Clip {
        private final FallenHull hull;
        private final DerelictSecurityDirector director;
        private int squad = -1;
        private int fullTick = -1;

        StripperClip(FallenHull hull, DerelictSecurityDirector director) {
            super("stripper", "2  Stripper: walks to the town's salvage and cuts it into feedstock", 3, 10f, 20f, true);
            this.hull = hull;
            this.director = director;
        }

        @Override boolean ready(BattleSimulation sim, int tick) {
            if (tick * DT < 1f) return false;
            for (Map.Entry<Integer, StripperWork> e : hull.strippers().entrySet()) {
                long id = member(sim, e.getKey());
                if (id == 0L || e.getValue().load() > 0f) continue;
                Salvage pile = nearest(hull, sim.world().x(id), sim.world().y(id));
                if (pile == null) continue;
                double d = Math.hypot(pile.x - sim.world().x(id), pile.y - sim.world().y(id));
                if (d >= 5 && d <= 30) {
                    squad = e.getKey();
                    return true;
                }
            }
            return false;
        }

        @Override void observe(BattleSimulation sim, int tick) {
            StripperWork work = hull.strippers().get(squad);
            if (started && fullTick < 0 && work != null && work.load() >= StripperWork.CAPACITY - 0.5f) fullTick = tick;
        }

        @Override float[] focus(BattleSimulation sim) {
            long id = member(sim, squad);
            if (id == 0L) return new float[] {hull.site().mouthX(), hull.site().mouthY(), 40f};
            World w = sim.world();
            float x = w.renderX(id), y = w.renderY(id);
            Salvage pile = nearest(hull, w.x(id), w.y(id));
            if (pile == null) return new float[] {x, y, 30f};
            float d = (float) Math.hypot(pile.x - x, pile.y - y);
            return new float[] {(x + pile.x) / 2f, (y + pile.y) / 2f, Math.max(30f, Math.min(70f, d * 1.8f))};
        }

        @Override void draw(Overlay o, BattleSimulation sim) {
            long id = member(sim, squad);
            StripperWork work = hull.strippers().get(squad);
            Salvage pile = id == 0L ? null : nearest(hull, sim.world().x(id), sim.world().y(id));
            o.title(title, "load " + (work == null ? 0 : Math.round(work.load())) + " / "
                    + Math.round(StripperWork.CAPACITY) + "   salvage left here "
                    + (pile == null ? 0 : Math.round(pile.metal())) + " metal");
            if (id != 0L) o.label("MG-18 stripper", sim.world().renderX(id), sim.world().renderY(id), -220, -110);
            if (pile != null) {
                o.label(pile.kind == Salvage.Kind.WRECK ? "wreck (salvage)" : "town building's metal (salvage)",
                        pile.x, pile.y, 240, 120);
            }
        }

        @Override boolean done(BattleSimulation sim, int tick) {
            return fullTick >= 0 && (tick - fullTick) * DT >= 3f;
        }
    }

    /** 3. A hauler taking a stripper's load and walking the haul road home, the hull's store rising. */
    private static final class HaulerClip extends Clip {
        private final FallenHull hull;
        private final DerelictSecurityDirector director;
        private int squad = -1, emptyTick = -1;
        private boolean loaded;

        HaulerClip(FallenHull hull, DerelictSecurityDirector director) {
            super("hauler", "3  Hauler: takes a stripper's load and carries it home to the hull's store", 4, 10f, 20f,
                    true);
            this.hull = hull;
            this.director = director;
        }

        @Override boolean ready(BattleSimulation sim, int tick) {
            for (Map.Entry<Integer, HaulerWork> e : hull.haulers().entrySet()) {
                long id = member(sim, e.getKey());
                if (id == 0L || e.getValue().load() > 0f) continue;
                for (Map.Entry<Integer, StripperWork> s : hull.strippers().entrySet()) {
                    long st = member(sim, s.getKey());
                    if (st == 0L || s.getValue().load() < StripperWork.CAPACITY - 0.5f) continue;
                    double d = Math.hypot(sim.world().x(st) - sim.world().x(id), sim.world().y(st) - sim.world().y(id));
                    if (d <= 10) {
                        squad = e.getKey();
                        return true;
                    }
                }
            }
            return false;
        }

        @Override void observe(BattleSimulation sim, int tick) {
            HaulerWork work = hull.haulers().get(squad);
            if (!started || work == null) return;
            if (work.load() > 0f) loaded = true;
            else if (loaded && emptyTick < 0) emptyTick = tick;
        }

        @Override float[] focus(BattleSimulation sim) {
            long id = member(sim, squad);
            if (id == 0L) return new float[] {hull.site().mouthX(), hull.site().mouthY(), 38f};
            return new float[] {sim.world().renderX(id), sim.world().renderY(id), 38f};
        }

        @Override void draw(Overlay o, BattleSimulation sim) {
            HaulerWork work = hull.haulers().get(squad);
            for (int[] road : hull.site().haulRoads()) o.road(road);
            o.title(title, "load " + (work == null ? 0 : Math.round(work.load())) + " / "
                            + Math.round(HaulerWork.CAPACITY) + "   hull store " + Math.round(hull.hullStore().metal())
                            + " metal",
                    "dashed amber: haul roads");
            long id = member(sim, squad);
            if (id != 0L) o.label("MG-30 hauler", sim.world().renderX(id), sim.world().renderY(id), 240, -110);
            o.label("hull's store (bay mouth)", hull.site().mouthX() + .5f, hull.site().mouthY() + .5f, -200, 120);
        }

        @Override boolean done(BattleSimulation sim, int tick) {
            return emptyTick >= 0 && (tick - emptyTick) * DT >= 4f;
        }
    }

    /** 5. The hull's walkers and flocks out in the town, fighting. */
    private static final class FightClip extends Clip {
        private final FallenHull hull;
        private final DerelictSecurityDirector director;
        /** Recent fire, the watch's and the rescue's: {x, y, tick, 1 for the watch}. */
        private final Deque<float[]> fire = new ArrayDeque<>();

        FightClip(FallenHull hull, DerelictSecurityDirector director) {
            super("watch-fights", "5  The hull's watch against the rescue in the town", 1, 10f, 20f,
                    true);
            this.hull = hull;
            this.director = director;
        }

        @Override void observe(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterFaction != Faction.DEFENDER && shot.shooterFaction != Faction.MARINE) continue;
                fire.add(new float[] {(shot.fromX + shot.toX) / 2f, (shot.fromY + shot.toY) / 2f, tick,
                        shot.shooterFaction == Faction.DEFENDER ? 1 : 0});
            }
            while (!fire.isEmpty() && tick - fire.peekFirst()[2] > 3f / DT) fire.removeFirst();
        }

        @Override boolean ready(BattleSimulation sim, int tick) {
            // Both sides firing: the rescue in contact with the watch.
            int watch = 0, rescue = 0;
            for (float[] f : fire) {
                if (f[3] > 0) watch++;
                else rescue++;
            }
            return watch >= 3 && rescue >= 3;
        }

        /** Where the fight last was: the camera holds there when the shooting stops. */
        private float[] last;
        private int quietSince = -1;

        @Override float[] focus(BattleSimulation sim) {
            if (fire.isEmpty()) {
                return last != null ? last.clone() : new float[] {hull.site().mouthX(), hull.site().mouthY(), 64f};
            }
            float x = 0, y = 0;
            for (float[] f : fire) {
                x += f[0];
                y += f[1];
            }
            last = new float[] {x / fire.size(), y / fire.size(), 64f};
            return last.clone();
        }

        @Override boolean done(BattleSimulation sim, int tick) {
            if (!fire.isEmpty()) quietSince = -1;
            else if (quietSince < 0) quietSince = tick;
            return quietSince >= 0 && (tick - quietSince) * DT >= 3f;
        }

        @Override void draw(Overlay o, BattleSimulation sim) {
            int out = 0, labelled = 0;
            int[][] offsets = {{220, -120}, {-240, -110}, {230, 130}, {-230, 140}};
            for (DerelictSecurityDirector.Walker w : director.walkers()) {
                long id = member(sim, w.squadId());
                if (id == 0L || !w.frame().armed()) continue;
                out++;
                float x = sim.world().renderX(id), y = sim.world().renderY(id);
                if (labelled < offsets.length && o.inFrame(x, y)) {
                    o.label(w.frame().designation(), x, y, offsets[labelled][0], offsets[labelled][1]);
                    labelled++;
                }
            }
            int up = 0;
            boolean flockLabelled = false;
            for (Flock f : sim.flocks().flocks()) {
                if (f.side() != Faction.DEFENDER || f.size() == 0) continue;
                up++;
                float x = 0, y = 0;
                int n = 0;
                for (long m : f.memberIds()) {
                    if (sim.resolveUnit(m) == 0L) continue;
                    x += sim.world().renderX(m);
                    y += sim.world().renderY(m);
                    n++;
                }
                if (n > 0 && !flockLabelled && o.inFrame(x / n, y / n)) {
                    o.label("flock (wasps)", x / n, y / n, 0, 150);
                    flockLabelled = true;
                }
            }
            o.title(title, "hull walkers out " + out + "   flocks up " + up,
                    "The Fall as played: the hull awake, the company escorting the rescue");
        }
    }

    /** 4a. The forge's stake train walking out to the lattice and planting its stake. */
    private static final class StakePlantClip extends Clip {
        private final FallenHull hull;
        private StakeTrain train;
        private int plantedTick = -1;

        StakePlantClip(FallenHull hull) {
            super("stake-planted", "4  Survey stake: the stake train plants a spire stake on the lattice", 3, 10f, 20f,
                    true);
            this.hull = hull;
        }

        @Override boolean ready(BattleSimulation sim, int tick) {
            for (StakeTrain t : hull.trains()) {
                long id = member(sim, t.haulerSquad);
                if (id == 0L) continue;
                double d = Math.hypot(t.plan.point().x() - sim.world().x(id), t.plan.point().y() - sim.world().y(id));
                if (d <= 18) {
                    train = t;
                    return true;
                }
            }
            return false;
        }

        @Override void observe(BattleSimulation sim, int tick) {
            if (started && plantedTick < 0 && train != null && hull.stakes().containsKey(train.plan.point().id()))
                plantedTick = tick;
        }

        /** Where the stake stands: its seat once planted, the lattice point before. */
        private float[] target() {
            int[] at = hull.seatOf(train.plan.point().id());
            return at != null ? new float[] {at[0] + .5f, at[1] + .5f}
                    : new float[] {train.plan.point().x() + .5f, train.plan.point().y() + .5f};
        }

        @Override float[] focus(BattleSimulation sim) {
            float[] t = target();
            float px = t[0], py = t[1];
            long id = member(sim, train.haulerSquad);
            if (id == 0L) return new float[] {px, py, 36f};
            float x = sim.world().renderX(id), y = sim.world().renderY(id);
            return new float[] {(x + px) / 2f, (y + py) / 2f, Math.max(36f, Math.min(64f,
                    (float) Math.hypot(x - px, y - py) * 1.8f))};
        }

        @Override void draw(Overlay o, BattleSimulation sim) {
            float[] t = target();
            float px = t[0], py = t[1];
            boolean planted = plantedTick >= 0;
            o.title(title, planted ? "planted: it rises in " + Math.round(SurveyStake.RAISE_SECONDS) + " s if its chain holds"
                    : "en route to lattice point " + train.plan.point().id());
            o.label(planted ? "spire stake, planted" : "open lattice point", px, py, 240, -120);
            long id = member(sim, train.haulerSquad);
            if (id != 0L) o.label("hauler carrying the stake", sim.world().renderX(id), sim.world().renderY(id),
                    -240, 120);
            int i = 0;
            for (int esc : train.escort) {
                long e = member(sim, esc);
                if (e != 0L && i++ == 0) o.label("escort", sim.world().renderX(e), sim.world().renderY(e), -220, -120);
            }
        }

        @Override boolean done(BattleSimulation sim, int tick) {
            return plantedTick >= 0 && (tick - plantedTick) * DT >= 3f;
        }

        @Override String whyNot(BattleSimulation sim) {
            return "trains " + hull.trains().size() + ", printed " + hull.forge().printed();
        }
    }

    /**
     * 4b. The planted stake rising, planted to raised to established, in time-lapse; then its relay node destroyed
     * (the clip's one scripted event) and the stake stranded.
     */
    private static final class StakeRiseClip extends Clip {
        private final FallenHull hull;
        private final DerelictSecurityDirector director;
        private int point = -1, establishedTick = -1, cutTick = -1;
        private boolean upstream;
        private final Set<Integer> before = new HashSet<>();
        private boolean seeded;

        StakeRiseClip(FallenHull hull, DerelictSecurityDirector director) {
            super("stake-rises", "4  Survey stake: planted, raised, established; then its relay chain cut", 12, 10f,
                    20f, true);
            this.hull = hull;
            this.director = director;
        }

        @Override void observe(BattleSimulation sim, int tick) {
            if (!seeded) {
                before.addAll(hull.stakes().keySet());
                seeded = true;
            }
            if (point < 0) {
                for (int p : hull.stakes().keySet()) if (!before.contains(p)) point = p;
            }
            SurveyStake s = point < 0 ? null : hull.stakes().get(point);
            if (started && s != null && establishedTick < 0 && s.stage() == SurveyStake.Stage.ESTABLISHED)
                establishedTick = tick;
        }

        @Override boolean ready(BattleSimulation sim, int tick) {
            return point >= 0;
        }

        @Override void beforeTick(BattleSimulation sim, int tick) {
            if (establishedTick < 0 || cutTick >= 0 || (tick - establishedTick) * DT < 4f) return;
            // The chain cut upstream, off the stake, so it stands whole and only stops growing: the node of the stake
            // it hangs off, else (hung off the hull) the hull zone's node, its autoforge.
            SurveyStake s = hull.stakes().get(point);
            upstream = s != null && s.parent >= 0 && director.nodes().containsKey(FallenHull.zoneOf(s.parent));
            Long node = director.nodes().get(upstream ? FallenHull.zoneOf(s.parent) : FallenHull.HULL_ZONE);
            if (node != null && sim.resolveUnit(node) != 0L) sim.applyExternalDamage(node, 1.0e6f, 1.0e6f);
            cutTick = tick;
            log("stake-rises: cut %s at %.0f s", upstream ? "the upstream stake's node" : "the hull's autoforge",
                    tick * DT);
        }

        /** The stake's footprint centre, its point before it seats. */
        private float[] centre() {
            int[] at = hull.seatOf(point);
            if (at == null) {
                var p = hull.lattice().point(point);
                at = new int[] {p.x(), p.y()};
            }
            return new float[] {at[0] + .5f, at[1] + .5f};
        }

        @Override float[] focus(BattleSimulation sim) {
            float[] c = centre();
            return new float[] {c[0], c[1], 30f};
        }

        @Override void draw(Overlay o, BattleSimulation sim) {
            SurveyStake s = hull.stakes().get(point);
            float[] c = centre();
            String status;
            if (s == null) status = "gone";
            else if (s.stranded()) status = s.stage().name().toLowerCase() + ", STRANDED: its chain is cut and it builds "
                    + "no further";
            else if (s.stage() == SurveyStake.Stage.ESTABLISHED) status = "established: a zone with its node, bay and store";
            else status = s.stage().name().toLowerCase() + "   " + Math.round(s.progress() * 100f) + "% to "
                    + (s.stage() == SurveyStake.Stage.PLANTED ? "raised" : "established") + "   relay chain holds";
            o.title(title, status, cutTick < 0 ? null : upstream
                    ? "the relay node it hangs off, upstream, destroyed by this clip (scripted)"
                    : "the hull's autoforge, upstream, put out by this clip (scripted)");
            o.label("spire stake", c[0], c[1], 300, -150);
            if (s != null && s.stage() != SurveyStake.Stage.PLANTED) {
                int[] cradle = StakeFootprint.slot(StakeFootprint.Part.CRADLE).centre(0, 0);
                o.label("flock cradle", c[0] + cradle[0], c[1] + cradle[1], -260, -120);
            }
            if (s != null && s.stage() == SurveyStake.Stage.ESTABLISHED) {
                int[] store = StakeFootprint.slot(StakeFootprint.Part.STORE).centre(0, 0);
                o.label("store", c[0] + store[0], c[1] + store[1], 0, 150);
            }
        }

        @Override boolean done(BattleSimulation sim, int tick) {
            return cutTick >= 0 && (tick - cutTick) * DT >= 30f;
        }

        @Override String whyNot(BattleSimulation sim) {
            return "no stake planted";
        }
    }

    // ---- helpers

    private static long member(BattleSimulation sim, int squad) {
        if (squad < 0 || sim.squadMemberCount(squad) <= 0) return 0L;
        long id = sim.squadMemberAt(squad, 0);
        return sim.resolveUnit(id) == 0L ? 0L : id;
    }

    private static Salvage nearest(FallenHull hull, float x, float y) {
        Salvage best = null;
        double bestD = Double.MAX_VALUE;
        for (Salvage s : hull.salvage()) {
            if (!s.cuttable() || s.metal() <= 0f) continue;
            double d = Math.hypot(s.x - x, s.y - y);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private static String nameOf(BuildQueue.Item item) {
        return item.kind() == BuildQueue.Kind.STAKE ? "survey stake" : item.frame().designation();
    }

    /** Labels burned into a frame in screen space. */
    private static final class Overlay {
        private static final Color AMBER = new Color(255, 200, 70);
        private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 26);
        private static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 22);
        /** Labels sit below the title box. */
        private static final int TOP = 130;
        private final Graphics2D g;
        private final BattleCamera cam;

        Overlay(Graphics2D g, BattleCamera cam) {
            this.g = g;
            this.cam = cam;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        float sx(float x) { return cam.cellToScreenX(x); }

        float sy(float y) { return H - cam.cellToScreenY(y); }

        boolean inFrame(float x, float y) {
            float px = sx(x), py = sy(y);
            return px > 40 && px < W - 40 && py > 40 && py < H - 40;
        }

        /** The clip's title and status lines, top left. */
        void title(String title, String... lines) {
            List<String> rows = new ArrayList<>();
            for (String l : lines) if (l != null) rows.add(l);
            g.setFont(FONT);
            FontMetrics big = g.getFontMetrics();
            g.setFont(SMALL);
            FontMetrics small = g.getFontMetrics();
            int w = big.stringWidth(title);
            for (String l : rows) w = Math.max(w, small.stringWidth(l));
            int h = big.getHeight() + rows.size() * small.getHeight() + 20;
            box(16, 16, w + 28, h);
            g.setFont(FONT);
            g.setColor(AMBER);
            g.drawString(title, 30, 26 + big.getAscent());
            g.setFont(SMALL);
            g.setColor(Color.WHITE);
            for (int i = 0; i < rows.size(); i++) {
                g.drawString(rows.get(i), 30, 26 + big.getHeight() + i * small.getHeight() + small.getAscent());
            }
        }

        /** The playback speed, bottom right. */
        void speed(int speed) {
            String text = speed == 1 ? "real time" : speed + "x speed";
            g.setFont(SMALL);
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(text) + 24, h = fm.getHeight() + 12;
            box(W - 16 - w, H - 16 - h, w, h);
            g.setColor(Color.WHITE);
            g.drawString(text, W - 16 - w + 12, H - 16 - h + 6 + fm.getAscent());
        }

        /** A short label {@code (dx, dy)} pixels off cell point (x, y), with a leader to a ring there. */
        void label(String text, float x, float y, int dx, int dy) {
            if (!inFrame(x, y)) return;
            float px = sx(x), py = sy(y);
            g.setFont(FONT);
            FontMetrics fm = g.getFontMetrics();
            int pad = 12, bw = fm.stringWidth(text) + 2 * pad, bh = fm.getHeight() + pad;
            int bx = Math.max(16, Math.min(W - 16 - bw, Math.round(px + dx - bw / 2f)));
            int by = Math.max(TOP, Math.min(H - 16 - bh, Math.round(py + dy - bh / 2f)));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(AMBER);
            float lx = Math.max(bx, Math.min(bx + bw, px)), ly = Math.max(by, Math.min(by + bh, py));
            g.drawLine(Math.round(px), Math.round(py), Math.round(lx), Math.round(ly));
            g.drawOval(Math.round(px) - 9, Math.round(py) - 9, 18, 18);
            box(bx, by, bw, bh);
            g.setColor(Color.WHITE);
            g.drawString(text, bx + pad, by + pad / 2 + fm.getAscent());
        }

        /** A haul road: a dashed amber line along its polyline. */
        void road(int[] poly) {
            Path2D path = new Path2D.Float();
            for (int i = 0; i + 1 < poly.length; i += 2) {
                float x = sx(poly[i] + .5f), y = sy(poly[i + 1] + .5f);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g.setColor(new Color(255, 200, 70, 200));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {16f, 12f},
                    0f));
            g.draw(path);
        }

        private void box(int x, int y, int w, int h) {
            g.setColor(new Color(14, 12, 20, 215));
            g.fillRoundRect(x, y, w, h, 12, 12);
            g.setColor(AMBER);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 12, 12);
        }
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/fallen-hull-sim] " + format + "%n", args);
    }
}
