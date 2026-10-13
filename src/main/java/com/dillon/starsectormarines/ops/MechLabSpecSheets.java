package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ops.battleview.MechReplenisherArt;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.List;
import java.util.function.Supplier;

/**
 * Which names in the Mech Lab's catalog a player can ask about
 * ({@code ui-nouns.md}).
 *
 * <p>The catalog names a chassis or a piece of hardware per row, so the row's
 * name is the subject and the sheet comes from the mech catalog. This runs
 * <b>beside</b> the row's paper-doll hover rather than instead of it: pointing
 * at a weapon row still previews that weapon on the doll, which is a preview of
 * a fitting rather than a description of an item.
 *
 * <p>Catalog rows arrive after the document is installed — a player moving from
 * a fitted gantry to a vacant one grows the list — so this is called again from
 * the screen's advance and skips what it has already bound.
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
            } else if (row.replenisherPreview() != null) {
                binder.bind(name, replenisherSheet(row.replenisherPreview()));
            }
        }
    }

    static void bindActions(SpecSheetBinder binder, MarkupInstance markup,
                            MechLabViewModel viewModel) {
        if (binder == null) return;
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            UiElement action = markup.requireElement(row.actionId());
            if (!binder.isBound(action)) {
                binder.bindLive(action, () -> viewModel.catalogRows().get().stream()
                        .filter(current -> current.id().equals(row.id()))
                        .findFirst().map(current -> new SpecSheet(current.name(),
                                current.actionLabel(), null, "item", List.of(),
                                List.of(current.actionHint()))).orElse(null));
            }
        }
    }

    private static SpecSheet replenisherSheet(MissileReplenisherComponent component) {
        return new SpecSheet(component.displayName(), Strings.get("openingMechLabSpecSheetsMissileMiniFab"),
                MechReplenisherArt.icon(component), "item", List.of(
                        SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsSrmCycle"), Strings.format("openingMechLabSpecSheetsS", component.srmReplenishmentSeconds())),
                        SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsLrmCycle"), Strings.format("openingMechLabSpecSheetsS", component.lrmReplenishmentSeconds()))),
                List.of(Strings.get("openingMechLabSpecSheetsBuildsOneMissileTriggerPackPerCycle"),
                        Strings.get("openingMechLabSpecSheetsInstallFromFleetSparesThisSystemCannot")));
    }

    static void bindMaterials(SpecSheetBinder binder, MarkupInstance markup,
                              MechLabViewModel viewModel) {
        if (binder == null) return;
        for (MechLabViewModel.MaterialRow material : viewModel.materialStock().get()) {
            bindMaterial(binder, markup, material, () -> viewModel.materialStock().get());
        }
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            for (MechLabViewModel.MaterialRow material : row.materials()) {
                bindMaterial(binder, markup, material, () -> viewModel.catalogRows().get().stream()
                        .flatMap(current -> current.materials().stream()).toList());
            }
        }
    }

    private static void bindMaterial(SpecSheetBinder binder, MarkupInstance markup,
                                     MechLabViewModel.MaterialRow material,
                                     Supplier<List<MechLabViewModel.MaterialRow>> currentRows) {
        UiElement target = markup.requireElement(material.id());
        if (!binder.isBound(target)) {
            binder.bind(target, () -> currentRows.get().stream()
                    .filter(current -> current.id().equals(material.id()))
                    .findFirst().map(MechLabSpecSheets::materialSheet).orElse(null));
        }
    }

    static SpecSheet materialSheet(MechLabViewModel.MaterialRow material) {
        boolean cost = material.required() > 0;
        return new SpecSheet(material.name(), cost ? Strings.get("openingMechLabSpecSheetsManufacturingCost") : Strings.get("openingMechLabSpecSheetsFleetMaterials"),
                material.icon(), "item", cost ? List.of(
                        SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsRequired"), Integer.toString(material.required())),
                        SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsInCargo"), Integer.toString(material.available())))
                        : List.of(SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsInCargo"), Integer.toString(material.available()))),
                List.of(cost ? material.available() >= material.required()
                        ? Strings.get("openingMechLabSpecSheetsEnoughInCargoForThisMaterialCost")
                        : Strings.format("openingMechLabSpecSheetsShortBy", (material.required() - material.available()))
                        : Strings.get("openingMechLabSpecSheetsSharedFleetCargoUsedByFabrication")));
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
