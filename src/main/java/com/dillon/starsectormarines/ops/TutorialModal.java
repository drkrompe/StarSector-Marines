package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import java.util.function.BooleanSupplier;

/** Shared portrait-dialogue input boundary for shipboard tutorials. */
final class TutorialModal {
    private TutorialModal() { }

    static void bind(UiDocument document, MarkupInstance markup, BooleanSupplier visible,
                     String dialogId, String rootId, Runnable dismiss, Runnable cancel) {
        document.inputScope(() -> markup.requireElement(visible.getAsBoolean() ? dialogId : rootId));
        document.onCancel(() -> {
            if (visible.getAsBoolean()) dismiss.run();
            else cancel.run();
        });
    }
}
