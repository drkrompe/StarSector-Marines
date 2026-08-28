package com.dillon.starsectormarines.battle.world.tiles;

import java.util.List;

/**
 * A human-authored annotation for one source-sheet cell — the per-cell
 * {@code name}, {@code description} and {@code tags} the dev tileset viewer
 * shows. This is the folded-in successor to the former per-sheet
 * {@code .catalog.json} files: grid sheets carry these in their
 * {@code .tileset.json} {@code "cells"} array, sliced sheets derive them from
 * each tile's {@code name}/{@code description}. Resolved via
 * {@link TileRegistry#cellLabel}.
 *
 * <p>Doc-only metadata — generation and rendering never read it; it exists so the
 * art reference lives in one file per sheet alongside the game-used tile/block
 * definitions.
 *
 * <p>{@link #tags} is the greppable half of that annotation, for questions like
 * "which of these are exterior industrial pieces" that a free-text description
 * answers only to a reader. It stays descriptive: a tag becomes a generation or
 * tactical selector only through the code path that owns that law, never by
 * being read here.
 */
public final class CellLabel {

    public final String name;
    public final String description;
    /** Descriptive tags, lowercase and de-duplicated; empty when unannotated. */
    public final List<String> tags;

    public CellLabel(String name, String description) {
        this(name, description, List.of());
    }

    public CellLabel(String name, String description, List<String> tags) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
