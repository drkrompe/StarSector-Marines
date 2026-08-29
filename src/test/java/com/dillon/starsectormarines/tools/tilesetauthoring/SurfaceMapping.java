package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes which block a surface is drawn with.
 *
 * <p>This is the other half of asking "what can be a wall". Seeing the
 * alternatives is worth little if choosing one means finding
 * {@code urban.mapping.json} and editing a string by hand — the listing knows
 * exactly which key and which id, and typing them again is a chance to get one
 * of them wrong.
 *
 * <p>The change is a surgical text edit and the result is validated before it
 * lands. One entry's value is replaced in place, because re-serialising the
 * document would reorder every key in it; and the whole mapping is then loaded
 * against the real catalog, because a mapping naming an id no tileset defines
 * is a startup crash rather than a wrong-looking map.
 */
public final class SurfaceMapping {

    private SurfaceMapping() {}

    /** The mapping the shipped content lives in. */
    public static final String MAPPING = "data/tilesets/urban.mapping.json";

    /**
     * Point {@code purpose} at {@code blockId} and save.
     *
     * @param purpose the surface, as {@link SurfaceCatalog.Purpose#name} reports it
     * @param vocabulary which key space it belongs to, which decides the section
     * @throws IOException if the change would not load, in which case nothing is written
     */
    public static void use(Path projectRoot, String purpose, String vocabulary, String blockId)
            throws IOException, JSONException {
        Path file = projectRoot.resolve("mod");
        for (String part : MAPPING.split("/")) file = file.resolve(part);
        if (!Files.isRegularFile(file)) {
            throw new IOException("no mapping at " + file + " to change");
        }

        String original = Files.readString(file);
        String section = SurfaceCatalog.SURFACE_ROLE.equals(vocabulary)
                ? "surfaceRender" : "groundRender";
        JSONObject dispatch = new JSONObject(original).optJSONObject(section);
        String previous = dispatch == null ? null : dispatch.optString(purpose, null);
        if (blockId.equals(previous)) return;

        String written = replaceEntry(original, section, purpose, blockId);
        verify(projectRoot, written);

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, written);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Replace one entry's value, touching nothing else in the file.
     *
     * <p>Deliberately a text edit rather than a re-serialisation. Reading the
     * mapping into {@code org.json} and writing it back out reorders every key
     * in the document — its objects are hash maps — so changing one string
     * would rewrite the whole file and lose whatever ordering and comments a
     * hand-maintained shipped file was given. Caught by a test asserting that
     * the ground dispatch is untouched when a surface role changes.
     */
    static String replaceEntry(String document, String section, String key, String value)
            throws IOException {
        int sectionAt = document.indexOf('"' + section + '"');
        if (sectionAt < 0) {
            throw new IOException("the mapping has no \"" + section + "\" section to change");
        }
        int open = document.indexOf('{', sectionAt);
        if (open < 0) throw new IOException('"' + section + "\" is not an object");
        int close = matchingBrace(document, open);

        Pattern entry = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher found = entry.matcher(document.substring(open, close));
        if (found.find()) {
            int from = open + found.start(1);
            int to = open + found.end(1);
            return document.substring(0, from) + value + document.substring(to);
        }
        // Not there yet: add it as the section's first entry, matching whatever
        // indentation the entry after it already uses.
        String rest = document.substring(open + 1, close);
        String indent = rest.replaceFirst("(?s)^([ \t\r\n]*).*", "$1");
        String separator = rest.isBlank() ? "" : ",";
        return document.substring(0, open + 1)
                + indent + '"' + key + "\": \"" + value + '"' + separator
                + document.substring(open + 1);
    }

    /** The index of the brace closing the one at {@code open}, ignoring braces in strings. */
    private static int matchingBrace(String document, int open) throws IOException {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open; i < document.length(); i++) {
            char c = document.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        throw new IOException("the mapping has an unclosed object");
    }

    /**
     * Load the proposed mapping against the real catalog before it replaces the
     * one on disk.
     *
     * <p>The same cross-check the application does at startup, run here instead,
     * so a surface pointed at art that does not exist is refused at the moment
     * somebody chooses it rather than the next time the game loads.
     */
    private static void verify(Path projectRoot, String proposed) throws IOException {
        try {
            TileRegistry tiles = new TileRegistry();
            for (String path : TileRegistry.BUILTIN_TILESETS) {
                tiles.ingestSheet(new JSONObject(read(projectRoot, path)));
            }
            TileRegistry previous = TileRegistry.installed();
            try {
                TileRegistry.install(tiles);
                GenMappingRegistry mapping = new GenMappingRegistry();
                mapping.ingest(new JSONObject(proposed));
                mapping.validateReferences();
            } finally {
                TileRegistry.install(previous);
            }
        } catch (JSONException | IllegalStateException refused) {
            throw new IOException("that mapping would not load: " + refused.getMessage(), refused);
        }
    }

    private static String read(Path projectRoot, String modRelative) throws IOException {
        Path path = projectRoot.resolve("mod");
        for (String part : modRelative.split("/")) path = path.resolve(part);
        return Files.readString(path);
    }
}
