package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleSoundLog;
import com.dillon.starsectormarines.ops.battleview.BattleSoundMix;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import com.dillon.starsectormarines.tools.snapshot.RawFrameDump;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owner-facing look frames in one call: a subject stood on real generated ground ({@link LookGround}), played
 * forward with every effect the review frame can draw, and framed through the
 * {@link BattleReviewFrameRenderer#look look preset} at play zoom and close.
 *
 * <p><b>This is the picture to show the owner, never a verdict scene's arena frames.</b> A verdict frame is drawn
 * on a bare grid with faction dots, labels and a caption band over it because it is evidence for a mechanism; a
 * look frame answers "how does this look in a battle", which only the battle's own ground, props, smoke, contrails
 * and shadows can.
 *
 * <p>A shot is written as {@code look/<shot>-<view>.mp4} (H.264, streamed into {@code ffmpeg} as the frames render,
 * {@link FfmpegVideo}) plus {@code look/<shot>-<view>-<n>.png} stills of that view's busiest moments: the recorded
 * frames with the most effect particles (launch smoke, trails, blasts, dust) laid inside it, at least a second apart.
 * {@code -Dsnapshot.video=gif} writes a full-colour GIF instead, as does a machine with no {@code ffmpeg}, saying so.
 * {@code -Dsnapshot.lookSound=true} logs the battle's sound starts ({@link BattleSoundLog}) and muxes a soundtrack
 * mixed from them ({@link BattleSoundMix}) into each MP4. A {@link BehaviorSceneSnapshotSuite} lists its shots in
 * {@link BehaviorSceneSnapshotSuite#lookShots()} and gets them alongside its verdict loops; any other suite calls
 * {@link #record}.
 */
public final class LookReview {

    /** The owner's preview size: every look frame is 1920x1080 at 100% scale. */
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;

    private LookReview() { }

    /**
     * Where a shot stands: the ground, the freshly generated map, and the window chosen on it; and the subjects the
     * stage named ({@link #subject}), which a {@link View#follow following view} keeps in frame.
     */
    public record Placement(LookGround ground, MapResult map, LookGround.Site site, Map<String, Long> subjects,
                            List<Consumer<BattleSimulation>> beforeTicks, List<Integer> selectedSquads) {

        public Placement(LookGround ground, MapResult map, LookGround.Site site) {
            this(ground, map, site, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>());
        }

        /** Draws the shot as though the player had this squad selected, for a mark shown only on a selection. */
        public void select(int squadId) {
            selectedSquads.add(squadId);
        }

        /**
         * Runs {@code step} before every simulation tick of the recording, as a mission's own serial tick drives what
         * it owns (an allied command, a screened advance) in a real battle.
         */
        public void beforeTick(Consumer<BattleSimulation> step) {
            beforeTicks.add(step);
        }

        /** Names a spawned unit or hull, so a following view can find it. Returns the id. */
        public long subject(String key, long id) {
            subjects.put(key, id);
            return id;
        }

        /** A scene builder over this placement, taking coordinates local to the window. */
        public SceneBuilder builder() {
            return SceneBuilder.onTerrain(ground, map, site).missionCompletion(false);
        }

        /**
         * One still, unarmed body a side, each 24 cells beyond the window's west and east edges, out of every view: the
         * simulation stops ticking a side that has no body, and a look shot whose subjects are all hulls has none.
         */
        public SceneBuilder holdouts(SceneBuilder builder) {
            float[] west = openNear(-24f, site.height() / 2f);
            float[] east = openNear(site.width() + 24f, site.height() / 2f);
            return builder
                    .unit("look-marine-holdout", Faction.MARINE, UnitType.MARINE,
                            (int) west[0] - site.x0(), (int) west[1] - site.y0(), spec -> spec.moveSpeed(0f))
                    .unit("look-defender-holdout", Faction.DEFENDER, UnitType.MARINE,
                            (int) east[0] - site.x0(), (int) east[1] - site.y0(), spec -> spec.moveSpeed(0f));
        }

        /**
         * The centre of the open cell nearest the window-local point {@code (localX, localY)}, in map cells: where to
         * set a hull down so it never lands in a building or a hedge the generator happened to put there.
         */
        public float[] openNear(float localX, float localY) {
            int cx = (int) Math.floor(site.x(localX));
            int cy = (int) Math.floor(site.y(localY));
            for (int r = 0; r < 24; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                        int x = cx + dx;
                        int y = cy + dy;
                        if (!map.grid.inBounds(x, y) || LookGround.blocked(map.grid, map.topology, x, y)) continue;
                        return new float[] {x + 0.5f, y + 0.5f};
                    }
                }
            }
            return new float[] {site.x(localX), site.y(localY)};
        }
    }

    /**
     * One camera on a shot: {@code focus} answers {@code {centreX, centreY, cellsAcross}} in map cells for the frame
     * being drawn, so a view may follow its subject.
     */
    public record View(String name, Function<BattleSimulation, float[]> focus) {
        public View {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(focus, "focus");
        }

        /** A fixed camera at play zoom ({@link BattleReviewFrameRenderer#PLAY_ZOOM_CELLS} cells across). */
        public static View play(float centreX, float centreY) {
            return new View("play", sim -> new float[] {centreX, centreY, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS});
        }

        /** A fixed close camera {@code cellsAcross} wide. */
        public static View close(String name, float centreX, float centreY, float cellsAcross) {
            return new View(name, sim -> new float[] {centreX, centreY, cellsAcross});
        }

        /**
         * A camera {@code cellsAcross} wide on the middle of the named subjects, unit or hull, as they move; one that
         * has died or driven off stays where it was last seen, so the frame does not jump. Until the stage has named
         * them it holds on the window's centre.
         */
        public static View follow(String name, Placement placement, float cellsAcross, String... keys) {
            float[] last = new float[2 * keys.length];
            Arrays.fill(last, Float.NaN);
            return new View(name, sim -> {
                float sumX = 0f;
                float sumY = 0f;
                int known = 0;
                for (int i = 0; i < keys.length; i++) {
                    Long id = placement.subjects().get(keys[i]);
                    if (id != null) {
                        float[] at = where(sim, id);
                        if (at != null) {
                            last[2 * i] = at[0];
                            last[2 * i + 1] = at[1];
                        }
                    }
                    if (Float.isNaN(last[2 * i])) continue;
                    sumX += last[2 * i];
                    sumY += last[2 * i + 1];
                    known++;
                }
                if (known == 0) {
                    return new float[] {placement.site().centreX(), placement.site().centreY(), cellsAcross};
                }
                return new float[] {sumX / known, sumY / known, cellsAcross};
            });
        }

        /** A following camera at play zoom. */
        public static View followAtPlay(Placement placement, String... keys) {
            return follow("play", placement, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, keys);
        }

        private static float[] where(BattleSimulation sim, long id) {
            if (sim.convoy().isVehicle(id)) {
                GroundBody body = sim.convoy().body(id);
                return body == null ? null : new float[] {body.x, body.y};
            }
            if (!sim.world().isAlive(id)) return null;
            return new float[] {sim.world().renderX(id), sim.world().renderY(id)};
        }
    }

    /**
     * One look recording.
     *
     * @param name       the shot's name in its file names
     * @param ground     the ground it stands on
     * @param footprintW width of the window the subject needs, in cells
     * @param footprintH height of that window
     * @param stage      stands the subject up on the placement and returns the simulation (built through
     *                   {@link Placement#builder()}, then whatever the scene spawns itself)
     * @param views      the cameras; given the placement, so a view can be written in the window's own coordinates
     * @param ticks      ticks to play
     * @param everyTicks a frame is recorded every this many ticks
     * @param stills     how many stills of the busiest moments to keep per view
     * @param stillFrames recorded-frame indices to keep as stills instead of the busiest moments; empty for those
     * @param speed      how many times real speed the recording plays at: 1 plays a frame every {@code everyTicks}
     *                   in real time; 4 with a frame every 4 ticks is 30 frames a second, and the soundtrack's starts
     *                   move with it while each sound keeps its own length and pitch
     */
    public record Shot(String name, LookGround ground, int footprintW, int footprintH,
                       Function<Placement, BattleSimulation> stage, Function<Placement, List<View>> views,
                       int ticks, int everyTicks, int stills, int[] stillFrames, int speed) {
        public Shot {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(ground, "ground");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(views, "views");
            if (ticks <= 0 || everyTicks <= 0) throw new IllegalArgumentException("ticks and everyTicks must be positive");
            if (speed <= 0) throw new IllegalArgumentException("speed must be positive");
            stillFrames = stillFrames == null ? new int[0] : stillFrames.clone();
        }

        /** A shot played back in real time. */
        public Shot(String name, LookGround ground, int footprintW, int footprintH,
                    Function<Placement, BattleSimulation> stage, Function<Placement, List<View>> views,
                    int ticks, int everyTicks, int stills, int[] stillFrames) {
            this(name, ground, footprintW, footprintH, stage, views, ticks, everyTicks, stills, stillFrames, 1);
        }

        /** A shot whose stills are its busiest moments. */
        public Shot(String name, LookGround ground, int footprintW, int footprintH,
                    Function<Placement, BattleSimulation> stage, Function<Placement, List<View>> views,
                    int ticks, int everyTicks, int stills) {
            this(name, ground, footprintW, footprintH, stage, views, ticks, everyTicks, stills, null);
        }

        /** A shot on {@link LookGround#verdant()} ground, a frame every 6 ticks, four stills a view. */
        public static Shot of(String name, int footprintW, int footprintH, Function<Placement, BattleSimulation> stage,
                              Function<Placement, List<View>> views, int ticks) {
            return new Shot(name, LookGround.verdant(), footprintW, footprintH, stage, views, ticks, 6, 4, null);
        }

        public Shot on(LookGround other) {
            return new Shot(name, other, footprintW, footprintH, stage, views, ticks, everyTicks, stills, stillFrames,
                    speed);
        }

        /**
         * Keeps these recorded frames (a frame every {@code everyTicks}) as each view's stills, for a subject whose
         * telling moment is not its busiest: a pack circling before it dives fires nothing.
         */
        public Shot withStills(int... frames) {
            return new Shot(name, ground, footprintW, footprintH, stage, views, ticks, everyTicks, stills, frames, speed);
        }

        /** Plays the recording back at {@code times} real speed. */
        public Shot atSpeed(int times) {
            return new Shot(name, ground, footprintW, footprintH, stage, views, ticks, everyTicks, stills, stillFrames,
                    times);
        }
    }

    /** {@code mp4} (the default) or {@code gif}: what a view's recording is written as. */
    public static final String VIDEO_PROPERTY = "snapshot.video";
    /** {@code true} gives each MP4 a soundtrack mixed from the battle's own sound starts ({@link BattleSoundLog}). */
    public static final String SOUND_PROPERTY = "snapshot.lookSound";
    /** Where the sound logs ({@code <shot>.jsonl}) are written; default {@code build/look-sound}. */
    public static final String SOUND_LOG_DIR_PROPERTY = "snapshot.lookSound.logDir";

    private static final AtomicBoolean NO_FFMPEG_SAID = new AtomicBoolean();

    /** Plays {@code shot} once and returns its recordings and stills, each {@link #WIDTH} x {@link #HEIGHT}. */
    public static List<SnapshotArtifact> record(Path modRoot, Shot shot) throws Exception {
        int width = WIDTH;
        int height = HEIGHT;
        Path projectRoot = modRoot.toAbsolutePath().normalize().getParent();
        boolean mp4 = mp4();
        boolean sound = Boolean.getBoolean(SOUND_PROPERTY);
        if (sound && !mp4) {
            System.out.println("    [look/" + shot.name() + "] no soundtrack: " + SOUND_PROPERTY
                    + " needs the MP4 recording");
            sound = false;
        }
        boolean raw = !blank(System.getProperty(RawFrameDump.PROPERTY));
        MapResult map = shot.ground().generate();
        LookGround.Site site = LookGround.openSite(map, shot.footprintW(), shot.footprintH());
        Placement placement = new Placement(shot.ground(), map, site);
        List<View> views = shot.views().apply(placement);
        BattleSimulation sim = shot.stage().apply(placement);
        Path soundLog = null;
        if (sound) {
            String dir = System.getProperty(SOUND_LOG_DIR_PROPERTY);
            soundLog = (blank(dir) ? projectRoot.resolve("build/look-sound") : Path.of(dir))
                    .resolve(shot.name() + ".jsonl");
            // A view of its own as the ear: asking the recorded one would move a following camera's memory.
            BattleSoundLog.attach(placement, shot.views().apply(placement).get(0), soundLog);
        }
        Path scratch = projectRoot.resolve("build/tmp/look-video");
        List<FfmpegVideo> encoders = new ArrayList<>();
        try {
            ReviewImpactFx fx = new ReviewImpactFx(modRoot);
            int gap = Math.max(1, Math.round(1f / (BattleSimulation.TICK_DT * shot.everyTicks())));
            List<BattleReviewFrameRenderer> renderers = new ArrayList<>(views.size());
            List<List<BufferedImage>> frames = new ArrayList<>(views.size());
            List<StillCandidates> candidates = new ArrayList<>(views.size());
            if (mp4) Files.createDirectories(scratch);
            Selection selection = null;
            if (!placement.selectedSquads().isEmpty()) {
                selection = new Selection();
                selection.selectSquads(placement.selectedSquads(), placement.selectedSquads().get(0), 0L, false);
            }
            for (View view : views) {
                float[] f = view.focus().apply(sim);
                renderers.add(BattleReviewFrameRenderer.look(modRoot, width, height, f[0], f[1], f[2])
                        .selecting(selection));
                frames.add(!mp4 || raw ? new CompressedFrameList() : List.of());
                candidates.add(new StillCandidates(shot.stillFrames(), shot.stills(), gap));
                if (mp4) {
                    Path out = Files.createTempFile(scratch, shot.name() + "-" + view.name() + "-", ".mp4");
                    encoders.add(FfmpegVideo.open(out, width, height,
                            Math.round(shot.speed() / BattleSimulation.TICK_DT) + "/" + shot.everyTicks()));
                }
            }
            List<List<Integer>> activity = new ArrayList<>();
            for (int v = 0; v < views.size(); v++) activity.add(new ArrayList<>());
            int fired = 0;
            for (int tick = 0; tick <= shot.ticks(); tick++) {
                if (tick > 0) {
                    for (Consumer<BattleSimulation> step : placement.beforeTicks()) step.accept(sim);
                    sim.advance(BattleSimulation.TICK_DT);
                    // The game screen fades roofs toward what the company can see every frame; without it every roof
                    // stays opaque and fights indoors are hidden.
                    BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
                    fx.feed(sim);
                    fired += sim.getShotsThisFrame().size();
                }
                if (tick % shot.everyTicks() != 0) continue;
                for (int v = 0; v < views.size(); v++) {
                    float[] f = views.get(v).focus().apply(sim);
                    BattleReviewFrameRenderer renderer = renderers.get(v).refocus(f[0], f[1], f[2]);
                    BufferedImage frame = renderer.render(sim, "", ReviewAnnotations.NONE, fx);
                    // Launch smoke, trails, blasts and dust laid in this view: its busiest moments are its stills.
                    int index = activity.get(v).size();
                    activity.get(v).add(fx.lastPainted());
                    candidates.get(v).offer(index, fx.lastPainted(), frame);
                    if (mp4) encoders.get(v).add(frame);
                    if (!mp4 || raw) frames.get(v).add(frame);
                }
            }
            int recordedFrames = activity.get(0).size();
            Path wav = null;
            if (sound) {
                wav = Files.createTempFile(scratch, shot.name() + "-", ".wav");
                BattleSoundMix.mix(modRoot, soundLog,
                        recordedFrames * shot.everyTicks() * (double) BattleSimulation.TICK_DT / shot.speed(), wav,
                        shot.speed());
            }
            int delay = Math.max(20, Math.round(1000f * BattleSimulation.TICK_DT * shot.everyTicks() / shot.speed()));
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            StringBuilder stillsAt = new StringBuilder();
            for (int v = 0; v < views.size(); v++) {
                int recorded = activity.get(v).size();
                int[] picked = shot.stillFrames().length > 0
                        ? Arrays.stream(shot.stillFrames()).filter(f -> f >= 0 && f < recorded).toArray()
                        : busiest(activity.get(v), shot.stills(), gap);
                stillsAt.append(' ').append(views.get(v).name()).append(Arrays.toString(picked));
                String stem = "look/" + shot.name() + "-" + views.get(v).name();
                if (mp4) {
                    Path video = encoders.get(v).finish();
                    if (wav != null) {
                        Path scored = Files.createTempFile(scratch, shot.name() + "-" + views.get(v).name() + "-",
                                ".mp4");
                        FfmpegVideo.mux(video, wav, scored);
                        Files.delete(video);
                        video = scored;
                    }
                    artifacts.add(SnapshotArtifact.video(stem + ".mp4", video, frames.get(v)));
                } else {
                    artifacts.add(SnapshotArtifact.colorAnimation(stem + ".gif", frames.get(v), delay));
                }
                for (int i = 0; i < picked.length; i++) {
                    artifacts.add(new SnapshotArtifact(String.format("%s-%02d.png", stem, i + 1),
                            candidates.get(v).frame(picked[i])));
                }
            }
            if (wav != null) Files.deleteIfExists(wav);
            System.out.printf("    [look/%s] window %d,%d %dx%d (%d blocked cells), %d frames, %d rounds fired,"
                            + " stills at%s%n",
                    shot.name(), site.x0(), site.y0(), site.width(), site.height(), site.blockedCells(),
                    recordedFrames, fired, stillsAt);
            return artifacts;
        } finally {
            for (FfmpegVideo encoder : encoders) encoder.close();
            sim.close();
        }
    }

    /** True unless {@code -Dsnapshot.video=gif} asks for GIFs, or there is no ffmpeg to encode with. */
    private static boolean mp4() {
        String video = System.getProperty(VIDEO_PROPERTY, "mp4").trim().toLowerCase(Locale.ROOT);
        if (video.equals("gif")) return false;
        if (!video.equals("mp4")) throw new IllegalArgumentException(VIDEO_PROPERTY + " must be mp4 or gif: " + video);
        if (FfmpegVideo.executable() != null) return true;
        if (NO_FFMPEG_SAID.compareAndSet(false, true)) {
            System.out.println("    [look] ffmpeg is not on PATH (nor named by FFMPEG): writing look recordings as"
                    + " GIFs, which are far larger; install ffmpeg for MP4s");
        }
        return false;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * The frames of one view that can still become its stills, kept as they are recorded so the whole recording never
     * has to be held: the named frames when the shot names them, otherwise the best frames by activity (ties to the
     * earlier) that {@link #busiest} could still pick. Its k-th pick is ranked at worst {@code (k-1)(2*gap-1)+1}, as
     * every frame ranked above it is a pick or within {@code gap - 1} of one, so that many are enough.
     */
    static final class StillCandidates {
        private final Set<Integer> named = new HashSet<>();
        private final int keep;
        /** Best first: higher activity, then the earlier frame. Entries are {@code {activity, index}}. */
        private final TreeSet<int[]> ranked = new TreeSet<>((a, b) -> a[0] != b[0]
                ? Integer.compare(b[0], a[0]) : Integer.compare(a[1], b[1]));
        private final Map<Integer, List<BufferedImage>> kept = new HashMap<>();

        StillCandidates(int[] stillFrames, int stills, int gap) {
            for (int f : stillFrames) named.add(f);
            keep = stillFrames.length > 0 ? 0 : Math.max(0, (stills - 1) * (2 * gap - 1) + 1);
        }

        void offer(int index, int activity, BufferedImage frame) {
            if (!named.isEmpty()) {
                if (named.contains(index)) kept.put(index, packed(frame));
                return;
            }
            if (keep == 0) return;
            int[] entry = {activity, index};
            if (ranked.size() >= keep && ranked.comparator().compare(entry, ranked.last()) > 0) return;
            ranked.add(entry);
            kept.put(index, packed(frame));
            if (ranked.size() > keep) kept.remove(ranked.pollLast()[1]);
        }

        int size() {
            return kept.size();
        }

        BufferedImage frame(int index) {
            List<BufferedImage> frame = kept.get(index);
            if (frame == null) throw new IllegalStateException("frame " + index + " was not kept as a still candidate");
            return frame.get(0);
        }

        private static List<BufferedImage> packed(BufferedImage frame) {
            CompressedFrameList one = new CompressedFrameList();
            one.add(frame);
            return one;
        }
    }

    /**
     * Indices of the {@code count} frames with the highest {@code activity}, no two closer than {@code gap} frames, in
     * time order. Ties go to the earlier frame, so the pick is a pure function of the play.
     */
    static int[] busiest(List<Integer> activity, int count, int gap) {
        List<Integer> chosen = new ArrayList<>();
        boolean[] taken = new boolean[activity.size()];
        while (chosen.size() < count) {
            int best = -1;
            for (int i = 0; i < activity.size(); i++) {
                if (taken[i]) continue;
                boolean near = false;
                for (int c : chosen) near |= Math.abs(c - i) < gap;
                if (near) continue;
                if (best < 0 || activity.get(i) > activity.get(best)) best = i;
            }
            if (best < 0) break;
            taken[best] = true;
            chosen.add(best);
        }
        return chosen.stream().mapToInt(Integer::intValue).sorted().toArray();
    }
}
