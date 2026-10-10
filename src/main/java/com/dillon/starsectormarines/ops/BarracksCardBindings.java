package com.dillon.starsectormarines.ops;

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

    void update(UiDocument document, MarkupInstance instance, BarracksCommandModel model, SpecSheetBinder hints) {
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
}
