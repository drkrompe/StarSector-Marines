package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

/** Keeps the tutorial's input boundary identical in the live screen and headless evidence. */
final class BarracksGuideModal {
    private BarracksGuideModal() { }

    static void bind(UiDocument document, MarkupInstance markup, BarracksCommandModel command, Runnable cancel) {
        TutorialModal.bind(document, markup, command::guideVisible,
                "barracks-guide", "barracks-root", command::dismissGuide, cancel);
    }
}
