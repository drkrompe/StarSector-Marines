package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Choosing which block a surface is drawn with.
 *
 * <p>This writes shipped content, so the guard that matters is the refusal: a
 * mapping naming an id no tileset defines is a startup crash rather than a
 * wrong-looking map, and the moment to find that out is when somebody picks it.
 */
public class SurfaceMappingTest {

    /** A project root holding the real catalog and mapping, so the check is the real one. */
    private static Path fixture(Path root) throws IOException {
        Path from = Paths.get("").toAbsolutePath().resolve("mod").resolve("data")
                .resolve("tilesets");
        Path to = root.resolve("mod").resolve("data").resolve("tilesets");
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file)) Files.copy(file, to.resolve(file.getFileName()));
            }
        }
        return root;
    }

    private static JSONObject mappingOf(Path root) throws IOException, JSONException {
        Path file = root.resolve("mod");
        for (String part : SurfaceMapping.MAPPING.split("/")) file = file.resolve(part);
        return new JSONObject(Files.readString(file));
    }

    /** A surface role writes into surfaceRender; a ground kind into groundRender. */
    @Test
    void choosingABlockPointsTheRightSectionAtIt(@TempDir Path temp) throws Exception {
        Path root = fixture(temp);

        SurfaceMapping.use(root, "WALL", SurfaceCatalog.SURFACE_ROLE, "road.embankment");
        assertEquals("road.embankment",
                mappingOf(root).getJSONObject("surfaceRender").getString("WALL"));

        SurfaceMapping.use(root, "INDOOR", SurfaceCatalog.GROUND_KIND, "urban.rubble");
        assertEquals("urban.rubble",
                mappingOf(root).getJSONObject("groundRender").getString("INDOOR"));
    }

    /** Everything else in the mapping survives the change. */
    @Test
    void nothingElseInTheMappingMoves(@TempDir Path temp) throws Exception {
        Path root = fixture(temp);
        JSONObject before = mappingOf(root);

        SurfaceMapping.use(root, "WALL", SurfaceCatalog.SURFACE_ROLE, "road.embankment");
        JSONObject after = mappingOf(root);

        assertEquals(before.getJSONObject("groundRender").toString(),
                after.getJSONObject("groundRender").toString(),
                "changing a surface role must not disturb the ground dispatch");
        assertEquals(before.getJSONObject("doodadPools").toString(),
                after.getJSONObject("doodadPools").toString());
        assertEquals(before.getJSONObject("fillers").toString(),
                after.getJSONObject("fillers").toString());
    }

    /**
     * The refusal. A surface pointed at art nothing defines fails here, with the
     * file on disk untouched, rather than at the next startup.
     */
    @Test
    void aBlockNothingDefinesIsRefusedAndNothingIsWritten(@TempDir Path temp) throws Exception {
        Path root = fixture(temp);
        String before = mappingOf(root).toString();

        IOException refused = assertThrows(IOException.class, () ->
                SurfaceMapping.use(root, "WALL", SurfaceCatalog.SURFACE_ROLE, "no.such.block"));
        assertTrue(refused.getMessage().contains("no.such.block"),
                "the refusal should name the id: " + refused.getMessage());
        assertEquals(before, mappingOf(root).toString(), "the mapping must be untouched");
    }

    /** Choosing what is already drawn changes nothing and does not rewrite the file. */
    @Test
    void choosingWhatIsAlreadyDrawnIsANoOp(@TempDir Path temp) throws Exception {
        Path root = fixture(temp);
        Path file = root.resolve("mod");
        for (String part : SurfaceMapping.MAPPING.split("/")) file = file.resolve(part);
        String before = Files.readString(file);

        SurfaceMapping.use(root, "WALL", SurfaceCatalog.SURFACE_ROLE, "urban.wall");
        assertEquals(before, Files.readString(file), "an unchanged choice must not rewrite bytes");
    }

    /** The catalog reads the change back, which is what the listing shows. */
    @Test
    void theListingSeesTheChange(@TempDir Path temp) throws Exception {
        Path root = fixture(temp);
        Files.createDirectories(root.resolve("art-source").resolve("tilesets"));

        SurfaceMapping.use(root, "WALL", SurfaceCatalog.SURFACE_ROLE, "road.embankment");

        List<SurfaceCatalog.Purpose> purposes = SurfaceCatalog.scan(root);
        SurfaceCatalog.Purpose wall = purposes.stream()
                .filter(purpose -> purpose.name().equals("WALL")).findFirst().orElseThrow();
        assertEquals("road.embankment", wall.mappedId());
        assertEquals("road.embankment", wall.inUse().blockId());
    }

    /** The surfaces are listed alphabetically, because it is a list a name is looked up in. */
    @Test
    void theSurfacesAreAlphabetical() throws Exception {
        List<String> names = SurfaceCatalog.scan(Paths.get("").toAbsolutePath()).stream()
                .map(SurfaceCatalog.Purpose::name).toList();
        assertEquals(names.stream().sorted().toList(), names);
    }
}
