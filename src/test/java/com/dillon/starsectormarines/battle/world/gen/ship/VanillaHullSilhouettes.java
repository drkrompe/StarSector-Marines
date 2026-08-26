package com.dillon.starsectormarines.battle.world.gen.ship;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Traces hull outlines out of the installed game's own ship sprites.
 *
 * <p>Vanilla sprites are drawn bow-up, so a row of the image is one frame of the
 * deck and a column is a position across the beam. Walking each row for its
 * first and last opaque pixel gives the hull's port and starboard extent there,
 * which is exactly what {@link HullSilhouette} wants. The result is a real
 * ship's proportions and asymmetry for free, instead of a synthetic curve
 * pretending to be one.
 *
 * <p><b>Tool-side only.</b> This reads the read-only game install with ordinary
 * file I/O, which mod runtime code may not do. Generating decks inside a running
 * game will need these outlines baked into a data catalog first; until then this
 * serves evidence and authoring.
 */
public final class VanillaHullSilhouettes {

    /** Alpha above which a sprite pixel counts as hull rather than background. */
    private static final int OPAQUE = 40;
    /** Samples taken along the hull. Enough to keep a bow point crisp when stretched. */
    private static final int SAMPLES = 160;
    /** Half-beam percentile that maps to full deck depth; above it the hull clips. */
    private static final float BEAM_PERCENTILE = 0.80f;

    private final Path core;

    public VanillaHullSilhouettes(Path starsectorCore) {
        this.core = starsectorCore;
    }

    /** One vanilla hull: its outline, class, and the complement and hold that size its decks. */
    public record Hull(String id, HullSilhouette silhouette, HullClass hullClass,
                       int maxCrew, int cargo) {}

    /** Whether the installed game is present; suites skip their evidence when it is not. */
    public boolean available() {
        return Files.isDirectory(core.resolve("data").resolve("hulls"));
    }

    /**
     * Read one hull by id. Returns null when the hull, its sprite reference, or
     * the sprite file is missing, so a suite degrades instead of failing.
     */
    public Hull read(String hullId) throws IOException {
        Path spec = core.resolve("data/hulls/" + hullId + ".ship");
        if (!Files.isRegularFile(spec)) return null;

        String spriteName;
        HullClass hullClass;
        try (Reader reader = Files.newBufferedReader(spec, StandardCharsets.UTF_8)) {
            JSONObject json = new JSONObject(new JSONTokener(reader));
            spriteName = json.optString("spriteName", null);
            hullClass = HullClass.fromHullSize(json.optString("hullSize", null));
        } catch (JSONException | RuntimeException malformed) {
            return null;
        }
        if (spriteName == null || spriteName.isBlank()) return null;

        Path sprite = core.resolve(spriteName);
        if (!Files.isRegularFile(sprite)) return null;
        BufferedImage image = ImageIO.read(sprite.toFile());
        if (image == null) return null;

        int[] crewAndCargo = readCrewAndCargo(hullId);
        return new Hull(hullId, trace(image, hullId), hullClass, crewAndCargo[0], crewAndCargo[1]);
    }

    /** Reads {@code max crew} and {@code cargo} for one hull from the vanilla ship table. */
    private int[] readCrewAndCargo(String hullId) throws IOException {
        Path table = core.resolve("data/hulls/ship_data.csv");
        if (!Files.isRegularFile(table)) return new int[]{ 1, 0 };
        List<String> lines = Files.readAllLines(table, StandardCharsets.ISO_8859_1);
        if (lines.isEmpty()) return new int[]{ 1, 0 };
        String[] header = lines.get(0).split(",", -1);
        int idColumn = columnOf(header, "id");
        int crewColumn = columnOf(header, "max crew");
        int cargoColumn = columnOf(header, "cargo");
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            if (idColumn >= cells.length || !cells[idColumn].trim().equals(hullId)) continue;
            return new int[]{ intAt(cells, crewColumn, 1), intAt(cells, cargoColumn, 0) };
        }
        return new int[]{ 1, 0 };
    }

    private static int columnOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static int intAt(String[] cells, int column, int fallback) {
        if (column < 0 || column >= cells.length) return fallback;
        try {
            return (int) Double.parseDouble(cells[column].trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /**
     * Walk the sprite bow to stern, recording how far the hull reaches either
     * side of its own centreline at each sample. The two sides are kept apart
     * so the deck inherits the hull's real asymmetry rather than averaging it
     * away.
     *
     * <p>Normalizing against the hull's absolute widest point would be wrong.
     * Many hulls are long and slender with one bulge — a wing root or an engine
     * block — and dividing by that peak shrinks the whole rest of the ship to a
     * sliver too shallow to hold any compartment, leaving a deck that is nearly
     * all dead structure. Scaling against a high percentile instead maps the
     * hull's <em>typical</em> beam near full depth and lets the genuine outlier
     * clip, which is what makes small hulls produce usable decks.
     */
    private static HullSilhouette trace(BufferedImage image, String hullId) {
        // Vanilla art is drawn bow-up, but a few hulls are broader than they are
        // long. Walking the short axis of one of those would trace across the
        // beam and produce a deck at right angles to the ship, so the long axis
        // is chosen rather than assumed.
        boolean bowUp = image.getHeight() >= image.getWidth();
        int along = bowUp ? image.getHeight() : image.getWidth();
        int across = bowUp ? image.getWidth() : image.getHeight();
        float centre = (across - 1) / 2f;

        float[] port = new float[SAMPLES];
        float[] starboard = new float[SAMPLES];
        int firstOccupied = -1;
        int lastOccupied = -1;
        float widest = 0f;

        for (int s = 0; s < SAMPLES; s++) {
            int line = Math.min(along - 1, Math.round((float) s / (SAMPLES - 1) * (along - 1)));
            int first = -1;
            int last = -1;
            for (int a = 0; a < across; a++) {
                int argb = bowUp ? image.getRGB(a, line) : image.getRGB(line, a);
                if ((argb >>> 24) < OPAQUE) continue;
                if (first < 0) first = a;
                last = a;
            }
            if (first < 0) continue;
            port[s] = Math.max(0f, centre - first);
            starboard[s] = Math.max(0f, last - centre);
            if (firstOccupied < 0) firstOccupied = s;
            lastOccupied = s;
            widest = Math.max(widest, last - first + 1);
        }

        float reference = typicalHalfBeam(port, starboard);
        for (int s = 0; s < SAMPLES; s++) {
            port[s] /= reference;
            starboard[s] /= reference;
        }
        // Aspect has to come from the hull, not the canvas. Sprites are padded,
        // and a small hull on a large sheet measured corner to corner reads as
        // almost square — which produced a deck shaped like a station.
        float length = firstOccupied < 0
                ? along
                : (lastOccupied - firstOccupied + 1) / (float) SAMPLES * along;
        return new HullSilhouette(port, starboard,
                Math.max(0.05f, widest / Math.max(1f, length)), hullId);
    }

    /** The {@link #BEAM_PERCENTILE} half-beam across every sample the hull occupies. */
    private static float typicalHalfBeam(float[] port, float[] starboard) {
        float[] widths = new float[port.length];
        int count = 0;
        for (int s = 0; s < port.length; s++) {
            float half = Math.max(port[s], starboard[s]);
            if (half > 0f) widths[count++] = half;
        }
        if (count == 0) return 1f;
        float[] present = Arrays.copyOf(widths, count);
        Arrays.sort(present);
        int index = Math.min(count - 1, Math.round((count - 1) * BEAM_PERCENTILE));
        return Math.max(1f, present[index]);
    }
}
