package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared live-game asset cache for every Armory soldier preview canvas. */
public final class ArmoryPreviewAssets implements ArmoryLoadoutPreviewComposer.Assets {

    private final BattleSprites sprites = new BattleSprites();
    private final Map<String, LayeredSpriteCache> catalogIcons = new LinkedHashMap<>();
    private boolean loadAttempted;

    @Override
    public LayeredUnitAssets layered(LayeredArmorFamily armor) {
        ensureLoaded();
        return sprites.layeredUnitSprites().get(armor);
    }

    @Override
    public LayeredSpriteCache icon(String path) {
        if (path == null) return null;
        ensureLoaded();
        return catalogIcons.computeIfAbsent(path, sprites::loadLayeredSprite);
    }

    private void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        sprites.ensureLayeredUnitSprites();
    }
}
