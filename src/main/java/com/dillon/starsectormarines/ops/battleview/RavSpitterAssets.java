package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavSpitterRig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Spitter's parts, exported by {@code art-source/rav-redesign/spitter/export_spitter_rig.py}: its haunched back,
 * its head with the hood folded and popped open and its tail in short slices, all head-up; the parts assembled and
 * dulled for its corpse, and the acid pool it lies in. Tail slices run from the root down.
 */
public final class RavSpitterAssets {

    public static final String ROOT = "graphics/battle/rav-spitter/";

    final LayeredSpriteCache back;
    final LayeredSpriteCache headShut;
    final LayeredSpriteCache headOpen;
    final LayeredSpriteCache dead;
    final LayeredSpriteCache pool;
    final LayeredSpriteCache[] tail = new LayeredSpriteCache[RavSpitterRig.TAIL_ART_SLICES];

    private RavSpitterAssets(LayeredSpriteCache back, LayeredSpriteCache headShut, LayeredSpriteCache headOpen,
                             LayeredSpriteCache dead, LayeredSpriteCache pool) {
        this.back = back;
        this.headShut = headShut;
        this.headOpen = headOpen;
        this.dead = dead;
        this.pool = pool;
    }

    /** Every sprite through loader, or null when any is missing, so the Spitter keeps its old art. */
    public static RavSpitterAssets load(Function<String, LayeredSpriteCache> loader) {
        RavSpitterAssets assets = new RavSpitterAssets(loader.apply(ROOT + "back.png"),
                loader.apply(ROOT + "head-shut.png"), loader.apply(ROOT + "head-open.png"),
                loader.apply(ROOT + "dead.png"), loader.apply(ROOT + "acid-pool.png"));
        if (assets.back == null || assets.headShut == null || assets.headOpen == null || assets.dead == null
                || assets.pool == null) return null;
        for (int i = 0; i < assets.tail.length; i++) {
            assets.tail[i] = loader.apply(ROOT + "tail-" + i + ".png");
            if (assets.tail[i] == null) return null;
        }
        return assets;
    }

    /** Every image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>();
        all.add(back);
        all.add(headShut);
        all.add(headOpen);
        all.add(dead);
        all.add(pool);
        for (LayeredSpriteCache t : tail) all.add(t);
        return all;
    }
}
