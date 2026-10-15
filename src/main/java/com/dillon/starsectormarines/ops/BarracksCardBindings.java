package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerCareer;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.ops.battleview.SquadMusterCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded keyed-card wiring, shared by the live room and its render walkthrough. */
final class BarracksCardBindings {
    private List<Map<String, Object>> previous;
    private final List<UiElement> targets = new ArrayList<>();
    private List<Map<String, Object>> previousSpecialties;
    private final List<UiElement> specialtyTargets = new ArrayList<>();

    void update(UiDocument document, MarkupInstance instance, BarracksViewModel model, SpecSheetBinder hints) {
        update(document, instance, model.command(), hints);
    }

    void update(UiDocument document, MarkupInstance instance, BarracksCommandModel model, SpecSheetBinder hints) {
        updateSpecialties(instance, model, hints);
        List<Map<String, Object>> cards = model.cards();
        if (cards == previous) return;
        previous = cards;
        for (UiElement target : targets) hints.unbind(target);
        targets.clear();
        for (Map<String, Object> card : cards) {
            String id = (String) card.get("squadId");
            UiElement canvas = instance.requireElement((String) card.get("musterId"));
            if (document.canvases().producerOf(canvas) == null) {
                document.canvases().set(canvas, new SquadMusterCanvas(() -> model.muster(id), SquadMusterCanvas.SHIPBOARD));
            } else {
                document.canvases().invalidate(canvas);
            }
            UiElement target = instance.requireElement((String) card.get("id"));
            hints.bindLive(target, () -> model.squadSheet(id));
            targets.add(target);
        }
    }

    /** Each rank node describes itself on hover; the tree carries no effect copy of its own. */
    private void updateSpecialties(MarkupInstance instance, BarracksCommandModel model, SpecSheetBinder hints) {
        List<Map<String, Object>> columns = model.specialtyColumns();
        if (columns == previousSpecialties) return;
        previousSpecialties = columns;
        for (UiElement target : specialtyTargets) hints.unbind(target);
        specialtyTargets.clear();
        if (columns.isEmpty()) return;
        UiElement root = instance.requireElement("specialty-root");
        hints.bindLive(root, model::specialtyRootSheet);
        specialtyTargets.add(root);
        for (Map<String, Object> column : columns) {
            OfficerSpecialty specialty = (OfficerSpecialty) column.get("specialty");
            for (int rank = 1; rank <= OfficerCareer.MAX_LEVEL; rank++) {
                UiElement node = instance.requireElement((String) column.get("node" + rank + "Id"));
                int shown = rank;
                hints.bindLive(node, () -> model.specialtySheet(specialty, shown));
                specialtyTargets.add(node);
            }
        }
    }
}
