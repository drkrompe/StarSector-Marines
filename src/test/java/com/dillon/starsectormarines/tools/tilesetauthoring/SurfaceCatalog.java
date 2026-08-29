package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.SurfaceRole;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The project's art seen from the other end: not "what is on this sheet" but
 * "what can be a wall".
 *
 * <p>Authoring runs sheet-first — open a sheet, cut it, say what each piece is.
 * That is the right order for ingesting art and the wrong order for needing
 * some. Work starts from a purpose: a wall is wanted, or a floor, and the
 * questions are which walls already exist, where they were cut from, and what
 * it would take to add another. Nothing in the sheet-first tools answers those,
 * because the answer is spread across every document in the project and the
 * mapping that chooses between them.
 *
 * <p>So this joins the three places a purpose is described — the mapping that
 * says which art is in use, the exported tilesets that say what art exists, and
 * the authoring documents that say where it was cut from — and reports each
 * purpose with its candidates.
 *
 * <p>Nothing here is stored. Candidates are derived on every scan from files
 * that were going to be read anyway, because a second written record of "these
 * are the walls" is a second thing that can be wrong: it would go stale the
 * first time a block was renamed in the only file that actually defines it.
 */
public final class SurfaceCatalog {

    private SurfaceCatalog() {}

    /**
     * The shape of a block with no autotile layout — a pool of interchangeable
     * variants picked by cell hash.
     *
     * <p>Shape is what decides whether one block can stand in for another. A
     * {@code wall-3x3} cannot fill a slot a variant pool fills, or the reverse,
     * because the resolver asks them different questions: one wants a frame for
     * a four-neighbour mask, the other wants any member of a set.
     */
    public static final String VARIANT_POOL = "variants";

    /** The two key spaces a purpose can come from, named as an operator would say them. */
    public static final String GROUND_KIND = "ground kind";
    public static final String SURFACE_ROLE = "surface role";

    /** A piece of a raw sheet assigned to one slot of a block. */
    public record Slot(String pieceId, String slot, boolean included) {}

    /**
     * One block that could fill a purpose, and where it came from.
     *
     * @param blockId   the id a mapping would name
     * @param shape     its layout name, or {@link #VARIANT_POOL}
     * @param sheetName the sheet its art is packed on
     * @param document  the authoring document that declares it, or null
     * @param slots     the pieces assigned to its slots; empty when unauthored
     * @param inUse     whether the mapping currently points the purpose here
     */
    public record Candidate(String blockId, String shape, String sheetName,
                            Path document, List<Slot> slots, boolean inUse) {

        /**
         * Whether the slicing behind this block can be edited.
         *
         * <p>A block that exists only in an exported tileset can be seen and
         * mapped but not re-cut, because nothing in the project records which
         * pieces of which raw sheet it was made from. Reporting that is the
         * point: offering to edit it and then finding nothing to edit wastes
         * the trip and reads as a bug in the tool rather than a gap in the art.
         */
        public boolean isEditable() {
            return document != null && !slots.isEmpty();
        }

        /** One line for a listing: what it is, where it came from, whether it is live. */
        public String describe() {
            String provenance = isEditable()
                    ? sheetName + ", " + slots.size() + " slots authored"
                    : sheetName + ", shipped only — no authoring document";
            return blockId + "  (" + shape + "; " + provenance + ")"
                    + (inUse ? "  <- in use" : "");
        }
    }

    /**
     * One surface the generator can ask for, and everything that could answer.
     *
     * @param name       the {@code GroundKind} or {@code SurfaceRole} name
     * @param vocabulary which key space {@code name} belongs to
     * @param mappedId   what the mapping points it at, or null when unmapped
     * @param shape      the shape a block must have to stand in here
     * @param candidates every block of that shape in the project, in-use first
     */
    public record Purpose(String name, String vocabulary, String mappedId,
                          String shape, List<Candidate> candidates) {

        /**
         * A purpose no mapping fills. Some are deliberate — the renderer
         * special-cases {@code SIDEWALK} — and some are simply not built yet,
         * and this does not claim to tell them apart.
         */
        public boolean isUnmapped() {
            return mappedId == null;
        }

        /** The candidates that are not in use: what could be swapped in. */
        public List<Candidate> alternatives() {
            return candidates.stream().filter(candidate -> !candidate.inUse()).toList();
        }

        /** The candidate the mapping currently points at, or null when unmapped. */
        public Candidate inUse() {
            return candidates.stream().filter(Candidate::inUse).findFirst().orElse(null);
        }
    }

    /**
     * Every purpose in the project with its candidates.
     *
     * <p>{@code VOID} is left out. It is the absence of deck rather than a
     * surface — nothing draws it, so nothing can fill it.
     */
    public static List<Purpose> scan(Path projectRoot) throws IOException, JSONException {
        TileRegistry tiles = loadTilesets(projectRoot);
        GenMappingRegistry mapping = loadMapping(projectRoot);
        Map<String, SheetDocument> documents = loadDocuments(projectRoot);

        List<Purpose> purposes = new ArrayList<>();
        for (GroundKind kind : GroundKind.values()) {
            if (kind == GroundKind.VOID) continue;
            purposes.add(purpose(kind.name(), GROUND_KIND,
                    mapping.groundBlockId(kind), tiles, documents));
        }
        for (SurfaceRole role : SurfaceRole.values()) {
            purposes.add(purpose(role.name(), SURFACE_ROLE,
                    mapping.surfaceBlockId(role), tiles, documents));
        }
        return purposes;
    }

    /**
     * The purposes filled by blocks of {@code shape} — the "show me the walls"
     * query, asked by shape because that is what a candidate has to match.
     */
    public static List<Purpose> withShape(List<Purpose> purposes, String shape) {
        return purposes.stream()
                .filter(purpose -> shape.equalsIgnoreCase(purpose.shape()))
                .toList();
    }

    /** Every distinct shape currently in use, so a chooser can offer the real ones. */
    public static List<String> shapes(List<Purpose> purposes) {
        return purposes.stream()
                .map(Purpose::shape)
                .filter(shape -> shape != null)
                .distinct()
                .sorted()
                .toList();
    }

    private static Purpose purpose(String name, String vocabulary, String mappedId,
                                   TileRegistry tiles, Map<String, SheetDocument> documents) {
        GridBlockDef mapped = mappedId == null ? null : tiles.block(mappedId);
        // A purpose the mapping sends to a sliced tile rather than a block has no
        // block shape, so it has no candidates to compare against. STREET is the
        // live example: it resolves to a frame of an auto-strip.
        String shape = mapped == null ? null : shapeOf(mapped);

        List<Candidate> candidates = new ArrayList<>();
        if (shape != null) {
            for (GridBlockDef block : tiles.blocks()) {
                if (!shape.equals(shapeOf(block))) continue;
                candidates.add(candidate(block, documents, block.id.equals(mappedId)));
            }
            candidates.sort(Comparator.comparing(Candidate::inUse).reversed()
                    .thenComparing(Candidate::blockId));
        }
        return new Purpose(name, vocabulary, mappedId, shape, List.copyOf(candidates));
    }

    /** An authoring document and where it was read from, so a candidate can point at it. */
    private record SheetDocument(Path path, TilesetDocument document) {}

    private static Candidate candidate(GridBlockDef block,
                                       Map<String, SheetDocument> documents, boolean inUse) {
        String sheetName = sheetNameOf(block.sheetPath);
        SheetDocument source = documents.get(sheetName);
        List<Slot> slots = new ArrayList<>();
        if (source != null) {
            for (TilesetExport.Entry entry : source.document().entries) {
                if (block.id.equals(entry.blockId)) {
                    slots.add(new Slot(entry.id, entry.slot, entry.included));
                }
            }
        }
        return new Candidate(block.id, shapeOf(block), sheetName,
                slots.isEmpty() ? null : source.path(), List.copyOf(slots), inUse);
    }

    /** The layout name a block resolves by, in the spelling its JSON uses. */
    static String shapeOf(GridBlockDef block) {
        if (block.isVariantPool() || block.layout == null) return VARIANT_POOL;
        return block.layout.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** {@code graphics/tilesets/urban-tileset.png} to {@code urban-tileset}. */
    static String sheetNameOf(String sheetPath) {
        if (sheetPath == null) return "";
        String file = sheetPath.substring(sheetPath.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        return dot < 0 ? file : file.substring(0, dot);
    }

    /**
     * A sheet's art and its authoring document are named alike but the block's
     * sheet path is the <em>packed</em> atlas, which may carry a different name
     * from the raw sheet it came from. Where they differ the document names the
     * output, so the lookup goes both ways.
     */
    private static Map<String, SheetDocument> loadDocuments(Path projectRoot) {
        Map<String, SheetDocument> byName = new LinkedHashMap<>();
        for (TilesetLibrary.Sheet sheet : TilesetLibrary.scan(projectRoot)) {
            if (sheet.document() == null) continue;
            TilesetDocument document;
            try {
                document = TilesetDocument.read(sheet.document());
            } catch (IOException | JSONException unreadable) {
                // A document that will not parse is the sheet-first tools' problem
                // to report; here it simply contributes no candidates.
                continue;
            }
            SheetDocument source = new SheetDocument(sheet.document(), document);
            byName.put(sheet.name(), source);
            String output = sheetNameOf(document.resolvedOutputSheet(!document.blocks.isEmpty()));
            if (!output.isEmpty()) byName.putIfAbsent(output, source);
        }
        return byName;
    }

    private static TileRegistry loadTilesets(Path projectRoot) throws IOException, JSONException {
        TileRegistry registry = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            registry.ingestSheet(new JSONObject(readModResource(projectRoot, path)));
        }
        return registry;
    }

    private static GenMappingRegistry loadMapping(Path projectRoot)
            throws IOException, JSONException {
        GenMappingRegistry mapping = new GenMappingRegistry();
        for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
            mapping.ingest(new JSONObject(readModResource(projectRoot, path)));
        }
        return mapping;
    }

    private static String readModResource(Path projectRoot, String modRelative) throws IOException {
        Path path = projectRoot.resolve("mod");
        for (String part : modRelative.split("/")) path = path.resolve(part);
        return Files.readString(path);
    }
}
