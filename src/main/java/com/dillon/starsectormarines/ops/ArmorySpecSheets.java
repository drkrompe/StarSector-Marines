package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Which elements of the Fleet Armory's cards a player can ask about
 * ({@code ui-nouns.md}).
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

    /**
     * Every entry a loadout card's issue lines name: each weapon in a weapon
     * loadout's twelve-billet issue and its specialty count, and each armour
     * pattern and carried capability on a tactic sheet.
     *
     * <p>Cards arrive and leave after the document is installed — a rarity
     * filter, the supplies switch, or a newly known loadout changes the list —
     * so this is called again from the screen's advance after reconciliation
     * and skips what it has already bound. An entry that names no single
     * catalog item, such as a specialty count over mixed items, is bound to a
     * supplier answering null: it is still a name in the line, and the overlay
     * that opens for it is nothing.
     */
    static void bindDoctrineTiles(SpecSheetBinder binder, MarkupInstance markup,
                                  Signal<List<FleetArmoryViewModel.DoctrineTile>> tiles) {
        if (binder == null) return;
        for (FleetArmoryViewModel.DoctrineTile tile : tiles.get()) {
            bindSpans(binder, markup, tiles, tile.id(), tile.distributionSpans(),
                    FleetArmoryViewModel.DoctrineTile::distributionSpans);
            bindSpans(binder, markup, tiles, tile.id(), tile.carriesSpans(),
                    FleetArmoryViewModel.DoctrineTile::carriesSpans);
        }
    }

    /** The first loadout entry {@link #bindDoctrineTiles} binds, for headless evidence. */
    static String firstSpecSheetAnchorId(List<FleetArmoryViewModel.DoctrineTile> tiles) {
        for (FleetArmoryViewModel.DoctrineTile tile : tiles) {
            for (FleetArmoryViewModel.DoctrineSpan span : tile.distributionSpans()) {
                if (span.sheet() != null) return span.id();
            }
        }
        throw new IllegalStateException("No loadout card lists an entry with a spec sheet");
    }

    private static void bindSpans(
            SpecSheetBinder binder, MarkupInstance markup,
            Signal<List<FleetArmoryViewModel.DoctrineTile>> tiles, String tileId,
            List<FleetArmoryViewModel.DoctrineSpan> spans,
            Function<FleetArmoryViewModel.DoctrineTile,
                    List<FleetArmoryViewModel.DoctrineSpan>> line) {
        for (int index = 0; index < spans.size(); index++) {
            UiElement element = markup.requireElement(spans.get(index).id());
            if (binder.isBound(element)) continue;
            int slot = index;
            binder.bind(element, () -> sheetAt(tiles, tileId, slot, line));
        }
    }

    /**
     * The entry's sheet as the card reads <b>now</b>. Loadout cards are keyed
     * and reconciled in place, and the list is filtered and re-ranked
     * underneath them, so the card is found by its own id rather than by the
     * position it held when the row was built.
     */
    private static SpecSheet sheetAt(
            Signal<List<FleetArmoryViewModel.DoctrineTile>> tiles, String tileId, int slot,
            Function<FleetArmoryViewModel.DoctrineTile,
                    List<FleetArmoryViewModel.DoctrineSpan>> line) {
        for (FleetArmoryViewModel.DoctrineTile tile : tiles.get()) {
            if (!tile.id().equals(tileId)) continue;
            List<FleetArmoryViewModel.DoctrineSpan> spans = line.apply(tile);
            return slot < spans.size() ? spans.get(slot).sheet() : null;
        }
        return null;
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
