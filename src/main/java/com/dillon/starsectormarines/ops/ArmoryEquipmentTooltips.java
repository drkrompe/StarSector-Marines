package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

import java.util.ArrayList;
import java.util.List;

/** Screen-scoped hover behavior for equipment lore in marine dossiers. */
final class ArmoryEquipmentTooltips {

    private static final String HIDDEN_CLASS = "tooltip-hidden";
    private static final ArmoryEquipmentTooltips EMPTY =
            new ArmoryEquipmentTooltips(List.of());

    private final List<CardTooltips> cards;

    private ArmoryEquipmentTooltips(List<CardTooltips> cards) {
        this.cards = cards;
    }

    static ArmoryEquipmentTooltips empty() {
        return EMPTY;
    }

    static ArmoryEquipmentTooltips bind(
            MarkupInstance markup, List<FleetArmoryViewModel.MarineViewerCard> cards) {
        List<CardTooltips> bindings = new ArrayList<>();
        for (FleetArmoryViewModel.MarineViewerCard card : cards) {
            markup.requireElement(card.id()).layout(UiLayout.STACK);
            markup.requireElement(card.contentId()).align(UiAlign.STRETCH, UiAlign.STRETCH);

            List<EquipmentTooltip> equipment = List.of(
                    tooltip(markup, card.primaryId(), card.primaryDescriptionId()),
                    tooltip(markup, card.armorId(), card.armorDescriptionId()),
                    tooltip(markup, card.specialId(), card.specialDescriptionId()));
            bindings.add(new CardTooltips(equipment));
        }
        return new ArmoryEquipmentTooltips(List.copyOf(bindings));
    }

    private static EquipmentTooltip tooltip(
            MarkupInstance markup, String targetId, String tooltipId) {
        UiElement popup = markup.requireElement(tooltipId)
                .align(UiAlign.CENTER, UiAlign.START)
                .addClass(HIDDEN_CLASS);
        return new EquipmentTooltip(markup.requireElement(targetId), popup);
    }

    void update() {
        for (CardTooltips card : cards) card.update();
    }

    private static final class CardTooltips {
        private final List<EquipmentTooltip> equipment;
        private EquipmentTooltip open;

        private CardTooltips(List<EquipmentTooltip> equipment) {
            this.equipment = equipment;
        }

        private void update() {
            EquipmentTooltip next = null;
            for (EquipmentTooltip candidate : equipment) {
                if (candidate.target().hovered()) {
                    next = candidate;
                    break;
                }
            }
            if (next == null && open != null && open.popup().hovered()) next = open;
            if (next == open) return;
            if (open != null) open.popup().addClass(HIDDEN_CLASS);
            open = next;
            if (open != null) open.popup().removeClass(HIDDEN_CLASS);
        }
    }

    private record EquipmentTooltip(UiElement target, UiElement popup) { }
}
