package com.dillon.starsectormarines.ui.retained.style;

import java.awt.Color;

/**
 * The CSS {@code grid-lines} value: one-pixel lines every {@code spacing} document pixels across
 * a box's border box, under its children. It is the faint grid the base game's screens have
 * behind everything. Painting only.
 */
public record GridLines(float spacing, Color color) {
}
