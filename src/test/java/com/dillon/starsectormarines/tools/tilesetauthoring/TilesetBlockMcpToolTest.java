package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.tools.mcp.McpServer;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Authoring a block over the wire.
 *
 * <p>The assertions worth having here are of two kinds. One is that the pieces
 * end up packed as a block actually is — one contiguous patch whose cells sit at
 * their layout offsets — because that is what the export depends on. The other
 * is that the result says, in words, what every slot the call filled
 * <em>means</em>: a mirrored assignment passes every mechanical check there is,
 * so the wording of the answer is the only defence and is therefore a contract
 * rather than a nicety.
 */
class TilesetBlockMcpToolTest {

    private static final int CELL = 16;
    private static final String BLOCK = "hall.wall";

    private static McpServer server(Path root) throws Exception {
        return new McpServer(new McpToolCatalog(new TilesetMcpToolProvider().tools()),
                new McpToolContext(root, root.resolve("core")), "test", "0.0.1");
    }

    private static JSONObject call(Path root, String tool, JSONObject arguments) throws Exception {
        JSONObject params = new JSONObject().put("name", tool).put("arguments", arguments);
        JSONObject response = server(root).handle(new JSONObject()
                .put("jsonrpc", "2.0").put("id", 1)
                .put("method", "tools/call").put("params", params));
        assertFalse(response.has("error"),
                "a tool call must never become a protocol error: " + response);
        return response.getJSONObject("result");
    }

    private static String textOf(JSONObject result) throws JSONException {
        return result.getJSONArray("content").getJSONObject(0).getString("text");
    }

    /**
     * A row of ten separated opaque squares.
     *
     * <p>Each is comfortably above {@link SheetSlicer#DEFAULT_MIN_AREA} and the
     * gutters are wider than the slicer's eight-connected reach, so the piece
     * list is a property of the art rather than of a threshold.
     */
    private static BufferedImage sheet() {
        BufferedImage image = new BufferedImage(CELL * 20, CELL * 2, BufferedImage.TYPE_INT_ARGB);
        for (int piece = 0; piece < 10; piece++) {
            for (int y = 4; y < CELL * 2 - 4; y++) {
                for (int x = 4; x < CELL * 2 - 4; x++) {
                    image.setRGB(piece * CELL * 2 + x, y, 0xFF000000 | (0x101010 * (piece + 1)));
                }
            }
        }
        return image;
    }

    /** A sliced, saved document: ten pieces, all doodads, nothing grouped yet. */
    private static TilesetDocument slicedSheet(Path root) throws Exception {
        Path raw = root.resolve(TilesetLibrary.SOURCE_DIR).resolve("hall.raw.png");
        Files.createDirectories(raw.getParent());
        ImageIO.write(sheet(), "png", raw.toFile());

        TilesetDocument document = new TilesetDocument();
        document.sheet = TilesetLibrary.SOURCE_DIR + "/hall.raw.png";
        document.sheetName = "hall";
        document.idPrefix = "doodad.hall";
        document.cellPx = CELL;
        document.note = "A synthetic sheet used by the block authoring test.";
        document.write(TilesetDocument.pathFor(root, "hall"));

        call(root, "tileset_slice", new JSONObject().put("name", "hall").put("apply", true));
        return TilesetDocument.read(TilesetDocument.pathFor(root, "hall"));
    }

    /** The nine sliced pieces mapped onto the nine slots in reading order. */
    private static JSONObject nineSlots(TilesetDocument document) throws JSONException {
        List<String> slots = BlockSlots.of(GridLayout.WALL_3X3);
        JSONObject assignment = new JSONObject();
        for (int i = 0; i < slots.size(); i++) {
            assignment.put(slots.get(i), document.entries.get(i).id);
        }
        return assignment;
    }

    private static JSONObject setBlock(Path root, JSONObject slots, boolean apply)
            throws Exception {
        return call(root, "tileset_set_block", new JSONObject()
                .put("name", "hall")
                .put("blockId", BLOCK)
                .put("layout", "wall-3x3")
                .put("fillRgb", "0x060A10")
                .put("slots", slots)
                .put("apply", apply));
    }

    // -------------------------------------------------------------- assigning

    @Test
    void anAppliedAssignmentPersistsAndPacksAsOneContiguousPatch(@TempDir Path root)
            throws Exception {
        TilesetDocument document = slicedSheet(root);

        JSONObject result = setBlock(root, nineSlots(document), true);
        assertFalse(result.getBoolean("isError"), textOf(result));

        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "hall"));
        assertEquals(1, saved.blocks.size());
        assertEquals(GridLayout.WALL_3X3, saved.blocks.get(0).layout);
        assertEquals(0x060A10, saved.blocks.get(0).fillRgb);

        // A block is addressed as origin plus a layout offset, so the members
        // being one patch is what makes the exported block resolvable at all.
        TilesetExport.Packing packing = TilesetExport.pack(saved.entries, saved.blocks);
        int[] origin = packing.blockOrigins().get(BLOCK);
        assertNotNull(origin, "the packer must report where it put the block");
        int members = 0;
        for (TilesetExport.Entry entry : saved.entries) {
            if (!BLOCK.equals(entry.blockId)) continue;
            members++;
            int[] offset = BlockSlots.offset(entry.slot);
            assertEquals(origin[0] + offset[0], entry.col, entry.slot);
            assertEquals(origin[1] + offset[1], entry.row, entry.slot);
        }
        assertEquals(9, members);
    }

    @Test
    void aPreviewLeavesTheDocumentByteIdentical(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);
        Path path = TilesetDocument.pathFor(root, "hall");
        byte[] before = Files.readAllBytes(path);

        JSONObject result = setBlock(root, nineSlots(document), false);

        assertFalse(result.getJSONObject("structuredContent").getBoolean("applied"));
        assertEquals(9, result.getJSONObject("structuredContent").getInt("assigned"),
                "a preview still reports the whole assignment; it just does not keep it");
        assertUnchanged(before, Files.readAllBytes(path));
    }

    private static void assertUnchanged(byte[] expected, byte[] actual) {
        assertEquals(new String(expected, StandardCharsets.UTF_8),
                new String(actual, StandardCharsets.UTF_8),
                "apply=false must not touch the document");
    }

    @Test
    void everyFilledSlotIsReportedWithWhatItMeans(@TempDir Path root) throws Exception {
        // The mirrored assignment is the mistake no validation can catch, so
        // saying what each slot means is the tool's actual deliverable.
        TilesetDocument document = slicedSheet(root);

        JSONObject result = setBlock(root, nineSlots(document), false);
        JSONArray slots = result.getJSONObject("structuredContent").getJSONArray("slots");

        assertEquals(9, slots.length(), "every slot of the layout is accounted for");
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            assertEquals(BlockSlots.describe(slot.getString("slot")), slot.getString("means"),
                    "the reported meaning is BlockSlots' own wording, not a paraphrase");
            assertTrue(slot.getBoolean("assignedNow"));
        }
        assertEquals("exterior on the north and west",
                slots.getJSONObject(0).getString("means"));
        assertEquals("enclosed — no exterior on any side",
                slots.getJSONObject(4).getString("means"));

        // The prose half carries it too: the caller reads that, not the JSON.
        String text = textOf(result);
        assertTrue(text.contains("exterior on the north and west"), text);
        assertTrue(text.contains("the exterior is on this side"),
                "the summary states the reading the whole check depends on: " + text);
    }

    @Test
    void unfilledSlotsAreNamedRatherThanLeftToBeNoticed(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);
        JSONObject slots = nineSlots(document);
        slots.remove("center");
        slots.remove("se");

        JSONObject structured =
                setBlock(root, slots, false).getJSONObject("structuredContent");

        JSONArray unfilled = structured.getJSONArray("unfilled");
        assertEquals(2, unfilled.length());
        assertEquals("center", unfilled.getString(0));
        assertEquals("se", unfilled.getString(1));
        assertEquals(7, structured.getInt("assigned"));
    }

    @Test
    void slotsNotNamedAgainKeepWhatTheyHold(@TempDir Path root) throws Exception {
        // A sheet is grouped a few slots at a time, so a second call must not
        // silently release the members the first one made.
        TilesetDocument document = slicedSheet(root);
        JSONObject first = new JSONObject()
                .put("nw", document.entries.get(0).id)
                .put("n", document.entries.get(1).id);
        setBlock(root, first, true);

        JSONObject second = new JSONObject().put("ne", document.entries.get(2).id);
        JSONObject structured = setBlock(root, second, true).getJSONObject("structuredContent");

        assertEquals(1, structured.getInt("assigned"));
        assertEquals(6, structured.getJSONArray("unfilled").length());
        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "hall"));
        assertEquals(3, saved.entries.stream()
                .filter(entry -> BLOCK.equals(entry.blockId)).count());
    }

    @Test
    void aPieceAssignedOverAnOccupiedSlotDisplacesTheOneThatWasThere(@TempDir Path root)
            throws Exception {
        TilesetDocument document = slicedSheet(root);
        setBlock(root, new JSONObject().put("n", document.entries.get(1).id), true);

        JSONObject structured = setBlock(root,
                new JSONObject().put("n", document.entries.get(9).id), true)
                .getJSONObject("structuredContent");

        JSONArray displaced = structured.getJSONArray("displaced");
        assertEquals(1, displaced.length(), "a slot holds one piece");
        assertEquals(document.entries.get(1).id, displaced.getString(0));
        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "hall"));
        assertEquals("", saved.entries.get(1).blockId,
                "the displaced piece goes back to being a doodad rather than vanishing");
    }

    @Test
    void assigningAPieceMakesItShip(@TempDir Path root) throws Exception {
        // An excluded block cell would leave a hole the layout has no fill for,
        // which is the one piece of doodad-side state a grouping settles.
        TilesetDocument document = slicedSheet(root);
        document.entries.get(0).included = false;
        document.write(TilesetDocument.pathFor(root, "hall"));

        setBlock(root, new JSONObject().put("nw", document.entries.get(0).id), true);

        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "hall"));
        assertTrue(saved.entries.get(0).included);
    }

    // ------------------------------------------------------------- rejections

    @Test
    void anUnknownLayoutIsRefusedAndTheValidOnesAreListed(@TempDir Path root) throws Exception {
        slicedSheet(root);

        JSONObject result = call(root, "tileset_set_block", new JSONObject()
                .put("name", "hall").put("blockId", BLOCK).put("layout", "wall3x3"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("unknown layout 'wall3x3'"), textOf(result));
        assertTrue(textOf(result).contains("wall-3x3"),
                "a refusal a model can act on names the spellings that work: " + textOf(result));
    }

    @Test
    void aSlotTheLayoutDoesNotHaveIsRefused(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);

        JSONObject result = call(root, "tileset_set_block", new JSONObject()
                .put("name", "hall").put("blockId", "hall.floor").put("layout", "single")
                .put("slots", new JSONObject().put("ne", document.entries.get(0).id)));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("'ne' is not a slot of single"), textOf(result));
        assertTrue(textOf(result).contains(BlockSlots.ONLY), textOf(result));
    }

    @Test
    void aPieceThatIsNotInTheDocumentIsRefused(@TempDir Path root) throws Exception {
        slicedSheet(root);

        JSONObject result = setBlock(root, new JSONObject().put("n", "doodad.hall.imaginary"),
                true);

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("no piece 'doodad.hall.imaginary'"), textOf(result));
        assertTrue(TilesetDocument.read(TilesetDocument.pathFor(root, "hall")).blocks.isEmpty(),
                "a refused call writes nothing, even with apply=true");
    }

    @Test
    void aPieceAlreadyClaimedByAnotherBlockIsRefused(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);
        setBlock(root, new JSONObject().put("n", document.entries.get(1).id), true);

        JSONObject result = call(root, "tileset_set_block", new JSONObject()
                .put("name", "hall").put("blockId", "hall.other").put("layout", "wall-3x3")
                .put("slots", new JSONObject().put("s", document.entries.get(1).id))
                .put("apply", true));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("is already hall.wall / n"), textOf(result));
        assertTrue(textOf(result).contains("tileset_remove_block"),
                "the refusal names the tool that would clear the claim: " + textOf(result));
    }

    @Test
    void twoSlotsClaimingTheSamePieceIsRefused(@TempDir Path root) throws Exception {
        // A block cell is addressed by its offset, so one piece in two slots
        // would have to be packed at two coordinates.
        TilesetDocument document = slicedSheet(root);
        String shared = document.entries.get(0).id;

        JSONObject result = setBlock(root, new JSONObject().put("n", shared).put("s", shared),
                true);

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("assigned to both"), textOf(result));
        assertTrue(textOf(result).contains(shared), textOf(result));
    }

    @Test
    void redeclaringUnderALayoutThatDropsAFilledSlotIsRefused(@TempDir Path root)
            throws Exception {
        // Silently discarding the member would lose an assignment that cannot be
        // re-derived; the caller is told to dissolve the block on purpose.
        TilesetDocument document = slicedSheet(root);
        setBlock(root, new JSONObject().put("ne", document.entries.get(2).id), true);

        JSONObject result = call(root, "tileset_set_block", new JSONObject()
                .put("name", "hall").put("blockId", BLOCK).put("layout", "single")
                .put("apply", true));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("which single does not have"), textOf(result));
    }

    @Test
    void aSheetNameThatIsReallyAPathIsRefused(@TempDir Path root) throws Exception {
        JSONObject result = call(root, "tileset_set_block", new JSONObject()
                .put("name", "../../mod/data/tilesets/urban-tileset")
                .put("blockId", BLOCK).put("layout", "wall-3x3"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("is a path, not a sheet name"), textOf(result));
    }

    @Test
    void aMalformedFillIsRefused(@TempDir Path root) throws Exception {
        slicedSheet(root);

        JSONObject result = call(root, "tileset_set_block", new JSONObject()
                .put("name", "hall").put("blockId", BLOCK).put("layout", "wall-3x3")
                .put("fillRgb", "dark grey"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("0x060A10"), textOf(result));
    }

    // ---------------------------------------------------------------- removal

    @Test
    void removingABlockHandsItsMembersBackAsDoodads(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);
        setBlock(root, nineSlots(document), true);

        JSONObject result = call(root, "tileset_remove_block", new JSONObject()
                .put("name", "hall").put("blockId", BLOCK).put("apply", true));
        JSONArray released = result.getJSONObject("structuredContent").getJSONArray("released");

        assertEquals(9, released.length(), "every member it releases is reported");
        assertEquals(BlockSlots.describe(released.getJSONObject(0).getString("slot")),
                released.getJSONObject(0).getString("means"));
        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "hall"));
        assertTrue(saved.blocks.isEmpty());
        assertTrue(saved.entries.stream().noneMatch(TilesetExport.Entry::isBlockMember));
        assertEquals(document.entries.get(0).id, saved.entries.get(0).id,
                "a released member keeps its own id; only the membership is withdrawn");
    }

    @Test
    void previewingARemovalLeavesTheDocumentAlone(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);
        setBlock(root, nineSlots(document), true);
        Path path = TilesetDocument.pathFor(root, "hall");
        byte[] before = Files.readAllBytes(path);

        JSONObject structured = call(root, "tileset_remove_block", new JSONObject()
                .put("name", "hall").put("blockId", BLOCK)).getJSONObject("structuredContent");

        assertFalse(structured.getBoolean("applied"));
        assertEquals(9, structured.getJSONArray("released").length());
        assertUnchanged(before, Files.readAllBytes(path));
    }

    @Test
    void removingABlockThatIsNotThereSaysWhichOnesAre(@TempDir Path root) throws Exception {
        TilesetDocument document = slicedSheet(root);
        setBlock(root, nineSlots(document), true);

        JSONObject result = call(root, "tileset_remove_block", new JSONObject()
                .put("name", "hall").put("blockId", "hall.floor"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("It declares " + BLOCK), textOf(result));
    }
}
