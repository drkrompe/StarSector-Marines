package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.KitBrick;
import com.dillon.starsectormarines.ops.battleview.KitBrickCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

import java.util.List;
import java.util.function.Supplier;

/**
 * Paints the kit bricks a weapon card lists its issue on ({@link FleetArmoryViewModel.DoctrineTile#issuesAsBricks}).
 * Cards arrive and leave as the list is filtered, so this is called again after each
 * reconciliation and skips a brick that already has its producer; each producer finds its
 * card by id in the live list, as the cards' hover sheets do.
 */
final class LoadoutCardBricks {
    private LoadoutCardBricks() { }

    static void bind(UiDocument document, MarkupInstance markup,
                     Supplier<List<FleetArmoryViewModel.DoctrineTile>> tiles) {
        if (document == null || markup == null) return;
        for (FleetArmoryViewModel.DoctrineTile tile : tiles.get()) {
            if (!tile.issuesAsBricks()) continue;
            List<FleetArmoryViewModel.DoctrineSpan> spans = tile.distributionSpans();
            for (int index = 0; index < spans.size(); index++) {
                UiElement canvas = markup.requireElement(spans.get(index).id() + ":brick");
                if (document.canvases().producerOf(canvas) != null) continue;
                String tileId = tile.id();
                int slot = index;
                document.canvases().set(canvas, new KitBrickCanvas(() -> brickAt(tiles, tileId, slot)));
            }
        }
    }

    private static KitBrick brickAt(Supplier<List<FleetArmoryViewModel.DoctrineTile>> tiles,
                                    String tileId, int slot) {
        for (FleetArmoryViewModel.DoctrineTile tile : tiles.get()) {
            if (!tile.id().equals(tileId)) continue;
            return slot < tile.distributionSpans().size() ? tile.distributionSpans().get(slot).brick() : null;
        }
        return null;
    }
}
