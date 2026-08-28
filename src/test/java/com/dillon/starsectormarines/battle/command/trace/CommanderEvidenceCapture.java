package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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
import java.util.Comparator;
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

    private static final int DEFAULT_FRAME_DELAY_MILLIS = 125;
    private static final int DEFAULT_WIDTH = 960;
    private static final int DEFAULT_HEIGHT = 640;

    private final BattleSimulation simulation;
    private final String fixtureId;
    private final int cadenceTicks;
    private final int frameDelayMillis;
    private final int width;
    private final int height;
    private final Path output;
    private final Path frames;
    private final AnimatedGifWriter gif;
    private final List<Frame> captured = new ArrayList<>();
    private BattleReviewFrameRenderer frameRenderer;
    private int nextTick;
    private int lastTick = -1;
    private boolean closed;

    static CommanderEvidenceCapture open(Path reportRoot, String fixtureId,
                                         BattleSimulation simulation)
            throws IOException {
        int cadence = integerProperty(CADENCE_PROPERTY, 0);
        if (reportRoot == null) {
            return new CommanderEvidenceCapture();
        }
        if (cadence < 1) {
            if (cadence == 0) {
                deleteTree(outputPath(reportRoot, fixtureId));
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
                boundedProperty(HEIGHT_PROPERTY, DEFAULT_HEIGHT, 120, 4_096));
    }

    private CommanderEvidenceCapture() {
        simulation = null;
        fixtureId = "";
        cadenceTicks = 0;
        frameDelayMillis = 0;
        width = 0;
        height = 0;
        output = null;
        frames = null;
        gif = null;
    }

    private CommanderEvidenceCapture(Path reportRoot, String fixtureId,
                                     BattleSimulation simulation,
                                     int cadenceTicks, int frameDelayMillis,
                                     int width, int height) throws IOException {
        if (simulation == null) {
            throw new IllegalArgumentException("simulation is required");
        }
        this.simulation = simulation;
        this.fixtureId = sanitize(fixtureId);
        this.cadenceTicks = cadenceTicks;
        this.frameDelayMillis = frameDelayMillis;
        this.width = width;
        this.height = height;
        output = outputPath(reportRoot, this.fixtureId);
        deleteTree(output);
        frames = output.resolve("frames");
        Files.createDirectories(frames);
        gif = new AnimatedGifWriter(output.resolve("review.gif"),
                frameDelayMillis);
        capture();
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
        BufferedImage image = frames().render(simulation, caption(tick));
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

    @Override
    public void close() throws IOException {
        if (!enabled() || closed) return;
        closed = true;
        IOException failure = null;
        try {
            capture();
        } catch (IOException captureFailure) {
            failure = captureFailure;
        }
        try {
            gif.close();
        } catch (IOException closeFailure) {
            if (failure == null) failure = closeFailure;
            else failure.addSuppressed(closeFailure);
        }
        if (failure != null) throw failure;
        writeManifest();
        System.out.println("[commander-evidence-visual] "
                + output.resolve("review.gif") + " (" + captured.size()
                + " frames)");
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

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Frame(int tick, String file) { }
}
