package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.AnimatedGifWriter;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Watches one fight and writes it as a GIF: 1920x1080 at 100% scale, shots and impacts drawn, the whole map in frame.
 *
 * <p>The crossing is most of a fight's length and none of its interest, so frames are sparse until the first damage
 * and dense after it. The frame delay is fixed, so the crossing plays fast and the fighting plays slowly enough
 * to follow. The recording only reads the simulation; it never changes what happens, and the fight it records is the fight
 * the figures report.
 */
public final class MatchupRecording {

    /** Ticks between frames while nobody has been hurt yet: a quick look at the approach. */
    static final int APPROACH_EVERY_TICKS = 60;
    /** Ticks between frames once damage has been dealt: dense enough to follow individual engagements. */
    static final int ENGAGED_EVERY_TICKS = 8;
    static final int FRAME_DELAY_MILLIS = 100;
    /** A cap on a recording, so a long stalemate cannot produce a gigabyte; the last frames are kept. */
    static final int MAX_FRAMES = 360;
    /** The closest the camera comes, in cells across the frame. */
    private static final float MIN_CELLS_ACROSS = 40f;
    private static final float MARGIN_CELLS = 8f;
    private static final float OUTLIER_CELLS = 22f;
    private static final float CAMERA_EASE = 0.12f;
    private static final String FINAL = "";
    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;

    /**
     * Rendering shares process-wide headless draw state, so fights running side by side take turns drawing. The
     * simulation itself is not serialised; only the picture is.
     */
    private static final Object RENDER_LOCK = new Object();

    private final Path destination;
    private final String title;
    private final ReviewAnnotations annotations;
    private final BattleReviewFrameRenderer renderer;
    private final ReviewImpactFx fx;
    private float cameraX = Float.NaN, cameraY, cameraAcross;
    private AnimatedGifWriter gif;
    private int frames;
    private boolean engaged;
    private int sinceFrame = APPROACH_EVERY_TICKS;

    public MatchupRecording(Path modRoot, Path destination, String title, ReviewAnnotations annotations) {
        this.annotations = annotations;
        this.destination = destination;
        this.title = title;
        synchronized (RENDER_LOCK) {
            this.renderer = new BattleReviewFrameRenderer(modRoot, WIDTH, HEIGHT).withImpactEffects();
            this.fx = new ReviewImpactFx(modRoot);
        }
    }

    /** Called once per tick before the sim advances. {@code status} is the caption's live reading. */
    void observe(BattleSimulation sim, int tick, boolean damageDealt, String status, long[][] sides) {
        if (damageDealt) engaged = true;
        sinceFrame++;
        int every = engaged ? ENGAGED_EVERY_TICKS : APPROACH_EVERY_TICKS;
        if (sinceFrame < every) return;
        sinceFrame = 0;
        capture(sim, tick, status, sides);
    }

    /** Called once per tick after the sim advanced, so shots fired this tick can be drawn as impacts. */
    void advanced(BattleSimulation sim) {
        synchronized (RENDER_LOCK) {
            fx.feed(sim);
        }
    }

    /** The final state, so the recording always ends on the result. */
    void finish(BattleSimulation sim, int tick, String status, long[][] sides) {
        capture(sim, tick, FINAL + status, sides);
    }

    private void capture(BattleSimulation sim, int tick, String status, long[][] sides) {
        try {
            // Streamed to disk as it goes: 1920x1080 frames are six megabytes each, so holding a fight's worth would
            // exhaust the heap. Past the cap the recording stops growing but still ends on the result (`last`).
            boolean last = status.startsWith(FINAL);
            if (frames >= MAX_FRAMES && !last) return;
            BufferedImage frame;
            frameCamera(sim, sides);
            synchronized (RENDER_LOCK) {
                frame = opaque(renderer.render(sim, title + "   t=" + tick + "   " + status.replace(FINAL, ""),
                        annotations, fx));
            }
            if (gif == null) {
                Files.createDirectories(destination.getParent());
                gif = new AnimatedGifWriter(destination, FRAME_DELAY_MILLIS, AnimatedGifWriter.Palette.ADAPTIVE);
            }
            gif.append(frame);
            frames++;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Follows the fight: the camera frames the main body of each side, easing toward it frame to frame. A body far
     * from its own side's median (a routed survivor running for the map edge) is left out, or the frame would stretch
     * to keep one man in view and everything else would be a speck. Before the first damage only the first side, the
     * attackers, is framed, so the approach is not a whole-map view; once anything is hit, both are, because a fight
     * at standoff range (a mortar shelling a trench fifty cells off) is a fight between two places.
     */
    private void frameCamera(BattleSimulation sim, long[][] sides) {
        float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        int framedSides = engaged ? sides.length : 1;
        for (int s = 0; s < framedSides; s++) frameSide(sim, sides[s], box);
        if (box[0] > box[1]) {
            for (long[] side : sides) frameSide(sim, side, box);
        }
        if (box[0] > box[1]) return;
        float minX = box[0], maxX = box[1], minY = box[2], maxY = box[3];
        float aspect = (float) WIDTH / (HEIGHT - BattleReviewFrameRenderer.HEADER_HEIGHT);
        float across = Math.max(MIN_CELLS_ACROSS,
                Math.max(maxX - minX + 2 * MARGIN_CELLS, (maxY - minY + 2 * MARGIN_CELLS) * aspect));
        float x = (minX + maxX) / 2f;
        float y = (minY + maxY) / 2f;
        if (Float.isNaN(cameraX)) {
            cameraX = x; cameraY = y; cameraAcross = across;
        } else {
            cameraX += (x - cameraX) * CAMERA_EASE;
            cameraY += (y - cameraY) * CAMERA_EASE;
            cameraAcross += (across - cameraAcross) * CAMERA_EASE;
        }
        renderer.refocus(cameraX, cameraY, cameraAcross);
    }

    /** Widens {@code box} (minX, maxX, minY, maxY) to one side's living main body. */
    private static void frameSide(BattleSimulation sim, long[] bodies, float[] box) {
        float[] xs = new float[bodies.length];
        float[] ys = new float[bodies.length];
        int n = 0;
        for (long id : bodies) {
            if (sim.convoy().isVehicle(id)) {
                // A hull is not a unit: it is framed while it is whole, at its body's position.
                if (!sim.convoy().isPresent(id)) continue;
                xs[n] = sim.convoy().body(id).x;
                ys[n] = sim.convoy().body(id).y;
                n++;
                continue;
            }
            if (sim.resolveUnit(id) == 0L) continue;
            xs[n] = sim.world().cellX(id) + 0.5f;
            ys[n] = sim.world().cellY(id) + 0.5f;
            n++;
        }
        if (n == 0) return;
        float[] sx = Arrays.copyOf(xs, n);
        float[] sy = Arrays.copyOf(ys, n);
        Arrays.sort(sx);
        Arrays.sort(sy);
        float medianX = sx[n / 2];
        float medianY = sy[n / 2];
        for (int i = 0; i < n; i++) {
            if (Math.abs(xs[i] - medianX) > OUTLIER_CELLS || Math.abs(ys[i] - medianY) > OUTLIER_CELLS) continue;
            box[0] = Math.min(box[0], xs[i]);
            box[1] = Math.max(box[1], xs[i]);
            box[2] = Math.min(box[2], ys[i]);
            box[3] = Math.max(box[3], ys[i]);
        }
    }

    /** Closes the GIF; a recording with no frames writes nothing. */
    public Path write() throws IOException {
        if (gif == null) return null;
        gif.close();
        gif = null;
        return destination;
    }

    private static BufferedImage opaque(BufferedImage frame) {
        BufferedImage copy = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(frame, 0, 0, null);
        graphics.dispose();
        return copy;
    }
}
