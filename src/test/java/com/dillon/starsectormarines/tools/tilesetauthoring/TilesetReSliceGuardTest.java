package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which lost entries a re-slice may throw away and which it may not.
 *
 * <p>The two claims pull against each other and both matter. Tuning a threshold
 * on a keyed sheet is supposed to drop and re-find pieces freely, so a guard
 * that fired on mechanically derived pieces would make the sweep unusable. A cut
 * plate is the opposite case: its cells reconcile to nothing every time, so an
 * unguarded apply destroys the cut and everything assigned onto it.
 */
class TilesetReSliceGuardTest {

    private static final String PREFIX = "doodad.urban";

    private static TilesetExport.Entry piece(String id) {
        return new TilesetExport.Entry(new SheetSlicer.Piece(0, 0, 64, 64), id);
    }

    private static List<TilesetOperations.AtRisk> atRisk(TilesetExport.Entry... lost) {
        return TilesetOperations.atRisk(List.of(lost), PREFIX);
    }

    @Test
    void aMechanicallyFoundPieceIsNotAuthoredWork() {
        assertEquals(List.of(), atRisk(piece(PREFIX + ".piece-007")),
                "a serial id with nothing on it is what a slice produced, so a later slice "
                        + "may take it back");
    }

    @Test
    void anExcludedSpeckIsNotAuthoredWork() {
        // Marking specks as not shipping and then raising the threshold until
        // they vanish is the sweep working, not work being lost.
        TilesetExport.Entry speck = piece(PREFIX + ".piece-012");
        speck.included = false;

        assertEquals(List.of(), atRisk(speck));
    }

    @Test
    void aCutCellIsAuthoredWorkEvenWithNothingWrittenOnIt() {
        TilesetExport.Entry cell = piece(TilesetOperations.gridId(PREFIX, 6, 1));

        List<TilesetOperations.AtRisk> found = atRisk(cell);

        assertEquals(1, found.size());
        assertEquals(PREFIX + ".c6r1", found.get(0).id());
        assertEquals("a cut cell", found.get(0).reason(),
                "the cut is a stated decision, and a re-slice silently reverses it");
    }

    @Test
    void aRenamedPieceIsAuthoredWork() {
        List<TilesetOperations.AtRisk> found = atRisk(piece(PREFIX + ".crate"));

        assertEquals(1, found.size());
        assertEquals("a chosen id", found.get(0).reason());
    }

    @Test
    void everyKindOfAnnotationCountsAndIsNamed() {
        TilesetExport.Entry member = piece(PREFIX + ".piece-000");
        member.blockId = "urban.wall";
        member.slot = "nw";
        TilesetExport.Entry noted = piece(PREFIX + ".piece-001");
        noted.note = "the loading bay's roller door";
        TilesetExport.Entry tagged = piece(PREFIX + ".piece-002");
        tagged.tags.add("industrial");
        TilesetExport.Entry covering = piece(PREFIX + ".piece-003");
        covering.cover = "heavy";
        TilesetExport.Entry wide = piece(PREFIX + ".piece-004");
        wide.footprintX = 3;
        TilesetExport.Entry candidate = piece(PREFIX + ".piece-005");
        candidate.standsInFor = "urban.wall";

        List<TilesetOperations.AtRisk> found =
                atRisk(member, noted, tagged, covering, wide, candidate);

        assertEquals(List.of("slot nw of block urban.wall", "a note", "tags", "cover heavy",
                        "footprint 3x1", "stands in for urban.wall"),
                found.stream().map(TilesetOperations.AtRisk::reason).toList());
    }

    @Test
    void theRefusalNamesWhatWouldGoAndStopsCountingSomewhere() {
        TilesetExport.Entry[] cells = new TilesetExport.Entry[100];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = piece(TilesetOperations.gridId(PREFIX, i % 10, i / 10));
        }

        String warning = TilesetOperations.discardWarning(atRisk(cells));

        assertTrue(warning.contains("100 entries"), warning);
        assertTrue(warning.contains(PREFIX + ".c0r0 (a cut cell)"), warning);
        assertTrue(warning.contains("and 94 more"),
                "a hundred names is not a warning anyone reads: " + warning);
        assertTrue(warning.contains("fused plate"),
                "the refusal should say why re-slicing cannot bring them back: " + warning);
    }
}
