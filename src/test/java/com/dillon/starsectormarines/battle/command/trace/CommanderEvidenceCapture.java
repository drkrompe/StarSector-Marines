package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattleReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.tools.snapshot.AnimatedGifWriter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional neutral-observer PNG/GIF capture attached to commander evidence. */
final class CommanderEvidenceCapture implements AutoCloseable {
    static final String CADENCE_PROPERTY =
            "commander.evidence.snapshot.everyTicks";
    static final String FRAME_DELAY_PROPERTY =
            "commander.evidence.snapshot.frameDelayMillis";
    static final String WIDTH_PROPERTY =
            "commander.evidence.snapshot.width";
    static final String HEIGHT_PROPERTY =
            "commander.evidence.snapshot.height";
    /**
     * Whether a frame carries the compound boxes, landing zones and approach
     * arrow. On by default: a whole-map frame of a Conquest is a picture of a
     * city, and without the marks nothing in it says which grey rectangle is
     * the keep. {@code false} for the bare scene.
     */
    static final String ANNOTATIONS_PROPERTY =
            "commander.evidence.snapshot.annotations";

    private static final int DEFAULT_FRAME_DELAY_MILLIS = 125;
    private static final int DEFAULT_WIDTH = 960;
    private static final int DEFAULT_HEIGHT = 640;

    private final BattleSimulation simulation;
    private final String fixtureId;
    private final int cadenceTicks;
    private final int frameDelayMillis;
    private final int width;
    private final int height;
    private final boolean annotated;
    private final Path output;
    private final Path frames;
    private final AnimatedGifWriter gif;
    private final List<Frame> captured = new ArrayList<>();
    private final FrameSource frameSource;
    private BattleReviewFrameRenderer frameRenderer;
    private int nextTick;
    private int lastTick = -1;
    private boolean closed;

    /**
     * How one frame is drawn. The battle review renderer in production; the
     * seam exists so a test can ask what {@link #close()} does when making a
     * frame fails, which is the case that used to leave {@code review.gif}
     * open.
     */
    @FunctionalInterface
    interface FrameSource {
        BufferedImage render(BattleSimulation simulation, String caption,
                             ReviewAnnotations marks) throws IOException;
    }

    static CommanderEvidenceCapture open(Path reportRoot, String fixtureId,
                                         BattleSimulation simulation)
            throws IOException {
        return open(reportRoot, fixtureId, simulation, null);
    }

    static CommanderEvidenceCapture open(Path reportRoot, String fixtureId,
                                         BattleSimulation simulation,
                                         FrameSource frameSource)
            throws IOException {
        int cadence = integerProperty(CADENCE_PROPERTY, 0);
        if (reportRoot == null) {
            return new CommanderEvidenceCapture();
        }
        if (cadence < 1) {
            if (cadence == 0) {
                EvidenceCleanup.deleteTree(outputPath(reportRoot, fixtureId));
                return new CommanderEvidenceCapture();
            }
            throw new IllegalArgumentException(
                    CADENCE_PROPERTY + " must be positive");
        }
        return new CommanderEvidenceCapture(reportRoot, fixtureId,
                simulation, cadence,
                boundedProperty(FRAME_DELAY_PROPERTY,
                        DEFAULT_FRAME_DELAY_MILLIS, 10, 60_000),
                boundedProperty(WIDTH_PROPERTY, DEFAULT_WIDTH, 160, 4_096),
                boundedProperty(HEIGHT_PROPERTY, DEFAULT_HEIGHT, 120, 4_096),
                booleanProperty(ANNOTATIONS_PROPERTY, true), frameSource);
    }

    private CommanderEvidenceCapture() {
        simulation = null;
        fixtureId = "";
        cadenceTicks = 0;
        frameDelayMillis = 0;
        width = 0;
        height = 0;
        annotated = false;
        output = null;
        frames = null;
        gif = null;
        frameSource = null;
    }

    private CommanderEvidenceCapture(Path reportRoot, String fixtureId,
                                     BattleSimulation simulation,
                                     int cadenceTicks, int frameDelayMillis,
                                     int width, int height,
                                     boolean annotated,
                                     FrameSource frameSource)
            throws IOException {
        if (simulation == null) {
            throw new IllegalArgumentException("simulation is required");
        }
        this.simulation = simulation;
        this.fixtureId = sanitize(fixtureId);
        this.cadenceTicks = cadenceTicks;
        this.frameDelayMillis = frameDelayMillis;
        this.width = width;
        this.height = height;
        this.annotated = annotated;
        this.frameSource = frameSource;
        output = outputPath(reportRoot, this.fixtureId);
        EvidenceCleanup.deleteTree(output);
        frames = output.resolve("frames");
        Files.createDirectories(frames);
        gif = new AnimatedGifWriter(output.resolve("review.gif"),
                frameDelayMillis);
        // A constructor that throws hands the caller nothing to close, so the
        // opening frame is taken under a guard of its own: without it a first
        // frame that fails leaves the encoder holding review.gif for the rest
        // of the run, and nothing can ever delete the staging tree again.
        try {
            capture();
        } catch (Throwable failure) {
            closeWriter(failure);
            throw failure;
        }
        nextTick = cadenceTicks;
    }

    void afterAdvance() throws IOException {
        if (!enabled()) return;
        int tick = simulation.getSimTickIndex();
        if (tick < nextTick && !simulation.isComplete()) return;
        capture();
        while (nextTick <= tick) nextTick += cadenceTicks;
    }

    private void capture() throws IOException {
        int tick = simulation.getSimTickIndex();
        if (tick == lastTick) return;
        ReviewAnnotations marks =
                annotated ? BattleReviewAnnotations.forBattle(simulation)
                        : ReviewAnnotations.NONE;
        BufferedImage image = frameSource != null
                ? frameSource.render(simulation, caption(tick), marks)
                : frames().render(simulation, caption(tick), marks);
        String filename = String.format(Locale.ROOT,
                "frame-%04d-tick-%06d.png", captured.size(), tick);
        Path frame = frames.resolve(filename);
        if (!ImageIO.write(image, "PNG", frame.toFile())) {
            throw new IOException("No PNG writer is installed");
        }
        gif.append(image);
        captured.add(new Frame(tick, "frames/" + filename));
        lastTick = tick;
    }

    /** Caption band text: which fixture, where in the run, and what each side has left. */
    private String caption(int tick) {
        String state = simulation.isComplete()
                ? "  \u2022  " + (simulation.getWinner() == null
                ? "COMPLETE" : simulation.getWinner() + " WIN") : "";
        return fixtureId + "  \u2022  tick " + tick
                + "  \u2022  " + String.format(Locale.ROOT, "%.1fs",
                tick * BattleSimulation.TICK_DT)
                + "  \u2022  M " + simulation.getRoster().factionLiveCount(Faction.MARINE)
                + " / D " + simulation.getRoster().factionLiveCount(Faction.DEFENDER)
                + " / C " + simulation.getRoster().factionLiveCount(Faction.CIVILIAN)
                + state;
    }

    private BattleReviewFrameRenderer frames() {
        if (frameRenderer == null) {
            frameRenderer = new BattleReviewFrameRenderer(
                    Path.of("mod").toAbsolutePath().normalize(), width, height);
        }
        return frameRenderer;
    }

    private boolean enabled() {
        return gif != null;
    }

    /**
     * Releases the encoder whatever else happened, then reports the first
     * failure with the rest suppressed.
     *
     * <p>The closing frame is taken from a simulation that may be in whatever
     * state ended the run, so this step fails in more ways than an
     * {@link IOException}: an {@link AssertionError} or a runtime failure out
     * of the renderer used to skip straight past {@code gif.close()} and leave
     * {@code review.gif} open. On Windows the harness's own
     * {@code finally}-block cleanup then could not delete the staging tree, and
     * that "the process cannot access the file" is what the run reported —
     * instead of the failure that caused it.
     */
    @Override
    public void close() throws IOException {
        if (!enabled() || closed) return;
        closed = true;
        Throwable failure = null;
        try {
            capture();
        } catch (Throwable captureFailure) {
            failure = captureFailure;
        } finally {
            failure = closeWriter(failure);
        }
        if (failure != null) throw asCheckedFailure(failure);
        writeManifest();
        System.out.println("[commander-evidence-visual] "
                + output.resolve("review.gif") + " (" + captured.size()
                + " frames)");
    }

    /**
     * Closes the GIF encoder, folding a failure of its own into
     * {@code failure} rather than over it. Returns whichever throwable the
     * caller should now report.
     */
    private Throwable closeWriter(Throwable failure) {
        try {
            gif.close();
            return failure;
        } catch (Throwable closeFailure) {
            if (failure == null) return closeFailure;
            if (failure != closeFailure) failure.addSuppressed(closeFailure);
            return failure;
        }
    }

    /** Reports {@code failure} as itself where the signature allows it, wrapped where it does not. */
    private static IOException asCheckedFailure(Throwable failure) {
        if (failure instanceof IOException checked) return checked;
        if (failure instanceof RuntimeException unchecked) throw unchecked;
        if (failure instanceof Error error) throw error;
        return new IOException("Visual evidence capture failed", failure);
    }

    private void writeManifest() throws IOException {
        try {
            JSONArray frameRows = new JSONArray();
            for (Frame frame : captured) {
                frameRows.put(new JSONObject()
                        .put("tick", frame.tick())
                        .put("file", frame.file()));
            }
            JSONObject manifest = new JSONObject()
                    .put("schemaVersion", 1)
                    .put("perspective", "NEUTRAL_OBSERVER")
                    .put("fixture", fixtureId)
                    .put("cadenceTicks", cadenceTicks)
                    .put("frameDelayMillis", frameDelayMillis)
                    .put("width", width)
                    .put("height", height)
                    .put("frameCount", captured.size())
                    .put("terminalTick", simulation.getSimTickIndex())
                    .put("neutralUnitMarkers", true)
                    .put("annotations", annotated)
                    .put("omittedCommandKinds", new JSONArray()
                            .put("CUSTOM")
                            .put("RIBBON"))
                    .put("frames", frameRows);
            Files.writeString(output.resolve("manifest.json"),
                    manifest.toString(2) + '\n', StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IOException("Could not write visual evidence manifest",
                    failure);
        }
    }

    private static int boundedProperty(String name, int fallback,
                                       int minimum, int maximum) {
        int value = integerProperty(name, fallback);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between "
                    + minimum + " and " + maximum);
        }
        return value;
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        String configured = System.getProperty(name, "").trim();
        return configured.isBlank() ? fallback : Boolean.parseBoolean(configured);
    }

    private static int integerProperty(String name, int fallback) {
        String configured = System.getProperty(name, "").trim();
        return configured.isBlank() ? fallback : Integer.parseInt(configured);
    }

    private static String sanitize(String value) {
        String clean = value == null ? "fixture"
                : value.replaceAll("[^A-Za-z0-9_-]", "-");
        return clean.isBlank() ? "fixture" : clean;
    }

    private static Path outputPath(Path reportRoot, String fixtureId) {
        return reportRoot.toAbsolutePath().normalize()
                .resolve("visuals").resolve(sanitize(fixtureId));
    }

    private record Frame(int tick, String file) { }
}
