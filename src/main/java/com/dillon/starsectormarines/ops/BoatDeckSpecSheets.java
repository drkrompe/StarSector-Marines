package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas;
import com.dillon.starsectormarines.ops.battleview.BoatDoll;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the Boat Deck says about a part when the player points at it, as
 * {@link MechLabSpecSheets} does for the Mech Lab: one sheet per part, the same
 * on its stores card and fitted on the boat's doll.
 */
final class BoatDeckSpecSheets {

    private BoatDeckSpecSheets() { }

    /**
     * Binds each stores card to its part's sheet, written when it opens so it describes the part
     * on the boat selected then; cards already bound are left alone.
     */
    static void bindCatalogRows(SpecSheetBinder binder, MarkupInstance markup,
                                List<BoatDeckViewModel.CatalogRow> rows, Supplier<ShuttleType> pattern) {
        if (binder == null) return;
        for (BoatDeckViewModel.CatalogRow row : rows) {
            UiElement card = markup.requireElement(row.id());
            if (binder.isBound(card)) continue;
            BoatDoll.Kind kind = row.kind();
            String itemId = row.itemId();
            binder.bind(card, () -> partSheet(kind, itemId, pattern.get()));
        }
    }

    /**
     * The doll answers like the stores: a fitted part under the pointer opens its
     * {@link #partSheet}, beside the part. While a part is in hand the board points at
     * nothing, so no sheet covers the socket it is going into.
     */
    static void bindDoll(SpecSheetBinder binder, UiDocument document, UiElement bay,
                         Supplier<BoatDeckCanvas> canvas, Supplier<ShuttleType> pattern) {
        binder.bindLive(bay, () -> {
            BoatDeckCanvas.PointedPart part = pointed(canvas);
            return part == null ? null : partSheet(part.kind(), part.itemId(), pattern.get());
        }, () -> {
            BoatDeckCanvas.PointedPart part = pointed(canvas);
            if (part == null) return null;
            CanvasMetrics metrics = document.canvasMetrics(bay, 1f);
            float left = metrics.toDocumentX(part.left());
            float top = metrics.toDocumentY(part.top());
            return new Rect(left, top, metrics.toDocumentX(part.right()) - left,
                    metrics.toDocumentY(part.bottom()) - top);
        });
    }

    private static BoatDeckCanvas.PointedPart pointed(Supplier<BoatDeckCanvas> canvas) {
        BoatDeckCanvas current = canvas.get();
        return current == null ? null : current.pointedPart();
    }

    /**
     * A door gun's or a fitting's sheet: its tier and kind, its figures on a {@code pattern} boat,
     * and its yard's account of it.
     */
    static SpecSheet partSheet(BoatDoll.Kind kind, String itemId, ShuttleType pattern) {
        if (itemId == null) return null;
        if (kind == BoatDoll.Kind.HARDPOINT) {
            BoatGun gun = BoatGun.findById(itemId);
            if (gun == null) return null;
            return new SpecSheet(gun.displayName(), BoatDeckViewModel.kindLine(gun.tier(), kind), null, "item",
                    List.of(), List.of(gun.effect(), gun.provenance()));
        }
        BoatFitting fitting = BoatFitting.findById(itemId);
        if (fitting == null) return null;
        List<SpecSheet.Stat> stats = new ArrayList<>();
        switch (fitting.slot()) {
            case PLATING -> stats.add(SpecSheet.Stat.of(Strings.get("boatDeckArmor"),
                    "×" + BoatDeckViewModel.factor(fitting.armorFactor())));
            case DRIVE -> {
                stats.add(SpecSheet.Stat.of(Strings.get("openingBoatDeckSpeed"),
                        "×" + BoatDeckViewModel.factor(fitting.speedFactor())));
                stats.add(SpecSheet.Stat.of(Strings.get("boatDeckSpecAccel"),
                        "×" + BoatDeckViewModel.factor(fitting.accelFactor())));
            }
            case LIFT -> stats.add(SpecSheet.Stat.of(Strings.get("boatDeckLift"),
                    LiftReadout.ratingName(fitting.liftOn(pattern))));
            case CABIN -> {
                if (fitting.seats() > 0) {
                    stats.add(SpecSheet.Stat.of(Strings.get("openingBoatDeckSeats"), String.valueOf(fitting.seats())));
                }
            }
            case EMITTER -> {
                if (fitting.shieldArcDeg() > 0f) {
                    stats.add(SpecSheet.Stat.of(Strings.get("boatDeckSpecShieldArc"),
                            Math.round(fitting.shieldArcDeg()) + "°"));
                }
                stats.add(SpecSheet.Stat.of(Strings.get("boatDeckSpecFluxPerHit"),
                        "×" + BoatDeckViewModel.factor(fitting.shieldEfficiency())));
            }
            case POWER -> {
                stats.add(SpecSheet.Stat.of(Strings.get("boatDeckSpecFluxCapacity"),
                        "×" + BoatDeckViewModel.factor(fitting.fluxCapacityFactor())));
                stats.add(SpecSheet.Stat.of(Strings.get("boatDeckSpecFluxVent"),
                        "×" + BoatDeckViewModel.factor(fitting.fluxDissipationFactor())));
            }
        }
        return new SpecSheet(fitting.displayName(), BoatDeckViewModel.kindLine(fitting.tier(), kind), null, "item",
                List.copyOf(stats), List.of(fitting.provenance()));
    }
}
