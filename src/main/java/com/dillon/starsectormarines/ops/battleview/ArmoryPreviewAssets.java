package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared live-game asset cache for every Armory soldier preview canvas.
 *
 * <p>Holds its textures under a lease of its own ({@code battle-textures.md}): only the armour
 * families and icons a preview has actually asked for, loaded on that first ask, and given back
 * by {@link #release()} when the Armory closes. A release drops every sprite handed out, so a
 * preview asks again on each draw rather than keeping one.
 */
public final class ArmoryPreviewAssets implements ArmoryLoadoutPreviewComposer.Assets {

    private final Map<String, LayeredSpriteCache> catalogIcons = new LinkedHashMap<>();
    private TextureLease lease;
    private BattleSprites sprites;

    @Override
    public LayeredUnitAssets layered(LayeredArmorFamily armor) {
        BattleSprites loaded = open();
        loaded.ensureLayeredFamily(armor);
        return loaded.layeredUnitSprites().get(armor);
    }

    @Override
    public LayeredSpriteCache icon(String path) {
        if (path == null) return null;
        BattleSprites loaded = open();
        return catalogIcons.computeIfAbsent(path, loaded::loadLayeredSprite);
    }

    /** Gives every texture back; the next preview drawn loads what it shows afresh. */
    public void release() {
        if (lease == null) return;
        lease.release();
        lease = null;
        sprites = null;
        catalogIcons.clear();
    }

    private BattleSprites open() {
        if (sprites == null) {
            lease = TextureLedger.shared().lease("armory previews");
            sprites = new BattleSprites(lease);
        }
        return sprites;
    }
}
