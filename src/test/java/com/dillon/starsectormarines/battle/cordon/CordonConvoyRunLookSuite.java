package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleSoundLog;
import com.dillon.starsectormarines.ops.battleview.BattleSoundMix;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One relief run on Ain Tafrant as the owner would watch it: the Cordon Road on the town pool's arid seed 7, played as
 * the evidence plays it (the Marine commander playing the company), fast-forwarded unrecorded until a convoy's lead
 * truck nears the town's truck yard, then recorded in one go through the look preset as one MP4,
 * {@code look/convoy-run.mp4}, in three parts joined without a cut in the battle:
 * <ol>
 *   <li>{@code pickup}, in real time: the camera held on the yard and the staging area at play zoom while the truck
 *   drives in, turns, and the townspeople walk out and climb aboard, until it has pulled away toward the gate;</li>
 *   <li>{@code drive}, sped up to last about twenty seconds: the camera eases onto the truck and follows it out of the
 *   yard's gate, through the gap in the line and down the road to the camp;</li>
 *   <li>{@code dropoff}, in real time: the camera held on the camp while the truck stops and its people climb down and
 *   walk in, and a few seconds after.</li>
 * </ol>
 * A truck destroyed on the way hands the camera to the other truck of its convoy; a convoy lost whole drops what was
 * recorded and waits for the next. {@code -Dsnapshot.lookSound=true} gives each part its soundtrack (each part's log is
 * its own, so the music starts afresh at each join). Stills ({@code look/convoy-run-NN.png}): boarding, pulling out,
 * the gap, and the people stepping down.
 *
 * <p>{@code -Dsnapshot.convoyRun.seed} (default 7, Ain Tafrant's pinned seed, {@code WellhollowBloomCampaign}),
 * {@code .driveSeconds} (20). Opt-in: it plays a whole battle up to the first relief run and the run itself.
 */
public final class CordonConvoyRunLookSuite implements SnapshotSuite {

    /** The latest tick a convoy is waited for. */
    static final int BY = 24_000;
    /** Cells from the yard's gate the lead truck opens the shot at. */
    static final float APPROACH = 34f;
    /** Corridor cells short of the camp's drop point the drive gives way to the drop-off. */
    static final int CAMP_NEAR = 26;
    /** Ticks between frames of the drive as recorded, before it is sped up. */
    static final int DRIVE_EVERY = 4;
    /** Ticks the pickup goes on after the convoy turns for home, at most, before the camera leaves the yard. */
    static final int PULL_AWAY = Math.round(8f / BattleSimulation.TICK_DT);
    /** Ticks the drop-off holds after the last person is down. */
    static final int TAIL = Math.round(5f / BattleSimulation.TICK_DT);
    /** Caps on the real-time parts, in ticks, so a run gone wrong cannot record forever. */
    static final int PICKUP_CAP = Math.round(100f / BattleSimulation.TICK_DT);
    static final int DROPOFF_CAP = Math.round(50f / BattleSimulation.TICK_DT);
    static final int DRIVE_CAP = Math.round(600f / BattleSimulation.TICK_DT);
    /** Per tick, the share of the way to its target the camera closes. */
    static final float EASE = 0.05f;
    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;

    @Override public String id() { return "cordon-convoy-run"; }

    @Override public String label() { return "Cordon Road, Ain Tafrant: one convoy's pickup, drive and drop-off"; }

    @Override public boolean inAll() { return false; }

    private enum Part { PICKUP, DRIVE, DROPOFF }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.convoyRun.seed", "7").trim());
        float driveSeconds = Float.parseFloat(System.getProperty("snapshot.convoyRun.driveSeconds", "20").trim());
        boolean sound = Boolean.getBoolean("snapshot.lookSound");
        String ffmpeg = FfmpegVideo.executable();
        if (ffmpeg == null) throw new IllegalStateException("cordon-convoy-run needs ffmpeg on PATH or FFMPEG");
        Path modRoot = context.modRoot();
        Path scratch = context.projectRoot().resolve("build/tmp/convoy-run");
        Files.createDirectories(scratch);
        BattleSimulation sim = HeadlessBattles.build("cordon:variant=road,density=town,biome=arid,seed=" + seed);
        try {
            CordonConvoys convoys = sim.getCordon().convoys();
            if (convoys == null) throw new IllegalStateException("no convoys on this Road");
            CordonGateYard yard = sim.getCordon().yard();
            CordonCamp camp = convoys.camp();
            List<int[]> corridor = convoys.corridor();
            int[] roadEnd = corridor.get(corridor.size() - 1);
            int[] gate = yard != null ? new int[] {yard.gateX(), yard.gateY()} : roadEnd;
            float[] pickupHold = pickupHold(sim, yard, roadEnd);
            System.out.printf(Locale.ROOT, "    [convoy-run] seed %d: corridor %d cells, gap at %d, entry %d, yard %s,"
                            + " gate %d,%d, camp %s, pickup hold %.0f,%.0f %.0f cells%n",
                    seed, corridor.size(), convoys.gapIndex(), convoys.entry(), yard != null,
                    gate[0], gate[1], camp == null ? "none" : camp.centreX() + "," + camp.centreY(),
                    pickupHold[0], pickupHold[1], pickupHold[2]);

            ReviewImpactFx fx = new ReviewImpactFx(modRoot);
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(modRoot, WIDTH, HEIGHT,
                    pickupHold[0], pickupHold[1], pickupHold[2]);
            List<BufferedImage> stills = new ArrayList<>();
            int skipped = -1;
            while (true) {
                // Fast-forward, drawing nothing, until a convoy's lead truck nears the yard.
                int number = 0;
                while (number == 0) {
                    step(sim, null, null, fx, false);
                    if (sim.getSimTickIndex() % 900 == 0) {
                        System.out.printf("    [convoy-run] tick %d: %s%n", sim.getSimTickIndex(), convoys.describe(sim));
                    }
                    if (sim.getSimTickIndex() >= BY) throw new IllegalStateException("no convoy near the yard by tick " + BY);
                    number = approaching(sim, convoys, gate, skipped);
                }
                long hull = hullOf(sim, convoys, number, 0);
                System.out.printf("    [convoy-run] tick %d: convoy %d's lead truck %d is %.0f cells from the gate;"
                        + " recording. %s%n", sim.getSimTickIndex(), number, hull, distance(sim, hull, gate),
                        convoys.describe(sim));
                Run run = new Run(scratch, sound, modRoot);
                stills.clear();
                boolean lost = record(sim, convoys, number, hull, gate, pickupHold, camp, corridor, renderer, fx, run,
                        stills);
                if (!lost) {
                    Path video = run.assemble(ffmpeg, modRoot, scratch, driveSeconds);
                    List<SnapshotArtifact> out = new ArrayList<>();
                    out.add(SnapshotArtifact.video("look/convoy-run.mp4", video, List.of()));
                    for (int i = 0; i < stills.size(); i++) {
                        out.add(new SnapshotArtifact(String.format("look/convoy-run-%02d.png", i + 1), stills.get(i)));
                    }
                    return out;
                }
                System.out.printf("    [convoy-run] tick %d: convoy %d lost on the road; waiting for the next%n",
                        sim.getSimTickIndex(), number);
                run.discard();
                skipped = number;
            }
        } finally {
            sim.close();
        }
    }

    /**
     * Records one convoy's run into {@code run}: true when the convoy was lost before its people were down, so the
     * recording is thrown away.
     */
    private static boolean record(BattleSimulation sim, CordonConvoys convoys, int number, long firstHull, int[] gate,
                                  float[] pickupHold, CordonCamp camp, List<int[]> corridor,
                                  BattleReviewFrameRenderer renderer, ReviewImpactFx fx, Run run,
                                  List<BufferedImage> stills) throws IOException {
        long hull = firstHull;
        int index = 0;
        int entry = convoys.entry();
        int[] drop = corridor.get(Math.max(0, Math.min(corridor.size() - 1, entry)));
        float[] dropHold = dropHold(sim, camp, drop);
        float[] cam = pickupHold.clone();
        Part part = Part.PICKUP;
        run.begin(part, 1);
        int partFrom = sim.getSimTickIndex();
        int outboundAt = -1, doneAt = -1;
        int lastAboard = 0;
        boolean boardStill = false, halfStill = false, outStill = false, gapStill = false, downStill = false;
        int downAt = -1;
        int maxAboard = 0;
        int seats = 0;
        while (true) {
            // The tracked truck destroyed: the camera goes to the other truck of the convoy, if one is left.
            if (!alive(sim, hull)) {
                long other = 0L;
                for (int i = 0; i < CordonConvoys.TRUCKS; i++) {
                    long h = hullOf(sim, convoys, number, i);
                    if (h != 0L && h != hull) {
                        other = h;
                        index = i;
                    }
                }
                if (other == 0L && part != Part.DROPOFF) return true;
                if (other != 0L) {
                    System.out.printf("    [convoy-run] tick %d: truck %d destroyed; following truck %d%n",
                            sim.getSimTickIndex(), hull, other);
                    hull = other;
                }
            }
            CordonConvoys.Reading reading = reading(sim, convoys, number);
            CordonConvoys.Phase phase = reading == null ? CordonConvoys.Phase.OUT : reading.phase();
            if (phase == CordonConvoys.Phase.LOST) return true;
            int aboard = reading == null ? 0 : reading.aboard();
            seats = Math.max(seats, reading == null ? 0 : reading.seats());
            maxAboard = Math.max(maxAboard, aboard);
            int tick = sim.getSimTickIndex();
            int[] at = convoys.truck(sim, number, index);
            float[] target;
            switch (part) {
                case PICKUP -> {
                    target = pickupHold;
                    if (phase == CordonConvoys.Phase.OUTBOUND && outboundAt < 0) {
                        outboundAt = tick;
                        System.out.printf("    [convoy-run] tick %d: convoy %d turns for home with %d aboard (%.1f s of"
                                + " pickup)%n", tick, number, aboard, (tick - partFrom) * BattleSimulation.TICK_DT);
                    }
                    boolean pulledAway = outboundAt >= 0 && (tick - outboundAt >= PULL_AWAY
                            || alive(sim, hull) && distance(sim, hull, gate) < 6f);
                    if (pulledAway || tick - partFrom >= PICKUP_CAP) {
                        System.out.printf("    [convoy-run] tick %d: pickup ends (%s); drive begins. %s%n", tick,
                                pulledAway ? "truck pulling out" : "cap", convoys.describe(sim));
                        part = Part.DRIVE;
                        run.begin(part, DRIVE_EVERY);
                        partFrom = tick;
                        continue;
                    }
                }
                case DRIVE -> {
                    target = alive(sim, hull) ? where(sim, hull) : cam;
                    boolean near = at != null && at[0] <= entry + CAMP_NEAR;
                    if (near || reading == null || aboard < lastAboard || tick - partFrom >= DRIVE_CAP) {
                        System.out.printf("    [convoy-run] tick %d: drive ends after %.1f s of battle (%s); drop-off"
                                        + " begins. %s%n", tick, (tick - partFrom) * BattleSimulation.TICK_DT,
                                near ? "near the camp" : "cap or unloading", convoys.describe(sim));
                        part = Part.DROPOFF;
                        run.begin(part, 1);
                        partFrom = tick;
                        continue;
                    }
                }
                default -> {
                    target = dropHold;
                    if (aboard < lastAboard && downAt < 0) downAt = tick;
                    if (doneAt < 0 && aboard == 0 && (phase == CordonConvoys.Phase.OUT || reading == null
                            || !convoys.unloading()) && downAt >= 0) {
                        doneAt = tick;
                        System.out.printf("    [convoy-run] tick %d: everyone down (%.1f s of drop-off). %s%n", tick,
                                (tick - partFrom) * BattleSimulation.TICK_DT, convoys.describe(sim));
                    }
                    if (doneAt >= 0 && tick - doneAt >= TAIL || tick - partFrom >= DROPOFF_CAP) {
                        run.end();
                        System.out.printf("    [convoy-run] convoy %d: %d seats, %d aboard at most, sheltered %d,"
                                + " out %d, lost %d%n", number, seats, maxAboard, convoys.sheltered(),
                                convoys.peopleOut(), convoys.peopleLost());
                        return false;
                    }
                }
            }
            lastAboard = aboard;
            // The camera eases toward its target, the same per battle tick whatever the frame rate.
            int every = run.every();
            float share = 1f - (float) Math.pow(1f - EASE, every);
            for (int i = 0; i < 3; i++) cam[i] += (target[i] - cam[i]) * share;
            if ((tick - partFrom) % every == 0) {
                BufferedImage frame = renderer.refocus(cam[0], cam[1], cam[2])
                        .render(sim, "", ReviewAnnotations.NONE, fx);
                run.add(frame);
                if (part == Part.PICKUP && !boardStill && aboard >= 2) { stills.add(copy(frame)); boardStill = true; }
                if (part == Part.PICKUP && !halfStill && seats > 0 && aboard >= seats * 2 / 3) {
                    stills.add(copy(frame));
                    halfStill = true;
                }
                if (part == Part.PICKUP && !outStill && outboundAt >= 0 && tick - outboundAt >= 90) {
                    stills.add(copy(frame));
                    outStill = true;
                }
                if (part == Part.DRIVE && !gapStill && at != null && at[0] <= convoys.gapIndex()) {
                    stills.add(copy(frame));
                    gapStill = true;
                }
                if (part == Part.DROPOFF && !downStill && downAt >= 0 && tick - downAt >= 150) {
                    stills.add(copy(frame));
                    downStill = true;
                }
            }
            if (sim.getSimTickIndex() % 150 == 0) {
                System.out.printf("    [convoy-run] tick %d %s: %s%n", tick, part.name().toLowerCase(Locale.ROOT),
                        convoys.describe(sim));
            }
            step(sim, run, cam, fx, true);
        }
    }

    /** One battle tick, logging its sounds to the part being recorded (heard from the camera), drawing nothing. */
    private static void step(BattleSimulation sim, Run run, float[] cam, ReviewImpactFx fx, boolean recording) {
        if (run != null && run.log != null) run.log.tick(sim, new float[] {cam[0], cam[1]});
        if (run != null) run.ticks++;
        sim.advance(BattleSimulation.TICK_DT);
        // The screen fades roofs toward what the company sees every frame; without it every roof stays opaque.
        BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
        if (recording) fx.feed(sim);
    }

    /**
     * The first convoy (past {@code skipped}) coming in or loading whose lead truck is within {@link #APPROACH} cells
     * of the yard's gate, or 0.
     */
    private static int approaching(BattleSimulation sim, CordonConvoys convoys, int[] gate, int skipped) {
        for (CordonConvoys.Reading r : convoys.readings(sim)) {
            if (r.number() <= skipped) continue;
            if (r.phase() != CordonConvoys.Phase.INBOUND && r.phase() != CordonConvoys.Phase.LOADING) continue;
            long hull = hullOf(sim, convoys, r.number(), 0);
            if (hull != 0L && distance(sim, hull, gate) <= APPROACH) return r.number();
        }
        return 0;
    }

    private static CordonConvoys.Reading reading(BattleSimulation sim, CordonConvoys convoys, int number) {
        for (CordonConvoys.Reading r : convoys.readings(sim)) if (r.number() == number) return r;
        return null;
    }

    /** The hull of truck {@code index} of convoy {@code number}, matched by where it stands; 0 when it is not on the road. */
    private static long hullOf(BattleSimulation sim, CordonConvoys convoys, int number, int index) {
        int[] t = convoys.truck(sim, number, index);
        if (t == null) return 0L;
        for (long hull : convoys.trucks()) {
            if (!alive(sim, hull)) continue;
            GroundBody body = sim.convoy().body(hull);
            if ((int) body.x == t[2] && (int) body.y == t[3]) return hull;
        }
        return 0L;
    }

    private static boolean alive(BattleSimulation sim, long hull) {
        return hull != 0L && sim.convoy().isVehicle(hull) && sim.convoy().body(hull) != null
                && sim.convoy().structure(hull) > 0f;
    }

    private static float[] where(BattleSimulation sim, long hull) {
        GroundBody body = sim.convoy().body(hull);
        return new float[] {body.x, body.y, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS};
    }

    private static float distance(BattleSimulation sim, long hull, int[] at) {
        GroundBody body = sim.convoy().body(hull);
        return (float) Math.hypot(body.x - at[0], body.y - at[1]);
    }

    /**
     * The pickup's camera: round the yard's gate, its turn and stop and the staging area's middle, at play zoom unless
     * they need a little more.
     */
    private static float[] pickupHold(BattleSimulation sim, CordonGateYard yard, int[] roadEnd) {
        List<int[]> points = new ArrayList<>();
        if (yard != null) {
            points.add(new int[] {yard.gateX(), yard.gateY()});
            points.add(new int[] {yard.turnX(), yard.turnY()});
            points.add(new int[] {yard.stopX(), yard.stopY()});
        } else {
            points.add(roadEnd);
        }
        CordonStagingArea staging = sim.getCordon().staging();
        if (staging != null && !staging.isEmpty()) points.add(new int[] {staging.centreX(), staging.centreY()});
        return frame(points);
    }

    /** The drop-off's camera: round where the trucks stop and the camp's middle. */
    private static float[] dropHold(BattleSimulation sim, CordonCamp camp, int[] drop) {
        List<int[]> points = new ArrayList<>();
        points.add(drop);
        if (camp != null) points.add(new int[] {camp.centreX(), camp.centreY()});
        return frame(points);
    }

    /** A view {centreX, centreY, cellsAcross} holding every point with a margin, never closer than play zoom. */
    static float[] frame(List<int[]> points) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        float across = Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS,
                Math.max(maxX - minX + 14f, (maxY - minY + 10f) * WIDTH / HEIGHT));
        return new float[] {(minX + maxX) / 2f + 0.5f, (minY + maxY) / 2f + 0.5f, across};
    }

    private static BufferedImage copy(BufferedImage frame) {
        BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(frame, 0, 0, null);
        return out;
    }

    /** The three parts' encodes and sound logs, and the ffmpeg pass that joins them. */
    private static final class Run {
        final Path scratch;
        final boolean sound;
        final Path modRoot;
        final List<Path> videos = new ArrayList<>();
        final List<Path> logs = new ArrayList<>();
        final List<Integer> everies = new ArrayList<>();
        final List<Integer> tickCounts = new ArrayList<>();
        FfmpegVideo encoder;
        BattleSoundLog log;
        int ticks;

        Run(Path scratch, boolean sound, Path modRoot) {
            this.scratch = scratch;
            this.sound = sound;
            this.modRoot = modRoot;
        }

        void begin(Part part, int every) throws IOException {
            end();
            Path video = scratch.resolve(part.name().toLowerCase(Locale.ROOT) + ".mp4");
            videos.add(video);
            everies.add(every);
            encoder = FfmpegVideo.open(video, WIDTH, HEIGHT, Math.round(1f / BattleSimulation.TICK_DT) + "/" + every);
            Path logFile = scratch.resolve(part.name().toLowerCase(Locale.ROOT) + ".jsonl");
            logs.add(logFile);
            log = sound ? BattleSoundLog.open(logFile).hearingTrackEnds(modRoot) : null;
            ticks = 0;
        }

        int every() { return everies.get(everies.size() - 1); }

        void add(BufferedImage frame) throws IOException { encoder.add(frame); }

        void end() throws IOException {
            if (encoder == null) return;
            encoder.finish();
            encoder.close();
            encoder = null;
            tickCounts.add(ticks);
            log = null;
        }

        void discard() throws IOException {
            end();
            for (Path p : videos) Files.deleteIfExists(p);
        }

        /**
         * Joins the parts into one 30 fps MP4: the real-time parts as they are, the drive sped up a whole number of
         * times so it lasts about {@code driveSeconds}, each with its soundtrack mixed at its own speed.
         */
        Path assemble(String ffmpeg, Path modRoot, Path scratch, float driveSeconds) throws IOException {
            int driveTicks = tickCounts.get(1);
            int speed = Math.max(DRIVE_EVERY, Math.round(driveTicks * BattleSimulation.TICK_DT / driveSeconds));
            int[] speeds = {1, speed, 1};
            List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y"));
            for (Path v : videos) command.addAll(List.of("-i", v.toString()));
            List<Path> wavs = new ArrayList<>();
            if (sound) {
                for (int i = 0; i < 3; i++) {
                    Path wav = scratch.resolve("part-" + i + ".wav");
                    // The part's length as recorded: its frames times their ticks, at its playback speed.
                    double seconds = tickCounts.get(i) * (double) BattleSimulation.TICK_DT / speeds[i];
                    BattleSoundMix.mix(modRoot, logs.get(i), seconds, wav, speeds[i]);
                    wavs.add(wav);
                    command.addAll(List.of("-i", wav.toString()));
                }
            }
            StringBuilder filter = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                filter.append(String.format(Locale.ROOT, "[%d:v]setpts=(PTS-STARTPTS)/%d,fps=30,format=yuv420p[v%d];",
                        i, speeds[i], i));
                if (sound) {
                    filter.append(String.format(Locale.ROOT, "[%d:a]aresample=48000,aformat=channel_layouts=stereo[a%d];",
                            3 + i, i));
                }
            }
            for (int i = 0; i < 3; i++) filter.append("[v").append(i).append(']').append(sound ? "[a" + i + "]" : "");
            filter.append("concat=n=3:v=1:a=").append(sound ? 1 : 0).append("[v]").append(sound ? "[a]" : "");
            Path out = scratch.resolve("convoy-run.mp4");
            command.addAll(List.of("-filter_complex", filter.toString(), "-map", "[v]"));
            if (sound) command.addAll(List.of("-map", "[a]", "-c:a", "aac", "-b:a", "192k"));
            command.addAll(List.of("-c:v", "libx264", "-preset", "medium", "-crf", "18", "-movflags", "+faststart",
                    out.toString()));
            Path errors = scratch.resolve("ffmpeg-join.log");
            Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(errors.toFile()).start();
            process.getOutputStream().close();
            try {
                if (process.waitFor() != 0) {
                    throw new IOException("ffmpeg could not join the parts: "
                            + Files.readString(errors, StandardCharsets.UTF_8).strip());
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted joining the parts", e);
            }
            double total = 0;
            for (int i = 0; i < 3; i++) total += tickCounts.get(i) * BattleSimulation.TICK_DT / speeds[i];
            System.out.printf(Locale.ROOT, "    [convoy-run] parts %.1f s, %.1f s (drive %.1f s of battle at %dx),"
                            + " %.1f s: %.1f s in all%s%n",
                    tickCounts.get(0) * BattleSimulation.TICK_DT, tickCounts.get(1) * BattleSimulation.TICK_DT / speed,
                    tickCounts.get(1) * BattleSimulation.TICK_DT, speed, tickCounts.get(2) * BattleSimulation.TICK_DT,
                    total, sound ? ", with sound" : "");
            for (Path w : wavs) Files.deleteIfExists(w);
            return out;
        }
    }
}
