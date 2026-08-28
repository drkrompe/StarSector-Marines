package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two ways of producing a shipped atlas exist here, and only one of them may
 * own a given sheet.
 *
 * <p>{@code normalize_tilesets.py} derives an atlas by transferring fresh
 * colour onto the alpha topology of the atlas it is about to overwrite. That
 * makes the shipped file its own input, which is tolerable only while the raw
 * plate is opaque and has therefore recorded nothing about what is background
 * and what is art. The tileset authoring exporter derives an atlas from a raw
 * sheet that carries its own keyed alpha and from the judgements in its
 * authoring document, which is the only arrangement in which the shipped file
 * is genuinely re-derivable.
 *
 * <p>A sheet claimed by both is the hazard. The script would rebuild it from
 * the alpha of the export it is replacing and silently revert a day of
 * annotation: a valid PNG, the right size, the wrong art, and nothing red at
 * the moment it happens. Every downstream check stays green, because the atlas
 * and the tileset that describes it remain internally consistent.
 *
 * <p>Which producer owns a sheet is <em>measured</em>, not declared. Keying the
 * plate is itself the withdrawal from the alpha-transfer model, so there is no
 * flag to set and no second list to keep in step. The script carries the same
 * refusal at run time; this is the earlier of the two, so a re-add goes red in
 * {@code check} rather than at the moment art is destroyed.
 */
class KeyedSheetsAreExportedNotNormalizedTest {

    private static final Path TILESETS = Path.of("art-source", "tilesets");
    private static final Path SCRIPT = TILESETS.resolve("normalize_tilesets.py");

    /** {@code GridSpec("x.png", "x.raw.png", "x.png")} and its strip sibling. */
    private static final Pattern SPEC =
            Pattern.compile("(?:Grid|Strip)Spec\\(\\s*\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");

    /**
     * Below this, a pixel is background the key carved away rather than art.
     * Keyed sheets here are hard 0/255, so the threshold only has to sit clear
     * of an opaque plate's own dark outlines, which live in the colour channels
     * and leave alpha at 255.
     */
    private static final int CLEAR_ALPHA = 128;

    @Test
    void everySheetTheNormalizeScriptClaimsIsAnOpaquePlate() throws IOException {
        List<String> claimed = claimedRawSheets();
        assertTrue(claimed.size() >= 3, "parsed " + claimed.size() + " sheets out of "
                + SCRIPT + "; the spec tuples changed shape and this guard no longer reads "
                + "them, so it is no longer guarding anything");
        List<String> keyed = new ArrayList<>();
        for (String raw : claimed) {
            if (carriesKeyedAlpha(TILESETS.resolve(raw))) keyed.add(raw);
        }
        assertEquals(List.of(), keyed, "these sheets carry their own keyed alpha, so their "
                + "atlases are exported from their authoring documents; normalizing them "
                + "would rebuild them from the alpha of the export being overwritten and "
                + "silently revert it. Remove them from GRID_SPECS/STRIP_SPECS in " + SCRIPT);
    }

    /**
     * The same law approached from the other side, and deliberately by a
     * different mechanism: this one never parses a spec tuple, so it still
     * holds if the script's spec syntax is rewritten and the pattern above
     * stops matching.
     *
     * <p>Comments and the module docstring are removed first. Prose explaining
     * why a sheet was withdrawn may name it — that is the comment doing its
     * job — and only the executable body decides what the script touches.
     */
    @Test
    void noKeyedRawSheetIsNamedInTheNormalizeScript() throws IOException {
        String body = executableBody(Files.readString(SCRIPT));
        List<String> named = new ArrayList<>();
        for (Path raw : rawSheets()) {
            String name = raw.getFileName().toString();
            if (carriesKeyedAlpha(raw) && body.contains(name)) named.add(name);
        }
        assertEquals(List.of(), named, "a keyed raw sheet is exported through the tileset "
                + "authoring exporter and must not be reachable from " + SCRIPT);
    }

    /**
     * The withdrawn sheets are still keyed, so the guard above is still armed
     * against something. Without this, re-flattening a raw sheet would disarm
     * both checks at once and leave them passing.
     */
    @Test
    void theWithdrawnSheetsStillCarryTheAlphaThatWithdrewThem() throws IOException {
        for (String name : List.of("urban-tileset.raw.png", "urban-tileset-3.raw.png")) {
            assertTrue(carriesKeyedAlpha(TILESETS.resolve(name)),
                    name + " no longer carries its own alpha; it is not re-exportable and "
                            + "the guard that keeps it out of the normalize script is inert");
        }
    }

    private static List<String> claimedRawSheets() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = SPEC.matcher(executableBody(Files.readString(SCRIPT)));
        while (matcher.find()) found.add(matcher.group(1));
        return List.copyOf(found);
    }

    /** The script with its module docstring and {@code #} comment lines removed. */
    private static String executableBody(String source) {
        int open = source.indexOf("\"\"\"");
        if (open >= 0) {
            int close = source.indexOf("\"\"\"", open + 3);
            if (close >= 0) source = source.substring(close + 3);
        }
        StringBuilder body = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            if (!line.strip().startsWith("#")) body.append(line).append('\n');
        }
        return body.toString();
    }

    private static List<Path> rawSheets() throws IOException {
        try (Stream<Path> walk = Files.list(TILESETS)) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".raw.png"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean carriesKeyedAlpha(Path sheet) throws IOException {
        BufferedImage raw = ImageIO.read(sheet.toFile());
        assertNotNull(raw, "cannot read " + sheet);
        if (!raw.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < raw.getHeight(); y++) {
            for (int x = 0; x < raw.getWidth(); x++) {
                if ((raw.getRGB(x, y) >>> 24) < CLEAR_ALPHA) return true;
            }
        }
        return false;
    }
}
