package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        bindCatalogRows(binder, markup, rows, null);
    }

    /**
     * With a view model, a weapon's flux lines read against the selected mech's own flux
     * capacitors and dissipation at the moment the sheet opens rather than the stock frame's.
     */
    static void bindCatalogRows(SpecSheetBinder binder, MarkupInstance markup,
                                List<MechLabViewModel.CatalogRow> rows,
                                MechLabViewModel viewModel) {
        if (binder == null) return;
        for (MechLabViewModel.CatalogRow row : rows) {
            // The whole row answers hover, not only its name; its FAB button and material
            // chips keep their own sheets because the deepest bound element wins.
            UiElement name = markup.requireElement(row.id());
            if (binder.isBound(name)) continue;
            if (row.chassisPreview() != null) {
                binder.bind(name, SpecSheets.mech(row.chassisPreview()));
            } else if (row.weaponPreview() != null || row.internalPreview() != null) {
                binder.bind(name, () -> partSheet(row.weaponPreview(), row.internalPreview(), viewModel));
            }
        }
    }

    /**
     * The doll answers like the stores: a fitted part under the pointer opens its
     * {@link #partSheet}, beside the part rather than the whole board. While a part is in hand
     * the doll points at nothing, so no sheet covers the socket it is going into.
     */
    static void bindDoll(SpecSheetBinder binder, UiDocument document, UiElement dollElement,
                         MechLabDollCanvas doll, MechLabViewModel viewModel) {
        binder.bindLive(dollElement, () -> {
            MechLabDollCanvas.PointedPart part = doll.pointedPart();
            return part == null ? null : partSheet(part.weapon(), part.internal(), viewModel);
        }, () -> {
            MechLabDollCanvas.PointedPart part = doll.pointedPart();
            if (part == null) return null;
            CanvasMetrics metrics = document.canvasMetrics(dollElement, 1f);
            float left = metrics.toDocumentX(part.left());
            float top = metrics.toDocumentY(part.top());
            return new Rect(left, top, metrics.toDocumentX(part.right()) - left,
                    metrics.toDocumentY(part.bottom()) - top);
        });
    }

    /**
     * A weapon's or an internal's sheet: the same in stores and fitted on the doll, so a part
     * reads alike wherever the player points at it. With a view model, a weapon's flux lines read
     * against the selected mech's own capacitors and dissipation. Null when there is no part.
     */
    static SpecSheet partSheet(MechWeaponComponent weapon, MechInternal internal, MechLabViewModel viewModel) {
        if (weapon != null) {
            return viewModel == null ? SpecSheets.mechWeapon(weapon) : SpecSheets.mechWeapon(weapon,
                    viewModel.selectedFluxCapacity(), viewModel.selectedDissipationPerSecond());
        }
        return internal != null ? internalSheet(internal) : null;
    }

    /** One internal's own numbers; what it does to a particular frame is the catalog row's. */
    static SpecSheet internalSheet(MechInternal internal) {
        List<SpecSheet.Stat> stats = new ArrayList<>();
        stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecMass"), internal.role == MechInternal.Role.PLATING
                ? Strings.format("openingMechLabSpecPlatingMass", number(internal.mass))
                : number(internal.mass)));
        switch (internal.role) {
            case ENGINE -> {
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecOutput"),
                        Strings.format("openingMechLabSpecOutputFactor", number(internal.outputFactor))));
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecSpeed"),
                        Strings.format("openingMechLabSpecBand", Math.round(internal.speedFraction * 100f))));
                if (internal.dissipation != 0f) stats.add(SpecSheet.Stat.of(
                        Strings.get("openingMechLabSpecDissipation"), signed(internal.dissipation)));
            }
            case PLATING -> stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecArmor"),
                    Strings.format("openingMechLabSpecBand", Math.round(internal.armorFraction * 100f))));
            case CAPACITOR_BANK -> {
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecFlux"), number(internal.fluxCapacity)));
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecDissipation"), number(internal.dissipation)));
            }
            case VENT_ARRAY -> stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecDissipation"),
                    signed(internal.dissipation)));
            case LOADER -> {
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsSrmCycle"),
                        Strings.format("openingMechLabSpecSheetsS", internal.replenisher().srmReplenishmentSeconds())));
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsLrmCycle"),
                        Strings.format("openingMechLabSpecSheetsS", internal.replenisher().lrmReplenishmentSeconds())));
                // The same loader feeds every rotary gun's belt (LoaderPace).
                stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsGunFeed"),
                        Strings.format("openingMechLabSpecSheetsFeedFactor", number(internal.replenisher().loaderFactor()))));
            }
        }
        if (internal.role.compartment()) stats.add(SpecSheet.Stat.of(Strings.get("openingMechLabSpecFootprint"),
                internal.footprintColumns + "x" + internal.footprintRows));
        String role = switch (internal.role) {
            case ENGINE -> Strings.get("openingMechLabRoleEngine");
            case PLATING -> Strings.get("openingMechLabRolePlating");
            case CAPACITOR_BANK -> Strings.get("openingMechLabRoleCapacitorBank");
            case VENT_ARRAY -> Strings.get("openingMechLabRoleVentArray");
            case LOADER -> Strings.get("openingMechLabRoleLoader");
        };
        String copy = switch (internal.role) {
            case ENGINE -> Strings.get("openingMechLabEngineCopy");
            case PLATING -> Strings.get("openingMechLabPlatingCopy");
            case CAPACITOR_BANK -> Strings.get("openingMechLabCapacitorBankCopy");
            case VENT_ARRAY -> Strings.get("openingMechLabVentArrayCopy");
            case LOADER -> Strings.get("openingMechLabLoaderCopy");
        };
        return new SpecSheet(internal.displayName(), role, null, "item", List.copyOf(stats), List.of(copy));
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return Integer.toString(Math.round(value));
        return String.format(Strings.locale(), "%.2f", value).replaceAll("0+$", "");
    }

    private static String signed(float value) {
        return (value > 0f ? "+" : "") + number(value);
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

    /** A category symbol names itself on hover, since it carries no word. */
    static void bindFilterPills(SpecSheetBinder binder, MarkupInstance markup,
                                List<FittingFilterPill> pills) {
        if (binder == null) return;
        for (FittingFilterPill pill : pills) {
            UiElement element = markup.requireElement(pill.id());
            if (binder.isBound(element) || pill.icon() == null) continue;
            binder.bind(element, new SpecSheet(pill.label(), Strings.get("openingMechLabFilterSubtitle"),
                    pill.icon(), "item", List.of(), List.of(Strings.get("openingMechLabFilterHint"))));
        }
    }

    static void bindMaterials(SpecSheetBinder binder, MarkupInstance markup,
                              MechLabViewModel viewModel) {
        if (binder == null) return;
        for (CompanyResources.Row material : viewModel.materialStock().get()) {
            bindMaterial(binder, markup, material, () -> viewModel.materialStock().get());
        }
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            for (CompanyResources.Row material : row.materials()) {
                bindMaterial(binder, markup, material, () -> viewModel.catalogRows().get().stream()
                        .flatMap(current -> current.materials().stream()).toList());
            }
        }
    }

    private static void bindMaterial(SpecSheetBinder binder, MarkupInstance markup,
                                     CompanyResources.Row material,
                                     Supplier<List<CompanyResources.Row>> currentRows) {
        UiElement target = markup.requireElement(material.id());
        if (!binder.isBound(target)) {
            binder.bind(target, () -> currentRows.get().stream()
                    .filter(current -> current.id().equals(material.id()))
                    .findFirst().map(MechLabSpecSheets::materialSheet).orElse(null));
        }
    }

    static SpecSheet materialSheet(CompanyResources.Row material) {
        if (material.note() != null) return new SpecSheet(material.name(), material.note(), material.icon(), "item",
                List.of(), List.of(Strings.get("fabricationPortSheet")));
        boolean cost = material.required() > 0;
        return new SpecSheet(material.name(), cost ? Strings.get("openingMechLabSpecSheetsManufacturingCost") : Strings.get("openingMechLabSpecSheetsFleetMaterials"),
                material.icon(), "item", cost ? List.of(
                        SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsRequired"), CompanyResources.number(material.required())),
                        SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsInCargo"), CompanyResources.number(material.available())))
                        : List.of(SpecSheet.Stat.of(Strings.get("openingMechLabSpecSheetsInCargo"), CompanyResources.number(material.available()))),
                List.of(cost ? material.available() >= material.required()
                        ? Strings.get("openingMechLabSpecSheetsEnoughInCargoForThisMaterialCost")
                        : Strings.format("openingMechLabSpecSheetsShortBy", (material.required() - material.available()))
                        : Strings.get("openingMechLabSpecSheetsSharedFleetCargoUsedByFabrication")));
    }

    /** The first row this binds, for headless evidence. */
    static String firstSpecSheetAnchorId(List<MechLabViewModel.CatalogRow> rows) {
        for (MechLabViewModel.CatalogRow row : rows) {
            if (row.chassisPreview() != null || row.weaponPreview() != null
                    || row.internalPreview() != null) {
                return row.nameId();
            }
        }
        throw new IllegalStateException("No catalog row on this page carries a spec-sheet subject");
    }
}
