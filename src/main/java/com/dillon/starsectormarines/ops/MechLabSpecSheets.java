package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.List;

/**
 * Which names in the Mech Lab's catalog a player can ask about
 * ({@code spec-sheet.md}).
 *
 * <p>The catalog names a chassis or a piece of hardware per row, so the row's
 * name is the subject and the sheet comes from the mech catalog. This runs
 * <b>beside</b> the row's paper-doll hover rather than instead of it: pointing
 * at a weapon row still previews that weapon on the doll, which is a preview of
 * a fitting rather than a description of an item.
 *
 * <p>Catalog rows arrive after the document is installed — a player moving from
 * a fitted gantry to a vacant one grows the list — so this is called again from
 * the screen's advance and skips what it has already bound. A row whose subject
 * is a replenisher carries no sheet; the mech catalog describes chassis and
 * hardware, and there is nothing authored to say about a magazine feed.
 */
final class MechLabSpecSheets {

    private MechLabSpecSheets() { }

    static void bindCatalogRows(SpecSheetBinder binder, MarkupInstance markup,
                                List<MechLabViewModel.CatalogRow> rows) {
        if (binder == null) return;
        for (MechLabViewModel.CatalogRow row : rows) {
            UiElement name = markup.requireElement(row.nameId());
            if (binder.isBound(name)) continue;
            if (row.chassisPreview() != null) {
                binder.bind(name, SpecSheets.mech(row.chassisPreview()));
            } else if (row.weaponPreview() != null) {
                binder.bind(name, SpecSheets.mechWeapon(row.weaponPreview()));
            }
        }
    }

    /** The first row this binds, for headless evidence. */
    static String firstSpecSheetAnchorId(List<MechLabViewModel.CatalogRow> rows) {
        for (MechLabViewModel.CatalogRow row : rows) {
            if (row.chassisPreview() != null || row.weaponPreview() != null) {
                return row.nameId();
            }
        }
        throw new IllegalStateException("No catalog row on this page carries a spec-sheet subject");
    }
}
