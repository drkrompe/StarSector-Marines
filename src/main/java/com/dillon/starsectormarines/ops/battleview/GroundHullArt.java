package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.Warship;

import java.util.Map;

/**
 * The ground battle's own art for a hull that has a remaster: a high-resolution redraw of the vanilla sprite that
 * keeps its silhouette, detail placement and canvas proportions, so slots, nozzles and armour-grid fractions read
 * off the vanilla {@code .ship} land where they always did ({@code landed-ships-true-scale.md}). It is drawn in
 * every regime, berthed and flying, so detail does not change when a ship lifts. The fleet-combat sprite is the
 * game's and is untouched; a hull with no remaster, or a remaster that failed to load, draws the vanilla sprite.
 */
final class GroundHullArt {

    private static final Map<String, String> REMASTERED = Map.of(
            "graphics/ships/venture/venture.png", "graphics/battle/ships/venture.png",
            "graphics/ships/hound/hound_pirates.png", "graphics/battle/ships/hound_pirates.png");

    private GroundHullArt() {}

    /** The remastered path for a vanilla hull sprite, or the vanilla path itself when there is none. */
    static String path(String vanillaPath) {
        if (vanillaPath == null) return null;
        String key = vanillaPath.replace('\\', '/');
        return REMASTERED.getOrDefault(key, vanillaPath);
    }

    /** The art a hull is drawn with: its remaster when one loads, else its own sprite. */
    static ShuttleSpriteCache sprite(BattleSprites sprites, Warship ship) {
        String remaster = path(ship.spritePath);
        if (remaster != null && !remaster.equals(ship.spritePath)) {
            ShuttleSpriteCache art = sprites.spriteByPath(remaster);
            if (art != null) return art;
        }
        return sprites.spriteByPath(ship.spritePath);
    }
}
