package com.dillon.starsectormarines.battle.world.tiles;

/**
 * A human-authored annotation for one source-sheet cell — the per-cell
 * {@code name} + {@code description} the dev tileset viewer shows. This is the
 * folded-in successor to the former per-sheet {@code .catalog.json} files: grid
 * sheets carry these in their {@code .tileset.json} {@code "cells"} array, sliced
 * sheets derive them from each tile's {@code name}/{@code description}. Resolved
 * via {@link TileRegistry#cellLabel}.
 *
 * <p>Doc-only metadata — generation and rendering never read it; it exists so the
 * art reference lives in one file per sheet alongside the game-used tile/block
 * definitions.
 */
public final class CellLabel {

    public final String name;
    public final String description;

    public CellLabel(String name, String description) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
    }
}
