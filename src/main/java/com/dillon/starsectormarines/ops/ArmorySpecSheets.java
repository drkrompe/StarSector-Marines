package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Which elements of the Fleet Armory's cards a player can ask about
 * ({@code spec-sheet.md}).
 *
 * <p>Static and projection-driven so headless evidence hovers the same elements
 * the game does (law 11) rather than a reconstruction of them. It replaces
 * {@code ArmoryEquipmentTooltips}, which built four popup elements per card and
 * ran its own hover state machine; the shared layer is one overlay for the whole
 * document, and the copy comes from the item's owning catalog rather than from a
 * description the view model carried for the popup's sake.
 *
 * <p>Every binding <b>reads the live projection by slot</b> rather than the sheet
 * held by the card that was on screen when the document was built. Cards are
 * keyed and reconciled in place — cycling a billet's primary, or switching fire
 * team, replaces the projection while leaving the element alone — so a captured
 * sheet would go on describing equipment that is no longer there.
 */
final class ArmorySpecSheets {

    private ArmorySpecSheets() { }

    /** The four equipment labels on each marine dossier. */
    static void bindMarineCards(SpecSheetBinder binder, MarkupInstance markup,
                                Signal<List<FleetArmoryViewModel.MarineViewerCard>> cards) {
        List<FleetArmoryViewModel.MarineViewerCard> projected = cards.get();
        for (int index = 0; index < projected.size(); index++) {
            FleetArmoryViewModel.MarineViewerCard card = projected.get(index);
            int slot = index;
            bind(binder, markup, card.primaryId(), () -> at(cards, slot,
                    FleetArmoryViewModel.MarineViewerCard::primarySheet));
            bind(binder, markup, card.armorId(), () -> at(cards, slot,
                    FleetArmoryViewModel.MarineViewerCard::armorSheet));
            bind(binder, markup, card.specialId(), () -> at(cards, slot,
                    FleetArmoryViewModel.MarineViewerCard::specialSheet));
            bind(binder, markup, card.systemId(), () -> at(cards, slot,
                    FleetArmoryViewModel.MarineViewerCard::systemSheet));
        }
    }

    /**
     * The primary and specialty a billet is being authored with in the doctrine
     * designer. Role and grade are not catalog items and carry no sheet; armour
     * is not authored here at all — the squad's tactic sheet issues it.
     */
    static void bindBilletCards(
            SpecSheetBinder binder, MarkupInstance markup,
            Signal<List<EquipmentDoctrineDesignerViewModel.BilletCard>> cards) {
        List<EquipmentDoctrineDesignerViewModel.BilletCard> projected = cards.get();
        for (int index = 0; index < projected.size(); index++) {
            EquipmentDoctrineDesignerViewModel.BilletCard card = projected.get(index);
            int slot = index;
            bind(binder, markup, card.primaryId(), () -> at(cards, slot,
                    EquipmentDoctrineDesignerViewModel.BilletCard::primarySheet));
            bind(binder, markup, card.specialId(), () -> at(cards, slot,
                    EquipmentDoctrineDesignerViewModel.BilletCard::specialSheet));
        }
    }

    private static void bind(SpecSheetBinder binder, MarkupInstance markup,
                             String elementId, Supplier<SpecSheet> sheet) {
        binder.bind(markup.requireElement(elementId), sheet);
    }

    /** Null where the slot has gone away or the billet carries nothing to describe. */
    private static <T> SpecSheet at(Signal<List<T>> cards, int slot,
                                    Function<T, SpecSheet> sheet) {
        List<T> projected = cards.get();
        return slot < projected.size() ? sheet.apply(projected.get(slot)) : null;
    }
}
