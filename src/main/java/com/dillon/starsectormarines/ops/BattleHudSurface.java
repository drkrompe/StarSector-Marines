package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;

/** One currently visible retained surface, with its production host placement. */
record BattleHudSurface(UiDocument document, UiViewport viewport) { }
